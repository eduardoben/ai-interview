package com.local.interviewassistant.ui.prompter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.interviewassistant.core.model.CopilotUiState
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.preferences.UserPreferencesRepository
import com.local.interviewassistant.service.AssistantPipelineCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrompterUiState(
    val copilotState: CopilotUiState = CopilotUiState(),
    val isServiceRunning: Boolean = false,
    val isFocusMode: Boolean = false,
    val fontSizeSp: Float = 20f,
    val stealthDimmerAlpha: Float = 0f,
    val hardwareSocName: String? = null,
    val showTranscriptSheet: Boolean = false,
    val customQuestionInput: String = "",
    val showDimmerDialog: Boolean = false,
    val autoFollowStream: Boolean = true
)

/**
 * MVVM ViewModel managing state and user interactions for the Teleprompter screen.
 */
class PrompterViewModel(
    private val coordinator: AssistantPipelineCoordinator,
    private val preferencesRepository: UserPreferencesRepository? = null,
    private val isServiceRunningFlow: StateFlow<Boolean>? = null,
    hardwareSocName: String? = null,
    private val onStartServiceCallback: (() -> Unit)? = null,
    private val onStopServiceCallback: (() -> Unit)? = null,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope
        get() = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        PrompterUiState(
            copilotState = coordinator.copilotUiState.value,
            isServiceRunning = isServiceRunningFlow?.value ?: false,
            hardwareSocName = hardwareSocName
        )
    )
    val uiState: StateFlow<PrompterUiState> = _uiState.asStateFlow()

    init {
        // Collect live pipeline UI state
        scope.launch {
            coordinator.copilotUiState.collect { copilotState ->
                _uiState.update { current ->
                    current.copy(copilotState = copilotState)
                }
            }
        }

        // Collect foreground service running state
        if (isServiceRunningFlow != null) {
            scope.launch {
                isServiceRunningFlow.collect { running ->
                    _uiState.update { it.copy(isServiceRunning = running) }
                }
            }
        }

        // Collect preferences changes (font size, dimmer)
        if (preferencesRepository != null) {
            scope.launch {
                preferencesRepository.userPreferencesFlow.collect { prefs ->
                    _uiState.update { current ->
                        current.copy(
                            fontSizeSp = prefs.teleprompterFontSizeSp,
                            stealthDimmerAlpha = prefs.stealthDimmerAlpha
                        )
                    }
                }
            }
        }
    }

    fun onStartService() {
        onStartServiceCallback?.invoke()
    }

    fun onStopService() {
        onStopServiceCallback?.invoke()
    }

    fun onPauseToggle() {
        coordinator.togglePause()
    }

    fun onForceAnswer() {
        coordinator.forceAnswerNow()
    }

    fun onClearAnswer() {
        coordinator.clearCurrentAnswer()
    }

    fun onSelectCategory(category: InterviewCategory) {
        coordinator.selectCategory(category)
    }

    fun onSelectTranscriptSegment(segment: TranscriptSegment) {
        coordinator.selectTranscriptSegmentForAnswer(segment)
        _uiState.update { it.copy(showTranscriptSheet = false) }
    }

    fun onSubmitCustomQuestion(question: String) {
        if (question.isNotBlank()) {
            coordinator.triggerAnswerGeneration(question.trim())
            _uiState.update {
                it.copy(
                    customQuestionInput = "",
                    showTranscriptSheet = false
                )
            }
        }
    }

    fun onUpdateCustomQuestionInput(input: String) {
        _uiState.update { it.copy(customQuestionInput = input) }
    }

    fun onToggleFocusMode() {
        _uiState.update { it.copy(isFocusMode = !it.isFocusMode) }
    }

    fun onToggleTranscriptSheet(show: Boolean) {
        _uiState.update { it.copy(showTranscriptSheet = show) }
    }

    fun onToggleDimmerDialog(show: Boolean) {
        _uiState.update { it.copy(showDimmerDialog = show) }
    }

    fun onDimmerAlphaChange(alpha: Float) {
        _uiState.update { it.copy(stealthDimmerAlpha = alpha) }
        preferencesRepository?.let { repo ->
            scope.launch {
                repo.updateStealthDimmerAlpha(alpha)
            }
        }
    }

    fun setAutoFollowStream(enabled: Boolean) {
        _uiState.update { it.copy(autoFollowStream = enabled) }
    }
}
