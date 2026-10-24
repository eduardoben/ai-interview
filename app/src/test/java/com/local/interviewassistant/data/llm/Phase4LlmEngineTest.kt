package com.local.interviewassistant.data.llm

import com.local.interviewassistant.core.model.AppConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class Phase4LlmEngineTest {

    class FakeLlmBackend : LlmInferenceBackend {
        var throwOnInitialize = false
        var isInitialized = false
        var promptReceived = ""
        var partialListener: ((String, Boolean) -> Unit)? = null
        var errorListener: ((Throwable) -> Unit)? = null
        var isCanceled = false
        var isClosed = false

        override fun initialize(config: AppConfig) {
            if (throwOnInitialize) throw IllegalStateException("Model missing")
            isInitialized = true
        }

        override fun generateAsync(
            prompt: String,
            onPartialResult: (String, Boolean) -> Unit,
            onError: (Throwable) -> Unit,
        ) {
            promptReceived = prompt
            partialListener = onPartialResult
            errorListener = onError
        }

        override fun cancel() {
            isCanceled = true
        }

        override fun close() {
            isClosed = true
        }

        fun simulateTokens(tokens: List<String>) {
            tokens.forEachIndexed { index, token ->
                val isLast = index == (tokens.size - 1)
                partialListener?.invoke(token, isLast)
            }
        }
    }

    @Test
    fun testInitializeSuccessAndFailure() = runTest {
        val config = AppConfig(File("."))
        val backend = FakeLlmBackend()
        val engine = LiteRtGemmaEngine(config, backend, UnconfinedTestDispatcher(testScheduler))

        val successResult = engine.initialize()
        assertTrue(successResult.isSuccess)
        assertTrue(engine.isInitialized.value)
        
        engine.release()
        assertFalse(engine.isInitialized.value)

        backend.throwOnInitialize = true
        val failResult = engine.initialize()
        assertTrue(failResult.isFailure)
        assertFalse(engine.isInitialized.value)
    }

    @Test
    fun testGenerateAnswerStreamEmitsPartialsAndCompletedWithTtft() = runTest {
        val config = AppConfig(File("."))
        val backend = FakeLlmBackend()
        val engine = LiteRtGemmaEngine(config, backend, UnconfinedTestDispatcher(testScheduler))
        engine.initialize()

        val question = "What is Kotlin?"
        val expectedPrompt = config.formatGemmaPrompt(question)

        val events = mutableListOf<LlmStreamEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.generateAnswerStream(question).toList(events)
        }

        assertEquals(expectedPrompt, backend.promptReceived)

        // Simulate streaming tokens with an end_of_turn tag that should be stripped
        backend.simulateTokens(listOf("• First point\n", "• Second point\n", "• Third point<end_of_turn>"))

        // Verify captured events
        assertEquals(4, events.size) // 3 partials + 1 completed
        
        val (tokenChunk, accumulatedText, ttftMs) = events[2] as LlmStreamEvent.PartialToken
        assertEquals("• Third point", tokenChunk) // Stripped tag
        assertEquals("• First point\n• Second point\n• Third point", accumulatedText)
        assertTrue(ttftMs >= 0L)

        val (fullText, ttftMsCompleted) = events[3] as LlmStreamEvent.Completed
        assertEquals("• First point\n• Second point\n• Third point", fullText)
        assertTrue(ttftMsCompleted >= 0L)

        job.cancel()
    }

    @Test
    fun testCancelMidStreamInvokesBackendCancel() = runTest {
        val config = AppConfig(File("."))
        val backend = FakeLlmBackend()
        val engine = LiteRtGemmaEngine(config, backend, UnconfinedTestDispatcher(testScheduler))
        engine.initialize()

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.generateAnswerStream("Hello?").collect {
                // Cancel collection on first emit
                throw kotlinx.coroutines.CancellationException("Test cancel")
            }
        }
        
        backend.simulateTokens(listOf("Word", "Another"))
        
        assertTrue(backend.isCanceled)
        job.cancel()
    }
}
