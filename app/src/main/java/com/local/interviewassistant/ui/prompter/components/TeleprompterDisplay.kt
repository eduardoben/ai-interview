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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.history.AiReportReason
import com.local.interviewassistant.ui.dialog.ReportAiContentDialog
import com.local.interviewassistant.ui.prompter.FormattedTeleprompterAnswer
import com.local.interviewassistant.ui.theme.*
import kotlinx.coroutines.launch

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
    modifier: Modifier = Modifier,
    onReportAiContent: ((AiReportReason, String?) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showReportDialog by remember { mutableStateOf(false) }

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
            .background(OledSurface, RoundedCornerShape(16.dp))
            .border(0.5.dp, OledBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
            if (activeQuestion != null) {
                // Elegant left-accented Question Callout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OledSurfaceHigh.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .background(OledAccentIndigo, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = activeQuestion,
                        color = OledTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (!activeAnswer.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI PRACTICE RESPONSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OledAccentIndigoSoft
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(OledSurfaceHigh)
                            .clickable { showReportDialog = true }
                            .semantics { contentDescription = "Report AI-generated response" },
                        contentAlignment = Alignment.Center
                    ) {
                        PrompterIcons.Flag(color = OledTextSecondary, size = 15.dp)
                    }
                }

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
                        color = OledStatusAmber
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Synthesizing response...",
                        color = OledStatusAmber,
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
                        color = OledAccentIndigo
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Initializing On-Device Pipeline",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OledTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Allocating neural accelerator & speech streams",
                        fontSize = 12.sp,
                        color = OledTextMuted
                    )
                }
            } else if (pipelineState is AssistantState.Error) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OledStatusCoral.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, OledStatusCoral.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrompterIcons.AlertCircle(color = OledStatusCoral, size = 16.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = pipelineState.message,
                        color = OledStatusCoral,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            } else if (livePartialTranscript.isNotBlank() || transcriptHistory.isNotEmpty()) {
                if (livePartialTranscript.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OledSurfaceHigh, RoundedCornerShape(10.dp))
                            .border(0.5.dp, OledStatusEmerald.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrompterIcons.Microphone(color = OledStatusEmerald, size = 16.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$livePartialTranscript...",
                            color = OledStatusEmerald,
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
                        color = OledTextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    transcriptHistory.takeLast(5).reversed().forEach { seg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OledSurfaceHigh)
                                .border(
                                    0.5.dp,
                                    if (seg.isLikelyQuestion) OledAccentIndigo.copy(alpha = 0.45f) else OledBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectTranscriptSegment(seg) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = seg.text,
                                color = if (seg.isLikelyQuestion) OledTextPrimary else OledTextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Answer →",
                                color = OledAccentIndigoSoft,
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
                                if (isServiceRunning) OledStatusEmerald.copy(alpha = 0.12f)
                                else OledSurfaceHigh,
                                CircleShape
                            )
                            .border(
                                0.5.dp,
                                if (isServiceRunning) OledStatusEmerald.copy(alpha = 0.35f)
                                else OledBorder,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        PrompterIcons.Microphone(
                            color = if (isServiceRunning) OledStatusEmerald else OledTextSecondary,
                            size = 20.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isServiceRunning) "Listening for Questions" else "Teleprompter Ready",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isServiceRunning) OledTextPrimary else OledTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isServiceRunning) {
                            "Speak naturally or tap the transcript bar below to enter a prompt."
                        } else {
                            "Start the practice copilot below to capture questions in real time."
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = OledTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.5.dp, color = OledBorder.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(6.dp))

        // Persistent AI Hallucination Disclaimer & Reporting Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "AI hallucination notice: AI responses may contain inaccuracies. Verify technical concepts independently."
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrompterIcons.AlertCircle(color = OledTextMuted, size = 12.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AI responses may contain inaccuracies. Verify technical concepts independently.",
                    fontSize = 11.sp,
                    color = OledTextMuted,
                    lineHeight = 15.sp
                )
            }
            if (!activeAnswer.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Report",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OledAccentIndigoSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showReportDialog = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .semantics { contentDescription = "Report AI-generated answer" }
                )
            }
        }
    }

        if (showReportDialog && !activeAnswer.isNullOrBlank()) {
            ReportAiContentDialog(
                question = activeQuestion,
                answer = activeAnswer,
                onDismiss = { showReportDialog = false },
                onSubmit = { reason, comment ->
                    showReportDialog = false
                    if (onReportAiContent != null) {
                        onReportAiContent(reason, comment)
                    } else {
                        coroutineScope.launch {
                            try {
                                val appContainer = AppContainer.getInstance(context)
                                appContainer.sessionHistoryRepository.submitAiReport(
                                    question = activeQuestion,
                                    answer = activeAnswer,
                                    reason = reason,
                                    comment = comment
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    "Report submitted. Thank you for your feedback.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } catch (_: Exception) {}
                        }
                    }
                }
            )
        }
    }
}

