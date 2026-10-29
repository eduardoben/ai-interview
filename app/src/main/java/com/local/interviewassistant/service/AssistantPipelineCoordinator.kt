package com.local.interviewassistant.service

import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.CopilotUiState
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.audio.AudioSource
import com.local.interviewassistant.data.history.QuestionAnswerRecord
import com.local.interviewassistant.data.history.SessionHistoryRepository
import com.local.interviewassistant.data.llm.LlmEngine
import com.local.interviewassistant.data.llm.LlmStreamEvent
import com.local.interviewassistant.data.stt.SherpaVadSttEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistantPipelineCoordinator(
    private val audioSource: AudioSource,
    private val sttEngine: SherpaVadSttEngine,
    private val llmEngine: LlmEngine,
    private val scope: CoroutineScope,
    private val appConfig: com.local.interviewassistant.core.model.AppConfig? = null,
    private val historyRepository: SessionHistoryRepository? = null,
) {
    private val _state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _copilotUiState = MutableStateFlow(CopilotUiState())
    val copilotUiState: StateFlow<CopilotUiState> = _copilotUiState.asStateFlow()

    private var audioJob: Job? = null
    private var partialTranscriptJob: Job? = null
    private var completedQuestionJob: Job? = null
    private var llmGenerationJob: Job? = null
    private var syncJob: Job? = null

    companion object {
        private const val MAX_TRANSCRIPT_HISTORY = 30
    }

    suspend fun start() {
        audioJob?.cancel()
        partialTranscriptJob?.cancel()
        completedQuestionJob?.cancel()
        syncJob?.cancel()

        syncJob = scope.launch {
            _state.collect { s ->
                _copilotUiState.update { it.copy(pipelineState = s) }
            }
        }

        _state.value = AssistantState.Initializing
        _copilotUiState.update { it.copy(pipelineState = AssistantState.Initializing) }

        val sttResult = runCatching {
            withContext(Dispatchers.Default) {
                sttEngine.initialize().getOrThrow()
            }
        }
        val llmResult = runCatching { llmEngine.initialize().getOrThrow() }

        if (sttResult.isFailure || llmResult.isFailure) {
            val sttErrMsg = sttResult.exceptionOrNull()?.message
            val llmErrMsg = llmResult.exceptionOrNull()?.message
            val message = when {
                sttErrMsg != null && llmErrMsg != null -> "STT: $sttErrMsg | LLM: $llmErrMsg"
                sttErrMsg != null -> "STT Error: $sttErrMsg"
                llmErrMsg != null -> "LLM Error: $llmErrMsg"
                else -> "Initialization failed"
            }
            _state.value = AssistantState.Error(message, isRecoverable = false)
            _copilotUiState.update { it.copy(pipelineState = _state.value) }
            return
        }

        _state.value = AssistantState.Listening
        _copilotUiState.update { it.copy(pipelineState = AssistantState.Listening) }

        audioJob = scope.launch {
            try {
                audioSource.audioStream().collect { samples ->
                    if (_state.value !is AssistantState.Paused) {
                        sttEngine.processAudioChunk(samples)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                val errState = AssistantState.Error("Microphone error: ${t.message ?: t.javaClass.simpleName}", isRecoverable = true)
                _state.value = errState
                _copilotUiState.update { it.copy(pipelineState = errState) }
            }
        }

        partialTranscriptJob = scope.launch {
            sttEngine.partialTranscript.collect { partial ->
                _copilotUiState.update { it.copy(livePartialTranscript = partial, isSpeechActive = sttEngine.isSpeechActive.value) }
                if (partial.isNotBlank() &&
                    _state.value !is AssistantState.GeneratingAnswer &&
                    _state.value !is AssistantState.Paused
                ) {
                    _state.value = AssistantState.DetectingSpeech(partial)
                } else if (partial.isBlank() && _state.value is AssistantState.DetectingSpeech) {
                    _state.value = AssistantState.Listening
                }
            }
        }

        completedQuestionJob = scope.launch {
            sttEngine.completedQuestion.collect { segment ->
                _copilotUiState.update {
                    val newHistory = (it.transcriptHistory + segment).takeLast(MAX_TRANSCRIPT_HISTORY)
                    it.copy(transcriptHistory = newHistory, livePartialTranscript = "")
                }
                // Only auto-trigger on voice if we are NOT already in the middle of generating an answer
                // (or if the user explicitly triggers via manual tap / Answer Now).
                // Wait: if the interviewer asks a new question while an old answer is done, _state is AnswerReady or Listening, so it triggers!
                // If _state is currently GeneratingAnswer and a short background noise/utterance endpoints, do not interrupt active generation unless it's a long question (>= 25 chars).
                val canAutoTrigger = _state.value !is AssistantState.Paused &&
                        (_state.value !is AssistantState.GeneratingAnswer || segment.normalizedText.length >= 25)
                if (canAutoTrigger && segment.isLikelyQuestion) {
                    triggerAnswerGeneration(segment.normalizedText)
                }
            }
        }
    }

    fun triggerAnswerGeneration(question: String) {
        if (question.isBlank() || _state.value is AssistantState.Paused) return
        val previousJob = llmGenerationJob
        llmGenerationJob = scope.launch {
            // Wait for any previous generation coroutine to finish cancelling cleanly before starting a new one
            previousJob?.cancel()
            previousJob?.join()

            _state.value = AssistantState.GeneratingAnswer(detectedQuestion = question, streamingAnswer = "", ttftMs = 0L)
            _copilotUiState.update {
                it.copy(
                    pipelineState = _state.value,
                    activeQuestion = question,
                    activeAnswer = "",
                    isGeneratingAnswer = true,
                    lastTtftMs = 0L
                )
            }

            if (!llmEngine.isInitialized.value) {
                val initRes = runCatching { llmEngine.initialize().getOrThrow() }
                if (initRes.isFailure) {
                    val errMsg = initRes.exceptionOrNull()?.message ?: "LLM failed to initialize"
                    _state.value = AssistantState.Error(errMsg, isRecoverable = true)
                    _copilotUiState.update { it.copy(pipelineState = _state.value, isGeneratingAnswer = false) }
                    return@launch
                }
            }

            llmEngine.generateAnswerStream(question).collect { event ->
                when (event) {
                    is LlmStreamEvent.PartialToken -> {
                        val genState = AssistantState.GeneratingAnswer(question, event.accumulatedText, event.ttftMs)
                        _state.value = genState
                        _copilotUiState.update {
                            it.copy(
                                pipelineState = genState,
                                activeAnswer = event.accumulatedText,
                                lastTtftMs = event.ttftMs
                            )
                        }
                    }
                    is LlmStreamEvent.Completed -> {
                        val readyState = AssistantState.AnswerReady(question, event.fullText, event.totalDurationMs)
                        _state.value = readyState
                        _copilotUiState.update {
                            it.copy(
                                pipelineState = readyState,
                                activeAnswer = event.fullText,
                                isGeneratingAnswer = false
                            )
                        }
                        historyRepository?.recordQa(
                            QuestionAnswerRecord(
                                question = question,
                                answer = event.fullText,
                                category = _copilotUiState.value.selectedCategory,
                                ttftMs = event.ttftMs,
                                decodeTps = event.decodeTps,
                                tokenCount = event.tokenCount
                            )
                        )
                    }
                    is LlmStreamEvent.Error -> {
                        val errState = AssistantState.Error(event.throwable.message ?: "LLM generation failed", isRecoverable = true)
                        _state.value = errState
                        _copilotUiState.update {
                            it.copy(
                                pipelineState = errState,
                                isGeneratingAnswer = false
                            )
                        }
                    }
                }
            }
        }
    }

    fun togglePause() {
        if (_state.value is AssistantState.Paused) {
            scope.launch {
                sttEngine.reset()
                _state.value = AssistantState.Listening
                _copilotUiState.update { it.copy(pipelineState = AssistantState.Listening) }
            }
        } else {
            llmGenerationJob?.cancel()
            scope.launch {
                sttEngine.reset()
                _state.value = AssistantState.Paused
                _copilotUiState.update { it.copy(pipelineState = AssistantState.Paused, isGeneratingAnswer = false) }
            }
        }
    }

    fun forceAnswerNow() {
        if (_state.value is AssistantState.Paused) return

        scope.launch {
            val partialBeforeEndpoint = _copilotUiState.value.livePartialTranscript
            val forcedSegment = sttEngine.forceEndpoint()
            val textToAnswer = when {
                forcedSegment != null && forcedSegment.normalizedText.isNotBlank() -> forcedSegment.normalizedText
                partialBeforeEndpoint.isNotBlank() -> partialBeforeEndpoint
                else -> _copilotUiState.value.transcriptHistory.lastOrNull()?.normalizedText ?: ""
            }
            if (textToAnswer.isNotBlank()) {
                triggerAnswerGeneration(textToAnswer)
            }
        }
    }

    fun clearCurrentAnswer() {
        if (_state.value is AssistantState.Paused) return
        llmGenerationJob?.cancel()
        scope.launch {
            sttEngine.reset()
            _state.value = AssistantState.Listening
            _copilotUiState.update {
                it.copy(
                    pipelineState = AssistantState.Listening,
                    activeQuestion = null,
                    activeAnswer = null,
                    isGeneratingAnswer = false,
                    transcriptHistory = emptyList(),
                    livePartialTranscript = ""
                )
            }
        }
    }

    fun selectTranscriptSegmentForAnswer(segment: TranscriptSegment) {
        if (_state.value is AssistantState.Paused) return
        triggerAnswerGeneration(segment.normalizedText)
    }

    fun selectCategory(category: InterviewCategory) {
        _copilotUiState.update { it.copy(selectedCategory = category) }
        val config = appConfig
        if (config != null) {
            val current = config.llmConfig.value
            val isDefaultOrPresetContext = current.interviewContext.isBlank() ||
                    InterviewCategory.entries.any { it.contextSnippet == current.interviewContext }
            config.updateLlmConfig(
                current.copy(
                    promptStyle = category.promptStyle,
                    interviewContext = if (isDefaultOrPresetContext) category.contextSnippet else current.interviewContext
                )
            )
        }
    }

    fun setMinimized(minimized: Boolean) {
        _copilotUiState.update { it.copy(isMinimized = minimized) }
    }

    suspend fun stop() {
        audioJob?.cancel()
        partialTranscriptJob?.cancel()
        completedQuestionJob?.cancel()
        llmGenerationJob?.cancel()
        llmGenerationJob?.join()
        syncJob?.cancel()

        sttEngine.release()
        llmEngine.release()

        _state.value = AssistantState.Idle
        _copilotUiState.update {
            it.copy(
                pipelineState = AssistantState.Idle,
                livePartialTranscript = "",
                isSpeechActive = false,
                isGeneratingAnswer = false
            )
        }
    }
}
