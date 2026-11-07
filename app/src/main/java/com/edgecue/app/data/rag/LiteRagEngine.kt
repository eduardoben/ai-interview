package com.edgecue.app.data.rag

import com.edgecue.app.data.rag.db.RagChunkEntity
import java.util.concurrent.atomic.AtomicReference

/**
 * Result of Lite RAG retrieval.
 */
data class FormattedRagContext(
    val formattedContext: String,
    val cvChunkCount: Int,
    val jdChunkCount: Int,
    val retrievalLatencyMicros: Long,
    val usedFallbackOverview: Boolean = false
)

/**
 * Contract for ultra-low-latency on-device lexical RAG retrieval.
 */
interface LiteRagEngine {
    /**
     * Synchronously retrieves and formats the top candidate CV and target JD chunks
     * within the strict [maxChars] budget.
     */
    fun retrieveFormattedContext(question: String, maxChars: Int = 700): FormattedRagContext

    /**
     * Atomically swaps the in-memory lexical index snapshot.
     */
    fun updateIndex(chunks: List<RagChunkEntity>)
}

/**
 * High-performance, zero-neural-weight in-memory RAG engine.
 * Combines Okapi BM25, exact bigram matching, section boosting, broad question query
 * expansion, and deterministic chunk 0 overview fallback.
 */
class LiteRagEngineImpl(
    private val chunker: RagDocumentChunker = RagDocumentChunker(),
    private val k1: Double = 1.2,
    private val b: Double = 0.75,
    private val bigramWeight: Double = 1.5,
    private val sectionBoostWeight: Double = 0.8
) : LiteRagEngine {

    private val snapshotRef = AtomicReference(RagIndexSnapshot.EMPTY)

    override fun updateIndex(chunks: List<RagChunkEntity>) {
        val newSnapshot = RagIndexSnapshot.build(chunks, chunker)
        snapshotRef.set(newSnapshot)
    }

    override fun retrieveFormattedContext(question: String, maxChars: Int): FormattedRagContext {
        val startNano = System.nanoTime()
        val snapshot = snapshotRef.get()

        if (snapshot.totalChunks == 0 || question.isBlank()) {
            val latency = (System.nanoTime() - startNano) / 1000
            return FormattedRagContext(
                formattedContext = "",
                cvChunkCount = 0,
                jdChunkCount = 0,
                retrievalLatencyMicros = latency
            )
        }

        val rawQueryTokens = chunker.tokenizeText(question)
        val expandedTokens = expandQueryTokens(question, rawQueryTokens)

        val queryBigrams = HashSet<String>()
        for (i in 0 until rawQueryTokens.size - 1) {
            queryBigrams.add("${rawQueryTokens[i]}_${rawQueryTokens[i + 1]}")
        }

        val questionCategory = classifyQuestionCategory(question)

        // Score CV chunks
        val scoredCv = scoreChunks(snapshot.cvChunks, expandedTokens, queryBigrams, questionCategory, snapshot)
        // Score JD chunks
        val scoredJd = scoreChunks(snapshot.jdChunks, expandedTokens, queryBigrams, questionCategory, snapshot)

        var usedFallback = false

        val maxCvChunks = when {
            maxChars >= 900 -> 3
            maxChars >= 550 -> if (questionCategory == QuestionCategory.ROLE_FIT || questionCategory == QuestionCategory.GENERAL) 3 else 2
            maxChars >= 350 -> 2
            else -> 1
        }

        val (selectedCvChunks, cvFallback) = selectTopDiverseChunks(
            scored = scoredCv,
            allChunks = snapshot.cvChunks,
            maxChunks = maxCvChunks,
            preferInitialSectionChunks = questionCategory == QuestionCategory.ROLE_FIT || questionCategory == QuestionCategory.GENERAL
        )
        if (cvFallback) usedFallback = true

        // Select top JD chunk or overview fallback
        val topJdChunk = if (scoredJd.isNotEmpty() && scoredJd.first().score > 0.05) {
            scoredJd.first().chunk
        } else {
            snapshot.jdChunks.firstOrNull { it.entity.chunkIndex == 0 }?.also { usedFallback = true }
                ?: scoredJd.firstOrNull()?.chunk
        }

        val formatted = formatBudgetedContext(selectedCvChunks, topJdChunk, maxChars)
        val latency = (System.nanoTime() - startNano) / 1000

        return FormattedRagContext(
            formattedContext = formatted,
            cvChunkCount = selectedCvChunks.size,
            jdChunkCount = if (topJdChunk != null) 1 else 0,
            retrievalLatencyMicros = latency,
            usedFallbackOverview = usedFallback
        )
    }

    private fun selectTopDiverseChunks(
        scored: List<ScoredChunk>,
        allChunks: List<IndexedChunk>,
        maxChunks: Int,
        preferInitialSectionChunks: Boolean = false
    ): Pair<List<IndexedChunk>, Boolean> {
        if (allChunks.isEmpty()) return Pair(emptyList(), false)

        val positive = scored.filter { it.score > 0.05 }
        if (positive.isEmpty()) {
            val fallback = allChunks.firstOrNull { it.entity.chunkIndex == 0 } ?: allChunks.first()
            return Pair(listOf(fallback), true)
        }

        val topScore = positive.first().score
        val selected = ArrayList<IndexedChunk>(maxChunks)
        val seenSections = HashSet<String>()

        // Pass 1: Section-diverse selection (pick top chunk from each relevant section)
        val crossSectionMinRatio = if (preferInitialSectionChunks) 0.22 else 0.42
        for (sc in positive) {
            if (selected.size >= maxChunks) break
            val secKey = sc.chunk.entity.sectionTitle.lowercase()
            if (secKey !in seenSections && sc.score >= topScore * crossSectionMinRatio) {
                // For broad overview/walkthrough questions, prefer the first chunk of each section
                // (e.g., most recent Experience role) if its score is close to the top chunk in that section.
                val chosenChunk = if (preferInitialSectionChunks) {
                    val firstInSec = positive
                        .filter { it.chunk.entity.sectionTitle.equals(sc.chunk.entity.sectionTitle, ignoreCase = true) }
                        .minByOrNull { it.chunk.entity.chunkIndex }
                    if (firstInSec != null && firstInSec.score >= sc.score * 0.65) {
                        firstInSec.chunk
                    } else {
                        sc.chunk
                    }
                } else {
                    sc.chunk
                }
                selected.add(chosenChunk)
                seenSections.add(secKey)
            }
        }

        // Pass 2: Fill remaining slots with high-scoring chunks from any section
        if (selected.size < maxChunks) {
            for (sc in positive) {
                if (selected.size >= maxChunks) break
                if (sc.chunk !in selected && sc.score >= topScore * 0.45) {
                    selected.add(sc.chunk)
                }
            }
        }

        return Pair(selected, false)
    }

    private data class ScoredChunk(val chunk: IndexedChunk, val score: Double)

    private fun scoreChunks(
        chunks: List<IndexedChunk>,
        queryTokens: List<String>,
        queryBigrams: Set<String>,
        category: QuestionCategory,
        snapshot: RagIndexSnapshot
    ): List<ScoredChunk> {
        if (chunks.isEmpty()) return emptyList()

        val avgdl = snapshot.avgdl.coerceAtLeast(1.0)
        val scored = ArrayList<ScoredChunk>(chunks.size)

        for (c in chunks) {
            var bm25 = 0.0
            for (token in queryTokens) {
                val tf = c.termFreqs[token] ?: 0
                if (tf > 0) {
                    val idf = snapshot.idf[token] ?: 0.5
                    val numerator = tf * (k1 + 1.0)
                    val denominator = tf + k1 * (1.0 - b + b * (c.docLength / avgdl))
                    bm25 += idf * (numerator / denominator)
                }
            }

            var bigramScore = 0.0
            if (queryBigrams.isNotEmpty()) {
                for (bg in queryBigrams) {
                    if (c.bigrams.contains(bg)) {
                        bigramScore += bigramWeight
                    }
                }
            }

            val lexicalScore = bm25 + bigramScore
            var sectionBoost = 0.0
            if (lexicalScore > 0.0 && isSectionBoosted(c.entity.sectionTitle, category)) {
                sectionBoost = sectionBoostWeight
            }

            val totalScore = lexicalScore + sectionBoost
            scored.add(ScoredChunk(c, totalScore))
        }

        scored.sortByDescending { it.score }
        return scored
    }

    private enum class QuestionCategory {
        TECHNICAL,
        BEHAVIORAL,
        ROLE_FIT,
        GENERAL
    }

    private fun classifyQuestionCategory(question: String): QuestionCategory {
        val lower = question.lowercase()
        return when {
            lower.contains("tell me about yourself") ||
                    lower.contains("walk me through your") ||
                    lower.contains("introduce yourself") ||
                    lower.contains("why this role") ||
                    lower.contains("why our company") ||
                    lower.contains("why should we hire you") ||
                    lower.contains("fit for this role") -> QuestionCategory.ROLE_FIT

            lower.contains("describe a time") ||
                    lower.contains("tell me about a time") ||
                    lower.contains("conflict") ||
                    lower.contains("challenge") ||
                    lower.contains("mentored") ||
                    lower.contains("led") ||
                    lower.contains("leadership") ||
                    lower.contains("failure") ||
                    lower.contains("success") -> QuestionCategory.BEHAVIORAL

            lower.contains("how") ||
                    lower.contains("what is") ||
                    lower.contains("code") ||
                    lower.contains("architecture") ||
                    lower.contains("concurrency") ||
                    lower.contains("coroutine") ||
                    lower.contains("jni") ||
                    lower.contains("tpu") ||
                    lower.contains("gpu") ||
                    lower.contains("npu") ||
                    lower.contains("audio") ||
                    lower.contains("buffer") ||
                    lower.contains("streaming") ||
                    lower.contains("vad") ||
                    lower.contains("stt") ||
                    lower.contains("llm") ||
                    lower.contains("optim") -> QuestionCategory.TECHNICAL

            else -> QuestionCategory.GENERAL
        }
    }

    private fun isSectionBoosted(sectionTitle: String, category: QuestionCategory): Boolean {
        val s = sectionTitle.lowercase()
        return when (category) {
            QuestionCategory.TECHNICAL ->
                s.contains("skill") || s.contains("technolog") || s.contains("experience") || s.contains("project")
            QuestionCategory.BEHAVIORAL ->
                s.contains("experience") || s.contains("project") || s.contains("work history") || s.contains("employment")
            QuestionCategory.ROLE_FIT ->
                s.contains("summary") || s.contains("overview") || s.contains("experience") ||
                        s.contains("education") || s.contains("requirement") || s.contains("qualification") || s.contains("responsibilit")
            QuestionCategory.GENERAL ->
                s.contains("summary") || s.contains("overview") || s.contains("experience") || s.contains("education")
        }
    }

    private fun expandQueryTokens(question: String, baseTokens: List<String>): List<String> {
        val lower = question.lowercase()
        val expansion = mutableListOf<String>()
        expansion.addAll(baseTokens)

        fun addStemmed(vararg words: String) {
            for (w in words) {
                expansion.add(chunker.stemToken(w))
            }
        }

        if (lower.contains("tell me about yourself") ||
            lower.contains("walk me through") ||
            lower.contains("your background") ||
            lower.contains("your resume") ||
            lower.contains("introduce yourself")
        ) {
            addStemmed("summary", "overview", "experience", "background", "education", "engineer", "lead")
        }

        if (lower.contains("academic") ||
            lower.contains("progression") ||
            lower.contains("education") ||
            lower.contains("degree") ||
            lower.contains("university") ||
            lower.contains("school") ||
            lower.contains("studied") ||
            lower.contains("study") ||
            lower.contains("master") ||
            lower.contains("bachelor")
        ) {
            addStemmed("education", "academic", "degree", "university", "universidad", "progression", "master", "bachelor", "candidate")
        }

        if (lower.contains("professional") ||
            lower.contains("journey") ||
            lower.contains("career") ||
            lower.contains("work history") ||
            lower.contains("previous role") ||
            lower.contains("experience")
        ) {
            addStemmed("experience", "professional", "journey", "career", "work", "role", "company", "lead", "engineer")
        }

        if (lower.contains("why this role") ||
            lower.contains("why our company") ||
            lower.contains("why should we hire you") ||
            lower.contains("good fit") ||
            lower.contains("fit for")
        ) {
            addStemmed("requirements", "qualifications", "role", "fit", "skills", "overview", "summary", "experience")
        }

        if (lower.contains("strength") || lower.contains("bring to the role") || lower.contains("skills")) {
            addStemmed("skills", "technical", "strength", "experience", "achievement", "summary", "projects")
        }

        return expansion
    }

    private fun formatBudgetedContext(
        cvChunks: List<IndexedChunk>,
        jdChunk: IndexedChunk?,
        maxChars: Int
    ): String {
        if (cvChunks.isEmpty() && jdChunk == null) return ""

        val result = StringBuilder()

        if (cvChunks.isNotEmpty() && jdChunk != null) {
            val jdHeader = "\n[Target Job - ${jdChunk.entity.sectionTitle}]:\n"
            val cvTotalHeaders = cvChunks.sumOf { "[Candidate CV - ${it.entity.sectionTitle}]: ".length + 1 }
            val availableTotal = (maxChars - jdHeader.length - cvTotalHeaders).coerceAtLeast(80)

            // Allocate ~78% of content budget to the Candidate CV chunks and ~22% to Target Job
            val totalCvBudget = (availableTotal * 0.78).toInt().coerceAtLeast(60)
            val jdBudget = (availableTotal - totalCvBudget).coerceAtLeast(20).coerceAtMost(320)

            val formattedCvBlock = formatMultipleCvChunks(cvChunks, totalCvBudget)
            val safeJdContent = trimToBoundary(jdChunk.entity.content, jdBudget)

            result.append(formattedCvBlock)
            result.append(jdHeader).append(safeJdContent)
        } else if (cvChunks.isNotEmpty()) {
            val cvTotalHeaders = cvChunks.sumOf { "[Candidate CV - ${it.entity.sectionTitle}]: ".length + 1 }
            val totalCvBudget = (maxChars - cvTotalHeaders).coerceAtLeast(60)
            result.append(formatMultipleCvChunks(cvChunks, totalCvBudget))
        } else if (jdChunk != null) {
            val header = "[Target Job - ${jdChunk.entity.sectionTitle}]:\n"
            val safeJdContent = trimToBoundary(jdChunk.entity.content, (maxChars - header.length).coerceAtLeast(40))
            result.append(header).append(safeJdContent)
        }

        var formatted = result.toString().trim()
        if (formatted.length > maxChars) {
            formatted = formatted.take((maxChars - 3).coerceAtLeast(1)).trimEnd() + "..."
        }
        return formatted
    }

    private fun formatMultipleCvChunks(cvChunks: List<IndexedChunk>, totalContentBudget: Int): String {
        if (cvChunks.size == 1) {
            val c = cvChunks[0]
            val header = "[Candidate CV - ${c.entity.sectionTitle}]:\n"
            return header + trimToBoundary(c.entity.content, totalContentBudget)
        }

        // Redistribute unused budget from shorter chunks (e.g., Education) to longer chunks (e.g., Experience/Summary)
        val budgets = IntArray(cvChunks.size)
        val indicesByLength = cvChunks.indices.sortedBy { cvChunks[it].entity.content.length }
        var remainingBudget = totalContentBudget
        var remainingCount = cvChunks.size

        for (idx in indicesByLength) {
            val fairShare = (remainingBudget / remainingCount).coerceAtLeast(40)
            val actualLen = cvChunks[idx].entity.content.length
            val allocated = minOf(actualLen, fairShare)
            budgets[idx] = allocated
            remainingBudget = (remainingBudget - allocated).coerceAtLeast(0)
            remainingCount--
        }

        // If any surplus budget remains, distribute it to chunks that were truncated
        if (remainingBudget > 0) {
            for (idx in cvChunks.indices) {
                val needed = cvChunks[idx].entity.content.length - budgets[idx]
                if (needed > 0 && remainingBudget > 0) {
                    val extra = minOf(needed, remainingBudget)
                    budgets[idx] += extra
                    remainingBudget -= extra
                }
            }
        }

        return cvChunks.mapIndexed { idx, chunk ->
            val safeContent = trimToBoundary(chunk.entity.content, budgets[idx])
            "[Candidate CV - ${chunk.entity.sectionTitle}]: $safeContent"
        }.joinToString("\n")
    }

    private fun trimToBoundary(text: String, maxLen: Int): String {
        if (text.length <= maxLen) return text
        val candidate = text.take(maxLen)
        val lastSentenceBoundary = candidate.lastIndexOfAny(charArrayOf('.', '\n'))
        if (lastSentenceBoundary > maxLen - 60) {
            return candidate.substring(0, lastSentenceBoundary + 1).trim()
        }
        val lastWordBoundary = candidate.lastIndexOf(' ')
        if (lastWordBoundary > maxLen - 30) {
            return candidate.substring(0, lastWordBoundary).trim() + "..."
        }
        return candidate.trim() + "..."
    }
}
