package com.local.interviewassistant.ui.prompter.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.ui.prompter.FormattedTeleprompterAnswer
import com.local.interviewassistant.ui.theme.*

/**
 * Top-weighted Teleprompter reading zone displaying questions, streaming answers,
 * acoustic transcripts, and empty state guides. Features automatic smooth scrolling as
 * answers stream in from LiteRT-LM.
 */
@Composable
fun TeleprompterDisplay(
    activeQuestion: String?,
    activeAnswer: String?,
    pipelineState: AssistantState,
    livePartialTranscript: String,
    transcriptHistory: List<TranscriptSegment>,
    isServiceRunning: Boolean,
    fontSizeSp: Float,
    autoFollowStream: Boolean,
    onAutoFollowChange: (Boolean) -> Unit,
    onSelectTranscriptSegment: (TranscriptSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Disable auto-follow if the user manually drags upward to inspect earlier lines
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && scrollState.value < scrollState.maxValue - 48) {
            onAutoFollowChange(false)
        }
    }

    // Reset scroll to top and re-enable auto-follow when a new question starts
    LaunchedEffect(activeQuestion) {
        if (activeQuestion != null) {
            onAutoFollowChange(true)
            scrollState.scrollTo(0)
        }
    }

    // Smoothly auto-scroll as generated answer tokens stream in and expand the layout
    LaunchedEffect(activeAnswer, scrollState.maxValue) {
        if (autoFollowStream && !activeAnswer.isNullOrBlank() && scrollState.maxValue > 0) {
            scrollState.animateScrollTo(
                value = scrollState.maxValue,
                animationSpec = tween(durationMillis = 160)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(StealthSurface, RoundedCornerShape(16.dp))
            .border(0.5.dp, StealthBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            if (activeQuestion != null) {
                // Elegant left-accented Question Callout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StealthSurfaceHigh.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .background(StealthAccentIndigo, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = activeQuestion,
                        color = StealthTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (!activeAnswer.isNullOrBlank()) {
                FormattedTeleprompterAnswer(
                    text = activeAnswer,
                    fontSize = fontSizeSp.sp
                )
            } else if (pipelineState is AssistantState.GeneratingAnswer) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.75.dp,
                        color = StealthStatusAmber
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Synthesizing response...",
                        color = StealthStatusAmber,
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            } else if (pipelineState is AssistantState.Initializing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = StealthAccentIndigo
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Initializing On-Device Pipeline",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StealthTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Allocating neural accelerator & speech streams",
                        fontSize = 12.sp,
                        color = StealthTextMuted
                    )
                }
            } else if (pipelineState is AssistantState.Error) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StealthStatusCoral.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, StealthStatusCoral.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StealthIcons.AlertCircle(color = StealthStatusCoral, size = 16.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = pipelineState.message,
                        color = StealthStatusCoral,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            } else if (livePartialTranscript.isNotBlank() || transcriptHistory.isNotEmpty()) {
                if (livePartialTranscript.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StealthSurfaceHigh, RoundedCornerShape(10.dp))
                            .border(0.5.dp, StealthStatusEmerald.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StealthIcons.Microphone(color = StealthStatusEmerald, size = 16.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$livePartialTranscript...",
                            color = StealthStatusEmerald,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 21.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (transcriptHistory.isNotEmpty()) {
                    Text(
                        text = "RECENT SPEECH • TAP TO ANSWER",
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StealthTextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    transcriptHistory.takeLast(5).reversed().forEach { seg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(StealthSurfaceHigh)
                                .border(
                                    0.5.dp,
                                    if (seg.isLikelyQuestion) StealthAccentIndigo.copy(alpha = 0.45f) else StealthBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectTranscriptSegment(seg) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = seg.text,
                                color = if (seg.isLikelyQuestion) StealthTextPrimary else StealthTextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Answer →",
                                color = StealthAccentIndigoSoft,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                // Minimal empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isServiceRunning) StealthStatusEmerald.copy(alpha = 0.12f)
                                else StealthSurfaceHigh,
                                CircleShape
                            )
                            .border(
                                0.5.dp,
                                if (isServiceRunning) StealthStatusEmerald.copy(alpha = 0.35f)
                                else StealthBorder,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        StealthIcons.Microphone(
                            color = if (isServiceRunning) StealthStatusEmerald else StealthTextSecondary,
                            size = 20.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isServiceRunning) "Listening for Questions" else "Teleprompter Ready",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isServiceRunning) StealthTextPrimary else StealthTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isServiceRunning) {
                            "Speak naturally or tap the transcript bar below to enter a prompt."
                        } else {
                            "Start the live copilot below to capture questions in real time."
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = StealthTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                }
            }
        }
    }
}

