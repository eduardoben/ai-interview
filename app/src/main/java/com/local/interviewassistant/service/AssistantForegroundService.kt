package com.local.interviewassistant.service

import android.app.NotificationChannel
import android.app.NotificationManager

import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat

import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.local.interviewassistant.ui.overlay.FloatingHudOverlay
import kotlinx.coroutines.launch

class AssistantForegroundService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.local.interviewassistant.action.START"
        const val ACTION_STOP = "com.local.interviewassistant.action.STOP"
        const val NOTIFICATION_CHANNEL_ID = "interview_assistant_channel"
        const val NOTIFICATION_ID = 1001
    }

    private var coordinator: AssistantPipelineCoordinator? = null
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

        if (!Settings.canDrawOverlays(this)) {
            return // Or we could show an error, but the app should check this beforehand
        }

        val appContainer = com.local.interviewassistant.core.di.AppContainer.getInstance(this)
        
        coordinator = appContainer.createPipelineCoordinator(lifecycleScope)

        lifecycleScope.launch {
            coordinator?.start()
        }

        setupOverlay()
    }

    private fun setupOverlay() {
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

        overlayLifecycleOwner = OverlayLifecycleOwner()
        overlayLifecycleOwner?.onCreate()

        overlayView = ComposeView(this).apply {
            overlayLifecycleOwner?.attachToView(this)
            setContent {
                val uiState = coordinator?.copilotUiState?.collectAsState()?.value ?: com.local.interviewassistant.core.model.CopilotUiState()
                FloatingHudOverlay(
                    uiState = uiState,
                    onPauseToggle = { coordinator?.togglePause() },
                    onForceAnswer = { coordinator?.forceAnswerNow() },
                    onClear = { coordinator?.clearCurrentAnswer() },
                    onSelectSegment = { segment -> coordinator?.selectTranscriptSegmentForAnswer(segment) },
                    onSelectCategory = { category -> coordinator?.selectCategory(category) },
                    onToggleMinimize = { coordinator?.setMinimized(!uiState.isMinimized) },
                    onCloseService = { stopAssistantService() },
                    onDrag = { dx, dy ->
                        layoutParams.x += dx.toInt()
                        layoutParams.y += dy.toInt()
                        windowManager?.updateViewLayout(this, layoutParams)
                    },
                )
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
        lifecycleScope.launch {
            coordinator?.stop()
            coordinator = null
            
            overlayView?.let {
                windowManager?.removeView(it)
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
