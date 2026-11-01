package com.local.interviewassistant.e2e

import java.util.Locale

/**
 * Pure Kotlin evaluation engine for transcription accuracy (WER), text normalization,
 * compound-aware hotword recall, and LLM keyword relevance verification.
 *
 * Implements Milestone 3 (M3) verification algorithms without Android SDK dependencies,
 * enabling deterministic evaluation in both JVM unit tests and on-device instrumentation tests.
 */
object TranscriptEvaluator {

    /**
     * Word Error Rate calculation breakdown including Levenshtein edit distance operations.
     */
    data class WerResult(
        val wer: Float,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val referenceWords: Int,
        val hypothesisWords: Int
    )

    /**
     * Evaluation result for contextual domain hotwords recall.
     */
    data class HotwordMatchResult(
        val totalRequired: Int,
        val matchedCount: Int,
        val recall: Float,
        val matchedHotwords: List<String>,
        val missingHotwords: List<String>
    )

    private val CONTRACTION_REPLACEMENTS = listOf(
        Regex("\\bwhat's\\b") to "what is",
        Regex("\\bwhats\\b") to "what is",
        Regex("\\bhow's\\b") to "how is",
        Regex("\\bhows\\b") to "how is",
        Regex("\\bwhy's\\b") to "why is",
        Regex("\\bwhys\\b") to "why is",
        Regex("\\bwhere's\\b") to "where is",
        Regex("\\bwheres\\b") to "where is",
        Regex("\\bwhen's\\b") to "when is",
        Regex("\\bwhens\\b") to "when is",
        Regex("\\bwho's\\b") to "who is",
        Regex("\\bwhos\\b") to "who is",
        Regex("\\bthat's\\b") to "that is",
        Regex("\\bthats\\b") to "that is",
        Regex("\\bthere's\\b") to "there is",
        Regex("\\btheres\\b") to "there is",
        Regex("\\bhere's\\b") to "here is",
        Regex("\\bheres\\b") to "here is",
        Regex("\\bit's\\b") to "it is",
        Regex("\\bcan't\\b") to "cannot",
        Regex("\\bcant\\b") to "cannot",
        Regex("\\bdon't\\b") to "do not",
        Regex("\\bdont\\b") to "do not",
        Regex("\\bwon't\\b") to "will not",
        Regex("\\bwont\\b") to "will not",
        Regex("\\bdidn't\\b") to "did not",
        Regex("\\bdidnt\\b") to "did not",
        Regex("\\bdoesn't\\b") to "does not",
        Regex("\\bdoesnt\\b") to "does not",
        Regex("\\bisn't\\b") to "is not",
        Regex("\\bisnt\\b") to "is not",
        Regex("\\baren't\\b") to "are not",
        Regex("\\barent\\b") to "are not",
        Regex("\\bcouldn't\\b") to "could not",
        Regex("\\bcouldnt\\b") to "could not",
        Regex("\\bwouldn't\\b") to "would not",
        Regex("\\bwouldnt\\b") to "would not",
        Regex("\\bshouldn't\\b") to "should not",
        Regex("\\bshouldnt\\b") to "should not",
        Regex("\\bhasn't\\b") to "has not",
        Regex("\\bhasnt\\b") to "has not",
        Regex("\\bhaven't\\b") to "have not",
        Regex("\\bhavent\\b") to "have not",
        Regex("\\bi'm\\b") to "i am",
        Regex("\\bi've\\b") to "i have",
        Regex("\\bi'd\\b") to "i would",
        Regex("\\bi'll\\b") to "i will",
        Regex("\\byou're\\b") to "you are",
        Regex("\\byoure\\b") to "you are",
        Regex("\\byou've\\b") to "you have",
        Regex("\\byouve\\b") to "you have",
        Regex("\\bthey're\\b") to "they are",
        Regex("\\btheyre\\b") to "they are",
        Regex("\\bwe've\\b") to "we have",
        Regex("\\bweve\\b") to "we have",
        Regex("\\bgonna\\b") to "going to",
        Regex("\\bwanna\\b") to "want to"
    )

    /**
     * Normalizes text for evaluation:
     * 1. Lowercases text with [Locale.US].
     * 2. Expands common contractions matching QuestionPunctuationEngine grammar rules.
     * 3. Replaces non-alphanumeric characters ([^a-z0-9\\s]) with space.
     * 4. Collapses whitespace sequences into single spaces and trims edges.
     */
    fun normalize(text: String): String {
        var result = text.lowercase(Locale.US).trim()
        if (result.isEmpty()) return ""

        // Expand contractions before stripping punctuation
        for ((pattern, replacement) in CONTRACTION_REPLACEMENTS) {
            result = result.replace(pattern, replacement)
        }

        // Replace non-alphanumeric with spaces
        result = result.replace(Regex("[^a-z0-9\\s]"), " ")

        // Collapse multiple spaces
        return result.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Computes Word Error Rate (WER) using word-level Levenshtein distance dynamic programming.
     * Accurately calculates substitutions (S), deletions (D), and insertions (I) via optimal backtracking.
     */
    fun computeWer(reference: String, hypothesis: String): WerResult {
        val normRef = normalize(reference)
        val normHyp = normalize(hypothesis)

        val refTokens = if (normRef.isEmpty()) emptyList() else normRef.split(" ").filter { it.isNotBlank() }
        val hypTokens = if (normHyp.isEmpty()) emptyList() else normHyp.split(" ").filter { it.isNotBlank() }

        val m = refTokens.size
        val n = hypTokens.size

        if (m == 0) {
            return if (n == 0) {
                WerResult(wer = 0.0f, substitutions = 0, deletions = 0, insertions = 0, referenceWords = 0, hypothesisWords = 0)
            } else {
                WerResult(wer = 1.0f, substitutions = 0, deletions = 0, insertions = n, referenceWords = 0, hypothesisWords = n)
            }
        }

        if (n == 0) {
            return WerResult(wer = 1.0f, substitutions = 0, deletions = m, insertions = 0, referenceWords = m, hypothesisWords = 0)
        }

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (refTokens[i - 1] == hypTokens[j - 1]) 0 else 1
                val del = dp[i - 1][j] + 1
                val ins = dp[i][j - 1] + 1
                val sub = dp[i - 1][j - 1] + cost
                dp[i][j] = minOf(del, minOf(ins, sub))
            }
        }

        // Backtracking to find exact counts of S, D, I
        var i = m
        var j = n
        var substitutions = 0
        var deletions = 0
        var insertions = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                val cost = if (refTokens[i - 1] == hypTokens[j - 1]) 0 else 1
                if (dp[i][j] == dp[i - 1][j - 1] + cost) {
                    if (cost == 1) {
                        substitutions++
                    }
                    i--
                    j--
                    continue
                }
            }
            if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                deletions++
                i--
            } else if (j > 0 && dp[i][j] == dp[i][j - 1] + 1) {
                insertions++
                j--
            } else {
                if (i > 0) {
                    deletions++
                    i--
                } else {
                    insertions++
                    j--
                }
            }
        }

        val totalErrors = substitutions + deletions + insertions
        val wer = totalErrors.toFloat() / m.toFloat()

        return WerResult(
            wer = wer,
            substitutions = substitutions,
            deletions = deletions,
            insertions = insertions,
            referenceWords = m,
            hypothesisWords = n
        )
    }

    /**
     * Evaluates domain hotwords recall in [normalizedHypothesis].
     *
     * Applies compound-aware matching:
     * - Multi-word hotwords match contiguous token sequences or concatenated tokens.
     * - Single-word compound hotwords match exact tokens or adjacent bigram concatenation
     *   (t_k + t_{k+1} == hotword) so that split compounds (e.g. "state flow" vs "stateflow")
     *   achieve 100% recall without false positives on short words.
     */
    fun evaluateHotwords(normalizedHypothesis: String, mustContainHotwords: List<String>): HotwordMatchResult {
        if (mustContainHotwords.isEmpty()) {
            return HotwordMatchResult(
                totalRequired = 0,
                matchedCount = 0,
                recall = 1.0f,
                matchedHotwords = emptyList(),
                missingHotwords = emptyList()
            )
        }

        val normText = normalize(normalizedHypothesis)
        val textTokens = if (normText.isEmpty()) emptyList() else normText.split(" ").filter { it.isNotBlank() }

        val matched = mutableListOf<String>()
        val missing = mutableListOf<String>()

        for (hotword in mustContainHotwords) {
            val normHotword = normalize(hotword)
            if (normHotword.isBlank()) continue

            val hotwordTokens = normHotword.split(" ").filter { it.isNotBlank() }
            val isMatched = checkHotwordMatch(normText, textTokens, normHotword, hotwordTokens)

            if (isMatched) {
                matched.add(hotword)
            } else {
                missing.add(hotword)
            }
        }

        val totalRequired = mustContainHotwords.size
        val matchedCount = matched.size
        val recall = if (totalRequired > 0) matchedCount.toFloat() / totalRequired.toFloat() else 1.0f

        return HotwordMatchResult(
            totalRequired = totalRequired,
            matchedCount = matchedCount,
            recall = recall,
            matchedHotwords = matched,
            missingHotwords = missing
        )
    }

    private fun checkHotwordMatch(
        normText: String,
        textTokens: List<String>,
        normHotword: String,
        hotwordTokens: List<String>
    ): Boolean {
        if (textTokens.isEmpty() || hotwordTokens.isEmpty()) return false

        // Case 1: Multi-word hotword (e.g., "clean architecture", "can you explain", "what is the difference between")
        if (hotwordTokens.size > 1) {
            val n = hotwordTokens.size
            for (i in 0..textTokens.size - n) {
                if (textTokens.subList(i, i + n) == hotwordTokens) return true
            }
            // Check if hypothesis concatenated them into a single word (e.g. "cleanarchitecture")
            val joined = hotwordTokens.joinToString("")
            if (textTokens.contains(joined)) return true

            // Full substring check if normalized text contains contiguous phrase
            if (normText.contains(normHotword)) return true
            return false
        }

        // Case 2: Single-word hotword (e.g., "stateflow", "conflict", "python")
        val target = hotwordTokens[0]
        if (textTokens.contains(target)) return true

        // Compound-aware adjacent bigram concatenation (e.g. textTokens[k] + textTokens[k+1] == "stateflow")
        for (i in 0 until textTokens.size - 1) {
            if (textTokens[i] + textTokens[i + 1] == target) {
                return true
            }
        }

        return false
    }

    /**
     * Evaluates which expected keywords appear in the response text [text].
     * Supports exact tokens, compound concatenation, and grammatical suffix variants (plurals, verb tenses).
     */
    fun evaluateKeywords(text: String, expectedKeywords: List<String>): List<String> {
        if (expectedKeywords.isEmpty() || text.isBlank()) return emptyList()

        val normText = normalize(text)
        val textTokens = if (normText.isEmpty()) emptyList() else normText.split(" ").filter { it.isNotBlank() }
        if (textTokens.isEmpty()) return emptyList()

        val matched = mutableListOf<String>()

        for (kw in expectedKeywords) {
            val normKw = normalize(kw)
            if (normKw.isBlank()) continue

            val kwTokens = normKw.split(" ").filter { it.isNotBlank() }
            if (kwTokens.isEmpty()) continue

            if (kwTokens.size > 1) {
                // Multi-word keyword match
                var isMatched = false
                val n = kwTokens.size
                for (i in 0..textTokens.size - n) {
                    if (textTokens.subList(i, i + n) == kwTokens) {
                        isMatched = true
                        break
                    }
                }
                if (!isMatched && textTokens.contains(kwTokens.joinToString(""))) {
                    isMatched = true
                }
                if (!isMatched && normText.contains(normKw)) {
                    isMatched = true
                }
                if (isMatched) {
                    matched.add(kw)
                }
            } else {
                // Single-word keyword match with morphology handling
                val target = kwTokens[0]
                var isMatched = textTokens.contains(target)

                if (!isMatched) {
                    // Check bigram concatenation
                    for (i in 0 until textTokens.size - 1) {
                        if (textTokens[i] + textTokens[i + 1] == target) {
                            isMatched = true
                            break
                        }
                    }
                }

                if (!isMatched) {
                    // Check morphological variations (plurals, verb tenses)
                    for (token in textTokens) {
                        if (target.length <= 4) {
                            // Short words (e.g., "star"): strictly allow plural "stars", reject "start" or "started"
                            if (token == "${target}s") {
                                isMatched = true
                                break
                            }
                        } else {
                            // Words with 5+ chars (e.g., "listen", "escalate", "gradient", "weights")
                            val stem = if (target.endsWith("e")) target.dropLast(1) else target
                            if (token.startsWith(stem) || token.startsWith(target) || (token.length >= 4 && target.startsWith(token))) {
                                isMatched = true
                                break
                            }
                        }
                    }
                }

                if (isMatched) {
                    matched.add(kw)
                }
            }
        }

        return matched
    }
}
