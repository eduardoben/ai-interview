package com.local.interviewassistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.ui.overlay.FloatingHudOverlay
import com.local.interviewassistant.ui.theme.StealthOledTheme
import kotlinx.coroutines.launch

class AssistantForegroundService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.local.interviewassistant.action.START"
        const val ACTION_STOP = "com.local.interviewassistant.action.STOP"
        const val NOTIFICATION_CHANNEL_ID = "interview_assistant_channel"
        const val NOTIFICATION_ID = 1001
    }

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startAssistantService()
            ACTION_STOP -> stopAssistantService()
        }
        return START_NOT_STICKY
    }

    private fun startAssistantService() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Interview Assistant Active")
            .setContentText("Listening and generating answers...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        val appContainer = AppContainer.getInstance(this)
        val coordinator = appContainer.pipelineCoordinator
        appContainer.isServiceRunning.value = true

        lifecycleScope.launch {
            coordinator.start()
        }

        if (Settings.canDrawOverlays(this) && overlayView == null) {
            setupOverlay(appContainer)
        }
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
                visibility = if (isPrompterVisible) View.GONE else View.VISIBLE

                StealthOledTheme {
                    FloatingHudOverlay(
                        uiState = uiState,
                        onPauseToggle = { coordinator.togglePause() },
                        onForceAnswer = { coordinator.forceAnswerNow() },
                        onClear = { coordinator.clearCurrentAnswer() },
                        onSelectSegment = { segment -> coordinator.selectTranscriptSegmentForAnswer(segment) },
                        onSelectCategory = { category -> coordinator.selectCategory(category) },
                        onToggleMinimize = { coordinator.setMinimized(!uiState.isMinimized) },
                        onCloseService = { stopAssistantService() },
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

    private fun stopAssistantService() {
        val appContainer = AppContainer.getInstance(this)
        appContainer.isServiceRunning.value = false

        lifecycleScope.launch {
            appContainer.pipelineCoordinator.stop()

            overlayView?.let {
                try {
                    windowManager?.removeView(it)
                } catch (_: Exception) {}
            }
            overlayView = null
            overlayLifecycleOwner?.onDestroy()
            overlayLifecycleOwner = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopAssistantService()
        super.onDestroy()
    }
}
