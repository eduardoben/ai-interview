# Multi-Audio E2E Interview Test Suite Guide 🤖🎧

This guide documents the automated End-to-End (E2E) instrumentation test suite for the **AI Interview Assistant** pipeline (**Silero VAD $\rightarrow$ Zipformer Streaming ASR $\rightarrow$ Whisper `tiny.en` Rescorer $\rightarrow$ Gemma 4 E2B LiteRT-LM**).

---

## 1. Modular Architecture: Decoupling App from Test Suite

To guarantee a clean, production-ready release artifact (`:app:assembleRelease` / `:app:bundleRelease`), the E2E test suite is segregated into a dedicated standalone Android test module: **`:test-suite`**.

```
AIInterviewAssistant/
├── app/                                       # Production Android Application Module
│   ├── src/main/                              # Pure production source code (zero test contamination)
│   └── src/test/                              # Fast JVM unit tests (domain models, view models, DB DAO)
│
├── test-suite/                                # Standalone E2E Test Suite Module (com.android.test)
│   ├── build.gradle.kts                       # Targets :app via targetProjectPath = ":app"
│   └── src/
│       ├── main/
│       │   ├── assets/audio/                  # Audio fixtures & ground truth (zero bloat in :app)
│       │   │   ├── interview_ground_truth.json
│       │   │   ├── turn_01_hr.wav             # 16 kHz mono s16le HR Behavioral fixture (9.0s)
│       │   │   └── turn_02_ml.wav             # 16 kHz mono s16le ML Technical fixture (10.8s)
│       │   └── java/com/local/interviewassistant/
│       │       ├── RealDevicePipelineIntegrationTest.kt  # Physical device sanity check
│       │       └── e2e/
│       │           ├── ScriptedWavAudioSource.kt         # Non-intrusive 32ms audio frame injector
│       │           ├── E2ePipelineHarness.kt             # Test-scoped coordinator & warm LLM retainer
│       │           ├── TranscriptEvaluator.kt            # Dynamic programming Levenshtein WER engine
│       │           └── ParameterizedInterviewE2ETest.kt  # Parameterized JUnit4 6-gate runner
│       └── test/                                         # JVM unit tests for evaluators & stress harness
│
└── scripts/
    ├── generate_ground_truth.py               # Host TTS & faster-whisper ground truth generator
    ├── push_models_to_fleet.py                # Differential model synchronization tool
    └── run_e2e_fleet.py                       # Concurrent 2-device fleet test runner & aggregator
```

### Production Readiness Advantages
1. **Zero Asset Bloat**: Audio fixtures (`.wav`), ground truth metadata, and test audio frames live in `test-suite/src/main/assets/audio/`. The production APK contains zero audio test assets.
2. **Clean ProGuard / R8 Boundary**: `:app` release builds compile, obfuscate, and package only production code without test seams or mock harnesses.
3. **Independent CI/CD Lifecycles**: Standard application builds (`./gradlew :app:bundleRelease`) complete rapidly without processing multi-megabyte audio fixtures or compiling heavy test dependencies.
4. **Targeted Instrumentation**: The test APK (`test-suite-debug.apk`) is packaged under `com.local.interviewassistant.testsuite` and instruments the target application (`com.local.interviewassistant`) via standard Android instrumentation contracts.

---

## 2. The 6 Quantitative Verification Gates

Every audio turn executed by [`ParameterizedInterviewE2ETest`](file:///c:/Users/Master/AndroidStudioProjects/AIInterviewAssistant/test-suite/src/main/java/com/local/interviewassistant/e2e/ParameterizedInterviewE2ETest.kt) must pass all 6 evaluation gates:

| Gate | Name | Measurement | Threshold / Pass Criteria | Emulator SLA Calibration |
| :--- | :--- | :--- | :--- | :--- |
| **Gate 1** | **Pre-Flight Asset Check** | File existence and exact byte lengths | All 6 required model files present; GPU LLM file equals `2,588,147,712` bytes; Whisper rescorer reported. | Prevents running tests against missing or corrupt models. |
| **Gate 2** | **VAD Segmentation Integrity** | Finalized speech segments & endpoint latency | Exactly **1 finalized segment** per turn; endpoint latency $\le 2000\text{ ms}$ from trailing silence start. | Emitted via Silero VAD without mid-utterance splits. |
| **Gate 2b** | **Question Classification** | `TranscriptSegment.isLikelyQuestion` | Soft-fail: if classifier misses, manually invokes `triggerAnswerGeneration()` and records `QUESTION_DETECT_MISS`. | Ensures Gate 5 still executes even on classification edge cases. |
| **Gate 3** | **Normalized Word Error Rate** | Dynamic programming word-level Levenshtein WER | $\text{WER} = \frac{S + D + I}{N} \le 0.15$ | Contraction-normalized (`don't` $\rightarrow$ `do not`, `it's` $\rightarrow$ `it is`). |
| **Gate 4** | **Technical Hotwords Recall** | Required domain keywords present | **100% recall** of turn keywords (case- and compound-insensitive). | Verified against canonical [`AppConfig.ensureHotwordsFile()`](file:///c:/Users/Master/AndroidStudioProjects/AIInterviewAssistant/app/src/main/java/com/local/interviewassistant/core/model/AppConfig.kt#L130). |
| **Gate 5** | **LLM Response Relevance & TTFT** | Keyword coverage & Time-To-First-Token | Answer non-empty; contains $\ge \max(2, 75\%)$ keywords; $\text{TTFT} \le 15000\text{ ms}$ on x86_64 CPU. | Scaled for memory-mapped CPU execution on emulators. |
| **Gate 6** | **Process Stability & Native Memory** | Crash detection & native memory delta | Zero crashes (`FATAL EXCEPTION`, `SIGSEGV`, ANR); heap + VmRSS growth $< 50\text{ MB}$ after Turn 1. | Guarantees no JNI session or native tensor memory leaks. |

---

## 3. Test Seam Architecture & Components

### 1. [`ScriptedWavAudioSource.kt`](file:///c:/Users/Master/AndroidStudioProjects/AIInterviewAssistant/test-suite/src/main/java/com/local/interviewassistant/e2e/ScriptedWavAudioSource.kt)
- Implements [`AudioSource`](file:///c:/Users/Master/AndroidStudioProjects/AIInterviewAssistant/app/src/main/java/com/local/interviewassistant/data/audio/AudioCaptureSource.kt#L16-L18) emitting `Flow<FloatArray>`.
- **Chunk-Scanning RIFF Parser**: Reliably reads standard PCM WAV files and safely skips metadata chunks (`LIST`, `JUNK`, `id3 `) with 2-byte alignment padding.
- **Drift-Free Monotonic Pacing**: Emits 512-sample frames ($32\text{ ms}$ @ $16\text{ kHz}$) using monotonic nano-clock delays, eliminating cumulative thread sleep drift.
- **Trailing Silence Injection**: Follows the audio with $2528\text{ ms}$ (79 frames) of silence to trigger Silero VAD speech-end.
- **Indefinite Suspension**: Calls `awaitCancellation()` after silence to mimic a persistent live microphone without terminating the audio flow.

### 2. [`E2ePipelineHarness.kt`](file:///c:/Users/Master/AndroidStudioProjects/AIInterviewAssistant/test-suite/src/main/java/com/local/interviewassistant/e2e/E2ePipelineHarness.kt)
- **Zero Production Modifications**: Constructs an isolated [`AssistantPipelineCoordinator`](file:///c:/Users/Master/AndroidStudioProjects/AIInterviewAssistant/app/src/main/java/com/local/interviewassistant/service/AssistantPipelineCoordinator.kt) using public constructor seams.
- **`SharedWarmLlmEngineWrapper`**: Wraps [`LiteRtGemmaEngine`](file:///c:/Users/Master/AndroidStudioProjects/AIInterviewAssistant/app/src/main/java/com/local/interviewassistant/data/llm/LiteRtGemmaEngine.kt). Turns coordinator `stop()` release calls into NO-OPs, keeping the 2.58 GB model weights warm in RAM across turns (0 ms re-initialization).
- **Test Coroutine Scope**: Uses an isolated `SupervisorJob` per turn to prevent asynchronous coroutine leaks.

### 3. [`TranscriptEvaluator.kt`](file:///c:/Users/Master/AndroidStudioProjects/AIInterviewAssistant/test-suite/src/main/java/com/local/interviewassistant/e2e/TranscriptEvaluator.kt)
- Computes word-level Levenshtein distance using an $O(N \times M)$ dynamic programming matrix with full Substitution, Deletion, and Insertion backtracking.
- Contraction expansion and casing normalization matches [`QuestionPunctuationEngine`](file:///c:/Users/Master/AndroidStudioProjects/AIInterviewAssistant/app/src/main/java/com/local/interviewassistant/data/stt/QuestionPunctuationEngine.kt).
- Compound-aware hotwords matching checks both individual tokens and adjacent bigrams ($t_k + t_{k+1} == \text{hotword}$, e.g. `"state flow"` vs `"stateflow"`).

---

## 4. Ground Truth Pipeline (`scripts/generate_ground_truth.py`)

Generates test audio fixtures and ground-truth metadata:
```powershell
# Validate existing on-disk audio fixtures and ground truth JSON:
python scripts/generate_ground_truth.py --validate-only

# Synthesize new audio fixtures via Edge-TTS and transcribe via faster-whisper:
python scripts/generate_ground_truth.py --generate-tts --model-size large-v3
```

- **Neural TTS Voices**: Uses Microsoft Edge Neural voices (`en-US-JennyNeural` for HR behavioral questions, `en-US-GuyNeural` for ML technical questions).
- **Acoustic Standardization**: Converts to $16\text{ kHz}$, mono, 16-bit signed PCM WAV with internal speech pauses $\le 256\text{ ms}$ (well below the VAD endpoint SLA of $600\text{ ms}$).
- **Hotword Cross-Validation**: Automatically extracts canonical hotwords from `AppConfig.kt` and validates that all `mustContainHotwords` are actively boosted in Sherpa-ONNX's BPE vocabulary.

---

## 5. Concurrent Fleet Orchestration (`scripts/run_e2e_fleet.py`)

Automates batch testing across the Android Studio virtual test fleet:

```
Round 1 (Concurrent): Pixel_10_Pro_XL (API 37) + Galaxy_Tab_S7_Plus (API 35) [8 GB RAM each]
Round 2 (Concurrent): Galaxy_S25_Ultra (API 37) + Galaxy_A54_5G (API 33)     [8 GB RAM each]
```

### Execution Commands
```powershell
# 1. Dry run (simulates scheduling, APK verification, and report aggregation):
python scripts/run_e2e_fleet.py --dry-run

# 2. Live execution against connected emulator fleet:
python scripts/run_e2e_fleet.py

# 3. Target a single emulator serial:
python scripts/run_e2e_fleet.py --serial emulator-5554

# 4. Skip APK compilation if already built:
python scripts/run_e2e_fleet.py --skip-build
```

### Key Capabilities
- **2-Device Concurrency**: Uses Python `ThreadPoolExecutor(max_workers=2)` to run two devices in parallel per round, maximizing host RAM utilization while avoiding CPU/OOM saturation.
- **Differential Model Push**: Uses `scripts/push_models_to_fleet.py` to check remote file sizes before pushing multi-gigabyte models, skipping already-synchronized weights.
- **Logcat Forensic Scanning**: Monitors logcat for `FATAL EXCEPTION`, `SIGSEGV`, `SIGBUS`, `art::ThrowOutOfMemoryError`, and ANRs.
- **Structured Reporting**: Exports per-device JSON reports (`build/e2e-reports/<serial>_report.json`) and a combined markdown summary matrix (`build/e2e-reports/fleet_summary.md`).
- **Windows CP1252 / UTF-8 Resilience**: Explicitly reconfigures stdout/stderr to UTF-8 with defensive subprocess timeouts on all ADB and Gradle commands.

---

## 6. How to Add New Interview Audio Turns

1. **Add Turn Configuration** in `scripts/generate_ground_truth.py`:
   ```python
   {
       "turnId": "turn_03_architecture",
       "filename": "turn_03_architecture.wav",
       "topic": "System Architecture (Clean Architecture & MVI)",
       "voice": "en-US-JennyNeural",
       "script": "How do you structure unidirectional data flow in MVI architecture on Android?",
       "mustContainHotwords": ["architecture", "flow", "unidirectional"],
       "expectedLlmKeywords": ["intent", "state", "reducer", "immutable"],
       "maxAllowedWer": 0.15
   }
   ```
2. **Run Generator**:
   ```powershell
   python scripts/generate_ground_truth.py --generate-tts
   ```
3. **Verify Asset Alignment**:
   ```powershell
   python scripts/generate_ground_truth.py --validate-only
   ```
4. **Run Parameterized E2E Suite**:
   ```powershell
   python scripts/run_e2e_fleet.py
   ```
   The parameterized test will automatically discover `turn_03_architecture` and execute it as an individual JUnit test case.

