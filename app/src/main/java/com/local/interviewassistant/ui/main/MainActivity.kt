package com.local.interviewassistant.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
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
import com.local.interviewassistant.data.hardware.DeviceHardwareDetector
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.data.provisioning.ProvisioningState
import com.local.interviewassistant.service.AssistantForegroundService
import com.local.interviewassistant.service.ModelDownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var readinessStatus by mutableStateOf<AppContainer.ReadinessStatus?>(null)
    private var hardwareProfile by mutableStateOf<HardwareProfile?>(null)
    private var isMeteredNetwork by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
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
                val target = appContainer.appConfig.activeLlmModelFile
                appContainer.provisioningManager.importModelFromStream(
                    inputStreamProvider = { contentResolver.openInputStream(it) },
                    targetFile = target
                )
                refreshStatus()
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
                    if (state is ProvisioningState.Ready) {
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
                        hardwareProfile = hardwareProfile,
                        isServiceRunning = isServiceRunning,
                        provisioningState = provisioningState,
                        llmConfig = llmConfig,
                        isMeteredNetwork = isMeteredNetwork,
                        onUpdateLlmConfig = { appContainer.saveLlmConfig(it) },
                        onGrantPermissionsClick = { requestBasePermissions() },
                        onOpenOverlaySettingsClick = { requestOverlayPermission() },
                        onRefreshDiskCheckClick = { refreshStatus() },
                        onStartUnifiedDownloadClick = { downloadAllModels() },
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

        val profile = DeviceHardwareDetector.detectHardware(this)
        hardwareProfile = profile

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        isMeteredNetwork = cm?.isActiveNetworkMetered ?: false

        val appContainer = AppContainer.getInstance(this)
        appContainer.provisioningManager.checkProvisioningStatus(profile)

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

    private fun downloadAllModels() {
        ModelDownloadService.startAllModelsDownload(this)
    }

    private fun cancelOngoingProvisioning() {
        ModelDownloadService.cancelDownload(this)
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
