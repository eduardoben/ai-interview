package com.edgecue.app.data.rag

import com.edgecue.app.core.model.AppConfig
import com.edgecue.app.core.model.LlmGenerationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Formal evaluation and benchmark suite for the on-device Lite RAG subsystem.
 * Evaluates:
 * 1. Retrieval Accuracy & Recall (>= 90% across technical, role alignment, behavioral, and broad questions)
 * 2. Strict Context Budget Gate (<= 700 chars total)
 * 3. Latency Performance Gate (< 2.0 ms, median < 0.5 ms)
 * 4. Backward Compatibility & Seamless Fallback when no RAG documents are configured
 */
class LiteRagEvaluationTest {

    private val sampleCv = """
        PROFESSIONAL SUMMARY
        Staff Android & Systems Engineer with 9+ years developing high-performance mobile and edge AI applications.
        Specializes in on-device LLM inference, NPU hardware acceleration, low-latency DSP audio pipelines, and Kotlin multiplatform architecture.

        TECHNICAL SKILLS
        Languages: Kotlin, C++, Java, Python
        Mobile & AI: Jetpack Compose, Coroutines, Flow, Room, LiteRT-LM, Sherpa-ONNX, ONNX Runtime
        Hardware Acceleration: Google Tensor EdgeTPU, Qualcomm Adreno GPU, MediaTek APU

        EXPERIENCE
        EdgeCue AI - Staff Systems Architect (2022 - Present)
        - Designed JNI thread serialization dispatcher LiteRT-GPU-Thread resolving native concurrency crashes on Tensor SoCs.
        - Integrated Gemma 4 E2B models achieving sub-250ms first-token latency on Google Tensor G5 EdgeTPU.
        - Built zero-allocation CircularFloatRingBuffer for 16 kHz mono audio streaming eliminating garbage collection spikes.

        NextGen Mobile - Senior Android Engineer (2019 - 2022)
        - Led architecture migration from monolithic View system to 100% Jetpack Compose and reactive MVI StateFlow.
        - Implemented local Room database caching with indexed queries and background DataStore synchronization.
        - Mentored team of 8 mobile engineers, establishing unit testing standards and CI/CD pipelines.

        EDUCATION
        B.S. in Computer Science - University of California, Berkeley
    """.trimIndent()

    private val sampleJd = """
        ROLE OVERVIEW
        We are seeking a Staff Mobile AI Engineer to drive our local-first AI copilot on modern Android flagships.
        You will architect native inference runtimes, real-time speech recognition, and fluid teleprompter user interfaces.

        KEY RESPONSIBILITIES
        - Build zero-cloud-dependency on-device RAG systems retrieving candidate CV and job requirements in under 2 milliseconds.
        - Optimize native C++ JNI bindings and LiteRT delegate execution on Google Tensor TPU and mobile GPU hardware.
        - Collaborate with UX and product teams to deliver accessible, OLED-optimized Jetpack Compose user experiences.

        REQUIREMENTS & QUALIFICATIONS
        - 7+ years of Android development experience with mastery in Kotlin, Coroutines, Flow, and NPU acceleration.
        - Hands-on experience integrating on-device language models (LiteRT-LM / GGUF) and speech recognition (Sherpa-ONNX / Whisper).
        - Proven ability to write zero-allocation real-time audio code and prevent main-thread UI jank.
    """.trimIndent()

    private lateinit var chunker: RagDocumentChunker
    private lateinit var engine: LiteRagEngine

    @Before
    fun setUp() {
        chunker = RagDocumentChunker()
        engine = LiteRagEngineImpl(chunker)

        val cvChunks = chunker.chunkDocument("CURRICULUM", sampleCv)
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", sampleJd)
        engine.updateIndex(cvChunks + jdChunks)
    }

    // =========================================================================
    // GATE 1: Retrieval Accuracy & Recall (>= 90% across 10 benchmark queries)
    // =========================================================================

    private data class BenchmarkCase(
        val question: String,
        val expectedKeywords: List<String>,
        val category: String
    )

    @Test
    fun testRetrievalRecallOver90PercentAcrossBenchmarkSuite() {
        val testCases = listOf(
            // Technical Deep-Dive
            BenchmarkCase(
                question = "How do you handle JNI thread serialization and native C++ concurrency?",
                expectedKeywords = listOf("JNI", "serialization", "dispatcher"),
                category = "Technical"
            ),
            BenchmarkCase(
                question = "What is your experience with Google Tensor EdgeTPU and Gemma 4 E2B models?",
                expectedKeywords = listOf("Tensor", "EdgeTPU", "Gemma"),
                category = "Technical"
            ),
            BenchmarkCase(
                question = "How do you prevent audio buffer GC pauses during 16 kHz recording?",
                expectedKeywords = listOf("CircularFloatRingBuffer", "16 kHz", "audio"),
                category = "Technical"
            ),
            BenchmarkCase(
                question = "What frameworks do you use for on-device speech recognition?",
                expectedKeywords = listOf("Sherpa-ONNX", "speech"),
                category = "Technical"
            ),

            // Role Alignment
            BenchmarkCase(
                question = "How does your experience match our requirements for building on-device RAG?",
                expectedKeywords = listOf("RAG", "under 2"),
                category = "Role Alignment"
            ),
            BenchmarkCase(
                question = "Why are you a strong fit for this Staff Mobile AI Engineer position?",
                expectedKeywords = listOf("Staff", "Mobile", "Engineer"),
                category = "Role Alignment"
            ),

            // Behavioral / Leadership
            BenchmarkCase(
                question = "Tell me about a time you led an architecture migration or mentored engineers.",
                expectedKeywords = listOf("migration", "Compose", "mentored"),
                category = "Behavioral"
            ),
            BenchmarkCase(
                question = "Describe how you optimized database performance and background synchronization.",
                expectedKeywords = listOf("Room", "database", "DataStore"),
                category = "Behavioral"
            ),

            // Broad & Conversational
            BenchmarkCase(
                question = "Tell me about yourself and your engineering background.",
                expectedKeywords = listOf("SUMMARY", "Staff", "Engineer"),
                category = "Broad"
            ),
            BenchmarkCase(
                question = "Walk me through your resume and key achievements.",
                expectedKeywords = listOf("Staff", "Systems", "Architect"),
                category = "Broad"
            )
        )

        var passedHits = 0
        val failureDetails = mutableListOf<String>()

        for (test in testCases) {
            val result = engine.retrieveFormattedContext(test.question, maxChars = 700)
            val matched = test.expectedKeywords.any { kw ->
                result.formattedContext.contains(kw, ignoreCase = true)
            }

            if (matched) {
                passedHits++
            } else {
                failureDetails.add("[${test.category}] '${test.question}' -> Did not find any of ${test.expectedKeywords} in:\n${result.formattedContext}")
            }
        }

        val recall = passedHits.toDouble() / testCases.size
        println("=== BENCHMARK RETRIEVAL RECALL ===")
        println("Passed: $passedHits / ${testCases.size} (${(recall * 100).toInt()}%)")
        if (failureDetails.isNotEmpty()) {
            println("Misses:\n" + failureDetails.joinToString("\n"))
        }

        assertTrue("Retrieval recall must be >= 90%, was ${(recall * 100).toInt()}%", recall >= 0.90)
    }

    // =========================================================================
    // GATE 2: Strict Context Budget Gate (<= 700 chars)
    // =========================================================================

    @Test
    fun testContextBudgetHardCapEnforcedUnderExtremeQueries() {
        val longQueries = listOf(
            "Can you give an extremely detailed description of every single project you worked on with full technical details, coroutines, memory layout, and architecture?",
            "Explain in exhaustive depth how the entire Sherpa-ONNX streaming Zipformer transducer works in tandem with Silero VAD gating and int8 Whisper rescoring on Android 17?",
            "a ".repeat(500)
        )

        for (q in longQueries) {
            val res = engine.retrieveFormattedContext(q, maxChars = 700)
            assertTrue(
                "Formatted context length ${res.formattedContext.length} must be <= 700 chars",
                res.formattedContext.length <= 700
            )
        }
    }

    // =========================================================================
    // GATE 3: Latency Performance Gate (< 2.0 ms, median < 0.5 ms)
    // =========================================================================

    @Test
    fun testRetrievalLatencyPerformanceGate() {
        val testQueries = listOf(
            "How do you serialize JNI calls on LiteRT?",
            "What is your experience with Sherpa-ONNX?",
            "Tell me about yourself.",
            "Why this role at our company?",
            "Describe an architecture migration."
        )

        // Warm up JIT
        for (i in 0 until 50) {
            engine.retrieveFormattedContext(testQueries[i % testQueries.size], maxChars = 700)
        }

        val iterations = 200
        val latenciesMicros = LongArray(iterations)

        for (i in 0 until iterations) {
            val q = testQueries[i % testQueries.size]
            val t0 = System.nanoTime()
            val result = engine.retrieveFormattedContext(q, maxChars = 700)
            val elapsedMicros = (System.nanoTime() - t0) / 1000
            latenciesMicros[i] = elapsedMicros
            assertNotNull(result)
        }

        latenciesMicros.sort()
        val medianMicros = latenciesMicros[iterations / 2]
        val p99Micros = latenciesMicros[(iterations * 0.99).toInt()]

        println("=== LATENCY BENCHMARK ===")
        println("Median: ${medianMicros}µs (${medianMicros / 1000.0}ms)")
        println("P99: ${p99Micros}µs (${p99Micros / 1000.0}ms)")

        assertTrue(
            "Median latency must be < 500µs (0.5ms), was ${medianMicros}µs",
            medianMicros < 500
        )
        assertTrue(
            "P99 latency must be < 2000µs (2.0ms), was ${p99Micros}µs",
            p99Micros < 2000
        )
    }

    // =========================================================================
    // GATE 4: Backward Compatibility & Zero-Overhead Empty Fallback
    // =========================================================================

    @Test
    fun testEmptyRagFallbackProducesLegacyPromptWithZeroOverhead() {
        val emptyEngine = LiteRagEngineImpl()
        val res = emptyEngine.retrieveFormattedContext("Tell me about yourself", maxChars = 700)

        assertEquals("", res.formattedContext)
        assertEquals(0, res.cvChunkCount)
        assertEquals(0, res.jdChunkCount)
        assertFalse(res.usedFallbackOverview)

        val appConfig = AppConfig(File("dummy/path"))
        val legacyPrompt = appConfig.formatGemmaPrompt("Tell me about yourself")
        val ragPrompt = appConfig.formatGemmaPrompt("Tell me about yourself", ragContext = res.formattedContext)

        assertEquals("Prompt with empty RAG context must match legacy prompt identically", legacyPrompt, ragPrompt)
        assertFalse("Prompt must not contain empty Tailored Reference tag", ragPrompt.contains("Tailored Reference Context:"))
    }

    // =========================================================================
    // GATE 5: Exact User Reproduction (Himmler Benitez CV + ML Engineer JD)
    // =========================================================================

    @Test
    fun testHimmlerBenitezResumeAndMlEngineerJdWithAudioQuestion() {
        val himmlerResumeText = """
            Himmler Benitez
            Staff Engineer | AI Software Engineering & Quality
            himmlerbenitez@gmail.com linkedin.com/in/himmlerbenitez github.com/HimmlerB

            Summary
            Staff Quality Engineer with over 5 years of experience specializing in enterprise firmware validation, test architecture, and AI-driven automation. Currently completing a Master's in Artificial Intelligence with a focus on Deep Learning, RAG, and AI Agents. Proven track record of leading global validation workflows, architecting end-to-end automated frameworks, and deploying custom AI agents that bridge software engineering and quality assurance to accelerate delivery and eliminate manual bottlenecks.

            Education
            Master's degree, Artificial Intelligence
            Universidad de Sonora                                                                 2024 – 2026
            Bachelor's degree, Biomedical Engineering
            Universidad La Salle Noroeste, A.C.                                                   2015 – 2019

            Experience
            Staff QA Engineer
            Lenovo                                                                                2024 – Present
            • Architected and deployed an AI-powered log failure triage assistant using Python and LLMs, reducing root-cause analysis time for complex server platform defects by 35%.
            • Spearhead the end-to-end validation strategy for next-generation enterprise ThinkSystem servers, coordinating cross-functional quality gates across global firmware, hardware, and OS teams.
            • Designed scalable, modular Python test automation frameworks for BMC/UEFI firmware interfaces (Redfish API, IPMI), increasing regression coverage by 40% while cutting execution cycles.

            QA Test Lead
            Lenovo                                                                                2022 – 2024
            • Directed a distributed team of 6 QA engineers executing full-cycle system integration, stress, and reliability testing across enterprise server product lines.
            • Built automated CI/CD regression pipelines in Jenkins and GitLab CI, integrating automated hardware-in-the-loop (HIL) provisioning to enable continuous overnight validation.

            Tester Junior Engineer
            Pinnacle Aerospace Inc                                                                2021 – 2022
            • Executed rigorous verification and validation (V&V) protocols for mission-critical avionics software in strict compliance with DO-178B/C safety standards.
            • Developed automated test scripts and requirements traceability matrices, achieving 100% structural code coverage for airborne software modules.
            Himmler Benitez himmlerbenitez@gmail.com                                              1/2

            Projects
            Autonomous AI Diagnostic Agent (Multimodal RAG)
            • Engineered an autonomous triage and diagnostic agent utilizing LangGraph, Python, and vector databases to ingest multi-gigabyte server telemetry logs and technical specifications.
            • Implemented hybrid semantic + lexical retrieval with cross-encoder reranking, enabling engineers to query complex hardware fault signatures in natural language with high precision.

            Enterprise Multi-Agent RAG Orchestration Pipeline
            • Designed a multi-agent workflow orchestrating specialized retrieval, verification, and synthesis agents to automate technical documentation generation and defect correlation.
            • Integrated local and cloud LLM inference endpoints with strict schema validation and hallucination guardrails.

            Agriculture Crop Yield Prediction (Deep Learning)
            • Developed a spatio-temporal deep learning pipeline combining satellite imagery (CNNs) and environmental time-series data (LSTMs/Transformers) to forecast regional crop yields.

            Certifications
            ISTQB® Certified Tester, Foundation Level (CTFL) — 2022
            Scrum Fundamentals Certified (SFC) — 2021

            Technical Skills
            AI & Machine Learning: Python, PyTorch, TensorFlow, Scikit-learn, LangChain, LangGraph, RAG Architectures, Multi-Agent Systems, Prompt Engineering, Vector Databases (FAISS, ChromaDB), Deep Learning (CNNs, Transformers), Computer Vision, NLP.
            Test Automation & Engineering: End-to-End Test Architecture, CI/CD (Jenkins, GitLab CI, GitHub Actions), Pytest, Robot Framework, Docker, Linux Administration, Git, SQL, RESTful APIs.
            Enterprise Systems & Firmware: Server Architecture, UEFI/BIOS, BMC (OpenBMC, Redfish, IPMI), Hardware-in-the-Loop (HIL) Testing, PCIe, CXL, DO-178B/C Avionics Compliance.
            Himmler Benitez himmlerbenitez@gmail.com                                              2/2
        """.trimIndent()

        val mlEngineerJdText = """
            Machine Learning Engineer
            Location: [Remote / Hybrid / On-site]
            Employment Type: Full-time
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
            Bachelor's or Master's degree in Computer Science, Data Science, Mathematics, or a related technical field (or equivalent practical experience).
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

        val extractor = DocumentTextExtractor(context = null)
        val normalizedCv = extractor.normalizeText(himmlerResumeText)
        val normalizedJd = extractor.normalizeText(mlEngineerJdText)

        val cvChunks = chunker.chunkDocument("CURRICULUM", normalizedCv)
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", normalizedJd)

        val customEngine = LiteRagEngineImpl(chunker)
        customEngine.updateIndex(cvChunks + jdChunks)

        // Exact question transcribed from user's uploaded audio file:
        val audioQuestion = "Good. Could you walk me through your background, covering your professional journey as well as your academic progression?"

        val ragResult = customEngine.retrieveFormattedContext(audioQuestion, maxChars = 1100)
        println("=== HIMMLER BENITEZ RAG RETRIEVAL FOR AUDIO QUESTION ===")
        println("CV Chunks Selected: ${ragResult.cvChunkCount}, JD Chunks Selected: ${ragResult.jdChunkCount}")
        println(ragResult.formattedContext)

        assertTrue("Must select multiple CV chunks (Summary, Experience, Education)", ragResult.cvChunkCount >= 2)
        assertTrue("Formatted context must fit within 1100 char budget", ragResult.formattedContext.length <= 1100)

        // Verify Summary + Candidate Identity is present
        assertTrue(
            "Must include candidate summary/identity (Himmler Benitez / Staff Quality Engineer)",
            ragResult.formattedContext.contains("Himmler Benitez", ignoreCase = true) ||
                ragResult.formattedContext.contains("Staff Quality Engineer", ignoreCase = true)
        )

        // Verify Academic Progression (Education section) is retrieved!
        assertTrue(
            "Must include academic progression (Master's in Artificial Intelligence / Universidad de Sonora / Biomedical Engineering)",
            ragResult.formattedContext.contains("Artificial Intelligence", ignoreCase = true) &&
                (ragResult.formattedContext.contains("Universidad de Sonora", ignoreCase = true) ||
                    ragResult.formattedContext.contains("Biomedical Engineering", ignoreCase = true))
        )

        // Verify Professional Journey (Experience section) is retrieved!
        assertTrue(
            "Must include professional journey (Lenovo / Staff QA Engineer)",
            ragResult.formattedContext.contains("Lenovo", ignoreCase = true) &&
                ragResult.formattedContext.contains("Staff QA Engineer", ignoreCase = true)
        )

        // Verify Page Footers were stripped and did not pollute chunks
        assertFalse(
            "Page footers (1/2, 2/2 with email) should be stripped",
            ragResult.formattedContext.contains("himmlerbenitez@gmail.com")
        )

        // Verify AppConfig prompt includes first-person candidate grounding directive
        val appConfig = AppConfig(File("dummy/path"))
        val finalPrompt = appConfig.formatGemmaPrompt(audioQuestion, ragContext = ragResult.formattedContext)
        assertTrue(
            "Final Gemma prompt must include first-person grounding directive",
            finalPrompt.contains(AppConfig.CANDIDATE_RAG_GROUNDING_DIRECTIVE)
        )
    }

    // =========================================================================
    // GATE 6: Specialized Technical Knowledge & Tradeoffs (ML Deployment & Serving)
    // =========================================================================

    @Test
    fun testSpecializedTechnicalQuestionRetrievesProjectsSkillsAndJdRequirements() {
        val himmlerResumeText = """
            Himmler Benitez
            Staff Engineer | AI Software Engineering & Quality
            himmlerbenitez@gmail.com linkedin.com/in/himmlerbenitez github.com/HimmlerB

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

            Certifications
            ISTQB® Certified Tester, Foundation Level (CTFL) — 2022
            Scrum Fundamentals Certified (SFC) — 2021

            Technical Skills
            AI & Machine Learning: Python, PyTorch, TensorFlow, Scikit-learn, LangChain, LangGraph, RAG Architectures, Multi-Agent Systems, Prompt Engineering, Vector Databases (FAISS, ChromaDB), Deep Learning (CNNs, Transformers), Computer Vision, NLP.
            Test Automation & Engineering: End-to-End Test Architecture, CI/CD (Jenkins, GitLab CI, GitHub Actions), Pytest, Robot Framework, Docker, Linux Administration, Git, SQL, RESTful APIs.
            Enterprise Systems & Firmware: Server Architecture, UEFI/BIOS, BMC (OpenBMC, Redfish, IPMI), Hardware-in-the-Loop (HIL) Testing, PCIe, CXL, DO-178B/C Avionics Compliance.
        """.trimIndent()

        val mlEngineerJdText = """
            Machine Learning Engineer
            Location: [Remote / Hybrid / On-site]
            Employment Type: Full-time
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
            Bachelor's or Master's degree in Computer Science, Data Science, Mathematics, or a related technical field (or equivalent practical experience).
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

        val extractor = DocumentTextExtractor(context = null)
        val cvChunks = chunker.chunkDocument("CURRICULUM", extractor.normalizeText(himmlerResumeText))
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", extractor.normalizeText(mlEngineerJdText))

        val customEngine = LiteRagEngineImpl(chunker)
        customEngine.updateIndex(cvChunks + jdChunks)

        // Specialized technical interview question from uploaded audio turn 04:
        val specializedQuestion = "Could you walk me through an end-to-end project where you took a trained PyTorch or scikit-learn model and exposed it via an API like FastAPI or Flask inside a Docker container? What tradeoffs did you make regarding latency versus throughput?"

        val ragResult = customEngine.retrieveFormattedContext(specializedQuestion, maxChars = 1100)
        println("=== SPECIALIZED ML TECHNICAL RETRIEVAL ===")
        println("CV Chunks: ${ragResult.cvChunkCount}, JD Chunks: ${ragResult.jdChunkCount}")
        println(ragResult.formattedContext)

        // 1. Must retrieve Technical Skills or Projects or Experience with PyTorch / Docker / APIs
        assertTrue(
            "Must retrieve relevant technical content (Projects or Technical Skills or Experience)",
            ragResult.formattedContext.contains("Technical Skills", ignoreCase = true) ||
                ragResult.formattedContext.contains("Projects", ignoreCase = true) ||
                ragResult.formattedContext.contains("Python", ignoreCase = true)
        )

        // 2. Must retrieve Target Job deployment responsibilities
        assertTrue(
            "Must retrieve Target Job deployment context",
            ragResult.formattedContext.contains("Deploy models", ignoreCase = true) ||
                ragResult.formattedContext.contains("REST APIs", ignoreCase = true) ||
                ragResult.formattedContext.contains("Responsibilities", ignoreCase = true) ||
                ragResult.formattedContext.contains("Qualifications", ignoreCase = true)
        )

        // 3. Verify AppConfig prompt instructs LLM to answer the technical substance first rather than just recapping the CV
        val appConfig = AppConfig(File("dummy/path"))
        val prompt = appConfig.formatGemmaPrompt(specializedQuestion, ragContext = ragResult.formattedContext)
        assertTrue(prompt.contains(AppConfig.CANDIDATE_RAG_GROUNDING_DIRECTIVE))
    }
}

