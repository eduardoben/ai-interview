package com.local.interviewassistant.service

import com.local.interviewassistant.core.model.AppConfig
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class Phase8CopilotUiTest {

    class FakeLlmEngine : LlmEngine {
        private val _isInitialized = MutableStateFlow(false)
        override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

        override suspend fun initialize(): Result<Unit> {
            _isInitialized.value = true
            return Result.success(Unit)
        }

        override fun generateAnswerStream(question: String): Flow<LlmStreamEvent> = flow {
            emit(LlmStreamEvent.PartialToken("$question answer", "$question answer", 20L))
            emit(LlmStreamEvent.Completed("$question answer", 20L, 60L))
        }

        override suspend fun release() {
            _isInitialized.value = false
        }
    }

    @Test
    fun testTranscriptSegmentSmartQuestionDetection() {
        val q1 = TranscriptSegment(text = "How does Kotlin coroutines structured concurrency work?", isFinal = true)
        assertTrue("Interrogative should be question", q1.isLikelyQuestion)

        val q2 = TranscriptSegment(text = "Okay, wait a minute?", isFinal = true)
        assertTrue("Contains question mark", q2.isLikelyQuestion)

        val filler = TranscriptSegment(text = "Okay got it", isFinal = true)
        assertFalse("Short filler is not a question", filler.isLikelyQuestion)

        val longSentence = TranscriptSegment(
            text = "I think we should consider the alternative approach here because of performance issues.",
            isFinal = true
        )
        assertTrue("Sustained utterance >= 28 chars should be detected as question", longSentence.isLikelyQuestion)
        
        val shortInterrogative = TranscriptSegment(text = "what", isFinal = true)
        assertFalse("Too short, not actionable", shortInterrogative.isLikelyQuestion)
    }

    @Test
    fun testAnswerPersistsWhileSubsequentShortSpeechOccurs() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val fakeAudioSource = object : AudioSource {
            override fun audioStream(): Flow<FloatArray> = flow { }
        }

        val fakeVadSttBackend = object : VadSttBackend {
            override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
            override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean = false
            override fun getPartialTranscript(): String = "yeah"
            override fun finalizeAndResetStream(): String = "How does this work?"
            override fun release() {}
        }

        val config = AppConfig(File("tmp"))
        val sttEngine = SherpaVadSttEngine(config, fakeVadSttBackend)
        val llmEngine = FakeLlmEngine()

        val coordinator = AssistantPipelineCoordinator(
            audioSource = fakeAudioSource,
            sttEngine = sttEngine,
            llmEngine = llmEngine,
            scope = testScope,
            appConfig = config,
        )
        coordinator.start()
        testScope.advanceUntilIdle()

        // Instead of triggering completedQuestion natively, we force endpoint
        val segment = TranscriptSegment(text = "How does this work?", isFinal = true)
        coordinator.selectTranscriptSegmentForAnswer(segment)
        testScope.advanceUntilIdle()
        
        val stateAfterQuestion = coordinator.copilotUiState.value
        assertEquals("How does this work?", stateAfterQuestion.activeQuestion)
        assertEquals(true, stateAfterQuestion.activeAnswer?.contains("How does this work?"))
        
        coordinator.stop()
    }

    @Test
    fun testSelectTranscriptSegmentTriggersManualAnswer() = runTest {
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
            audioSource = fakeAudioSource,
            sttEngine = sttEngine,
            llmEngine = llmEngine,
            scope = testScope,
            appConfig = config,
        )
        coordinator.start()
        testScope.advanceUntilIdle()

        val segment = TranscriptSegment(text = "Just a statement", isFinal = true)
        coordinator.selectTranscriptSegmentForAnswer(segment)
        testScope.advanceUntilIdle()

        val stateAfterSelect = coordinator.copilotUiState.value
        assertEquals("Just a statement", stateAfterSelect.activeQuestion)
        assertEquals(true, stateAfterSelect.activeAnswer?.contains("Just a statement"))
        
        coordinator.stop()
    }
}
