package com.local.interviewassistant.ui.overlay

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.CopilotUiState
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.ui.prompter.FormattedTeleprompterAnswer
import com.local.interviewassistant.ui.theme.*

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
) {
    val rawStatusColor = when (uiState.pipelineState) {
        is AssistantState.Listening, is AssistantState.DetectingSpeech -> StealthStatusEmerald
        is AssistantState.GeneratingAnswer -> StealthStatusAmber
        is AssistantState.AnswerReady -> StealthAccentIndigoSoft
        is AssistantState.Paused -> StealthStatusAmber
        is AssistantState.Error -> StealthStatusCoral
        else -> StealthTextSecondary
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
                    spotColor = StealthAccentIndigo.copy(alpha = 0.45f)
                )
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xF506070A))
                .border(0.5.dp, StealthBorderHigh, RoundedCornerShape(50))
                .clickable { onToggleMinimize() }
                .semantics { contentDescription = "Expand Copilot HUD" }
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Copilot",
                    style = MaterialTheme.typography.titleSmall,
                    color = StealthTextPrimary
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
        Column(
            modifier = modifier
                .padding(8.dp)
                .width(352.dp)
                .heightIn(max = 416.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black,
                    spotColor = StealthAccentIndigo.copy(alpha = 0.40f)
                )
                // 96% opaque OLED surface with 0.5.dp top-lit border to lift off underlying dark apps
                .background(Color(0xF506070A), RoundedCornerShape(16.dp))
                .border(0.5.dp, StealthBorderHigh, RoundedCornerShape(16.dp))
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
                        .background(StealthBorderHigh, RoundedCornerShape(50))
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.lastTtftMs > 0) "$statusText • ${uiState.lastTtftMs}ms" else statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = StealthTextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    // Milestone 2.3: Thermal throttle indicator badge
                    if (uiState.isThermalThrottled) {
                        Box(
                            modifier = Modifier
                                .background(StealthStatusAmber.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, StealthStatusAmber, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "THROTTLED",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = StealthStatusAmber
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
                                .background(StealthSurfaceHigh, CircleShape)
                                .border(0.5.dp, StealthBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            StealthIcons.MinimizeLine(color = StealthTextSecondary, size = 12.dp)
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
                                .background(StealthSurfaceHigh, CircleShape)
                                .border(0.5.dp, StealthBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            StealthIcons.CloseCross(color = StealthTextSecondary, size = 12.dp)
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
                                    if (isSelected) StealthAccentIndigo.copy(alpha = 0.22f) else StealthSurfaceHigh
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) StealthAccentIndigo else StealthBorder,
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) StealthTextPrimary else StealthTextSecondary
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
                            .background(StealthSurfaceHigh, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(16.dp)
                                .background(StealthAccentIndigo, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.activeQuestion,
                            style = MaterialTheme.typography.titleSmall,
                            color = StealthTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!uiState.activeAnswer.isNullOrBlank()) {
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
                            color = StealthStatusAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Synthesizing answer...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic
                            ),
                            color = StealthStatusAmber
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
                                .background(StealthStatusEmerald.copy(alpha = 0.12f), CircleShape)
                                .border(0.5.dp, StealthStatusEmerald.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            StealthIcons.Microphone(
                                color = StealthStatusEmerald,
                                size = 16.dp,
                                modifier = Modifier.alpha(micPulseAlpha)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ready for your interview",
                            style = MaterialTheme.typography.titleSmall,
                            color = StealthTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Speak naturally or select a category above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StealthTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zone 2: Compact Bottom STT Ticker with pulsing Microphone micro-animation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StealthSurface, RoundedCornerShape(8.dp))
                    .border(
                        0.5.dp,
                        if (isDetectingSpeech) StealthStatusEmerald.copy(alpha = 0.45f) else StealthBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StealthIcons.Microphone(
                    color = if (isDetectingSpeech) StealthStatusEmerald else StealthTextMuted,
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
                    color = if (isDetectingSpeech) StealthStatusEmerald else StealthTextMuted,
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
                        .background(StealthAccentIndigo.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                        .padding(2.dp)
                ) {
                    Button(
                        onClick = onForceAnswer,
                        enabled = uiState.pipelineState !is AssistantState.Paused &&
                                uiState.pipelineState !is AssistantState.GeneratingAnswer,
                        modifier = Modifier.fillMaxSize(),
                        colors = ButtonDefaults.buttonColors(containerColor = StealthAccentIndigo),
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
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StealthSurface,
                        contentColor = StealthTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, StealthBorderHigh),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (uiState.pipelineState is AssistantState.Paused) "Resume" else "Pause",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = StealthTextPrimary
                    )
                }

                // Tertiary Action (10% quiet weight): Low-emphasis ghost button
                TextButton(
                    onClick = onClear,
                    modifier = Modifier
                        .weight(0.8f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StealthTextSecondary
                    )
                }
            }
        }
    }
}
