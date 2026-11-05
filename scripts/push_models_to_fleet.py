#!/usr/bin/env python3
"""
push_models_to_fleet.py - Intelligent Differential Model Push for Android Fleet

Synchronizes required neural network models (Silero VAD, Zipformer ASR, optional Whisper rescorer,
and Gemma 4 E2B GPU/CPU LLM) to connected Android emulators or physical devices.

Uses differential transfer verification: queries remote file size via `adb shell ls -l` / `stat`
prior to pushing, skipping transfer if the remote file exists and byte size matches.

Destination on device:
    /sdcard/Android/data/com.edgecue.app/files/{vad,asr,whisper,llm}/

Usage:
    python scripts/push_models_to_fleet.py [--serial <serial>] [--models-dir <path>] [--dry-run]
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

ROOT_DIR = Path(__file__).resolve().parent.parent

# Configure standard UTF-8 console streams on Windows to prevent UnicodeEncodeError
if sys.platform == "win32":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# Standard model categories and files
REQUIRED_MODELS = [
    # VAD
    {
        "category": "vad",
        "filename": "silero_vad.onnx",
        "remote_subpath": "vad/silero_vad.onnx",
        "required": True,
        "description": "Silero VAD ONNX model (~1.4 MB)"
    },
    # Streaming Zipformer ASR
    {
        "category": "asr",
        "filename": "encoder-epoch-99-avg-1.int8.onnx",
        "remote_subpath": "asr/encoder-epoch-99-avg-1.int8.onnx",
        "required": True,
        "description": "Zipformer int8 encoder (~187.8 MB)"
    },
    {
        "category": "asr",
        "filename": "decoder-epoch-99-avg-1.onnx",
        "alternative_filenames": ["decoder-epoch-99-avg-1.int8.onnx"],
        "remote_subpath": "asr/decoder-epoch-99-avg-1.onnx",
        "required": True,
        "description": "Zipformer decoder (~3.5 MB)"
    },
    {
        "category": "asr",
        "filename": "joiner-epoch-99-avg-1.int8.onnx",
        "remote_subpath": "asr/joiner-epoch-99-avg-1.int8.onnx",
        "required": True,
        "description": "Zipformer int8 joiner (~4.6 MB)"
    },
    {
        "category": "asr",
        "filename": "tokens.txt",
        "remote_subpath": "asr/tokens.txt",
        "required": True,
        "description": "Sherpa-ONNX BPE vocabulary (~55 KB)"
    },
    # Universal GPU/CPU LLM for Emulators (2,588,147,712 bytes)
    {
        "category": "llm",
        "filename": "gemma-4-E2B-it.litertlm",
        "remote_subpath": "llm/gemma-4-E2B-it.litertlm",
        "expected_bytes": 2588147712,
        "required": True,
        "description": "Gemma 4 E2B GPU/CPU LiteRT-LM (2,588,147,712 bytes)"
    },
    # Optional Whisper Rescorer
    {
        "category": "whisper",
        "filename": "tiny.en-encoder.int8.onnx",
        "remote_subpath": "whisper/tiny.en-encoder.int8.onnx",
        "required": False,
        "description": "Whisper tiny.en int8 encoder (~39 MB)"
    },
    {
        "category": "whisper",
        "filename": "tiny.en-decoder.int8.onnx",
        "remote_subpath": "whisper/tiny.en-decoder.int8.onnx",
        "required": False,
        "description": "Whisper tiny.en int8 decoder (~60 MB)"
    },
    {
        "category": "whisper",
        "filename": "tiny.en-tokens.txt",
        "remote_subpath": "whisper/tiny.en-tokens.txt",
        "required": False,
        "description": "Whisper tokens vocabulary"
    }
]

REMOTE_BASE_DIR = "/sdcard/Android/data/com.edgecue.app/files"


def find_adb_path() -> str:
    """Locate adb executable across environment and standard Android SDK paths."""
    # 1. Check PATH
    adb_in_path = shutil.which("adb")
    if adb_in_path:
        return adb_in_path

    # 2. Check LOCALAPPDATA (Windows)
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        candidate = Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
        if candidate.exists():
            return str(candidate)

    # 3. Check ANDROID_HOME / ANDROID_SDK_ROOT
    for env_var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        val = os.environ.get(env_var)
        if val:
            for ext in (".exe", ""):
                candidate = Path(val) / "platform-tools" / f"adb{ext}"
                if candidate.exists():
                    return str(candidate)

    return "adb"


def run_adb(
    cmd: List[str],
    adb_path: str = "adb",
    serial: Optional[str] = None,
    timeout: int = 60
) -> Tuple[int, str, str]:
    """Execute an adb command targeting an optional device serial with timeout."""
    full_cmd = [adb_path]
    if serial:
        full_cmd.extend(["-s", serial])
    full_cmd.extend(cmd)

    try:
        proc = subprocess.run(
            full_cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout
        )
        return proc.returncode, proc.stdout.strip(), proc.stderr.strip()
    except subprocess.TimeoutExpired:
        return -1, "", f"ADB command timed out after {timeout}s: {' '.join(full_cmd)}"
    except Exception as e:
        return -1, "", str(e)


def get_connected_devices(adb_path: str) -> List[str]:
    """Query connected online devices from adb."""
    code, stdout, _ = run_adb(["devices"], adb_path=adb_path)
    if code != 0:
        return []

    devices = []
    for line in stdout.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def resolve_local_model_file(spec: Dict, candidate_dirs: List[Path]) -> Optional[Path]:
    """Locate local model file across candidate source directories."""
    filenames = [spec["filename"]]
    if "alternative_filenames" in spec:
        filenames.extend(spec["alternative_filenames"])

    for base_dir in candidate_dirs:
        if not base_dir.exists():
            continue

        for fname in filenames:
            # 1. Check nested path (e.g. base_dir / vad / silero_vad.onnx)
            nested = base_dir / spec["category"] / fname
            if nested.is_file() and nested.stat().st_size > 0:
                return nested

            # 2. Check flat path (e.g. base_dir / silero_vad.onnx)
            flat = base_dir / fname
            if flat.is_file() and flat.stat().st_size > 0:
                return flat

    return None


def get_remote_file_size(remote_path: str, serial: str, adb_path: str) -> int:
    """
    Query remote file size in bytes via adb shell.
    Uses `stat -c %s` first, falling back to `ls -l` regex parsing.
    Returns file size in bytes, or -1 if the file does not exist.
    """
    # Attempt 1: stat -c %s
    code, stdout, _ = run_adb(
        ["shell", f"stat -c %s '{remote_path}' 2>/dev/null"],
        adb_path=adb_path,
        serial=serial
    )
    if code == 0 and stdout.strip().isdigit():
        return int(stdout.strip())

    # Attempt 2: ls -l
    code, stdout, _ = run_adb(
        ["shell", f"ls -l '{remote_path}' 2>/dev/null"],
        adb_path=adb_path,
        serial=serial
    )
    if code == 0 and stdout:
        for line in stdout.splitlines():
            line = line.strip()
            if "No such file" in line or "not found" in line:
                return -1
            # Standard toybox/toolbox ls -l:
            # -rw-rw---- 1 u0_a123 media_rw 2588147712 2026-11-01 04:00 filename
            # or -rw-rw---- 1 root root 2588147712 Nov 1 04:00 filename
            match = re.search(r"\s+(\d{1,12})\s+(?:\d{4}-\d{2}-\d{2}|\w{3}\s+\d+)", line)
            if match:
                return int(match.group(1))

            parts = line.split()
            for part in parts[3:6]:
                if part.isdigit() and int(part) > 0:
                    return int(part)

    return -1


def ensure_remote_dir(remote_dir: str, serial: str, adb_path: str) -> bool:
    """Create directory on device if it does not exist."""
    code, _, _ = run_adb(
        ["shell", f"mkdir -p '{remote_dir}'"],
        adb_path=adb_path,
        serial=serial
    )
    return code == 0


def push_models_to_device(
    serial: str,
    candidate_dirs: List[Path],
    adb_path: str,
    dry_run: bool = False,
    force: bool = False,
    verbose: bool = False
) -> Dict[str, any]:
    """
    Executes differential model push to a single Android device.
    Returns status report dictionary.
    """
    report = {
        "serial": serial,
        "transferred": [],
        "skipped": [],
        "missing_local": [],
        "errors": [],
        "all_required_present": True
    }

    print(f"\n==================================================================")
    print(f" Syncing models to device: {serial}")
    print(f" Target path: {REMOTE_BASE_DIR}/")
    print(f"==================================================================")

    for spec in REQUIRED_MODELS:
        category = spec["category"]
        remote_path = f"{REMOTE_BASE_DIR}/{spec['remote_subpath']}"
        remote_dir = str(Path(remote_path).parent).replace("\\", "/")
        desc = spec["description"]
        is_required = spec["required"]

        local_file = resolve_local_model_file(spec, candidate_dirs)

        if not local_file:
            if is_required:
                report["missing_local"].append(spec["filename"])
                report["all_required_present"] = False
                print(f"[MISSING LOCAL] Required model: {spec['filename']} ({desc}) not found in search paths")
            else:
                if verbose:
                    print(f"[OPTIONAL] Model {spec['filename']} not found locally (rescorer optional)")
            continue

        local_size = local_file.stat().st_size

        # Validation check for LLM model size
        if "expected_bytes" in spec and local_size != spec["expected_bytes"]:
            print(f"[WARN] Local file {local_file.name} size ({local_size:,} B) != expected ({spec['expected_bytes']:,} B)")

        # Query remote size
        remote_size = get_remote_file_size(remote_path, serial, adb_path)

        if not force and remote_size == local_size:
            report["skipped"].append({
                "file": local_file.name,
                "remote_path": remote_path,
                "size_bytes": local_size
            })
            print(f"[SKIP] {spec['remote_subpath']} - remote size matches ({local_size:,} bytes)")
            continue

        # Need to transfer
        if dry_run:
            report["transferred"].append({
                "file": local_file.name,
                "remote_path": remote_path,
                "size_bytes": local_size,
                "dry_run": True
            })
            status_str = f"remote missing" if remote_size == -1 else f"size mismatch ({remote_size:,} B -> {local_size:,} B)"
            print(f"[DRY-RUN PUSH] {local_file.name} -> {remote_path} ({local_size:,} B, {status_str})")
            continue

        # Real push
        ensure_remote_dir(remote_dir, serial, adb_path)
        print(f"[PUSHING] {local_file.name} ({local_size:,} B) -> {remote_path}...")
        code, stdout, stderr = run_adb(
            ["push", str(local_file), remote_path],
            adb_path=adb_path,
            serial=serial,
            timeout=600
        )

        if code != 0:
            err_msg = f"Failed to push {local_file.name}: {stderr or stdout}"
            report["errors"].append(err_msg)
            print(f"[ERROR] {err_msg}")
            if is_required:
                report["all_required_present"] = False
            continue

        # Verify size after push
        verified_size = get_remote_file_size(remote_path, serial, adb_path)
        if verified_size == local_size:
            report["transferred"].append({
                "file": local_file.name,
                "remote_path": remote_path,
                "size_bytes": verified_size
            })
            print(f"[VERIFIED] Successfully synced {spec['remote_subpath']} ({verified_size:,} B)")
        else:
            err_msg = f"Post-push verification failed for {remote_path}: expected {local_size:,} B, got {verified_size:,} B"
            report["errors"].append(err_msg)
            print(f"[ERROR] {err_msg}")
            if is_required:
                report["all_required_present"] = False

    return report


def build_candidate_directories(custom_dir: Optional[str]) -> List[Path]:
    """Construct ordered list of candidate model directories to search."""
    candidates = []

    if custom_dir:
        candidates.append(Path(custom_dir).resolve())

    env_dir = os.environ.get("INTERVIEW_MODELS_DIR")
    if env_dir:
        candidates.append(Path(env_dir).resolve())

    # Project default directories
    candidates.append(ROOT_DIR / "models")
    candidates.append(ROOT_DIR / "app" / "src" / "main" / "assets" / "models")
    candidates.append(ROOT_DIR / "app" / "src" / "main" / "assets")
    candidates.append(Path.home() / ".interview_models")
    candidates.append(Path.home() / "Downloads")

    # De-duplicate while preserving order
    seen = set()
    result = []
    for c in candidates:
        if str(c) not in seen:
            seen.add(str(c))
            result.append(c)

    return result


def main():
    parser = argparse.ArgumentParser(
        description="Intelligent differential model sync for Android interview test fleet."
    )
    parser.add_argument(
        "--serial",
        type=str,
        default=None,
        help="Target device serial (default: sync all connected online devices)."
    )
    parser.add_argument(
        "--models-dir",
        type=str,
        default=None,
        help="Path to source models directory on host (containing vad, asr, llm models)."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Inspect differences and print push actions without modifying device storage."
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Force overwrite on device even if remote file size matches."
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print verbose diagnostics."
    )

    args = parser.parse_args()

    adb_path = find_adb_path()
    candidate_dirs = build_candidate_directories(args.models_dir)

    print("==================================================================")
    print(" EdgeCue - Fleet Model Push Tool")
    print(f" ADB Path:        {adb_path}")
    print(f" Mode:            {'DRY-RUN' if args.dry_run else 'LIVE DIFFERENTIAL PUSH'}")
    print(f" Search Paths:")
    for d in candidate_dirs:
        status = "EXISTS" if d.exists() else "NOT FOUND"
        print(f"   - {d} [{status}]")
    print("==================================================================")

    # Determine target devices
    if args.serial:
        devices = [args.serial]
    else:
        devices = get_connected_devices(adb_path)
        if not devices:
            print("[INFO] No connected online devices found via ADB.")
            if args.dry_run:
                print("[DRY-RUN] Simulating model sync against virtual target 'emulator-5554'...")
                devices = ["emulator-5554"]
            else:
                print("Connect an emulator or device and retry, or use --dry-run.")
                sys.exit(0)

    print(f"Found {len(devices)} target device(s): {', '.join(devices)}")

    all_success = True
    for serial in devices:
        report = push_models_to_device(
            serial=serial,
            candidate_dirs=candidate_dirs,
            adb_path=adb_path,
            dry_run=args.dry_run,
            force=args.force,
            verbose=args.verbose
        )
        if not report["all_required_present"] or report["errors"]:
            all_success = False

    print("\n==================================================================")
    if all_success or args.dry_run:
        print(" Fleet model synchronization completed successfully!" if all_success else " Fleet model synchronization dry-run completed (missing local files reported).")
    else:
        print(" Model synchronization finished with warnings or missing files.")
    print("==================================================================")

    sys.exit(0 if (all_success or args.dry_run) else 1)


if __name__ == "__main__":
    main()
