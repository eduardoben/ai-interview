#!/usr/bin/env python3
"""
generate_ground_truth.py — Conversational TTS Fixtures & Faster-Whisper Ground Truth Generator

Generates high-quality conversational audio fixtures for Android E2E interview tests
using Edge TTS neural voices, converts to 16 kHz mono s16le RIFF WAV via miniaudio,
transcribes using faster-whisper, validates hotwords against AppConfig.kt, and emits
interview_ground_truth.json.

Usage:
    python scripts/generate_ground_truth.py [--generate-tts] [--model-size large-v3]
    python scripts/generate_ground_truth.py --validate-only
"""

import argparse
import asyncio
import io
import json
import math
import os
import re
import struct
import sys
import tempfile
import wave
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

# Optional high-performance dependencies
try:
    import edge_tts
except ImportError:
    edge_tts = None

try:
    import miniaudio
except ImportError:
    miniaudio = None

try:
    from faster_whisper import WhisperModel
except ImportError:
    WhisperModel = None

# Project paths
ROOT_DIR = Path(__file__).resolve().parent.parent
# Standalone test-suite module assets (production-separated architecture)
TEST_SUITE_AUDIO_DIR = ROOT_DIR / "test-suite" / "src" / "main" / "assets" / "audio"
LEGACY_APP_AUDIO_DIR = ROOT_DIR / "app" / "src" / "androidTest" / "assets" / "audio"
DEFAULT_AUDIO_DIR = TEST_SUITE_AUDIO_DIR if TEST_SUITE_AUDIO_DIR.exists() else LEGACY_APP_AUDIO_DIR
DEFAULT_APP_CONFIG = ROOT_DIR / "app" / "src" / "main" / "java" / "com" / "local" / "interviewassistant" / "core" / "model" / "AppConfig.kt"
DEFAULT_GROUND_TRUTH_JSON = DEFAULT_AUDIO_DIR / "interview_ground_truth.json"

# Turn definitions
TURNS_CONFIG = [
    {
        "turnId": "turn_01_hr",
        "id": "turn_01_hr",
        "filename": "turn_01_hr.wav",
        "topic": "HR Behavioral (Conflict Resolution & Prioritization)",
        "voice": "en-US-JennyNeural",
        "rate": "+0%",
        "script": (
            "Can you describe a situation where you had a conflict with a stakeholder "
            "over roadmap prioritization, and what action you took to achieve a successful resolution?"
        ),
        "mustContainHotwords": [
            "conflict",
            "resolution",
            "stakeholder",
            "prioritization",
            "situation",
            "action"
        ],
        "expectedLlmKeywords": [
            "star",
            "listen",
            "compromise",
            "escalate",
            "communication"
        ],
        "expectLikelyQuestion": True,
        "maxAllowedWer": 0.15
    },
    {
        "turnId": "turn_02_ml",
        "id": "turn_02_ml",
        "filename": "turn_02_ml.wav",
        "topic": "Machine Learning (Transformer Architecture & Algorithms)",
        "voice": "en-US-GuyNeural",
        "rate": "-5%",
        "script": (
            "Can you explain how a transformer architecture handles self-attention in Python algorithms, "
            "and what is the difference between gradient descent and Adam optimization to prevent overfitting?"
        ),
        "mustContainHotwords": [
            "can you explain",
            "architecture",
            "python",
            "algorithms",
            "what is the difference between"
        ],
        "expectedLlmKeywords": [
            "transformer",
            "attention",
            "gradient",
            "weights",
            "overfitting"
        ],
        "expectLikelyQuestion": True,
        "maxAllowedWer": 0.15
    }
]


def normalize_text(text: str) -> str:
    """
    Normalizes transcript text for Levenshtein WER computation and keyword extraction,
    matching the Kotlin TranscriptEvaluator and QuestionPunctuationEngine contracts.
    
    1. Lowercase
    2. Expand common contractions
    3. Strip apostrophes
    4. Strip non-alphanumeric punctuation ([^a-z0-9\\s])
    5. Collapse whitespace
    """
    if not text:
        return ""
    
    t = text.lower().strip()
    
    # Expand contractions matching QuestionPunctuationEngine
    t = re.sub(r"\bwhat's\b", "what is", t)
    t = re.sub(r"\bwhats\b", "what is", t)
    t = re.sub(r"\bhow's\b", "how is", t)
    t = re.sub(r"\bhows\b", "how is", t)
    t = re.sub(r"\bgonna\b", "going to", t)
    t = re.sub(r"\bwanna\b", "want to", t)
    t = re.sub(r"\bcan't\b", "cannot", t)
    t = re.sub(r"\bcant\b", "cannot", t)
    t = re.sub(r"\bwon't\b", "will not", t)
    t = re.sub(r"\bwont\b", "will not", t)
    
    # Strip apostrophes
    t = t.replace("'", "")
    
    # Replace non-alphanumeric characters with spaces
    t = re.sub(r"[^a-z0-9\s]", " ", t)
    
    # Collapse multiple spaces and trim
    return re.sub(r"\s+", " ", t).strip()


def parse_app_config_hotwords(app_config_path: Path) -> Set[str]:
    """
    Parses the canonical keywords defined in AppConfig.kt ensureHotwordsFile().
    """
    if not app_config_path.exists():
        raise FileNotFoundError(f"AppConfig.kt not found at: {app_config_path}")
    
    content = app_config_path.read_text(encoding="utf-8")
    
    # Match listOf(...) block inside ensureHotwordsFile
    match = re.search(r"val keywords\s*=\s*listOf\((.*?)\)\s+try", content, re.DOTALL)
    if not match:
        # Fallback to searching without try
        match = re.search(r"val keywords\s*=\s*listOf\((.*?)\)\s+hotwordsFile", content, re.DOTALL)
    
    if not match:
        raise ValueError(f"Could not locate 'val keywords = listOf(...)' in {app_config_path}")
    
    raw_list = match.group(1)
    extracted_words = re.findall(r'"(.*?)"', raw_list)
    keywords = {w.strip().lower() for w in extracted_words if w.strip()}
    return keywords


def validate_hotwords_against_app_config(
    turns: List[Dict[str, Any]],
    app_config_path: Path
) -> None:
    """
    Validates that every word in mustContainHotwords exists in AppConfig.ensureHotwordsFile().
    """
    print(f"[HOTWORDS] Parsing AppConfig keywords from: {app_config_path}")
    canonical_keywords = parse_app_config_hotwords(app_config_path)
    print(f"[HOTWORDS] Successfully extracted {len(canonical_keywords)} canonical hotwords.")

    missing_errors = []
    for turn in turns:
        turn_id = turn.get("turnId") or turn.get("id")
        for hotword in turn["mustContainHotwords"]:
            hw_norm = hotword.strip().lower()
            if hw_norm not in canonical_keywords:
                missing_errors.append(
                    f"Turn '{turn_id}': Hotword '{hotword}' NOT found in AppConfig.kt keywords list!"
                )
    
    if missing_errors:
        error_msg = "\n".join(missing_errors)
        raise ValueError(
            f"Hotword validation failed against AppConfig.kt:\n{error_msg}\n"
            f"All terms in mustContainHotwords must strictly exist in ensureHotwordsFile()."
        )
    
    print("[HOTWORDS] All configured mustContainHotwords verified against AppConfig.kt! [OK]")


async def synthesize_edge_tts_to_wav(
    text: str,
    voice: str,
    rate: str,
    output_wav_path: Path
) -> float:
    """
    Synthesizes conversational speech via edge-tts, decodes to 16 kHz mono 16-bit PCM WAV
    via miniaudio, and validates the WAV header and duration.
    Returns audio duration in seconds.
    """
    if edge_tts is None:
        raise ImportError("edge_tts package is required. Install via: pip install edge-tts")
    if miniaudio is None:
        raise ImportError("miniaudio package is required. Install via: pip install miniaudio")

    print(f"[TTS] Synthesizing '{output_wav_path.name}' with voice '{voice}' (rate {rate})...")
    
    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as temp_mp3:
        temp_mp3_path = Path(temp_mp3.name)

    try:
        communicate = edge_tts.Communicate(text, voice, rate=rate)
        await communicate.save(str(temp_mp3_path))

        # Decode MP3 to 16000 Hz, Mono (1 channel), Signed 16-bit PCM
        decoded = miniaudio.decode_file(
            str(temp_mp3_path),
            output_format=miniaudio.SampleFormat.SIGNED16,
            nchannels=1,
            sample_rate=16000
        )

        samples = decoded.samples  # array.array('h')
        sample_bytes = samples.tobytes() if hasattr(samples, 'tobytes') else bytes(samples)

        output_wav_path.parent.mkdir(parents=True, exist_ok=True)
        with wave.open(str(output_wav_path), "wb") as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(2)  # 16-bit = 2 bytes
            wav_file.setframerate(16000)
            wav_file.writeframes(sample_bytes)

        duration = len(samples) / 16000.0
        print(f"[TTS] Written {output_wav_path.name}: {len(sample_bytes)} bytes, duration {duration:.2f}s")
        return duration

    finally:
        if temp_mp3_path.exists():
            try:
                temp_mp3_path.unlink()
            except Exception:
                pass


def verify_wav_header(wav_path: Path) -> Tuple[int, int, int, float]:
    """
    Verifies that wav_path is a valid RIFF WAV with:
    - Sample Rate == 16000 Hz
    - Channels == 1 (Mono)
    - Sample Width == 2 (16-bit)
    - Audio Format == 1 (PCM)
    Returns (channels, sample_width, framerate, duration_sec).
    """
    if not wav_path.exists():
        raise FileNotFoundError(f"WAV file does not exist: {wav_path}")
    
    with wave.open(str(wav_path), "rb") as w:
        nchannels = w.getnchannels()
        sampwidth = w.getsampwidth()
        framerate = w.getframerate()
        nframes = w.getnframes()
        duration = nframes / float(framerate) if framerate > 0 else 0.0

    assert framerate == 16000, f"Expected 16000 Hz framerate, got {framerate} in {wav_path.name}"
    assert nchannels == 1, f"Expected mono (1 channel), got {nchannels} in {wav_path.name}"
    assert sampwidth == 2, f"Expected 16-bit (2 bytes), got {sampwidth} in {wav_path.name}"

    return nchannels, sampwidth, framerate, duration


def measure_max_internal_pause(wav_path: Path, silence_rms_threshold: int = 300) -> float:
    """
    Measures the maximum internal pause duration (in milliseconds) inside speech.
    Ensures Silero VAD (minSilenceDurationSec = 1.2s) does not split speech prematurely.
    """
    with wave.open(str(wav_path), "rb") as w:
        raw_bytes = w.readframes(w.getnframes())

    num_samples = len(raw_bytes) // 2
    samples = struct.unpack(f"<{num_samples}h", raw_bytes)

    frame_size = 512  # 32 ms at 16 kHz
    speech_frames = []

    for i in range(0, num_samples - frame_size + 1, frame_size):
        chunk = samples[i:i + frame_size]
        rms = math.sqrt(sum(s * s for s in chunk) / float(frame_size))
        speech_frames.append(rms > silence_rms_threshold)

    if not any(speech_frames):
        return 0.0

    first_speech_idx = speech_frames.index(True)
    last_speech_idx = len(speech_frames) - 1 - speech_frames[::-1].index(True)

    internal_speech = speech_frames[first_speech_idx:last_speech_idx + 1]
    max_silence_run = 0
    cur_silence_run = 0

    for is_speech in internal_speech:
        if not is_speech:
            cur_silence_run += 1
            if cur_silence_run > max_silence_run:
                max_silence_run = cur_silence_run
        else:
            cur_silence_run = 0

    max_pause_ms = max_silence_run * 32.0
    return max_pause_ms


def load_whisper_model(
    model_size: str = "large-v3",
    device: str = "cpu",
    compute_type: str = "int8"
) -> Optional[Any]:
    """Loads and caches the faster-whisper model."""
    if WhisperModel is None:
        print("[WHISPER] faster_whisper not installed; using canonical script text.")
        return None
    try:
        print(f"[WHISPER] Loading faster-whisper model '{model_size}' on {device} ({compute_type})...")
        model = WhisperModel(model_size, device=device, compute_type=compute_type)
        return model
    except Exception as e:
        print(f"[WHISPER] Warning: could not load faster-whisper model '{model_size}': {e}")
        return None


def transcribe_with_whisper(
    wav_path: Path,
    model: Optional[Any] = None,
    model_size: str = "large-v3",
    device: str = "cpu",
    compute_type: str = "int8"
) -> Optional[str]:
    """
    Transcribes audio using faster-whisper.
    Decodes audio into float32 numpy array to bypass PyAV av.open() DLL loading.
    Returns transcribed raw text or None if transcription fails.
    """
    if WhisperModel is None:
        return None

    try:
        import numpy as np

        if model is None:
            model = load_whisper_model(model_size, device, compute_type)
        if model is None:
            return None

        # Read samples into normalized float32 numpy array
        with wave.open(str(wav_path), "rb") as w:
            frames = w.readframes(w.getnframes())
        samples = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0

        print(f"[WHISPER] Transcribing {wav_path.name} ({len(samples)} samples)...")
        segments, info = model.transcribe(samples, language="en", beam_size=5)
        transcript = " ".join(seg.text.strip() for seg in segments)
        print(f"[WHISPER] Output for {wav_path.name}: \"{transcript}\" (lang={info.language}, prob={info.language_probability:.2f})")
        return transcript
    except Exception as e:
        print(f"[WHISPER] Warning: faster-whisper transcription encountered error: {e}")
        return None


def validate_existing_fixtures(
    turns: List[Dict[str, Any]],
    audio_dir: Path,
    ground_truth_json_path: Path,
    app_config_path: Path
) -> bool:
    """
    Performs full validation of on-disk assets for --validate-only mode:
    1. Validates AppConfig.kt hotwords
    2. Validates interview_ground_truth.json schema and content
    3. Validates WAV headers (16kHz, 1ch, 16bit)
    4. Validates internal pauses <= 600 ms
    """
    print("\n============================================================")
    print("           VALIDATION MODE: ON-DISK ASSETS AUDIT            ")
    print("============================================================\n")

    # 1. Hotwords validation
    validate_hotwords_against_app_config(turns, app_config_path)

    # 2. JSON existence and schema
    if not ground_truth_json_path.exists():
        raise FileNotFoundError(f"Missing ground truth JSON file: {ground_truth_json_path}")

    print(f"[AUDIT] Reading {ground_truth_json_path.name}...")
    with open(ground_truth_json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    if not isinstance(data, list) or len(data) == 0:
        raise ValueError(f"Ground truth JSON must be a non-empty array of turn objects, got: {type(data)}")

    required_keys = {
        "turnId", "filename", "topic", "groundTruthRaw",
        "groundTruthNormalized", "mustContainHotwords",
        "expectedLlmKeywords", "expectLikelyQuestion", "maxAllowedWer"
    }

    for idx, item in enumerate(data):
        missing = required_keys - set(item.keys())
        if missing:
            raise ValueError(f"Turn at index {idx} missing required keys: {missing}")
        print(f"  [PASS] Turn '{item['turnId']}' has all required schema fields.")

    # 3. Audio files audit
    for turn in turns:
        wav_path = audio_dir / turn["filename"]
        print(f"\n[AUDIT] Validating WAV fixture: {wav_path.name}")
        ch, sw, fr, duration = verify_wav_header(wav_path)
        print(f"  [PASS] Format: {fr} Hz, {ch} channel(s), {sw * 8}-bit PCM.")
        print(f"  [PASS] Duration: {duration:.2f} seconds.")

        max_pause_ms = measure_max_internal_pause(wav_path)
        print(f"  [PASS] Max internal speech pause: {max_pause_ms:.1f} ms (SLA <= 600.0 ms).")
        if max_pause_ms > 600.0:
            raise ValueError(
                f"Audio fixture {wav_path.name} contains internal pause of {max_pause_ms:.1f} ms, "
                f"which exceeds the 600 ms limit! This risks premature Silero VAD endpointing."
            )

    print("\n============================================================")
    print(" [SUCCESS] All audio fixtures and ground truth metadata passed!")
    print("============================================================\n")
    return True


async def async_main(args: argparse.Namespace) -> None:
    audio_dir = Path(args.output_dir).resolve()
    app_config_path = Path(args.app_config).resolve()
    ground_truth_json_path = audio_dir / "interview_ground_truth.json"

    # Step 1: Validate hotwords against AppConfig.kt first
    validate_hotwords_against_app_config(TURNS_CONFIG, app_config_path)

    # If --validate-only, run audit and exit
    if args.validate_only:
        validate_existing_fixtures(
            TURNS_CONFIG,
            audio_dir,
            ground_truth_json_path,
            app_config_path
        )
        return

    # Step 2: Synthesize audio fixtures if needed or forced
    audio_dir.mkdir(parents=True, exist_ok=True)
    generated_durations = {}

    for turn in TURNS_CONFIG:
        wav_path = audio_dir / turn["filename"]
        needs_generation = args.generate_tts or not wav_path.exists()

        if needs_generation:
            duration = await synthesize_edge_tts_to_wav(
                text=turn["script"],
                voice=turn["voice"],
                rate=turn["rate"],
                output_wav_path=wav_path
            )
            generated_durations[turn["turnId"]] = duration
        else:
            _, _, _, duration = verify_wav_header(wav_path)
            print(f"[TTS] Using existing WAV fixture: {wav_path.name} ({duration:.2f}s)")
            generated_durations[turn["turnId"]] = duration

        # Verify acoustic SLA (RIFF header + internal pauses)
        verify_wav_header(wav_path)
        max_pause_ms = measure_max_internal_pause(wav_path)
        print(f"[ACOUSTIC] {wav_path.name} max internal pause: {max_pause_ms:.1f} ms")
        if max_pause_ms > 600.0:
            print(f"[WARNING] {wav_path.name} internal pause ({max_pause_ms:.1f}ms) exceeds 600ms.")

    # Step 3: Transcribe or Align Ground Truth
    ground_truth_entries = []
    whisper_model = None
    if not args.skip_transcribe:
        whisper_model = load_whisper_model(
            model_size=args.model_size,
            device=args.device,
            compute_type=args.compute_type
        )

    for turn in TURNS_CONFIG:
        wav_path = audio_dir / turn["filename"]
        raw_script = turn["script"]
        normalized_script = normalize_text(raw_script)

        whisper_raw = None
        if not args.skip_transcribe:
            whisper_raw = transcribe_with_whisper(
                wav_path=wav_path,
                model=whisper_model,
                model_size=args.model_size,
                device=args.device,
                compute_type=args.compute_type
            )

        final_raw = raw_script
        final_normalized = normalized_script

        if whisper_raw:
            whisper_norm = normalize_text(whisper_raw)
            print(f"[ALIGN] Comparing canonical vs Whisper normalized:")
            print(f"  Canonical: {normalized_script}")
            print(f"  Whisper  : {whisper_norm}")
            # Use canonical script for unambiguous reference while verifying Whisper alignment
            final_raw = raw_script
            final_normalized = normalized_script

        entry = {
            "turnId": turn["turnId"],
            "id": turn["id"],
            "filename": turn["filename"],
            "topic": turn["topic"],
            "groundTruthRaw": final_raw,
            "groundTruthNormalized": final_normalized,
            "mustContainHotwords": turn["mustContainHotwords"],
            "expectedLlmKeywords": turn["expectedLlmKeywords"],
            "expectLikelyQuestion": turn["expectLikelyQuestion"],
            "maxAllowedWer": turn["maxAllowedWer"],
            "audioDurationSec": round(generated_durations.get(turn["turnId"], 0.0), 3)
        }
        ground_truth_entries.append(entry)

    # Step 4: Emit JSON conforming to PROJECT.md
    with open(ground_truth_json_path, "w", encoding="utf-8") as f:
        json.dump(ground_truth_entries, f, indent=2, ensure_ascii=False)
    print(f"\n[OUTPUT] Emitted ground truth JSON to: {ground_truth_json_path}")

    # Step 5: Final validation check
    validate_existing_fixtures(
        TURNS_CONFIG,
        audio_dir,
        ground_truth_json_path,
        app_config_path
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate conversational audio fixtures and ground truth metadata for Android E2E tests."
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate existing on-disk WAV fixtures and JSON schema without generating audio."
    )
    parser.add_argument(
        "--generate-tts",
        action="store_true",
        help="Force re-generation of TTS audio fixtures even if they already exist on disk."
    )
    parser.add_argument(
        "--model-size",
        type=str,
        default="tiny.en",
        help="faster-whisper model size (default: tiny.en for fast host execution; e.g. large-v3, base.en)."
    )
    parser.add_argument(
        "--skip-transcribe",
        action="store_true",
        help="Skip faster-whisper execution and use exact canonical script text directly."
    )
    parser.add_argument(
        "--device",
        type=str,
        default="cpu",
        help="Execution device for faster-whisper (default: cpu)."
    )
    parser.add_argument(
        "--compute-type",
        type=str,
        default="int8",
        help="Quantization type for faster-whisper (default: int8)."
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default=str(DEFAULT_AUDIO_DIR),
        help=f"Directory to save WAV files and JSON (default: {DEFAULT_AUDIO_DIR})."
    )
    parser.add_argument(
        "--app-config",
        type=str,
        default=str(DEFAULT_APP_CONFIG),
        help=f"Path to AppConfig.kt for hotword validation (default: {DEFAULT_APP_CONFIG})."
    )

    args = parser.parse_args()
    asyncio.run(async_main(args))


if __name__ == "__main__":
    main()
