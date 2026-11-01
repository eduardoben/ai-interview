package com.local.interviewassistant.e2e

import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.hardware.DeviceHardwareDetector
import com.local.interviewassistant.data.hardware.RamReadinessTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Parameterized end-to-end interview verification test suite running on Android Studio emulator fleet.
 *
 * Implements Milestone 3 (M3) six emulator-calibrated verification gates per interview turn:
 * - Gate 1: Pre-Flight Model Verification (6 required model files + GPU model size 2,588,147,712 bytes + rescorer status).
 * - Gate 2: VAD Real-Time Segmentation (exactly 1 segment, endpoint latency SLA <= 2000 ms).
 * - Gate 2b: Question Classification (soft-fail fallback with manual answer trigger).
 * - Gate 3: Normalized Word Error Rate (Levenshtein WER <= maxAllowedWer).
 * - Gate 4: Domain Hotwords Recall (100% recall with compound-aware matching).
 * - Gate 5: LLM Keyword Relevance & TTFT (>= max(2, 75%) keywords, TTFT <= 15000 ms on x86_64 CPU).
 * - Gate 6: Process Stability & Memory Growth (< 50 MB growth after Turn 1 warmup).
 */
@RunWith(Parameterized::class)
class ParameterizedInterviewE2ETest(
    private val turn: TurnFixture
) {

    data class TurnFixture(
        val turnId: String,
        val filename: String,
        val topic: String,
        val groundTruthRaw: String,
        val groundTruthNormalized: String,
        val mustContainHotwords: List<String>,
        val expectedLlmKeywords: List<String>,
        val expectLikelyQuestion: Boolean,
        val maxAllowedWer: Float,
        val audioDurationSec: Double
    ) {
        override fun toString(): String = "$turnId: $topic"
    }

    companion object {
        private const val EXPECTED_GPU_LLM_BYTES = 2_588_147_712L // 2.58 GB gemma-4-E2B-it.litertlm
        private const val VAD_ENDPOINT_SLA_MS = 2000L
        private const val LLM_TTFT_SLA_MS = 15_000L
        private const val MEMORY_GROWTH_LIMIT_MB = 50L

        private var warmLlmWrapper: SharedWarmLlmEngineWrapper? = null
        private var baselineMemoryMb: Long = -1L
        private var turnExecutionCount = 0

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val testContext = instrumentation.context
            val targetContext = instrumentation.targetContext

            val inputStream = runCatching {
                testContext.assets.open("audio/interview_ground_truth.json")
            }.getOrElse {
                targetContext.assets.open("audio/interview_ground_truth.json")
            }

            val jsonContent = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonContent)
            val parameters = mutableListOf<Array<Any>>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val turnId = obj.optString("turnId", obj.optString("id", "turn_${i + 1}"))
                val filename = obj.getString("filename")
                val topic = obj.optString("topic", "Interview Turn ${i + 1}")
                val groundTruthRaw = obj.getString("groundTruthRaw")
                val groundTruthNormalized = obj.getString("groundTruthNormalized")

                val hotwordsJson = obj.getJSONArray("mustContainHotwords")
                val mustContainHotwords = (0 until hotwordsJson.length()).map { hotwordsJson.getString(it) }

                val keywordsJson = obj.getJSONArray("expectedLlmKeywords")
                val expectedLlmKeywords = (0 until keywordsJson.length()).map { keywordsJson.getString(it) }

                val expectLikelyQuestion = obj.optBoolean("expectLikelyQuestion", true)
                val maxAllowedWer = obj.optDouble("maxAllowedWer", 0.15).toFloat()
                val audioDurationSec = obj.optDouble("audioDurationSec", 0.0)

                val fixture = TurnFixture(
                    turnId = turnId,
                    filename = filename,
                    topic = topic,
                    groundTruthRaw = groundTruthRaw,
                    groundTruthNormalized = groundTruthNormalized,
                    mustContainHotwords = mustContainHotwords,
                    expectedLlmKeywords = expectedLlmKeywords,
                    expectLikelyQuestion = expectLikelyQuestion,
                    maxAllowedWer = maxAllowedWer,
                    audioDurationSec = audioDurationSec
                )
                parameters.add(arrayOf(fixture))
            }

            return parameters
        }

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val appContainer = AppContainer.getInstance(targetContext)
            val appConfig = appContainer.appConfig

            val args = InstrumentationRegistry.getArguments()
            val llmMode = args.getString("e2e.llm", "auto")?.lowercase() ?: "auto"

            if (llmMode != "off") {
                val hwProfile = DeviceHardwareDetector.detectHardware(targetContext)
                val shouldWarm = (llmMode == "on") ||
                        (llmMode == "auto" && hwProfile.ramTier != RamReadinessTier.UNSUPPORTED)

                if (shouldWarm && appConfig.llmModelFile.exists() && appConfig.llmModelFile.length() > 0L) {
                    try {
                        println("[E2E] Warming shared LLM engine in @BeforeClass...")
                        warmLlmWrapper = E2ePipelineHarness.createAndWarmLlmEngineBlocking(
                            context = targetContext,
                            appConfig = appConfig
                        )
                        println("[E2E] Shared LLM engine warmed successfully.")
                    } catch (t: Throwable) {
                        println("[WARN] LLM warmup failed: ${t.message}. Test will continue with per-turn fallback.")
                    }
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            warmLlmWrapper?.let { wrapper ->
                println("[E2E] Releasing shared LLM engine in @AfterClass...")
                runCatching {
                    E2ePipelineHarness.releaseUnderlyingEngineBlocking(wrapper)
                }
                warmLlmWrapper = null
            }
        }

        private fun getVmRssKb(): Long {
            return try {
                File("/proc/self/status").useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("VmRSS:")) {
                            val parts = line.split("\\s+".toRegex())
                            if (parts.size >= 2) {
                                return@useLines parts[1].toLongOrNull() ?: 0L
                            }
                        }
                    }
                    0L
                }
            } catch (_: Throwable) {
                0L
            }
        }

        fun getCurrentTotalMemoryMb(): Long {
            val nativeHeapBytes = Debug.getNativeHeapAllocatedSize()
            val vmRssBytes = getVmRssKb() * 1024L
            return (nativeHeapBytes + vmRssBytes) / (1024L * 1024L)
        }
    }

    @Test
    fun testTurnExecution() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val appContainer = AppContainer.getInstance(targetContext)
        val appConfig = appContainer.appConfig

        // -----------------------------------------------------------------------------------------
        // Gate 1: Pre-Flight Verification Gate
        // -----------------------------------------------------------------------------------------
        assertTrue("Gate 1: VAD model file must exist", appConfig.vadModelFile.exists())
        assertTrue("Gate 1: VAD model file must be non-empty", appConfig.vadModelFile.length() > 0L)

        assertTrue("Gate 1: STT encoder model must exist", appConfig.sttEncoderFile.exists())
        assertTrue("Gate 1: STT encoder model must be non-empty", appConfig.sttEncoderFile.length() > 0L)

        assertTrue("Gate 1: STT decoder model must exist", appConfig.sttDecoderFile.exists())
        assertTrue("Gate 1: STT decoder model must be non-empty", appConfig.sttDecoderFile.length() > 0L)

        assertTrue("Gate 1: STT joiner model must exist", appConfig.sttJoinerFile.exists())
        assertTrue("Gate 1: STT joiner model must be non-empty", appConfig.sttJoinerFile.length() > 0L)

        assertTrue("Gate 1: STT tokens file must exist", appConfig.sttTokensFile.exists())
        assertTrue("Gate 1: STT tokens file must be non-empty", appConfig.sttTokensFile.length() > 0L)

        assertTrue("Gate 1: LLM model file must exist", appConfig.llmModelFile.exists())
        assertEquals(
            "Gate 1: LLM model file size must match standard GPU/CPU model ($EXPECTED_GPU_LLM_BYTES bytes)",
            EXPECTED_GPU_LLM_BYTES,
            appConfig.llmModelFile.length()
        )

        val rescorerStatus = if (appConfig.hasOfflineRescorer()) "ON" else "OFF"
        println("[E2E] Gate 1 PASSED: 6/6 model files verified. Whisper rescorer=$rescorerStatus")

        // -----------------------------------------------------------------------------------------
        // Baseline Memory Measurement
        // -----------------------------------------------------------------------------------------
        val startMemoryMb = getCurrentTotalMemoryMb()
        println("[E2E] Pre-turn memory: $startMemoryMb MB (Native Heap + VmRSS)")

        // -----------------------------------------------------------------------------------------
        // Pipeline & Audio Source Setup
        // -----------------------------------------------------------------------------------------
        val scriptedSource = runCatching {
            ScriptedWavAudioSource(testContext.assets, "audio/${turn.filename}")
        }.getOrElse {
            ScriptedWavAudioSource(targetContext.assets, "audio/${turn.filename}")
        }

        val sttEngine = appContainer.createSttEngine()
        val llmEngine = warmLlmWrapper ?: appContainer.createLlmEngine()

        val harness = E2ePipelineHarness(
            context = targetContext,
            audioSource = scriptedSource,
            sttEngine = sttEngine,
            llmEngine = llmEngine,
            appConfig = appConfig
        )

        // Asynchronously collect completed question segments from STT engine
        val emittedSegments = mutableListOf<TranscriptSegment>()
        var endpointNanos = 0L

        val segmentCollectJob = harness.scope.launch {
            sttEngine.completedQuestion.collect { segment ->
                endpointNanos = System.nanoTime()
                emittedSegments.add(segment)
            }
        }

        try {
            // Start harness and wait for AssistantState.Listening or DetectingSpeech
            harness.start().getOrThrow()
            withTimeout(10_000L) {
                harness.state.first { it is AssistantState.Listening || it is AssistantState.DetectingSpeech }
            }

            // -------------------------------------------------------------------------------------
            // Gate 2: VAD Real-Time Segmentation Gate
            // -------------------------------------------------------------------------------------
            // Await speech completion and Silero VAD endpointing (timeout 35s)
            withTimeout(35_000L) {
                while (emittedSegments.isEmpty()) {
                    delay(100L)
                }
            }

            // Wait 2500 ms debounce to verify no secondary spurious segments are produced
            delay(2500L)
            segmentCollectJob.cancel()

            assertEquals(
                "Gate 2: Exactly 1 segment must be emitted for the turn",
                1,
                emittedSegments.size
            )

            val segment = emittedSegments.first()
            val audioEndAtNanos = scriptedSource.audioEndAtNanos
            val endpointLatencyMs = if (audioEndAtNanos > 0L) {
                (endpointNanos - audioEndAtNanos) / 1_000_000L
            } else {
                0L
            }

            assertTrue(
                "Gate 2 SLA: Endpoint latency $endpointLatencyMs ms must be <= $VAD_ENDPOINT_SLA_MS ms",
                endpointLatencyMs <= VAD_ENDPOINT_SLA_MS
            )
            println("[E2E] Gate 2 PASSED: 1 segment emitted with endpoint SLA $endpointLatencyMs ms")

            // -------------------------------------------------------------------------------------
            // Gate 2b: Question Classification Gate (Soft-Fail Fallback)
            // -------------------------------------------------------------------------------------
            var questionDetectStatus = "PASS"
            var questionDetectWarning: String? = null

            if (segment.isLikelyQuestion != turn.expectLikelyQuestion) {
                questionDetectStatus = "WARNING_QUESTION_DETECT_MISS"
                questionDetectWarning = "QUESTION_DETECT_MISS: expected ${turn.expectLikelyQuestion}, got ${segment.isLikelyQuestion}"
                println("[WARN] Gate 2b: $questionDetectWarning. Manually triggering answer generation.")
                // Manually trigger answer generation so Gate 5 still executes
                harness.triggerAnswerGeneration(segment.normalizedText)
            } else {
                println("[E2E] Gate 2b PASSED: Question detection matched expectation (${turn.expectLikelyQuestion})")
            }

            // -------------------------------------------------------------------------------------
            // Gate 3: Normalized Word Error Rate (WER) Gate
            // -------------------------------------------------------------------------------------
            val werResult = TranscriptEvaluator.computeWer(turn.groundTruthNormalized, segment.normalizedText)
            println("[E2E] Gate 3 WER: ${werResult.wer} (max: ${turn.maxAllowedWer}), S=${werResult.substitutions}, D=${werResult.deletions}, I=${werResult.insertions}")
            println("      Ref: '${turn.groundTruthNormalized}'")
            println("      Hyp: '${segment.normalizedText}'")

            assertTrue(
                "Gate 3 WER violation: WER ${werResult.wer} exceeds max allowed ${turn.maxAllowedWer}. S=${werResult.substitutions}, D=${werResult.deletions}, I=${werResult.insertions}",
                werResult.wer <= turn.maxAllowedWer
            )

            // -------------------------------------------------------------------------------------
            // Gate 4: Domain Hotwords Recall Gate
            // -------------------------------------------------------------------------------------
            val hotwordResult = TranscriptEvaluator.evaluateHotwords(segment.normalizedText, turn.mustContainHotwords)
            println("[E2E] Gate 4 Hotwords Recall: ${hotwordResult.recall * 100}% (${hotwordResult.matchedCount}/${hotwordResult.totalRequired})")
            if (hotwordResult.missingHotwords.isNotEmpty()) {
                println("      Missing hotwords: ${hotwordResult.missingHotwords}")
            }

            assertTrue(
                "Gate 4 Hotwords Recall violation: 100% recall required, but was ${hotwordResult.recall}. Missing: ${hotwordResult.missingHotwords}",
                hotwordResult.recall >= 1.0f
            )

            // -------------------------------------------------------------------------------------
            // Gate 5: LLM Relevance & TTFT Gate
            // -------------------------------------------------------------------------------------
            val args = InstrumentationRegistry.getArguments()
            val llmMode = args.getString("e2e.llm", "auto")?.lowercase() ?: "auto"
            var gate5Status = "SKIPPED"
            var ttftMs = 0L
            var matchedKeywords = emptyList<String>()
            var answerSnippet = ""

            if (llmMode == "off") {
                println("[E2E] Gate 5 SKIPPED: e2e.llm=off specified")
            } else {
                val hw = DeviceHardwareDetector.detectHardware(targetContext)
                if (llmMode == "auto") {
                    Assume.assumeTrue(
                        "Skipping Gate 5 in auto mode because device RAM tier is UNSUPPORTED (${hw.totalRamMb} MB)",
                        hw.ramTier != RamReadinessTier.UNSUPPORTED
                    )
                }

                // Wait for AnswerReady state with 90s timeout
                val readyState = withTimeout(90_000L) {
                    harness.state.filterIsInstance<AssistantState.AnswerReady>().first()
                }

                answerSnippet = readyState.finalAnswer.ifBlank { harness.copilotUiState.value.activeAnswer ?: "" }
                ttftMs = if (harness.copilotUiState.value.lastTtftMs > 0L) {
                    harness.copilotUiState.value.lastTtftMs
                } else {
                    readyState.totalGenerationMs
                }

                println("[E2E] Gate 5 TTFT: $ttftMs ms (SLA: $LLM_TTFT_SLA_MS ms)")
                assertTrue(
                    "Gate 5 TTFT SLA: TTFT $ttftMs ms must be <= $LLM_TTFT_SLA_MS ms",
                    ttftMs <= LLM_TTFT_SLA_MS
                )

                matchedKeywords = TranscriptEvaluator.evaluateKeywords(answerSnippet, turn.expectedLlmKeywords)
                val minRequiredKeywords = maxOf(2, (turn.expectedLlmKeywords.size * 0.75).toInt())

                println("[E2E] Gate 5 Keywords: Matched ${matchedKeywords.size}/$minRequiredKeywords minimum required ($matchedKeywords)")
                assertTrue(
                    "Gate 5 Keyword Relevance: Matched ${matchedKeywords.size} ($matchedKeywords), required >= $minRequiredKeywords out of ${turn.expectedLlmKeywords}",
                    matchedKeywords.size >= minRequiredKeywords
                )
                gate5Status = "PASS"
            }

            // -------------------------------------------------------------------------------------
            // Gate 6: Process Stability & Memory Gate
            // -------------------------------------------------------------------------------------
            val postTurnMemoryMb = getCurrentTotalMemoryMb()
            turnExecutionCount++
            var memoryDeltaMb = 0L

            if (turnExecutionCount == 1) {
                baselineMemoryMb = postTurnMemoryMb
                println("[E2E] Turn 1 Warmup baseline memory set: $baselineMemoryMb MB")
            } else {
                memoryDeltaMb = postTurnMemoryMb - baselineMemoryMb
                println("[E2E] Turn $turnExecutionCount memory delta: $memoryDeltaMb MB (Limit: $MEMORY_GROWTH_LIMIT_MB MB)")
                assertTrue(
                    "Gate 6 Memory Stability violation: Growth was $memoryDeltaMb MB, exceeding $MEMORY_GROWTH_LIMIT_MB MB threshold (baseline: $baselineMemoryMb MB, current: $postTurnMemoryMb MB)",
                    memoryDeltaMb < MEMORY_GROWTH_LIMIT_MB
                )
            }

            // -------------------------------------------------------------------------------------
            // Emit Per-Turn Structured JSON Result
            // -------------------------------------------------------------------------------------
            val resultJson = JSONObject().apply {
                put("turnId", turn.turnId)
                put("topic", turn.topic)
                put("timestamp", System.currentTimeMillis())
                put("status", "PASS")
                put("gate1_preflight", JSONObject().apply {
                    put("status", "PASS")
                    put("vadExists", appConfig.vadModelFile.exists())
                    put("sttEncoderExists", appConfig.sttEncoderFile.exists())
                    put("sttDecoderExists", appConfig.sttDecoderFile.exists())
                    put("sttJoinerExists", appConfig.sttJoinerFile.exists())
                    put("sttTokensExists", appConfig.sttTokensFile.exists())
                    put("llmExists", appConfig.llmModelFile.exists())
                    put("llmSizeBytes", appConfig.llmModelFile.length())
                    put("rescorer", rescorerStatus)
                })
                put("gate2_vad_segmentation", JSONObject().apply {
                    put("status", "PASS")
                    put("segmentCount", emittedSegments.size)
                    put("endpointLatencyMs", endpointLatencyMs)
                    put("slaMs", VAD_ENDPOINT_SLA_MS)
                })
                put("gate2b_question_detection", JSONObject().apply {
                    put("status", questionDetectStatus)
                    put("expected", turn.expectLikelyQuestion)
                    put("actual", segment.isLikelyQuestion)
                    put("warning", questionDetectWarning)
                })
                put("gate3_wer", JSONObject().apply {
                    put("status", "PASS")
                    put("wer", werResult.wer.toDouble())
                    put("maxAllowedWer", turn.maxAllowedWer.toDouble())
                    put("substitutions", werResult.substitutions)
                    put("deletions", werResult.deletions)
                    put("insertions", werResult.insertions)
                    put("referenceWords", werResult.referenceWords)
                    put("hypothesisWords", werResult.hypothesisWords)
                    put("reference", turn.groundTruthNormalized)
                    put("hypothesis", segment.normalizedText)
                })
                put("gate4_hotwords", JSONObject().apply {
                    put("status", "PASS")
                    put("recall", hotwordResult.recall.toDouble())
                    put("matchedCount", hotwordResult.matchedCount)
                    put("totalRequired", hotwordResult.totalRequired)
                    put("matched", JSONArray(hotwordResult.matchedHotwords))
                    put("missing", JSONArray(hotwordResult.missingHotwords))
                })
                put("gate5_llm", JSONObject().apply {
                    put("status", gate5Status)
                    put("ttftMs", ttftMs)
                    put("ttftSlaMs", LLM_TTFT_SLA_MS)
                    put("keywordHitCount", matchedKeywords.size)
                    put("matchedKeywords", JSONArray(matchedKeywords))
                    put("expectedKeywords", JSONArray(turn.expectedLlmKeywords))
                    put("answerSnippet", if (answerSnippet.length > 200) answerSnippet.take(200) + "..." else answerSnippet)
                })
                put("gate6_stability", JSONObject().apply {
                    put("status", "PASS")
                    put("baselineMemoryMb", baselineMemoryMb)
                    put("currentMemoryMb", postTurnMemoryMb)
                    put("memoryDeltaMb", memoryDeltaMb)
                    put("deltaLimitMb", MEMORY_GROWTH_LIMIT_MB)
                })
            }

            val externalDir = targetContext.getExternalFilesDir("e2e") ?: File(targetContext.filesDir, "e2e")
            val internalDir = File(targetContext.filesDir, "e2e")
            externalDir.mkdirs()
            internalDir.mkdirs()

            val jsonOutput = resultJson.toString(2)
            File(externalDir, "${turn.turnId}_result.json").writeText(jsonOutput)
            File(internalDir, "${turn.turnId}_result.json").writeText(jsonOutput)
            println("[E2E] Wrote turn result to ${externalDir.absolutePath}/${turn.turnId}_result.json")

        } finally {
            segmentCollectJob.cancel()
            harness.stop()
        }
    }
}
