package com.local.interviewassistant.data.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.local.interviewassistant.core.model.AppConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LiteRtGemmaEngine(
    private val context: Context?,
    private val config: AppConfig,
    private val systemPrompt: String? = null,
    private val customBackend: LlmInferenceBackend? = null
) : LlmEngine {
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var engine: Engine? = null
    private var activeConversation: Conversation? = null

    // Track in-flight native C++ callback completion so we NEVER call Conversation.close()
    // while the LiteRT C++ worker thread is still executing a cancelled or completing turn.
    @Volatile
    private var activeCallbackLatch: CountDownLatch? = null
    private val generationIdCounter = AtomicInteger(0)

    private val liteRtDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LiteRT-GPU-Thread")
    }.asCoroutineDispatcher()

    private val engineMutex = Mutex()
    private val tracker = LitertlmInferenceMetricsTracker()

    override suspend fun initialize(): Result<Unit> = withContext(liteRtDispatcher) {
        engineMutex.withLock {
            try {
                if (customBackend != null) {
                    customBackend.initialize(config)
                    _isInitialized.value = true
                    return@withContext Result.success(Unit)
                }

                if (engine != null) {
                    return@withContext Result.success(Unit)
                }

                val appContext = context ?: throw IllegalStateException("Android Context required for native LiteRT initialization")

                val modelFile = config.activeLlmModelFile
                if (!modelFile.exists()) {
                    throw IllegalStateException("Model file not found")
                }

                val isTensorTpuModelFile = modelFile.name.contains("Tensor", ignoreCase = true) ||
                        modelFile.name.contains("tpu", ignoreCase = true) ||
                        modelFile.name.contains("npu", ignoreCase = true)

                val attemptBackends = BackendResolver.getFallbackSequence(
                    context = appContext,
                    isTensorTpuModelFile = isTensorTpuModelFile,
                    useGpuDelegate = config.useGpuDelegate,
                    preferredDelegate = config.preferredDelegate
                )

                var initialized = false
                val errors = mutableListOf<String>()
                var lastThrowable: Throwable? = null

                // LITERTLM TENSOR DIMENSION FIX
                // Ensure maxNumTokens = null for baked TPU models, and 2048 for CPU/GPU.
                // DO NOT pass config.maxTokens to EngineConfig.maxNumTokens for standard GPU models
                // to avoid "Input tensor not found" crashes.
                for ((backend, backendLabel) in attemptBackends) {
                    val needsMaxTokens = !isTensorTpuModelFile || (backend !is Backend.NPU)
                    val engineConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                        maxNumTokens = if (needsMaxTokens) 2048 else null,
                        cacheDir = appContext.cacheDir.absolutePath
                    )

                    val newEngine = Engine(engineConfig)
                    try {
                        newEngine.initialize()
                        engine = newEngine
                        Log.i("LiteRtGemmaEngine", "Successfully initialized LiteRT engine on backend '$backendLabel' for model '${modelFile.name}'")
                        initialized = true
                        break
                    } catch (t: Throwable) {
                        val errorDetail = "${backendLabel}: ${t.message ?: t.javaClass.simpleName}"
                        Log.w("LiteRtGemmaEngine", "Failed initialization on $errorDetail. Attempting next fallback...", t)
                        errors.add(errorDetail)
                        lastThrowable = t
                        try {
                            newEngine.close()
                        } catch (_: Throwable) {}
                    }
                }

                if (!initialized && isTensorTpuModelFile) {
                    val standardGpuModel = config.llmModelFile
                    val isDifferentGpuFile = standardGpuModel.exists() &&
                            standardGpuModel.absolutePath != modelFile.absolutePath &&
                            !standardGpuModel.name.contains("Tensor", ignoreCase = true) &&
                            !standardGpuModel.name.contains("tpu", ignoreCase = true) &&
                            standardGpuModel.length() != modelFile.length() &&
                            standardGpuModel.length() >= 100 * 1024 * 1024L

                    if (isDifferentGpuFile) {
                        try {
                            val fallbackEngineConfig = EngineConfig(
                                modelPath = standardGpuModel.absolutePath,
                                backend = Backend.GPU(),
                                maxNumTokens = 2048,
                                cacheDir = appContext.cacheDir.absolutePath
                            )
                            val newEngine = Engine(fallbackEngineConfig)
                            newEngine.initialize()
                            engine = newEngine
                            config.updateLlmConfig(config.llmConfig.value.copy(selectedModelPath = standardGpuModel.absolutePath))
                            Log.i("LiteRtGemmaEngine", "TPU model failed to allocate on NPU. Successfully fell back to standard GPU model '${standardGpuModel.name}' on GPU.")
                            initialized = true
                        } catch (t: Throwable) {
                            val msg = "Standard GPU Fallback (${standardGpuModel.name}): ${t.message ?: t.javaClass.simpleName}"
                            errors.add(msg)
                            lastThrowable = t
                            Log.e("LiteRtGemmaEngine", msg, t)
                        }
                    } else if (standardGpuModel.exists() && standardGpuModel.length() == modelFile.length()) {
                        errors.add("Standard GPU model file has identical size to TPU model (${standardGpuModel.name}). It cannot serve as GPU fallback.")
                    }
                }

                if (!initialized) {
                    val combinedMsg = if (errors.isNotEmpty()) {
                        "LLM failed to initialize on all backends: ${errors.joinToString(" | ")}"
                    } else {
                        "LLM Engine failed to initialize: ${lastThrowable?.message ?: "Unknown error"}"
                    }
                    throw IllegalStateException(combinedMsg, lastThrowable)
                }

                _isInitialized.value = true
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure((t as? Exception) ?: RuntimeException(t))
            }
        }
    }

    private fun awaitAndClosePreviousConversation() {
        val prevConv = activeConversation
        val prevLatch = activeCallbackLatch
        if (prevConv != null) {
            if (prevLatch != null && prevLatch.count > 0) {
                try {
                    prevConv.cancelProcess()
                } catch (_: Throwable) {}
                try {
                    // Wait on LiteRT-GPU-Thread for C++ worker thread (Thread-XX) to finish onDone/onError
                    // before destroying native Conversation memory!
                    prevLatch.await(3000, TimeUnit.MILLISECONDS)
                } catch (_: Throwable) {}
            }
            try {
                prevConv.close()
            } catch (_: Throwable) {}
            activeConversation = null
            activeCallbackLatch = null
        }
    }

    override fun generateAnswerStream(question: String): Flow<LlmStreamEvent> = callbackFlow {
        if (customBackend != null) {
            if (!_isInitialized.value) {
                trySend(LlmStreamEvent.Error(IllegalStateException("Engine not initialized")))
                close()
                return@callbackFlow
            }
            val formattedPrompt = config.formatGemmaPrompt(question)
            val genId = generationIdCounter.incrementAndGet()
            val callbackFinished = AtomicBoolean(false)
            tracker.startTracking()
            val accumulatedText = StringBuilder()

            customBackend.generateAsync(
                prompt = formattedPrompt,
                onPartialResult = { rawChunk, done ->
                    if (generationIdCounter.get() != genId || callbackFinished.get()) return@generateAsync
                    val partial = rawChunk.replace("<end_of_turn>", "")
                    if (partial.isNotEmpty()) {
                        tracker.recordToken()
                        accumulatedText.append(partial)
                        trySend(LlmStreamEvent.PartialToken(partial, accumulatedText.toString(), tracker.getTtftMs()))
                    }
                    if (done && callbackFinished.compareAndSet(false, true)) {
                        tracker.stopTracking()
                        tracker.logMetrics("LiteRtGemmaEngine")
                        context?.let { MemoryMonitor.logMemoryUsage(it, "LiteRtGemmaEngine") }
                        trySend(
                            LlmStreamEvent.Completed(
                                accumulatedText.toString().trim(),
                                tracker.getTtftMs(),
                                tracker.getTotalDurationMs(),
                                tracker.getDecodeTokensPerSecond(),
                                tracker.getTokenCount()
                            )
                        )
                        close()
                    }
                },
                onError = { throwable ->
                    if (callbackFinished.compareAndSet(false, true)) {
                        if (generationIdCounter.get() == genId) {
                            val msg = throwable.message ?: ""
                            if (!msg.contains("CANCELLED", ignoreCase = true)) {
                                trySend(LlmStreamEvent.Error(throwable))
                            }
                            close()
                        }
                    }
                }
            )

            awaitClose {
                if (!callbackFinished.get()) {
                    try {
                        customBackend.cancel()
                    } catch (_: Throwable) {}
                }
            }
            return@callbackFlow
        }

        if (!_isInitialized.value || engine == null) {
            trySend(LlmStreamEvent.Error(IllegalStateException("Engine not initialized")))
            close()
            return@callbackFlow
        }

        val currentEngine = engine!!
        val llmConfig = config.llmConfig.value
        val isTensorTpuModel = config.activeLlmModelFile.name.let {
            it.contains("Tensor", ignoreCase = true) || it.contains("tpu", ignoreCase = true) || it.contains("npu", ignoreCase = true)
        }

        val samplerConfig = if (isTensorTpuModel) {
            null
        } else {
            SamplerConfig(
                topK = llmConfig.topK,
                topP = 0.9,
                temperature = llmConfig.temperature.toDouble()
            )
        }

        val effectiveSystemPrompt = systemPrompt ?: run {
            val base = llmConfig.promptStyle.systemInstruction
            if (llmConfig.interviewContext.isNotBlank()) {
                "$base\nContext: ${llmConfig.interviewContext}"
            } else {
                base
            }
        }
        val sysPromptContents = effectiveSystemPrompt.ifBlank { null }?.let { SystemPromptHelper.createSystemPromptContents(it) }

        val conversationConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            systemInstruction = sysPromptContents
        )

        // Safely drain any previous in-flight conversation before closing its C++ pointer
        awaitAndClosePreviousConversation()

        val genId = generationIdCounter.incrementAndGet()
        val completionLatch = CountDownLatch(1)
        val callbackFinished = AtomicBoolean(false)
        activeCallbackLatch = completionLatch

        val newConversation = currentEngine.createConversation(conversationConfig)
        activeConversation = newConversation

        tracker.startTracking()
        val accumulatedText = StringBuilder()

        newConversation.sendMessageAsync(
            Contents.of(Content.Text(question)),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    if (generationIdCounter.get() != genId || callbackFinished.get()) return
                    val partial = message.toString().replace("<end_of_turn>", "")
                    if (partial.isNotEmpty()) {
                        tracker.recordToken()
                        accumulatedText.append(partial)
                        trySend(LlmStreamEvent.PartialToken(partial, accumulatedText.toString(), tracker.getTtftMs()))
                    }
                }

                override fun onDone() {
                    if (callbackFinished.compareAndSet(false, true)) {
                        completionLatch.countDown()
                        if (generationIdCounter.get() == genId) {
                            tracker.stopTracking()
                            tracker.logMetrics("LiteRtGemmaEngine")
                            context?.let { MemoryMonitor.logMemoryUsage(it, "LiteRtGemmaEngine") }
                            trySend(
                                LlmStreamEvent.Completed(
                                    accumulatedText.toString().trim(),
                                    tracker.getTtftMs(),
                                    tracker.getTotalDurationMs(),
                                    tracker.getDecodeTokensPerSecond(),
                                    tracker.getTokenCount()
                                )
                            )
                            close()
                        }
                    }
                }

                override fun onError(throwable: Throwable) {
                    if (callbackFinished.compareAndSet(false, true)) {
                        completionLatch.countDown()
                        if (generationIdCounter.get() == genId) {
                            val msg = throwable.message ?: ""
                            if (!msg.contains("CANCELLED", ignoreCase = true)) {
                                trySend(LlmStreamEvent.Error(throwable))
                            }
                            close()
                        }
                    }
                }
            }
        )

        awaitClose {
            if (!callbackFinished.get()) {
                try {
                    newConversation.cancelProcess()
                } catch (_: Throwable) {}
            }
        }
    }.flowOn(liteRtDispatcher)

    override suspend fun release() = withContext(liteRtDispatcher) {
        engineMutex.withLock {
            if (customBackend != null) {
                try { customBackend.close() } catch (_: Throwable) {}
                _isInitialized.value = false
                return@withContext
            }

            // Strict Teardown Sequence: wait for any in-flight C++ worker callback before closing
            awaitAndClosePreviousConversation()

            try { engine?.close() } catch (_: Throwable) {}
            engine = null

            _isInitialized.value = false
        }
    }
}
