package com.local.interviewassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.local.interviewassistant.R
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.data.provisioning.ModelProvisioningManager
import com.local.interviewassistant.data.provisioning.ProvisioningState
import com.local.interviewassistant.ui.main.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground Service dedicated to performing uninterrupted model downloads
 * in the background, even when the device screen is turned off or locked.
 *
 * Utilizes FOREGROUND_SERVICE_TYPE_DATA_SYNC and a partial WakeLock to prevent
 * Android Doze mode and CPU suspension from dropping active network sockets.
 */
class ModelDownloadService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var downloadJob: Job? = null
    private var lastNotificationUpdateTime = 0L

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        const val CHANNEL_ID = "model_downloads_channel"
        const val CHANNEL_NAME = "Model Downloads"
        const val NOTIFICATION_ID = 2002

        const val ACTION_DOWNLOAD_SPEECH = "com.local.interviewassistant.action.DOWNLOAD_SPEECH"
        const val ACTION_DOWNLOAD_RESCORER = "com.local.interviewassistant.action.DOWNLOAD_RESCORER"
        const val ACTION_DOWNLOAD_LLM = "com.local.interviewassistant.action.DOWNLOAD_LLM"
        const val ACTION_CANCEL = "com.local.interviewassistant.action.CANCEL_DOWNLOAD"

        const val EXTRA_CUSTOM_URL = "extra_custom_url"
        const val EXTRA_HF_TOKEN = "extra_hf_token"

        fun startSpeechDownload(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_SPEECH
            }
            context.startForegroundService(intent)
        }

        fun startRescorerDownload(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_RESCORER
            }
            context.startForegroundService(intent)
        }

        fun startLlmDownload(context: Context, customUrl: String, hfToken: String?) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_LLM
                putExtra(EXTRA_CUSTOM_URL, customUrl)
                putExtra(EXTRA_HF_TOKEN, hfToken)
            }
            context.startForegroundService(intent)
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_DOWNLOAD_SPEECH -> handleDownloadSpeech()
            ACTION_DOWNLOAD_RESCORER -> handleDownloadRescorer()
            ACTION_DOWNLOAD_LLM -> {
                val url = intent.getStringExtra(EXTRA_CUSTOM_URL) ?: AppConfig.DEFAULT_LLM_URL
                val token = intent.getStringExtra(EXTRA_HF_TOKEN)
                handleDownloadLlm(url, token)
            }
            ACTION_CANCEL -> handleCancel()
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun handleDownloadSpeech() {
        startInForeground("Preparing speech models download...")
        val appContainer = AppContainer.getInstance(this)
        observeProgress(appContainer.provisioningManager)

        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            appContainer.provisioningManager.downloadMissingSpeechModels()
        }
    }

    private fun handleDownloadRescorer() {
        startInForeground("Preparing Whisper rescorer download...")
        val appContainer = AppContainer.getInstance(this)
        observeProgress(appContainer.provisioningManager)

        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            appContainer.provisioningManager.downloadOfflineRescorerModel()
        }
    }

    private fun handleDownloadLlm(url: String, token: String?) {
        startInForeground("Preparing LLM model download...")
        val appContainer = AppContainer.getInstance(this)
        observeProgress(appContainer.provisioningManager)

        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            val result = appContainer.provisioningManager.downloadLlmModel(
                customUrl = url.ifBlank { AppConfig.DEFAULT_LLM_URL },
                hfBearerToken = token?.ifBlank { null }
            )
            if (result.isSuccess) {
                appContainer.saveLlmConfig(appContainer.appConfig.llmConfig.value)
            }
        }
    }

    private fun handleCancel() {
        downloadJob?.cancel()
        downloadJob = null
        val appContainer = AppContainer.getInstance(this)
        appContainer.provisioningManager.resetState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observeProgress(manager: ModelProvisioningManager) {
        lifecycleScope.launch {
            manager.state.collectLatest { state ->
                when (state) {
                    is ProvisioningState.InProgress -> {
                        val now = System.currentTimeMillis()
                        // Throttle notification updates to at most once per 600ms to avoid system IPC overhead
                        if (now - lastNotificationUpdateTime >= 600L || state.downloadedBytes == state.totalBytes) {
                            lastNotificationUpdateTime = now
                            updateProgressNotification(state)
                        }
                    }
                    is ProvisioningState.Completed -> {
                        showCompletionNotification("All models downloaded successfully!")
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    is ProvisioningState.Error -> {
                        showErrorNotification("Download failed: ${state.message}")
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    is ProvisioningState.Idle -> {
                        // No active download
                    }
                }
            }
        }
    }

    private fun startInForeground(initialText: String) {
        val notification = buildNotification(initialText, progress = 0, indeterminate = true)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun updateProgressNotification(state: ProvisioningState.InProgress) {
        val downloadedMb = state.downloadedBytes / (1024f * 1024f)
        val totalMb = if (state.totalBytes > 0) state.totalBytes / (1024f * 1024f) else 0f
        val percent = if (state.totalBytes > 0) {
            ((state.downloadedBytes * 100) / state.totalBytes).toInt()
        } else 0

        val fileCounter = if (state.totalFilesCount > 1) {
            "[${state.completedFilesCount + 1}/${state.totalFilesCount}] "
        } else ""

        val sizeText = if (state.totalBytes > 0) {
            String.format(Locale.US, "%.1f / %.1f MB (%d%%)", downloadedMb, totalMb, percent)
        } else {
            String.format(Locale.US, "%.1f MB", downloadedMb)
        }

        val text = "$fileCounter${state.currentFileName}: $sizeText"
        val notification = buildNotification(
            text = text,
            progress = percent,
            indeterminate = state.totalBytes <= 0
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading AI Models")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
    }

    private fun showCompletionNotification(message: String) {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Model Download Complete")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(error: String) {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Model Download Failed")
            .setContentText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for background model downloads"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AIInterviewAssistant:ModelDownloadWakeLock"
            ).apply {
                // 2-hour safety timeout to avoid battery drain if network hangs
                acquire(2 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
