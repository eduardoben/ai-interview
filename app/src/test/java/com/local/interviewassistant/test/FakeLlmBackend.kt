package com.local.interviewassistant.test

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.data.llm.LlmInferenceBackend
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Headless fake LLM backend implementing LlmInferenceBackend for continuous
 * integration (CI) tests and emulators without requiring 3GB binary model files.
 */
class FakeLlmBackend(
    val cannedTokens: List<String> = listOf("• Use ", "Redis ", "for ", "caching ", "and ", "scalability.")
) : LlmInferenceBackend {
    var initialized = false
        private set

    private val isCancelled = AtomicBoolean(false)

    override fun initialize(config: AppConfig) {
        initialized = true
    }

    override suspend fun initializeSuspend(config: AppConfig) {
        initialized = true
    }

    override fun generateAsync(
        prompt: String,
        onPartialResult: (partialText: String, done: Boolean) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (!initialized) {
            onError(IllegalStateException("FakeLlmBackend not initialized"))
            return
        }
        isCancelled.set(false)
        cannedTokens.forEachIndexed { index, token ->
            if (isCancelled.get()) return
            val isDone = index == cannedTokens.size - 1
            onPartialResult(token, isDone)
        }
    }

    override fun cancel() {
        isCancelled.set(true)
    }

    override fun close() {
        initialized = false
        isCancelled.set(true)
    }

    override suspend fun closeSuspend() {
        close()
    }
}
