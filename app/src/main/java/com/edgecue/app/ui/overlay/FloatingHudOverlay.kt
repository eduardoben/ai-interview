package com.edgecue.app.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgecue.app.core.di.AppContainer
import com.edgecue.app.core.model.AssistantState
import com.edgecue.app.core.model.CopilotUiState
import com.edgecue.app.core.model.InterviewCategory
import com.edgecue.app.core.model.TranscriptSegment
import com.edgecue.app.data.history.AiReportReason
import com.edgecue.app.ui.prompter.FormattedTeleprompterAnswer
import com.edgecue.app.ui.theme.*
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
@Composable
fun FloatingHudOverlay(
    uiState: CopilotUiState,
    onPauseToggle: () -> Unit,
    onForceAnswer: () -> Unit,
    onClear: () -> Unit,
    onSelectSegment: (TranscriptSegment) -> Unit,
    onToggleMinimize: () -> Unit,
    onCloseService: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    modifier: Modifier = Modifier,
    onSelectCategory: (InterviewCategory) -> Unit = {},
    onReportAiContent: ((AiReportReason, String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showReportModal by remember { mutableStateOf(false) }
    val rawStatusColor = when (uiState.pipelineState) {
        is AssistantState.Listening, is AssistantState.DetectingSpeech -> OledStatusEmerald
        is AssistantState.GeneratingAnswer -> OledStatusAmber
        is AssistantState.AnswerReady -> OledAccentIndigoSoft
        is AssistantState.Paused -> OledStatusAmber
        is AssistantState.Error -> OledStatusCoral
        else -> OledTextSecondary
    }

    val statusColor by animateColorAsState(
        targetValue = rawStatusColor,
        animationSpec = tween(250),
        label = "OverlayStatusColor"
    )

    val isDetectingSpeech = uiState.pipelineState is AssistantState.DetectingSpeech ||
            uiState.livePartialTranscript.isNotBlank()

    val infiniteTransition = rememberInfiniteTransition(label = "HudSpeechPulse")
    val micPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isDetectingSpeech) 1.0f else 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MicPulseAlpha"
    )

    if (uiState.isMinimized) {
        // Minimized Pill with 8pt grid spacing, 48dp minimum accessible touch target, and soft elevation shadow
        Box(
            modifier = modifier
                .padding(8.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(50),
                    ambientColor = Color.Black,
                    spotColor = OledAccentIndigo.copy(alpha = 0.45f)
                )
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xF506070A))
                .border(0.5.dp, OledBorderHigh, RoundedCornerShape(50))
                .clickable { onToggleMinimize() }
                .semantics { contentDescription = "Expand EdgeCue HUD" }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                PrompterIcons.DachshundMascot(color = statusColor, size = 16.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EdgeCue",
                    style = MaterialTheme.typography.titleSmall,
                    color = OledTextPrimary
                )
            }
        }
    } else {
        val scrollState = rememberScrollState()
        var autoFollowStream by remember { mutableStateOf(true) }

        LaunchedEffect(scrollState.isScrollInProgress) {
            if (scrollState.isScrollInProgress && scrollState.value < scrollState.maxValue - 36) {
                autoFollowStream = false
            }
        }

        // Reset to top when a new question starts
        LaunchedEffect(uiState.activeQuestion) {
            if (uiState.activeQuestion != null) {
                autoFollowStream = true
                scrollState.scrollTo(0)
            }
        }

        // Smoothly auto-scroll as generated answer tokens stream in and expand the HUD
        LaunchedEffect(uiState.activeAnswer, scrollState.maxValue) {
            if (autoFollowStream && !uiState.activeAnswer.isNullOrBlank() && scrollState.maxValue > 0) {
                scrollState.animateScrollTo(
                    value = scrollState.maxValue,
                    animationSpec = tween(durationMillis = 160)
                )
            }
        }

        // Outer 8.dp margin prevents WindowManager ComposeView from clipping the 16.dp drop shadow
        Box(
            modifier = modifier
                .padding(8.dp)
                .width(352.dp)
                .heightIn(max = 416.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black,
                    spotColor = OledAccentIndigo.copy(alpha = 0.40f)
                )
                // 96% opaque OLED surface with 0.5.dp top-lit border to lift off underlying dark apps
                .background(Color(0xF506070A), RoundedCornerShape(16.dp))
                .border(0.5.dp, OledBorderHigh, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Subtle Top Drag Handle + Header Bar (48dp accessible hit targets for Minimize/Close)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .background(OledBorderHigh, RoundedCornerShape(50))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when (uiState.pipelineState) {
                        is AssistantState.Idle -> "STANDBY"
                        is AssistantState.Initializing -> "LOADING"
                        is AssistantState.Listening -> "LISTENING"
                        is AssistantState.DetectingSpeech -> "SPEECH ACTIVE"
                        is AssistantState.GeneratingAnswer -> "SYNTHESIZING"
                        is AssistantState.AnswerReady -> "READY"
                        is AssistantState.Paused -> "PAUSED"
                        is AssistantState.Error -> "ERROR"
                    }

                    Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    PrompterIcons.DachshundMascot(color = statusColor, size = 14.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.lastTtftMs > 0) "$statusText • ${uiState.lastTtftMs}ms" else statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = OledTextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    // Milestone 2.3: Thermal throttle indicator badge
                    if (uiState.isThermalThrottled) {
                        Box(
                            modifier = Modifier
                                .background(OledStatusAmber.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, OledStatusAmber, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "THROTTLED",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = OledStatusAmber
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // 48x48dp touch target wrapping a clean 28dp visual circle
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onToggleMinimize() }
                            .semantics { contentDescription = "Minimize HUD overlay" },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(OledSurfaceHigh, CircleShape)
                                .border(0.5.dp, OledBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            PrompterIcons.MinimizeLine(color = OledTextSecondary, size = 12.dp)
                        }
                    }

                    // 48x48dp touch target wrapping a clean 28dp visual circle
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onCloseService() }
                            .semantics { contentDescription = "Close assistant overlay" },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(OledSurfaceHigh, CircleShape)
                                .border(0.5.dp, OledBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            PrompterIcons.CloseCross(color = OledTextSecondary, size = 12.dp)
                        }
                    }
                }
            }

            // Quick Category Chips (48dp accessible touch target height with compact 28dp visual pill)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InterviewCategory.entries.forEach { category ->
                    val isSelected = uiState.selectedCategory == category
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable { onSelectCategory(category) }
                            .semantics { contentDescription = "Category: ${category.displayName}" },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) OledAccentIndigo.copy(alpha = 0.22f) else OledSurfaceHigh
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) OledAccentIndigo else OledBorder,
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) OledTextPrimary else OledTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Zone 1: Top-Anchored Question & Formatted Answer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                if (uiState.activeQuestion != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OledSurfaceHigh, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(16.dp)
                                .background(OledAccentIndigo, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.activeQuestion,
                            style = MaterialTheme.typography.titleSmall,
                            color = OledTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!uiState.activeAnswer.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI RESPONSE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OledAccentIndigoSoft
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(OledSurfaceHigh)
                                .clickable { showReportModal = true }
                                .semantics { contentDescription = "Report AI Response" },
                            contentAlignment = Alignment.Center
                        ) {
                            PrompterIcons.Flag(color = OledTextSecondary, size = 13.dp)
                        }
                    }

                    FormattedTeleprompterAnswer(
                        text = uiState.activeAnswer,
                        fontSize = 15.sp
                    )
                } else if (uiState.pipelineState is AssistantState.GeneratingAnswer) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = OledStatusAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Synthesizing answer...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic
                            ),
                            color = OledStatusAmber
                        )
                    }
                } else {
                    // Welcoming, structured Empty State (Peak-End & Emotional Design Rule)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(OledStatusEmerald.copy(alpha = 0.12f), CircleShape)
                                .border(0.5.dp, OledStatusEmerald.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            PrompterIcons.Microphone(
                                color = OledStatusEmerald,
                                size = 16.dp,
                                modifier = Modifier.alpha(micPulseAlpha)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ready for interview practice",
                            style = MaterialTheme.typography.titleSmall,
                            color = OledTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Speak naturally or select a category above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OledTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Persistent micro-disclaimer beneath reading area (100% visible across all states)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .semantics { contentDescription = "AI hallucination notice: AI output may contain inaccuracies. Verify independently." },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrompterIcons.AlertCircle(color = OledTextMuted, size = 10.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "AI output may contain inaccuracies. Verify independently.",
                        fontSize = 10.sp,
                        color = OledTextMuted
                    )
                }
                if (!uiState.activeAnswer.isNullOrBlank()) {
                    Text(
                        text = "Report",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OledAccentIndigoSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showReportModal = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .semantics { contentDescription = "Report AI-generated answer" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Zone 2: Compact Bottom STT Ticker with pulsing Microphone micro-animation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledSurface, RoundedCornerShape(8.dp))
                    .border(
                        0.5.dp,
                        if (isDetectingSpeech) OledStatusEmerald.copy(alpha = 0.45f) else OledBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrompterIcons.Microphone(
                    color = if (isDetectingSpeech) OledStatusEmerald else OledTextMuted,
                    size = 12.dp,
                    modifier = Modifier.alpha(if (isDetectingSpeech) micPulseAlpha else 1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val transcriptText = when {
                    uiState.livePartialTranscript.isNotBlank() -> "${uiState.livePartialTranscript}..."
                    uiState.transcriptHistory.isNotEmpty() -> uiState.transcriptHistory.last().text
                    else -> "Audio capture active"
                }
                Text(
                    text = transcriptText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDetectingSpeech) OledStatusEmerald else OledTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zone 3: Footer Action Buttons (48dp minimum accessible height + 60/30/10 visual hierarchy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary CTA (60% focal weight): Soft indigo glow halo + solid primary fill
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .background(OledAccentIndigo.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                        .padding(2.dp)
                ) {
                    Button(
                        onClick = onForceAnswer,
                        enabled = uiState.pipelineState !is AssistantState.Paused &&
                                uiState.pipelineState !is AssistantState.GeneratingAnswer,
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Force immediate answer generation" },
                        colors = ButtonDefaults.buttonColors(containerColor = OledAccentIndigo),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Answer Now",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                // Secondary Action (30% tonal weight): Subtle outlined surface button
                OutlinedButton(
                    onClick = onPauseToggle,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = if (uiState.pipelineState is AssistantState.Paused) "Resume assistant listening" else "Pause assistant listening" }
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = OledSurface,
                        contentColor = OledTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, OledBorderHigh),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (uiState.pipelineState is AssistantState.Paused) "Resume" else "Pause",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = OledTextPrimary
                    )
                }

                // Tertiary Action (10% quiet weight): Low-emphasis ghost button
                TextButton(
                    onClick = onClear,
                    modifier = Modifier
                        .weight(0.8f)
                        .semantics { contentDescription = "Clear current answer and transcript" }
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OledTextSecondary
                    )
                }
            }
        }

        // In-Tree Modal: Prevents WindowManager$BadTokenException in Foreground Service Overlay
        if (showReportModal && !uiState.activeAnswer.isNullOrBlank()) {
            ReportAiContentOverlayCard(
                question = uiState.activeQuestion,
                answer = uiState.activeAnswer,
                onDismiss = { showReportModal = false },
                onSubmit = { reason, comment ->
                    showReportModal = false
                    if (onReportAiContent != null) {
                        onReportAiContent(reason, comment)
                    } else {
                        coroutineScope.launch {
                            try {
                                val appContainer = AppContainer.getInstance(context)
                                appContainer.sessionHistoryRepository.submitAiReport(
                                    question = uiState.activeQuestion,
                                    answer = uiState.activeAnswer,
                                    reason = reason,
                                    comment = comment
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
            )
        }
    }
}
}

/**
 * In-Tree Composable Modal for reporting AI content inside the system overlay (Requirement R2, P0-03).
 *
 * CRITICAL ARCHITECTURAL CONSTRAINT:
 * Standard Android [Dialog] or [AlertDialog] MUST NOT be used inside FloatingHudOverlay because
 * AssistantForegroundService's WindowManager context throws WindowManager$BadTokenException.
 * This component is rendered purely in-tree on the existing overlay surface.
 */
@Composable
fun ReportAiContentOverlayCard(
    question: String?,
    answer: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: AiReportReason, comment: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedReason by remember { mutableStateOf(AiReportReason.FACTUAL_INACCURACY) }
    var userComment by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFA06070A), RoundedCornerShape(16.dp))
            .border(0.5.dp, OledAccentIndigo.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrompterIcons.Flag(color = OledStatusAmber, size = 16.dp)
                    Text(
                        text = "Report AI Response",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = OledTextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(OledSurfaceHigh)
                        .clickable { onDismiss() }
                        .semantics { contentDescription = "Close report dialog" },
                    contentAlignment = Alignment.Center
                ) {
                    PrompterIcons.CloseCross(color = OledTextMuted, size = 12.dp)
                }
            }

            Text(
                text = "Reason for Report",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = OledTextPrimary
            )

            AiReportReason.values().forEach { reason ->
                val isSelected = (reason == selectedReason)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReason = reason }
                        .background(
                            color = if (isSelected) OledSurfaceHigh else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedReason = reason },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = OledAccentIndigo,
                            unselectedColor = OledTextMuted
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = reason.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) OledTextPrimary else OledTextSecondary
                    )
                }
            }

            OutlinedTextField(
                value = userComment,
                onValueChange = { userComment = it },
                placeholder = { Text("Optional details...", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = OledTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OledAccentIndigo,
                    unfocusedBorderColor = OledBorder,
                    focusedLabelColor = OledAccentIndigoSoft,
                    unfocusedLabelColor = OledTextMuted
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = OledTextMuted, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        val commentOrNull = userComment.trim().ifEmpty { null }
                        onSubmit(selectedReason, commentOrNull)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OledAccentIndigo),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Submit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}
