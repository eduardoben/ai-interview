package com.local.interviewassistant.data.stt

import com.local.interviewassistant.core.model.AppConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

interface VadSttBackend {
    suspend fun initialize(config: AppConfig): Result<Unit>
    fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean
    fun getPartialTranscript(): String
    fun finalizeAndResetStream(): String
    fun release()
}

class SherpaOnnxRuntimeBackend : VadSttBackend {
    
    @Volatile private var vadInstance: Vad? = null
    @Volatile private var recognizerInstance: OnlineRecognizer? = null
    @Volatile private var streamInstance: OnlineStream? = null
    
    override suspend fun initialize(config: AppConfig): Result<Unit> {
        // Synchronously release old handles first to prevent C++ mutex race condition
        release()

        val validation = config.validateModelsOnDisk()
        if (!validation.allPresent) {
            return Result.failure(IllegalStateException("Missing STT/VAD model files on disk"))
        }

        // Verify minimum valid file sizes on disk to prevent C++ native crashes from truncated downloads
        if (config.sttEncoderFile.length() < 1 * 1024 * 1024L ||
            config.sttDecoderFile.length() < 100 * 1024L ||
            config.sttJoinerFile.length() < 100 * 1024L ||
            config.sttTokensFile.length() < 100L) {
            return Result.failure(IllegalStateException("Incomplete or downloading STT model files on disk"))
        }
        
        return try {
            val sileroConfig = SileroVadModelConfig()
            sileroConfig.model = config.vadModelFile.absolutePath
            sileroConfig.threshold = config.vadThreshold
            sileroConfig.windowSize = config.vadWindowSizeSamples
            
            val vadConfig = VadModelConfig()
            vadConfig.sileroVadModelConfig = sileroConfig
            vadConfig.sampleRate = config.sampleRateHz
            vadConfig.numThreads = 1
            vadConfig.debug = false
            
            val vad = Vad(config = vadConfig)
            
            val transducerConfig = OnlineTransducerModelConfig()
            transducerConfig.encoder = config.sttEncoderFile.absolutePath
            transducerConfig.decoder = config.sttDecoderFile.absolutePath
            transducerConfig.joiner = config.sttJoinerFile.absolutePath
            
            val modelConfig = OnlineModelConfig()
            modelConfig.transducer = transducerConfig
            modelConfig.tokens = config.sttTokensFile.absolutePath
            modelConfig.numThreads = 1
            modelConfig.debug = false
            
            val featConfig = FeatureConfig()
            featConfig.sampleRate = config.sampleRateHz
            featConfig.featureDim = 80
            
            val recognizerConfig = OnlineRecognizerConfig()
            recognizerConfig.featConfig = featConfig
            recognizerConfig.modelConfig = modelConfig
            recognizerConfig.decodingMethod = "greedy_search"
            recognizerConfig.enableEndpoint = true

            val recognizer = OnlineRecognizer(assetManager = null, config = recognizerConfig)
            val stream = recognizer.createStream()
            
            vadInstance = vad
            recognizerInstance = recognizer
            streamInstance = stream

            Result.success(Unit)
        } catch (e: Exception) {
            release()
            Result.failure(e)
        }
    }

    override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean {
        val stream = streamInstance ?: return false
        val rec = recognizerInstance ?: return false

        try {
            // Accept waveform into STT recognizer stream
            stream.acceptWaveform(samples, sampleRate)

            // Decode ready frames
            while (rec.isReady(stream)) {
                rec.decode(stream)
            }
        } catch (_: Throwable) {
            return false
        }

        // Check Silero VAD for active speech in current window
        val vad = vadInstance
        val isSpeechByVad = if (vad != null) {
            try {
                vad.acceptWaveform(samples)
                vad.isSpeechDetected()
            } catch (_: Throwable) {
                false
            }
        } else false

        // Realistic RMS threshold for speech energy backup (~ -36 dB)
        var sumSquares = 0.0f
        for (sample in samples) {
            sumSquares += sample * sample
        }
        val rms = kotlin.math.sqrt((sumSquares / samples.size).toDouble()).toFloat()
        val isSpeechByRms = rms > 0.015f

        return isSpeechByVad || isSpeechByRms
    }

    override fun getPartialTranscript(): String {
        val stream = streamInstance ?: return ""
        val rec = recognizerInstance ?: return ""
        return try {
            rec.getResult(stream).text.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    override fun finalizeAndResetStream(): String {
        val result = getPartialTranscript()
        val rec = recognizerInstance
        try {
            streamInstance?.release()
        } catch (_: Throwable) {}
        
        streamInstance = if (rec != null) {
            try {
                rec.createStream()
            } catch (_: Throwable) { null }
        } else null

        try {
            vadInstance?.clear()
        } catch (_: Throwable) {}
        
        return result
    }

    @Synchronized
    override fun release() {
        try { streamInstance?.release() } catch (_: Throwable) {}
        try { recognizerInstance?.release() } catch (_: Throwable) {}
        try { vadInstance?.release() } catch (_: Throwable) {}
        
        streamInstance = null
        recognizerInstance = null
        vadInstance = null
    }
}
