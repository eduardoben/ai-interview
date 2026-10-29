package com.local.interviewassistant.ui.overlay

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.CopilotUiState
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.ui.prompter.FormattedTeleprompterAnswer
import com.local.interviewassistant.ui.theme.*

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

    if (uiState.isMinimized) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xF506070A))
                .border(0.5.dp, StealthBorderHigh, RoundedCornerShape(50))
                .clickable { onToggleMinimize() }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).background(statusColor, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Copilot",
                color = StealthTextPrimary,
                fontSize = 12.sp,
                letterSpacing = 0.2.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        val scrollState = rememberScrollState()

        // Top-First Reading Anchor: Keep eyes at the top of the answer
        LaunchedEffect(uiState.activeQuestion) {
            if (uiState.activeQuestion != null) {
                scrollState.scrollTo(0)
            }
        }

        Column(
            modifier = modifier
                .width(344.dp)
                .heightIn(max = 396.dp)
                // 96% opaque OLED background to eliminate background bleed-through
                .background(Color(0xF506070A), RoundedCornerShape(16.dp))
                .border(0.5.dp, StealthBorderHigh, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // Subtle Top Drag Handle + Header Bar
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
                        .height(3.dp)
                        .background(StealthBorderHigh, RoundedCornerShape(50))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(6.dp))

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

                    Box(modifier = Modifier.size(7.dp).background(statusColor, CircleShape))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (uiState.lastTtftMs > 0) "$statusText • ${uiState.lastTtftMs}ms" else statusText,
                        color = StealthTextPrimary,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(StealthSurfaceHigh)
                            .clickable { onToggleMinimize() },
                        contentAlignment = Alignment.Center
                    ) {
                        StealthIcons.MinimizeLine(color = StealthTextSecondary, size = 12.dp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(StealthSurfaceHigh)
                            .clickable { onCloseService() },
                        contentAlignment = Alignment.Center
                    ) {
                        StealthIcons.CloseCross(color = StealthTextSecondary, size = 12.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Category Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InterviewCategory.entries.forEach { category ->
                    val isSelected = uiState.selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) StealthAccentIndigo.copy(alpha = 0.2f) else StealthSurfaceHigh
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) StealthAccentIndigo else StealthBorder,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onSelectCategory(category) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = category.displayName,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) StealthTextPrimary else StealthTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(16.dp)
                                .background(StealthAccentIndigo, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.activeQuestion,
                            color = StealthTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!uiState.activeAnswer.isNullOrBlank()) {
                    FormattedTeleprompterAnswer(
                        text = uiState.activeAnswer,
                        fontSize = 14.sp
                    )
                } else if (uiState.pipelineState is AssistantState.GeneratingAnswer) {
                    Text(
                        text = "Synthesizing answer...",
                        color = StealthStatusAmber,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    Text(
                        text = "Listening for questions...",
                        color = StealthTextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zone 2: Compact Bottom STT Ticker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StealthSurface, RoundedCornerShape(8.dp))
                    .border(0.5.dp, StealthBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StealthIcons.Microphone(
                    color = if (uiState.livePartialTranscript.isNotBlank()) StealthStatusEmerald else StealthTextMuted,
                    size = 12.dp
                )
                Spacer(modifier = Modifier.width(7.dp))
                val transcriptText = when {
                    uiState.livePartialTranscript.isNotBlank() -> "${uiState.livePartialTranscript}..."
                    uiState.transcriptHistory.isNotEmpty() -> uiState.transcriptHistory.last().text
                    else -> "Audio capture active"
                }
                Text(
                    text = transcriptText,
                    color = if (uiState.livePartialTranscript.isNotBlank()) StealthStatusEmerald else StealthTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zone 3: Footer Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onForceAnswer,
                    enabled = uiState.pipelineState !is AssistantState.Paused && uiState.pipelineState !is AssistantState.GeneratingAnswer,
                    modifier = Modifier
                        .weight(1.35f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StealthAccentIndigo),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Answer Now", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                OutlinedButton(
                    onClick = onPauseToggle,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StealthSurface,
                        contentColor = StealthTextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, StealthBorderHigh),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (uiState.pipelineState is AssistantState.Paused) "Resume" else "Pause",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier
                        .weight(0.9f)
                        .height(38.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StealthSurface,
                        contentColor = StealthTextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, StealthBorder),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Clear", fontSize = 11.sp)
                }
            }
        }
    }
}
