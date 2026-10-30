package com.local.interviewassistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.interviewassistant.core.model.LlmGenerationConfig
import com.local.interviewassistant.core.model.PromptStyle
import com.local.interviewassistant.data.hardware.DelegateType
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val config: LlmGenerationConfig = LlmGenerationConfig(),
    val hardwareProfile: HardwareProfile? = null
)

/**
 * MVVM ViewModel managing configuration, model delegates, and speech parameters for the Settings screen.
 */
class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    initialHardwareProfile: HardwareProfile? = null,
    initialConfig: LlmGenerationConfig = LlmGenerationConfig()
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
}

