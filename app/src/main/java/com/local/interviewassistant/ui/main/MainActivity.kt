package com.local.interviewassistant.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.local.interviewassistant.ui.prompter.PrompterViewModel
import com.local.interviewassistant.ui.settings.SettingsViewModel
import com.local.interviewassistant.ui.theme.StealthOledTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

    private var readinessStatus by mutableStateOf<AppContainer.ReadinessStatus?>(null)
    private var hardwareProfile by mutableStateOf<HardwareProfile?>(null)
    private var isMeteredNetwork by mutableStateOf(false)
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

        // Android 17 Edge-to-Edge & Lockscreen Display
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    AppContainer.getInstance(this@MainActivity).isPrompterScreenVisible.value = true
                    refreshStatus()
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    AppContainer.getInstance(this@MainActivity).isPrompterScreenVisible.value = false
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
            val prompterViewModel = remember {
                PrompterViewModel(
                    coordinator = appContainer.pipelineCoordinator,
                    preferencesRepository = appContainer.userPreferencesRepository,
                    isServiceRunningFlow = appContainer.isServiceRunning,
                    hardwareSocName = hardwareProfile?.socName,
                    onStartServiceCallback = { startAssistantService() },
                    onStopServiceCallback = { stopAssistantService() }
                )
            }

            val settingsViewModel = remember {
                SettingsViewModel(
                    preferencesRepository = appContainer.userPreferencesRepository,
                    initialHardwareProfile = hardwareProfile,
                    initialConfig = appContainer.appConfig.llmConfig.value
                )
            }

            LaunchedEffect(hardwareProfile) {
                settingsViewModel.setHardwareProfile(hardwareProfile)
            }
            val provisioningState by appContainer.provisioningManager.state.collectAsStateWithLifecycle()
            val llmConfig by appContainer.appConfig.llmConfig.collectAsStateWithLifecycle()

            val historyRecords by appContainer.sessionHistoryRepository.records.collectAsStateWithLifecycle()
            val telemetry by appContainer.sessionHistoryRepository.telemetry.collectAsStateWithLifecycle()


            // Dynamic WakeLock binding
            if (llmConfig.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            StealthOledTheme {
                AppShell(
                    prompterViewModel = prompterViewModel,
                    settingsViewModel = settingsViewModel,


                    readiness = readinessStatus,

                    provisioningState = provisioningState,
                    isMeteredNetwork = isMeteredNetwork,
                    telemetry = telemetry,
                    historyRecords = historyRecords,









                    onExportMarkdown = { appContainer.sessionHistoryRepository.exportToMarkdown() },
                    onClearHistory = {
                        lifecycleScope.launch {
                            appContainer.sessionHistoryRepository.clearHistory()
                        }
                    },
                    onGrantPermissionsClick = { requestBasePermissions() },
                    onOpenOverlaySettingsClick = { requestOverlayPermission() },
                    onStartDownloadClick = { downloadAllModels() },
                    onCancelDownloadClick = { cancelOngoingProvisioning() },
                    onImportLlmFileClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                    onRefreshDiskCheckClick = { refreshStatus() }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val appContainer = AppContainer.getInstance(this)
        val config = appContainer.appConfig.llmConfig.value
        val coordinator = appContainer.pipelineCoordinator

        if (config.enableVolumeKeyShortcuts && appContainer.isServiceRunning.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    coordinator.forceAnswerNow()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    coordinator.togglePause()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun refreshStatus() {
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasNotification = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val hasOverlay = Settings.canDrawOverlays(this)

        val profile = DeviceHardwareDetector.detectHardware(this)
        hardwareProfile = profile

        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
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
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            requestBasePermissions()
            return
        }
        val intent = Intent(this, AssistantForegroundService::class.java).apply {
            action = AssistantForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAssistantService() {
        val intent = Intent(this, AssistantForegroundService::class.java).apply {
            action = AssistantForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val appContainer = AppContainer.getInstance(this)
        appContainer.onTrimMemory(level)
    }
}
