package com.local.interviewassistant.data.provisioning

import android.app.NotificationChannel
import android.app.NotificationManager
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
        private const val CHANNEL_ID = "download_channel"
        private const val CHANNEL_NAME = "Model Downloads"
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

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("Model", 0)

    private fun ensureNotificationChannel() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(modelName: String, progress: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
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
