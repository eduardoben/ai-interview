package com.local.interviewassistant.data.stt

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.TranscriptSegment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SherpaVadSttEngine(
    private val config: AppConfig,
    private val backend: VadSttBackend = SherpaOnnxRuntimeBackend(),
) {
    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _completedQuestion = MutableSharedFlow<TranscriptSegment>(extraBufferCapacity = 8)
    val completedQuestion: SharedFlow<TranscriptSegment> = _completedQuestion.asSharedFlow()

    private val _isSpeechActive = MutableStateFlow(value = false)
    val isSpeechActive: StateFlow<Boolean> = _isSpeechActive.asStateFlow()

    private val mutex = Mutex()
    private var audioBuffer = FloatArray(0)

    private val windowDurationSec = config.vadWindowSizeSamples.toFloat() / config.sampleRateHz.toFloat()
    private val minSpeechFrames = kotlin.math.ceil(config.minSpeechDurationSec / windowDurationSec).toInt().coerceAtLeast(1)
    private val minSilenceFrames = kotlin.math.ceil(config.minSilenceDurationSec / windowDurationSec).toInt().coerceAtLeast(1)

    private var speechFrameCount = 0
    private var silenceFrameCount = 0
    private var hasValidSpeech = false

    suspend fun initialize(): Result<Unit> {
        return backend.initialize(config)
    }

    suspend fun processAudioChunk(samples: FloatArray) {
        mutex.withLock {
            val newBuffer = FloatArray(audioBuffer.size + samples.size)
            audioBuffer.copyInto(newBuffer)
            samples.copyInto(newBuffer, audioBuffer.size)
            audioBuffer = newBuffer

            while (audioBuffer.size >= config.vadWindowSizeSamples) {
                val window = audioBuffer.copyOfRange(0, config.vadWindowSizeSamples)
                audioBuffer = audioBuffer.copyOfRange(config.vadWindowSizeSamples, audioBuffer.size)
                
                processWindow(window)
            }
        }
    }

    private fun processWindow(window: FloatArray) {
        val isSpeech = backend.acceptWaveform(window, config.sampleRateHz)

        if (isSpeech) {
            speechFrameCount++
            silenceFrameCount = 0
            if (speechFrameCount >= minSpeechFrames) {
                hasValidSpeech = true
                _isSpeechActive.value = true
                _partialTranscript.value = backend.getPartialTranscript().trim()
            }
        } else {
            if (hasValidSpeech) {
                silenceFrameCount++
                if (silenceFrameCount >= minSilenceFrames) {
                    completeCurrentUtterance()
                }
            }
        }
    }

    private fun completeCurrentUtterance(): TranscriptSegment? {
        val rawFinal = backend.finalizeAndResetStream().trim()
        val finalString = if (rawFinal.isBlank() && _partialTranscript.value.isNotBlank()) {
            _partialTranscript.value
        } else {
            rawFinal
        }

        speechFrameCount = 0
        silenceFrameCount = 0
        hasValidSpeech = false
        _isSpeechActive.value = false
        _partialTranscript.value = ""

        val segment = TranscriptSegment(text = finalString, isFinal = true)
        
        if (segment.normalizedText.length >= config.minQuestionLengthChars) {
            _completedQuestion.tryEmit(segment)
            return segment
        }
        return null
    }

    suspend fun forceEndpoint(): TranscriptSegment? {
        return mutex.withLock {
            completeCurrentUtterance()
        }
    }

    suspend fun reset() {
        mutex.withLock {
            audioBuffer = FloatArray(0)
            speechFrameCount = 0
            silenceFrameCount = 0
            hasValidSpeech = false
            _isSpeechActive.value = false
            _partialTranscript.value = ""
            backend.finalizeAndResetStream()
        }
    }

    fun release() {
        backend.release()
    }
}
