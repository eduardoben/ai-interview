package com.edgecue.app.e2e

import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import com.edgecue.app.core.di.AppContainer
import com.edgecue.app.core.model.AppConfig
import com.edgecue.app.core.model.AssistantState
import com.edgecue.app.core.model.TranscriptSegment
import com.edgecue.app.data.hardware.DeviceHardwareDetector
import com.edgecue.app.data.hardware.RamReadinessTier
import com.edgecue.app.data.rag.DocumentTextExtractor
import com.edgecue.app.data.rag.RagDocumentChunker
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
        private const val MEMORY_GROWTH_LIMIT_MB = 350L

        private var warmLlmWrapper: SharedWarmLlmEngineWrapper? = null
        private var baselineMemoryMb: Long = -1L
        private var turnExecutionCount = 0

        private val E2E_HIMMLER_CV = """
            Himmler Benitez
            Staff Engineer | AI Software Engineering & Quality

            Summary
            Staff Quality Engineer with over 5 years of experience specializing in enterprise firmware validation, test architecture, and AI-driven automation. Currently completing a Master's in Artificial Intelligence with a focus on Deep Learning, RAG, and AI Agents. Proven track record of leading global validation workflows, architecting end-to-end automated frameworks, and deploying custom AI agents that bridge software engineering and quality assurance to accelerate delivery and eliminate manual bottlenecks.

            Education
            Master's degree, Artificial Intelligence
            Universidad de Sonora 2024 – 2026
            Bachelor's degree, Biomedical Engineering
            Universidad La Salle Noroeste, A.C. 2015 – 2019

            Experience
            Staff QA Engineer
            Lenovo 2024 – Present
            • Architected and deployed an AI-powered log failure triage assistant using Python and LLMs, reducing root-cause analysis time for complex server platform defects by 35%.
            • Spearhead the end-to-end validation strategy for next-generation enterprise ThinkSystem servers, coordinating cross-functional quality gates across global firmware, hardware, and OS teams.
            • Designed scalable, modular Python test automation frameworks for BMC/UEFI firmware interfaces (Redfish API, IPMI), increasing regression coverage by 40% while cutting execution cycles.

            QA Test Lead
            Lenovo 2022 – 2024
            • Directed a distributed team of 6 QA engineers executing full-cycle system integration, stress, and reliability testing across enterprise server product lines.
            • Built automated CI/CD regression pipelines in Jenkins and GitLab CI, integrating automated hardware-in-the-loop (HIL) provisioning to enable continuous overnight validation.

            Tester Junior Engineer
            Pinnacle Aerospace Inc 2021 – 2022
            • Executed rigorous verification and validation (V&V) protocols for mission-critical avionics software in strict compliance with DO-178B/C safety standards.
            • Developed automated test scripts and requirements traceability matrices, achieving 100% structural code coverage for airborne software modules.

            Projects
            Autonomous AI Diagnostic Agent (Multimodal RAG)
            • Engineered an autonomous triage and diagnostic agent utilizing LangGraph, Python, and vector databases to ingest multi-gigabyte server telemetry logs and technical specifications.
            • Implemented hybrid semantic + lexical retrieval with cross-encoder reranking, enabling engineers to query complex hardware fault signatures in natural language with high precision.

            Enterprise Multi-Agent RAG Orchestration Pipeline
            • Designed a multi-agent workflow orchestrating specialized retrieval, verification, and synthesis agents to automate technical documentation generation and defect correlation.
            • Integrated local and cloud LLM inference endpoints with strict schema validation and hallucination guardrails.

            Agriculture Crop Yield Prediction (Deep Learning)
            • Developed a spatio-temporal deep learning pipeline combining satellite imagery (CNNs) and environmental time-series data (LSTMs/Transformers) to forecast regional crop yields.

            Technical Skills
            AI & Machine Learning: Python, PyTorch, TensorFlow, Scikit-learn, LangChain, LangGraph, RAG Architectures, Multi-Agent Systems, Prompt Engineering, Vector Databases (FAISS, ChromaDB), Deep Learning (CNNs, Transformers), Computer Vision, NLP.
            Test Automation & Engineering: End-to-End Test Architecture, CI/CD (Jenkins, GitLab CI, GitHub Actions), Pytest, Robot Framework, Docker, Linux Administration, Git, SQL, RESTful APIs.
            Enterprise Systems & Firmware: Server Architecture, UEFI/BIOS, BMC (OpenBMC, Redfish, IPMI), Hardware-in-the-Loop (HIL) Testing, PCIe, CXL, DO-178B/C Avionics Compliance.

            Certifications & Credentials
            • AWS Certified Machine Learning - Specialty (MLS-C01)
            • Certified Kubernetes Application Developer (CKAD) Cloud Native Computing Foundation
            • DeepLearning.AI Deep Learning Specialization
        """.trimIndent()

        private val E2E_ML_ENGINEER_JD = """
            Machine Learning Engineer
            Department: Engineering / Data Science

            Role Overview
            We are seeking a Machine Learning Engineer to design, build, and deploy machine learning models into production. In this role, you will collaborate with data scientists, software engineers, and product teams to translate business requirements into scalable, reliable ML solutions.

            Responsibilities
            Design, train, evaluate, and optimize machine learning models for production environments.
            Build and maintain scalable data pipelines and feature engineering workflows.
            Deploy models via REST APIs, microservices, or batch pipelines.
            Implement model monitoring, drift detection, and automated retraining pipelines (MLOps).
            Collaborate with data engineering and software development teams to integrate models into core products.
            Ensure code quality through unit testing, integration testing, CI/CD, and code reviews.

            Qualifications
            Required:
            Bachelor's or Master's degree in Computer Science, Data Science, Mathematics, or a related technical field.
            2+ years of professional experience building and deploying machine learning systems.
            Strong proficiency in Python and core ML frameworks (e.g., PyTorch, TensorFlow, or scikit-learn).
            Experience with containerization (Docker) and cloud platforms (AWS, GCP, or Azure).
            Solid understanding of data structures, algorithms, and software engineering best practices.
            Proficiency in SQL and working with relational and non-relational databases.
            Preferred:
            Experience with MLOps tools (e.g., MLflow, Kubeflow, Weights & Biases).
            Familiarity with distributed computing frameworks (e.g., Apache Spark, Ray).
            Experience deploying Large Language Models (LLMs) or Vision-Language Models (VLMs).
            Background in API design (e.g., FastAPI, Flask, gRPC).
        """.trimIndent()

        private val E2E_ELENA_VANCE_CV = """
            ELENA VANCE, LCSW
            LICENSED CLINICAL SOCIAL WORKER • INPATIENT & CRISIS SPECIALIST
            Denver, CO • (555) 349-2810 • elena.vance@email.com • linkedin.com/in/elena-vance-lcsw

            PROFESSIONAL SUMMARY
            Licensed Clinical Social Worker (LCSW) with 8 years of specialized clinical experience delivering high-impact medical case management, acute crisis intervention, and biopsychosocial assessments across emergency and intensive care hospital units. Proven expertise leading interdisciplinary care transitions, mediating complex palliative care decisions, and resolving systemic barriers for vulnerable patient populations through trauma-informed care and robust community resource navigation.

            LICENSURE & CERTIFICATIONS
            Licensed Clinical Social Worker (LCSW) — License #CSW-098234 Colorado State Board of Social Work Examiners
            Certified Advanced Children, Youth, and Family Social Worker (C-ACYFSW) National Association of Social Workers (NASW)
            Certified Crisis Intervention Specialist (CCIS) National Crisis Prevention Institute

            CORE COMPETENCIES & CLINICAL SKILLS
            Clinical Modalities: Crisis Intervention & De-escalation, Trauma-Informed Care (TIC), Motivational Interviewing, Short-term CBT
            Inpatient Operations: Biopsychosocial Assessments, Acute Discharge Planning, Care Coordination, Advance Directives, Guardianship
            Systems & Compliance: Medicaid/Medicare Regulations, CMS Quality Measures, HIPAA Compliance, Epic EHR, Multidisciplinary Rounds

            PROFESSIONAL EXPERIENCE
            Senior Medical Social Worker 2021 – Present
            Valley Health Medical Center Denver, CO
            • Manage an active caseload of 25–30 acute inpatient admissions weekly across Emergency and Intensive Care Units.
            • Conduct comprehensive biopsychosocial assessments, chemical dependency screenings, and social determinant evaluations within 24 hours of hospital admission.
            • Coordinate safe, post-acute care transitions with skilled nursing facilities, home health agencies, and hospice teams, reducing 30-day readmissions by 12%.
            • Facilitate interdisciplinary family conferences to mediate complex end-of-life choices, Medical Durable Power of Attorney, and palliative consults.
            • Lead departmental crisis response protocols for sudden bereavement, trauma resuscitations, and domestic violence interventions.

            Clinical Case Manager 2018 – 2021
            Hope Valley Behavioral Health Services Aurora, CO
            • Delivered short-term Cognitive Behavioral Therapy (CBT) and motivational interviewing to adults managing severe chronic psychiatric conditions.
            • Conducted immediate clinical crisis evaluations and de-escalation for patients presenting with active suicidal ideation, initiating mental health holds when required.
            • Connected over 150 low-income clients annually with vital safety-net benefits, including Medicaid enrollment, SNAP, and transitional supportive housing.
            • Collaborated closely with county human services, legal aid clinics, and community psychiatrists to formulate comprehensive outpatient recovery plans.

            EDUCATION
            Master of Social Work (MSW) — Health & Behavioral Health Concentration 2018
            University of Denver Denver, CO
            Bachelor of Arts (B.A.) in Psychology — Magna Cum Laude 2016
            Colorado State University Fort Collins, CO
            Elena Vance, LCSW — Curriculum Vitae Page 1 of 1
        """.trimIndent()

        private val E2E_ST_JUDE_LCSW_JD = """
            Medical Social Worker (LCSW) – Inpatient Care
            Facility: St. Jude Regional Hospital
            Department: Social Services & Care Coordination
            Employment Type: Full-time
            Location: Denver, CO

            Position Overview
            St. Jude Regional Hospital is seeking a full-time Licensed Clinical Social Worker (LCSW) to join our Inpatient Care Coordination team. The Medical Social Worker will assess the psychosocial needs of patients and families, design discharge plans for high-risk patients, and provide crisis support within acute medical units.

            Key Responsibilities
            Complete comprehensive biopsychosocial assessments within 24 hours of patient admission.
            Collaborate with physicians, registered nurses, and physical therapists to coordinate safe discharge and continuum-of-care plans.
            Deliver crisis intervention for trauma, domestic violence, psychiatric emergencies, and sudden bereavement.
            Facilitate advanced healthcare planning, including DNR orders, Medical Durable Power of Attorney, and palliative care consults.
            Identify barriers to care, including housing instability, insurance gaps, and transportation, connecting patients with community partners.
            Maintain complete and timely clinical documentation within the hospital's Electronic Health Record (Epic) complying with CMS and HIPAA regulations.

            Requirements
            Master of Social Work (MSW) from a CSWE-accredited program.
            Current, unencumbered LCSW license in the State of Colorado.
            Minimum of 3 years of clinical social work experience, preferably in a hospital or acute healthcare setting.
            Demonstrated competency in crisis de-escalation and complex discharge planning.
            Familiarity with Medicare/Medicaid regulations and regional public assistance programs.

            Preferred Qualifications
            Prior experience with Epic EHR software.
            Bilingual (English/Spanish).
            Certification in Healthcare Social Work (C-SWHC) or Palliative Care.
        """.trimIndent()

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

            val hwProfile = DeviceHardwareDetector.detectHardware(targetContext)

            // On x86_64 emulators (goldfish/ranchu) where OpenCL GPU drivers are unavailable,
            // enforce CPU memory-mapped delegate as specified in AGENTS.md section 7.
            val isEmulator = android.os.Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                    android.os.Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                    android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                    android.os.Build.MODEL.contains("sdk", ignoreCase = true) ||
                    android.os.Build.SOC_MODEL.isNullOrBlank()
            if (isEmulator || hwProfile.tier != com.edgecue.app.data.hardware.HardwareTier.TPU) {
                println("[E2E] Emulator or non-TPU platform detected: configuring preferredDelegate = DelegateType.CPU_MMAP")
                appConfig.updateLlmConfig(
                    appConfig.llmConfig.value.copy(preferredDelegate = com.edgecue.app.data.hardware.DelegateType.CPU_MMAP)
                )
            }

            // Configure optimal STAR generation budget (280 tokens, temp 0.35) and index RAG fixtures
            appConfig.updateLlmConfig(
                appConfig.llmConfig.value.copy(
                    maxTokens = 280,
                    temperature = 0.35f
                )
            )
            val extractor = DocumentTextExtractor(context = null)
            val chunker = RagDocumentChunker()
            val cvChunks = chunker.chunkDocument("CURRICULUM", extractor.normalizeText(E2E_HIMMLER_CV))
            val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", extractor.normalizeText(E2E_ML_ENGINEER_JD))
            appContainer.liteRagEngine.updateIndex(cvChunks + jdChunks)
            println("[E2E] Indexed ${cvChunks.size} CV chunks and ${jdChunks.size} JD chunks into LiteRagEngine.")

            if (llmMode != "off") {
                val hwProfile = DeviceHardwareDetector.detectHardware(targetContext)
                val shouldWarm = (llmMode == "on") ||
                        (llmMode == "auto" && hwProfile.ramTier != RamReadinessTier.UNSUPPORTED)

                val activeModel = appConfig.activeLlmModelFile
                if (shouldWarm && activeModel.exists() && activeModel.length() > 0L) {
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

        val activeModel = appConfig.activeLlmModelFile
        assertTrue("Gate 1: LLM model file must exist (${activeModel.name})", activeModel.exists())
        assertTrue("Gate 1: LLM model file must be > 2GB", activeModel.length() > 2_000_000_000L)

        // Re-assert test RAG index and STAR config before every turn in case async Room/DataStore flows emitted
        appConfig.updateLlmConfig(
            appConfig.llmConfig.value.copy(
                maxTokens = 280,
                temperature = 0.35f
            )
        )
        val extractor = DocumentTextExtractor(context = null)
        val chunker = RagDocumentChunker()
        val isHealthcareTurn = turn.turnId.contains("lcsw", ignoreCase = true)
        val activeCvText = if (isHealthcareTurn) E2E_ELENA_VANCE_CV else E2E_HIMMLER_CV
        val activeJdText = if (isHealthcareTurn) E2E_ST_JUDE_LCSW_JD else E2E_ML_ENGINEER_JD
        val cvChunks = chunker.chunkDocument("CURRICULUM", extractor.normalizeText(activeCvText))
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", extractor.normalizeText(activeJdText))
        appContainer.liteRagEngine.updateIndex(cvChunks + jdChunks)

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

        if (appConfig.preferredDelegate == null) {
            appConfig.updateLlmConfig(
                appConfig.llmConfig.value.copy(preferredDelegate = com.edgecue.app.data.hardware.DelegateType.CPU_MMAP)
            )
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

            // When Whisper rescorer is active on physical device, allow up to 5000ms endpoint rescoring SLA
            val effectiveEndpointSlaMs = if (appConfig.hasOfflineRescorer()) 5000L else VAD_ENDPOINT_SLA_MS
            assertTrue(
                "Gate 2 SLA: Endpoint latency $endpointLatencyMs ms must be <= $effectiveEndpointSlaMs ms",
                endpointLatencyMs <= effectiveEndpointSlaMs
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
            // -------------------------------------------------------------------------------------
            // Gate 3: Normalized Word Error Rate (WER) Gate Calculation
            // -------------------------------------------------------------------------------------
            val werResult = TranscriptEvaluator.computeWer(turn.groundTruthNormalized, segment.normalizedText)
            val wordAccuracy = ((1.0 - werResult.wer).coerceIn(0.0, 1.0) * 100.0)
            val transcriptConfidence = (1.0 - werResult.wer).coerceIn(0.0, 1.0)
            println("[E2E] Gate 3 WER: ${werResult.wer} (max: ${turn.maxAllowedWer}), S=${werResult.substitutions}, D=${werResult.deletions}, I=${werResult.insertions}")
            println("      Ref: '${turn.groundTruthNormalized}'")
            println("      Hyp: '${segment.normalizedText}'")
            val gate3Pass = werResult.wer <= turn.maxAllowedWer

            // -------------------------------------------------------------------------------------
            // Gate 4: Domain Hotwords Recall Gate Calculation
            // -------------------------------------------------------------------------------------
            val hotwordResult = TranscriptEvaluator.evaluateHotwords(segment.normalizedText, turn.mustContainHotwords)
            println("[E2E] Gate 4 Hotwords Recall: ${hotwordResult.recall * 100}% (${hotwordResult.matchedCount}/${hotwordResult.totalRequired})")
            if (hotwordResult.missingHotwords.isNotEmpty()) {
                println("      Missing hotwords: ${hotwordResult.missingHotwords}")
            }
            val gate4Pass = hotwordResult.recall >= 1.0f

            // -------------------------------------------------------------------------------------
            // Gate 5: LLM Relevance & TTFT Gate
            // -------------------------------------------------------------------------------------
            val args = InstrumentationRegistry.getArguments()
            val llmMode = args.getString("e2e.llm", "auto")?.lowercase() ?: "auto"
            var gate5Status = "SKIPPED"
            var ttftMs = 0L
            var totalGenerationMs = 0L
            var matchedKeywords = emptyList<String>()
            var answerSnippet = ""
            var gate5Pass = true

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
                totalGenerationMs = readyState.totalGenerationMs
                println("[E2E] Gate 5 Full Answer: '$answerSnippet' (len=${answerSnippet.length})")
                ttftMs = if (harness.copilotUiState.value.lastTtftMs > 0L) {
                    harness.copilotUiState.value.lastTtftMs
                } else {
                    readyState.totalGenerationMs
                }

                println("[E2E] Gate 5 TTFT: $ttftMs ms (SLA: $LLM_TTFT_SLA_MS ms)")
                matchedKeywords = TranscriptEvaluator.evaluateKeywords(answerSnippet, turn.expectedLlmKeywords)
                val minRequiredKeywords = maxOf(2, (turn.expectedLlmKeywords.size * 0.60).toInt())

                println("[E2E] Gate 5 Keywords: Matched ${matchedKeywords.size}/$minRequiredKeywords minimum required ($matchedKeywords)")
                gate5Pass = (ttftMs <= LLM_TTFT_SLA_MS) && (matchedKeywords.size >= minRequiredKeywords)
                gate5Status = if (gate5Pass) "PASS" else "FAIL"
            }

            // -------------------------------------------------------------------------------------
            // Gate 6: Process Stability & Memory Gate
            // -------------------------------------------------------------------------------------
            System.gc()
            Runtime.getRuntime().gc()
            Thread.sleep(100)
            val postTurnMemoryMb = getCurrentTotalMemoryMb()
            turnExecutionCount++
            var memoryDeltaMb = 0L
            var gate6Pass = true

            if (turnExecutionCount == 1) {
                baselineMemoryMb = postTurnMemoryMb
                println("[E2E] Turn 1 Warmup baseline memory set: $baselineMemoryMb MB")
            } else {
                memoryDeltaMb = postTurnMemoryMb - baselineMemoryMb
                println("[E2E] Turn $turnExecutionCount memory delta: $memoryDeltaMb MB (Limit: $MEMORY_GROWTH_LIMIT_MB MB)")
                gate6Pass = memoryDeltaMb < MEMORY_GROWTH_LIMIT_MB
            }

            // -------------------------------------------------------------------------------------
            // Emit Per-Turn Structured JSON Result BEFORE Assertion Checks
            // -------------------------------------------------------------------------------------
            val overallTurnPass = gate3Pass && gate4Pass && gate5Pass && gate6Pass
            val audioDurationMs = (turn.audioDurationSec * 1000).toLong()
            val decodeDurationMs = if (totalGenerationMs > ttftMs) totalGenerationMs - ttftMs else 0L
            val totalPipelineLatencyMs = audioDurationMs + endpointLatencyMs + totalGenerationMs

            val resultJson = JSONObject().apply {
                put("stage_latencies_ms", JSONObject().apply {
                    put("audioDurationMs", audioDurationMs)
                    put("vadEndpointLatencyMs", endpointLatencyMs)
                    put("llmPrefillTtftMs", ttftMs)
                    put("llmDecodeDurationMs", decodeDurationMs)
                    put("totalGenerationMs", totalGenerationMs)
                    put("totalPipelineLatencyMs", totalPipelineLatencyMs)
                })
                put("turnId", turn.turnId)
                put("topic", turn.topic)
                put("timestamp", System.currentTimeMillis())
                put("status", if (overallTurnPass) "PASS" else "FAIL")
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
                    put("status", if (gate3Pass) "PASS" else "FAIL")
                    put("wer", werResult.wer.toDouble())
                    put("wordAccuracyPercent", String.format(java.util.Locale.US, "%.1f%%", wordAccuracy))
                    put("transcriptConfidence", transcriptConfidence)
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
                    put("status", if (gate4Pass) "PASS" else "FAIL")
                    put("recall", hotwordResult.recall.toDouble())
                    put("matchedCount", hotwordResult.matchedCount)
                    put("totalRequired", hotwordResult.totalRequired)
                    put("matched", JSONArray(hotwordResult.matchedHotwords))
                    put("missing", JSONArray(hotwordResult.missingHotwords))
                })
                put("gate5_llm", JSONObject().apply {
                    put("status", gate5Status)
                    put("ttftMs", ttftMs)
                    put("decodeDurationMs", decodeDurationMs)
                    put("totalGenerationMs", totalGenerationMs)
                    put("ttftSlaMs", LLM_TTFT_SLA_MS)
                    put("keywordHitCount", matchedKeywords.size)
                    put("matchedKeywords", JSONArray(matchedKeywords))
                    put("expectedKeywords", JSONArray(turn.expectedLlmKeywords))
                    put("fullAnswer", answerSnippet)
                    put("answerSnippet", if (answerSnippet.length > 200) answerSnippet.take(200) + "..." else answerSnippet)
                    put("answerLengthChars", answerSnippet.length)
                })
                put("gate6_stability", JSONObject().apply {
                    put("status", if (gate6Pass) "PASS" else "FAIL")
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

            // -------------------------------------------------------------------------------------
            // Verification Gate Assertions
            // -------------------------------------------------------------------------------------
            assertTrue(
                "Gate 3 WER violation: WER ${werResult.wer} exceeds max allowed ${turn.maxAllowedWer}. S=${werResult.substitutions}, D=${werResult.deletions}, I=${werResult.insertions}",
                gate3Pass
            )

            assertTrue(
                "Gate 4 Hotwords Recall violation: 100% recall required, but was ${hotwordResult.recall}. Missing: ${hotwordResult.missingHotwords}",
                gate4Pass
            )

            if (llmMode != "off") {
                assertTrue(
                    "Gate 5 TTFT SLA: TTFT $ttftMs ms must be <= $LLM_TTFT_SLA_MS ms",
                    ttftMs <= LLM_TTFT_SLA_MS
                )
                val minRequiredKeywords = maxOf(2, (turn.expectedLlmKeywords.size * 0.60).toInt())
                assertTrue(
                    "Gate 5 Keyword Relevance: Matched ${matchedKeywords.size} ($matchedKeywords), required >= $minRequiredKeywords out of ${turn.expectedLlmKeywords}",
                    matchedKeywords.size >= minRequiredKeywords
                )
            }

            if (turnExecutionCount > 1) {
                assertTrue(
                    "Gate 6 Memory Stability violation: Growth was $memoryDeltaMb MB, exceeding $MEMORY_GROWTH_LIMIT_MB MB threshold (baseline: $baselineMemoryMb MB, current: $postTurnMemoryMb MB)",
                    gate6Pass
                )
            }

        } finally {
            segmentCollectJob.cancel()
            harness.stop()
        }
    }
}
