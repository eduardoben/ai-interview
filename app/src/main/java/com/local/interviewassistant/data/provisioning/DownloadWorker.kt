package com.local.interviewassistant.data.provisioning

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.flow.firstOrNull

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val entry = ModelAllowlist.getModelById(modelId) ?: return Result.failure()
        
        val hfClient = HuggingFaceApiClient(applicationContext)
        val token = hfClient.tokenFlow.firstOrNull()

        setForeground(createForegroundInfo(entry.name, 0))

        val targetDir = File(applicationContext.filesDir, "models")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        val targetFile = File(targetDir, "${entry.name.replace(" ", "_")}.litertlm")
        val downloader = LiteRtLmModelDownloader()

        return try {
            downloader.downloadModelAtomic(
                url = entry.url,
                targetFile = targetFile,
                expectedBytes = entry.expectedBytes,
                authToken = token,
                onProgress = { bytesDownloaded, totalBytes ->
                    val progress = if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes).toInt() else 0
                    setProgressAsync(workDataOf("progress" to progress))
                    // Could update notification here, but rate limit it
                }
            )
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry() // Enable exponential backoff retry on network failure
        }
    }

    private fun createForegroundInfo(modelName: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, "download_channel")
            .setContentTitle("Downloading Model")
            .setContentText("Downloading $modelName ($progress%)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
            
        return ForegroundInfo(
            NOTIFICATION_ID, 
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
