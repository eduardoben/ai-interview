package com.local.interviewassistant.data.stt

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.audio.AudioSource
import com.local.interviewassistant.data.audio.MicrophoneAudioCaptureSource
import com.local.interviewassistant.data.llm.LlmEngine
import com.local.interviewassistant.data.llm.LlmStreamEvent
import com.local.interviewassistant.service.AssistantPipelineCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Component Latency Benchmark Test Suite:
 * Measures and validates latency budgets across each layer of the assistant pipeline:
 * 1. Audio capture & PCM16 normalization throughput & latency.
 * 2. VAD frame windowing & chunk ingestion latency (real-time budget = 32 ms).
 * 3. Utterance endpointing & question emission latency.
 * 4. Punctuation & truecasing restoration latency (< 1 ms zero-overhead guarantee).
 * 5. Two-pass rescoring latency overhead.
 * 6. Pipeline coordinator state machine dispatch latency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComponentLatencyBenchmarkTest {

    private val tempDir = File.createTempFile("benchmark_test_", "").apply {
        delete()
        mkdirs()
    }
    private val config = AppConfig(tempDir)

    class FakeVadSttBackend : VadSttBackend {
        var isSpeechNext = false
        var currentPartial = ""
        var currentFinal = ""

        override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
        override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean = isSpeechNext
        override fun getPartialTranscript(): String = currentPartial
        override fun finalizeAndResetStream(): String = currentFinal
        override fun release() {}
    }

    class FakeOfflineRescorer(
        override var isAvailable: Boolean = true,
        var simulatedDelayMs: Long = 0L,
        var resultText: String? = "rescored result"
    ) : OfflineRescorerBackend {
        override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
        override fun rescoreUtterance(samples: FloatArray, sampleRate: Int): String? {
            if (simulatedDelayMs > 0) {
                Thread.sleep(simulatedDelayMs)
            }
            return resultText
        }
        override fun release() {}
    }

    class FakeLlmEngine : LlmEngine {
        private val _isInitialized = MutableStateFlow(true)
        override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
        var questionReceived = ""

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)

        override fun generateAnswerStream(question: String): Flow<LlmStreamEvent> = flow {
            questionReceived = question
            emit(LlmStreamEvent.PartialToken("Answer", "Answer", 30L))
            emit(LlmStreamEvent.Completed("Answer", 30L, 80L))
        }

        override suspend fun release() {
            _isInitialized.value = false
        }
    }

    @Test
    fun testAudioNormalizationLatency() {
        // 1 second of audio at 16kHz = 16,000 samples
        val oneSecondPcm = ShortArray(16000) { Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
        
        // Warmup
        repeat(10) {
            MicrophoneAudioCaptureSource.pcm16ToFloatArray(oneSecondPcm, oneSecondPcm.size)
        }

        val startNs = System.nanoTime()
        val floats = MicrophoneAudioCaptureSource.pcm16ToFloatArray(oneSecondPcm, oneSecondPcm.size)
        val elapsedNs = System.nanoTime() - startNs
        val elapsedMs = elapsedNs / 1_000_000.0

        assertEquals(16000, floats.size)
        // Normalizing 1 second of audio must take less than 2.0 ms (real-time allowance is 1000 ms)
        assertTrue(
            "PCM16 normalization of 1s of audio took ${elapsedMs}ms, expected < 2.0ms",
            elapsedMs < 2.0
        )

        // 10 seconds of audio at 16kHz = 160,000 samples
        val tenSecondsPcm = ShortArray(160000) { Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
        val start10sNs = System.nanoTime()
        val floats10s = MicrophoneAudioCaptureSource.pcm16ToFloatArray(tenSecondsPcm, tenSecondsPcm.size)
        val elapsed10sMs = (System.nanoTime() - start10sNs) / 1_000_000.0

        assertEquals(160000, floats10s.size)
        assertTrue(
            "PCM16 normalization of 10s of audio took ${elapsed10sMs}ms, expected < 15.0ms",
            elapsed10sMs < 15.0
        )
    }

    @Test
    fun testVadFrameProcessingLatency() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(config, fakeBackend)
        val frame = FloatArray(config.vadWindowSizeSamples) // 512 samples = 32ms of audio

        // Warmup
        repeat(50) {
            engine.processAudioChunk(frame)
        }

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "What is Kotlin?"

        val frameDurations = mutableListOf<Double>()
        val iterations = 500

        repeat(iterations) {
            val startNs = System.nanoTime()
            engine.processAudioChunk(frame)
            val durationMs = (System.nanoTime() - startNs) / 1_000_000.0
            frameDurations.add(durationMs)
        }

        val avgDurationMs = frameDurations.average()
        val maxDurationMs = frameDurations.maxOrNull() ?: 0.0
        val p95DurationMs = frameDurations.sorted()[(iterations * 0.95).toInt()]

        // Real-time audio window is 32ms. Average processing latency should be < 2.0ms (typically <0.1ms on JVM)
        assertTrue(
            "Average VAD frame processing latency ($avgDurationMs ms) must be far below 32ms real-time budget (threshold < 2.0ms)",
            avgDurationMs < 2.0
        )
        assertTrue(
            "P95 VAD frame processing latency ($p95DurationMs ms) must be < 5.0ms",
            p95DurationMs < 5.0
        )
    }

    @Test
    fun testUtteranceEndpointingAndEmissionLatency() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(config, fakeBackend)
        val frame = FloatArray(config.vadWindowSizeSamples)

        // Feed speech
        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "Can you explain REST API and GraphQL?"
        repeat(10) {
            engine.processAudioChunk(frame)
        }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "Can you explain REST API and GraphQL?"

        val completedQuestions = mutableListOf<String>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { segment ->
                completedQuestions.add(segment.text)
            }
        }

        // Feed silence up to 37 frames (threshold is 38)
        repeat(37) {
            engine.processAudioChunk(frame)
        }
        assertEquals(0, completedQuestions.size)

        // Measure time taken on the 38th frame that triggers utterance endpointing and finalization
        val startNs = System.nanoTime()
        engine.processAudioChunk(frame)
        val endpointingDurationMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertEquals(1, completedQuestions.size)
        assertEquals("Can you explain REST API and GraphQL?", completedQuestions.first())

        // Utterance completion and punctuation processing must be under 10.0 ms
        assertTrue(
            "Endpointing & question finalization latency ($endpointingDurationMs ms) must be < 10.0ms",
            endpointingDurationMs < 10.0
        )

        job.cancel()
    }

    @Test
    fun testQuestionPunctuationEngineLatencyBenchmark() {
        val sampleSentences = listOf(
            "what is the difference between val and var in kotlin",
            "how do kotlin coroutines handle structured concurrency and cancellation",
            "can you explain rest api and sql with mvvm",
            "tell me about a time you optimized mobile app rendering to 60 fps",
            "whats the difference between room and retrofit in android development",
            "i dont think we should block the main thread with heavy io operations",
            "explain how docker and k8s assist in microservices deployment",
            "walk me through your approach to memory leak detection using leakcanary",
            "what are the solid principles and how do they apply to clean architecture",
            "we achieved 100 percent code coverage with junit and mockk"
        )

        // Warmup
        repeat(100) {
            for (sentence in sampleSentences) {
                QuestionPunctuationEngine.process(sentence)
            }
        }

        val iterations = 1000
        val latenciesMs = mutableListOf<Double>()

        for (i in 0 until iterations) {
            val sentence = sampleSentences[i % sampleSentences.size]
            val startNs = System.nanoTime()
            QuestionPunctuationEngine.process(sentence)
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
            latenciesMs.add(elapsedMs)
        }

        val avgLatencyMs = latenciesMs.average()
        val p95LatencyMs = latenciesMs.sorted()[(iterations * 0.95).toInt()]
        val maxLatencyMs = latenciesMs.maxOrNull() ?: 0.0

        // Zero-overhead requirement: average latency must be < 1.0 ms (typically <0.05 ms on JVM)
        assertTrue(
            "QuestionPunctuationEngine average latency ($avgLatencyMs ms) must satisfy < 1.0 ms SLA",
            avgLatencyMs < 1.0
        )
        assertTrue(
            "QuestionPunctuationEngine P95 latency ($p95LatencyMs ms) must be < 2.0 ms",
            p95LatencyMs < 2.0
        )
    }

    @Test
    fun testTwoPassRescoringOverheadLatency() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val fakeRescorer = FakeOfflineRescorer(isAvailable = true, simulatedDelayMs = 15L, resultText = "what is clean architecture")
        val engine = SherpaVadSttEngine(config, fakeBackend, fakeRescorer)
        val frame = FloatArray(config.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "what is clean architecture"
        repeat(10) {
            engine.processAudioChunk(frame)
        }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "what is clean architecture"

        val completedQuestions = mutableListOf<String>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { segment ->
                completedQuestions.add(segment.text)
            }
        }

        // Feed 37 frames of silence
        repeat(37) {
            engine.processAudioChunk(frame)
        }

        // 38th frame triggers two-pass rescore with simulated 15ms delay
        val startNs = System.nanoTime()
        engine.processAudioChunk(frame)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertEquals(1, completedQuestions.size)
        assertEquals("What is clean architecture?", completedQuestions.first())

        // The duration should reflect the ~15ms simulated rescore delay without unbounded lag
        assertTrue(
            "Two-pass rescore latency ($elapsedMs ms) should be within expected simulated range (>= 12ms and < 100ms)",
            elapsedMs >= 12.0 && elapsedMs < 100.0
        )

        job.cancel()
    }

    @Test
    fun testPipelineCoordinatorQuestionToAnswerLatency() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val fakeAudioSource = object : AudioSource {
            override fun audioStream(): Flow<FloatArray> = flow { }
        }

        val fakeBackend = FakeVadSttBackend()
        val sttEngine = SherpaVadSttEngine(config, fakeBackend)
        val fakeLlm = FakeLlmEngine()

        val coordinator = AssistantPipelineCoordinator(
            audioSource = fakeAudioSource,
            sttEngine = sttEngine,
            llmEngine = fakeLlm,
            scope = testScope,
            appConfig = config
        )

        coordinator.start()
        testScope.advanceUntilIdle()

        assertEquals(AssistantState.Listening, coordinator.state.value)

        // Select a segment to answer and measure time until GeneratingAnswer state is set
        val segment = TranscriptSegment(
            text = "How does Kotlin handle coroutine cancellation?",
            isFinal = true
        )

        val startNs = System.nanoTime()
        coordinator.selectTranscriptSegmentForAnswer(segment)
        testScope.advanceUntilIdle()
        val dispatchDurationMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertEquals(
            "How does Kotlin handle coroutine cancellation?",
            coordinator.copilotUiState.value.activeQuestion
        )
        assertTrue(
            "Coordinator dispatch to LLM took ${dispatchDurationMs}ms, expected < 25.0ms",
            dispatchDurationMs < 25.0
        )

        coordinator.stop()
    }
}
