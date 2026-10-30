# AGENTS.md — AI Coding Agent Architecture & Context Guide 🤖

This document serves as the authoritative technical context guide for AI Coding Agents (Claude, Gemini, Cursor, Copilot, ChatGPT) working on this codebase.

---

## 1. System Design & Core Architectural Rules

### A. JNI Thread Serialization Rule (CRITICAL)
- **Constraint:** LiteRT-LM (`com.google.ai.edge.litertlm`) JNI C++ functions (`Engine.initialize()`, `Engine.createConversation()`, `Conversation.sendMessageAsync()`, `Conversation.cancelProcess()`, and `Conversation.close()`) are NOT thread-safe for concurrent calls.
- **Rule:** ALL LiteRT operations in [`LiteRtGemmaEngine.kt`](file:///app/src/main/java/com/local/interviewassistant/data/llm/LiteRtGemmaEngine.kt) and [`GalleryLiteRtBackend.kt`](file:///app/src/main/java/com/local/interviewassistant/data/llm/GalleryLiteRtBackend.kt) MUST be serialized on a dedicated single-threaded dispatcher (`LiteRT-GPU-Thread` created via `Executors.newSingleThreadExecutor().asCoroutineDispatcher()`).
  - **Generation:** Use `.flowOn(liteRtDispatcher)` to confine downstream native emissions.
  - **Synchronous / Suspending Actions:** Use `initializeSuspend()` and `closeSuspend()` (`withContext(liteRtDispatcher)`) from coroutine callers; synchronous methods like `close()` or `initialize()` route to the accelerator thread using `kotlinx.coroutines.runBlocking(liteRtDispatcher) { ... }`. Never invoke JNI methods directly on `Dispatchers.Main` or concurrent IO pools.

### B. Hardware Delegation & Model Signature Rules
1. **Pixel Tensor TPU Models (`*_Google_Tensor_*.litertlm`):**
   - **NPU Dispatch Library (CRITICAL):** LiteRT-LM requires `libLiteRtDispatch_GoogleTensor.so` inside `context.applicationInfo.nativeLibraryDir`. The Maven artifact `litertlm-android` does NOT package this library; it is bundled in `app/src/main/jniLibs/arm64-v8a/` and extracted via `packaging { jniLibs { useLegacyPackaging = true } }`.
   - Must use `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` as primary backend.
   - Must pass `maxNumTokens = null` during `EngineConfig` creation to preserve baked-in AOT EdgeTPU tensor shape dimensions.
   - Must pass `samplerConfig = null` in `ConversationConfig` so LiteRT JNI uses the model's baked-in EdgeTPU sampler graph.
   - File size for Tensor G5 is ~3.11 GB (3,113,545,589 bytes). AOT TPU graphs cannot execute on GPU/CPU backends.
2. **Standard GPU Models (`gemma-4-E2B-it.litertlm`):**
   - Must use `Backend.GPU()` (OpenCL/Vulkan delegate) with `Backend.CPU()` fallback.
   - Uses `maxNumTokens = 2048` for dynamic sequence context allocation. (CRITICAL: Do NOT pass `config.maxTokens` to `EngineConfig.maxNumTokens` for GPU models, as this causes a dimension mismatch and results in `Input tensor not found` exceptions.)
   - File size is ~2.58 GB (2,588,147,712 bytes).

### C. Fail-Safe Fallback Contract & Error Handling
- **Catch `Throwable`:** Native `Engine.initialize()` operations must catch `Throwable` (not just `Exception`) to intercept JNI `LinkageError` and `UnsatisfiedLinkError` failures without crashing or bypassing fallbacks.
- **Error Accumulation:** Detailed error traces per backend (`NPU`, `GPU`, `CPU`) are preserved in `LiteRtGemmaEngine` for actionable debugging.
- **Masquerade Protection:** If a TPU model fails NPU allocation, the GPU fallback verifies that the fallback file on disk is an authentic GPU model (different size and name), preventing redundant failure loops when files are mislabeled.
- **Dynamic System Prompt:** If `systemPrompt` is omitted in `LiteRtGemmaEngine`, `generateAnswerStream()` automatically injects `config.llmConfig.value.promptStyle.systemInstruction` and `interviewContext`.

---

## 2. Core Subsystems & Key Classes

### 1. `AppContainer.kt` & `AppConfig.kt`
- **`AppContainer`**: Singleton DI container initialized with application context and exposing supervisor `appScope` for non-blocking background teardown. Implements `onTrimMemory(level)` dispatch.
- **`AppConfig`**: Manages model storage paths (`modelsRootDir`), URL constants, model validation reports, and Gemma prompt formatting (`formatGemmaPrompt`).
- **`ensureHotwordsFile()`**: Generates `hotwords.txt` containing 70+ technical, behavioral, business, finance, and medical terms formatted in lowercase matching Sherpa-ONNX's BPE vocabulary for `modified_beam_search` biasing.

### 2. Audio Capture & STT Pipeline (v1.3 - v1.8.0)
- **`MicrophoneAudioCaptureSource`**: Captures 16 kHz MONO PCM-16 float chunks. Bypasses aggressive hardware `NoiseSuppressor` and `AutomaticGainControl` by default to preserve unvoiced high frequencies (3 kHz – 8 kHz) essential for English consonant recognition.
- **`CircularFloatRingBuffer`**: Zero-allocation circular float ring buffer in `com.local.interviewassistant.data.audio` eliminating per-frame array allocation and `copyOfRange` GC churn during sustained 16 kHz audio streaming.
- **`VadSttBackend` / `SherpaOnnxRuntimeBackend`**: Wraps native `com.k2fsa.sherpa.onnx.Vad` (Silero VAD) and `com.k2fsa.sherpa.onnx.OnlineRecognizer` (Zipformer transducer).
  - Uses multi-corpus conversational model trained on **GigaSpeech + LibriSpeech** (`csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-21`, 10,000h+ conversational audio, ~187.8 MB encoder).
  - Uses `decodingMethod = "modified_beam_search"` with `maxActivePaths = 4`, `blankPenalty = 1.0f`, and contextual hotwords (`hotwordsScore = 2.0f`).
  - **BPE Unit Rule:** Explicitly sets `modelConfig.modelingUnit = "bpe"` and `modelConfig.bpeVocab = config.sttTokensFile.absolutePath` to enable native C++ hotword FST compilation without tokenizer mismatch warnings.
  - Employs a 250ms pre-roll rolling buffer to gate speech into the transducer, preventing silence and background noise from corrupting the neural network's recurrent left-context memory.
  - Flushes acoustic lookahead buffers on endpointing via `stream.inputFinished()` prior to final result extraction, and resets state natively via `rec.reset(stream)`.
- **`SherpaVadSttEngine` (Two-Pass Architecture)**:
  - **Pass 1 (Streaming):** 32ms audio frame window manager using `CircularFloatRingBuffer` and emitting low-latency partial word hypotheses to `partialTranscript` for the HUD overlay.
  - **Pass 2 (Endpointing):** Upon utterance endpointing, the accumulated audio frames (capped at 30s) are optionally rescored via `OfflineRescorerBackend`.
  - **Punctuation & Truecasing:** Every endpointed transcript is processed by `QuestionPunctuationEngine` before emission, ensuring technical acronyms (`API`, `REST`, `SQL`, `Kotlin`, `Room`, `MVVM`) and terminal `?` or `.` are restored.
  - **Low-Memory & Thermal Hooks:** `trimMemory()` immediately releases the offline Whisper rescorer model and clears utterance audio buffers; `setRescorerThrottled(true)` skips heavy second-pass rescoring during thermal surges.
- **`QuestionPunctuationEngine`**: Zero-overhead (<1 ms) deterministic grammar & casing engine. Restores proper capitalization, fixes colloquial contractions, truecases 100+ technical acronyms and domain keywords, and classifies questions to append `?` so `TranscriptSegment.isLikelyQuestion` is triggered deterministically.
- **`OfflineRescorerBackend` / `SherpaOfflineWhisperRescorer`**: Second-pass offline recognizer contract wrapping `com.k2fsa.sherpa.onnx.OfflineRecognizer` with `OfflineWhisperModelConfig` (int8 Whisper tiny.en). Falls back cleanly to Pass 1 streaming result if offline models are absent or disabled.
- **`ModelProvisioningManager`**: Automates speech model lifecycle with `.stt_version` tracking (`STT_MODEL_VERSION = "2023-06-21-gigaspeech"`). Provides `downloadOfflineRescorerModel()` for optional high-accuracy second-pass ASR rescoring.

### 3. Hardware Profiling, LLM Backend & Thermal Protection (v1.4 - v1.8.0)
- **`DeviceHardwareDetector`**: Probes device SoC model (`Build.SOC_MODEL`), total RAM, NPU system features, and presence of `libLiteRtDispatch_GoogleTensor.so` on Android 17 (API 37). Automatically classifies system into `HardwareTier.TPU`, `GPU`, or `CPU`, and evaluates `RamReadinessTier` (`OPTIMAL` >= 11.5 GB, `CONSTRAINED` 7–11.5 GB, `UNSUPPORTED` < 7 GB).
- **`BackendResolver`**: Negotiates LiteRT delegates based on user preference (`preferredDelegate`) and detected hardware capabilities (`NPU_GOOGLE_TENSOR`, `GPU_LITERT`, `CPU_MMAP`), providing ordered fallback sequences (`getFallbackSequence()`) consumed directly by `LiteRtGemmaEngine.initialize()`.
- **Thermal Throttling Protection**: `AssistantPipelineCoordinator` monitors `PowerManager.OnThermalStatusChangedListener`; under `THERMAL_STATUS_SEVERE` (3) or higher, caps generation at 256 tokens and informs `SherpaVadSttEngine` to bypass Whisper rescoring.
- **`GalleryLiteRtBackend`**: Manages LiteRT `Engine` and `Conversation` sessions, supporting NPU, GPU, and CPU delegation with suspending `initializeSuspend()` / `closeSuspend()` and clean synchronous resource teardown (`closeSynchronously()`).
- **`LiteRtGemmaEngine`**: Wraps backend generation into a Kotlin `Flow<LlmStreamEvent>` emitting `PartialToken`, `Completed`, and `Error` events with TTFT latency tracking, dynamic system prompt fallback, and injectable `customBackend` support for unit testing.
- **Native Shims (`libLiteRtDispatch_GoogleTensor.so`)**: Placed in `app/src/main/jniLibs/arm64-v8a/` to enable hardware-accelerated EdgeTPU execution on Tensor G3/G4/G5 SoCs.

### 4. Service & Pipeline Coordinator
- **`AssistantPipelineCoordinator`**: Mutex-synchronized (`lifecycleMutex`) state machine driving Audio -> STT -> Question Detection -> Gemma Stream -> Copilot UI state.
- **`AssistantForegroundService`**: Android Foreground Service (`foregroundServiceType="microphone"`) with `PARTIAL_WAKE_LOCK` audio capture lock, ongoing notification with accessible `"Stop Assistant"` action, non-blocking FGS-priority teardown via `AppContainer.appScope`, and `WindowManager` overlay views.
- **`ModelDownloadService`**: Foreground service (`FOREGROUND_SERVICE_TYPE_DATA_SYNC` + partial `WakeLock`) enabling uninterrupted background downloads of multi-gigabyte models when the screen turns off or the phone is locked.
- **`FloatingHudOverlay`**: Draggable floating Compose window featuring live transcript history, quick-action mode chips, response panel, and WCAG/Google Play 48x48dp compliant hit targets with screen reader semantics.

### 5. MVVM Presentation & Jetpack Storage Subsystems (v1.8.0)
- **`PrompterViewModel` & `SettingsViewModel`**: Decouple the UI layer from service lifecycles and background singletons. Expose reactive, immutable `StateFlow` (`PrompterUiState`, `SettingsUiState`) consumed by Compose screens. Eliminated 25-parameter prop drilling across `MainActivity` and `AppShell`.
- **`PrompterScreen` Decomposition**: Split 874-line monolithic screen into focused, isolated composables in `com.local.interviewassistant.ui.prompter.components`: `PrompterHeader`, `CategoryChipRow`, `TeleprompterDisplay`, `LiveTranscriptBar`, `TranscriptHistorySheet`, `ThumbControlDock`, and `StealthDimmerDialog`.
- **`AppDatabase` & `InterviewSessionDao` (Room Database)**: Replaces flat-file JSON persistence (`interview_session_history.json`). Backed by Android Room 2.8.5 with automatic schema validation, indexed timestamps, and reactive `Flow<List<InterviewSessionEntity>>` streams. `SessionHistoryRepository` performs atomic one-time migration and cleanup of legacy JSON records.
- **`UserPreferencesRepository` (Jetpack DataStore)**: Replaces synchronous, blocking `SharedPreferences` with asynchronous, non-blocking `DataStore<Preferences>`, configured with `SharedPreferencesMigration(context, "llm_prefs")`.

### 6. Testing & Verification Harness (v1.8.0)
- **Headless Test Mock Suite (`FakeLlmBackend` in `com.local.interviewassistant.test`)**: Emulates LiteRT C++ generation streams for continuous integration runners without downloading multi-gigabyte model weights.
- **`Phase2StabilityAndThermalTest`**: Asserts thermal status transitions (SEVERE/CRITICAL -> LIGHT), token truncation at 256 tokens, and memory trim cache reclamation.
- **`RealDevicePipelineIntegrationTest`**: Grounded test suite verifying physical device specs (Pixel 10 Pro XL, Tensor G5, API 37), on-disk model layouts, sub-millisecond punctuation latency (<0.25ms), Sherpa-ONNX streaming Zipformer frame processing (<8ms), LiteRT EdgeTPU NPU execution via `libLiteRtDispatch_GoogleTensor.so`, and background service contracts.
- **Production Readiness Blueprint (`docs/production_readiness_plan.md`)**: Authoritative roadmap and specification for Phase 1–4 production hardening, Google Play Store compliance, ASO strategy, and Stealth OLED brand assets.

---

## 3. Adding New Features or Refactoring Guide

1. **Adding New Category Chips:**
   - Update `InterviewCategory` in [`CopilotUiState.kt`](file:///app/src/main/java/com/local/interviewassistant/core/model/CopilotUiState.kt).
   - Add prompt directives in [`LlmGenerationConfig.kt`](file:///app/src/main/java/com/local/interviewassistant/core/model/LlmGenerationConfig.kt).
   - The chip will automatically appear in [`FloatingHudOverlay.kt`](file:///app/src/main/java/com/local/interviewassistant/ui/overlay/FloatingHudOverlay.kt).

2. **Testing:**
   - Always run `./gradlew testDebugUnitTest` to verify unit test assertions after modifying domain models or pipeline state logic.

---

## 4. Version Control & Release Strategy 🌿

This project follows a **Two-Branch (`main` + `dev`) GitFlow with Annotated Git Tags & Semantic Versioning**.

### A. Branch Responsibilities
- **`main` (Production / Stable):**
  - Always clean, compilable, and tested.
  - No incomplete or experimental features are committed directly to `main`.
  - Every release cut on `main` receives an annotated Git tag (e.g., `v1.7.0`).
- **`dev` (Active Development & Integration):**
  - Daily working branch for new features, experiments, model adjustments, and bug fixes.
  - Tested on-device or via unit tests before merging into `main`.

### B. Release Workflow Checklist
When cutting a new release (e.g., `v1.7.0`):
1. **Merge `dev` into `main`:**
   ```bash
   git checkout main
   git merge dev
   ```
2. **Bump Android Version & Update Changelog:**
   - Update `versionCode` and `versionName` in [`app/build.gradle.kts`](file:///app/build.gradle.kts).
   - Document new additions, changes, fixes, and verification in [`CHANGELOG.md`](file:///CHANGELOG.md).
   ```bash
   git add app/build.gradle.kts CHANGELOG.md
   git commit -m "chore(release): bump version to v1.7.0"
   ```
3. **Create Annotated Git Tag & Push:**
   ```bash
   git tag -a v1.7.0 -m "Release v1.7.0: <Summary of changes>"
   git push origin main --tags
   ```
4. **Sync Back to `dev`:**
   ```bash
   git checkout dev
   git merge main
   git push origin dev
   ```
