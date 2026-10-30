package com.local.interviewassistant.service

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.audio.AudioSource
import com.local.interviewassistant.data.llm.LiteRtGemmaEngine
import com.local.interviewassistant.data.llm.LlmEngine
import com.local.interviewassistant.data.llm.LlmStreamEvent
import com.local.interviewassistant.data.stt.OfflineRescorerBackend
import com.local.interviewassistant.data.stt.SherpaVadSttEngine
import com.local.interviewassistant.data.stt.VadSttBackend
import com.local.interviewassistant.test.FakeLlmBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class Phase2StabilityAndThermalTest {

    private class MockAudioSource : AudioSource {
        override fun audioStream(): Flow<FloatArray> = flow {}
    }

    private class MockVadBackend : VadSttBackend {
        var isInit = false
        override suspend fun initialize(config: AppConfig): Result<Unit> {
            isInit = true
            return Result.success(Unit)
        }
        override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean = false
        override fun getPartialTranscript(): String = ""
        override fun finalizeAndResetStream(): String = ""
        override fun release() {
            isInit = false
        }
    }

    private class MockOfflineRescorer : OfflineRescorerBackend {
        var isReleased = false
        override val isAvailable: Boolean = true
        override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
        override fun rescoreUtterance(samples: FloatArray, sampleRate: Int): String? = null
        override fun release() {
            isReleased = true
        }
    }

    private class FakeLlmEngine : LlmEngine {
        private val _isInitialized = MutableStateFlow(false)
        override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

        override suspend fun initialize(): Result<Unit> {
            _isInitialized.value = true
            return Result.success(Unit)
        }

        override fun generateAnswerStream(question: String): Flow<LlmStreamEvent> = flow {
            // Emit 300 tokens to test throttling cap
            for (i in 1..300) {
                emit(LlmStreamEvent.PartialToken("token$i ", "accumulated up to $i", 10L))
            }
            emit(LlmStreamEvent.Completed("full answer with 300 tokens", 10L, 100L))
        }

        override suspend fun release() {
            _isInitialized.value = false
        }
    }

    @Test
    fun testThermalStatusTransitions() = runTest {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "p2_thermal_test_${System.nanoTime()}")
        tempDir.mkdirs()

        try {
            val appConfig = AppConfig(tempDir)
            val sttEngine = SherpaVadSttEngine(appConfig, MockVadBackend(), MockOfflineRescorer())
            val coordinator = AssistantPipelineCoordinator(
                audioSource = MockAudioSource(),
                sttEngine = sttEngine,
                llmEngine = FakeLlmEngine(),
                scope = this,
                appConfig = appConfig
            )

            // Initially not throttled
            assertFalse(coordinator.isThermalThrottled.value)
            assertFalse(coordinator.copilotUiState.value.isThermalThrottled)

            // Trigger SEVERE thermal status (3)
            coordinator.onThermalStatusChanged(3)
            assertTrue(coordinator.isThermalThrottled.value)
            assertTrue(coordinator.copilotUiState.value.isThermalThrottled)

            // Trigger CRITICAL thermal status (4)
            coordinator.onThermalStatusChanged(4)
            assertTrue(coordinator.isThermalThrottled.value)

            // Recover to LIGHT thermal status (1)
            coordinator.onThermalStatusChanged(1)
            assertFalse(coordinator.isThermalThrottled.value)
            assertFalse(coordinator.copilotUiState.value.isThermalThrottled)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testThermalThrottlingCapsTokensAt256() = runTest {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "p2_cap_test_${System.nanoTime()}")
        tempDir.mkdirs()

        try {
            val appConfig = AppConfig(tempDir)
            val sttEngine = SherpaVadSttEngine(appConfig, MockVadBackend(), MockOfflineRescorer())
            val coordinator = AssistantPipelineCoordinator(
                audioSource = MockAudioSource(),
                sttEngine = sttEngine,
                llmEngine = FakeLlmEngine(),
                scope = this,
                appConfig = appConfig
            )

            coordinator.start()
            advanceUntilIdle()

            // Turn on thermal throttle
            coordinator.onThermalStatusChanged(3)
            assertTrue(coordinator.isThermalThrottled.value)

            // Trigger generation with FakeLlmEngine that tries to emit 300 tokens
            coordinator.triggerAnswerGeneration("What is horizontal scalability?")
            advanceUntilIdle()

            // Should have capped at 256 tokens and transitioned to AnswerReady
            assertTrue(
                "Expected AnswerReady state but got: ${coordinator.state.value}",
                coordinator.state.value is AssistantState.AnswerReady
            )
            val readyState = coordinator.state.value as AssistantState.AnswerReady
            assertTrue(readyState.finalAnswer.contains("up to 256"))
            assertFalse(readyState.finalAnswer.contains("up to 257"))

            coordinator.stop()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testLowMemoryTrimReleasesRescorerAndTrimsHistory() = runTest {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "p2_mem_test_${System.nanoTime()}")
        tempDir.mkdirs()

        try {
            val appConfig = AppConfig(tempDir)
            val mockRescorer = MockOfflineRescorer()
            val sttEngine = SherpaVadSttEngine(appConfig, MockVadBackend(), mockRescorer)
            val coordinator = AssistantPipelineCoordinator(
                audioSource = MockAudioSource(),
                sttEngine = sttEngine,
                llmEngine = FakeLlmEngine(),
                scope = this,
                appConfig = appConfig
            )

            coordinator.start()
            advanceUntilIdle()

            // Simulate accumulating segments into UI state history
            for (i in 1..12) {
                val seg = TranscriptSegment(text = "Historical question #$i", isFinal = true)
                coordinator.selectTranscriptSegmentForAnswer(seg)
            }
            advanceUntilIdle()

            // Call low-memory hook with TRIM_MEMORY_RUNNING_LOW (10)
            coordinator.onTrimMemory(10)

            // Assert rescorer was released to reclaim native RAM
            assertTrue("Expected offline rescorer to be released", mockRescorer.isReleased)

            coordinator.stop()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testHeadlessFakeLlmBackendWithLiteRtGemmaEngine() = runTest {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "p2_fake_llm_${System.nanoTime()}")
        tempDir.mkdirs()

        try {
            val appConfig = AppConfig(tempDir)
            val fakeBackend = FakeLlmBackend(
                cannedTokens = listOf("• Use ", "Valkey ", "or ", "Redis.")
            )

            val engine = LiteRtGemmaEngine(
                context = null,
                config = appConfig,
                customBackend = fakeBackend
            )

            val initResult = engine.initialize()
            assertTrue(initResult.isSuccess)
            assertTrue(fakeBackend.initialized)

            val events = mutableListOf<LlmStreamEvent>()
            engine.generateAnswerStream("How to cache?").collect { event ->
                events.add(event)
            }

            val partials = events.filterIsInstance<LlmStreamEvent.PartialToken>()
            val completed = events.filterIsInstance<LlmStreamEvent.Completed>()

            assertEquals(4, partials.size)
            assertEquals(1, completed.size)
            assertEquals("• Use Valkey or Redis.", completed.first().fullText)

            engine.release()
            assertFalse(fakeBackend.initialized)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
