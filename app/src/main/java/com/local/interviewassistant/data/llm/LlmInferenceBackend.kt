package com.local.interviewassistant.data.llm

import com.local.interviewassistant.core.model.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface LlmStreamEvent {
    data class PartialToken(val tokenChunk: String, val accumulatedText: String, val ttftMs: Long) : LlmStreamEvent
    data class Completed(val fullText: String, val ttftMs: Long, val totalDurationMs: Long, val decodeTps: Float = 0f, val tokenCount: Int = 0) : LlmStreamEvent
    data class Error(val throwable: Throwable) : LlmStreamEvent
}

interface LlmEngine {
    val isInitialized: StateFlow<Boolean>
    suspend fun initialize(): Result<Unit>
    fun generateAnswerStream(question: String): Flow<LlmStreamEvent>
    suspend fun release()
}

interface LlmInferenceBackend {
    fun initialize(config: AppConfig)
    fun generateAsync(prompt: String, onPartialResult: (partialText: String, done: Boolean) -> Unit, onError: (Throwable) -> Unit)
    fun cancel()
    fun close()
}

