package com.local.interviewassistant.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.local.interviewassistant.core.model.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface AudioSource {
    fun audioStream(): Flow<FloatArray>
}

class MicrophoneAudioCaptureSource(private val config: AppConfig) : AudioSource {

    @SuppressLint("MissingPermission")
    override fun audioStream(): Flow<FloatArray> = flow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBufferSize, config.vadWindowSizeSamples * 2 * 4)

        var audioRecord: AudioRecord? = null
        var noiseSuppressor: NoiseSuppressor? = null
        var autoGainControl: AutomaticGainControl? = null
        var echoCanceler: AcousticEchoCanceler? = null

        try {
            audioRecord = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    config.sampleRateHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            } catch (_: Throwable) { null }

            if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    config.sampleRateHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            }

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed. State: ${audioRecord.state}")
            }

            val sessionId = audioRecord.audioSessionId
            if (sessionId != 0) {
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)
                        noiseSuppressor?.enabled = true
                    } catch (_: Throwable) {}
                }
                if (AutomaticGainControl.isAvailable()) {
                    try {
                        autoGainControl = AutomaticGainControl.create(sessionId)
                        autoGainControl?.enabled = true
                    } catch (_: Throwable) {}
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)
                        echoCanceler?.enabled = true
                    } catch (_: Throwable) {}
                }
            }

            audioRecord.startRecording()

            val pcmBuffer = ShortArray(config.vadWindowSizeSamples)

            while (true) {
                val readCount = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                if (readCount > 0) {
                    val floatArray = pcm16ToFloatArray(pcmBuffer, readCount)
                    emit(floatArray)
                } else if (readCount < 0) {
                    throw IllegalStateException("AudioRecord read error: $readCount")
                }
            }
        } catch (e: SecurityException) {
            throw SecurityException("Microphone permission denied", e)
        } finally {
            try { noiseSuppressor?.release() } catch (_: Throwable) {}
            try { autoGainControl?.release() } catch (_: Throwable) {}
            try { echoCanceler?.release() } catch (_: Throwable) {}
            try { audioRecord?.stop() } catch (_: Throwable) {}
            try { audioRecord?.release() } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        fun pcm16ToFloatArray(pcmBuffer: ShortArray, readCount: Int): FloatArray {
            val floatArray = FloatArray(readCount)
            for (i in 0 until readCount) {
                floatArray[i] = (pcmBuffer[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
            }
            return floatArray
        }
    }
}
