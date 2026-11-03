package com.local.interviewassistant.data.history

/**
 * Standard classification reasons for user-flagged AI-generated responses (Requirement R2, P0-03).
 * Aligns with Google Play Generative AI Apps Policy (Help Article 14290099).
 */
enum class AiReportReason(
    val title: String,
    val description: String
) {
    FACTUAL_INACCURACY(
        "Factual Inaccuracy or Hallucination",
        "Incorrect technical facts, flawed syntax, or fabricated information"
    ),
    INAPPROPRIATE_OFFENSIVE(
        "Inappropriate or Offensive Content",
        "Disrespectful, vulgar, biased, or discriminatory phrasing"
    ),
    HARMFUL_UNSAFE(
        "Harmful or Unsafe Advice",
        "Potentially harmful guidance, security vulnerabilities, or hazardous commands"
    ),
    OTHER(
        "Other Quality or Safety Issue",
        "Repetitive, off-topic, incoherent, or formatting defect"
    )
}
