package com.local.interviewassistant.core.model

enum class InterviewCategory(
    val displayName: String,
    val promptStyle: PromptStyle,
    val contextSnippet: String
) {
    GENERAL("General", PromptStyle.CONCISE, "General Candidate Assistance"),
    SYSTEM_DESIGN("System Design", PromptStyle.VERBOSE, "System Design & Software Architecture"),
    ALGORITHMS("Algorithms", PromptStyle.CONCISE, "Algorithms, Data Structures & Coding"),
    BEHAVIORAL("Behavioral (STAR)", PromptStyle.CONCISE, "Behavioral Interview (Situation, Task, Action, Result)"),
    QA_REVIEW("QA / Code Review", PromptStyle.CONCISE, "QA Engineering & Code Review")
}

data class CopilotUiState(
    val pipelineState: AssistantState = AssistantState.Idle,
    val livePartialTranscript: String = "",
    val isSpeechActive: Boolean = false,
    val transcriptHistory: List<TranscriptSegment> = emptyList(),
    val activeQuestion: String? = null,
    val activeAnswer: String? = null,
    val isGeneratingAnswer: Boolean = false,
    val lastTtftMs: Long = 0L,
    val isMinimized: Boolean = false,
    val selectedCategory: InterviewCategory = InterviewCategory.GENERAL
)