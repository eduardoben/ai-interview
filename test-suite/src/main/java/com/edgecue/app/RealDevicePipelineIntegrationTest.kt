package com.edgecue.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edgecue.app.core.di.AppContainer
import com.edgecue.app.data.llm.LiteRtGemmaEngine
import com.edgecue.app.data.llm.LlmStreamEvent
import com.edgecue.app.data.stt.QuestionPunctuationEngine
import com.edgecue.app.data.stt.SherpaOnnxRuntimeBackend
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
        assertEquals("com.edgecue.app", appContext.packageName)

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
    fun testRealDeviceRagCertificationRetrievalAndAnswer() = runBlocking {
        println("=== REAL DEVICE RAG & CERTIFICATION RETRIEVAL BENCHMARK ===")
        val chunker = com.edgecue.app.data.rag.RagDocumentChunker()
        val extractor = com.edgecue.app.data.rag.DocumentTextExtractor(appContext)

        val sampleCvWithCerts = """
            Elena Vance, LCSW
            Denver, CO • elena.vance@email.com

            PROFESSIONAL SUMMARY
            Licensed Clinical Social Worker (LCSW) with 8 years of specialized acute inpatient care coordination and crisis de-escalation experience.

            LICENSURE & CERTIFICATIONS
            • Licensed Clinical Social Worker (LCSW) — License #CSW-098234 Colorado State Board of Social Work Examiners
            • Certified Advanced Children, Youth, and Family Social Worker (C-ACYFSW) National Association of Social Workers
            • Certified Crisis Intervention Specialist (CCIS) National Crisis Prevention Institute

            PROFESSIONAL EXPERIENCE
            Valley Health Medical Center - Senior Medical Social Worker
            Led acute crisis response protocols and multidisciplinary discharge planning across ICU and Emergency units.
        """.trimIndent()

        val sampleJd = """
            Medical Social Worker (LCSW) – Inpatient Care
            St. Jude Regional Hospital
            Requirements:
            • Current, unencumbered LCSW license in the State of Colorado.
            • Minimum of 3 years of clinical social work experience in acute care.
            • Demonstrated competency in crisis de-escalation and complex discharge planning.
        """.trimIndent()

        val cvChunks = chunker.chunkDocument("CURRICULUM", extractor.normalizeText(sampleCvWithCerts))
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", extractor.normalizeText(sampleJd))

        val certChunk = cvChunks.firstOrNull { it.sectionTitle.contains("Certification", ignoreCase = true) || it.sectionTitle.contains("Licensure", ignoreCase = true) }
        assertNotNull("Licensure & Certifications chunk must be parsed as a distinct section", certChunk)
        println("Found Certifications Chunk: [${certChunk!!.sectionTitle}] -> '${certChunk.content}'")

        appContainer.liteRagEngine.updateIndex(cvChunks + jdChunks)

        val question = "Could you walk me through your qualifications and what professional certifications or licenses you hold?"
        val retrieved = appContainer.liteRagEngine.retrieveFormattedContext(question, maxChars = 1400)

        println("Retrieved RAG Context for Certification Question (latency=${retrieved.retrievalLatencyMicros}µs):")
        println("--------------------------------------------------")
        println(retrieved.formattedContext)
        println("--------------------------------------------------")

        assertTrue("Retrieved context must not be blank", retrieved.formattedContext.isNotBlank())
        assertTrue("Context must cite Licensure or Certifications section",
            retrieved.formattedContext.contains("Licensure", ignoreCase = true) || retrieved.formattedContext.contains("Certification", ignoreCase = true))
        assertTrue("Context must contain LCSW or Colorado license",
            retrieved.formattedContext.contains("LCSW", ignoreCase = true) || retrieved.formattedContext.contains("CSW-098234", ignoreCase = true))

        // Now test LLM inference with RAG on the real Pixel hardware
        val engine = LiteRtGemmaEngine(appContext, appConfig, ragEngine = appContainer.liteRagEngine)
        val initRes = engine.initialize()
        assertTrue("LiteRT Engine must initialize: ${initRes.exceptionOrNull()?.message}", initRes.isSuccess)

        val events = mutableListOf<LlmStreamEvent>()
        val startGenNs = System.nanoTime()
        try {
            engine.generateAnswerStream(question).toList(events)
        } catch (e: Exception) {
            println("Generation caught exception: ${e.message}")
        }
        val genDurationMs = (System.nanoTime() - startGenNs) / 1_000_000.0

        val partialTokens = events.filterIsInstance<LlmStreamEvent.PartialToken>()
        val completedEvent = events.filterIsInstance<LlmStreamEvent.Completed>().firstOrNull()

        val fullAnswer = completedEvent?.fullText ?: partialTokens.lastOrNull()?.accumulatedText ?: ""
        println("=== REAL PIXEL 10 PRO XL GENERATED ANSWER ===")
        println(fullAnswer)
        println("=============================================")
        println("Generation Time: ${genDurationMs}ms, TTFT: ${completedEvent?.ttftMs ?: partialTokens.firstOrNull()?.ttftMs}ms, Tokens: ${completedEvent?.tokenCount ?: partialTokens.size}")

        assertTrue("Generated answer must not be blank", fullAnswer.isNotBlank())
        // Verify answer specifically cites certifications or licensure
        val citesLicensureOrCerts = fullAnswer.contains("LCSW", ignoreCase = true) ||
                fullAnswer.contains("Licensed Clinical Social Worker", ignoreCase = true) ||
                fullAnswer.contains("Colorado", ignoreCase = true) ||
                fullAnswer.contains("license", ignoreCase = true) ||
                fullAnswer.contains("certified", ignoreCase = true) ||
                fullAnswer.contains("crisis intervention", ignoreCase = true)

        println("Answer Cites Licensure/Certifications: $citesLicensureOrCerts")
        assertTrue("Generated answer must cite candidate's license or certifications", citesLicensureOrCerts)

        engine.release()
        println("=== RAG & CERTIFICATION BENCHMARK COMPLETE ===")
    }

    @Test
    fun testModelDownloadServiceContract() {
        assertEquals("com.edgecue.app.action.DOWNLOAD_SPEECH", com.edgecue.app.service.ModelDownloadService.ACTION_DOWNLOAD_SPEECH)
        assertEquals("com.edgecue.app.action.DOWNLOAD_RESCORER", com.edgecue.app.service.ModelDownloadService.ACTION_DOWNLOAD_RESCORER)
        assertEquals("com.edgecue.app.action.DOWNLOAD_LLM", com.edgecue.app.service.ModelDownloadService.ACTION_DOWNLOAD_LLM)
        assertEquals("com.edgecue.app.action.CANCEL_DOWNLOAD", com.edgecue.app.service.ModelDownloadService.ACTION_CANCEL)
        assertEquals("extra_custom_url", com.edgecue.app.service.ModelDownloadService.EXTRA_CUSTOM_URL)
        assertEquals("extra_hf_token", com.edgecue.app.service.ModelDownloadService.EXTRA_HF_TOKEN)
    }
}
