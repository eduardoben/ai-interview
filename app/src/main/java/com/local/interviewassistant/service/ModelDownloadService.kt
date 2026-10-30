package com.local.interviewassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.data.hardware.DeviceHardwareDetector
import com.local.interviewassistant.data.provisioning.ErrorReason
import com.local.interviewassistant.data.provisioning.ModelProvisioningManager
import com.local.interviewassistant.data.provisioning.ProvisioningState
import com.local.interviewassistant.ui.main.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground Service dedicated to performing uninterrupted model downloads
 * in the background, compliant with Android 17 (API 37) guidelines.
 *
 * Utilizes FOREGROUND_SERVICE_TYPE_DATA_SYNC, a partial WakeLock (4h timeout),
 * a high-perf WifiLock, and mandatory onTimeout() handling.
 */
class ModelDownloadService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var downloadJob: Job? = null
    private var lastNotificationUpdateTime = 0L

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        const val CHANNEL_ID = "model_downloads_channel"
        const val CHANNEL_NAME = "Model Downloads"
        const val NOTIFICATION_ID = 2002

        const val ACTION_DOWNLOAD_ALL = "com.local.interviewassistant.action.DOWNLOAD_ALL"
        const val ACTION_CANCEL = "com.local.interviewassistant.action.CANCEL_DOWNLOAD"

        // Legacy action aliases for backward compatibility
        const val ACTION_DOWNLOAD_SPEECH = "com.local.interviewassistant.action.DOWNLOAD_SPEECH"
        const val ACTION_DOWNLOAD_RESCORER = "com.local.interviewassistant.action.DOWNLOAD_RESCORER"
        const val ACTION_DOWNLOAD_LLM = "com.local.interviewassistant.action.DOWNLOAD_LLM"

        const val EXTRA_HF_TOKEN = "extra_hf_token"
        const val EXTRA_CUSTOM_URL = "extra_custom_url"

        fun startAllModelsDownload(context: Context, token: String? = null) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_ALL
                if (!token.isNullOrBlank()) {
                    putExtra(EXTRA_HF_TOKEN, token)
                }
            }
            context.startForegroundService(intent)
        }

        @Suppress("unused")
        fun startSpeechDownload(context: Context) {
            startAllModelsDownload(context)
        }

        @Suppress("unused")
        fun startRescorerDownload(context: Context) {
            startAllModelsDownload(context)
        }

        @Suppress("unused", "UNUSED_PARAMETER")
        fun startLlmDownload(context: Context, customUrl: String? = null, hfToken: String? = null) {
            startAllModelsDownload(context, hfToken)
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
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_DOWNLOAD_ALL,
            ACTION_DOWNLOAD_SPEECH,
            ACTION_DOWNLOAD_RESCORER,
            ACTION_DOWNLOAD_LLM -> {
                val token = intent.getStringExtra(EXTRA_HF_TOKEN)
                handleDownloadAll(token)
            }
            ACTION_CANCEL -> handleCancel()
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        releaseLocks()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        handleTimeout(startId)
    }

    fun handleTimeout(startId: Int) {
        downloadJob?.cancel()
        downloadJob = null
        val appContainer = AppContainer.getInstance(this)
        val profile = DeviceHardwareDetector.detectHardware(this)
        appContainer.provisioningManager.cancelOngoingDownload(
            profile = profile,
            reason = ErrorReason.SERVICE_TIMEOUT,
            message = "Download timed out by system limit. Partial progress is saved, tap Resume to continue."
        )
        showErrorNotification("Download timed out by system limit. Partial progress is saved.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun handleDownloadAll(token: String?) {
        startInForeground("Preparing AI models download...")
        val appContainer = AppContainer.getInstance(this)
        val profile = DeviceHardwareDetector.detectHardware(this)
        observeProgress(appContainer.provisioningManager)

        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            val result = appContainer.provisioningManager.downloadAllModels(
                profile = profile,
                hfBearerToken = token
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
        val profile = DeviceHardwareDetector.detectHardware(this)
        appContainer.provisioningManager.cancelOngoingDownload(profile)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observeProgress(manager: ModelProvisioningManager) {
        lifecycleScope.launch {
            manager.state.collectLatest { state ->
                when (state) {
                    is ProvisioningState.Downloading -> {
                        val now = System.currentTimeMillis()
                        // Throttle notification updates to at most once per 600ms
                        if (now - lastNotificationUpdateTime >= 600L || state.bytesDownloaded >= state.totalBytesRequired) {
                            lastNotificationUpdateTime = now
                            updateProgressNotification(state)
                        }
                    }
                    is ProvisioningState.Ready -> {
                        showCompletionNotification("All AI models downloaded & ready for inference!")
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    is ProvisioningState.Error -> {
                        val msg = if (state.canResume) "Download paused: ${state.message}" else "Download failed: ${state.message}"
                        showErrorNotification(msg)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    is ProvisioningState.NeedsDownload,
                    is ProvisioningState.Checking -> {
                        // Inactive/awaiting user action
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

    private fun updateProgressNotification(state: ProvisioningState.Downloading) {
        val downloadedMb = state.bytesDownloaded / (1024f * 1024f)
        val totalMb = if (state.totalBytesRequired > 0) state.totalBytesRequired / (1024f * 1024f) else 0f
        val percent = if (state.totalBytesRequired > 0) {
            ((state.bytesDownloaded * 100) / state.totalBytesRequired).toInt().coerceIn(0, 100)
        } else 0

        val speedMb = state.speedBytesPerSec / (1024f * 1024f)
        val fileCounter = "[${state.currentFileIndex}/${state.totalFiles}] "
        val sizeText = if (state.totalBytesRequired > 0) {
            String.format(Locale.US, "%.1f / %.1f MB (%d%%) • %.1f MB/s", downloadedMb, totalMb, percent, speedMb)
        } else {
            String.format(Locale.US, "%.1f MB • %.1f MB/s", downloadedMb, speedMb)
        }

        val text = "$fileCounter${state.currentFileName}: $sizeText"
        val notification = buildNotification(
            text = text,
            progress = percent,
            indeterminate = state.totalBytesRequired <= 0
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
            .setContentTitle("Downloading AI Pipeline Models")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Pause / Cancel", cancelIntent)
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
            .setContentTitle("AI Models Ready")
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
            .setContentTitle("Model Download Interrupted")
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

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AIInterviewAssistant:ModelDownloadWakeLock"
            ).apply {
                // 4-hour safety timeout to comply with Android 17 FGS boundaries
                acquire(4 * 60 * 60 * 1000L)
            }

            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "AIInterviewAssistant:ModelDownloadWifiLock"
            )?.apply {
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null

            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wifiLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
