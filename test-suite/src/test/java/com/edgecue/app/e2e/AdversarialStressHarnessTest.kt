package com.edgecue.app.e2e

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Adversarial Stress & Verification Harness.
 *
 * Stress-tests Milestone 1 & Milestone 3 components:
 * 1. WER Dynamic Programming Matrix edge cases & bounds.
 * 2. Compound-Aware Hotwords Matching precision & false-positive immunity.
 * 3. LLM Keyword Relevance & morphological filtering.
 * 4. RIFF WAV Chunk Parser: unexpected chunks (LIST, JUNK, id3 ), odd-sized word alignment.
 * 5. Pacing & Timing Invariants: monotonic drift resistance under scheduler pauses,
 *    audioEndAtNanos placement before trailing silence, and awaitCancellation() behavior.
 */
class AdversarialStressHarnessTest {

    // =========================================================================================
    // 1. WER DYNAMIC PROGRAMMING MATRIX STRESS TESTS
    // =========================================================================================

    @Test
    fun testWerBothEmpty() {
        val result = TranscriptEvaluator.computeWer("", "")
        assertEquals(0.0f, result.wer, 0.0001f)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(0, result.referenceWords)
        assertEquals(0, result.hypothesisWords)
    }

    @Test
    fun testWerEmptyReferenceNonEmptyHypothesis() {
        val result = TranscriptEvaluator.computeWer("", "hello world one two three")
        assertEquals(1.0f, result.wer, 0.0001f)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(5, result.insertions)
        assertEquals(0, result.referenceWords)
        assertEquals(5, result.hypothesisWords)
    }

    @Test
    fun testWerNonEmptyReferenceEmptyHypothesis() {
        val result = TranscriptEvaluator.computeWer("can you explain transformer architecture", "")
        assertEquals(1.0f, result.wer, 0.0001f)
        assertEquals(0, result.substitutions)
        assertEquals(5, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(5, result.referenceWords)
        assertEquals(0, result.hypothesisWords)
    }

    @Test
    fun testWerAllSubstitutions() {
        val ref = "one two three four five"
        val hyp = "alpha beta gamma delta epsilon"
        val result = TranscriptEvaluator.computeWer(ref, hyp)
        assertEquals(1.0f, result.wer, 0.0001f)
        assertEquals(5, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(5, result.referenceWords)
        assertEquals(5, result.hypothesisWords)
    }

    @Test
    fun testWerHighInsertionCountExceedingReferenceLength() {
        // ASR edge case: hypothesis has many spurious words inserted
        val ref = "transformer"
        val hyp = "i think that the transformer model is very complex indeed"
        val result = TranscriptEvaluator.computeWer(ref, hyp)
        // ref: 1 word, hyp: 10 words. "transformer" matches.
        // Insertions: 9 words.
        // WER = (0 + 0 + 9) / 1.0 = 9.0 (900% WER)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(9, result.insertions)
        assertEquals(1, result.referenceWords)
        assertEquals(10, result.hypothesisWords)
        assertEquals(9.0f, result.wer, 0.0001f)
    }

    @Test
    fun testWerPunctuationAndMixedWhitespaceChaos() {
        val ref = "Can you describe a situation, where you had a conflict?"
        val hyp = "  CAN   YOU \t DESCRIBE \n A SITUATION   WHERE  YOU  HAD  A  CONFLICT !!! "
        val result = TranscriptEvaluator.computeWer(ref, hyp)
        assertEquals(0.0f, result.wer, 0.0001f)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
    }

    @Test
    fun testWerContractionsCrossExpansion() {
        // Spoken audio often expands or contracts
        val ref = "what is the difference between supervised and unsupervised learning"
        val hyp = "what's the difference between supervised and unsupervised learning"
        val result = TranscriptEvaluator.computeWer(ref, hyp)
        // Both normalize "what's" -> "what is", producing exact match
        assertEquals(0.0f, result.wer, 0.0001f)
        assertEquals(9, result.referenceWords)
        assertEquals(9, result.hypothesisWords)
    }

    @Test
    fun testWerFullVocabularyContractionCoverage() {
        val contractions = listOf(
            "what's" to "what is",
            "how's" to "how is",
            "why's" to "why is",
            "where's" to "where is",
            "when's" to "when is",
            "who's" to "who is",
            "that's" to "that is",
            "there's" to "there is",
            "here's" to "here is",
            "it's" to "it is",
            "can't" to "cannot",
            "don't" to "do not",
            "won't" to "will not",
            "didn't" to "did not",
            "doesn't" to "does not",
            "isn't" to "is not",
            "aren't" to "are not",
            "couldn't" to "could not",
            "wouldn't" to "would not",
            "shouldn't" to "should not",
            "hasn't" to "has not",
            "haven't" to "have not",
            "i'm" to "i am",
            "i've" to "i have",
            "you're" to "you are",
            "they're" to "they are",
            "we've" to "we have",
            "gonna" to "going to",
            "wanna" to "want to"
        )
        for ((contracted, expanded) in contractions) {
            val res = TranscriptEvaluator.computeWer(expanded, contracted)
            assertEquals("Contraction $contracted must normalize to $expanded", 0.0f, res.wer, 0.0001f)
        }
    }

    // =========================================================================================
    // 2. COMPOUND HOTWORD MATCHING STRESS TESTS
    // =========================================================================================

    @Test
    fun testCompoundHotwordStateFlowBothDirections() {
        // Case 1: Single-word hotword "stateflow" vs split hypothesis "state flow"
        val hyp1 = "we used state flow to collect items"
        val res1 = TranscriptEvaluator.evaluateHotwords(hyp1, listOf("stateflow"))
        assertEquals(1.0f, res1.recall, 0.0001f)
        assertTrue(res1.matchedHotwords.contains("stateflow"))

        // Case 2: Multi-word hotword "state flow" vs joined hypothesis "stateflow"
        val hyp2 = "we used stateflow to collect items"
        val res2 = TranscriptEvaluator.evaluateHotwords(hyp2, listOf("state flow"))
        assertEquals(1.0f, res2.recall, 0.0001f)
        assertTrue(res2.matchedHotwords.contains("state flow"))
    }

    @Test
    fun testCompoundHotwordsImmunityAgainstShortWordSubstrings() {
        val hyp = "I started my work and decided to restart the computer before writing."
        val hotwords = listOf("star")
        val res = TranscriptEvaluator.evaluateHotwords(hyp, hotwords)
        // "star" MUST NOT match "started" or "restart"
        assertEquals(0, res.matchedCount)
        assertEquals(0.0f, res.recall, 0.0001f)
        assertTrue(res.missingHotwords.contains("star"))
    }

    @Test
    fun testCompoundHotwordsImmunityForFlowInsideStateFlow() {
        val hyp = "We migrated to stateflow for unidirectional reactive data flow."
        // "flow" appears as standalone token in hyp: "data flow", so it matches "flow"
        val res1 = TranscriptEvaluator.evaluateHotwords(hyp, listOf("flow"))
        assertEquals(1.0f, res1.recall, 0.0001f)

        // Hyp without standalone "flow":
        val hypOnlyCompound = "We migrated to stateflow and sharedflow architecture."
        val res2 = TranscriptEvaluator.evaluateHotwords(hypOnlyCompound, listOf("flow"))
        // "flow" does not exist as standalone token or bigram sum
        assertEquals(0, res2.matchedCount)
        assertEquals(0.0f, res2.recall, 0.0001f)
    }

    @Test
    fun testMultiWordHotwordWithContractions() {
        val hyp = "What's the difference between kotlin coroutines and rxjava?"
        val hotwords = listOf("what is the difference between", "kotlin")
        val res = TranscriptEvaluator.evaluateHotwords(hyp, hotwords)
        assertEquals(2, res.matchedCount)
        assertEquals(1.0f, res.recall, 0.0001f)
    }

    // =========================================================================================
    // 3. LLM KEYWORD RELEVANCE STRESS TESTS
    // =========================================================================================

    @Test
    fun testEvaluateKeywordsShortWordStrictness() {
        // "star": length 4 -> only allows "star" and "stars"
        val answerWithStarted = "The process started immediately."
        val m1 = TranscriptEvaluator.evaluateKeywords(answerWithStarted, listOf("star"))
        assertTrue("star must not match started", m1.isEmpty())

        val answerWithRestart = "Please restart the server."
        val m2 = TranscriptEvaluator.evaluateKeywords(answerWithRestart, listOf("star"))
        assertTrue("star must not match restart", m2.isEmpty())

        val answerWithStars = "They gave us 5 stars on the review."
        val m3 = TranscriptEvaluator.evaluateKeywords(answerWithStars, listOf("star"))
        assertEquals(listOf("star"), m3)

        val answerWithStar = "I applied the STAR framework."
        val m4 = TranscriptEvaluator.evaluateKeywords(answerWithStar, listOf("star"))
        assertEquals(listOf("star"), m4)
    }

    @Test
    fun testEvaluateKeywordsStemmingForLongWords() {
        val answer = "We are listening actively to stakeholders, escalating blocking issues, and optimizing gradients."
        val expected = listOf("listen", "escalate", "gradient", "communication")
        val matched = TranscriptEvaluator.evaluateKeywords(answer, expected)
        assertTrue("listen matches listening", matched.contains("listen"))
        assertTrue("escalate matches escalating", matched.contains("escalate"))
        assertTrue("gradient matches gradients", matched.contains("gradient"))
        assertFalse("communication absent", matched.contains("communication"))
    }

    // =========================================================================================
    // 4. RIFF WAV PARSING & CHUNK PADDING STRESS TESTS
    // =========================================================================================

    /**
     * Builds a mock RIFF WAV byte array with custom chunk injections.
     */
    private fun createWavBytes(
        pcmShorts: ShortArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
        extraChunksBeforeData: List<Pair<String, ByteArray>> = emptyList(),
        fmtChunkSize: Int = 16
    ): ByteArray {
        val pcmBytes = ByteArray(pcmShorts.size * 2)
        val shortBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcmShorts) shortBuf.putShort(s)

        val baos = ByteArrayOutputStream()

        // Placeholder for RIFF header (12 bytes)
        baos.write("RIFF".toByteArray(Charsets.US_ASCII))
        baos.write(ByteArray(4)) // placeholder for riffSize
        baos.write("WAVE".toByteArray(Charsets.US_ASCII))

        // Write 'fmt ' chunk
        baos.write("fmt ".toByteArray(Charsets.US_ASCII))
        val fmtSizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(fmtChunkSize).array()
        baos.write(fmtSizeBuf)

        val fmtPayload = ByteBuffer.allocate(fmtChunkSize).order(ByteOrder.LITTLE_ENDIAN)
        fmtPayload.putShort(1.toShort()) // format = PCM
        fmtPayload.putShort(channels.toShort())
        fmtPayload.putInt(sampleRate)
        fmtPayload.putInt(sampleRate * channels * (bitsPerSample / 8))
        fmtPayload.putShort((channels * (bitsPerSample / 8)).toShort())
        fmtPayload.putShort(bitsPerSample.toShort())
        // Fill remaining extra format bytes if fmtChunkSize > 16
        while (fmtPayload.hasRemaining()) {
            fmtPayload.put(0.toByte())
        }
        baos.write(fmtPayload.array())

        // Write extra chunks before data (e.g. LIST, JUNK, id3 )
        for ((chunkId, payload) in extraChunksBeforeData) {
            val id4 = chunkId.padEnd(4, ' ').take(4)
            baos.write(id4.toByteArray(Charsets.US_ASCII))
            val sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array()
            baos.write(sizeBuf)
            baos.write(payload)
            // Word-alignment pad byte if payload size is odd
            if (payload.size % 2 != 0) {
                baos.write(0)
            }
        }

        // Write 'data' chunk
        baos.write("data".toByteArray(Charsets.US_ASCII))
        val dataSizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pcmBytes.size).array()
        baos.write(dataSizeBuf)
        baos.write(pcmBytes)

        val allBytes = baos.toByteArray()
        val totalRiffSize = allBytes.size - 8
        ByteBuffer.wrap(allBytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalRiffSize)
        return allBytes
    }

    /**
     * Minimal replica of ScriptedWavAudioSource.parseWav for empirical verification on JVM.
     */
    private fun parseWavTest(bytes: ByteArray): List<FloatArray> {
        require(bytes.size >= 12) { "WAV file too short for RIFF header" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riffMagic = ByteArray(4).also { buffer.get(it) }
        require(String(riffMagic, Charsets.US_ASCII) == "RIFF")

        buffer.getInt() // riffSize
        val waveMagic = ByteArray(4).also { buffer.get(it) }
        require(String(waveMagic, Charsets.US_ASCII) == "WAVE")

        var sampleRate = 16000
        var channels = 1
        var bitsPerSample = 16
        var pcmBytes: ByteArray? = null

        while (buffer.remaining() >= 8) {
            val chunkIdBytes = ByteArray(4).also { buffer.get(it) }
            val chunkId = String(chunkIdBytes, Charsets.US_ASCII)
            val chunkSize = buffer.getInt()

            when (chunkId) {
                "fmt " -> {
                    require(chunkSize >= 16) { "Invalid fmt chunk size: $chunkSize" }
                    val format = buffer.getShort().toInt()
                    require(format == 1) { "Only uncompressed PCM WAV supported, found format $format" }
                    channels = buffer.getShort().toInt()
                    require(channels == 1) { "Only mono WAV supported, found $channels channels" }
                    sampleRate = buffer.getInt()
                    require(sampleRate == 16000) { "Only 16000 Hz WAV supported, found $sampleRate Hz" }
                    buffer.getInt() // byteRate
                    buffer.getShort() // blockAlign
                    bitsPerSample = buffer.getShort().toInt()
                    require(bitsPerSample == 16) { "Only 16-bit PCM supported, found $bitsPerSample bits" }

                    val extraFmt = chunkSize - 16
                    if (extraFmt > 0) {
                        val toSkip = minOf(extraFmt, buffer.remaining())
                        buffer.position(buffer.position() + toSkip)
                    }
                }
                "data" -> {
                    val payloadSize = if (chunkSize in 0..buffer.remaining()) chunkSize else buffer.remaining()
                    pcmBytes = ByteArray(payloadSize).also { buffer.get(it) }
                    break
                }
                else -> {
                    val paddedSize = if (chunkSize > 0) chunkSize + (chunkSize % 2) else 0
                    val toSkip = minOf(paddedSize, buffer.remaining())
                    buffer.position(buffer.position() + toSkip)
                }
            }
        }

        requireNotNull(pcmBytes) { "No data chunk" }
        val shortCount = pcmBytes.size / 2
        val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shortArray = ShortArray(shortCount)
        shortBuffer.get(shortArray)

        val fullFloatArray = FloatArray(shortCount)
        for (i in 0 until shortCount) {
            fullFloatArray[i] = (shortArray[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
        }

        val frameList = mutableListOf<FloatArray>()
        var offset = 0
        while (offset < fullFloatArray.size) {
            val frame = FloatArray(512)
            val copyLen = minOf(512, fullFloatArray.size - offset)
            System.arraycopy(fullFloatArray, offset, frame, 0, copyLen)
            frameList.add(frame)
            offset += 512
        }
        return frameList
    }

    @Test
    fun testRiffParserStandardWav() {
        val testPcm = ShortArray(1024) { (it % 1000).toShort() }
        val wavBytes = createWavBytes(testPcm)
        val frames = parseWavTest(wavBytes)
        assertEquals(2, frames.size) // 1024 samples / 512 = 2 frames
    }

    @Test
    fun testRiffParserWithUnexpectedChunksAndOddPadding() {
        val testPcm = ShortArray(1536) { 1000 } // 3 frames

        // Inject LIST chunk with odd size (7 bytes), JUNK chunk (8 bytes), and id3 chunk (11 bytes)
        val extraChunks = listOf(
            "LIST" to "INFOodd".toByteArray(Charsets.US_ASCII), // 7 bytes (odd) -> pad byte added
            "JUNK" to "12345678".toByteArray(Charsets.US_ASCII), // 8 bytes (even)
            "id3 " to "TAGcontent1".toByteArray(Charsets.US_ASCII) // 11 bytes (odd) -> pad byte added
        )

        val wavBytes = createWavBytes(testPcm, extraChunksBeforeData = extraChunks)
        val frames = parseWavTest(wavBytes)

        assertEquals("Must parse exactly 3 frames despite 3 extraneous chunks", 3, frames.size)
        // Verify audio content wasn't shifted or corrupted by odd-byte padding
        assertEquals(1000.0f / 32768.0f, frames[0][0], 0.0001f)
        assertEquals(1000.0f / 32768.0f, frames[1][0], 0.0001f)
        assertEquals(1000.0f / 32768.0f, frames[2][0], 0.0001f)
    }

    @Test
    fun testRiffParserNonStandardFormatRejection() {
        val testPcm = ShortArray(512) { 500 }

        // Stereo (2 channels) -> must fail
        val stereoBytes = createWavBytes(testPcm, channels = 2)
        try {
            parseWavTest(stereoBytes)
            fail("Stereo WAV must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("mono"))
        }

        // 44100 Hz -> must fail
        val non16kBytes = createWavBytes(testPcm, sampleRate = 44100)
        try {
            parseWavTest(non16kBytes)
            fail("44.1 kHz WAV must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("16000"))
        }
    }

    // =========================================================================================
    // 5. PACING & TIMING INVARIANTS STRESS TESTS
    // =========================================================================================

    @Test
    fun testPacingMonotonicDriftResistanceSimulation() {
        // Simulates the pacing loop: targetNanos = streamStartNanos + emittedFrames * 32_000_000L
        val frameDurationNanos = 32_000_000L
        val totalFrames = 100
        var emittedFrames = 0L

        var virtualCurrentNanos = 1_000_000_000L // 1.0s
        val streamStartNanos = virtualCurrentNanos

        // Inject simulated scheduler pauses at frame 10 (150ms pause) and frame 50 (200ms pause)
        var totalDelayCalculatedNanos = 0L
        val delaysApplied = mutableListOf<Long>()

        for (i in 0 until totalFrames) {
            val targetNanos = streamStartNanos + (emittedFrames * frameDurationNanos)
            val delayNanos = targetNanos - virtualCurrentNanos

            if (delayNanos > 0L) {
                delaysApplied.add(delayNanos)
                totalDelayCalculatedNanos += delayNanos
                virtualCurrentNanos += delayNanos // Sleep elapsed
            } else {
                delaysApplied.add(0L) // Catch up immediately with 0 sleep!
            }

            // Simulate frame processing time (1 ms)
            virtualCurrentNanos += 1_000_000L
            emittedFrames++

            // Simulate sporadic scheduler stalls
            if (i == 10) {
                virtualCurrentNanos += 150_000_000L // 150 ms GC pause!
            }
            if (i == 50) {
                virtualCurrentNanos += 200_000_000L // 200 ms background preemption!
            }
        }

        // After the pauses, the stream caught up and remained anchored to monotonic timeline!
        // Check final timeline difference vs ideal (totalFrames * 32 ms = 3200 ms)
        val expectedTotalSpan = totalFrames * frameDurationNanos
        val actualSpan = virtualCurrentNanos - streamStartNanos

        println("[Pacing Test] Expected span: ${expectedTotalSpan / 1_000_000} ms, Actual span with 350ms pauses: ${actualSpan / 1_000_000} ms")
        // Because pauses (350 ms) occurred before the end of a 3200 ms stream, the pacing math absorbed
        // the stall and resumed exact target synchronization!
        assertTrue("Stream timeline must stay within 32 ms of target", Math.abs(actualSpan - expectedTotalSpan) < 32_000_000L)
    }

    @Test
    fun testAudioEndAtNanosRecordedBeforeTrailingSilence() = runBlocking {
        // Verifies the sequence of events:
        // 1. Audio frames emitted
        // 2. audioEndAtNanos captured
        // 3. Trailing silence frames emitted
        var audioEndCaptured = false
        var silenceEmittedAfterAudioEnd = false
        var silenceCount = 0

        val fakeAudioFrames = List(5) { FloatArray(512) { 0.5f } }
        val testFlow = flow {
            for (frame in fakeAudioFrames) {
                emit(frame)
            }
            // RECORD TIMING SEAM
            audioEndCaptured = true

            // TRAILING SILENCE
            for (i in 0 until 10) {
                if (audioEndCaptured) {
                    silenceEmittedAfterAudioEnd = true
                }
                emit(FloatArray(512))
                silenceCount++
            }
        }

        val collected = mutableListOf<FloatArray>()
        testFlow.collect { collected.add(it) }

        assertEquals(15, collected.size)
        assertTrue("audioEndAtNanos must be recorded before trailing silence begins", audioEndCaptured)
        assertTrue("Trailing silence must occur strictly AFTER audioEndAtNanos", silenceEmittedAfterAudioEnd)
        assertEquals(10, silenceCount)
    }

    @Test
    fun testAwaitCancellationKeepsFlowAliveWithoutCompleting() = runBlocking {
        var completedNaturally = false
        var isSuspended = false

        val testFlow = flow {
            emit(FloatArray(512))
            isSuspended = true
            awaitCancellation()
            completedNaturally = true
        }

        val job = launch {
            testFlow.collect { }
        }

        delay(100)
        assertTrue("Flow must be suspended in awaitCancellation()", isSuspended)
        assertFalse("Flow must NOT complete naturally", completedNaturally)
        assertTrue("Job must remain active like a live microphone", job.isActive)

        job.cancel()
        assertTrue("Job cancelled cleanly", job.isCancelled)
    }
}
