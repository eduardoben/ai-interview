package com.local.interviewassistant.service

import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.CopilotUiState
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.audio.AudioSource
import com.local.interviewassistant.data.llm.LlmEngine
import com.local.interviewassistant.data.llm.LlmStreamEvent
import com.local.interviewassistant.data.stt.SherpaVadSttEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantPipelineCoordinator(
    private val audioSource: AudioSource,
    private val sttEngine: SherpaVadSttEngine,
    private val llmEngine: LlmEngine,
    private val scope: CoroutineScope,
    private val appConfig: com.local.interviewassistant.core.model.AppConfig? = null,
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
        private const val MAX_TRANSCRIPT_HISTORY = 25
    }

    suspend fun start() {
        syncJob?.cancel()
        syncJob = scope.launch {
            _state.collect { s ->
                _copilotUiState.update { it.copy(pipelineState = s) }
            }
        }

        _state.value = AssistantState.Initializing

        val sttResult = runCatching { sttEngine.initialize().getOrThrow() }
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
            return
        }

        _state.value = AssistantState.Listening

        audioJob = scope.launch {
            audioSource.audioStream().collect { samples ->
                if (_state.value !is AssistantState.Paused) {
                    sttEngine.processAudioChunk(samples)
                }
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
                    it.copy(transcriptHistory = newHistory)
                }
                if (_state.value !is AssistantState.Paused) {
                    if (segment.isLikelyQuestion) {
                        triggerAnswerGeneration(segment.normalizedText)
                    }
                }
            }
        }
    }

    private fun triggerAnswerGeneration(question: String) {
        llmGenerationJob?.cancel()
        llmGenerationJob = scope.launch {
            _state.value = AssistantState.GeneratingAnswer(detectedQuestion = question, streamingAnswer = "", ttftMs = 0L)
            _copilotUiState.update { 
                it.copy(activeQuestion = question, activeAnswer = "", isGeneratingAnswer = true, lastTtftMs = 0L) 
            }
            
            llmEngine.generateAnswerStream(question).collect { event ->
                when (event) {
                    is LlmStreamEvent.PartialToken -> {
                        _state.value = AssistantState.GeneratingAnswer(question, event.accumulatedText, event.ttftMs)
                        _copilotUiState.update { 
                            it.copy(activeAnswer = event.accumulatedText, lastTtftMs = event.ttftMs) 
                        }
                    }
                    is LlmStreamEvent.Completed -> {
                        _state.value = AssistantState.AnswerReady(question, event.fullText, event.totalDurationMs)
                        _copilotUiState.update { 
                            it.copy(activeAnswer = event.fullText, isGeneratingAnswer = false) 
                        }
                    }
                    is LlmStreamEvent.Error -> {
                        _state.value = AssistantState.Error(event.throwable.message ?: "LLM generation failed", isRecoverable = true)
                        _copilotUiState.update { 
                            it.copy(isGeneratingAnswer = false) 
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
            }
        } else {
            llmGenerationJob?.cancel()
            scope.launch {
                sttEngine.reset()
                _state.value = AssistantState.Paused
            }
        }
    }

    fun forceAnswerNow() {
        if (_state.value is AssistantState.Paused) return
        
        scope.launch {
            sttEngine.forceEndpoint()
            val partial = _copilotUiState.value.livePartialTranscript
            val textToAnswer = if (partial.isNotBlank()) {
                partial
            } else {
                _copilotUiState.value.transcriptHistory.lastOrNull()?.normalizedText ?: ""
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
                it.copy(activeQuestion = null, activeAnswer = null, transcriptHistory = emptyList())
            }
        }
    }
    
    fun selectTranscriptSegmentForAnswer(segment: TranscriptSegment) {
        if (_state.value is AssistantState.Paused) return
        triggerAnswerGeneration(segment.normalizedText)
    }

    fun selectCategory(category: com.local.interviewassistant.core.model.InterviewCategory) {
        _copilotUiState.update { it.copy(selectedCategory = category) }
        val config = appConfig
        if (config != null) {
            val current = config.llmConfig.value
            config.updateLlmConfig(
                current.copy(
                    promptStyle = category.promptStyle,
                    interviewContext = category.contextSnippet
                )
            )
            // Trigger a clean restart to apply the new system instruction and release TPU buffers
            scope.launch {
                llmEngine.release()
                llmEngine.initialize()
            }
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
        syncJob?.cancel()
        
        sttEngine.release()
        llmEngine.release()
        
        _state.value = AssistantState.Idle
    }
}
