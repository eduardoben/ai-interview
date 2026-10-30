package com.local.interviewassistant.core.model

sealed interface AssistantState {
    data object Idle : AssistantState
    data object Initializing : AssistantState
    data object Listening : AssistantState
    data class DetectingSpeech(val partialTranscript: String) : AssistantState
    data class GeneratingAnswer(val detectedQuestion: String, val streamingAnswer: String, val ttftMs: Long = 0L) : AssistantState
    data class AnswerReady(val detectedQuestion: String, val finalAnswer: String, val totalGenerationMs: Long = 0L) : AssistantState
    data object Paused : AssistantState
    data class Error(val message: String, val isRecoverable: Boolean = true) : AssistantState

    @Suppress("unused")
    val isProcessing: Boolean
        get() = (this is Initializing) || (this is GeneratingAnswer)

    @Suppress("unused")
    val activeQuestionOrNull: String?
        get() = when (this) {
            is GeneratingAnswer -> detectedQuestion
            is AnswerReady -> detectedQuestion
            else -> null
        }

    @Suppress("unused")
    val activeAnswerOrNull: String?
        get() = when (this) {
            is GeneratingAnswer -> streamingAnswer
            is AnswerReady -> finalAnswer
            else -> null
        }
}
