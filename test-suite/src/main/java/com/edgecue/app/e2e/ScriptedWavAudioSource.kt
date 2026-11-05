package com.edgecue.app.e2e

import android.content.Context
import android.content.res.AssetManager
import com.edgecue.app.data.audio.AudioSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Deterministic audio injector conforming to [AudioSource].
 *
 * Implements non-intrusive audio feeding for automated end-to-end tests:
 * - Robust RIFF chunk parser handling `fmt `, `LIST`, `JUNK`, `id3 `, and `data` chunks.
 * - Normalized [FloatArray] frames in `[-1.0f, 1.0f]` matching production capture geometry.
 * - 512 samples per frame (32 ms @ 16 kHz), zero-padding any fractional trailing speech frame.
 * - Monotonic nano-anchored 32 ms pacing eliminating cumulative scheduler drift.
 * - Exactly 2500 ms (79 frames) of trailing silence for deterministic Silero VAD endpointing.
 * - Captures [audioEndAtNanos] precisely before trailing silence starts for Gate 2 SLA evaluation.
 * - Suspends indefinitely after silence via [awaitCancellation] mimicking a continuous live microphone.
 */
class ScriptedWavAudioSource(
    val audioFrames: List<FloatArray>,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val totalAudioDurationMs: Long = 0L,
    val assetPath: String = ""
) : AudioSource {

    @Volatile
    var audioEndAtNanos: Long = 0L
        private set

    constructor(context: Context, assetPath: String) : this(
        assetManager = context.assets,
        assetPath = assetPath
    )

    constructor(assetManager: AssetManager, assetPath: String) : this(
        parsed = assetManager.open(assetPath).use { parseWav(it) },
        assetPath = assetPath
    )

    private constructor(parsed: ParsedWav, assetPath: String) : this(
        audioFrames = parsed.frames,
        sampleRateHz = parsed.sampleRate,
        totalAudioDurationMs = parsed.durationMs,
        assetPath = assetPath
    )

    override fun audioStream(): Flow<FloatArray> = flow {
        val frameDurationNanos = FRAME_DURATION_NANOS
        val streamStartNanos = System.nanoTime()
        var emittedFrames = 0L

        // 1. Emit active speech frames with monotonic drift-free pacing
        for (frame in audioFrames) {
            val targetNanos = streamStartNanos + (emittedFrames * frameDurationNanos)
            val nowNanos = System.nanoTime()
            val delayNanos = targetNanos - nowNanos
            if (delayNanos > 0L) {
                val delayMs = delayNanos / 1_000_000L
                if (delayMs > 0L) {
                    delay(delayMs)
                }
            }
            emit(frame)
            emittedFrames++
        }

        // 2. CRITICAL TIMING SEAM: Record exact acoustic end of speech before trailing silence begins
        audioEndAtNanos = System.nanoTime()

        // 3. Emit exactly 2500 ms trailing silence (79 frames of 512 samples @ 32 ms = 2528 ms)
        val silenceFrame = FloatArray(VAD_WINDOW_SIZE_SAMPLES) { 0.0f }
        for (i in 0 until TRAILING_SILENCE_FRAMES) {
            val targetNanos = streamStartNanos + (emittedFrames * frameDurationNanos)
            val nowNanos = System.nanoTime()
            val delayNanos = targetNanos - nowNanos
            if (delayNanos > 0L) {
                val delayMs = delayNanos / 1_000_000L
                if (delayMs > 0L) {
                    delay(delayMs)
                }
            }
            emit(silenceFrame)
            emittedFrames++
        }

        // 4. Suspend indefinitely like a live microphone until test scope cancellation
        awaitCancellation()
    }

    private data class ParsedWav(
        val frames: List<FloatArray>,
        val sampleRate: Int,
        val durationMs: Long
    )

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 16000
        const val VAD_WINDOW_SIZE_SAMPLES = 512
        const val FRAME_DURATION_NANOS = 32_000_000L // 32 ms per 512 samples @ 16 kHz
        const val TRAILING_SILENCE_FRAMES = 79 // 79 * 32 ms = 2528 ms (~2500 ms)

        fun fromAsset(assetManager: AssetManager, assetPath: String): ScriptedWavAudioSource {
            return ScriptedWavAudioSource(assetManager, assetPath)
        }

        fun fromAsset(context: Context, assetPath: String): ScriptedWavAudioSource {
            return ScriptedWavAudioSource(context, assetPath)
        }

        fun fromInputStream(inputStream: InputStream, assetPath: String = "stream"): ScriptedWavAudioSource {
            val parsed = parseWav(inputStream)
            return ScriptedWavAudioSource(parsed, assetPath)
        }

        fun fromWavBytes(bytes: ByteArray, assetPath: String = "bytes"): ScriptedWavAudioSource {
            return fromInputStream(ByteArrayInputStream(bytes), assetPath)
        }

        fun fromFrames(
            frames: List<FloatArray>,
            sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
            assetPath: String = "direct"
        ): ScriptedWavAudioSource {
            val totalSamples = frames.size.toLong() * VAD_WINDOW_SIZE_SAMPLES
            val durationMs = if (sampleRateHz > 0) (totalSamples * 1000L) / sampleRateHz else 0L
            return ScriptedWavAudioSource(
                audioFrames = frames,
                sampleRateHz = sampleRateHz,
                totalAudioDurationMs = durationMs,
                assetPath = assetPath
            )
        }

        private fun parseWav(inputStream: InputStream): ParsedWav {
            val allBytes = inputStream.readBytes()
            require(allBytes.size >= 12) { "WAV file too short for RIFF header (size: ${allBytes.size} bytes)" }

            val buffer = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN)
            val riffMagic = ByteArray(4).also { buffer.get(it) }
            val riffStr = String(riffMagic, Charsets.US_ASCII)
            require(riffStr == "RIFF") { "Not a RIFF file, magic was '$riffStr'" }

            buffer.getInt() // riffSize (fileSize - 8)
            val waveMagic = ByteArray(4).also { buffer.get(it) }
            val waveStr = String(waveMagic, Charsets.US_ASCII)
            require(waveStr == "WAVE") { "Not a WAVE file, format was '$waveStr'" }

            var sampleRate = DEFAULT_SAMPLE_RATE_HZ
            var channels = 1
            var bitsPerSample = 16
            var pcmBytes: ByteArray? = null

            while (buffer.remaining() >= 8) {
                val chunkIdBytes = ByteArray(4).also { buffer.get(it) }
                val chunkId = String(chunkIdBytes, Charsets.US_ASCII)
                val chunkSize = buffer.getInt()

                when (chunkId) {
                    "fmt " -> {
                        require(chunkSize >= 16) { "Invalid fmt chunk size: $chunkSize" }
                        val format = buffer.getShort().toInt()
                        require(format == 1) { "Only uncompressed PCM WAV supported, found format $format" }
                        channels = buffer.getShort().toInt()
                        require(channels == 1) { "Only mono WAV supported, found $channels channels" }
                        sampleRate = buffer.getInt()
                        require(sampleRate == DEFAULT_SAMPLE_RATE_HZ) {
                            "Only 16000 Hz WAV supported, found $sampleRate Hz"
                        }
                        buffer.getInt() // byteRate (16000 * 1 * 2 = 32000)
                        buffer.getShort() // blockAlign (2)
                        bitsPerSample = buffer.getShort().toInt()
                        require(bitsPerSample == 16) { "Only 16-bit PCM supported, found $bitsPerSample bits" }

                        // Skip any extra format bytes (e.g. cbSize in extensible fmt)
                        val extraFmt = chunkSize - 16
                        if (extraFmt > 0) {
                            val toSkip = minOf(extraFmt, buffer.remaining())
                            buffer.position(buffer.position() + toSkip)
                        }
                    }
                    "data" -> {
                        val payloadSize = if (chunkSize in 0..buffer.remaining()) chunkSize else buffer.remaining()
                        pcmBytes = ByteArray(payloadSize).also { buffer.get(it) }
                        break // Audio data located; stop scanning
                    }
                    else -> {
                        // Skip extraneous metadata chunks (LIST, JUNK, ID3, bext, etc.)
                        val paddedSize = if (chunkSize > 0) chunkSize + (chunkSize % 2) else 0
                        val toSkip = minOf(paddedSize, buffer.remaining())
                        buffer.position(buffer.position() + toSkip)
                    }
                }
            }

            requireNotNull(pcmBytes) { "No 'data' chunk found in WAV asset" }

            // Convert Little-Endian S16LE bytes to ShortArray
            val shortCount = pcmBytes.size / 2
            val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val shortArray = ShortArray(shortCount)
            shortBuffer.get(shortArray)

            // Normalize 16-bit signed PCM to FloatArray [-1.0f, 1.0f]
            val fullFloatArray = FloatArray(shortCount)
            for (i in 0 until shortCount) {
                fullFloatArray[i] = (shortArray[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
            }

            // Slice into 512-sample frames with zero-padded remainder
            val frameList = mutableListOf<FloatArray>()
            var offset = 0
            while (offset < fullFloatArray.size) {
                val frame = FloatArray(VAD_WINDOW_SIZE_SAMPLES)
                val copyLen = minOf(VAD_WINDOW_SIZE_SAMPLES, fullFloatArray.size - offset)
                System.arraycopy(fullFloatArray, offset, frame, 0, copyLen)
                frameList.add(frame)
                offset += VAD_WINDOW_SIZE_SAMPLES
            }

            val durationMs = if (sampleRate > 0) (shortCount.toLong() * 1000L) / sampleRate else 0L
            return ParsedWav(frameList, sampleRate, durationMs)
        }
    }
}
