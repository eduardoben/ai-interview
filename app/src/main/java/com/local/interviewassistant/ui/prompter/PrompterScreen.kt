package com.local.interviewassistant.ui.prompter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.CopilotUiState
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.ui.prompter.components.*
import com.local.interviewassistant.ui.theme.StealthBackground

/**
 * State-aware entrypoint composable for the Teleprompter screen, powered by [PrompterViewModel].
 */
@Composable
fun PrompterScreen(
    viewModel: PrompterViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PrompterScreenContent(
        uiState = state.copilotState,
        isServiceRunning = state.isServiceRunning,
        isFocusMode = state.isFocusMode,
        fontSizeSp = state.fontSizeSp,
        stealthDimmerAlpha = state.stealthDimmerAlpha,
        hardwareSocName = state.hardwareSocName,
        showTranscriptSheet = state.showTranscriptSheet,
        customQuestionInput = state.customQuestionInput,
        showDimmerDialog = state.showDimmerDialog,
        autoFollowStream = state.autoFollowStream,
        onStartService = viewModel::onStartService,
        onStopService = viewModel::onStopService,
        onPauseToggle = viewModel::onPauseToggle,
        onForceAnswer = viewModel::onForceAnswer,
        onClear = viewModel::onClearAnswer,
        onSelectCategory = viewModel::onSelectCategory,
        onSelectTranscriptSegment = viewModel::onSelectTranscriptSegment,
        onCustomQuestionSubmit = viewModel::onSubmitCustomQuestion,
        onCustomQuestionInputChange = viewModel::onUpdateCustomQuestionInput,
        onToggleFocusMode = viewModel::onToggleFocusMode,
        onToggleTranscriptSheet = viewModel::onToggleTranscriptSheet,
        onToggleDimmerDialog = viewModel::onToggleDimmerDialog,
        onDimmerChange = viewModel::onDimmerAlphaChange,
        onAutoFollowChange = viewModel::setAutoFollowStream,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier
    )
}

/**
 * Legacy/stateless overload for backward compatibility with [AppShell], Compose previews, and tests.
 */
@Composable
fun PrompterScreen(
    uiState: CopilotUiState,
    isServiceRunning: Boolean,
    fontSizeSp: Float,
    stealthDimmerAlpha: Float,
    onDimmerChange: (Float) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onPauseToggle: () -> Unit,
    onForceAnswer: () -> Unit,
    onClear: () -> Unit,
    onSelectCategory: (InterviewCategory) -> Unit,
    onSelectTranscriptSegment: (TranscriptSegment) -> Unit,
    onCustomQuestionSubmit: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInfo: () -> Unit,
    isFocusMode: Boolean,
    onToggleFocusMode: () -> Unit,
    modifier: Modifier = Modifier,
    hardwareSocName: String? = null
) {
    var showTranscriptSheet by remember { mutableStateOf(false) }
    var customQuestionInput by remember { mutableStateOf("") }
    var showDimmerDialog by remember { mutableStateOf(false) }
    var autoFollowStream by remember { mutableStateOf(true) }

    PrompterScreenContent(
        uiState = uiState,
        isServiceRunning = isServiceRunning,
        isFocusMode = isFocusMode,
        fontSizeSp = fontSizeSp,
        stealthDimmerAlpha = stealthDimmerAlpha,
        hardwareSocName = hardwareSocName,
        showTranscriptSheet = showTranscriptSheet,
        customQuestionInput = customQuestionInput,
        showDimmerDialog = showDimmerDialog,
        autoFollowStream = autoFollowStream,
        onStartService = onStartService,
        onStopService = onStopService,
        onPauseToggle = onPauseToggle,
        onForceAnswer = onForceAnswer,
        onClear = onClear,
        onSelectCategory = onSelectCategory,
        onSelectTranscriptSegment = onSelectTranscriptSegment,
        onCustomQuestionSubmit = onCustomQuestionSubmit,
        onCustomQuestionInputChange = { customQuestionInput = it },
        onToggleFocusMode = onToggleFocusMode,
        onToggleTranscriptSheet = { showTranscriptSheet = it },
        onToggleDimmerDialog = { showDimmerDialog = it },
        onDimmerChange = onDimmerChange,
        onAutoFollowChange = { autoFollowStream = it },
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier
    )
}

/**
 * Pure, stateless UI content for the Prompter screen.
 */
@Composable
fun PrompterScreenContent(
    uiState: CopilotUiState,
    isServiceRunning: Boolean,
    isFocusMode: Boolean,
    fontSizeSp: Float,
    stealthDimmerAlpha: Float,
    hardwareSocName: String?,
    showTranscriptSheet: Boolean,
    customQuestionInput: String,
    showDimmerDialog: Boolean,
    autoFollowStream: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onPauseToggle: () -> Unit,
    onForceAnswer: () -> Unit,
    onClear: () -> Unit,
    onSelectCategory: (InterviewCategory) -> Unit,
    onSelectTranscriptSegment: (TranscriptSegment) -> Unit,
    onCustomQuestionSubmit: (String) -> Unit,
    onCustomQuestionInputChange: (String) -> Unit,
    onToggleFocusMode: () -> Unit,
    onToggleTranscriptSheet: (Boolean) -> Unit,
    onToggleDimmerDialog: (Boolean) -> Unit,
    onDimmerChange: (Float) -> Unit,
    onAutoFollowChange: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (showDimmerDialog) {
        StealthDimmerDialog(
            stealthDimmerAlpha = stealthDimmerAlpha,
            onDimmerChange = onDimmerChange,
            onDismiss = { onToggleDimmerDialog(false) }
        )
    }

    if (showTranscriptSheet) {
        TranscriptHistorySheet(
            transcriptHistory = uiState.transcriptHistory,
            customQuestionInput = customQuestionInput,
            onCustomQuestionInputChange = onCustomQuestionInputChange,
            onCustomQuestionSubmit = {
                onCustomQuestionSubmit(it)
                onToggleTranscriptSheet(false)
            },
            onSelectTranscriptSegment = {
                onSelectTranscriptSegment(it)
                onToggleTranscriptSheet(false)
            },
            onDismiss = { onToggleTranscriptSheet(false) }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StealthBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Status Bar & Telemetry Header
            PrompterHeader(
                pipelineState = uiState.pipelineState,
                isServiceRunning = isServiceRunning,
                lastTtftMs = uiState.lastTtftMs,
                hardwareSocName = hardwareSocName,
                stealthDimmerAlpha = stealthDimmerAlpha,
                isFocusMode = isFocusMode,
                onNavigateToSettings = onNavigateToSettings,
                onToggleDimmer = { onToggleDimmerDialog(true) },
                onToggleFocusMode = onToggleFocusMode
            )

            // 2. Category Filter Strip (collapses in focus mode)
            CategoryChipRow(
                selectedCategory = uiState.selectedCategory,
                isFocusMode = isFocusMode,
                onSelectCategory = onSelectCategory
            )

            if (isFocusMode) {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 3. Top-Weighted Teleprompter Reading Zone
            TeleprompterDisplay(
                activeQuestion = uiState.activeQuestion,
                activeAnswer = uiState.activeAnswer,
                pipelineState = uiState.pipelineState,
                livePartialTranscript = uiState.livePartialTranscript,
                transcriptHistory = uiState.transcriptHistory,
                isServiceRunning = isServiceRunning,
                fontSizeSp = fontSizeSp,
                autoFollowStream = autoFollowStream,
                onAutoFollowChange = onAutoFollowChange,
                onSelectTranscriptSegment = onSelectTranscriptSegment,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Collapsible Live Transcript Bar
            LiveTranscriptBar(
                livePartialTranscript = uiState.livePartialTranscript,
                transcriptHistory = uiState.transcriptHistory,
                isServiceRunning = isServiceRunning,
                onClick = { onToggleTranscriptSheet(true) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Thumb-Zone Control Dock
            ThumbControlDock(
                isServiceRunning = isServiceRunning,
                isPaused = uiState.pipelineState is AssistantState.Paused,
                onStartService = onStartService,
                onStopService = onStopService,
                onForceAnswer = onForceAnswer,
                onPauseToggle = onPauseToggle,
                onClear = onClear
            )
        }
    }
}
