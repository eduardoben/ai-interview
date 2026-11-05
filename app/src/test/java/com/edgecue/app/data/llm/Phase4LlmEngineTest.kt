package com.edgecue.app.data.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.edgecue.app.core.model.AppConfig
import com.edgecue.app.data.hardware.DelegateType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class Phase4LlmEngineTest {

    class FakeLlmBackend : LlmInferenceBackend {
        @Volatile var throwOnInitialize = false
        @Volatile var isInitialized = false
        @Volatile var promptReceived = ""
        @Volatile var partialListener: ((String, Boolean) -> Unit)? = null
        @Volatile var errorListener: ((Throwable) -> Unit)? = null
        @Volatile var isCanceled = false
        @Volatile var isClosed = false
        val listenerReadyLatch = CountDownLatch(1)
        val cancelLatch = CountDownLatch(1)

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
            listenerReadyLatch.countDown()
        }

        override fun cancel() {
            isCanceled = true
            cancelLatch.countDown()
        }

        override fun close() {
            isClosed = true
        }

        fun simulateTokens(tokens: List<String>) {
            assertTrue("Expected generateAsync to be called", listenerReadyLatch.await(2, TimeUnit.SECONDS))
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
        val engine = LiteRtGemmaEngine(
            context = null,
            config = config,
            customBackend = backend
        )

        val successResult = engine.initialize()
        assertTrue(successResult.isSuccess)
        assertTrue(engine.isInitialized.value)
        assertTrue(backend.isInitialized)

        engine.release()
        assertFalse(engine.isInitialized.value)
        assertTrue(backend.isClosed)

        backend.throwOnInitialize = true
        val failResult = engine.initialize()
        assertTrue(failResult.isFailure)
        assertFalse(engine.isInitialized.value)
    }

    @Test
    fun testGenerateAnswerStreamEmitsPartialsAndCompletedWithTtft() = runTest {
        val config = AppConfig(File("."))
        val backend = FakeLlmBackend()
        val engine = LiteRtGemmaEngine(
            context = null,
            config = config,
            customBackend = backend
        )
        engine.initialize()

        val question = "What is Kotlin?"
        val expectedPrompt = config.formatGemmaPrompt(question)

        val events = mutableListOf<LlmStreamEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.generateAnswerStream(question).toList(events)
        }

        // Simulate streaming tokens with an end_of_turn tag that should be stripped
        backend.simulateTokens(listOf("• First point\n", "• Second point\n", "• Third point<end_of_turn>"))
        collectJob.join()

        assertEquals(expectedPrompt, backend.promptReceived)

        // Verify captured events
        assertEquals(4, events.size) // 3 partials + 1 completed

        val (tokenChunk, accumulatedText, ttftMs) = events[2] as LlmStreamEvent.PartialToken
        assertEquals("• Third point", tokenChunk) // Stripped tag
        assertEquals("• First point\n• Second point\n• Third point", accumulatedText)
        assertTrue(ttftMs >= 0L)

        val completed = events[3] as LlmStreamEvent.Completed
        assertEquals("• First point\n• Second point\n• Third point", completed.fullText)
        assertTrue(completed.ttftMs >= 0L)
        assertEquals(3, completed.tokenCount)
    }

    @Test
    fun testCancelMidStreamInvokesBackendCancel() = runTest {
        val config = AppConfig(File("."))
        val backend = FakeLlmBackend()
        val engine = LiteRtGemmaEngine(
            context = null,
            config = config,
            customBackend = backend
        )
        engine.initialize()

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.generateAnswerStream("Hello?").take(1).collect {}
        }

        assertTrue("Expected generateAsync to be called", backend.listenerReadyLatch.await(2, TimeUnit.SECONDS))
        backend.partialListener?.invoke("Word", false)
        collectJob.join()

        assertTrue("Expected backend.cancel() to be invoked on stream cancellation", backend.cancelLatch.await(2, TimeUnit.SECONDS))
        assertTrue(backend.isCanceled)
    }

    @Test
    fun testBackendResolverFallbackSequences() {
        // 1. Explicit CPU_MMAP preference
        val cpuMmapChain = BackendResolver.getFallbackSequence(
            nativeLibDir = "/data/app/lib/arm64",
            isGpuAvailable = true,
            isTensorTpuModelFile = false,
            useGpuDelegate = true,
            preferredDelegate = DelegateType.CPU_MMAP
        )
        assertEquals(1, cpuMmapChain.size)
        assertTrue(cpuMmapChain[0].first is Backend.CPU)
        assertEquals("CPU (Memory-Mapped)", cpuMmapChain[0].second)

        // 2. Explicit NPU_GOOGLE_TENSOR preference with nativeLibDir
        val npuChain = BackendResolver.getFallbackSequence(
            nativeLibDir = "/data/app/lib/arm64",
            isGpuAvailable = true,
            isTensorTpuModelFile = true,
            useGpuDelegate = true,
            preferredDelegate = DelegateType.NPU_GOOGLE_TENSOR
        )
        assertEquals(2, npuChain.size)
        assertTrue(npuChain[0].first is Backend.NPU)
        assertTrue(npuChain[1].first is Backend.NPU)

        // 3. Automatic GPU -> CPU fallback when preferredDelegate is null
        val gpuFallbackChain = BackendResolver.getFallbackSequence(
            nativeLibDir = null,
            isGpuAvailable = true,
            isTensorTpuModelFile = false,
            useGpuDelegate = true,
            preferredDelegate = null
        )
        assertEquals(2, gpuFallbackChain.size)
        assertTrue(gpuFallbackChain[0].first is Backend.GPU)
        assertTrue(gpuFallbackChain[1].first is Backend.CPU)
    }

    @Test
    fun testBlankSystemPromptPreservesSystemSafetyGuardrail() {
        val config = AppConfig(File("."))

        // Case 1: systemPrompt = null (standard default fallback)
        val engineNull = LiteRtGemmaEngine(
            context = null,
            config = config,
            systemPrompt = null
        )
        val promptNull = engineNull.resolveEffectiveSystemPrompt()
        assertTrue(promptNull.contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL))
        assertTrue(promptNull.contains(config.llmConfig.value.promptStyle.systemInstruction))

        val conversationConfigNull = engineNull.createConversationConfig()
        val sysInstructionNull = conversationConfigNull.systemInstruction
        assertNotNull(sysInstructionNull)
        val textNull = (sysInstructionNull?.contents?.firstOrNull() as? Content.Text)?.text
        assertTrue(textNull?.contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL) == true)

        // Case 2: systemPrompt = "" (empty string must execute fallback, NOT set null)
        val engineEmpty = LiteRtGemmaEngine(
            context = null,
            config = config,
            systemPrompt = ""
        )
        val promptEmpty = engineEmpty.resolveEffectiveSystemPrompt()
        assertTrue(promptEmpty.contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL))
        assertTrue(promptEmpty.contains(config.llmConfig.value.promptStyle.systemInstruction))

        val conversationConfigEmpty = engineEmpty.createConversationConfig()
        val sysInstructionEmpty = conversationConfigEmpty.systemInstruction
        assertNotNull("sysInstruction must not be null when systemPrompt is empty", sysInstructionEmpty)
        val textEmpty = (sysInstructionEmpty?.contents?.firstOrNull() as? Content.Text)?.text
        assertTrue(textEmpty?.contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL) == true)

        // Case 3: systemPrompt = "   " (whitespace-only string must execute fallback, NOT set null)
        val engineWhitespace = LiteRtGemmaEngine(
            context = null,
            config = config,
            systemPrompt = "   \n\t  "
        )
        val promptWhitespace = engineWhitespace.resolveEffectiveSystemPrompt()
        assertTrue(promptWhitespace.contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL))
        assertTrue(promptWhitespace.contains(config.llmConfig.value.promptStyle.systemInstruction))

        val conversationConfigWhitespace = engineWhitespace.createConversationConfig()
        val sysInstructionWhitespace = conversationConfigWhitespace.systemInstruction
        assertNotNull("sysInstruction must not be null when systemPrompt is blank whitespace", sysInstructionWhitespace)
        val textWhitespace = (sysInstructionWhitespace?.contents?.firstOrNull() as? Content.Text)?.text
        assertTrue(textWhitespace?.contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL) == true)

        // Case 4: Non-blank custom system prompt is preserved without fallback injection
        val customPrompt = "You are a professional mock interviewer."
        val engineCustom = LiteRtGemmaEngine(
            context = null,
            config = config,
            systemPrompt = customPrompt
        )
        assertEquals(customPrompt, engineCustom.resolveEffectiveSystemPrompt())

        val conversationConfigCustom = engineCustom.createConversationConfig()
        val textCustom = (conversationConfigCustom.systemInstruction?.contents?.firstOrNull() as? Content.Text)?.text
        assertEquals(customPrompt, textCustom)
    }

    @Test
    fun testBlankSystemPromptWithInterviewContextPreservesGuardrailAndContext() {
        val config = AppConfig(File("."))
        config.updateLlmConfig(
            config.llmConfig.value.copy(
                interviewContext = "Android Staff Engineer Interview"
            )
        )

        // Blank string with interviewContext configured
        val engineBlank = LiteRtGemmaEngine(
            context = null,
            config = config,
            systemPrompt = "  "
        )
        val prompt = engineBlank.resolveEffectiveSystemPrompt()
        assertTrue(prompt.contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL))
        assertTrue(prompt.contains("<interview_context>"))
        assertTrue(prompt.contains("Android Staff Engineer Interview"))

        val conversationConfig = engineBlank.createConversationConfig()
        val sysInstruction = conversationConfig.systemInstruction
        assertNotNull(sysInstruction)
        val text = (sysInstruction?.contents?.firstOrNull() as? Content.Text)?.text
        assertTrue(text?.contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL) == true)
        assertTrue(text?.contains("<interview_context>") == true)
        assertTrue(text?.contains("Android Staff Engineer Interview") == true)
    }

    @Test
    fun testInitializeWithBlankSystemPromptSucceeds() = runTest {
        val config = AppConfig(File("."))
        val backendEmpty = FakeLlmBackend()
        val engineEmpty = LiteRtGemmaEngine(
            context = null,
            config = config,
            systemPrompt = "",
            customBackend = backendEmpty
        )
        val initResult = engineEmpty.initialize()
        assertTrue(initResult.isSuccess)
        assertTrue(engineEmpty.isInitialized.value)
        assertTrue(backendEmpty.isInitialized)
        assertTrue(engineEmpty.resolveEffectiveSystemPrompt().contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL))

        val backendWhitespace = FakeLlmBackend()
        val engineWhitespace = LiteRtGemmaEngine(
            context = null,
            config = config,
            systemPrompt = "   ",
            customBackend = backendWhitespace
        )
        val initWhitespaceResult = engineWhitespace.initialize()
        assertTrue(initWhitespaceResult.isSuccess)
        assertTrue(engineWhitespace.isInitialized.value)
        assertTrue(backendWhitespace.isInitialized)
        assertTrue(engineWhitespace.resolveEffectiveSystemPrompt().contains(AppConfig.SYSTEM_SAFETY_GUARDRAIL))
    }
}
