# AGENTS.md — AI Coding Agent Architecture & Context Guide 🤖

This document serves as the authoritative technical context guide for AI Coding Agents (Claude, Gemini, Cursor, Copilot, ChatGPT) working on this codebase.

---

## 1. System Design & Core Architectural Rules

### A. JNI Thread Serialization Rule (CRITICAL)
- **Constraint:** LiteRT-LM (`com.google.ai.edge.litertlm`) JNI C++ functions (`Engine.initialize()`, `Engine.createConversation()`, `Conversation.sendMessageAsync()`, `Conversation.cancelProcess()`, and `Conversation.close()`) are NOT thread-safe for concurrent calls.
- **Rule:** ALL LiteRT operations in [`LiteRtGemmaEngine.kt`](file:///app/src/main/java/com/local/interviewassistant/data/llm/LiteRtGemmaEngine.kt) and [`GalleryLiteRtBackend.kt`](file:///app/src/main/java/com/local/interviewassistant/data/llm/GalleryLiteRtBackend.kt) MUST be serialized on a dedicated single-threaded dispatcher (`LiteRT-GPU-Thread` created via `Executors.newSingleThreadExecutor()`). Never invoke JNI methods on `Dispatchers.Main` or concurrent IO pools.

### B. Hardware Delegation & Model Signature Rules
1. **Pixel Tensor TPU Models (`*_Google_Tensor_*.litertlm`):**
   - Must use `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` as primary backend.
   - Must pass `maxNumTokens = null` during `EngineConfig` creation to preserve baked-in AOT EdgeTPU tensor shape dimensions.
   - Must pass `samplerConfig = null` in `ConversationConfig` so LiteRT JNI uses the model's baked-in EdgeTPU sampler graph.
2. **Standard GPU Models (`gemma-4-E2B-it.litertlm`):**
   - Must use `Backend.GPU()` (OpenCL/Vulkan delegate) with `Backend.CPU()` fallback.
   - Uses `maxNumTokens = 2048` for dynamic sequence context allocation.

### C. Fail-Safe Fallback Contract
- If a TPU model fails NPU tensor allocation (e.g., missing vendor driver on non-Pixel device or emulator), [`GalleryLiteRtBackend`](file:///app/src/main/java/com/local/interviewassistant/data/llm/GalleryLiteRtBackend.kt) automatically checks if `gemma-4-E2B-it.litertlm` (GPU variant) exists on disk and seamlessly initializes `gemma-4-E2B-it.litertlm` on `Backend.GPU()`.

---

## 2. Core Subsystems & Key Classes

### 1. `AppContainer.kt` & `AppConfig.kt`
- **`AppContainer`**: Singleton DI container initialized with application context.
- **`AppConfig`**: Manages model storage paths (`modelsRootDir`), URL constants, model validation reports, and Gemma prompt formatting (`formatGemmaPrompt`).
- **`ensureHotwordsFile()`**: Generates `hotwords.txt` containing 70+ technical, behavioral, business, finance, and medical terms for Sherpa-ONNX beam search biasing.

### 2. Audio Capture & STT Pipeline
- **`MicrophoneAudioCaptureSource`**: Captures 16 kHz MONO PCM-16 float chunks and attaches Android hardware `NoiseSuppressor`, `AutomaticGainControl`, and `AcousticEchoCanceler` to `audioSessionId`.
- **`SherpaOnnxRuntimeBackend`**: Wraps native `com.k2fsa.sherpa.onnx.Vad` (Silero VAD) and `com.k2fsa.sherpa.onnx.OnlineRecognizer` (Zipformer transducer). Uses `decodingMethod = "greedy_search"` with native endpointing enabled.
- **`SherpaVadSttEngine`**: 32ms audio frame window manager, tracking speech/silence frames and emitting completed `TranscriptSegment` instances on utterance endpointing.

### 3. LLM Engine & Backend
- **`GalleryLiteRtBackend`**: Manages LiteRT `Engine` and `Conversation` sessions, supporting NPU and GPU delegation with clean synchronous resource teardown (`closeSynchronously()`).
- **`LiteRtGemmaEngine`**: Wraps backend generation into a Kotlin `Flow<LlmStreamEvent>` emitting `PartialToken`, `Completed`, and `Error` events with TTFT latency tracking.

### 4. Service & Pipeline Coordinator
- **`AssistantPipelineCoordinator`**: State machine driving Audio -> STT -> Question Detection -> Gemma Stream -> Copilot UI state.
- **`AssistantForegroundService`**: Android Foreground Service displaying ongoing notification and managing `WindowManager` overlay views.
- **`FloatingHudOverlay`**: Draggable floating Compose window featuring live transcript history, quick-action mode chips, and response panel.

---

## 3. Adding New Features or Refactoring Guide

1. **Adding New Category Chips:**
   - Update `InterviewCategory` in [`CopilotUiState.kt`](file:///app/src/main/java/com/local/interviewassistant/core/model/CopilotUiState.kt).
   - Add prompt directives in [`LlmGenerationConfig.kt`](file:///app/src/main/java/com/local/interviewassistant/core/model/LlmGenerationConfig.kt).
   - The chip will automatically appear in [`FloatingHudOverlay.kt`](file:///app/src/main/java/com/local/interviewassistant/ui/overlay/FloatingHudOverlay.kt).

2. **Testing:**
   - Always run `./gradlew testDebugUnitTest` to verify unit test assertions after modifying domain models or pipeline state logic.
