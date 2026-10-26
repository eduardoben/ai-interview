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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

class GalleryLiteRtBackend(private val context: Context) : LlmInferenceBackend {

    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    @Volatile private var currentAppConfig: AppConfig? = null

    // LITERTLM THREAD SERIALIZATION
    // All LiteRT operations must be synchronized on this single thread to avoid
    // 'Input tensor not found' or JVM/JNI corruption errors.
    private val liteRtDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LiteRT-GPU-Thread")
    }.asCoroutineDispatcher()

    private val isGenerating = AtomicBoolean(false)
    @Volatile private var currentGenerationId = 0

    override fun initialize(config: AppConfig) {
        // We must perform all LiteRT operations on the single thread
        kotlinx.coroutines.runBlocking(liteRtDispatcher) {
            initializeInternal(config)
        }
    }

    private fun initializeInternal(config: AppConfig) {
        val modelFile = config.activeLlmModelFile
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            throw IllegalStateException("LLM model file missing at ${modelFile.absolutePath}")
        }

        if (modelFile.length() < 100 * 1024 * 1024L) {
            throw IllegalStateException(
                "LLM model file '${modelFile.name}' is incomplete or truncated (${modelFile.length() / (1024 * 1024)} MB). Expected > 1 GB."
            )
        }

        closeSynchronously()
        currentAppConfig = config

        val resolvedPath = modelFile.absolutePath
        val isTensorTpuModelFile = modelFile.name.contains("Tensor", ignoreCase = true) ||
                modelFile.name.contains("tpu", ignoreCase = true) ||
                modelFile.name.contains("npu", ignoreCase = true)

        val cacheDir = context.cacheDir.absolutePath
        val attemptBackends = BackendResolver.getFallbackSequence(context, isTensorTpuModelFile, config.useGpuDelegate)

        var initializedEngine: Engine? = null
        val errors = mutableListOf<String>()

        // LITERTLM TENSOR DIMENSION FIX
        // Ensure maxNumTokens = null for baked TPU models, and 2048 for CPU/GPU.
        // DO NOT use config.maxTokens (which is standard response size), 
        // as the EdgeTPU/GPU compiler requires the exact dimension defined at compile time.
        for ((backend, label) in attemptBackends) {
            try {
                val engineConfig = EngineConfig(
                    modelPath = resolvedPath,
                    backend = backend,
                    maxNumTokens = if (isTensorTpuModelFile) null else 2048,
                    cacheDir = cacheDir
                )
                val newEngine = Engine(engineConfig)
                newEngine.initialize()
                initializedEngine = newEngine
                Log.i("GalleryLiteRtBackend", "Successfully initialized LiteRT engine on backend '$label' for model '$resolvedPath'")
                break
            } catch (t: Throwable) {
                val msg = t.message ?: t.toString()
                Log.w("GalleryLiteRtBackend", "Failed initialization on backend '$label' ($msg). Attempting fallback...", t)
                errors.add("$label: $msg")
            }
        }

        if (initializedEngine != null) {
            engine = initializedEngine
            return
        }

        if (isTensorTpuModelFile) {
            val standardGpuModel = config.llmModelFile
            if (standardGpuModel.exists() && standardGpuModel.length() >= 100 * 1024 * 1024L) {
                try {
                    val fallbackEngineConfig = EngineConfig(
                        modelPath = standardGpuModel.absolutePath,
                        backend = Backend.GPU(),
                        maxNumTokens = 2048,
                        cacheDir = cacheDir
                    )
                    val newEngine = Engine(fallbackEngineConfig)
                    newEngine.initialize()
                    engine = newEngine
                    config.updateLlmConfig(config.llmConfig.value.copy(selectedModelPath = standardGpuModel.absolutePath))
                    Log.i("GalleryLiteRtBackend", "TPU model failed to allocate on NPU. Successfully fell back to standard GPU model '${standardGpuModel.name}' on GPU.")
                    return
                } catch (t: Throwable) {
                    errors.add("Standard GPU Fallback: ${t.message ?: t.toString()}")
                }
            }

            throw IllegalStateException(
                "Pixel TPU model '${modelFile.name}' failed to allocate on this system NPU ($errors). Please download or select the standard GPU model ('gemma-4-E2B-it.litertlm') on the dashboard."
            )
        }

        throw IllegalStateException(
            "Failed to initialize LLM on all backends. Errors: ${errors.joinToString("; ")}"
        )
    }

    override fun generateAsync(
        prompt: String,
        onPartialResult: (partialText: String, done: Boolean) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val currentEngine = engine
        if (currentEngine == null) {
            onError(IllegalStateException("LLM Engine not initialized"))
            return
        }

        val genId = ++currentGenerationId
        isGenerating.set(true)

        val llmConfig = currentAppConfig?.llmConfig?.value
        val isTensorTpuModel = currentAppConfig?.activeLlmModelFile?.name?.let {
            it.contains("Tensor", ignoreCase = true) || it.contains("tpu", ignoreCase = true) || it.contains("npu", ignoreCase = true)
        } ?: false

        val samplerConfig = if (isTensorTpuModel) {
            null
        } else {
            SamplerConfig(
                topK = llmConfig?.topK ?: 20,
                topP = 0.9,
                temperature = (llmConfig?.temperature ?: 0.2f).toDouble()
            )
        }

        val conversationConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            systemInstruction = null
        )

        liteRtDispatcher.dispatch(kotlin.coroutines.EmptyCoroutineContext) {
            try {
                try {
                    activeConversation?.close()
                } catch (_: Exception) {}
                activeConversation = null

                val newConversation = currentEngine.createConversation(conversationConfig)
                activeConversation = newConversation

                newConversation.sendMessageAsync(
                    Contents.of(Content.Text(prompt)),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            if (currentGenerationId == genId) {
                                onPartialResult(message.toString(), false)
                            }
                        }

                        override fun onDone() {
                            if (currentGenerationId == genId) {
                                onPartialResult("", true)
                            }
                            isGenerating.set(false)
                        }

                        override fun onError(throwable: Throwable) {
                            if (currentGenerationId == genId) {
                                onError(throwable)
                            }
                            isGenerating.set(false)
                        }
                    }
                )
            } catch (e: Exception) {
                isGenerating.set(false)
                onError(e)
            }
        }
    }

    override fun cancel() {
        currentGenerationId++
        val conv = activeConversation
        if (conv != null) {
            CoroutineScope(liteRtDispatcher).launch {
                try {
                    conv.cancelProcess()
                } catch (_: Exception) {}
            }
        }
        isGenerating.set(false)
    }

    private fun closeSynchronously() {
        currentGenerationId++
        val conv = activeConversation
        val eng = engine
        activeConversation = null
        engine = null

        try { conv?.close() } catch (_: Exception) {}
        try { eng?.close() } catch (_: Exception) {}
        isGenerating.set(false)
    }

    override fun close() {
        currentGenerationId++
        currentAppConfig = null
        // Ensure this happens on the correct thread, synchronously to avoid race condition if another thread calls initialize immediately after
        kotlinx.coroutines.runBlocking(liteRtDispatcher) {
            closeSynchronously()
        }
    }
}
