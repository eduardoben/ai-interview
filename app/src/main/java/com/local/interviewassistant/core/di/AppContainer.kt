package com.local.interviewassistant.core.di

import android.annotation.SuppressLint
import android.content.Context
import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.LlmGenerationConfig
import com.local.interviewassistant.core.model.PromptStyle
import com.local.interviewassistant.data.audio.AudioSource
import com.local.interviewassistant.data.audio.MicrophoneAudioCaptureSource
import com.local.interviewassistant.data.llm.LiteRtGemmaEngine
import com.local.interviewassistant.data.provisioning.ModelProvisioningManager
import com.local.interviewassistant.data.stt.SherpaOfflineWhisperRescorer
import com.local.interviewassistant.data.stt.SherpaOnnxRuntimeBackend
import com.local.interviewassistant.data.stt.SherpaVadSttEngine
import com.local.interviewassistant.service.AssistantPipelineCoordinator
import kotlinx.coroutines.CoroutineScope
import java.io.File

class AppContainer private constructor(private val context: Context) {

    val modelsRootDir: File = (context.getExternalFilesDir(null) ?: context.filesDir).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    val appConfig: AppConfig = AppConfig(modelsRootDir)

    init {
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

        appConfig.updateLlmConfig(
            LlmGenerationConfig(
                temperature = temp,
                topK = topK,
                maxTokens = maxTokens,
                promptStyle = style,
                interviewContext = contextStr,
                selectedModelPath = selectedModelPath,
                preferredDelegate = preferredDelegate,
                enableWhisperRescorer = enableWhisperRescorer
            )
        )
    }
    
    fun saveLlmConfig(newConfig: LlmGenerationConfig) {
        val prefs = context.getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("temperature", newConfig.temperature)
            putInt("topK", newConfig.topK)
            putInt("maxTokens", newConfig.maxTokens)
            putString("promptStyle", newConfig.promptStyle.name)
            putString("interviewContext", newConfig.interviewContext)
            putString("selectedModelPath", newConfig.selectedModelPath)
            putString("preferredDelegate", newConfig.preferredDelegate?.name)
            putBoolean("enableWhisperRescorer", newConfig.enableWhisperRescorer)
            apply()
        }
        appConfig.updateLlmConfig(newConfig)
    }

    val provisioningManager: ModelProvisioningManager = ModelProvisioningManager(appConfig)

    fun createAudioSource(): AudioSource = MicrophoneAudioCaptureSource(appConfig)

    fun createSttEngine(): SherpaVadSttEngine = SherpaVadSttEngine(
        config = appConfig,
        backend = SherpaOnnxRuntimeBackend(),
        offlineRescorer = SherpaOfflineWhisperRescorer(),
    )

    fun createLlmEngine(): LiteRtGemmaEngine {
        return LiteRtGemmaEngine(context, appConfig)
    }

    fun createPipelineCoordinator(scope: CoroutineScope): AssistantPipelineCoordinator {
        return AssistantPipelineCoordinator(
            audioSource = createAudioSource(),
            sttEngine = createSttEngine(),
            llmEngine = createLlmEngine(),
            scope = scope,
            appConfig = appConfig,
        )
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
