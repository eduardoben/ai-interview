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
import com.local.interviewassistant.ui.settings.SettingsScreen
import com.local.interviewassistant.ui.theme.*
import com.local.interviewassistant.ui.usage.UsageScreen

enum class AppDestination(val label: String) {
    PROMPTER("Prompter"),
    SETTINGS("Settings"),
    USAGE("Activity"),
    INFO("System")
}

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
                            tonalElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppDestination.entries.forEach { dest ->
                                val isSelected = currentDestination == dest
                                val iconTint = if (isSelected) StealthTextPrimary else StealthTextMuted
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        Box(contentAlignment = Alignment.Center) {
                                            when (dest) {
                                                AppDestination.PROMPTER -> StealthIcons.Prompter(color = iconTint, size = 20.dp)
                                                AppDestination.SETTINGS -> StealthIcons.Sliders(color = iconTint, size = 20.dp)
                                                AppDestination.USAGE -> StealthIcons.ActivityBar(color = iconTint, size = 20.dp)
                                                AppDestination.INFO -> StealthIcons.InfoCircle(color = iconTint, size = 20.dp)
                                            }
                                            if (dest == AppDestination.PROMPTER && isListening) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .offset(x = 10.dp, y = (-8).dp)
                                                        .background(StealthStatusEmerald, CircleShape)
                                                )
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.2.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) StealthTextPrimary else StealthTextMuted
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = StealthTextPrimary,
                                        unselectedIconColor = StealthTextMuted,
                                        indicatorColor = StealthAccentIndigo.copy(alpha = 0.18f)
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
