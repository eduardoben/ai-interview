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
        var isReleased = false

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

        override fun release() {
            isReleased = true
        }
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
        // Min silence frames = 25
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

        repeat(25) {
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

        repeat(25) {
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
    fun testReleaseDelegatesToBackend() {
        val fakeBackend = FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)

        engine.release()

        assertTrue(fakeBackend.isReleased)
    }
}
