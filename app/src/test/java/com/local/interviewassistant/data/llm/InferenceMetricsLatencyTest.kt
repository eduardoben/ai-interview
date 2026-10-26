package com.local.interviewassistant.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit and latency benchmark test suite for LLM inference metrics,
 * TTFT (Time To First Token) tracking, and decode throughput calculation.
 */
class InferenceMetricsLatencyTest {

    @Test
    fun testTtftAndTotalDurationTracking() {
        val tracker = LitertlmInferenceMetricsTracker()
        tracker.startTracking()

        // Simulate 50ms prefill delay before first token
        Thread.sleep(50)
        tracker.recordToken()

        val ttft = tracker.getTtftMs()
        assertTrue("TTFT ($ttft ms) should be >= 45 ms", ttft >= 45L)

        // Simulate subsequent 4 tokens with 10ms intervals
        repeat(4) {
            Thread.sleep(10)
            tracker.recordToken()
        }

        tracker.stopTracking()

        val totalDuration = tracker.getTotalDurationMs()
        assertTrue("Total duration ($totalDuration ms) should be >= 80 ms", totalDuration >= 80L)
        assertTrue("Total duration should be >= TTFT", totalDuration >= ttft)

        val tps = tracker.getDecodeTokensPerSecond()
        assertTrue("Decode TPS ($tps) should be positive", tps > 0f)
    }

    @Test
    fun testDecodeTokensPerSecondCalculation() {
        val tracker = LitertlmInferenceMetricsTracker()
        tracker.startTracking()

        // 1st token at t=0
        tracker.recordToken()

        // Record 10 more tokens (total 11 tokens = 10 decode tokens)
        Thread.sleep(100)
        repeat(10) {
            tracker.recordToken()
        }
        tracker.stopTracking()

        val tps = tracker.getDecodeTokensPerSecond()
        // 10 decode tokens in ~100ms should yield ~100 tokens/sec (allowing range 50 - 250 on JVM timing variance)
        assertTrue("Decode TPS ($tps tokens/s) should be between 30 and 300", tps in 30f..300f)
    }

    @Test
    fun testEdgeCasesZeroTokens() {
        val tracker = LitertlmInferenceMetricsTracker()
        // Before start
        assertEquals(0L, tracker.getTtftMs())
        assertEquals(0L, tracker.getTotalDurationMs())
        assertEquals(0f, tracker.getDecodeTokensPerSecond(), 0.001f)

        // After start without any tokens
        tracker.startTracking()
        tracker.stopTracking()
        assertEquals(0L, tracker.getTtftMs())
        assertTrue(tracker.getTotalDurationMs() >= 0L)
        assertEquals(0f, tracker.getDecodeTokensPerSecond(), 0.001f)
    }

    @Test
    fun testEdgeCasesSingleTokenOnly() {
        val tracker = LitertlmInferenceMetricsTracker()
        tracker.startTracking()
        Thread.sleep(10)
        tracker.recordToken()
        tracker.stopTracking()

        // Exactly 1 token: TTFT is recorded, but decode tokens = 0 (tokenCount - 1), so decode TPS must be 0
        assertTrue(tracker.getTtftMs() >= 5L)
        assertTrue(tracker.getTotalDurationMs() >= tracker.getTtftMs())
        assertEquals(0f, tracker.getDecodeTokensPerSecond(), 0.001f)
    }

    @Test
    fun testTrackingOverheadBenchmark() {
        val tracker = LitertlmInferenceMetricsTracker()
        tracker.startTracking()

        val iterations = 10000
        val startNs = System.nanoTime()

        for (i in 0 until iterations) {
            tracker.recordToken()
        }

        val elapsedNs = System.nanoTime() - startNs
        val perTokenNs = elapsedNs.toDouble() / iterations
        val perTokenMicroseconds = perTokenNs / 1000.0

        // Recording a token must have virtually zero overhead (< 2.0 microseconds per call)
        assertTrue(
            "Per-token tracking overhead ($perTokenMicroseconds µs) should be < 2.0 µs",
            perTokenMicroseconds < 2.0
        )
    }

    @Test
    fun testLlmStreamEventDataContracts() {
        val partial = LlmStreamEvent.PartialToken("Kotlin", "Kotlin is", 45L)
        assertEquals("Kotlin", partial.tokenChunk)
        assertEquals("Kotlin is", partial.accumulatedText)
        assertEquals(45L, partial.ttftMs)

        val completed = LlmStreamEvent.Completed("Kotlin is concise.", 45L, 180L)
        assertEquals("Kotlin is concise.", completed.fullText)
        assertEquals(45L, completed.ttftMs)
        assertEquals(180L, completed.totalDurationMs)

        val error = LlmStreamEvent.Error(IllegalStateException("Out of memory"))
        assertEquals("Out of memory", error.throwable.message)
        assertNotNull(error.throwable)
    }
}
