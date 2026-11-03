package com.local.interviewassistant.data.provisioning

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.data.hardware.HardwareProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

enum class ErrorReason {
    INSUFFICIENT_STORAGE,
    NETWORK_ERROR,
    @Suppress("unused")
    FILE_CORRUPTION,
    SERVICE_TIMEOUT,
    CANCELLED,
    UNKNOWN
}

sealed interface ProvisioningState {
    data class Checking(val profile: HardwareProfile?) : ProvisioningState
    data class NeedsDownload(
        val profile: HardwareProfile,
        val missingFilesCount: Int,
        val remainingBytes: Long,
        val hasPartialResume: Boolean
    ) : ProvisioningState
    data class Downloading(
        val profile: HardwareProfile,
        val currentFileIndex: Int, // 1..9
        val totalFiles: Int = 9,
        val currentFileName: String,
        val bytesDownloaded: Long,
        val totalBytesRequired: Long,
        val speedBytesPerSec: Long
    ) : ProvisioningState
    data class Ready(val profile: HardwareProfile, val llmPath: String) : ProvisioningState
    data class Error(
        val profile: HardwareProfile,
        val reason: ErrorReason,
        val message: String,
        val canResume: Boolean
    ) : ProvisioningState
}

data class StreamResponse(
    val statusCode: Int,
    val inputStream: InputStream,
    val contentLength: Long
)

fun interface StreamFetcher {
    fun openStream(url: String, rangeOffset: Long, authToken: String?): StreamResponse
}

fun interface StorageChecker {
    fun getAvailableBytes(directory: File): Long
}

class DefaultStorageChecker : StorageChecker {
    override fun getAvailableBytes(directory: File): Long {
        return try {
            android.os.StatFs(directory.absolutePath).availableBytes
        } catch (_: Throwable) {
            Long.MAX_VALUE
        }
    }
}

class HttpUrlStreamFetcher : StreamFetcher {
    override fun openStream(url: String, rangeOffset: Long, authToken: String?): StreamResponse {
        var currentUrl = url
        var connection: HttpURLConnection
        var redirectCount = 0
        val maxRedirects = 10
        val initialHost = try { URI(url).host } catch (_: Exception) { null }

        while (true) {
            val u = URI(currentUrl).toURL()
            connection = u.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connection.setRequestProperty("Accept", "*/*")

            if (rangeOffset > 0L) {
                connection.setRequestProperty("Range", "bytes=$rangeOffset-")
            }

            if (!authToken.isNullOrEmpty()) {
                val currentHost = try { u.toURI().host } catch (_: Exception) { null }
                if (redirectCount == 0 || (initialHost != null && initialHost.equals(currentHost, ignoreCase = true))) {
                    connection.setRequestProperty("Authorization", "Bearer $authToken")
                }
            }

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                if (!location.isNullOrEmpty() && redirectCount < maxRedirects) {
                    val resolvedUri = u.toURI().resolve(location)
                    currentUrl = resolvedUri.toString()
                    connection.disconnect()
                    redirectCount++
                    continue
                }
            }

            if (code == 416) {
                val stream = connection.errorStream ?: connection.inputStream ?: java.io.ByteArrayInputStream(ByteArray(0))
                return StreamResponse(statusCode = 416, inputStream = stream, contentLength = 0L)
            }

            if (code >= 400) {
                connection.disconnect()
                throw IOException("HTTP error code: $code downloading $url")
            }

            if (code != 200 && code != 206) {
                connection.disconnect()
                throw IOException("HTTP download failed with code $code for URL: $url")
            }

            val contentLength = connection.contentLengthLong
            return StreamResponse(
                statusCode = code,
                inputStream = connection.inputStream,
                contentLength = contentLength
            )
        }
    }
}

data class ManifestEntry(
    val id: String,
    val targetFile: File,
    val downloadUrl: String,
    val estimatedBytes: Long,
    val minExpectedBytes: Long,
    val exactBytes: Long = -1L,
    val expectedSha256: String? = null
)

class ModelProvisioningManager(
    val config: AppConfig,
    private val fetcher: StreamFetcher = HttpUrlStreamFetcher(),
    private val storageChecker: StorageChecker = DefaultStorageChecker(),
    val verifyChecksums: Boolean = true
) {
    private val _state = MutableStateFlow<ProvisioningState>(ProvisioningState.Checking(null))
    val state: StateFlow<ProvisioningState> = _state.asStateFlow()

    @Volatile
    private var activeFileOutputStream: FileOutputStream? = null
    private var activeInputStream: InputStream? = null

    companion object {
        const val SAFETY_STORAGE_BUFFER_BYTES = 500_000_000L // 500 MB
    }

    internal fun computeSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun buildManifest(profile: HardwareProfile): List<ManifestEntry> {
        val llmFile = File(config.llmDir, profile.recommendedLlmFileName)
        val llmSha256 = AppConfig.resolveLlmSha256(profile.recommendedLlmFileName)
        val llmBytes = if (profile.expectedLlmBytes > 0L) profile.expectedLlmBytes else AppConfig.resolveLlmBytes(profile.recommendedLlmFileName)
        return listOf(
            ManifestEntry(
                id = "silero_vad",
                targetFile = File(config.vadDir, "silero_vad.onnx"),
                downloadUrl = AppConfig.VAD_URL,
                estimatedBytes = AppConfig.VAD_BYTES,
                minExpectedBytes = 2_000_000L,
                exactBytes = AppConfig.VAD_BYTES,
                expectedSha256 = AppConfig.VAD_SHA256
            ),
            ManifestEntry(
                id = "zipformer_encoder",
                targetFile = File(config.asrDir, "encoder-epoch-99-avg-1.int8.onnx"),
                downloadUrl = AppConfig.STT_ENCODER_URL,
                estimatedBytes = AppConfig.STT_ENCODER_BYTES,
                minExpectedBytes = 120_000_000L,
                exactBytes = AppConfig.STT_ENCODER_BYTES,
                expectedSha256 = AppConfig.STT_ENCODER_SHA256
            ),
            ManifestEntry(
                id = "zipformer_decoder",
                targetFile = File(config.asrDir, "decoder-epoch-99-avg-1.onnx"),
                downloadUrl = AppConfig.STT_DECODER_URL,
                estimatedBytes = AppConfig.STT_DECODER_BYTES,
                minExpectedBytes = 2_000_000L,
                exactBytes = AppConfig.STT_DECODER_BYTES,
                expectedSha256 = AppConfig.STT_DECODER_SHA256
            ),
            ManifestEntry(
                id = "zipformer_joiner",
                targetFile = File(config.asrDir, "joiner-epoch-99-avg-1.int8.onnx"),
                downloadUrl = AppConfig.STT_JOINER_URL,
                estimatedBytes = AppConfig.STT_JOINER_BYTES,
                minExpectedBytes = 200_000L,
                exactBytes = AppConfig.STT_JOINER_BYTES,
                expectedSha256 = AppConfig.STT_JOINER_SHA256
            ),
            ManifestEntry(
                id = "zipformer_tokens",
                targetFile = File(config.asrDir, "tokens.txt"),
                downloadUrl = AppConfig.STT_TOKENS_URL,
                estimatedBytes = AppConfig.STT_TOKENS_BYTES,
                minExpectedBytes = 4_000L,
                exactBytes = AppConfig.STT_TOKENS_BYTES,
                expectedSha256 = AppConfig.STT_TOKENS_SHA256
            ),
            ManifestEntry(
                id = "whisper_encoder",
                targetFile = File(config.whisperDir, "tiny.en-encoder.int8.onnx"),
                downloadUrl = AppConfig.WHISPER_TINY_ENCODER_URL,
                estimatedBytes = AppConfig.WHISPER_TINY_ENCODER_BYTES,
                minExpectedBytes = 10_000_000L,
                exactBytes = AppConfig.WHISPER_TINY_ENCODER_BYTES,
                expectedSha256 = AppConfig.WHISPER_TINY_ENCODER_SHA256
            ),
            ManifestEntry(
                id = "whisper_decoder",
                targetFile = File(config.whisperDir, "tiny.en-decoder.int8.onnx"),
                downloadUrl = AppConfig.WHISPER_TINY_DECODER_URL,
                estimatedBytes = AppConfig.WHISPER_TINY_DECODER_BYTES,
                minExpectedBytes = 85_000_000L,
                exactBytes = AppConfig.WHISPER_TINY_DECODER_BYTES,
                expectedSha256 = AppConfig.WHISPER_TINY_DECODER_SHA256
            ),
            ManifestEntry(
                id = "whisper_tokens",
                targetFile = File(config.whisperDir, "tiny.en-tokens.txt"),
                downloadUrl = AppConfig.WHISPER_TINY_TOKENS_URL,
                estimatedBytes = AppConfig.WHISPER_TINY_TOKENS_BYTES,
                minExpectedBytes = 500_000L,
                exactBytes = AppConfig.WHISPER_TINY_TOKENS_BYTES,
                expectedSha256 = AppConfig.WHISPER_TINY_TOKENS_SHA256
            ),
            ManifestEntry(
                id = "gemma_llm",
                targetFile = llmFile,
                downloadUrl = profile.recommendedLlmDownloadUrl,
                estimatedBytes = llmBytes,
                minExpectedBytes = 2_500_000_000L,
                exactBytes = llmBytes,
                expectedSha256 = llmSha256
            )
        )
    }

    fun isEntryValid(entry: ManifestEntry): Boolean {
        // Check primary structured target file
        if (entry.targetFile.exists() && entry.targetFile.length() >= entry.minExpectedBytes) {
            return true
        }
        // Also check if legacy flat path exists and is valid
        val legacyFlat = File(config.modelsRootDir, entry.targetFile.name)
        if (legacyFlat.exists() && legacyFlat.length() >= entry.minExpectedBytes) {
            return true
        }
        // Special legacy names for ASR models
        if (entry.id == "zipformer_encoder") {
            val flatEncoder = File(config.modelsRootDir, "encoder.onnx")
            if (flatEncoder.exists() && flatEncoder.length() >= entry.minExpectedBytes) return true
        } else if (entry.id == "zipformer_decoder") {
            val int8Decoder = File(config.asrDir, "decoder-epoch-99-avg-1.int8.onnx")
            if (int8Decoder.exists() && int8Decoder.length() >= 500_000L) return true
            val flatDecoder = File(config.modelsRootDir, "decoder.onnx")
            if (flatDecoder.exists() && flatDecoder.length() >= entry.minExpectedBytes) return true
        } else if (entry.id == "zipformer_joiner") {
            val flatJoiner = File(config.modelsRootDir, "joiner.onnx")
            if (flatJoiner.exists() && flatJoiner.length() >= entry.minExpectedBytes) return true
        }
        return false
    }

    fun checkProvisioningStatus(profile: HardwareProfile): ProvisioningState {
        config.ensureDirectoriesExist()
        val manifest = buildManifest(profile)

        var missingCount = 0
        var remainingBytes = 0L
        var hasPartialResume = false

        for (entry in manifest) {
            if (isEntryValid(entry)) {
                continue
            }

            // Remove corrupted files (< minExpectedBytes)
            if (entry.targetFile.exists() && entry.targetFile.length() < entry.minExpectedBytes) {
                entry.targetFile.delete()
            }

            missingCount++
            val tmpFile = File(entry.targetFile.parentFile, "${entry.targetFile.name}.tmp")
            val tmpBytes = if (tmpFile.exists()) tmpFile.length() else 0L
            if (tmpBytes > 0L) {
                hasPartialResume = true
            }
            val needed = (entry.estimatedBytes - tmpBytes).coerceAtLeast(0L)
            remainingBytes += needed
        }

        val newState = if (missingCount == 0) {
            val llmFile = manifest.last().targetFile
            val resolvedLlm = if (llmFile.exists()) llmFile else config.activeLlmModelFile
            ProvisioningState.Ready(profile, resolvedLlm.absolutePath)
        } else {
            ProvisioningState.NeedsDownload(
                profile = profile,
                missingFilesCount = missingCount,
                remainingBytes = remainingBytes,
                hasPartialResume = hasPartialResume
            )
        }

        _state.value = newState
        return newState
    }

    @Suppress("unused")
    fun resetState(profile: HardwareProfile? = null) {
        if (profile != null) {
            checkProvisioningStatus(profile)
        } else {
            _state.value = ProvisioningState.Checking(null)
        }
    }

    fun cancelOngoingDownload(profile: HardwareProfile, reason: ErrorReason = ErrorReason.CANCELLED, message: String = "Download paused by user") {
        closeActiveStreams()
        _state.value = ProvisioningState.Error(
            profile = profile,
            reason = reason,
            message = message,
            canResume = true
        )
    }

    private fun closeActiveStreams() {
        try {
            activeFileOutputStream?.flush()
            activeFileOutputStream?.close()
        } catch (_: Exception) {}
        activeFileOutputStream = null
        try {
            activeInputStream?.close()
        } catch (_: Exception) {}
        activeInputStream = null
    }

    internal fun cleanOrphanLlmFiles(recommendedFileName: String) {
        val searchDirs = listOf(config.llmDir, config.modelsRootDir)
        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles { file ->
                file.isFile && file.name.endsWith(".litertlm", ignoreCase = true) && file.name != recommendedFileName
            }
            files?.forEach { file ->
                try {
                    file.delete()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun downloadAllModels(
        profile: HardwareProfile,
        hfBearerToken: String? = null
    ): Result<AppConfig.ModelValidationReport> = withContext(Dispatchers.IO) {
        if (!config.ensureDirectoriesExist()) {
            val errState = ProvisioningState.Error(
                profile = profile,
                reason = ErrorReason.UNKNOWN,
                message = "Failed to access models storage directory",
                canResume = false
            )
            _state.value = errState
            return@withContext Result.failure(IOException(errState.message))
        }

        // Clean orphan LLM files to save 3GB
        cleanOrphanLlmFiles(profile.recommendedLlmFileName)

        val manifest = buildManifest(profile)
        val missingEntries = manifest.filter { !isEntryValid(it) }

        if (missingEntries.isEmpty()) {
            config.updateLlmConfig(
                config.llmConfig.value.copy(
                    selectedModelPath = manifest.last().targetFile.absolutePath,
                    preferredDelegate = profile.preferredDelegate,
                    enableWhisperRescorer = profile.autoEnableWhisperRescorer
                )
            )
            val readyState = ProvisioningState.Ready(profile, manifest.last().targetFile.absolutePath)
            _state.value = readyState
            return@withContext Result.success(config.validateModelsOnDisk())
        }

        // StatFs storage check with 500 MB safety buffer
        var remainingBytesNeeded = 0L
        for (entry in missingEntries) {
            val tmpFile = File(entry.targetFile.parentFile, "${entry.targetFile.name}.tmp")
            val tmpBytes = if (tmpFile.exists()) tmpFile.length() else 0L
            val needed = (entry.estimatedBytes - tmpBytes).coerceAtLeast(0L)
            remainingBytesNeeded += needed
        }
        val requiredWithBuffer = remainingBytesNeeded + SAFETY_STORAGE_BUFFER_BYTES
        val availableDisk = storageChecker.getAvailableBytes(config.modelsRootDir)

        if (availableDisk < requiredWithBuffer) {
            val msg = "Insufficient storage: required ${(requiredWithBuffer / (1024 * 1024))} MB, available ${(availableDisk / (1024 * 1024))} MB"
            val errState = ProvisioningState.Error(
                profile = profile,
                reason = ErrorReason.INSUFFICIENT_STORAGE,
                message = msg,
                canResume = false
            )
            _state.value = errState
            return@withContext Result.failure(IOException(msg))
        }

        val totalFiles = manifest.size
        for ((index, entry) in manifest.withIndex()) {
            currentCoroutineContext().ensureActive()

            if (isEntryValid(entry)) {
                continue
            }

            // Remove corrupted file if present
            if (entry.targetFile.exists() && entry.targetFile.length() < entry.minExpectedBytes) {
                entry.targetFile.delete()
            }

            try {
                downloadFileWithResume(
                    profile = profile,
                    entry = entry,
                    fileIndex = index + 1,
                    totalFiles = totalFiles,
                    authToken = hfBearerToken
                )
            } catch (e: Exception) {
                closeActiveStreams()
                if (e is CancellationException) {
                    if (_state.value !is ProvisioningState.Error) {
                        val cancelState = ProvisioningState.Error(
                            profile = profile,
                            reason = ErrorReason.CANCELLED,
                            message = "Download cancelled by user",
                            canResume = true
                        )
                        _state.value = cancelState
                    }
                    throw e
                }
                val reason = if (e is IOException) ErrorReason.NETWORK_ERROR else ErrorReason.UNKNOWN
                val errState = ProvisioningState.Error(
                    profile = profile,
                    reason = reason,
                    message = e.message ?: "Download failed for ${entry.targetFile.name}",
                    canResume = true
                )
                _state.value = errState
                return@withContext Result.failure(e)
            }
        }

        // Post-download auto configuration
        val finalLlm = manifest.last().targetFile
        config.updateLlmConfig(
            config.llmConfig.value.copy(
                selectedModelPath = finalLlm.absolutePath,
                preferredDelegate = profile.preferredDelegate,
                enableWhisperRescorer = profile.autoEnableWhisperRescorer
            )
        )

        val finalReport = config.validateModelsOnDisk()
        _state.value = ProvisioningState.Ready(profile, finalLlm.absolutePath)
        Result.success(finalReport)
    }

    internal suspend fun downloadFileWithResume(
        profile: HardwareProfile,
        entry: ManifestEntry,
        fileIndex: Int,
        totalFiles: Int,
        authToken: String?
    ) = withContext(Dispatchers.IO) {
        val targetFile = entry.targetFile
        val parent = targetFile.parentFile ?: config.modelsRootDir
        if (!parent.exists()) parent.mkdirs()

        val tmpFile = File(parent, "${targetFile.name}.tmp")
        var existingTmpBytes = if (tmpFile.exists()) tmpFile.length() else 0L

        // If .tmp file is already larger than estimated size or corrupted, clear it
        if (existingTmpBytes > entry.estimatedBytes * 1.5 && entry.estimatedBytes > 0) {
            tmpFile.delete()
            existingTmpBytes = 0L
        }

        var response = fetcher.openStream(entry.downloadUrl, existingTmpBytes, authToken)
        if (response.statusCode == 416) {
            try { response.inputStream.close() } catch (_: Exception) {}
            tmpFile.delete()
            existingTmpBytes = 0L
            response = fetcher.openStream(entry.downloadUrl, 0L, authToken)
        }

        val statusCode = response.statusCode
        val inputStream = response.inputStream
        activeInputStream = inputStream

        val isAppend = (statusCode == 206)
        val initialDownloadedBytes = if (isAppend) existingTmpBytes else 0L
        var currentDownloadedBytes = initialDownloadedBytes

        val totalFileBytes = if (isAppend && response.contentLength > 0) {
            existingTmpBytes + response.contentLength
        } else if (response.contentLength > 0) {
            response.contentLength
        } else {
            entry.estimatedBytes
        }

        var lastSpeedCalcTime = System.currentTimeMillis()
        var bytesSinceLastCalc = 0L

        val fos = FileOutputStream(tmpFile, isAppend)
        activeFileOutputStream = fos

        try {
            inputStream.use { input ->
                fos.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        currentDownloadedBytes += bytesRead
                        bytesSinceLastCalc += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSpeedCalcTime
                        if (elapsed >= 500L) {
                            val currentSpeed = (bytesSinceLastCalc * 1000L) / elapsed
                            lastSpeedCalcTime = now
                            bytesSinceLastCalc = 0L

                            _state.value = ProvisioningState.Downloading(
                                profile = profile,
                                currentFileIndex = fileIndex,
                                totalFiles = totalFiles,
                                currentFileName = targetFile.name,
                                bytesDownloaded = currentDownloadedBytes,
                                totalBytesRequired = totalFileBytes,
                                speedBytesPerSec = currentSpeed
                            )
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            closeActiveStreams()
        }

        // Validate byte length
        val finalTmpLength = tmpFile.length()
        if (finalTmpLength < entry.minExpectedBytes) {
            tmpFile.delete()
            throw IOException("Downloaded file ${targetFile.name} was truncated ($finalTmpLength bytes < expected ${entry.minExpectedBytes} bytes)")
        }

        // Exact byte length verification (Requirement R4, P0-08)
        if (verifyChecksums && entry.exactBytes > 0L && finalTmpLength != entry.exactBytes) {
            tmpFile.delete()
            throw IOException("Downloaded file ${targetFile.name} size mismatch: got $finalTmpLength bytes, expected ${entry.exactBytes} bytes")
        }

        // Cryptographic SHA-256 verification (Requirement R4, P0-08)
        if (verifyChecksums && !entry.expectedSha256.isNullOrBlank()) {
            val actualSha256 = computeSha256(tmpFile)
            if (!actualSha256.equals(entry.expectedSha256, ignoreCase = true)) {
                tmpFile.delete()
                throw IOException("Integrity verification failed for ${targetFile.name}: expected SHA-256 ${entry.expectedSha256}, got $actualSha256")
            }
        }

        // Atomically rename .tmp to target
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val renamed = tmpFile.renameTo(targetFile)
        if (!renamed) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun importModelFromStream(
        inputStreamProvider: () -> InputStream?,
        targetFile: File,
        expectedSizeBytes: Long = -1L
    ): Result<AppConfig.ModelValidationReport> = withContext(Dispatchers.IO) {
        val fileName = targetFile.name
        val parent = targetFile.parentFile ?: config.modelsRootDir
        if (!parent.exists()) parent.mkdirs()
        val tmpFile = File(parent, "$fileName.tmp")

        try {
            val input = inputStreamProvider() ?: throw IOException("Could not open input stream for $fileName")
            input.use { inStream ->
                FileOutputStream(tmpFile, false).use { outStream ->
                    inStream.copyTo(outStream)
                    outStream.flush()
                }
            }
            if (targetFile.exists()) targetFile.delete()
            val renamed = tmpFile.renameTo(targetFile)
            if (!renamed) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }
            config.updateLlmConfig(config.llmConfig.value.copy(selectedModelPath = targetFile.absolutePath))
            val report = config.validateModelsOnDisk()
            Result.success(report)
        } catch (e: Exception) {
            if (tmpFile.exists()) tmpFile.delete()
            Result.failure(e)
        }
    }
}

