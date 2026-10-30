package com.local.interviewassistant.data.stt

import com.local.interviewassistant.core.model.AppConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

/**
 * Phase 3: High-Fidelity Second-Pass Offline Rescorer contract.
 *
 * Provides optional offline re-scoring for completed audio utterances
 * using Whisper or Moonshine models from sherpa-onnx.
 * If the models are not present or disabled, passes through without latency overhead.
 */
interface OfflineRescorerBackend {
    suspend fun initialize(config: AppConfig): Result<Unit>
    val isAvailable: Boolean
    fun rescoreUtterance(samples: FloatArray, sampleRate: Int): String?
    fun release()
}

/**
 * Sherpa-ONNX implementation of offline Whisper re-scoring.
 * Uses int8 quantized Whisper models (e.g. tiny.en) for high-accuracy
 * capitalization, punctuation, and technical vocabulary recognition.
 */
class SherpaOfflineWhisperRescorer : OfflineRescorerBackend {

    @Volatile
    private var recognizerInstance: OfflineRecognizer? = null

    override val isAvailable: Boolean
        get() = recognizerInstance != null

    override suspend fun initialize(config: AppConfig): Result<Unit> {
        release()

        // Check if offline model files exist and have non-zero size
        if (!config.hasOfflineRescorer()) {
            return Result.success(Unit) // Soft success: optional rescorer not present
        }

        return try {
            val whisperConfig = OfflineWhisperModelConfig()
            whisperConfig.encoder = config.offlineWhisperEncoderFile.absolutePath
            whisperConfig.decoder = config.offlineWhisperDecoderFile.absolutePath
            whisperConfig.language = "en"
            whisperConfig.task = "transcribe"
            whisperConfig.tailPaddings = -1

            val modelConfig = OfflineModelConfig()
            modelConfig.whisper = whisperConfig
            modelConfig.tokens = config.offlineWhisperTokensFile.absolutePath
            modelConfig.numThreads = 2 // Efficiency cores
            modelConfig.provider = "cpu"
            modelConfig.debug = false

            val featConfig = FeatureConfig()
            featConfig.sampleRate = config.sampleRateHz
            featConfig.featureDim = 80

            val recognizerConfig = OfflineRecognizerConfig()
            recognizerConfig.featConfig = featConfig
            recognizerConfig.modelConfig = modelConfig

            val recognizer = OfflineRecognizer(assetManager = null, config = recognizerConfig)
            recognizerInstance = recognizer
            Result.success(Unit)
        } catch (e: Throwable) {
            release()
            Result.failure(e)
        }
    }

    override fun rescoreUtterance(samples: FloatArray, sampleRate: Int): String? {
        val recognizer = recognizerInstance ?: return null
        if (samples.isEmpty()) return null

        return synchronized(this) {
            try {
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, sampleRate)
                    recognizer.decode(stream)
                    val result = recognizer.getResult(stream).text.trim()
                    result.ifBlank { null }
                } finally {
                    try { stream.release() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    @Synchronized
    override fun release() {
        try {
            recognizerInstance?.release()
        } catch (_: Throwable) {}
        recognizerInstance = null
    }
}

/**
 * No-op implementation when offline rescoring is disabled or during lightweight unit testing.
 */
@Suppress("unused")
class NoOpOfflineRescorer : OfflineRescorerBackend {
    override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
    override val isAvailable: Boolean = false
    override fun rescoreUtterance(samples: FloatArray, sampleRate: Int): String? = null
    override fun release() {}
}
