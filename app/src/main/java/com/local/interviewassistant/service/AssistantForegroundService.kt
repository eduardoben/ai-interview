package com.local.interviewassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.ui.overlay.FloatingHudOverlay
import com.local.interviewassistant.ui.theme.StealthOledTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AssistantForegroundService : LifecycleService() {

    companion object {
        private const val TAG = "AssistantFGS"
        const val ACTION_START = "com.local.interviewassistant.action.START"
        const val ACTION_STOP = "com.local.interviewassistant.action.STOP"
        const val NOTIFICATION_CHANNEL_ID = "interview_assistant_channel"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_STOP = 2001
    }

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    // Milestone 2.4: Partial WakeLock to guarantee uninterrupted audio ingestion
    private var wakeLock: PowerManager.WakeLock? = null

    // Milestone 2.3: Thermal listener to throttle LLM and STT under thermal load
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    private val isStopping = AtomicBoolean(false)
    private val isTornDown = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startAssistantService()
            ACTION_STOP -> stopAssistantServiceSafely()
        }
        return START_NOT_STICKY
    }

    private fun startAssistantService() {
        isStopping.set(false)
        isTornDown.set(false)

        createNotificationChannel()
        val notification = buildOngoingNotification()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        val appContainer = AppContainer.getInstance(this)
        val coordinator = appContainer.pipelineCoordinator
        appContainer.isServiceRunning.value = true

        acquireWakeLock()
        registerThermalListener(coordinator)

        lifecycleScope.launch {
            coordinator.start()
        }

        // REC-3: Adaptive WakeLock release on pause to conserve battery
        lifecycleScope.launch {
            coordinator.state.collect { state ->
                if (state is com.local.interviewassistant.core.model.AssistantState.Paused) {
                    if (wakeLock?.isHeld == true) {
                        try { wakeLock?.release() } catch (_: Exception) {}
                    }
                } else if (state is com.local.interviewassistant.core.model.AssistantState.Listening ||
                    state is com.local.interviewassistant.core.model.AssistantState.DetectingSpeech
                ) {
                    if (wakeLock == null || wakeLock?.isHeld != true) {
                        acquireWakeLock()
                    }
                }
            }
        }

        if (Settings.canDrawOverlays(this) && overlayView == null) {
            setupOverlay(appContainer)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val stopIntent = Intent(this, AssistantForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AI Interview Assistant Active")
            .setContentText("Transcribing spoken audio and generating prompt suggestions.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_pause, "Stop Assistant", stopPendingIntent)
            .build()
    }

    private fun setupOverlay(appContainer: AppContainer) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 24
        }

        val coordinator = appContainer.pipelineCoordinator
        overlayLifecycleOwner = OverlayLifecycleOwner()
        overlayLifecycleOwner?.onCreate()

        overlayView = ComposeView(this).apply {
            overlayLifecycleOwner?.attachToView(this)
            setContent {
                val uiState = coordinator.copilotUiState.collectAsState().value
                val isPrompterVisible = appContainer.isPrompterScreenVisible.collectAsState().value

                // Stealth ergonomics: Auto-hide floating overlay when user is on full-screen Prompter inside the app
                LaunchedEffect(isPrompterVisible) {
                    visibility = if (isPrompterVisible) View.GONE else View.VISIBLE
                }

                StealthOledTheme {
                    FloatingHudOverlay(
                        uiState = uiState,
                        onPauseToggle = { coordinator.togglePause() },
                        onForceAnswer = { coordinator.forceAnswerNow() },
                        onClear = { coordinator.clearCurrentAnswer() },
                        onSelectSegment = { segment -> coordinator.selectTranscriptSegmentForAnswer(segment) },
                        onSelectCategory = { category -> coordinator.selectCategory(category) },
                        onToggleMinimize = { coordinator.setMinimized(!uiState.isMinimized) },
                        onCloseService = { stopAssistantServiceSafely() },
                        onDrag = { dx, dy ->
                            layoutParams.x += dx.toInt()
                            layoutParams.y += dy.toInt()
                            windowManager?.updateViewLayout(this, layoutParams)
                        },
                    )
                }
            }
        }

        overlayLifecycleOwner?.attachToView(overlayView!!)
        windowManager?.addView(overlayView, layoutParams)
        overlayLifecycleOwner?.onResume()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Interview Assistant Service",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun teardownOverlayView() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        overlayLifecycleOwner?.onDestroy()
        overlayLifecycleOwner = null
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AIInterviewAssistant:AudioCaptureLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L) // 2 hour max safety timeout
            }
            Log.d(TAG, "Audio WakeLock acquired.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire audio WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Audio WakeLock released.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio WakeLock", e)
        } finally {
            wakeLock = null
        }
    }

    private fun registerThermalListener(coordinator: AssistantPipelineCoordinator) {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                Log.i(TAG, "Thermal status changed: $status")
                coordinator.onThermalStatusChanged(status)
            }
            thermalListener = listener
            powerManager.addThermalStatusListener(mainExecutor, listener)
            val initialStatus = powerManager.currentThermalStatus
            coordinator.onThermalStatusChanged(initialStatus)
            Log.d(TAG, "Thermal listener registered, initial status: $initialStatus")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register thermal status listener", e)
        }
    }

    private fun unregisterThermalListener() {
        try {
            val listener = thermalListener ?: return
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
            powerManager?.removeThermalStatusListener(listener)
            Log.d(TAG, "Thermal listener unregistered.")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering thermal listener", e)
        } finally {
            thermalListener = null
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.i(TAG, "onTrimMemory received level: $level")
        AppContainer.getInstance(this).onTrimMemory(level)
    }

    private fun stopAssistantServiceSafely() {
        if (!isStopping.compareAndSet(false, true)) {
            Log.d(TAG, "Teardown already in progress, ignoring duplicate call.")
            return
        }

        unregisterThermalListener()
        releaseWakeLock()

        val appContainer = AppContainer.getInstance(this)
        appContainer.isServiceRunning.value = false

        // Retain FOREGROUND_SERVICE priority while async JNI cleanup executes on supervisor scope
        appContainer.appScope.launch {
            withContext(NonCancellable) {
                try {
                    Log.d(TAG, "Halting AssistantPipelineCoordinator...")
                    appContainer.pipelineCoordinator.stop()
                } catch (t: Throwable) {
                    Log.e(TAG, "Error stopping pipeline coordinator", t)
                }

                withContext(Dispatchers.Main) {
                    teardownOverlayView()
                    isTornDown.set(true)
                    Log.d(TAG, "Overlay removed. Stopping foreground and calling stopSelf().")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterThermalListener()
        releaseWakeLock()

        // Fallback guard if onDestroy was triggered directly by OS
        if (isTornDown.compareAndSet(false, true)) {
            val appContainer = AppContainer.getInstance(this)
            appContainer.isServiceRunning.value = false
            teardownOverlayView()
            appContainer.appScope.launch {
                withContext(NonCancellable) {
                    runCatching { appContainer.pipelineCoordinator.stop() }
                        .onFailure { Log.e(TAG, "Error in emergency JNI teardown", it) }
                }
            }
        }
        super.onDestroy()
    }
}
