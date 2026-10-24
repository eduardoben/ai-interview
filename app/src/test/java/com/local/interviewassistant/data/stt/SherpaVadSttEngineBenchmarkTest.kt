package com.local.interviewassistant.data.stt

import com.local.interviewassistant.core.model.AppConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class SherpaVadSttEngineBenchmarkTest {
    private val dummyConfig = AppConfig(File("."))

    @Test
    fun benchmarkProcessAudioChunk() = runTest {
        val fakeBackend = Phase3AudioSttTest.FakeVadSttBackend()
        val engine = SherpaVadSttEngine(dummyConfig, fakeBackend)

        // This simulates allocating the arrays that was described.
        // It creates a new buffer every time, and that buffer could grow if window size is large.
        // Even if we process size 512, allocating an array of size 512 + 512 + 512... until 512 is met.
        // If window is 512, and chunk size is 32, we do 16 allocations to get to 512.

        val chunk = FloatArray(32) { 0.5f }
        val numChunks = 4800000 / 32 * 50 // process a lot to get a stable number

        val timeMs = measureTimeMillis {
            repeat(numChunks) {
                engine.processAudioChunk(chunk)
            }
        }

        File("benchmark_result.txt").writeText("Benchmark time: $timeMs ms for $numChunks chunks.\n")
    }
}
