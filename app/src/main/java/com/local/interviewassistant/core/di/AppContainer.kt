package com.local.interviewassistant.core.di

import android.annotation.SuppressLint
import android.content.Context
import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.LlmGenerationConfig
import com.local.interviewassistant.core.model.PromptStyle
import com.local.interviewassistant.data.audio.AudioSource
import com.local.interviewassistant.data.audio.MicrophoneAudioCaptureSource
import com.local.interviewassistant.data.history.SessionHistoryRepository
import com.local.interviewassistant.data.history.db.AppDatabase
import com.local.interviewassistant.data.llm.LiteRtGemmaEngine
import com.local.interviewassistant.data.preferences.UserPreferencesRepository
import com.local.interviewassistant.data.provisioning.ModelProvisioningManager
import com.local.interviewassistant.data.stt.SherpaOfflineWhisperRescorer
import com.local.interviewassistant.data.stt.SherpaOnnxRuntimeBackend
import com.local.interviewassistant.data.stt.SherpaVadSttEngine
import com.local.interviewassistant.service.AssistantPipelineCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class AppContainer private constructor(private val context: Context) {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val modelsRootDir: File = (context.getExternalFilesDir(null) ?: context.filesDir).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    val appConfig: AppConfig = AppConfig(modelsRootDir)

    // Milestone 3.2: Room Database instance
    val appDatabase: AppDatabase = AppDatabase.getInstance(context)

    // Milestone 3.3: Asynchronous Jetpack DataStore preferences repository
    val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository(context)

    // Milestone 3.2: Room-backed session history repository with auto-migration
    val sessionHistoryRepository: SessionHistoryRepository = SessionHistoryRepository(
        dao = appDatabase.interviewSessionDao(),
        legacyStorageDir = context.filesDir,
        scope = appScope
    )

    init {
        // Fast-path initial configuration bootstrap from DataStore / SharedPreferences
        val prefs = context.getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)
        val temp = prefs.getFloat("temperature", 0.2f)
        val topK = prefs.getInt("topK", 20)
        val maxTokens = prefs.getInt("maxTokens", 200)
        val promptStyleStr = prefs.getString("promptStyle", PromptStyle.CONCISE.name) ?: PromptStyle.CONCISE.name
        val style = try { PromptStyle.valueOf(promptStyleStr) } catch (_: Exception) { PromptStyle.CONCISE }
        val contextStr = prefs.getString("interviewContext", "") ?: ""
        val selectedModelPath = prefs.getString("selectedModelPath", "") ?: ""
        val preferredDelegateStr = prefs.getString("preferredDelegate", null)
        val preferredDelegate = preferredDelegateStr?.let {
            try { com.local.interviewassistant.data.hardware.DelegateType.valueOf(it) } catch (_: Exception) { null }
        }
        val enableWhisperRescorer = prefs.getBoolean("enableWhisperRescorer", true)
        val teleprompterFontSizeSp = prefs.getFloat("teleprompterFontSizeSp", 20f)
        val stealthDimmerAlpha = prefs.getFloat("stealthDimmerAlpha", 0f)
        val enableVolumeKeyShortcuts = prefs.getBoolean("enableVolumeKeyShortcuts", true)
        val keepScreenOn = prefs.getBoolean("keepScreenOn", true)
        val minSilenceDurationSec = prefs.getFloat("minSilenceDurationSec", 1.2f)
        val enableHardwareAudioEffects = prefs.getBoolean("enableHardwareAudioEffects", false)

        appConfig.updateLlmConfig(
            LlmGenerationConfig(
                temperature = temp,
                topK = topK,
                maxTokens = maxTokens,
                promptStyle = style,
                interviewContext = contextStr,
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
        )

        // Reactive stream subscription to DataStore preferences updates
        appScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { config ->
                appConfig.updateLlmConfig(config)
            }
        }
    }

    fun saveLlmConfig(newConfig: LlmGenerationConfig) {
        appConfig.updateLlmConfig(newConfig)
        appScope.launch {
            userPreferencesRepository.saveConfig(newConfig)
        }
    }

    val provisioningManager: ModelProvisioningManager = ModelProvisioningManager(appConfig)

    val isServiceRunning = MutableStateFlow(false)
    val isPrompterScreenVisible = MutableStateFlow(false)

    val pipelineCoordinator: AssistantPipelineCoordinator by lazy {
        AssistantPipelineCoordinator(
            audioSource = createAudioSource(),
            sttEngine = createSttEngine(),
            llmEngine = createLlmEngine(),
            scope = appScope,
            appConfig = appConfig,
            historyRepository = sessionHistoryRepository
        )
    }

    @Suppress("unused")
    val activeCoordinator: AssistantPipelineCoordinator
        get() = pipelineCoordinator

    fun createAudioSource(): AudioSource = MicrophoneAudioCaptureSource(appConfig)

    fun createSttEngine(): SherpaVadSttEngine = SherpaVadSttEngine(
        config = appConfig,
        backend = SherpaOnnxRuntimeBackend(),
        offlineRescorer = SherpaOfflineWhisperRescorer(),
    )

    fun createLlmEngine(): LiteRtGemmaEngine {
        return LiteRtGemmaEngine(context, appConfig)
    }

    @Suppress("UNUSED_PARAMETER")
    fun getOrCreatePipelineCoordinator(scope: CoroutineScope): AssistantPipelineCoordinator {
        return pipelineCoordinator
    }

    /**
     * Forward system memory trim events to the active coordinator and engines.
     */
    fun onTrimMemory(level: Int) {
        pipelineCoordinator.onTrimMemory(level)
    }

    data class ReadinessStatus(
        val hasAudioPermission: Boolean,
        val hasNotificationPermission: Boolean,
        val hasOverlayPermission: Boolean,
        val modelReport: AppConfig.ModelValidationReport
    ) {
        val allPermissionsGranted: Boolean
            get() = hasAudioPermission && hasNotificationPermission && hasOverlayPermission

        val canStartAssistant: Boolean
            get() = allPermissionsGranted && modelReport.allPresent

        val isReady: Boolean
            get() = canStartAssistant
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
