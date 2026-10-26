package com.local.interviewassistant.data.stt

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.data.audio.MicrophoneAudioCaptureSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase3AudioSttTest {

    private val dummyConfig = AppConfig(File("."))

    // Fake backend that lets us simulate speech/silence frames and text results
    class FakeVadSttBackend : VadSttBackend {
        var isSpeechNext = false
        var currentPartial = ""
        var currentFinal = ""

        override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)

        override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean {
            return isSpeechNext
        }

        override fun getPartialTranscript(): String = currentPartial

        override fun finalizeAndResetStream(): String {
            val result = currentFinal
            currentFinal = ""
            currentPartial = ""
            return result
        }

        override fun release() {}
    }

    @Test
    fun testPcm16ToFloatNormalization() {
        val pcm = shortArrayOf(Short.MIN_VALUE, 0, 16384, Short.MAX_VALUE)
        val floats = MicrophoneAudioCaptureSource.pcm16ToFloatArray(pcm, pcm.size)

        assertEquals(4, floats.size)
        assertEquals(-1.0f, floats[0], 0.001f)
        assertEquals(0.0f, floats[1], 0.001f)
        assertEquals(0.5f, floats[2], 0.001f)
        assertEquals(1.0f, floats[3], 0.001f)
    }

    @Test
    fun testWindowFramingAndAutomaticEndpoint() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)

        // Min speech frames = 8
        // Min silence frames = 38 (ceil(1.2s / 0.032s))
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        // 1. Feed 10 frames of speech
        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "What is the difference between val and var in Kotlin?"
        repeat(10) {
            engine.processAudioChunk(window)
        }

        assertTrue(engine.isSpeechActive.value)
        assertEquals("What is the difference between val and var in Kotlin?", engine.partialTranscript.value)

        // 2. Feed 25 frames of silence to trigger endpointing
        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "What is the difference between val and var in Kotlin?"
        
        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        repeat(40) {
            engine.processAudioChunk(window)
        }

        assertFalse(engine.isSpeechActive.value)
        assertEquals("", engine.partialTranscript.value)
        assertEquals(1, completedQuestions.size)
        assertEquals("What is the difference between val and var in Kotlin?", completedQuestions.first())

        job.cancel()
    }

    @Test
    fun testShortNoiseFilteredOut() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "Hello"
        repeat(10) {
            engine.processAudioChunk(window)
        }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "Hello"

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        repeat(40) {
            engine.processAudioChunk(window)
        }

        assertEquals(0, completedQuestions.size)
        job.cancel()
    }

    @Test
    fun testForceEndpointEmitsImmediately() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "How does the Garbage Collector work?"
        repeat(10) {
            engine.processAudioChunk(window)
        }

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        // Call forceEndpoint with 0 frames of silence
        fakeBackend.currentFinal = "How does the Garbage Collector work?"
        val segment = engine.forceEndpoint()

        assertNotNull(segment)
        assertEquals("How does the Garbage Collector work?", segment!!.text)
        assertEquals(1, completedQuestions.size)

        job.cancel()
    }

    @Test
    fun testHotwordsGenerationAndAutoRefreshToLowerCase() {
        val tempDir = File.createTempFile("test_models", "")
        tempDir.delete()
        tempDir.mkdirs()
        try {
            val testConfig = AppConfig(tempDir)
            val hotwordsFile = testConfig.ensureHotwordsFile()
            
            assertTrue(hotwordsFile.exists())
            assertTrue(hotwordsFile.length() > 0)
            
            val content = hotwordsFile.readText()
            // Verify that all words in v1.1 hotwords are lowercase
            assertFalse(content.any { it.isUpperCase() })
            assertTrue(content.contains("kotlin"))
            assertTrue(content.contains("coroutines"))
            assertTrue(content.contains("dependency injection"))

            // Verify auto-refresh if legacy uppercase file is present
            hotwordsFile.writeText("LEGACY\nUPPERCASE\nTERMS")
            assertTrue(hotwordsFile.readText().contains("UPPERCASE"))
            
            val refreshed = testConfig.ensureHotwordsFile()
            val refreshedContent = refreshed.readText()
            assertFalse(refreshedContent.any { it.isUpperCase() })
            assertTrue(refreshedContent.contains("kotlin"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testConversationalQuestionTriggers() {
        val q1 = com.local.interviewassistant.core.model.TranscriptSegment(
            text = "can you talk about how you handle distributed systems",
            isFinal = true
        )
        assertTrue(q1.isLikelyQuestion)

        val q2 = com.local.interviewassistant.core.model.TranscriptSegment(
            text = "tell me about a time you resolved a major bug",
            isFinal = true
        )
        assertTrue(q2.isLikelyQuestion)

        val q3 = com.local.interviewassistant.core.model.TranscriptSegment(
            text = "what is the difference between coroutines and threads",
            isFinal = true
        )
        assertTrue(q3.isLikelyQuestion)

        val nonQ = com.local.interviewassistant.core.model.TranscriptSegment(
            text = "just saying hello",
            isFinal = true
        )
        assertFalse(nonQ.isLikelyQuestion)
    }

    @Test
    fun testTransientSpeechFramesResetOnSilence() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        // Feed only 4 frames of transient noise (below 8 minSpeechFrames)
        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "cough"
        repeat(4) {
            engine.processAudioChunk(window)
        }
        assertFalse(engine.isSpeechActive.value)

        // Feed silence: speechFrameCount should reset back to 0
        fakeBackend.isSpeechNext = false
        engine.processAudioChunk(window)

        // Now feed 4 more frames: since count was reset, it shouldn't add up to 8
        fakeBackend.isSpeechNext = true
        repeat(4) {
            engine.processAudioChunk(window)
        }
        assertFalse(engine.isSpeechActive.value)
    }

    class FakeOfflineRescorer(
        override var isAvailable: Boolean = true,
        var rescoreResult: String? = null
    ) : OfflineRescorerBackend {
        var recordedSamplesCount = 0
        var recordedSampleRate = 0
        var releaseCalled = false

        override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)

        override fun rescoreUtterance(samples: FloatArray, sampleRate: Int): String? {
            recordedSamplesCount = samples.size
            recordedSampleRate = sampleRate
            return rescoreResult
        }

        override fun release() {
            releaseCalled = true
        }
    }

    @Test
    fun testQuestionPunctuationEngineAcronymsAndTruecasing() {
        val q1 = QuestionPunctuationEngine.process("can you explain rest api and sql with mvvm")
        assertEquals("Can you explain REST API and SQL with MVVM?", q1)

        val q2 = QuestionPunctuationEngine.process("how does kotlin coroutine dispatchers work")
        assertEquals("How does Kotlin Coroutine dispatchers work?", q2)

        val q3 = QuestionPunctuationEngine.process("whats the difference between room and retrofit")
        assertEquals("What's the difference between Room and Retrofit?", q3)

        val q4 = QuestionPunctuationEngine.process("tell me about your experience with docker and k8s")
        assertEquals("Tell me about your experience with Docker and K8s?", q4)

        val stmt1 = QuestionPunctuationEngine.process("i dont think this is an issue")
        assertEquals("I don't think this is an issue.", stmt1)

        val stmt2 = QuestionPunctuationEngine.process("we achieved 60 fps with low ttft")
        assertEquals("We achieved 60 FPS with low TTFT.", stmt2)
    }

    @Test
    fun testTwoPassRescorerWhenAvailable() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val fakeRescorer = FakeOfflineRescorer(
            isAvailable = true,
            rescoreResult = "can you explain kotlin stateflow and sharedflow in android"
        )
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend, fakeRescorer)

        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        // Feed 10 frames of speech
        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "can you explain kotlin stateflow"
        repeat(10) {
            engine.processAudioChunk(window)
        }

        // Fast streaming check
        assertEquals("can you explain kotlin stateflow", engine.partialTranscript.value)

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        // Feed silence to trigger endpointing
        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "can you explain kotlin stateflow"
        repeat(40) {
            engine.processAudioChunk(window)
        }

        // Verify rescorer received the accumulated audio frames
        assertTrue(fakeRescorer.recordedSamplesCount >= 10 * dummyConfig.vadWindowSizeSamples)
        assertEquals(dummyConfig.sampleRateHz, fakeRescorer.recordedSampleRate)

        // Verify final emitted question was rescored AND properly punctuated & truecased!
        assertEquals(1, completedQuestions.size)
        assertEquals("Can you explain Kotlin StateFlow and SharedFlow in Android?", completedQuestions.first())

        job.cancel()
    }

    @Test
    fun testTwoPassFallbackToStreamingWhenRescorerReturnsNull() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val fakeRescorer = FakeOfflineRescorer(
            isAvailable = true,
            rescoreResult = null // Rescorer failed or had low confidence
        )
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend, fakeRescorer)

        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "what is clean architecture in kotlin"
        repeat(10) {
            engine.processAudioChunk(window)
        }

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "what is clean architecture in kotlin"
        repeat(40) {
            engine.processAudioChunk(window)
        }

        // Must fall back to streaming transcript, but STILL punctuated and truecased!
        assertEquals(1, completedQuestions.size)
        assertEquals("What is clean architecture in Kotlin?", completedQuestions.first())

        job.cancel()
    }

    @Test
    fun testUtteranceAudioBufferMemoryCapping() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val fakeRescorer = FakeOfflineRescorer(isAvailable = true, rescoreResult = "success")
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend, fakeRescorer)

        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        // Feed 1500 frames (~48 seconds of speech, exceeding the 30s cap)
        fakeBackend.isSpeechNext = true
        repeat(1500) {
            engine.processAudioChunk(window)
        }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "success"
        repeat(40) {
            engine.processAudioChunk(window)
        }

        // Verify maxUtteranceWindows capped the buffer to ~30 seconds (30 / 0.032 = 937 windows * 512 = 479744 samples)
        assertTrue(fakeRescorer.recordedSamplesCount <= 1000 * dummyConfig.vadWindowSizeSamples)
    }

    @Test
    fun testPcm16ToFloatArrayEdgeCases() {
        // Empty array
        val emptyFloats = MicrophoneAudioCaptureSource.pcm16ToFloatArray(shortArrayOf(), 0)
        assertEquals(0, emptyFloats.size)

        // Extreme bounds and clamping
        val extremes = shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE, 0, -1, 1, 16384, -16384)
        val extremeFloats = MicrophoneAudioCaptureSource.pcm16ToFloatArray(extremes, extremes.size)
        assertEquals(7, extremeFloats.size)
        assertEquals(-1.0f, extremeFloats[0], 0.0001f) // Short.MIN_VALUE (-32768) / 32768.0 = -1.0
        assertTrue("Short.MAX_VALUE should be within [-1.0, 1.0]", extremeFloats[1] in 0.999f..1.0f)
        assertEquals(0.0f, extremeFloats[2], 0.0001f)
        assertTrue("Values must be within [-1.0, 1.0]", extremeFloats.all { it in -1.0f..1.0f })

        // Partial buffer read: buffer has 10 elements, but readCount is only 4
        val partialPcm = ShortArray(10) { 16384 }
        val partialFloats = MicrophoneAudioCaptureSource.pcm16ToFloatArray(partialPcm, 4)
        assertEquals(4, partialFloats.size)
        assertTrue(partialFloats.all { it == 0.5f })
    }

    @Test
    fun testSubWindowAndSuperWindowAudioChunks() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "Testing arbitrary chunk sizes"

        // Window size is 512 samples.
        // Feed 5 chunks of 100 samples (total 500 samples) -> no window should be processed yet
        repeat(5) {
            engine.processAudioChunk(FloatArray(100))
        }
        assertFalse(engine.isSpeechActive.value)

        // Feed another chunk of 25 samples (total 525 samples) -> 1 window of 512 processed, 13 samples remaining in buffer
        engine.processAudioChunk(FloatArray(25))

        // Feed a super-window chunk of 1337 samples:
        // Remaining 13 + 1337 = 1350 samples -> 2 windows of 512 (1024) processed, 326 samples left
        engine.processAudioChunk(FloatArray(1337))

        // Feed remaining samples to reach total of 8 speech frames (minSpeechFrames)
        repeat(5) {
            engine.processAudioChunk(FloatArray(dummyConfig.vadWindowSizeSamples))
        }

        assertTrue(engine.isSpeechActive.value)
        assertEquals("Testing arbitrary chunk sizes", engine.partialTranscript.value)
    }

    @Test
    fun testContinuousSilenceNeverTriggersSpeech() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = false

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        // Feed 100 consecutive frames of silence
        repeat(100) {
            engine.processAudioChunk(window)
        }

        assertFalse(engine.isSpeechActive.value)
        assertEquals("", engine.partialTranscript.value)
        assertEquals(0, completedQuestions.size)

        job.cancel()
    }

    @Test
    fun testSpeechHesitationAndStutter() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        // Repeat 5 times: 4 frames of speech followed by 3 frames of silence
        // Speech count should never reach 8 (minSpeechFrames) because it resets on silence
        repeat(5) {
            fakeBackend.isSpeechNext = true
            repeat(4) { engine.processAudioChunk(window) }
            assertFalse(engine.isSpeechActive.value)

            fakeBackend.isSpeechNext = false
            repeat(3) { engine.processAudioChunk(window) }
            assertFalse(engine.isSpeechActive.value)
        }

        assertFalse(engine.isSpeechActive.value)
        assertEquals("", engine.partialTranscript.value)
    }

    @Test
    fun testShortUtteranceFilteredByMinQuestionLength() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        // Feed 10 frames of speech with short transcript (< 12 chars)
        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "Yes"
        repeat(10) {
            engine.processAudioChunk(window)
        }
        assertTrue(engine.isSpeechActive.value)

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "Yes"

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        // Feed silence to trigger endpointing
        repeat(40) {
            engine.processAudioChunk(window)
        }

        // State must be reset back to inactive
        assertFalse(engine.isSpeechActive.value)
        assertEquals("", engine.partialTranscript.value)
        // But NO completedQuestion should be emitted because "Yes." length is 4 (< 12)
        assertEquals(0, completedQuestions.size)

        job.cancel()
    }

    @Test
    fun testForceEndpointWhenIdleReturnsNull() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)

        // Calling forceEndpoint when no speech has occurred
        val segment = engine.forceEndpoint()
        org.junit.Assert.assertNull(segment)
        assertFalse(engine.isSpeechActive.value)
    }

    @Test
    fun testResetMidSpeechClearsAllBuffersAndState() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "Unfinished speech"
        repeat(10) {
            engine.processAudioChunk(window)
        }
        assertTrue(engine.isSpeechActive.value)
        assertEquals("Unfinished speech", engine.partialTranscript.value)

        // Invoke reset()
        engine.reset()

        assertFalse(engine.isSpeechActive.value)
        assertEquals("", engine.partialTranscript.value)
    }

    @Test
    fun testStreamingCandidateFallbackWhenFinalIsBlank() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "How does garbage collection work in Kotlin"
        repeat(10) {
            engine.processAudioChunk(window)
        }

        // Simulate backend finalize returning blank "" but partial was present
        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = ""

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        repeat(40) {
            engine.processAudioChunk(window)
        }

        assertEquals(1, completedQuestions.size)
        assertEquals("How does garbage collection work in Kotlin?", completedQuestions.first())

        job.cancel()
    }

    @Test
    fun testRescorerExceptionGracefullyFallsBackToStreaming() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val throwingRescorer = object : OfflineRescorerBackend {
            override val isAvailable: Boolean = true
            override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
            override fun rescoreUtterance(samples: FloatArray, sampleRate: Int): String? {
                throw RuntimeException("Rescorer model corrupted")
            }
            override fun release() {}
        }
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend, throwingRescorer)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "What is dependency injection"
        repeat(10) {
            engine.processAudioChunk(window)
        }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "What is dependency injection"

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        // Even though rescorer throws, engine should catch it and cleanly fall back
        repeat(40) {
            engine.processAudioChunk(window)
        }

        assertEquals(1, completedQuestions.size)
        assertEquals("What is dependency injection?", completedQuestions.first())

        job.cancel()
    }

    @Test
    fun testRescorerBlankResultFallsBackToStreaming() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val blankRescorer = FakeOfflineRescorer(isAvailable = true, rescoreResult = "   ")
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend, blankRescorer)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "What is dependency injection"
        repeat(10) {
            engine.processAudioChunk(window)
        }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "What is dependency injection"

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        repeat(40) {
            engine.processAudioChunk(window)
        }

        assertEquals(1, completedQuestions.size)
        assertEquals("What is dependency injection?", completedQuestions.first())

        job.cancel()
    }

    @Test
    fun testRescorerDisabledSkipsRescore() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val disabledRescorer = FakeOfflineRescorer(isAvailable = false, rescoreResult = "Alternative text")
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend, disabledRescorer)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "What is clean architecture"
        repeat(10) {
            engine.processAudioChunk(window)
        }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "What is clean architecture"

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        repeat(40) {
            engine.processAudioChunk(window)
        }

        // Disabled rescorer was not called; streaming final used
        assertEquals(0, disabledRescorer.recordedSamplesCount)
        assertEquals(1, completedQuestions.size)
        assertEquals("What is clean architecture?", completedQuestions.first())

        job.cancel()
    }

    @Test
    fun testMultipleConsecutiveQuestionsInSession() = runTest {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)
        val window = FloatArray(dummyConfig.vadWindowSizeSamples)

        val completedQuestions = mutableListOf<String>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.completedQuestion.collect { (_, text) ->
                completedQuestions.add(text)
            }
        }

        // Question 1
        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "what is kotlin"
        repeat(10) { engine.processAudioChunk(window) }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "what is kotlin"
        repeat(40) { engine.processAudioChunk(window) }

        assertEquals(1, completedQuestions.size)
        assertEquals("What is Kotlin?", completedQuestions[0])

        // Question 2
        fakeBackend.isSpeechNext = true
        fakeBackend.currentPartial = "how does stateflow work"
        repeat(10) { engine.processAudioChunk(window) }

        fakeBackend.isSpeechNext = false
        fakeBackend.currentFinal = "how does stateflow work"
        repeat(40) { engine.processAudioChunk(window) }

        assertEquals(2, completedQuestions.size)
        assertEquals("How does StateFlow work?", completedQuestions[1])

        job.cancel()
    }
}

