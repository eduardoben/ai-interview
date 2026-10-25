package com.local.interviewassistant.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.data.provisioning.ProvisioningState
import com.local.interviewassistant.service.AssistantForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var readinessStatus by mutableStateOf<AppContainer.ReadinessStatus?>(null)
    private var isServiceRunning by mutableStateOf(value = false)
    private var downloadJob: Job? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshStatus()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStatus()
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            downloadJob?.cancel()
            downloadJob = lifecycleScope.launch {
                val appContainer = AppContainer.getInstance(this@MainActivity)
                appContainer.provisioningManager.importModelFromStream(
                    inputStreamProvider = { contentResolver.openInputStream(it) },
                    targetFile = appContainer.appConfig.llmModelFile
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshStatus()
                }
            }
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppContainer.getInstance(this@MainActivity).provisioningManager.state.collect { state ->
                    if (state is ProvisioningState.Completed) {
                        refreshStatus()
                    }
                }
            }
        }

        setContent {
            val appContainer = AppContainer.getInstance(this)
            val provisioningState by appContainer.provisioningManager.state.collectAsStateWithLifecycle()
            val llmConfig by appContainer.appConfig.llmConfig.collectAsStateWithLifecycle()

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        readiness = readinessStatus,
                        isServiceRunning = isServiceRunning,
                        provisioningState = provisioningState,
                        llmConfig = llmConfig,
                        onUpdateLlmConfig = { appContainer.saveLlmConfig(it) },
                        onGrantPermissionsClick = { requestBasePermissions() },
                        onOpenOverlaySettingsClick = { requestOverlayPermission() },
                        onRefreshDiskCheckClick = { refreshStatus() },
                        onDownloadSpeechModelsClick = { downloadSpeechModels() },
                        onDownloadOfflineRescorerClick = { downloadOfflineRescorer() },
                        onDownloadLlmModelClick = { customUrl, token -> downloadLlmModel(customUrl, token) },
                        onImportLlmFileClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                        onCancelDownloadClick = { cancelOngoingProvisioning() },
                        onStartAssistantClick = { startAssistantService() },
                        onStopAssistantClick = { stopAssistantService() }
                    )
                }
            }
        }
    }

    private fun refreshStatus() {
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasNotification = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val hasOverlay = Settings.canDrawOverlays(this)

        val appContainer = AppContainer.getInstance(this)
        val modelReport = appContainer.appConfig.validateModelsOnDisk()

        readinessStatus = AppContainer.ReadinessStatus(
            hasAudioPermission = hasAudio,
            hasNotificationPermission = hasNotification,
            hasOverlayPermission = hasOverlay,
            modelReport = modelReport
        )
    }

    private fun requestBasePermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun downloadSpeechModels() {
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            AppContainer.getInstance(this@MainActivity).provisioningManager.downloadMissingSpeechModels()
        }
    }

    private fun downloadOfflineRescorer() {
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            AppContainer.getInstance(this@MainActivity).provisioningManager.downloadOfflineRescorerModel()
            refreshStatus()
        }
    }

    private fun downloadLlmModel(customUrl: String, hfToken: String?) {
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            val appContainer = AppContainer.getInstance(this@MainActivity)
            val result = appContainer.provisioningManager.downloadLlmModel(
                customUrl = customUrl.ifBlank { AppConfig.DEFAULT_LLM_URL },
                hfBearerToken = hfToken?.ifBlank { null }
            )
            if (result.isSuccess) {
                appContainer.saveLlmConfig(appContainer.appConfig.llmConfig.value)
                refreshStatus()
            }
        }
    }

    private fun cancelOngoingProvisioning() {
        downloadJob?.cancel()
        downloadJob = null
        AppContainer.getInstance(this).provisioningManager.resetState()
    }

    private fun startAssistantService() {
        val intent = Intent(this, AssistantForegroundService::class.java).apply {
            action = AssistantForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        isServiceRunning = true
    }

    private fun stopAssistantService() {
        val intent = Intent(this, AssistantForegroundService::class.java).apply {
            action = AssistantForegroundService.ACTION_STOP
        }
        startService(intent)
        isServiceRunning = false
    }
}
