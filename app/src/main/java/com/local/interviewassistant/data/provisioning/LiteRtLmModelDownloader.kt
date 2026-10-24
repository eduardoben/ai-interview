package com.local.interviewassistant.data.provisioning

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Handles the physical downloading and atomic saving of the .litertlm file.
 */
class LiteRtLmModelDownloader(private val fetcher: StreamFetcher = HttpUrlStreamFetcher()) {
    
    suspend fun downloadModelAtomic(
        url: String,
        targetFile: File,
        expectedBytes: Long,
        authToken: String?,
        onProgress: suspend (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        var actualTotalBytes = -1L
        
        try {
            val inputStream = fetcher.openStream(url, authToken) { reportedTotal ->
                actualTotalBytes = reportedTotal
            }
            
            var downloadedBytes = 0L
            val buffer = ByteArray(128 * 1024)
            
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // Callback to report progress
                        val totalToReport = if (expectedBytes > 0) expectedBytes else actualTotalBytes
                        onProgress(downloadedBytes, totalToReport)
                    }
                    output.flush()
                }
            }
            
            // Checksum / Size validation before atomic rename
            if (expectedBytes > 0 && tempFile.length() != expectedBytes) {
                throw IOException("Download corrupted: expected $expectedBytes bytes, but got ${tempFile.length()}")
            }
            
            // Atomic rename
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val success = tempFile.renameTo(targetFile)
            if (!success) {
                throw IOException("Failed to rename temporary file to ${targetFile.name}")
            }
            
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }
}
