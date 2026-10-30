# Changelog 📋

All notable changes to the **AI Interview Assistant** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.8.0] - 2026-10-30

### 🏛️ Phase 3 Production Hardening: MVVM Architecture, Room Database, Jetpack DataStore, Adaptive Icons & Play Store Dossier
- **MVVM Presentation Layer Decomposition (`PrompterViewModel`, `SettingsViewModel`, `PrompterScreen`):**
  - Eliminated the 25-parameter prop drilling anti-pattern from `MainActivity.kt` and `AppShell.kt`.
  - Decomposed the monolithic 874-line `PrompterScreen.kt` into 7 modular, focused components under `ui/prompter/components/` (`PrompterHeader`, `CategoryChipRow`, `TeleprompterDisplay`, `LiveTranscriptBar`, `TranscriptHistorySheet`, `ThumbControlDock`, and `StealthDimmerDialog`).
  - Implemented `PrompterViewModel` and `SettingsViewModel` managing immutable `StateFlow` UI state and decoupled from direct service references.
- **Jetpack Room Database Migration (`SessionHistoryRepository` & `data/history/db/`):**
  - Migrated interview session persistence from unindexed raw JSON file writes (`interview_session_history.json`) to Android Jetpack Room `2.8.5` (`AppDatabase`, `InterviewSessionDao`, `InterviewSessionEntity`).
  - Added atomic one-time migration and cleanup of legacy JSON session records.
  - Room DAO exposes reactive `Flow<List<InterviewSessionEntity>>` for real-time history observation with indexed queries.
- **Jetpack DataStore Migration (`UserPreferencesRepository` & `data/preferences/`):**
  - Migrated configuration persistence from blocking, synchronous `SharedPreferences` ("llm_prefs") to asynchronous Jetpack `DataStore<Preferences>`.
  - Configured automatic `SharedPreferencesMigration` with reactive updates exposed to `AppContainer.llmConfig`.
- **Adaptive Vector Icon Suite (`res/drawable/` & `res/mipmap-anydpi/`):**
  - Created high-contrast, store-ready adaptive vector assets: `ic_launcher_background.xml` (OLED linear gradient `#06070A` to `#0E1017`), `ic_launcher_foreground.xml` (The Guardian Node shield + audio soundwave/spark fusion with glowing neon core), and `ic_launcher_monochrome.xml` for Android 13+ Themed Icons.
  - Linked monochrome layer in `res/mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`.
- **Phase 2 Audit Remediation (REC-1 to REC-4):**
  - *REC-1 (Dynamic In-Flight Thermal Throttling):* Added in-flight thermal evaluation in `AssistantPipelineCoordinator` token generation loop to cap tokens immediately if thermal status escalates mid-turn.
  - *REC-2 (Rescorer Model Restoration):* Added `suspend fun restoreRescorerIfReleased()` to `SherpaVadSttEngine` to lazily restore Whisper rescorer once memory pressure abates.
  - *REC-3 (Paused State WakeLock Management):* In `AssistantForegroundService`, released `PARTIAL_WAKE_LOCK` during `AssistantState.Paused` and re-acquired upon resume to eliminate idle battery drain.
  - *REC-4 (Overlay Accessibility Semantics):* Added explicit screen reader accessibility labels (`contentDescription`) across all footer buttons in `FloatingHudOverlay.kt`.
- **Play Console Dossier & Submission Package (`docs/play_store_dossier.md`):**
  - Authored comprehensive Play Console submission dossier: FGS Microphone permission policy declaration, 15-second demonstration video storyboard, full store listing copy, ASO strategy, zero-data Data Safety declarations, and 8-panel screenshot specifications.
- **Verification & Testing:**
  - 121/121 unit tests passed (`PrompterViewModelTest`, `SettingsViewModelTest`, `SessionHistoryRepositoryTest`, `UserPreferencesRepositoryTest`, `Phase2StabilityAndThermalTest`, `RealDevicePipelineIntegrationTest`).
  - R8 full-mode minification and resource shrinking verified via `./gradlew assembleRelease` (3m 18s).
- Bumped application `versionCode = 12` and `versionName = "1.8.0"`.

---

## [1.7.0] - 2026-10-29

### ⚡ Phase 2 Production Hardening: Stability, Memory Optimization, Thermal Throttling & Headless Testing Harness
- **Audio WakeLock Integration (`AssistantForegroundService`):** Acquired `PowerManager.PARTIAL_WAKE_LOCK` ("AIInterviewAssistant:AudioCaptureLock") in `AssistantForegroundService` to guarantee continuous, zero-dropout microphone capture and VAD processing when the device screen locks or turns off.
- **Low-Memory Protection Hooks (`MainActivity`, `AssistantForegroundService`, `AppContainer`, `SherpaVadSttEngine`):** Overrode `onTrimMemory(level)` across Android components; under system RAM pressure (`TRIM_MEMORY_RUNNING_LOW` and above), dynamically releases the heavy offline Whisper rescorer model and trims volatile UI transcript history to protect against Linux `lmkd` out-of-memory kills.
- **Thermal Throttling Protection (`AssistantPipelineCoordinator`, `AssistantForegroundService`, `SherpaVadSttEngine`):** Registered `PowerManager.OnThermalStatusChangedListener` to monitor device thermal health; under `THERMAL_STATUS_SEVERE` or higher, caps LLM generation to 256 tokens, skips intensive second-pass Whisper rescoring, and reflects a visible `"THROTTLED"` badge in the HUD overlay.
- **Google Play Accessibility 48x48dp Compliance (`FloatingHudOverlay` & `InfoScreen`):** Upgraded all interactive controls (minimized pill, header minimize/close icons, quick category chips, action buttons, permission triggers, and diagnostic buttons) to meet the 48x48 dp minimum touch target standard with explicit semantics `contentDescription` for screen readers.
- **Headless Test Mock Suite (`FakeLlmBackend` & `Phase2StabilityAndThermalTest`):** Built a standalone `FakeLlmBackend` implementing `LlmInferenceBackend` for zero-overhead continuous integration (CI) environments without requiring 3GB model binaries, along with automated tests verifying thermal listeners, low-memory hooks, and token capping.
- **Physical Device Deployment & Verification:** Assembled, installed, and verified on connected physical Google Pixel 10 Pro XL (Tensor G5, Android 17 / API 37) with clean process startup (PID 9584).
- Bumped application `versionCode = 11` and `versionName = "1.7.0"`.

---

## [1.6.0] - 2026-10-29

### 🛡️ Phase 1 Production Hardening: R8/ProGuard Rules, Non-Blocking Teardown, Auto-Backup Privacy & Zero-Allocation Audio Ring Buffer
- **Production ProGuard / R8 Rules & Shrinking (`app/proguard-rules.pro` & `app/build.gradle.kts`):** Authored R8 full-mode keep rules preserving `com.google.ai.edge.litertlm` and `com.k2fsa.sherpa.onnx` native JNI bindings, enum class and member retention (`InterviewCategory.valueOf`), domain models, and coroutine dispatchers; enabled `isMinifyEnabled = true` and `isShrinkResources = true` for release builds.
- **Release Signing Pipeline (`app/build.gradle.kts` & `.gitignore`):** Added release keystore configuration reading from `keystore.properties` or environment variables (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) with V2/V3/V4 signing enabled.
- **Mutex-Guarded Lifecycle & Non-Blocking FGS Teardown (`AssistantPipelineCoordinator` & `AssistantForegroundService`):** Serialized `start()` and `stop()` via `lifecycleMutex`, added accessible `"Stop Assistant"` notification action (`PendingIntent.FLAG_IMMUTABLE`), and refactored teardown onto `AppContainer.appScope` (`withContext(NonCancellable)`) while retaining `FOREGROUND_SERVICE` priority until JNI cleanup finishes.
- **Google Play FGS Manifest Alignment (`AndroidManifest.xml`):** Removed uninvoked `|dataSync` type from `AssistantForegroundService` so it strictly declares `android:foregroundServiceType="microphone"`.
- **Android Auto-Backup Cloud Leak Prevention (`data_extraction_rules.xml` & `backup_rules.xml`):** Configured cloud backup and device transfer exclusion rules for `interview_session_history.json` and `models/` to uphold the 100% air-gapped zero-cloud privacy guarantee.
- **Non-Blocking Coroutine Backend Hooks (`LlmInferenceBackend`, `GalleryLiteRtBackend`, `LiteRtGemmaEngine`):** Added `initializeSuspend()` and `closeSuspend()` routing directly via `withContext(liteRtDispatcher)` without blocking caller threads.
- **3-Tier RAM Qualification Gate (`DeviceHardwareDetector`):** Added `RamReadinessTier` (`OPTIMAL` >= 11.5 GB, `CONSTRAINED` 7–11.5 GB, `UNSUPPORTED` < 7 GB) and memory pressure advisories to `HardwareProfile`.
- **Zero-Allocation Audio Frame Buffering (`CircularFloatRingBuffer` & `SherpaVadSttEngine`):** Replaced per-frame `FloatArray` reallocation and `copyOfRange` slicing in `SherpaVadSttEngine.processAudioChunk()` with a reusable `CircularFloatRingBuffer` and pre-allocated window buffer.
- Bumped application `versionCode = 10` and `versionName = "1.6.0"`.

---

## [1.5.1] - 2026-10-29

### 🛠️ Backend Fallback Wiring, Static Analysis Hardening, UI Refinements & Production Readiness Plan
- **Production Readiness Plan & Roadmap (`docs/production_readiness_plan.md`):** Authored comprehensive 1,685-line production engineering audit, Google Play Store compliance blueprint (Target SDK 37, 16 KB ELF segment alignment audit, FGS declarations, zero-data Data Safety), competitive benchmarking against *InterviewMan*, ASO metadata specification, Stealth OLED brand identity system, and a 4-phase execution roadmap.
- **Dynamic Backend Fallback Sequencing (`BackendResolver` & `LiteRtGemmaEngine`):** Wired `BackendResolver.getFallbackSequence()` directly into `LiteRtGemmaEngine.initialize()` and aligned `GalleryLiteRtBackend` (`CPU_MMAP` support and custom backend injection for deterministic unit testing).
- **OLED Stealth Theme & Teleprompter Polish:** Refined `StealthTheme` 4-size/2-weight typography scale, `FloatingHudOverlay` 44dp touch targets and drop shadow spacing, `PrompterScreen` layout hierarchy, and `FormattedTeleprompterAnswer` Compose modifier parameter ordering.
- **Compose Observable Locale & Android 17 Lint Compliance:** Replaced static `Locale.getDefault()` calls in `UsageScreen` with reactive `ConfigurationCompat.getLocales(LocalConfiguration.current)`, cleaned up redundant null/elvis checks on non-null `Build.SOC_MODEL` / `Build.SOC_MANUFACTURER` constants in `DeviceHardwareDetector` and `HardwareUtils`, and enforced `Dispatchers.IO` confinement in `ModelProvisioningManager.downloadFileWithResume()`.
- **Release Log Guarding & Test Suite Expansion:** Guarded diagnostic `Log.i` calls with `BuildConfig.DEBUG` in `LitertlmInferenceMetricsTracker` and `MemoryMonitor`, removed duplicate `"IPC"` entry in `QuestionPunctuationEngine`, migrated test runner dependencies to `gradle/libs.versions.toml`, and expanded `Phase4LlmEngineTest` to verify custom backend injection and error propagation.
- Bumped application `versionCode = 9` and `versionName = "1.5.1"`.

---

## [1.5.0] - 2026-10-28

### 🚀 Stealth Teleprompter Redesign, Real-Time Streaming Auto-Scroll & Settings Context Preservation
- **Stealth Teleprompter & Multi-Screen App Architecture:** Introduced modern tabbed `AppShell` with Prompter, Info, Usage, and Settings navigation with sleek stealth dark UI themes.
- **Dynamic Streaming Auto-Scroll HUD & Prompter:** Added intelligent `autoFollowStream` scrolling in `FloatingHudOverlay` and `PrompterScreen` that keeps up with token generation while respecting manual user scrolling inspection.
- **Preset vs Custom Interview Context Preservation:** Updated `AssistantPipelineCoordinator` and `SettingsScreen` to preserve custom user interview contexts across category chip selections while dynamically loading presets when context is blank or default.
- **Enhanced Context Input UX:** Expanded interview context text field with sentence capitalization, multi-line auto-grow, and smooth two-way state binding.
- Bumped application `versionCode = 8` and `versionName = "1.5.0"`.

---

## [1.4.0] - 2026-10-28

### ⚡ Dynamic Hardware Detection, Intelligent Backend Routing & Subfolder Organization
- **`DeviceHardwareDetector` Hardware Profiling:** Probes system SoC (`Build.SOC_MODEL`), device model, RAM threshold, NPU system features, and native Tensor dispatch library presence (`libLiteRtDispatch_GoogleTensor.so`) on Android 17 (API 37). Automatically classifies devices into `TPU`, `GPU`, or `CPU` tiers with tailored delegate configurations.
- **Dynamic Backend Routing (`BackendResolver`):** Intelligently negotiates LiteRT acceleration tiers based on user preferences and hardware capabilities (`NPU_GOOGLE_TENSOR`, `GPU_LITERT`, or `CPU_MMAP`), with clean fallbacks and informative diagnostic logging.
- **Hierarchical Model Subfolder Structure (`AppConfig`):** Upgraded model layout from flat root storage to organized domain subfolders (`vad/`, `asr/`, `whisper/`, `llm/`) while maintaining backward-compatible fallbacks for existing on-disk models.
- **Unified One-Tap Model Provisioning:** Streamlined model downloading to provision all required models (VAD, ASR streaming Zipformer, Whisper offline rescorer, and recommended LiteRT LLM) in a single workflow with metered network warnings and robust progress reporting.
- **Modern Dashboard UI (Android 17 Adaptive Layout):** Refreshed Compose dashboard with responsive layout constraints (max 640.dp width), live hardware capability chips, real-time download status, and delegate selector.
- Bumped application `versionCode = 7` and `versionName = "1.4.0"`.

---

## [1.3.2] - 2026-10-25

### 🚀 Background Model Provisioning Service (Lock Screen & Screen-Off Persistence)
- **`ModelDownloadService` Foreground Service:** Created dedicated `LifecycleService` running with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` and a partial `WakeLock` to guarantee uninterrupted background model downloads when the user locks their phone or turns off the screen.
- **Persistent Notification with Progress Bar:** Displays real-time download status, current model name, downloaded MB / total MB, progress percentage, and an in-notification "Cancel" action.
- **Auto-Sync with Compose Dashboard:** Seamlessly drives `ModelProvisioningManager` so the in-app `DashboardScreen` UI and notification stay 100% in sync without polling or dropped states.
- **Tensor TPU Dynamic Model Resolution:** Updated `AppConfig.activeLlmModelFile` to prioritize on-disk `tpuLlmModelFile` (`gemma-4-E2B-it_Google_Tensor_G5.litertlm`) dynamically on Google Pixel Tensor hardware.
- **Real-Device Instrumentation Test Suite (`RealDevicePipelineIntegrationTest`):** 7/7 tests verified live on physical Pixel 10 Pro XL (Tensor G5, Android 17), including sub-millisecond punctuation latency (<0.25ms), streaming Zipformer STT frame processing (<8ms), LiteRT EdgeTPU NPU execution via `libLiteRtDispatch_GoogleTensor.so`, and background service contracts.
- Bumped application `versionCode = 6` and `versionName = "1.3.2"`.

---

## [1.3.1] - 2026-10-25

### 🧪 Edge-Case Test Suite, Latency Profiling & On-Device Verification
- **Edge-Case Audio & STT Hardening:** Added 23 unit tests in `Phase3AudioSttTest` covering sub-window and super-window audio chunks (100, 25, 1337 samples), continuous silence streaming, hesitation reset, short utterance filtering (<12 chars), and buffer overflow protection.
- **Component Latency Profiling:** Validated strict latency budgets in `ComponentLatencyBenchmarkTest`: audio normalization (<2 ms), VAD frame processing (<2 ms avg, <5 ms p95), endpointing-to-emission (<10 ms), and punctuation restoration (<1 ms across 1,000 sentences).
- **LLM Inference Tracker & Profiling:** Integrated `LitertlmInferenceMetricsTracker` validating TTFT, decode tokens/sec, and token streaming overhead (<2 µs).
- **On-Device Instrumentation & Deployment:** Added `androidx.test.runner` to `app/build.gradle.kts` for physical Pixel 10 Pro XL (Tensor G5 SoC) testing and added live version indicator (`v1.3.1 (Build 5)`) to `DashboardScreen`.
- Bumped application `versionCode = 5` and `versionName = "1.3.1"`.

---

## [1.3.0] - 2026-10-25

### ⚡ Phase 3: Two-Pass Hybrid ASR & Technical Question Punctuation Engine

#### Added
- **`QuestionPunctuationEngine` (On-Device Truecasing & Punctuation Restoration):**
  - Instantaneous (<1 ms), zero-latency grammatical truecasing engine.
  - Automatically restores sentence initial capitalization and sentence-boundary capitalization.
  - Case-preservation and normalization for **100+ technical acronyms and domain terms** (`API`, `REST`, `SQL`, `JSON`, `HTTP`, `HTTPS`, `Kotlin`, `Room`, `Retrofit`, `Hilt`, `Dagger`, `Coroutines`, `StateFlow`, `SharedFlow`, `ViewModel`, `Compose`, `MVVM`, `MVI`, `CI/CD`, `Docker`, `K8s`, `Kafka`, `Redis`, `GraphQL`, `FastAPI`, `TTFT`, `FPS`, etc.).
  - Restores English contractions (`whats` -> `What's`, `dont` -> `don't`, `cant` -> `can't`, `wont` -> `won't`, `isnt` -> `isn't`).
  - Terminal punctuation restoration: Automatically detects questions vs declarative statements. Interrogative sentences and interview starters ("Can you explain...", "Tell me about...", "What is the difference between...", etc.) end with `?`, while statements end with `.`.
  - Guaranteed `TranscriptSegment.isLikelyQuestion` triggering: Because endpointed questions now reliably end in `?`, downstream question recognition and LLM prompt generation are 100% consistent.
- **`OfflineRescorerBackend` & `SherpaOfflineWhisperRescorer`:**
  - Pluggable second-pass ASR rescorer interface supporting offline int8 Whisper models (`tiny.en-encoder.int8.onnx`, `tiny.en-decoder.int8.onnx`, `tiny.en-tokens.txt`).
  - Utterance Audio Frame Accumulation: Utterance audio chunks are accumulated in a bounded 30s rolling buffer during speech and rescored upon endpointing if offline rescorer weights are present.
  - Zero-Overhead Streaming Fallback: If offline weights are not downloaded, the engine automatically falls back to Pass 1 streaming Zipformer output + `QuestionPunctuationEngine` with 0 ms latency penalty.
- **Optional Whisper Rescorer Provisioning:**
  - Added `downloadOfflineRescorerModel()` to `ModelProvisioningManager` with one-tap download button in `DashboardScreen`.
- **Comprehensive Phase 3 Test Suite:**
  - Added unit test coverage in `Phase3AudioSttTest` and `Phase7ProvisioningTest` validating truecasing, acronym capitalization, question punctuation, two-pass rescorer integration, fallback behavior, and buffer capping.

#### Changed
- Updated `SherpaVadSttEngine` to accumulate utterance frames, coordinate second-pass rescoring, and feed endpointed transcripts through `QuestionPunctuationEngine`.
- Updated `AppContainer.createSttEngine()` to instantiate `SherpaOfflineWhisperRescorer`.
- Updated `DashboardScreen` and `MainActivity` to expose Phase 3 STT Architecture details and optional Whisper Rescorer download.

---

## [1.2.0] - 2026-10-25

### 🧠 Phase 2: Conversational Multi-Corpus STT Model Upgrade (GigaSpeech + LibriSpeech)

#### Added
- **Conversational Speech Training Corpus:** Upgraded default streaming STT model from LibriSpeech-only (`2023-06-26`) to `csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-21`, trained on **GigaSpeech + LibriSpeech** (10,000+ hours of real-world conversational audio, interviews, YouTube talks, and podcasts; ~187.8 MB encoder).
- **Acoustic Diversity & Disfluency Robustness:** Drastically reduces Word Error Rate (WER) on natural English contractions ("gonna", "how'd", "we've"), colloquial conversational tempos, ambient noise, and diverse English accents.
- **Model Version Tracking & Auto-Migration:** Added `.stt_version` tracking in `ModelProvisioningManager` with `STT_MODEL_VERSION = "2023-06-21-gigaspeech"`. Automatically detects legacy unversioned or outdated speech models on device storage and performs clean automated replacement upon provisioning.
- **Native BPE Hotword FST Compilation:** Added explicit `modelingUnit = "bpe"` and `bpeVocab = tokens.txt` configuration to `OnlineModelConfig` in `VadSttBackend`, resolving native Sherpa-ONNX hotwords compilation warnings and enabling strict lowercase subword trie matching.

#### Changed
- Updated `AppConfig.STT_ENCODER_URL`, `STT_DECODER_URL`, `STT_JOINER_URL`, and `STT_TOKENS_URL` to point to the GigaSpeech-trained checkpoints on Hugging Face.
- Hardened `ModelProvisioningManager.downloadMissingSpeechModels()` and `downloadAllMissingModels()` to trigger automatic upgrades whenever `.stt_version` is absent or mismatched.
- Bumped application `versionCode = 3` and `versionName = "1.2"`.

#### Verified
- Verified on physical **Google Pixel 10 Pro XL** (Tensor G5 SoC):
  - 187.8 MB GigaSpeech `encoder.onnx` successfully downloaded, provisioned, and active.
  - Native Sherpa-ONNX streaming recognizer initialized with zero tokenizer/BPE warnings.
  - Concurrent LiteRT inference on Tensor G5 EdgeTPU NPU executing at ~20 tokens/sec with TTFT ~215 ms.
  - Floating HUD overlay live on Android WindowManager (`SYSTEM_ALERT_WINDOW`).

---

## [1.1.0] - 2026-10-24

### 🎙️ Speech Recognition (STT) & VAD Pipeline Overhaul

#### Added
- **Modified Beam Search (`modified_beam_search`):** Upgraded decoder search from `greedy_search` to `modified_beam_search` with `maxActivePaths = 4` and `blankPenalty = 1.0f`, allowing hypothesis path exploration and acoustic error recovery.
- **Dynamic Contextual Hotwords Biasing:** Connected `hotwords.txt` directly to `OnlineRecognizerConfig.hotwordsFile` with `hotwordsScore = 2.0f`.
- **Lowercase BPE Vocabulary Normalization:** Normalized 70+ technical, interview, and behavioral keywords to strictly lowercase matching Sherpa-ONNX's subword token trie, with an auto-refresh check that upgrades legacy uppercase hotword files on device.
- **Transducer Silence Gating & Pre-Roll Ring Buffer:** Added a 250ms (8-window) rolling pre-speech onset buffer. Quiescent silence frames and ambient room noise are strictly prevented from entering the streaming neural transducer, eliminating left-context recurrent memory corruption.
- **Lookahead Buffer Flushing (`stream.inputFinished()`):** Trailing speech frames trapped in the 320ms chunk encoder lookahead buffer are now explicitly drained upon speech endpointing before extracting the final question, eliminating truncated sentences.
- **Native JNI Stream Recycling:** Utterances now reset cleanly via `recognizer.reset(stream)` instead of tearing down and allocating JNI `OnlineStream` instances on every sentence.
- **Expanded Conversational Question Starters:** Added interview triggers to `TranscriptSegment` ("talk about", "share an example", "in your opinion", "tell me about", "what's", "how's").

#### Changed
- **Bypassed Destructive Hardware Audio Filters:** Bypassed Android hardware `NoiseSuppressor`, `AutomaticGainControl`, and `AcousticEchoCanceler` by default (`enableHardwareAudioEffects = false`). This restores pristine 3 kHz – 8 kHz high-frequency acoustic fidelity (fricatives and plosives like /s/, /t/, /th/, /k/, /p/) on `AudioSource.VOICE_RECOGNITION`.
- **Eliminated Faulty RMS Threshold:** Removed the arbitrary `rms > 0.015f` (-36.5 dBFS) override that previously bypassed Silero VAD silence detection in normal ambient rooms.
- **Transient Speech Frame Reset:** Added an automatic frame counter reset on silence if speech has not yet met the minimum speech duration threshold (250ms).
- Bumped application `versionCode = 2` and `versionName = "1.1"`.

---

## [1.0.0] - 2026-10-24

### 🚀 Initial Production Release

#### Added
- **On-Device LLM Copilot (Google LiteRT-LM Gemma 4 E2B IT):**
  - Southbound hardware acceleration for Google Pixel Tensor EdgeTPU (`Backend.NPU` on Tensor G3, G4, and G5).
  - Bundled `libLiteRtDispatch_GoogleTensor.so` native dispatch shim in `jniLibs/arm64-v8a/` to interface with the Pixel TPU HAL.
  - Universal GPU delegate fallback (`Backend.GPU`) using OpenCL and Vulkan for non-Tensor Android hardware.
  - Dedicated single-threaded executor (`LiteRT-GPU-Thread`) serializing all native LiteRT C++ calls to guarantee thread safety and prevent JNI memory corruption.
- **Real-Time Audio Capture & Streaming STT:**
  - 16 kHz mono PCM-16 float capture via Android `AudioRecord`.
  - Offline Silero VAD and Sherpa-ONNX Zipformer streaming STT.
- **Floating System Overlay HUD:**
  - System-wide draggable floating window (`TYPE_APPLICATION_OVERLAY`) with Compose UI.
  - Live partial transcript streaming and automated interview question detection.
  - One-tap quick-action category chips (*General*, *System Design*, *Algorithms*, *Behavioral STAR*, *QA / Code Review*) with runtime prompt style and domain adaptation.
  - Response panel displaying streaming token generation with live Time-To-First-Token (TTFT) metrics, manual force trigger ("Answer Now"), pause/resume, and reset.
- **Background Model Provisioning:**
  - Automated `WorkManager` download workers supporting resume, progress tracking, and Hugging Face authenticated token downloads for gated Gemma models.
