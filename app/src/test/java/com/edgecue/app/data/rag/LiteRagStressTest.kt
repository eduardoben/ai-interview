package com.edgecue.app.data.rag

import com.edgecue.app.data.rag.db.RagChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Empirical stress-test suite for Milestone 1 Lite RAG subsystem.
 * Evaluates extreme inputs, latency SLAs (< 2.0 ms, median < 0.5 ms),
 * context budget caps (<= 700 chars), query expansion, and concurrent access.
 */
class LiteRagStressTest {

    private val realisticCv = """
        PROFESSIONAL SUMMARY
        Principal Mobile & On-Device ML Architect with 10+ years engineering high-throughput low-latency Android systems.
        Pioneered on-device EdgeTPU and Vulkan GPU acceleration for conversational LLMs, local speech recognition, and zero-allocation DSP pipelines.

        CORE COMPETENCIES
        Languages: Kotlin, C++, Java, Python, Rust
        Frameworks: Jetpack Compose, Kotlin Coroutines & Flow, Room, LiteRT-LM, Sherpa-ONNX, ONNX Runtime
        Hardware Delegation: Google Tensor EdgeTPU, Qualcomm Adreno OpenCL, MediaTek APU

        EXPERIENCE
        Google / Android System Architecture - Staff Systems Engineer (2022 - Present)
        - Engineered dedicated JNI single-threaded serialization dispatcher LiteRT-GPU-Thread eliminating native C++ concurrency panics.
        - Integrated Gemma 4 E2B models on Tensor G3/G4/G5 SoCs with sub-millisecond TTFT prefill latency.
        - Designed zero-allocation CircularFloatRingBuffer for 16 kHz mono PCM-16 audio streaming eliminating GC pause jitter.

        EdgeCue AI - Principal Architect (2020 - 2022)
        - Built two-pass hybrid speech recognition using streaming Zipformer transducer with Silero VAD gating.
        - Implemented QuestionPunctuationEngine restoring 100+ technical acronyms (REST, API, JNI, MVVM, NPU) in < 0.25ms.
        - Hardened background foreground service with PARTIAL_WAKE_LOCK and graceful thermal throttling under THERMAL_STATUS_SEVERE.

        EDUCATION
        M.S. in Computer Science - Stanford University
        B.S. in Electrical Engineering & Computer Science - UC Berkeley
    """.trimIndent()

    private val realisticJd = """
        ROLE OVERVIEW
        We are seeking a Staff Mobile & On-Device AI Engineer to pioneer local private conversational intelligence.
        You will own the on-device inference stack, integrating state-of-the-art small language models and real-time audio systems.

        RESPONSIBILITIES
        - Architect zero-cloud-latency on-device RAG subsystems retrieving resume and job context in under 2.0 milliseconds.
        - Optimize native C++ JNI bridges and LiteRT hardware acceleration across heterogeneous Android NPU, GPU, and CPU backends.
        - Implement robust background services, low-power audio streaming, and seamless Jetpack Compose teleprompter UI.

        MINIMUM QUALIFICATIONS
        - 8+ years building complex Android applications with deep expertise in Kotlin, Coroutines, Flow, and NPU acceleration.
        - Demonstrated production experience deploying on-device LLMs (LiteRT / ONNX) and speech pipelines (Sherpa-ONNX / Whisper).
        - Mastery of memory management, zero-allocation ring buffers, and JNI thread safety.

        PREFERRED QUALIFICATIONS
        - Prior experience with Google Tensor TPU shims (libLiteRtDispatch_GoogleTensor.so) and Vulkan GPU delegates.
        - Contributions to open-source edge AI or high-frequency audio DSP libraries.
    """.trimIndent()

    // =========================================================================
    // CHALLENGE 1: Extreme Inputs & Boundary Conditions
    // =========================================================================

    @Test
    fun testExtremeInputs_EmptyAndWhitespace() {
        val chunker = RagDocumentChunker()
        val emptyChunks = chunker.chunkDocument("CURRICULUM", "")
        assertTrue("Empty document must produce 0 chunks", emptyChunks.isEmpty())

        val whitespaceChunks = chunker.chunkDocument("JOB_DESCRIPTION", "   \n\t  \r\n   ")
        assertTrue("Pure whitespace must produce 0 chunks", whitespaceChunks.isEmpty())
    }

    @Test
    fun testExtremeInputs_SingleCharacterAndPunctuation() {
        val chunker = RagDocumentChunker()
        val singleCharChunks = chunker.chunkDocument("CURRICULUM", "a")
        assertEquals(1, singleCharChunks.size)
        assertEquals("a", singleCharChunks[0].content)

        val punctuationChunks = chunker.chunkDocument("JOB_DESCRIPTION", "?!.,:;")
        assertEquals(1, punctuationChunks.size)

        // Index single-char chunks and verify retrieval handles without divide-by-zero
        val engine = LiteRagEngineImpl(chunker)
        engine.updateIndex(singleCharChunks)
        val result = engine.retrieveFormattedContext("query for single char", maxChars = 700)
        assertNotNull(result)
        assertTrue(result.formattedContext.contains("a"))
    }

    @Test
    fun testExtremeInputs_NoSpacesLongString() {
        val chunker = RagDocumentChunker()
        val noSpaceText = "x".repeat(2500)
        val chunks = chunker.chunkDocument("CURRICULUM", noSpaceText)

        assertFalse("Should produce chunks for long unspaced text", chunks.isEmpty())
        for ((idx, chunk) in chunks.withIndex()) {
            assertTrue(
                "Chunk $idx length ${chunk.content.length} must be <= 360",
                chunk.content.length <= 360
            )
        }
    }

    @Test
    fun testExtremeInputs_HugeDocument50kCap() {
        val extractor = DocumentTextExtractor(null)
        // Generate 120,000 characters
        val hugeText = buildString {
            append("PROFESSIONAL SUMMARY\n")
            repeat(1500) { i ->
                append("Sentence $i: Engineering scalable Android AI architectures with Kotlin and LiteRT. ")
            }
        }
        assertTrue("Generated text should exceed 100k chars", hugeText.length > 100_000)

        val normalized = extractor.normalizeText(hugeText)
        assertTrue(
            "Normalized text must be <= 50,000 chars, was ${normalized.length}",
            normalized.length <= DocumentTextExtractor.MAX_DOCUMENT_CHARS
        )

        val chunker = RagDocumentChunker()
        val chunks = chunker.chunkDocument("CURRICULUM", normalized)
        assertFalse(chunks.isEmpty())
        for (c in chunks) {
            assertTrue("Chunk length must be <= 360 chars, was ${c.content.length}", c.content.length <= 360)
        }
    }

    @Test
    fun testExtremeInputs_UnicodeEmojisAndMultilingual() {
        val chunker = RagDocumentChunker()
        val multilingualText = """
            PROFESSIONAL SUMMARY
            Building edge AI: 🚀 🔥 🎉 💻 📱 🧪 🤖.
            Multilingual support:
            - Chinese: 高级安卓架构师 负责端侧大模型推理与语音识别
            - Japanese: モバイルエンジニア 機械学習の最適化
            - Arabic: مهندس برمجيات ذكاء اصطناعي
            - German: Senior Entwickler für maschinelles Lernen mit Kotlin und C++
            - Accented: Résumé Ingénieur expérimenté en systèmes embarqués et modèles légers
        """.trimIndent()

        val chunks = chunker.chunkDocument("CURRICULUM", multilingualText)
        assertFalse(chunks.isEmpty())

        val engine = LiteRagEngineImpl(chunker)
        engine.updateIndex(chunks)

        // Query with emojis
        val resEmoji = engine.retrieveFormattedContext("Tell me about 🚀 and Kotlin", maxChars = 700)
        assertTrue(resEmoji.formattedContext.isNotBlank())

        // Query with accented words
        val resAccented = engine.retrieveFormattedContext("Résumé Ingénieur Kotlin", maxChars = 700)
        assertTrue(resAccented.formattedContext.isNotBlank())
    }

    @Test
    fun testExtremeInputs_ControlCharacters() {
        val extractor = DocumentTextExtractor(null)
        val dirtyText = "Start\u0000\u0001\u0007\u0008\u000B\u000C\u000E\u001FMiddle\uFEFFEnd"
        val cleaned = extractor.normalizeText(dirtyText)
        assertEquals("StartMiddleEnd", cleaned)
        assertFalse("Control characters must be stripped", cleaned.contains("\u0000"))
        assertFalse("BOM must be stripped", cleaned.contains("\uFEFF"))
    }

    // =========================================================================
    // CHALLENGE 2: Latency SLA (< 2.0 ms, median < 0.5 ms) over 100 queries
    // =========================================================================

    @Test
    fun testRetrievalLatencySlaOver100ConsecutiveQueries() {
        val chunker = RagDocumentChunker()
        val engine = LiteRagEngineImpl(chunker)

        val cvChunks = chunker.chunkDocument("CURRICULUM", realisticCv)
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", realisticJd)
        engine.updateIndex(cvChunks + jdChunks)

        val queries = listOf(
            "How do you handle JNI thread serialization and LiteRT?",
            "What is your experience with Google Tensor EdgeTPU?",
            "Can you explain the CircularFloatRingBuffer for 16 kHz audio?",
            "Tell me about yourself and your background.",
            "Why are you interested in this Staff Mobile AI role?",
            "Describe a time you handled severe thermal throttling on mobile.",
            "What is your experience with Sherpa-ONNX streaming Zipformer?",
            "How do you manage Room migrations and database privacy?",
            "What Kotlin coroutine dispatchers do you use for native C++ calls?",
            "What are your core strengths in on-device machine learning?"
        )

        // Warm up JVM JIT (50 queries)
        for (i in 0 until 50) {
            engine.retrieveFormattedContext(queries[i % queries.size], maxChars = 700)
        }

        // Measure 100 consecutive queries
        val latenciesMicros = LongArray(100)
        for (i in 0 until 100) {
            val q = queries[i % queries.size]
            val t0 = System.nanoTime()
            val result = engine.retrieveFormattedContext(q, maxChars = 700)
            val elapsedMicros = (System.nanoTime() - t0) / 1000
            latenciesMicros[i] = elapsedMicros

            assertTrue("Result formatted context must not be blank", result.formattedContext.isNotBlank())
        }

        latenciesMicros.sort()
        val minLatency = latenciesMicros.first()
        val maxLatency = latenciesMicros.last()
        val medianLatency = latenciesMicros[50]
        val p95Latency = latenciesMicros[95]
        val p99Latency = latenciesMicros[99]
        val avgLatency = latenciesMicros.average()

        println("=== LATENCY BENCHMARK (100 QUERIES) ===")
        println("Min: ${minLatency}µs (${minLatency / 1000.0}ms)")
        println("Median: ${medianLatency}µs (${medianLatency / 1000.0}ms)")
        println("Mean: ${avgLatency}µs (${avgLatency / 1000.0}ms)")
        println("P95: ${p95Latency}µs (${p95Latency / 1000.0}ms)")
        println("P99: ${p99Latency}µs (${p99Latency / 1000.0}ms)")
        println("Max: ${maxLatency}µs (${maxLatency / 1000.0}ms)")

        // Hard Acceptance Criteria:
        // 1. P99 latency < 2.0 ms (2,000 µs)
        assertTrue(
            "P99 latency must be < 2000 µs (2.0 ms), was ${p99Latency}µs",
            p99Latency < 2000
        )
        // 2. Median latency < 0.5 ms (500 µs)
        assertTrue(
            "Median latency must be < 500 µs (0.5 ms), was ${medianLatency}µs",
            medianLatency < 500
        )
    }

    // =========================================================================
    // CHALLENGE 3: Context Budget Hard-Cap (<= 700 chars across 50 diverse queries)
    // =========================================================================

    @Test
    fun testContextBudgetHardCapAcross50DiverseQueries() {
        val chunker = RagDocumentChunker()
        val engine = LiteRagEngineImpl(chunker)

        val cvChunks = chunker.chunkDocument("CURRICULUM", realisticCv)
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", realisticJd)
        engine.updateIndex(cvChunks + jdChunks)

        val diverseQueries = (1..50).map { i ->
            when (i % 10) {
                0 -> "Tell me about yourself and your career highlights #$i"
                1 -> "Why this role at our company and why should we hire you #$i"
                2 -> "Deep dive into JNI memory corruption and single-threaded dispatcher #$i"
                3 -> "What is your experience with Sherpa-ONNX streaming Zipformer audio #$i"
                4 -> "Explain how CircularFloatRingBuffer avoids garbage collection churn #$i"
                5 -> "How do you deploy Gemma 4 E2B on Google Tensor TPU with libLiteRtDispatch #$i"
                6 -> "Describe your leadership style and experience mentoring engineers #$i"
                7 -> "What are the biggest challenges with on-device RAG under 700 chars #$i"
                8 -> "What is your approach to Android Room database schema migrations #$i"
                else -> "Random general question about architecture and performance #$i"
            }
        }

        val budgetsToTest = listOf(700, 600, 500, 400, 300, 200, 100)

        for (budget in budgetsToTest) {
            for ((idx, q) in diverseQueries.withIndex()) {
                val result = engine.retrieveFormattedContext(q, maxChars = budget)
                val len = result.formattedContext.length
                assertTrue(
                    "Query $idx with budget $budget exceeded cap! Length was $len: '${result.formattedContext.take(50)}...'",
                    len <= budget
                )
            }
        }
    }

    // =========================================================================
    // CHALLENGE 4: Query Expansion & Overview Fallback
    // =========================================================================

    @Test
    fun testQueryExpansionSurfacesSummaryAndOverview() {
        val chunker = RagDocumentChunker()
        val engine = LiteRagEngineImpl(chunker)

        val cvChunks = chunker.chunkDocument("CURRICULUM", realisticCv)
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", realisticJd)
        engine.updateIndex(cvChunks + jdChunks)

        // 1. Broad intro question
        val introResult = engine.retrieveFormattedContext("Tell me about yourself", maxChars = 700)
        assertTrue("Intro query must retrieve non-empty context", introResult.formattedContext.isNotBlank())
        assertTrue(
            "Intro query must surface Summary/Overview chunk",
            introResult.formattedContext.contains("Principal Mobile", ignoreCase = true) ||
                    introResult.formattedContext.contains("SUMMARY", ignoreCase = true) ||
                    introResult.formattedContext.contains("OVERVIEW", ignoreCase = true)
        )

        // 2. Role fit question
        val roleResult = engine.retrieveFormattedContext("Why this role?", maxChars = 700)
        assertTrue("Role fit query must retrieve context", roleResult.formattedContext.isNotBlank())
        assertTrue(
            "Role fit query must retrieve both CV and JD context",
            roleResult.cvChunkCount > 0 && roleResult.jdChunkCount > 0
        )

        // 3. Complete gibberish / out-of-domain question
        val gibberishResult = engine.retrieveFormattedContext("Zyxwvutsrqponmlkjihgfedcba 99999", maxChars = 700)
        assertTrue("Gibberish query should fall back to overview chunk", gibberishResult.formattedContext.isNotBlank())
        assertTrue("Must indicate fallback was used", gibberishResult.usedFallbackOverview)
        assertTrue(
            "Fallback context should contain chunk 0 content",
            gibberishResult.formattedContext.contains("SUMMARY", ignoreCase = true) ||
                    gibberishResult.formattedContext.contains("OVERVIEW", ignoreCase = true)
        )
    }

    // =========================================================================
    // CHALLENGE 5: Concurrent Read / Write Safety
    // =========================================================================

    @Test
    fun testConcurrentRetrievalAndIndexUpdates() {
        val chunker = RagDocumentChunker()
        val engine = LiteRagEngineImpl(chunker)

        val cvChunks = chunker.chunkDocument("CURRICULUM", realisticCv)
        val jdChunks = chunker.chunkDocument("JOB_DESCRIPTION", realisticJd)
        engine.updateIndex(cvChunks + jdChunks)

        val executor = Executors.newFixedThreadPool(8)
        val tasks = mutableListOf<Callable<Boolean>>()

        // 20 reader tasks executing 50 queries each
        for (i in 0 until 20) {
            tasks.add(Callable {
                for (j in 0 until 50) {
                    val res = engine.retrieveFormattedContext("How do you handle JNI and TPU $j?", maxChars = 700)
                    if (res.formattedContext.length > 700) return@Callable false
                }
                true
            })
        }

        // 5 writer tasks updating the index concurrently
        for (w in 0 until 5) {
            tasks.add(Callable {
                val newCv = realisticCv + "\nUPDATED $w"
                val chunks = chunker.chunkDocument("CURRICULUM", newCv)
                engine.updateIndex(chunks)
                true
            })
        }

        val futures = executor.invokeAll(tasks)
        executor.shutdown()
        assertTrue("Executor should terminate within 10s", executor.awaitTermination(10, TimeUnit.SECONDS))

        for (f in futures) {
            assertTrue("Concurrent task must complete successfully", f.get())
        }
    }

    private fun createMockContext(): android.content.Context {
        val handler = java.lang.reflect.InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getApplicationContext" -> proxy
                "getContentResolver" -> null
                "getPackageName" -> "com.edgecue.app"
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> 42
                "toString" -> "MockContextProxy"
                else -> null
            }
        }
        return java.lang.reflect.Proxy.newProxyInstance(
            android.content.Context::class.java.classLoader,
            arrayOf(android.content.Context::class.java),
            handler
        ) as android.content.Context
    }
}
