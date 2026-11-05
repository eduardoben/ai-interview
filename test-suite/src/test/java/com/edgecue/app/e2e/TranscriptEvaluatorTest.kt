package com.edgecue.app.e2e

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification suite for [TranscriptEvaluator].
 *
 * Verifies:
 * 1. Exact match WER (0.0f) and S/D/I decomposition.
 * 2. Single insertion, deletion, and substitution Levenshtein accuracy.
 * 3. Compound-aware hotword matching with single and multi-word phrases.
 * 4. Split compound reconciliation ("state flow" vs "stateflow").
 * 5. Contraction expansion and punctuation normalization matching QuestionPunctuationEngine.
 * 6. Edge cases: empty strings, case differences, multiple spaces, short word safety ("star" vs "start").
 * 7. LLM keyword relevance evaluation.
 */
class TranscriptEvaluatorTest {

    // --- 1. Normalization Tests ---

    @Test
    fun testNormalizeCasingAndWhitespace() {
        val input = "  Hello   WORLD   this   is   A   TEST  "
        val expected = "hello world this is a test"
        assertEquals(expected, TranscriptEvaluator.normalize(input))
    }

    @Test
    fun testNormalizePunctuationStripping() {
        val input = "Can you describe a situation, where you had a conflict? (Yes, indeed!)"
        val expected = "can you describe a situation where you had a conflict yes indeed"
        assertEquals(expected, TranscriptEvaluator.normalize(input))
    }

    @Test
    fun testNormalizeContractionExpansion() {
        val input = "What's the plan? I can't do it, don't ask, won't happen, gonna do it, wanna try."
        val normalized = TranscriptEvaluator.normalize(input)
        assertTrue("Normalized text must expand what's", normalized.contains("what is"))
        assertTrue("Normalized text must expand can't", normalized.contains("cannot"))
        assertTrue("Normalized text must expand don't", normalized.contains("do not"))
        assertTrue("Normalized text must expand won't", normalized.contains("will not"))
        assertTrue("Normalized text must expand gonna", normalized.contains("going to"))
        assertTrue("Normalized text must expand wanna", normalized.contains("want to"))
        assertFalse("Normalized text must not contain apostrophes", normalized.contains("'"))
    }

    @Test
    fun testNormalizeEmptyAndBlank() {
        assertEquals("", TranscriptEvaluator.normalize(""))
        assertEquals("", TranscriptEvaluator.normalize("   \t\n  "))
    }

    // --- 2. Word Error Rate (WER) Tests ---

    @Test
    fun testComputeWerExactMatch() {
        val ref = "can you describe a situation where you had a conflict"
        val hyp = "Can you describe a situation where you had a conflict?"

        val result = TranscriptEvaluator.computeWer(ref, hyp)
        assertEquals(0.0f, result.wer, 0.0001f)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(10, result.referenceWords)
        assertEquals(10, result.hypothesisWords)
    }

    @Test
    fun testComputeWerSingleSubstitution() {
        val ref = "can you explain transformer architecture"
        val hyp = "can you explain transformer model" // architecture -> model (S = 1)

        val result = TranscriptEvaluator.computeWer(ref, hyp)
        assertEquals(1, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(5, result.referenceWords)
        assertEquals(5, result.hypothesisWords)
        assertEquals(0.20f, result.wer, 0.0001f)
    }

    @Test
    fun testComputeWerSingleDeletion() {
        val ref = "can you explain how a transformer works"
        val hyp = "can you explain a transformer works" // "how" deleted (D = 1)

        val result = TranscriptEvaluator.computeWer(ref, hyp)
        assertEquals(0, result.substitutions)
        assertEquals(1, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(7, result.referenceWords)
        assertEquals(6, result.hypothesisWords)
        assertEquals(1.0f / 7.0f, result.wer, 0.0001f)
    }

    @Test
    fun testComputeWerSingleInsertion() {
        val ref = "can you explain transformer works"
        val hyp = "can you please explain transformer works" // "please" inserted (I = 1)

        val result = TranscriptEvaluator.computeWer(ref, hyp)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(1, result.insertions)
        assertEquals(5, result.referenceWords)
        assertEquals(6, result.hypothesisWords)
        assertEquals(0.20f, result.wer, 0.0001f)
    }

    @Test
    fun testComputeWerComplexAlignment() {
        val ref = "the quick brown fox jumps over the lazy dog"
        val hyp = "a quick brown fox leaped over lazy brown dog"
        // ref: 9 words
        // "the" -> "a" (S)
        // "jumps" -> "leaped" (S)
        // "the" (deleted) (D)
        // "brown" (inserted before dog) (I)
        val result = TranscriptEvaluator.computeWer(ref, hyp)
        assertEquals(9, result.referenceWords)
        assertTrue("WER should be accurately bounded", result.wer in 0.3f..0.6f)
        assertEquals(result.substitutions + result.deletions + result.insertions, (result.wer * 9).toInt())
    }

    @Test
    fun testComputeWerEdgeCasesEmptyStrings() {
        // Both empty
        val bothEmpty = TranscriptEvaluator.computeWer("", "")
        assertEquals(0.0f, bothEmpty.wer, 0.0001f)
        assertEquals(0, bothEmpty.referenceWords)

        // Empty reference, non-empty hypothesis
        val emptyRef = TranscriptEvaluator.computeWer("", "hello world")
        assertEquals(1.0f, emptyRef.wer, 0.0001f)
        assertEquals(0, emptyRef.referenceWords)
        assertEquals(2, emptyRef.hypothesisWords)

        // Non-empty reference, empty hypothesis
        val emptyHyp = TranscriptEvaluator.computeWer("hello world", "")
        assertEquals(1.0f, emptyHyp.wer, 0.0001f)
        assertEquals(2, emptyHyp.referenceWords)
        assertEquals(0, emptyHyp.hypothesisWords)
        assertEquals(2, emptyHyp.deletions)
    }

    // --- 3. Compound-Aware Hotwords Recall Tests ---

    @Test
    fun testEvaluateHotwordsExactTokens() {
        val hyp = "We resolved the conflict with our stakeholder through clear prioritization."
        val hotwords = listOf("conflict", "stakeholder", "prioritization", "resolution")

        val result = TranscriptEvaluator.evaluateHotwords(hyp, hotwords)
        // Note: "resolution" is not in hyp directly (hyp has "resolved"), so 3 match, 1 missing
        assertEquals(4, result.totalRequired)
        assertEquals(3, result.matchedCount)
        assertEquals(0.75f, result.recall, 0.0001f)
        assertTrue(result.matchedHotwords.contains("conflict"))
        assertTrue(result.matchedHotwords.contains("stakeholder"))
        assertTrue(result.matchedHotwords.contains("prioritization"))
        assertTrue(result.missingHotwords.contains("resolution"))
    }

    @Test
    fun testEvaluateHotwordsCompoundSplit() {
        // Single-word hotword split across two tokens in hypothesis
        val hyp = "In Android we use state flow and shared flow alongside view model architecture."
        val hotwords = listOf("stateflow", "sharedflow", "viewmodel")

        val result = TranscriptEvaluator.evaluateHotwords(hyp, hotwords)
        assertEquals(3, result.totalRequired)
        assertEquals(3, result.matchedCount)
        assertEquals(1.0f, result.recall, 0.0001f)
        assertTrue(result.missingHotwords.isEmpty())
    }

    @Test
    fun testEvaluateHotwordsMultiWordPhrase() {
        val hyp = "Can you explain how clean architecture is implemented in python algorithms?"
        val hotwords = listOf("clean architecture", "can you explain", "python", "algorithms")

        val result = TranscriptEvaluator.evaluateHotwords(hyp, hotwords)
        assertEquals(4, result.totalRequired)
        assertEquals(4, result.matchedCount)
        assertEquals(1.0f, result.recall, 0.0001f)
    }

    @Test
    fun testEvaluateHotwordsMultiWordJoinedInHypothesis() {
        // Multi-word hotword concatenated into single token in hypothesis
        val hyp = "We implement cleanarchitecture and restapi endpoints."
        val hotwords = listOf("clean architecture", "rest api")

        val result = TranscriptEvaluator.evaluateHotwords(hyp, hotwords)
        assertEquals(2, result.totalRequired)
        assertEquals(2, result.matchedCount)
        assertEquals(1.0f, result.recall, 0.0001f)
    }

    @Test
    fun testEvaluateHotwordsShortWordFalsePositiveImmunity() {
        // Short hotword "star" must not match inside "started", "restart", or "starlight"
        val hyp = "I started my work and decided to restart the computer before writing."
        val hotwords = listOf("star")

        val result = TranscriptEvaluator.evaluateHotwords(hyp, hotwords)
        assertEquals(1, result.totalRequired)
        assertEquals(0, result.matchedCount)
        assertEquals(0.0f, result.recall, 0.0001f)
        assertTrue(result.missingHotwords.contains("star"))
    }

    @Test
    fun testEvaluateHotwordsEmptyList() {
        val result = TranscriptEvaluator.evaluateHotwords("any hypothesis", emptyList())
        assertEquals(0, result.totalRequired)
        assertEquals(0, result.matchedCount)
        assertEquals(1.0f, result.recall, 0.0001f)
    }

    // --- 4. Keyword Relevance Tests ---

    @Test
    fun testEvaluateKeywordsExactAndMorphology() {
        val answer = "I utilized the STAR method to listen to stakeholders, reached a compromise, and avoided escalating the conflict."
        val expectedKeywords = listOf("star", "listen", "compromise", "escalate", "communication")

        val matched = TranscriptEvaluator.evaluateKeywords(answer, expectedKeywords)
        assertTrue("star must match", matched.contains("star"))
        assertTrue("listen must match", matched.contains("listen"))
        assertTrue("compromise must match", matched.contains("compromise"))
        assertTrue("escalate must match escalating", matched.contains("escalate"))
        assertFalse("communication is absent", matched.contains("communication"))
        assertEquals(4, matched.size)
    }

    @Test
    fun testEvaluateKeywordsShortWordSafety() {
        val answer = "The process started quickly."
        val expectedKeywords = listOf("star")

        val matched = TranscriptEvaluator.evaluateKeywords(answer, expectedKeywords)
        assertFalse("star must not match started", matched.contains("star"))
        assertEquals(0, matched.size)
    }

    @Test
    fun testEvaluateKeywordsMultiWord() {
        val answer = "We applied clean architecture and gradient descent optimization."
        val expectedKeywords = listOf("clean architecture", "gradient descent", "adam")

        val matched = TranscriptEvaluator.evaluateKeywords(answer, expectedKeywords)
        assertTrue(matched.contains("clean architecture"))
        assertTrue(matched.contains("gradient descent"))
        assertFalse(matched.contains("adam"))
        assertEquals(2, matched.size)
    }
}
