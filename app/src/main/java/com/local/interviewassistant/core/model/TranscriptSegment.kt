package com.local.interviewassistant.core.model

import java.util.UUID

data class TranscriptSegment(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isFinal: Boolean,
) {
    val normalizedText: String
        get() = text.trim().replace(Regex("\\s+"), " ")

    val isActionableQuestion: Boolean
        get() = isFinal && (normalizedText.length >= 12)

    val isLikelyQuestion: Boolean
        get() {
            if (!isActionableQuestion) return false
            if (normalizedText.contains("?")) return true

            val lowerText = normalizedText.lowercase()
            val questionStarters = listOf(
                "what", "how", "why", "when", "where", "which", "who", "whom", "whose",
                "can ", "could ", "would ", "should ", "is ", "are ", "does ", "do ", "did ", "has ", "have ",
                "explain", "describe", "tell", "walk", "design", "implement", "compare", "difference",
                "define", "discuss", "give", "brief",
            )

            if (questionStarters.any { lowerText.startsWith(it) || lowerText.contains(" $it") }) return true
            if (lowerText.contains("what's") || lowerText.contains("how's") || lowerText.contains("difference between")) return true
            return normalizedText.length >= 24
        }
}
