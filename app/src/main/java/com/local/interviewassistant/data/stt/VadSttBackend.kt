package com.local.interviewassistant.data.stt

import com.local.interviewassistant.core.model.AppConfig
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
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

    private val preRollBuffer = ArrayDeque<FloatArray>()
    private val maxPreRollWindows = 8 // ~256ms rolling pre-speech buffer (8 * 32ms)
    @Volatile private var isStreamFeeding = false
    
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
            sileroConfig.minSpeechDuration = config.minSpeechDurationSec
            sileroConfig.minSilenceDuration = config.minSilenceDurationSec
            
            val vadConfig = VadModelConfig()
            vadConfig.sileroVadModelConfig = sileroConfig
            vadConfig.sampleRate = config.sampleRateHz
            vadConfig.numThreads = 2 // Isolate to efficiency cores
            vadConfig.provider = "cpu" // Enforce CPU provider to avoid NPU/GPU driver contention
            vadConfig.debug = false
            
            val vad = Vad(config = vadConfig)
            
            val transducerConfig = OnlineTransducerModelConfig()
            transducerConfig.encoder = config.sttEncoderFile.absolutePath
            transducerConfig.decoder = config.sttDecoderFile.absolutePath
            transducerConfig.joiner = config.sttJoinerFile.absolutePath
            
            val modelConfig = OnlineModelConfig()
            modelConfig.transducer = transducerConfig
            modelConfig.tokens = config.sttTokensFile.absolutePath
            modelConfig.numThreads = 2 // Isolate to efficiency cores
            modelConfig.provider = "cpu" // Enforce CPU provider to avoid NPU/GPU driver contention
            modelConfig.debug = false
            modelConfig.modelingUnit = "bpe"
            modelConfig.bpeVocab = config.sttTokensFile.absolutePath
            
            val featConfig = FeatureConfig()
            featConfig.sampleRate = config.sampleRateHz
            featConfig.featureDim = 80
            
            val recognizerConfig = OnlineRecognizerConfig()
            recognizerConfig.featConfig = featConfig
            recognizerConfig.modelConfig = modelConfig
            recognizerConfig.decodingMethod = config.sttDecodingMethod
            recognizerConfig.maxActivePaths = config.sttMaxActivePaths
            recognizerConfig.blankPenalty = config.sttBlankPenalty

            // Activate Contextual Hotwords Biasing with lowercase vocabulary matching
            val hotwords = config.ensureHotwordsFile()
            if (hotwords.exists() && hotwords.length() > 0L) {
                recognizerConfig.hotwordsFile = hotwords.absolutePath
                recognizerConfig.hotwordsScore = config.sttHotwordsScore
            }

            // Configure Native Endpoint Rules
            val endpointConfig = EndpointConfig()
            endpointConfig.rule1 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = config.minSilenceDurationSec, minUtteranceLength = config.minSpeechDurationSec)
            endpointConfig.rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = config.minSilenceDurationSec * 1.5f, minUtteranceLength = 0.0f)
            endpointConfig.rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0.0f, minUtteranceLength = 30.0f)
            recognizerConfig.endpointConfig = endpointConfig
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
        val vad = vadInstance ?: return false

        // Check Silero VAD for active speech in current window
        val isSpeech = try {
            vad.acceptWaveform(samples)
            vad.isSpeechDetected()
        } catch (_: Throwable) {
            false
        }

        try {
            if (isSpeech) {
                // If transitioning from quiescent silence to active speech, flush pre-roll onset buffer first
                if (!isStreamFeeding) {
                    while (preRollBuffer.isNotEmpty()) {
                        val preRoll = preRollBuffer.removeFirst()
                        stream.acceptWaveform(preRoll, sampleRate)
                    }
                    isStreamFeeding = true
                }

                // Stream active speech chunk into recognizer
                stream.acceptWaveform(samples, sampleRate)

                // Decode ready acoustic frames
                while (rec.isReady(stream)) {
                    rec.decode(stream)
                }
            } else {
                if (isStreamFeeding) {
                    // During an ongoing utterance (short pauses between words), feed silence so lookahead progresses
                    stream.acceptWaveform(samples, sampleRate)
                    while (rec.isReady(stream)) {
                        rec.decode(stream)
                    }
                } else {
                    // Quiescent silence before any speech has started: retain in pre-roll ring buffer, NEVER poison the transducer stream
                    if (preRollBuffer.size >= maxPreRollWindows) {
                        preRollBuffer.removeFirst()
                    }
                    preRollBuffer.addLast(samples.copyOf())
                }
            }
        } catch (_: Throwable) {
            return isSpeech
        }

        return isSpeech
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
        val stream = streamInstance
        val rec = recognizerInstance
        var result = ""

        if (stream != null && rec != null) {
            try {
                // Signal that input has finished for this utterance to flush lookahead buffer
                stream.inputFinished()
                while (rec.isReady(stream)) {
                    rec.decode(stream)
                }
                result = rec.getResult(stream).text.trim()
            } catch (_: Throwable) {}

            try {
                // Reset native stream state for the next utterance without tearing down JNI allocations
                rec.reset(stream)
            } catch (_: Throwable) {
                // Fallback to recreation if native reset throws
                try { stream.release() } catch (_: Throwable) {}
                streamInstance = try { rec.createStream() } catch (_: Throwable) { null }
            }
        }

        try {
            vadInstance?.clear()
        } catch (_: Throwable) {}

        isStreamFeeding = false
        preRollBuffer.clear()

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
        isStreamFeeding = false
        preRollBuffer.clear()
    }
}
