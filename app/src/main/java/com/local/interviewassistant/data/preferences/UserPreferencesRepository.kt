package com.local.interviewassistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.local.interviewassistant.core.model.LlmGenerationConfig
import com.local.interviewassistant.core.model.PromptStyle
import com.local.interviewassistant.data.hardware.DelegateType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "llm_prefs"))
    }
)

/**
 * Asynchronous, transaction-safe repository for user configuration and LLM settings
 * backed by Android Jetpack DataStore<Preferences>, replacing legacy SharedPreferences.
 */
class UserPreferencesRepository(
    private val context: Context? = null,
    private val externalDataStore: DataStore<Preferences>? = null
) {
    private val dataStore: DataStore<Preferences>
        get() = externalDataStore ?: context?.dataStore ?: error("No DataStore or Context provided")

    private object PreferencesKeys {
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("topK")
        val MAX_TOKENS = intPreferencesKey("maxTokens")
        val PROMPT_STYLE = stringPreferencesKey("promptStyle")
        val INTERVIEW_CONTEXT = stringPreferencesKey("interviewContext")
        val SELECTED_MODEL_PATH = stringPreferencesKey("selectedModelPath")
        val PREFERRED_DELEGATE = stringPreferencesKey("preferredDelegate")
        val ENABLE_WHISPER_RESCORER = booleanPreferencesKey("enableWhisperRescorer")
        val TELEPROMPTER_FONT_SIZE_SP = floatPreferencesKey("teleprompterFontSizeSp")
        val STEALTH_DIMMER_ALPHA = floatPreferencesKey("stealthDimmerAlpha")
        val ENABLE_VOLUME_KEY_SHORTCUTS = booleanPreferencesKey("enableVolumeKeyShortcuts")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keepScreenOn")
        val MIN_SILENCE_DURATION_SEC = floatPreferencesKey("minSilenceDurationSec")
        val ENABLE_HARDWARE_AUDIO_EFFECTS = booleanPreferencesKey("enableHardwareAudioEffects")
    }

    val userPreferencesFlow: Flow<LlmGenerationConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            mapUserPreferences(preferences)
        }

    suspend fun saveConfig(newConfig: LlmGenerationConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEMPERATURE] = newConfig.temperature
            preferences[PreferencesKeys.TOP_K] = newConfig.topK
            preferences[PreferencesKeys.MAX_TOKENS] = newConfig.maxTokens
            preferences[PreferencesKeys.PROMPT_STYLE] = newConfig.promptStyle.name
            preferences[PreferencesKeys.INTERVIEW_CONTEXT] = newConfig.interviewContext
            preferences[PreferencesKeys.SELECTED_MODEL_PATH] = newConfig.selectedModelPath
            if (newConfig.preferredDelegate != null) {
                preferences[PreferencesKeys.PREFERRED_DELEGATE] = newConfig.preferredDelegate.name
            } else {
                preferences.remove(PreferencesKeys.PREFERRED_DELEGATE)
            }
            preferences[PreferencesKeys.ENABLE_WHISPER_RESCORER] = newConfig.enableWhisperRescorer
            preferences[PreferencesKeys.TELEPROMPTER_FONT_SIZE_SP] = newConfig.teleprompterFontSizeSp
            preferences[PreferencesKeys.STEALTH_DIMMER_ALPHA] = newConfig.stealthDimmerAlpha
            preferences[PreferencesKeys.ENABLE_VOLUME_KEY_SHORTCUTS] = newConfig.enableVolumeKeyShortcuts
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = newConfig.keepScreenOn
            preferences[PreferencesKeys.MIN_SILENCE_DURATION_SEC] = newConfig.minSilenceDurationSec
            preferences[PreferencesKeys.ENABLE_HARDWARE_AUDIO_EFFECTS] = newConfig.enableHardwareAudioEffects
        }
    }

    suspend fun updateTeleprompterFontSize(fontSizeSp: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TELEPROMPTER_FONT_SIZE_SP] = fontSizeSp
        }
    }

    suspend fun updateStealthDimmerAlpha(alpha: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STEALTH_DIMMER_ALPHA] = alpha
        }
    }

    private fun mapUserPreferences(preferences: Preferences): LlmGenerationConfig {
        val temp = preferences[PreferencesKeys.TEMPERATURE] ?: 0.2f
        val topK = preferences[PreferencesKeys.TOP_K] ?: 20
        val maxTokens = preferences[PreferencesKeys.MAX_TOKENS] ?: 200
        val promptStyleStr = preferences[PreferencesKeys.PROMPT_STYLE] ?: PromptStyle.CONCISE.name
        val style = try {
            PromptStyle.valueOf(promptStyleStr)
        } catch (_: Exception) {
            PromptStyle.CONCISE
        }
        val interviewContext = preferences[PreferencesKeys.INTERVIEW_CONTEXT] ?: ""
        val selectedModelPath = preferences[PreferencesKeys.SELECTED_MODEL_PATH] ?: ""
        val preferredDelegateStr = preferences[PreferencesKeys.PREFERRED_DELEGATE]
        val preferredDelegate = preferredDelegateStr?.let {
            try {
                DelegateType.valueOf(it)
            } catch (_: Exception) {
                null
            }
        }
        val enableWhisperRescorer = preferences[PreferencesKeys.ENABLE_WHISPER_RESCORER] ?: true
        val teleprompterFontSizeSp = preferences[PreferencesKeys.TELEPROMPTER_FONT_SIZE_SP] ?: 20f
        val stealthDimmerAlpha = preferences[PreferencesKeys.STEALTH_DIMMER_ALPHA] ?: 0f
        val enableVolumeKeyShortcuts = preferences[PreferencesKeys.ENABLE_VOLUME_KEY_SHORTCUTS] ?: true
        val keepScreenOn = preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: true
        val minSilenceDurationSec = preferences[PreferencesKeys.MIN_SILENCE_DURATION_SEC] ?: 1.2f
        val enableHardwareAudioEffects = preferences[PreferencesKeys.ENABLE_HARDWARE_AUDIO_EFFECTS] ?: false

        return LlmGenerationConfig(
            temperature = temp,
            topK = topK,
            maxTokens = maxTokens,
            promptStyle = style,
            interviewContext = interviewContext,
            selectedModelPath = selectedModelPath,
            preferredDelegate = preferredDelegate,
            enableWhisperRescorer = enableWhisperRescorer,
            teleprompterFontSizeSp = teleprompterFontSizeSp,
            stealthDimmerAlpha = stealthDimmerAlpha,
            enableVolumeKeyShortcuts = enableVolumeKeyShortcuts,
            keepScreenOn = keepScreenOn,
            minSilenceDurationSec = minSilenceDurationSec,
            enableHardwareAudioEffects = enableHardwareAudioEffects
        )
    }
}
