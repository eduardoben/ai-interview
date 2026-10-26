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
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

class LiteRtGemmaEngine(
    private val context: Context,
    private val config: AppConfig,
    private val systemPrompt: String? = null
) : LlmEngine {
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    
    private val liteRtDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LiteRT-GPU-Thread")
    }.asCoroutineDispatcher()

    private val engineMutex = Mutex()
    private val tracker = LitertlmInferenceMetricsTracker()

    override suspend fun initialize(): Result<Unit> = withContext(liteRtDispatcher) {
        engineMutex.withLock {
            try {
                if (engine != null) {
                    return@withContext Result.success(Unit)
                }

                val modelFile = config.activeLlmModelFile
                if (!modelFile.exists()) {
                    throw IllegalStateException("Model file not found")
                }

                val isTensorTpuModelFile = modelFile.name.contains("Tensor", ignoreCase = true) ||
                        modelFile.name.contains("tpu", ignoreCase = true) ||
                        modelFile.name.contains("npu", ignoreCase = true)

                val attemptBackends = mutableListOf<Pair<Backend, String>>()
                if (isTensorTpuModelFile) {
                    val nativeLibDir = context.applicationInfo.nativeLibraryDir
                    attemptBackends.add(Backend.NPU(nativeLibraryDir = nativeLibDir) to "NPU (nativeLibraryDir: $nativeLibDir)")
                    attemptBackends.add(Backend.NPU() to "NPU (system default)")
                } else if (config.useGpuDelegate) {
                    attemptBackends.add(Backend.GPU() to "GPU")
                    attemptBackends.add(Backend.CPU() to "CPU Fallback")
                } else {
                    attemptBackends.add(Backend.CPU() to "CPU")
                }

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
                        cacheDir = context.cacheDir.absolutePath
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
                                cacheDir = context.cacheDir.absolutePath
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
                Result.failure(if (t is Exception) t else RuntimeException(t))
            }
        }
    }

    override fun generateAnswerStream(question: String): Flow<LlmStreamEvent> = callbackFlow {
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

        try {
            activeConversation?.close()
        } catch (_: Exception) {}
        
        val newConversation = currentEngine.createConversation(conversationConfig)
        activeConversation = newConversation

        tracker.startTracking()
        val accumulatedText = StringBuilder()
        
        newConversation.sendMessageAsync(
            Contents.of(Content.Text(question)),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val partial = message.toString().replace("<end_of_turn>", "")
                    if (partial.isNotEmpty()) {
                        tracker.recordToken()
                        accumulatedText.append(partial)
                        trySend(LlmStreamEvent.PartialToken(partial, accumulatedText.toString(), tracker.getTtftMs()))
                    }
                }

                override fun onDone() {
                    tracker.stopTracking()
                    tracker.logMetrics("LiteRtGemmaEngine")
                    MemoryMonitor.logMemoryUsage(context, "LiteRtGemmaEngine")
                    trySend(LlmStreamEvent.Completed(accumulatedText.toString().trim(), tracker.getTtftMs(), tracker.getTotalDurationMs()))
                    close()
                }

                override fun onError(throwable: Throwable) {
                    trySend(LlmStreamEvent.Error(throwable))
                    close(throwable)
                }
            }
        )

        awaitClose {
            try {
                activeConversation?.cancelProcess()
            } catch (_: Exception) {}
        }
    }.flowOn(liteRtDispatcher)

    override suspend fun release() = withContext(liteRtDispatcher) {
        engineMutex.withLock {
            // Strict Teardown Sequence
            try { activeConversation?.close() } catch (_: Exception) {}
            activeConversation = null

            try { engine?.close() } catch (_: Exception) {}
            engine = null
            
            _isInitialized.value = false
        }
    }
}
