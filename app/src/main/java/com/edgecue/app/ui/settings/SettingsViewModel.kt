package com.edgecue.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edgecue.app.core.model.LlmGenerationConfig
import com.edgecue.app.core.model.PromptStyle
import com.edgecue.app.data.hardware.DelegateType
import com.edgecue.app.data.hardware.HardwareProfile
import com.edgecue.app.data.preferences.UserPreferencesRepository
import com.edgecue.app.data.rag.RagRepository
import com.edgecue.app.data.rag.RagStatus
import com.edgecue.app.data.rag.db.RagSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val config: LlmGenerationConfig = LlmGenerationConfig(),
    val hardwareProfile: HardwareProfile? = null,
    val ragStatus: RagStatus = RagStatus()
)

/**
 * MVVM ViewModel managing configuration, model delegates, speech parameters,
 * and Lite RAG document uploads/status for the Settings screen.
 */
class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    initialHardwareProfile: HardwareProfile? = null,
    initialConfig: LlmGenerationConfig = LlmGenerationConfig(),
    private val ragRepository: RagRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            config = initialConfig,
            hardwareProfile = initialHardwareProfile
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { config ->
                _uiState.update { it.copy(config = config) }
            }
        }
        ragRepository?.let { repo ->
            viewModelScope.launch {
                repo.ragStatusFlow.collect { status ->
                    _uiState.update { it.copy(ragStatus = status) }
                }
            }
        }
    }

    fun setHardwareProfile(profile: HardwareProfile?) {
        _uiState.update { it.copy(hardwareProfile = profile) }
    }

    fun updateConfig(newConfig: LlmGenerationConfig) {
        _uiState.update { it.copy(config = newConfig) }
        viewModelScope.launch {
            preferencesRepository.saveConfig(newConfig)
        }
    }

    fun updateTemperature(temperature: Float) {
        updateConfig(_uiState.value.config.copy(temperature = temperature))
    }

    fun updateMaxTokens(maxTokens: Int) {
        updateConfig(_uiState.value.config.copy(maxTokens = maxTokens))
    }

    fun updatePromptStyle(style: PromptStyle) {
        updateConfig(_uiState.value.config.copy(promptStyle = style))
    }

    fun updatePreferredDelegate(delegate: DelegateType?) {
        updateConfig(_uiState.value.config.copy(preferredDelegate = delegate))
    }

    fun updateWhisperRescorer(enabled: Boolean) {
        updateConfig(_uiState.value.config.copy(enableWhisperRescorer = enabled))
    }

    fun updateHardwareAudioEffects(enabled: Boolean) {
        updateConfig(_uiState.value.config.copy(enableHardwareAudioEffects = enabled))
    }

    fun updateVolumeKeyShortcuts(enabled: Boolean) {
        updateConfig(_uiState.value.config.copy(enableVolumeKeyShortcuts = enabled))
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        updateConfig(_uiState.value.config.copy(keepScreenOn = enabled))
    }

    fun updateInterviewContext(context: String) {
        updateConfig(_uiState.value.config.copy(interviewContext = context))
    }

    // Lite RAG Document Management
    fun importDocument(uri: Uri, sourceType: RagSourceType) {
        val repo = ragRepository ?: return
        viewModelScope.launch {
            repo.importDocument(uri, sourceType)
        }
    }

    fun pasteDocument(text: String, sourceType: RagSourceType, label: String = "Direct Text Input") {
        val repo = ragRepository ?: return
        viewModelScope.launch {
            repo.pasteDocument(text, sourceType, label)
        }
    }

    fun clearDocument(sourceType: RagSourceType) {
        val repo = ragRepository ?: return
        viewModelScope.launch {
            repo.clearDocument(sourceType)
        }
    }
}

