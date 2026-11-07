package com.edgecue.app.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

enum class HudTab(val title: String) {
    ANSWER("Answer"),
    TRANSCRIPTS("Transcripts")
}

enum class HudSizePreset(val label: String, val width: Dp, val height: Dp) {
    COMPACT("S", 320.dp, 360.dp),
    STANDARD("M", 380.dp, 480.dp),
    EXPANDED("L", 460.dp, 640.dp)
}

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
    initialWidthDp: Float = 380f,
    initialHeightDp: Float = 480f,
    onResize: ((widthDp: Float, heightDp: Float) -> Unit)? = null,
    onSelectCategory: (InterviewCategory) -> Unit = {},
    onReportAiContent: ((AiReportReason, String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    val minWidthDp = 280.dp
    val maxWidthDp = (screenWidthDp - 16.dp).coerceAtLeast(320.dp)
    val minHeightDp = 260.dp
    val maxHeightDp = (screenHeightDp - 48.dp).coerceAtLeast(360.dp)

    var overlayWidth by remember(initialWidthDp) {
        mutableStateOf(initialWidthDp.dp.coerceIn(minWidthDp, maxWidthDp))
    }
    var overlayHeight by remember(initialHeightDp) {
        mutableStateOf(initialHeightDp.dp.coerceIn(minHeightDp, maxHeightDp))
    }

    var selectedTab by remember { mutableStateOf(HudTab.ANSWER) }
    var showReportModal by remember { mutableStateOf(false) }

    // Auto-switch to ANSWER tab when answer is generating or ready
    LaunchedEffect(uiState.activeAnswer, uiState.pipelineState) {
        if (!uiState.activeAnswer.isNullOrBlank() || uiState.pipelineState is AssistantState.GeneratingAnswer) {
            selectedTab = HudTab.ANSWER
        }
    }

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
        // Minimized Pill
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                Spacer(modifier = Modifier.width(5.dp))
                PrompterIcons.DachshundMascot(color = statusColor, size = 16.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EdgeCue",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = OledTextPrimary
                )
            }
        }
    } else {
        val answerScrollState = rememberScrollState()
        var autoFollowStream by remember { mutableStateOf(true) }

        // Disable auto-follow if the user manually drags upward to inspect earlier lines
        LaunchedEffect(answerScrollState.isScrollInProgress) {
            if (answerScrollState.isScrollInProgress) {
                if (answerScrollState.value < answerScrollState.maxValue - 48) {
                    autoFollowStream = false
                } else if (answerScrollState.value >= answerScrollState.maxValue - 16) {
                    autoFollowStream = true
                }
            }
        }

        // Reset scroll to top and re-enable auto-follow when a new question starts
        LaunchedEffect(uiState.activeQuestion) {
            if (uiState.activeQuestion != null) {
                autoFollowStream = true
                answerScrollState.scrollTo(0)
            }
        }

        // Smoothly auto-scroll as generated answer tokens stream in and expand the layout
        LaunchedEffect(autoFollowStream) {
            if (!autoFollowStream) return@LaunchedEffect
            snapshotFlow { answerScrollState.maxValue }
                .collect { maxVal ->
                    if (autoFollowStream && maxVal > 0) {
                        answerScrollState.animateScrollTo(
                            value = maxVal,
                            animationSpec = tween(
                                durationMillis = 120,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    }
                }
        }

        // Final scroll pass when answer generation completes
        LaunchedEffect(uiState.pipelineState) {
            if (uiState.pipelineState is AssistantState.AnswerReady && autoFollowStream && answerScrollState.maxValue > 0) {
                answerScrollState.animateScrollTo(
                    value = answerScrollState.maxValue,
                    animationSpec = tween(durationMillis = 160)
                )
            }
        }

        Box(
            modifier = modifier
                .padding(6.dp)
                .width(overlayWidth)
                .height(overlayHeight)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black,
                    spotColor = OledAccentIndigo.copy(alpha = 0.40f)
                )
                .background(Color(0xF506070A), RoundedCornerShape(16.dp))
                .border(0.5.dp, OledBorderHigh, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                // Top Drag Handle & Sleek Header Bar
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
                    // Center Drag Indicator Bar
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
                            is AssistantState.DetectingSpeech -> "SPEECH"
                            is AssistantState.GeneratingAnswer -> "AI ACTIVE"
                            is AssistantState.AnswerReady -> "READY"
                            is AssistantState.Paused -> "PAUSED"
                            is AssistantState.Error -> "ERROR"
                        }

                        // Left: Status Dot & Label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.size(7.dp).background(statusColor, CircleShape))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = OledTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (uiState.isThermalThrottled) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(OledStatusAmber.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                        .border(0.5.dp, OledStatusAmber, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "HOT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = OledStatusAmber
                                    )
                                }
                            }
                        }

                        // Middle: View Mode Tabs (Answer vs Transcripts)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(OledSurfaceHigh)
                                .border(0.5.dp, OledBorder, RoundedCornerShape(16.dp))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HudTab.values().forEach { tab ->
                                val isTabSelected = (selectedTab == tab)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isTabSelected) OledAccentIndigo else Color.Transparent)
                                        .clickable { selectedTab = tab }
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                        .semantics { contentDescription = "Switch to ${tab.title} view" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tab.title,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isTabSelected) Color.White else OledTextSecondary
                                        )
                                        if (tab == HudTab.TRANSCRIPTS && uiState.transcriptHistory.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isTabSelected) Color.White.copy(alpha = 0.25f) else OledBorderHigh,
                                                        CircleShape
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "${uiState.transcriptHistory.size}",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isTabSelected) Color.White else OledTextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Right: Quick Size Preset Toggle (S/M/L)
                        val currentPreset = when {
                            overlayWidth <= 340.dp && overlayHeight <= 380.dp -> HudSizePreset.COMPACT
                            overlayWidth >= 430.dp || overlayHeight >= 600.dp -> HudSizePreset.EXPANDED
                            else -> HudSizePreset.STANDARD
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val nextPreset = when (currentPreset) {
                                        HudSizePreset.COMPACT -> HudSizePreset.STANDARD
                                        HudSizePreset.STANDARD -> HudSizePreset.EXPANDED
                                        HudSizePreset.EXPANDED -> HudSizePreset.COMPACT
                                    }
                                    overlayWidth = nextPreset.width.coerceIn(minWidthDp, maxWidthDp)
                                    overlayHeight = nextPreset.height.coerceIn(minHeightDp, maxHeightDp)
                                    onResize?.invoke(overlayWidth.value, overlayHeight.value)
                                }
                                .semantics { contentDescription = "Cycle overlay size: current is ${currentPreset.label}" },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(OledSurfaceHigh, CircleShape)
                                    .border(0.5.dp, OledBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentPreset.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = OledAccentIndigoSoft
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Minimize Button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onToggleMinimize() }
                                .semantics { contentDescription = "Minimize HUD overlay" },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(OledSurfaceHigh, CircleShape)
                                    .border(0.5.dp, OledBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                PrompterIcons.MinimizeLine(color = OledTextSecondary, size = 10.dp)
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Close Button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onCloseService() }
                                .semantics { contentDescription = "Close assistant overlay" },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(OledSurfaceHigh, CircleShape)
                                    .border(0.5.dp, OledBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                PrompterIcons.CloseCross(color = OledTextSecondary, size = 10.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Primary Content View (Tabs: Answer or Transcripts)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedTab == HudTab.ANSWER) {
                        // TAB 1: ANSWER / TELEPROMPTER VIEW
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(answerScrollState)
                        ) {
                            if (uiState.activeQuestion != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(OledSurfaceHigh, RoundedCornerShape(8.dp))
                                        .border(0.5.dp, OledBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(14.dp)
                                            .background(OledAccentIndigo, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = uiState.activeQuestion,
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp),
                                        color = OledTextPrimary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            if (!uiState.activeAnswer.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "AI RESPONSE",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OledAccentIndigoSoft
                                        )
                                        if (uiState.lastTtftMs > 0) {
                                            Text(
                                                text = " • ${uiState.lastTtftMs}ms",
                                                fontSize = 9.sp,
                                                color = OledTextMuted
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Copy Answer Button
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(OledSurfaceHigh)
                                                .clickable {
                                                    clipboardManager.setText(AnnotatedString(uiState.activeAnswer))
                                                }
                                                .semantics { contentDescription = "Copy AI response to clipboard" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PrompterIcons.Copy(color = OledTextSecondary, size = 12.dp)
                                        }

                                        // Report Button
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(OledSurfaceHigh)
                                                .clickable { showReportModal = true }
                                                .semantics { contentDescription = "Report AI Response" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PrompterIcons.Flag(color = OledTextSecondary, size = 12.dp)
                                        }
                                    }
                                }

                                FormattedTeleprompterAnswer(
                                    text = uiState.activeAnswer,
                                    fontSize = 14.sp
                                )
                            } else if (uiState.pipelineState is AssistantState.GeneratingAnswer) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = OledStatusAmber
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Synthesizing answer...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontStyle = FontStyle.Italic
                                        ),
                                        color = OledStatusAmber
                                    )
                                }
                            } else {
                                // Welcoming, structured Empty State
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
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
                                        text = "Listening for interview questions",
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                        color = OledTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Speak naturally or tap 'Answer Now' to answer recent speech.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = OledTextMuted,
                                        textAlign = TextAlign.Center
                                    )
                                    if (uiState.transcriptHistory.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "View ${uiState.transcriptHistory.size} recorded transcripts ->",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                color = OledAccentIndigoSoft
                                            ),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { selectedTab = HudTab.TRANSCRIPTS }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Floating button to resume auto-follow when user has scrolled upward
                        if (!autoFollowStream && answerScrollState.maxValue > 0 && answerScrollState.value < answerScrollState.maxValue - 32) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                                    .shadow(6.dp, RoundedCornerShape(50))
                                    .clip(RoundedCornerShape(50))
                                    .background(OledAccentIndigo.copy(alpha = 0.94f))
                                    .clickable {
                                        autoFollowStream = true
                                        coroutineScope.launch {
                                            answerScrollState.animateScrollTo(
                                                value = answerScrollState.maxValue,
                                                animationSpec = tween(durationMillis = 160)
                                            )
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .semantics { contentDescription = "Resume auto-scroll to latest answer text" },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PrompterIcons.ChevronUp(
                                        color = Color.White,
                                        size = 10.dp,
                                        modifier = Modifier.rotate(180f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Auto-scroll",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        // TAB 2: TRANSCRIPTS / CONVERSATION HISTORY VIEW
                        if (uiState.transcriptHistory.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                PrompterIcons.ActivityBar(color = OledTextMuted, size = 26.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No speech captured yet",
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp),
                                    color = OledTextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Spoken interview questions will appear here in real-time.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = OledTextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "TAP ANY TURN TO GENERATE ANSWER",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OledTextMuted,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )

                                uiState.transcriptHistory.asReversed().forEachIndexed { index, segment ->
                                    val originalTurnNumber = uiState.transcriptHistory.size - index
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (segment.isLikelyQuestion) OledSurfaceElevated else OledSurfaceHigh
                                            )
                                            .border(
                                                0.5.dp,
                                                if (segment.isLikelyQuestion) OledAccentIndigo.copy(alpha = 0.5f) else OledBorder,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                onSelectSegment(segment)
                                                selectedTab = HudTab.ANSWER
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                            .semantics {
                                                contentDescription = "Turn $originalTurnNumber: ${segment.text}"
                                            }
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (segment.isLikelyQuestion) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                OledAccentIndigo.copy(alpha = 0.22f),
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .border(0.5.dp, OledAccentIndigoSoft, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "QUESTION",
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = OledAccentIndigoSoft
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = "TURN $originalTurnNumber",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = OledTextMuted
                                                    )
                                                }

                                                Text(
                                                    text = "Answer this ->",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = OledAccentIndigoSoft
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = segment.text,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = OledTextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Compact Live STT Bar (Clickable to switch to Transcripts)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(OledSurface)
                        .border(
                            0.5.dp,
                            if (isDetectingSpeech) OledStatusEmerald.copy(alpha = 0.45f) else OledBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            selectedTab = if (selectedTab == HudTab.ANSWER) HudTab.TRANSCRIPTS else HudTab.ANSWER
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrompterIcons.Microphone(
                        color = if (isDetectingSpeech) OledStatusEmerald else OledTextMuted,
                        size = 11.dp,
                        modifier = Modifier.alpha(if (isDetectingSpeech) micPulseAlpha else 1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val transcriptText = when {
                        uiState.livePartialTranscript.isNotBlank() -> "${uiState.livePartialTranscript}..."
                        uiState.transcriptHistory.isNotEmpty() -> uiState.transcriptHistory.last().text
                        else -> "Mic listening (16 kHz mono)"
                    }
                    Text(
                        text = transcriptText,
                        fontSize = 11.sp,
                        color = if (isDetectingSpeech) OledStatusEmerald else OledTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Persistent Micro-Disclaimer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                        .semantics {
                            contentDescription = "AI hallucination notice: AI output may contain inaccuracies."
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrompterIcons.AlertCircle(color = OledTextMuted, size = 9.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AI output may contain inaccuracies.",
                            fontSize = 9.sp,
                            color = OledTextMuted
                        )
                    }
                    if (!uiState.activeAnswer.isNullOrBlank()) {
                        Text(
                            text = "Report",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OledAccentIndigoSoft,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showReportModal = true }
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                .semantics { contentDescription = "Report AI-generated answer" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer Action Buttons & Corner Resize Handle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Primary CTA: Answer Now
                    Button(
                        onClick = onForceAnswer,
                        enabled = uiState.pipelineState !is AssistantState.Paused &&
                                uiState.pipelineState !is AssistantState.GeneratingAnswer,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(36.dp)
                            .semantics { contentDescription = "Force immediate answer generation" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OledAccentIndigo,
                            disabledContainerColor = OledSurfaceHigh
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Answer Now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.pipelineState !is AssistantState.Paused &&
                                uiState.pipelineState !is AssistantState.GeneratingAnswer) Color.White else OledTextMuted
                        )
                    }

                    // Secondary Action: Pause / Resume
                    OutlinedButton(
                        onClick = onPauseToggle,
                        modifier = Modifier
                            .weight(1.0f)
                            .height(36.dp)
                            .semantics {
                                contentDescription = if (uiState.pipelineState is AssistantState.Paused)
                                    "Resume assistant listening" else "Pause assistant listening"
                            },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = OledSurface,
                            contentColor = OledTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, OledBorderHigh),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (uiState.pipelineState is AssistantState.Paused) "Resume" else "Pause",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OledTextPrimary
                        )
                    }

                    // Tertiary Action: Clear
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier
                            .weight(0.7f)
                            .height(36.dp)
                            .semantics { contentDescription = "Clear current answer and transcript" },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Clear",
                            fontSize = 12.sp,
                            color = OledTextSecondary
                        )
                    }

                    // Bottom-Right Corner Resize Grip Handle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val dxDp = with(density) { dragAmount.x.toDp() }
                                    val dyDp = with(density) { dragAmount.y.toDp() }
                                    val newW = (overlayWidth + dxDp).coerceIn(minWidthDp, maxWidthDp)
                                    val newH = (overlayHeight + dyDp).coerceIn(minHeightDp, maxHeightDp)
                                    overlayWidth = newW
                                    overlayHeight = newH
                                    onResize?.invoke(newW.value, newH.value)
                                }
                            }
                            .semantics { contentDescription = "Drag to resize overlay window" },
                        contentAlignment = Alignment.Center
                    ) {
                        PrompterIcons.ResizeGrip(color = OledTextMuted, size = 14.dp)
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
