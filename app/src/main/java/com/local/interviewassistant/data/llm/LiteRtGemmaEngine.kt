package com.local.interviewassistant.data.llm

import com.local.interviewassistant.core.model.AppConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class LiteRtGemmaEngine(
    private val config: AppConfig,
    private val backend: LlmInferenceBackend,
    private val llmDispatcher: CoroutineDispatcher = createDedicatedGpuDispatcher(),
) {
    companion object {
        fun createDedicatedGpuDispatcher(): ExecutorCoroutineDispatcher {
            return Executors.newSingleThreadExecutor { r ->
                Thread(r, "LiteRT-GPU-Thread").apply { priority = Thread.MAX_PRIORITY }
            }.asCoroutineDispatcher()
        }
    }

    private val _isInitialized = MutableStateFlow(value = false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private var activeConfigSnapshot: com.local.interviewassistant.core.model.LlmGenerationConfig? = null

    suspend fun initialize(): Result<Unit> = withContext(llmDispatcher) {
        try {
            backend.initialize(config)
            activeConfigSnapshot = config.llmConfig.value
            _isInitialized.value = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateAnswerStream(question: String): Flow<LlmStreamEvent> = callbackFlow {
        if (!_isInitialized.value) {
            trySend(LlmStreamEvent.Error(IllegalStateException("Engine not initialized")))
            close()
            return@callbackFlow
        }
        if (question.isBlank()) {
            trySend(LlmStreamEvent.Error(IllegalArgumentException("Question cannot be blank")))
            close()
            return@callbackFlow
        }
        
        val currentConfig = config.llmConfig.value
        val needsReinit = activeConfigSnapshot?.let {
            it.temperature != currentConfig.temperature || 
            it.topK != currentConfig.topK || 
            it.maxTokens != currentConfig.maxTokens
        } ?: true

        if (needsReinit) {
            try {
                backend.initialize(config)
                activeConfigSnapshot = currentConfig
            } catch (e: Exception) {
                trySend(LlmStreamEvent.Error(e))
                close(e)
                return@callbackFlow
            }
        }

        val formattedPrompt = config.formatGemmaPrompt(question, currentConfig)
        val startTimeMs = System.currentTimeMillis()
        var firstTokenTimeMs = -1L
        val accumulatedText = StringBuilder()

        backend.generateAsync(
            prompt = formattedPrompt,
            onPartialResult = { partialText, done ->
                val cleanPartial = partialText.replace("<end_of_turn>", "")
                
                if (cleanPartial.isNotEmpty() && (firstTokenTimeMs == -1L)) {
                    firstTokenTimeMs = System.currentTimeMillis()
                }
                
                val ttftMs = if (firstTokenTimeMs != -1L) {
                    (firstTokenTimeMs - startTimeMs).coerceAtLeast(1L)
                } else {
                    0L
                }

                if (cleanPartial.isNotEmpty()) {
                    accumulatedText.append(cleanPartial)
                    trySend(LlmStreamEvent.PartialToken(cleanPartial, accumulatedText.toString(), ttftMs))
                }

                if (done) {
                    val totalDurationMs = System.currentTimeMillis() - startTimeMs
                    trySend(LlmStreamEvent.Completed(accumulatedText.toString().trim(), ttftMs, totalDurationMs))
                    close()
                }
            },
        ) { throwable ->
            trySend(LlmStreamEvent.Error(throwable))
            close(throwable)
        }

        awaitClose {
            backend.cancel()
        }
    }.flowOn(llmDispatcher)


    suspend fun release() = withContext(llmDispatcher) {
        backend.close()
        _isInitialized.value = false
        (llmDispatcher as? ExecutorCoroutineDispatcher)?.close()
    }
}
