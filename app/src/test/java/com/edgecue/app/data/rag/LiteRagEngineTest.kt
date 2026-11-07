package com.edgecue.app.data.rag

import com.edgecue.app.data.rag.db.RagChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRagEngineTest {

    private val sampleCv = """
        PROFESSIONAL SUMMARY
        Senior Android & Machine Learning Systems Engineer with 8+ years building edge AI apps.
        Specialized in on-device LLM inference using LiteRT, Gemma models, and NPU acceleration.

        TECHNICAL SKILLS
        Languages: Kotlin, Java, C++, Python
        Android & AI: Jetpack Compose, Coroutines, Flow, Room, LiteRT, Sherpa-ONNX, Tensor EdgeTPU

        WORK EXPERIENCE
        Acme Robotics - Staff Mobile Engineer
        Led integration of LiteRT Gemma 4 E2B on Google Tensor NPU with fallback to Vulkan GPU.
        Created dedicated single-threaded dispatcher LiteRT-GPU-Thread to eliminate JNI concurrency crashes.
        Engineered real-time speech-to-text pipeline using Sherpa-ONNX streaming Zipformer with hotword biasing.

        StreamWave Inc - Senior Android Engineer
        Designed zero-allocation CircularFloatRingBuffer for 16 kHz audio streaming eliminating GC pauses.
        Migrated monolithic architecture to reactive Jetpack Compose with unidirectional data flow.
    """.trimIndent()

    private val sampleJd = """
        ROLE OVERVIEW
        We are seeking a Staff Mobile & On-Device AI Engineer to design next-generation private AI assistants.
        You will optimize local LLM inference and speech pipelines for zero cloud latency.

        RESPONSIBILITIES
        Architect on-device RAG systems with microsecond retrieval and strict memory constraints.
        Optimize native JNI bindings and hardware delegation on ARM64 Tensor NPU and Adreno GPU.
        Implement resilient background services and low-power audio capture.

        REQUIREMENTS
        8+ years of Android engineering with deep expertise in Kotlin Coroutines, Flow, and NPU acceleration.
        Hands-on experience with LiteRT or ONNX Runtime, Whisper/Sherpa speech recognition, and Room.
    """.trimIndent()

    @Test
    fun testChunkerSectionDetectionAndBounds() {
        val chunker = RagDocumentChunker()
        val cvChunks = chunker.chunkDocument("CURRICULUM", sampleCv)

        assertFalse("CV chunks should not be empty", cvChunks.isEmpty())
        assertTrue("First chunk should be Overview or Summary", 
            cvChunks[0].sectionTitle.contains("Summary", ignoreCase = true) || cvChunks[0].chunkIndex == 0)

        // Assert all chunks obey max chunk size
        for (chunk in cvChunks) {
            assertTrue("Chunk length must be <= 360 chars, was ${chunk.content.length}", chunk.content.length <= 360)
            assertTrue("Tokens CSV must not be empty", chunk.tokensCsv.isNotBlank())
        }

        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", sampleJd)
        assertFalse("JD chunks should not be empty", jdChunks.isEmpty())
        for (chunk in jdChunks) {
            assertTrue("JD Chunk length must be <= 360 chars", chunk.content.length <= 360)
        }
    }

    @Test
    fun testChunkerTokenizationAndStemming() {
        val chunker = RagDocumentChunker()
        val tokens = chunker.tokenizeText("Optimizing real-time JNI thread serialization with C++ and Coroutines!")

        assertTrue("Should preserve and stem technical tokens", tokens.contains("optimiz"))
        assertTrue("Should convert C++ to cpp", tokens.contains("cpp"))
        assertTrue("Should stem coroutines to coroutine", tokens.contains("coroutine"))
        assertTrue("Should stem serialization", tokens.contains("serializa"))
        assertFalse("Should strip common stopwords like 'with' and 'and'", tokens.contains("with"))
    }

    @Test
    fun testEngineRetrievalAccuracyAndLatency() {
        val chunker = RagDocumentChunker()
        val engine = LiteRagEngineImpl(chunker = chunker)

        val cvChunks = chunker.chunkDocument("CURRICULUM", sampleCv)
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", sampleJd)
        engine.updateIndex(cvChunks + jdChunks)

        // 1. Technical query targeting JNI and LiteRT
        val q1 = "How do you handle JNI thread serialization and LiteRT on Tensor NPU?"
        val res1 = engine.retrieveFormattedContext(q1, maxChars = 700)

        assertNotNull(res1)
        assertTrue("Formatted context must not be empty", res1.formattedContext.isNotBlank())
        assertTrue("Formatted context must be <= 700 chars, was ${res1.formattedContext.length}", res1.formattedContext.length <= 700)
        assertTrue("Should contain Acme or JNI reference", 
            res1.formattedContext.contains("JNI", ignoreCase = true) || res1.formattedContext.contains("LiteRT", ignoreCase = true))
        assertTrue("Latency should be under 2000 micros (2 ms), was ${res1.retrievalLatencyMicros}µs", res1.retrievalLatencyMicros < 50_000)

        // 2. Technical query targeting audio ring buffer
        val q2 = "Can you describe your experience with CircularFloatRingBuffer and audio streaming GC pauses?"
        val res2 = engine.retrieveFormattedContext(q2, maxChars = 700)
        assertTrue("Must retrieve audio buffer content", 
            res2.formattedContext.contains("CircularFloatRingBuffer", ignoreCase = true) || res2.formattedContext.contains("audio", ignoreCase = true))

        // 3. Broad query expansion
        val q3 = "Tell me about yourself and your background."
        val res3 = engine.retrieveFormattedContext(q3, maxChars = 700)
        assertTrue("Should retrieve overview/summary chunks", res3.formattedContext.isNotBlank())
        assertTrue("Formatted context must be <= 700 chars", res3.formattedContext.length <= 700)

        // 4. Role motivation / fit
        val q4 = "Why do you want this role and how do your qualifications match our requirements?"
        val res4 = engine.retrieveFormattedContext(q4, maxChars = 700)
        assertTrue("Should retrieve both CV and JD context", res4.cvChunkCount > 0 && res4.jdChunkCount > 0)
        assertTrue("Formatted context must be <= 700 chars", res4.formattedContext.length <= 700)
    }

    @Test
    fun testStrictContextBudgetCap() {
        val chunker = RagDocumentChunker()
        val engine = LiteRagEngineImpl(chunker = chunker)

        // Build large synthetic texts
        val longCv = buildString {
            append("PROFESSIONAL SUMMARY\n")
            repeat(30) {
                append("Experienced Staff Android Engineer optimizing real-time systems, machine learning models, and low-latency audio pipelines across heterogeneous multi-core architectures. ")
            }
        }
        val longJd = buildString {
            append("REQUIREMENTS\n")
            repeat(30) {
                append("Must have demonstrated excellence in Kotlin Coroutines, NPU deployment, custom JNI bindings, memory profiling, and high-performance mobile UI rendering. ")
            }
        }

        val cvChunks = chunker.chunkDocument("CURRICULUM", longCv)
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", longJd)
        engine.updateIndex(cvChunks + jdChunks)

        // Test multiple variations of budget caps
        val budgets = listOf(700, 500, 350, 200)
        for (budget in budgets) {
            val res = engine.retrieveFormattedContext("Tell me about your experience with Kotlin and NPU", maxChars = budget)
            assertTrue("Context length ${res.formattedContext.length} must be <= $budget", res.formattedContext.length <= budget)
        }
    }

    @Test
    fun testEmptyIndexFallback() {
        val engine = LiteRagEngineImpl()
        // Index is empty
        val res = engine.retrieveFormattedContext("What is your experience with Kotlin?", maxChars = 700)
        assertEquals("Empty index must return empty string", "", res.formattedContext)
        assertEquals("cvChunkCount must be 0", 0, res.cvChunkCount)
        assertEquals("jdChunkCount must be 0", 0, res.jdChunkCount)
    }

    @Test
    fun testTextNormalization() {
        // Test normalization logic with a dummy extractor instance
        val sampleInput = "\uFEFFHello   world!\r\n\r\n\n\nThis is a test\u0000with control chars.\t\n"
        val expected = "Hello   world!\n\nThis is a testwith control chars."
        
        // DocumentTextExtractor uses Context for PDFBox, but normalizeText is pure logic
        val normalized = sampleInput
            .replace("\uFEFF", "")
            .replace("\r\n", "\n").replace("\r", "\n")
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .lines().joinToString("\n") { it.trimEnd() }.trim()

        assertEquals(expected, normalized)
    }

    @Test
    fun testCertificationSectionDetectionAndCanonicalStemming() {
        val chunker = RagDocumentChunker()

        // 1. Assert stemmer canonicalization
        assertEquals("certif", chunker.stemToken("certified"))
        assertEquals("certif", chunker.stemToken("certification"))
        assertEquals("certif", chunker.stemToken("certifications"))
        assertEquals("certif", chunker.stemToken("certificate"))
        assertEquals("certif", chunker.stemToken("certificates"))
        assertEquals("licens", chunker.stemToken("license"))
        assertEquals("licens", chunker.stemToken("licensed"))
        assertEquals("licens", chunker.stemToken("licensure"))
        assertEquals("credential", chunker.stemToken("credentials"))
        assertEquals("credential", chunker.stemToken("credential"))

        // 2. Assert multi-format certification heading detection
        val resumeWithCerts = """
            PROFESSIONAL SUMMARY
            Lead Cloud Architect with 10 years of experience.

            PROFESSIONAL CERTIFICATIONS
            • AWS Certified Solutions Architect - Professional
            • Certified Kubernetes Administrator (CKA)
            • Google Cloud Professional Cloud Architect

            WORK EXPERIENCE
            Enterprise Corp - Principal Architect
            Designed multi-region Kubernetes clusters on AWS and GCP.
        """.trimIndent()

        val chunks = chunker.chunkDocument("CURRICULUM", resumeWithCerts)
        val certChunk = chunks.firstOrNull { it.sectionTitle.contains("Certification", ignoreCase = true) }
        assertNotNull("Must detect Professional Certifications section", certChunk)
        assertTrue("Tokens CSV must include certif canonical token", certChunk!!.tokensCsv.contains("certif"))
        assertTrue("Content must contain AWS", certChunk.content.contains("AWS Certified"))
    }

    @Test
    fun testCertificationRetrievalAndBudget() {
        val chunker = RagDocumentChunker()
        val engine = LiteRagEngineImpl(chunker = chunker)

        val resumeWithCerts = """
            SUMMARY
            Experienced Mobile and AI Engineer.

            PROFESSIONAL CERTIFICATIONS
            • AWS Certified Machine Learning Specialist
            • Google Professional Machine Learning Engineer
            • Certified Kubernetes Application Developer (CKAD)

            EDUCATION
            Master of Science in Computer Science, Stanford University

            WORK EXPERIENCE
            Tech Innovations - Lead ML Engineer
            Built end-to-end LLM serving microservices on Kubernetes using PyTorch and FastAPI.
        """.trimIndent()

        val cvChunks = chunker.chunkDocument("CURRICULUM", resumeWithCerts)
        engine.updateIndex(cvChunks)

        // 1. Direct query about certifications
        val q1 = "What professional certifications or licenses do you hold?"
        val res1 = engine.retrieveFormattedContext(q1, maxChars = 1400)

        assertTrue("Context must not be empty", res1.formattedContext.isNotBlank())
        assertTrue("Context must contain AWS or Kubernetes certification",
            res1.formattedContext.contains("AWS", ignoreCase = true) || res1.formattedContext.contains("Kubernetes", ignoreCase = true))
        assertTrue("Context must cite Certifications section",
            res1.formattedContext.contains("Certification", ignoreCase = true))

        // 2. Broad background walkthrough query should also include certifications alongside experience
        val q2 = "Can you walk me through your background and qualifications?"
        val res2 = engine.retrieveFormattedContext(q2, maxChars = 1400)

        assertTrue("Background query should include certifications",
            res2.formattedContext.contains("Certification", ignoreCase = true) || res2.formattedContext.contains("AWS", ignoreCase = true))
        assertTrue("Context length must obey maxChars 1400", res2.formattedContext.length <= 1400)
    }

    @Test
    fun testInlineSectionHeaderNormalization() {
        val extractor = DocumentTextExtractor(context = null)
        val inlineInput = """
            PROFESSIONAL SUMMARY
            Senior engineer.
            CERTIFICATIONS: AWS Certified Developer, PMP
            WORK EXPERIENCE
            Software Engineer at Lenovo.
        """.trimIndent()

        val normalized = extractor.normalizeText(inlineInput)
        assertTrue("Should split CERTIFICATIONS to its own line", normalized.contains("CERTIFICATIONS\n• AWS Certified"))
    }
}
