package com.local.interviewassistant.service

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.audio.AudioSource
import com.local.interviewassistant.data.llm.LlmEngine
import com.local.interviewassistant.data.llm.LlmStreamEvent
import com.local.interviewassistant.data.stt.SherpaVadSttEngine
import com.local.interviewassistant.data.stt.VadSttBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class Phase5PipelineCoordinatorTest {

    class FakeLlmEngine : LlmEngine {
        private val _isInitialized = MutableStateFlow(false)
        override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
        var generateAnswerCalledWith = ""
        var isReleased = false
        var initCount = 0
        var releaseCount = 0

        override suspend fun initialize(): Result<Unit> {
            initCount++
            _isInitialized.value = true
            return Result.success(Unit)
        }

        override fun generateAnswerStream(question: String): Flow<LlmStreamEvent> = flow {
            generateAnswerCalledWith = question
            emit(LlmStreamEvent.PartialToken("Answer", "Answer", 25L))
            emit(LlmStreamEvent.Completed("Answer for $question", 25L, 75L))
        }

        override suspend fun release() {
            releaseCount++
            _isInitialized.value = false
            isReleased = true
        }
    }

    @Test
    fun testInitializationAndStart() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val fakeAudioSource = object : AudioSource {
            override fun audioStream(): Flow<FloatArray> = flow { }
        }

        val fakeVadSttBackend = object : VadSttBackend {
            override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
            override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean = false
            override fun getPartialTranscript(): String = ""
            override fun finalizeAndResetStream(): String = ""
            override fun release() {}
        }

        val config = AppConfig(File("tmp"))
        val sttEngine = SherpaVadSttEngine(config, fakeVadSttBackend)
        val llmEngine = FakeLlmEngine()

        val coordinator = AssistantPipelineCoordinator(
            fakeAudioSource,
            sttEngine,
            llmEngine,
            testScope,
            config,
        )
        
        assertEquals(AssistantState.Idle, coordinator.state.value)

        coordinator.start()
        testScope.advanceUntilIdle()

        assertEquals(AssistantState.Listening, coordinator.state.value)
        assertTrue(llmEngine.isInitialized.value)

        coordinator.stop()
    }

    @Test
    fun testTogglePause() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val fakeAudioSource = object : AudioSource {
            override fun audioStream(): Flow<FloatArray> = flow { }
        }
        val fakeVadSttBackend = object : VadSttBackend {
            override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
            override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean = false
            override fun getPartialTranscript(): String = ""
            override fun finalizeAndResetStream(): String = ""
            override fun release() {}
        }

        val config = AppConfig(File("tmp"))
        val sttEngine = SherpaVadSttEngine(config, fakeVadSttBackend)
        val llmEngine = FakeLlmEngine()

        val coordinator = AssistantPipelineCoordinator(
            fakeAudioSource,
            sttEngine,
            llmEngine,
            testScope,
            config,
        )

        coordinator.start()
        testScope.advanceUntilIdle()
        assertEquals(AssistantState.Listening, coordinator.state.value)

        coordinator.togglePause()
        testScope.advanceUntilIdle()
        assertEquals(AssistantState.Paused, coordinator.state.value)

        coordinator.togglePause()
        testScope.advanceUntilIdle()
        assertEquals(AssistantState.Listening, coordinator.state.value)

        coordinator.stop()
    }

    @Test
    fun testClearCurrentAnswer() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val fakeAudioSource = object : AudioSource {
            override fun audioStream(): Flow<FloatArray> = flow { }
        }
        val fakeVadSttBackend = object : VadSttBackend {
            override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
            override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean = false
            override fun getPartialTranscript(): String = ""
            override fun finalizeAndResetStream(): String = ""
            override fun release() {}
        }

        val config = AppConfig(File("tmp"))
        val sttEngine = SherpaVadSttEngine(config, fakeVadSttBackend)
        val llmEngine = FakeLlmEngine()

        val coordinator = AssistantPipelineCoordinator(
            fakeAudioSource,
            sttEngine,
            llmEngine,
            testScope,
            config,
        )

        coordinator.start()
        testScope.advanceUntilIdle()

        val segment = TranscriptSegment(text = "What is Kotlin?", isFinal = true)
        coordinator.selectTranscriptSegmentForAnswer(segment)
        testScope.advanceUntilIdle()

        assertEquals("What is Kotlin?", coordinator.copilotUiState.value.activeQuestion)

        coordinator.clearCurrentAnswer()
        testScope.advanceUntilIdle()

        assertEquals(AssistantState.Listening, coordinator.state.value)
        assertNull(coordinator.copilotUiState.value.activeQuestion)
        assertNull(coordinator.copilotUiState.value.activeAnswer)

        coordinator.stop()
    }

    @Test
    fun testRapidStartAndStopMutexSerialization() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val fakeAudioSource = object : AudioSource {
            override fun audioStream(): Flow<FloatArray> = flow { }
        }
        val fakeVadSttBackend = object : VadSttBackend {
            override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
            override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean = false
            override fun getPartialTranscript(): String = ""
            override fun finalizeAndResetStream(): String = ""
            override fun release() {}
        }

        val config = AppConfig(File("tmp"))
        val sttEngine = SherpaVadSttEngine(config, fakeVadSttBackend)
        val llmEngine = FakeLlmEngine()

        val coordinator = AssistantPipelineCoordinator(
            fakeAudioSource,
            sttEngine,
            llmEngine,
            testScope,
            config,
        )

        // Rapid duplicate start() calls should be ignored once Listening
        coordinator.start()
        coordinator.start()
        testScope.advanceUntilIdle()
        assertEquals(1, llmEngine.initCount)
        assertEquals(AssistantState.Listening, coordinator.state.value)

        // Rapid duplicate stop() calls should be idempotent once Idle
        coordinator.stop()
        coordinator.stop()
        testScope.advanceUntilIdle()
        assertEquals(1, llmEngine.releaseCount)
        assertEquals(AssistantState.Idle, coordinator.state.value)
    }
}
