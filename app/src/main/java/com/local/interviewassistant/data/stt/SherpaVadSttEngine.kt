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

/**
 * Phase 3 STT Engine: Two-Pass Hybrid Architecture.
 *
 * Pass 1 (Streaming): Low-latency Online Zipformer streams partial words to HUD overlay.
 * Pass 2 (Endpointing):
 *   - Optional offline high-fidelity rescoring (Whisper / Moonshine) on accumulated utterance audio.
 *   - Truecasing & Technical Acronym capitalization (API, REST, SQL, Kotlin, Room, etc.).
 *   - Automatic grammatical Question / Imperative detection with terminal punctuation ('?' or '.').
 */
class SherpaVadSttEngine(
    private val config: AppConfig,
    private val backend: VadSttBackend = SherpaOnnxRuntimeBackend(),
    private val offlineRescorer: OfflineRescorerBackend? = null,
) {
    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _completedQuestion = MutableSharedFlow<TranscriptSegment>(extraBufferCapacity = 8)
    val completedQuestion: SharedFlow<TranscriptSegment> = _completedQuestion.asSharedFlow()

    private val _isSpeechActive = MutableStateFlow(value = false)
    val isSpeechActive: StateFlow<Boolean> = _isSpeechActive.asStateFlow()

    private val mutex = Mutex()
    private var audioBuffer = FloatArray(0)

    // Phase 3: Utterance audio accumulation for high-fidelity second-pass rescoring
    private val utteranceAudioBuffer = mutableListOf<FloatArray>()
    private val windowDurationSec = config.vadWindowSizeSamples.toFloat() / config.sampleRateHz.toFloat()
    private val minSpeechFrames = kotlin.math.ceil(config.minSpeechDurationSec / windowDurationSec).toInt().coerceAtLeast(1)
    private val minSilenceFrames = kotlin.math.ceil(config.minSilenceDurationSec / windowDurationSec).toInt().coerceAtLeast(1)
    // Cap utterance buffer to 30 seconds of audio to avoid unbounded memory growth
    private val maxUtteranceWindows = (30f / windowDurationSec).toInt().coerceAtLeast(100)

    private var speechFrameCount = 0
    private var silenceFrameCount = 0
    private var hasValidSpeech = false

    suspend fun initialize(): Result<Unit> {
        val backendResult = backend.initialize(config)
        if (backendResult.isFailure) return backendResult

        if (offlineRescorer != null) {
            try {
                offlineRescorer.initialize(config)
            } catch (_: Throwable) {}
        }

        return Result.success(Unit)
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
            // Accumulate speech window for Phase 3 second-pass rescoring
            if (utteranceAudioBuffer.size < maxUtteranceWindows) {
                utteranceAudioBuffer.add(window.copyOf())
            }
        } else {
            if (hasValidSpeech) {
                silenceFrameCount++
                if (silenceFrameCount >= minSilenceFrames) {
                    completeCurrentUtterance()
                } else {
                    // Accumulate brief pauses within an ongoing utterance
                    if (utteranceAudioBuffer.size < maxUtteranceWindows) {
                        utteranceAudioBuffer.add(window.copyOf())
                    }
                }
            } else {
                speechFrameCount = 0
            }
        }
    }

    private fun completeCurrentUtterance(): TranscriptSegment? {
        val rawFinal = backend.finalizeAndResetStream().trim()
        val streamingFinal = if (rawFinal.isBlank() && _partialTranscript.value.isNotBlank()) {
            _partialTranscript.value
        } else {
            rawFinal
        }

        // Phase 3: High-Fidelity Second-Pass Rescore (if offline rescorer is active)
        val rescoredText = if (offlineRescorer?.isAvailable == true && utteranceAudioBuffer.isNotEmpty()) {
            val totalSamples = utteranceAudioBuffer.sumOf { it.size }
            val waveform = FloatArray(totalSamples)
            var offset = 0
            for (chunk in utteranceAudioBuffer) {
                chunk.copyInto(waveform, offset)
                offset += chunk.size
            }
            try {
                offlineRescorer.rescoreUtterance(waveform, config.sampleRateHz)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        utteranceAudioBuffer.clear()

        // Choose rescored text if available and non-blank; otherwise fall back to low-latency stream
        val candidateText = if (!rescoredText.isNullOrBlank()) rescoredText else streamingFinal

        // Phase 3: Punctuation, Acronym Truecasing, and Question Mark Restoration
        val finalString = QuestionPunctuationEngine.process(candidateText)

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
            utteranceAudioBuffer.clear()
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
        try { offlineRescorer?.release() } catch (_: Throwable) {}
        utteranceAudioBuffer.clear()
    }
}
