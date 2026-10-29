package com.local.interviewassistant.core.model

import com.local.interviewassistant.data.hardware.DelegateType

enum class PromptStyle(val displayName: String, val systemInstruction: String) {
    CONCISE(
        "Concise",
        "Answer the following question directly, accurately, and concisely. Use at most 3 key bullet points. No introductions or greetings."
    ),
    VERBOSE(
        "Verbose",
        "Provide a comprehensive, structured answer covering core concepts, trade-offs, and practical examples. No greetings."
    ),
    SIMPLE(
        "Simple",
        "Explain the answer in plain, straightforward language using 2 to 3 short sentences focused on the core concept."
    )
}

data class LlmGenerationConfig(
    val temperature: Float = 0.2f,
    val topK: Int = 20,
    val maxTokens: Int = 200,
    val promptStyle: PromptStyle = PromptStyle.CONCISE,
    val interviewContext: String = "",
    val selectedModelPath: String = "",
    val preferredDelegate: DelegateType? = null,
    val enableWhisperRescorer: Boolean = true,
    val teleprompterFontSizeSp: Float = 20f,
    val stealthDimmerAlpha: Float = 0f,
    val enableVolumeKeyShortcuts: Boolean = true,
    val keepScreenOn: Boolean = true,
    val minSilenceDurationSec: Float = 1.2f,
    val enableHardwareAudioEffects: Boolean = false
) {
    fun validated(): LlmGenerationConfig {
        return copy(
            temperature = temperature.coerceIn(0.0f, 1.0f),
            topK = topK.coerceIn(1, 100),
            maxTokens = maxTokens.coerceIn(64, 512),
            interviewContext = interviewContext.trim(),
            selectedModelPath = selectedModelPath.trim(),
            preferredDelegate = preferredDelegate,
            enableWhisperRescorer = enableWhisperRescorer,
            teleprompterFontSizeSp = teleprompterFontSizeSp.coerceIn(16f, 28f),
            stealthDimmerAlpha = stealthDimmerAlpha.coerceIn(0f, 0.75f),
            enableVolumeKeyShortcuts = enableVolumeKeyShortcuts,
            keepScreenOn = keepScreenOn,
            minSilenceDurationSec = minSilenceDurationSec.coerceIn(0.5f, 3.0f),
            enableHardwareAudioEffects = enableHardwareAudioEffects
        )
    }
}

