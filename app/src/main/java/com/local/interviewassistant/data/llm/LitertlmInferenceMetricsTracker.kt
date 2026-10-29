package com.local.interviewassistant.data.llm

import android.util.Log

class LitertlmInferenceMetricsTracker {
    private var startTimeMs = 0L
    private var firstTokenTimeMs = 0L
    private var stopTimeMs = 0L
    private var tokenCount = 0

    fun startTracking() {
        startTimeMs = System.currentTimeMillis()
        firstTokenTimeMs = 0L
        stopTimeMs = 0L
        tokenCount = 0
    }

    fun recordToken() {
        if (firstTokenTimeMs == 0L) {
            firstTokenTimeMs = System.currentTimeMillis()
        }
        tokenCount++
    }

    fun stopTracking() {
        stopTimeMs = System.currentTimeMillis()
    }

    fun getTtftMs(): Long {
        if (firstTokenTimeMs == 0L || startTimeMs == 0L) return 0L
        return (firstTokenTimeMs - startTimeMs).coerceAtLeast(1L)
    }

    fun getTotalDurationMs(): Long {
        if (stopTimeMs == 0L || startTimeMs == 0L) return 0L
        return (stopTimeMs - startTimeMs).coerceAtLeast(1L)
    }

    fun getTokenCount(): Int = tokenCount

    fun getDecodeTokensPerSecond(): Float {
        if (firstTokenTimeMs == 0L || stopTimeMs == 0L) return 0f
        val decodeDurationMs = stopTimeMs - firstTokenTimeMs
        if (decodeDurationMs <= 0L) return 0f
        // the first token is part of prefill, so decode tokens are tokenCount - 1
        val decodeTokens = (tokenCount - 1).coerceAtLeast(0)
        return decodeTokens / (decodeDurationMs / 1000f)
    }

    fun logMetrics(tag: String = "LitertlmMetrics") {
        val ttft = getTtftMs()
        val totalMs = getTotalDurationMs()
        val decodeTps = getDecodeTokensPerSecond()
        Log.i(tag, "Inference completed. TTFT: $ttft ms | Total: $totalMs ms | Decode TPS: $decodeTps tokens/s | Total Tokens: $tokenCount")
    }
}


