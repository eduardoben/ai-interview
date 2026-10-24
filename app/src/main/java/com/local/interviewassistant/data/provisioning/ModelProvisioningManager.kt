package com.local.interviewassistant.data.provisioning

import com.local.interviewassistant.core.model.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

sealed interface ProvisioningState {
    data object Idle : ProvisioningState
    data class InProgress(
        val currentFileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val completedFilesCount: Int,
        val totalFilesCount: Int
    ) : ProvisioningState
    data class Completed(val report: AppConfig.ModelValidationReport) : ProvisioningState
    data class Error(val fileName: String, val message: String) : ProvisioningState
}

fun interface StreamFetcher {
    fun openStream(url: String, authToken: String?, onTotalBytes: (Long) -> Unit): InputStream
}

class HttpUrlStreamFetcher : StreamFetcher {
    override fun openStream(url: String, authToken: String?, onTotalBytes: (Long) -> Unit): InputStream {
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

            if (!authToken.isNullOrEmpty()) {
                val currentHost = try { u.toURI().host } catch (_: Exception) { null }
                if (redirectCount == 0 || (initialHost != null && initialHost.equals(currentHost, ignoreCase = true))) {
                    if (u.protocol.equals("http", ignoreCase = true)) {
                        throw IOException("Insecure connection: Cannot send bearer token over HTTP")
                    }
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

            if (code >= 400) {
                connection.disconnect()
                throw IOException("HTTP error code: $code downloading $url")
            }

            if (code !in 200..299) {
                connection.disconnect()
                throw IOException("HTTP download failed with code $code for URL: $url")
            }

            val contentLength = connection.contentLengthLong
            onTotalBytes(if (contentLength > 0) contentLength else -1L)
            return connection.inputStream
        }
    }
}

class ModelProvisioningManager(
    private val config: AppConfig,
    private val fetcher: StreamFetcher = HttpUrlStreamFetcher()
) {
    private val _state = MutableStateFlow<ProvisioningState>(ProvisioningState.Idle)
    val state: StateFlow<ProvisioningState> = _state.asStateFlow()

    fun resetState() {
        _state.value = ProvisioningState.Idle
    }

    suspend fun downloadAllModels(
        hfBearerToken: String? = null
    ): Result<AppConfig.ModelValidationReport> = withContext(Dispatchers.IO) {
        val allModels = listOf(
            config.vadModelFile to AppConfig.VAD_URL,
            config.sttEncoderFile to AppConfig.STT_ENCODER_URL,
            config.sttDecoderFile to AppConfig.STT_DECODER_URL,
            config.sttJoinerFile to AppConfig.STT_JOINER_URL,
            config.sttTokensFile to AppConfig.STT_TOKENS_URL,
            config.activeLlmModelFile to AppConfig.DEFAULT_LLM_URL
        )

        val missingModels = allModels.filter { (file, _) -> !file.exists() || file.length() <= 0L }
        if (missingModels.isEmpty()) {
            val report = config.validateModelsOnDisk()
            _state.value = ProvisioningState.Completed(report)
            return@withContext Result.success(report)
        }

        val totalCount = missingModels.size
        for ((index, pair) in missingModels.withIndex()) {
            val (targetFile, url) = pair
            val fileName = targetFile.name
            try {
                downloadOrCopyToFile(
                    fileName = fileName,
                    targetFile = targetFile,
                    completedFilesCount = index,
                    totalFilesCount = totalCount
                ) { onTotalBytes ->
                    fetcher.openStream(url, hfBearerToken, onTotalBytes)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val errorMsg = e.message ?: "Failed to download $fileName"
                _state.value = ProvisioningState.Error(fileName, errorMsg)
                return@withContext Result.failure(e)
            }
        }

        val report = config.validateModelsOnDisk()
        _state.value = ProvisioningState.Completed(report)
        Result.success(report)
    }

    suspend fun downloadMissingSpeechModels(forceUpgrade: Boolean = false): Result<AppConfig.ModelValidationReport> = withContext(Dispatchers.IO) {
        val speechModels = listOf(
            config.vadModelFile to AppConfig.VAD_URL,
            config.sttEncoderFile to AppConfig.STT_ENCODER_URL,
            config.sttDecoderFile to AppConfig.STT_DECODER_URL,
            config.sttJoinerFile to AppConfig.STT_JOINER_URL,
            config.sttTokensFile to AppConfig.STT_TOKENS_URL
        )

        if (forceUpgrade) {
            speechModels.forEach { (file, _) -> try { file.delete() } catch (_: Exception) {} }
        }

        val missingModels = speechModels.filter { (file, _) -> !file.exists() || file.length() <= 0L }
        if (missingModels.isEmpty()) {
            val report = config.validateModelsOnDisk()
            _state.value = ProvisioningState.Completed(report)
            return@withContext Result.success(report)
        }

        val totalCount = missingModels.size
        for ((index, pair) in missingModels.withIndex()) {
            val (targetFile, url) = pair
            val fileName = targetFile.name
            try {
                downloadOrCopyToFile(
                    fileName = fileName,
                    targetFile = targetFile,
                    completedFilesCount = index,
                    totalFilesCount = totalCount
                ) { onTotalBytes ->
                    fetcher.openStream(url, null, onTotalBytes)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val errorMsg = e.message ?: "Failed to download $fileName"
                _state.value = ProvisioningState.Error(fileName, errorMsg)
                return@withContext Result.failure(e)
            }
        }

        val report = config.validateModelsOnDisk()
        _state.value = ProvisioningState.Completed(report)
        Result.success(report)
    }

    suspend fun downloadLlmModel(
        customUrl: String = AppConfig.DEFAULT_LLM_URL,
        hfBearerToken: String? = null
    ): Result<AppConfig.ModelValidationReport> = withContext(Dispatchers.IO) {
        val filenameFromUrl = try {
            val uriPath = java.net.URI(customUrl).path
            val lastSegment = uriPath.substringAfterLast('/')
            if (lastSegment.isNotBlank() && (lastSegment.endsWith(".litertlm", ignoreCase = true) || lastSegment.endsWith(".bin", ignoreCase = true) || lastSegment.endsWith(".task", ignoreCase = true))) {
                lastSegment
            } else null
        } catch (_: Exception) { null }

        val targetFile = if (filenameFromUrl != null) {
            File(config.modelsRootDir, filenameFromUrl)
        } else {
            config.activeLlmModelFile
        }

        val fileName = targetFile.name
        try {
            downloadOrCopyToFile(
                fileName = fileName,
                targetFile = targetFile,
                completedFilesCount = 0,
                totalFilesCount = 1
            ) { onTotalBytes ->
                fetcher.openStream(customUrl, hfBearerToken, onTotalBytes)
            }

            // Automatically set the downloaded model as the active selected model
            config.updateLlmConfig(
                config.llmConfig.value.copy(selectedModelPath = targetFile.absolutePath)
            )

            val report = config.validateModelsOnDisk()
            _state.value = ProvisioningState.Completed(report)
            Result.success(report)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val errorMsg = e.message ?: "Failed to download $fileName"
            _state.value = ProvisioningState.Error(fileName, errorMsg)
            Result.failure(e)
        }
    }

    suspend fun importModelFromStream(
        inputStreamProvider: () -> InputStream?,
        targetFile: File,
        expectedSizeBytes: Long = -1L
    ): Result<AppConfig.ModelValidationReport> = withContext(Dispatchers.IO) {
        val fileName = targetFile.name
        try {
            downloadOrCopyToFile(
                fileName = fileName,
                targetFile = targetFile,
                completedFilesCount = 0,
                totalFilesCount = 1
            ) { onTotalBytes ->
                onTotalBytes(expectedSizeBytes)
                inputStreamProvider() ?: throw IOException("Could not open input stream for $fileName")
            }
            config.updateLlmConfig(
                config.llmConfig.value.copy(selectedModelPath = targetFile.absolutePath)
            )
            val report = config.validateModelsOnDisk()
            _state.value = ProvisioningState.Completed(report)
            Result.success(report)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val errorMsg = e.message ?: "Failed to import $fileName"
            _state.value = ProvisioningState.Error(fileName, errorMsg)
            Result.failure(e)
        }
    }

    private suspend fun downloadOrCopyToFile(
        fileName: String,
        targetFile: File,
        completedFilesCount: Int,
        totalFilesCount: Int,
        streamSupplier: (onTotalBytes: (Long) -> Unit) -> InputStream
    ) {
        if (!config.ensureDirectoriesExist()) {
            throw IOException("Failed to create or write to models directory: ${config.modelsRootDir.absolutePath}")
        }

        val partFile = File(config.modelsRootDir, "${targetFile.name}.part")
        if (partFile.exists()) {
            partFile.delete()
        }

        var totalBytes = -1L
        var downloadedBytes = 0L

        try {
            val inputStream = streamSupplier { bytes -> totalBytes = bytes }
            _state.value = ProvisioningState.InProgress(
                currentFileName = fileName,
                downloadedBytes = 0L,
                totalBytes = totalBytes,
                completedFilesCount = completedFilesCount,
                totalFilesCount = totalFilesCount
            )

            val buffer = ByteArray(64 * 1024)
            inputStream.use { input ->
                partFile.outputStream().use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        _state.value = ProvisioningState.InProgress(
                            currentFileName = fileName,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            completedFilesCount = completedFilesCount,
                            totalFilesCount = totalFilesCount
                        )
                    }
                    output.flush()
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            val renamed = partFile.renameTo(targetFile)
            if (!renamed) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }
        } catch (e: Exception) {
            if (partFile.exists()) {
                partFile.delete()
            }
            throw e
        }
    }
}
