package com.local.interviewassistant.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.CopilotUiState
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.core.model.LlmGenerationConfig
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.data.history.AggregateTelemetry
import com.local.interviewassistant.data.history.QuestionAnswerRecord
import com.local.interviewassistant.data.provisioning.ProvisioningState
import com.local.interviewassistant.ui.info.InfoScreen
import com.local.interviewassistant.ui.prompter.PrompterScreen
import com.local.interviewassistant.ui.prompter.PrompterViewModel
import com.local.interviewassistant.ui.settings.SettingsScreen
import com.local.interviewassistant.ui.settings.SettingsViewModel
import com.local.interviewassistant.ui.theme.*
import com.local.interviewassistant.ui.usage.UsageScreen

enum class AppDestination(val label: String) {
    PROMPTER("Prompter"),
    SETTINGS("Settings"),
    USAGE("Activity"),
    INFO("System")
}

/**
 * Modern MVVM entrypoint AppShell eliminating 25-parameter prop drilling.
 */
@Composable
fun AppShell(
    prompterViewModel: PrompterViewModel,
    settingsViewModel: SettingsViewModel,
    readiness: AppContainer.ReadinessStatus?,
    provisioningState: ProvisioningState,
    isMeteredNetwork: Boolean,
    telemetry: AggregateTelemetry,
    historyRecords: List<QuestionAnswerRecord>,
    onExportMarkdown: () -> String,
    onClearHistory: () -> Unit,
    onGrantPermissionsClick: () -> Unit,
    onOpenOverlaySettingsClick: () -> Unit,
    onStartDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onImportLlmFileClick: () -> Unit,
    onRefreshDiskCheckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prompterState by prompterViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(AppDestination.PROMPTER) }

    val isListening = prompterState.copilotState.pipelineState is AssistantState.Listening ||
            prompterState.copilotState.pipelineState is AssistantState.DetectingSpeech

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StealthBackground)
    ) {
        Scaffold(
            containerColor = StealthBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                AnimatedVisibility(
                    visible = !prompterState.isFocusMode,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = StealthBorder
                        )
                        NavigationBar(
                            containerColor = StealthSurface.copy(alpha = 0.96f),
                            contentColor = StealthTextPrimary,
                            tonalElevation = 0.dp
                        ) {
                            AppDestination.entries.forEach { destination ->
                                val selected = destination == currentDestination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        Box(contentAlignment = Alignment.Center) {
                                            when (destination) {
                                                AppDestination.PROMPTER -> {
                                                    StealthIcons.Prompter(
                                                        color = if (selected) StealthAccentIndigoSoft else StealthTextSecondary,
                                                        size = 20.dp
                                                    )
                                                    if (isListening) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .offset(x = 10.dp, y = (-8).dp)
                                                                .background(StealthStatusEmerald, CircleShape)
                                                        )
                                                    }
                                                }
                                                AppDestination.SETTINGS -> StealthIcons.Sliders(
                                                    color = if (selected) StealthAccentIndigoSoft else StealthTextSecondary,
                                                    size = 20.dp
                                                )
                                                AppDestination.USAGE -> StealthIcons.ActivityBar(
                                                    color = if (selected) StealthAccentIndigoSoft else StealthTextSecondary,
                                                    size = 20.dp
                                                )
                                                AppDestination.INFO -> StealthIcons.InfoCircle(
                                                    color = if (selected) StealthAccentIndigoSoft else StealthTextSecondary,
                                                    size = 20.dp
                                                )
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = destination.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selected) StealthTextPrimary else StealthTextMuted
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = StealthAccentIndigoSoft,
                                        selectedTextColor = StealthTextPrimary,
                                        indicatorColor = StealthAccentIndigo.copy(alpha = 0.16f),
                                        unselectedIconColor = StealthTextSecondary,
                                        unselectedTextColor = StealthTextMuted
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentDestination) {
                    AppDestination.PROMPTER -> {
                        PrompterScreen(
                            viewModel = prompterViewModel,
                            onNavigateToSettings = { currentDestination = AppDestination.SETTINGS }
                        )
                    }

                    AppDestination.SETTINGS -> {
                        SettingsScreen(
                            viewModel = settingsViewModel
                        )
                    }

                    AppDestination.USAGE -> {
                        UsageScreen(
                            telemetry = telemetry,
                            records = historyRecords,
                            hardwareSocName = settingsState.hardwareProfile?.socName,
                            onExportMarkdown = onExportMarkdown,
                            onClearHistory = onClearHistory
                        )
                    }

                    AppDestination.INFO -> {
                        InfoScreen(
                            readiness = readiness,
                            hardwareProfile = settingsState.hardwareProfile,
                            provisioningState = provisioningState,
                            isMeteredNetwork = isMeteredNetwork,
                            onGrantPermissionsClick = onGrantPermissionsClick,
                            onOpenOverlaySettingsClick = onOpenOverlaySettingsClick,
                            onStartDownloadClick = onStartDownloadClick,
                            onCancelDownloadClick = onCancelDownloadClick,
                            onImportLlmFileClick = onImportLlmFileClick,
                            onRefreshDiskCheckClick = onRefreshDiskCheckClick
                        )
                    }
                }
            }
        }

        // Stealth OLED Dimmer Scrim (Touch-transparent overlay)
        if (prompterState.stealthDimmerAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = prompterState.stealthDimmerAlpha))
            )
        }
    }
}

/**
 * Backward compatibility overload for legacy tests or direct parameter usage.
 */
@Composable
fun AppShell(
    uiState: CopilotUiState,
    isServiceRunning: Boolean,
    llmConfig: LlmGenerationConfig,
    readiness: AppContainer.ReadinessStatus?,
    hardwareProfile: HardwareProfile?,
    provisioningState: ProvisioningState,
    isMeteredNetwork: Boolean,
    telemetry: AggregateTelemetry,
    historyRecords: List<QuestionAnswerRecord>,
    onUpdateLlmConfig: (LlmGenerationConfig) -> Unit,
    onStartAssistant: () -> Unit,
    onStopAssistant: () -> Unit,
    onPauseToggle: () -> Unit,
    onForceAnswer: () -> Unit,
    onClear: () -> Unit,
    onSelectCategory: (InterviewCategory) -> Unit,
    onSelectTranscriptSegment: (TranscriptSegment) -> Unit,
    onCustomQuestionSubmit: (String) -> Unit,
    onExportMarkdown: () -> String,
    onClearHistory: () -> Unit,
    onGrantPermissionsClick: () -> Unit,
    onOpenOverlaySettingsClick: () -> Unit,
    onStartDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onImportLlmFileClick: () -> Unit,
    onRefreshDiskCheckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppDestination.PROMPTER) }
    var isFocusMode by remember { mutableStateOf(false) }

    val isListening = uiState.pipelineState is AssistantState.Listening ||
            uiState.pipelineState is AssistantState.DetectingSpeech

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StealthBackground)
    ) {
        Scaffold(
            containerColor = StealthBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                AnimatedVisibility(
                    visible = !isFocusMode,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = StealthBorder
                        )
                        NavigationBar(
                            containerColor = StealthSurface.copy(alpha = 0.96f),
                            contentColor = StealthTextPrimary,
                            tonalElevation = 0.dp
                        ) {
                            AppDestination.entries.forEach { destination ->
                                val selected = destination == currentDestination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        Box(contentAlignment = Alignment.Center) {
                                            when (destination) {
                                                AppDestination.PROMPTER -> {
                                                    StealthIcons.Prompter(
                                                        color = if (selected) StealthAccentIndigoSoft else StealthTextSecondary,
                                                        size = 20.dp
                                                    )
                                                    if (isListening) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .offset(x = 10.dp, y = (-8).dp)
                                                                .background(StealthStatusEmerald, CircleShape)
                                                        )
                                                    }
                                                }
                                                AppDestination.SETTINGS -> StealthIcons.Sliders(
                                                    color = if (selected) StealthAccentIndigoSoft else StealthTextSecondary,
                                                    size = 20.dp
                                                )
                                                AppDestination.USAGE -> StealthIcons.ActivityBar(
                                                    color = if (selected) StealthAccentIndigoSoft else StealthTextSecondary,
                                                    size = 20.dp
                                                )
                                                AppDestination.INFO -> StealthIcons.InfoCircle(
                                                    color = if (selected) StealthAccentIndigoSoft else StealthTextSecondary,
                                                    size = 20.dp
                                                )
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = destination.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selected) StealthTextPrimary else StealthTextMuted
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = StealthAccentIndigoSoft,
                                        selectedTextColor = StealthTextPrimary,
                                        indicatorColor = StealthAccentIndigo.copy(alpha = 0.16f),
                                        unselectedIconColor = StealthTextSecondary,
                                        unselectedTextColor = StealthTextMuted
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentDestination) {
                    AppDestination.PROMPTER -> {
                        PrompterScreen(
                            uiState = uiState,
                            isServiceRunning = isServiceRunning,
                            hardwareSocName = hardwareProfile?.socName,
                            fontSizeSp = llmConfig.teleprompterFontSizeSp,
                            stealthDimmerAlpha = llmConfig.stealthDimmerAlpha,
                            onDimmerChange = { onUpdateLlmConfig(llmConfig.copy(stealthDimmerAlpha = it)) },
                            onStartService = onStartAssistant,
                            onStopService = onStopAssistant,
                            onPauseToggle = onPauseToggle,
                            onForceAnswer = onForceAnswer,
                            onClear = onClear,
                            onSelectCategory = onSelectCategory,
                            onSelectTranscriptSegment = onSelectTranscriptSegment,
                            onCustomQuestionSubmit = onCustomQuestionSubmit,
                            onNavigateToSettings = { currentDestination = AppDestination.SETTINGS },
                            onNavigateToInfo = { currentDestination = AppDestination.INFO },
                            isFocusMode = isFocusMode,
                            onToggleFocusMode = { isFocusMode = !isFocusMode }
                        )
                    }

                    AppDestination.SETTINGS -> {
                        SettingsScreen(
                            config = llmConfig,
                            hardwareProfile = hardwareProfile,
                            onConfigChange = onUpdateLlmConfig
                        )
                    }

                    AppDestination.USAGE -> {
                        UsageScreen(
                            telemetry = telemetry,
                            records = historyRecords,
                            hardwareSocName = hardwareProfile?.socName,
                            onExportMarkdown = onExportMarkdown,
                            onClearHistory = onClearHistory
                        )
                    }

                    AppDestination.INFO -> {
                        InfoScreen(
                            readiness = readiness,
                            hardwareProfile = hardwareProfile,
                            provisioningState = provisioningState,
                            isMeteredNetwork = isMeteredNetwork,
                            onGrantPermissionsClick = onGrantPermissionsClick,
                            onOpenOverlaySettingsClick = onOpenOverlaySettingsClick,
                            onStartDownloadClick = onStartDownloadClick,
                            onCancelDownloadClick = onCancelDownloadClick,
                            onImportLlmFileClick = onImportLlmFileClick,
                            onRefreshDiskCheckClick = onRefreshDiskCheckClick
                        )
                    }
                }
            }
        }

        // Stealth OLED Dimmer Scrim (Touch-transparent overlay)
        if (llmConfig.stealthDimmerAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = llmConfig.stealthDimmerAlpha))
            )
        }
    }
}
