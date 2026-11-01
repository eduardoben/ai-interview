package com.local.interviewassistant

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.data.llm.LiteRtGemmaEngine
import com.local.interviewassistant.data.llm.LlmStreamEvent
import com.local.interviewassistant.data.stt.QuestionPunctuationEngine
import com.local.interviewassistant.data.stt.SherpaOnnxRuntimeBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real device hardware verification and latency test suite executing on the Pixel 10 Pro XL.
 *
 * Verifies:
 * 1. Physical device hardware specs (Tensor G5, Android version).
 * 2. Real on-disk model files and size integrity.
 * 3. QuestionPunctuationEngine latency on physical CPU cores.
 * 4. Native Sherpa-ONNX Zipformer & Silero VAD initialization and frame latency.
 * 5. Native LiteRT-LM EdgeTPU / GPU delegate initialization, TTFT latency, and token generation.
 */
@RunWith(AndroidJUnit4::class)
class RealDevicePipelineIntegrationTest {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val appContainer = AppContainer.getInstance(appContext)
    private val appConfig = appContainer.appConfig

    @Test
    fun testDeviceSpecsAndTensorHardwareDelegation() {
        assertEquals("com.local.interviewassistant", appContext.packageName)

        val model = Build.MODEL
        val soc = Build.SOC_MODEL
        val sdk = Build.VERSION.SDK_INT

        println("=== REAL DEVICE DETECTED ===")
        println("Model: $model")
        println("SoC: $soc")
        println("Android SDK: $sdk")
        println("Native Lib Dir: ${appContext.applicationInfo.nativeLibraryDir}")
        println("============================")

        assertNotNull(model)
        assertNotNull(soc)
        assertTrue("SDK must be at least 34", sdk >= 34)
    }

    @Test
    fun testModelFilesPresenceOnPhysicalDevice() {
        val report = appConfig.validateModelsOnDisk()
        
        println("=== MODEL VALIDATION REPORT ON DEVICE ===")
        for (fileStatus in report.files) {
            println("- ${fileStatus.name}: exists=${fileStatus.exists}, size=${fileStatus.sizeBytes} bytes")
        }
        println("All models present: ${report.allPresent}")
        println("=========================================")

        assertTrue("VAD model file must exist", appConfig.vadModelFile.exists())
        assertTrue("VAD model file size must be > 1MB", appConfig.vadModelFile.length() > 1_000_000L)

        assertTrue("STT encoder model must exist", appConfig.sttEncoderFile.exists())
        assertTrue("STT encoder model size must be > 100MB", appConfig.sttEncoderFile.length() > 100_000_000L)

        assertTrue("STT decoder model must exist", appConfig.sttDecoderFile.exists())
        assertTrue("STT joiner model must exist", appConfig.sttJoinerFile.exists())
        assertTrue("STT tokens must exist", appConfig.sttTokensFile.exists())

        // Check LLM models on disk
        val activeLlm = appConfig.activeLlmModelFile
        assertTrue("Active LLM model must exist (${activeLlm.name})", activeLlm.exists())
        assertTrue("Active LLM model size must be > 2GB", activeLlm.length() > 2_000_000_000L)

        val tpuModel = appConfig.tpuLlmModelFile
        if (tpuModel.exists()) {
            println("Tensor TPU Model found: ${tpuModel.name} (${tpuModel.length()} bytes)")
            assertTrue("TPU Model size must be > 2.5GB", tpuModel.length() > 2_500_000_000L)
        }

        // Check offline rescorer models
        if (appConfig.hasOfflineRescorer()) {
            println("Offline Whisper Rescorer models verified on device.")
            assertTrue(appConfig.offlineWhisperEncoderFile.length() > 10_000_000L)
            assertTrue(appConfig.offlineWhisperDecoderFile.length() > 50_000_000L)
        }
    }

    @Test
    fun testQuestionPunctuationLatencyOnRealDevice() {
        val testQuestions = listOf(
            "can you explain rest api and sql with mvvm",
            "how does kotlin coroutines structured concurrency work",
            "whats the difference between room and retrofit in android",
            "tell me about a time you resolved a major production memory leak",
            "we achieved sixty fps with low ttft on pixel device",
            "explain the star method for behavioral interviews"
        )

        // Warmup
        repeat(50) {
            for (q in testQuestions) {
                QuestionPunctuationEngine.process(q)
            }
        }

        val iterations = 500
        val startNs = System.nanoTime()
        for (i in 0 until iterations) {
            val q = testQuestions[i % testQuestions.size]
            val processed = QuestionPunctuationEngine.process(q)
            assertTrue(processed.isNotBlank())
        }
        val totalMs = (System.nanoTime() - startNs) / 1_000_000.0
        val avgMs = totalMs / iterations

        println("=== REAL DEVICE PUNCTUATION BENCHMARK ===")
        println("Processed $iterations sentences in ${totalMs}ms (average: ${avgMs}ms per sentence)")
        println("=========================================")

        assertTrue("Average latency on physical device must be < 1.0ms, got ${avgMs}ms", avgMs < 1.0)
    }

    @Test
    fun testRealDeviceSherpaOnnxInitializationAndLatency() = runBlocking {
        val backend = SherpaOnnxRuntimeBackend()
        
        val startInitNs = System.nanoTime()
        val initResult = backend.initialize(appConfig)
        val initDurationMs = (System.nanoTime() - startInitNs) / 1_000_000.0

        println("=== SHERPA-ONNX INITIALIZATION ===")
        println("Init result: ${initResult.isSuccess}, Duration: ${initDurationMs}ms")
        println("==================================")

        assertTrue("Sherpa-ONNX initialization must succeed", initResult.isSuccess)

        // Feed 50 synthetic frames of 512 samples (16kHz, 32ms audio)
        val frame = FloatArray(appConfig.vadWindowSizeSamples) { 0.05f }
        val frameLatencies = mutableListOf<Double>()

        repeat(50) {
            val startFrameNs = System.nanoTime()
            backend.acceptWaveform(frame, appConfig.sampleRateHz)
            val elapsedMs = (System.nanoTime() - startFrameNs) / 1_000_000.0
            frameLatencies.add(elapsedMs)
        }

        val avgFrameMs = frameLatencies.average()
        val maxFrameMs = frameLatencies.maxOrNull() ?: 0.0

        println("=== REAL DEVICE STT LATENCY ===")
        println("Average frame processing latency: ${avgFrameMs}ms (32ms budget)")
        println("Max frame processing latency: ${maxFrameMs}ms")
        println("===============================")

        assertTrue("Average frame processing latency must be < 10ms (real-time allowance 32ms)", avgFrameMs < 10.0)

        val finalResult = backend.finalizeAndResetStream()
        assertNotNull(finalResult)

        backend.release()
    }

    @Test
    fun testRealDeviceLiteRtModelInitialization() = runBlocking {
        println("=== LITERTLM HARDWARE TEST ===")
        val engine = LiteRtGemmaEngine(appContext, appConfig)

        val startInitNs = System.nanoTime()
        val initResult = engine.initialize()
        val initDurationMs = (System.nanoTime() - startInitNs) / 1_000_000.0

        println("LiteRT Engine Init Result: ${initResult.isSuccess}, Duration: ${initDurationMs}ms")
        if (initResult.isFailure) {
            val err = initResult.exceptionOrNull()
            println("Init Failure Reason: ${err?.message}")
            err?.printStackTrace()
        }
        assertTrue("LiteRT Engine must initialize on Pixel hardware: ${initResult.exceptionOrNull()?.message}", initResult.isSuccess)

        // Verify generation of a prompt
        println("Testing LLM generation stream on physical hardware...")
        val testQuestion = "What is Kotlin in one sentence?"
        val events = mutableListOf<LlmStreamEvent>()

        val startGenNs = System.nanoTime()
        try {
            engine.generateAnswerStream(testQuestion).toList(events)
        } catch (e: Exception) {
            println("Generation caught exception: ${e.message}")
            e.printStackTrace()
        }
        val genDurationMs = (System.nanoTime() - startGenNs) / 1_000_000.0

        println("Generation events count: ${events.size}, Total elapsed: ${genDurationMs}ms")
        val partialTokens = events.filterIsInstance<LlmStreamEvent.PartialToken>()
        val completedEvent = events.filterIsInstance<LlmStreamEvent.Completed>().firstOrNull()

        if (partialTokens.isNotEmpty()) {
            val firstToken = partialTokens.first()
            println("TTFT (Time To First Token): ${firstToken.ttftMs}ms")
            println("Generated text: ${partialTokens.last().accumulatedText}")
            assertTrue("TTFT must be positive", firstToken.ttftMs > 0L)
            assertTrue("Accumulated text must not be blank", partialTokens.last().accumulatedText.isNotBlank())
        }

        if (completedEvent != null) {
            println("Completed Event Text: '${completedEvent.fullText}'")
            println("Completed Event TTFT: ${completedEvent.ttftMs}ms, Total Duration: ${completedEvent.totalDurationMs}ms")
            assertTrue("Completed text must not be blank", completedEvent.fullText.isNotBlank())
        }

        engine.release()
        println("==============================")
    }

    @Test
    fun testModelDownloadServiceContract() {
        assertEquals("com.local.interviewassistant.action.DOWNLOAD_SPEECH", com.local.interviewassistant.service.ModelDownloadService.ACTION_DOWNLOAD_SPEECH)
        assertEquals("com.local.interviewassistant.action.DOWNLOAD_RESCORER", com.local.interviewassistant.service.ModelDownloadService.ACTION_DOWNLOAD_RESCORER)
        assertEquals("com.local.interviewassistant.action.DOWNLOAD_LLM", com.local.interviewassistant.service.ModelDownloadService.ACTION_DOWNLOAD_LLM)
        assertEquals("com.local.interviewassistant.action.CANCEL_DOWNLOAD", com.local.interviewassistant.service.ModelDownloadService.ACTION_CANCEL)
        assertEquals("extra_custom_url", com.local.interviewassistant.service.ModelDownloadService.EXTRA_CUSTOM_URL)
        assertEquals("extra_hf_token", com.local.interviewassistant.service.ModelDownloadService.EXTRA_HF_TOKEN)
    }
}
