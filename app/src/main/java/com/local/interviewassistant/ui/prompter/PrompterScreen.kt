package com.local.interviewassistant.ui.prompter

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.local.interviewassistant.ui.theme.*

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollState = rememberScrollState()
    var showTranscriptSheet by remember { mutableStateOf(false) }
    var customQuestionInput by remember { mutableStateOf("") }
    var showDimmerDialog by remember { mutableStateOf(false) }
    var autoFollowStream by remember { mutableStateOf(true) }

    // Disable auto-follow if the user manually drags upward to inspect earlier lines
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && scrollState.value < scrollState.maxValue - 48) {
            autoFollowStream = false
        }
    }

    // Reset scroll to top and re-enable auto-follow when a new question starts
    LaunchedEffect(uiState.activeQuestion) {
        if (uiState.activeQuestion != null) {
            autoFollowStream = true
            scrollState.scrollTo(0)
        }
    }

    // Smoothly auto-scroll as generated answer tokens stream in and expand the layout
    LaunchedEffect(uiState.activeAnswer, scrollState.maxValue) {
        if (autoFollowStream && !uiState.activeAnswer.isNullOrBlank() && scrollState.maxValue > 0) {
            scrollState.animateScrollTo(
                value = scrollState.maxValue,
                animationSpec = tween(durationMillis = 160)
            )
        }
    }

    val (rawStatusColor, statusText) = when (uiState.pipelineState) {
        is AssistantState.Listening -> StealthStatusEmerald to "Listening"
        is AssistantState.DetectingSpeech -> StealthStatusEmerald to "Speech Active"
        is AssistantState.GeneratingAnswer -> StealthStatusAmber to "Synthesizing"
        is AssistantState.AnswerReady -> StealthAccentIndigoSoft to "Answer Ready"
        is AssistantState.Paused -> StealthStatusAmber to "Paused"
        is AssistantState.Error -> StealthStatusCoral to "Error"
        is AssistantState.Initializing -> StealthStatusAmber to "Loading Models"
        is AssistantState.Idle -> StealthTextMuted to (if (isServiceRunning) "Starting" else "Standby")
    }

    val statusDotColor by animateColorAsState(
        targetValue = rawStatusColor,
        animationSpec = tween(durationMillis = 300),
        label = "StatusDotColor"
    )

    val isActivelyListening = uiState.pipelineState is AssistantState.Listening ||
            uiState.pipelineState is AssistantState.DetectingSpeech

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isActivelyListening) 0.85f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    if (showDimmerDialog) {
        AlertDialog(
            onDismissRequest = { showDimmerDialog = false },
            title = {
                Text(
                    "Stealth OLED Dimmer",
                    color = StealthTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        "Reduces screen luminance to eliminate glare on eyeglasses or webcams during low-light video calls.",
                        color = StealthTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Luminance Reduction", color = StealthTextSecondary, fontSize = 12.sp)
                        Text(
                            "${(stealthDimmerAlpha * 100).toInt()}%",
                            color = StealthTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = stealthDimmerAlpha,
                        onValueChange = onDimmerChange,
                        valueRange = 0f..0.70f,
                        steps = 6,
                        colors = SliderDefaults.colors(
                            thumbColor = StealthAccentIndigo,
                            activeTrackColor = StealthAccentIndigo,
                            inactiveTrackColor = StealthSurfaceHigh
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDimmerDialog = false }) {
                    Text("Done", color = StealthAccentIndigoSoft, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = StealthSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showTranscriptSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTranscriptSheet = false },
            containerColor = StealthSurface,
            contentColor = StealthTextPrimary,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Live Transcript & Prompt",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StealthTextPrimary
                    )
                    Text(
                        "${uiState.transcriptHistory.size} segments",
                        fontSize = 11.sp,
                        color = StealthTextMuted
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customQuestionInput,
                        onValueChange = { customQuestionInput = it },
                        placeholder = {
                            Text(
                                "Type or refine a question...",
                                fontSize = 13.sp,
                                color = StealthTextMuted
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StealthAccentIndigo,
                            unfocusedBorderColor = StealthBorder,
                            focusedTextColor = StealthTextPrimary,
                            unfocusedTextColor = StealthTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (customQuestionInput.isNotBlank()) {
                                onCustomQuestionSubmit(customQuestionInput.trim())
                                customQuestionInput = ""
                                showTranscriptSheet = false
                            }
                        },
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StealthAccentIndigo),
                        enabled = customQuestionInput.isNotBlank()
                    ) {
                        Text("Ask", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val listState = rememberLazyListState()
                if (uiState.transcriptHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No speech segments recorded yet.",
                            fontSize = 12.sp,
                            color = StealthTextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.transcriptHistory) { segment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (segment.isLikelyQuestion) StealthSurfaceHigh else Color.Transparent)
                                    .border(
                                        0.5.dp,
                                        if (segment.isLikelyQuestion) StealthAccentIndigo.copy(alpha = 0.4f) else StealthBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onSelectTranscriptSegment(segment)
                                        showTranscriptSheet = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (segment.isLikelyQuestion) {
                                    Box(
                                        modifier = Modifier
                                            .background(StealthAccentIndigo.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "Q",
                                            color = StealthAccentIndigoSoft,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    segment.text,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = if (segment.isLikelyQuestion) StealthTextPrimary else StealthTextSecondary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
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
            // 1. Minimal Status Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(StealthSurface, RoundedCornerShape(50))
                        .border(0.5.dp, StealthBorder, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
                        if (isActivelyListening) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(statusDotColor.copy(alpha = pulseAlpha * 0.35f), CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(statusDotColor, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = statusText.uppercase(),
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (uiState.pipelineState is AssistantState.Idle && !isServiceRunning) {
                            StealthTextSecondary
                        } else {
                            statusDotColor
                        }
                    )
                    if (uiState.lastTtftMs > 0L) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${uiState.lastTtftMs}ms",
                            fontSize = 10.sp,
                            color = StealthTextMuted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Hardware & Persona Context Pill
                    val socBadge = hardwareSocName?.takeIf { it.isNotBlank() } ?: "On-Device AI"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(StealthSurface)
                            .border(0.5.dp, StealthBorder, RoundedCornerShape(50))
                            .clickable { onNavigateToSettings() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        StealthIcons.CpuChip(color = StealthAccentIndigoSoft, size = 13.dp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = socBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = StealthTextSecondary
                        )
                    }

                    // Stealth Dimmer Action Button (36dp clean hit target)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (stealthDimmerAlpha > 0f) StealthAccentIndigo.copy(alpha = 0.16f) else StealthSurface)
                            .border(0.5.dp, if (stealthDimmerAlpha > 0f) StealthAccentIndigo.copy(alpha = 0.5f) else StealthBorder, CircleShape)
                            .clickable { showDimmerDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        StealthIcons.DimmerMoon(
                            color = if (stealthDimmerAlpha > 0f) StealthAccentIndigoSoft else StealthTextSecondary,
                            isDimmed = stealthDimmerAlpha > 0f,
                            size = 15.dp
                        )
                    }

                    // Focus Mode Toggle Button (36dp clean hit target)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isFocusMode) StealthAccentIndigo.copy(alpha = 0.16f) else StealthSurface)
                            .border(0.5.dp, if (isFocusMode) StealthAccentIndigo.copy(alpha = 0.5f) else StealthBorder, CircleShape)
                            .clickable { onToggleFocusMode() },
                        contentAlignment = Alignment.Center
                    ) {
                        StealthIcons.FocusExpand(
                            color = if (isFocusMode) StealthAccentIndigoSoft else StealthTextSecondary,
                            isExpanded = isFocusMode,
                            size = 15.dp
                        )
                    }
                }
            }

            // 2. Minimal Category Filter Strip (Collapses in Focus Mode for maximum reading room)
            AnimatedVisibility(visible = !isFocusMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InterviewCategory.entries.forEach { category ->
                        val isSelected = uiState.selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) StealthAccentIndigo.copy(alpha = 0.18f) else StealthSurface
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) StealthAccentIndigo else StealthBorder,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { onSelectCategory(category) }
                                .padding(horizontal = 13.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = category.displayName,
                                fontSize = 11.sp,
                                letterSpacing = 0.2.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) StealthTextPrimary else StealthTextSecondary
                            )
                        }
                    }
                }
            }

            if (isFocusMode) {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 3. Top-Weighted Teleprompter Reading Zone
            Box(
                modifier = Modifier
                    .weight(1f)
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
                    if (uiState.activeQuestion != null) {
                        // Elegant left-accented Question Callout instead of a heavy bordered box
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
                                text = uiState.activeQuestion,
                                color = StealthTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (!uiState.activeAnswer.isNullOrBlank()) {
                        FormattedTeleprompterAnswer(
                            text = uiState.activeAnswer,
                            fontSize = fontSizeSp.sp
                        )
                    } else if (uiState.pipelineState is AssistantState.GeneratingAnswer) {
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
                    } else if (uiState.pipelineState is AssistantState.Initializing) {
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
                    } else if (uiState.pipelineState is AssistantState.Error) {
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
                                text = uiState.pipelineState.message,
                                color = StealthStatusCoral,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    } else if (uiState.livePartialTranscript.isNotBlank() || uiState.transcriptHistory.isNotEmpty()) {
                        if (uiState.livePartialTranscript.isNotBlank()) {
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
                                    text = "${uiState.livePartialTranscript}...",
                                    color = StealthStatusEmerald,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 21.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        if (uiState.transcriptHistory.isNotEmpty()) {
                            Text(
                                text = "RECENT SPEECH • TAP TO ANSWER",
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StealthTextMuted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.transcriptHistory.takeLast(5).reversed().forEach { seg ->
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
                        // Minimal, centered empty state anchored comfortably in upper-middle reading zone
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

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Collapsible Live Transcript Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StealthSurface)
                    .border(
                        0.5.dp,
                        if (uiState.livePartialTranscript.isNotBlank()) StealthStatusEmerald.copy(alpha = 0.55f) else StealthBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { showTranscriptSheet = true }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    StealthIcons.Microphone(
                        color = if (uiState.livePartialTranscript.isNotBlank()) StealthStatusEmerald else StealthTextMuted,
                        size = 14.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    val transcriptPreview = when {
                        uiState.livePartialTranscript.isNotBlank() -> "${uiState.livePartialTranscript}..."
                        uiState.transcriptHistory.isNotEmpty() -> uiState.transcriptHistory.last().text
                        isServiceRunning -> "Listening... tap to type or view transcript"
                        else -> "Tap to type a custom question or view history"
                    }
                    Text(
                        text = transcriptPreview,
                        color = if (uiState.livePartialTranscript.isNotBlank()) StealthStatusEmerald else StealthTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Prompt",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = StealthAccentIndigoSoft
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    StealthIcons.ChevronUp(color = StealthAccentIndigoSoft, size = 12.dp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Thumb-Zone Control Dock
            if (!isServiceRunning) {
                Button(
                    onClick = onStartService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StealthAccentIndigo,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "Start Live Copilot",
                        fontSize = 15.sp,
                        letterSpacing = 0.2.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onForceAnswer,
                        modifier = Modifier
                            .weight(1.4f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StealthAccentIndigo),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Answer Now",
                            fontSize = 13.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onPauseToggle,
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = StealthSurface,
                            contentColor = StealthTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, StealthBorderHigh)
                    ) {
                        Text(
                            text = if (uiState.pipelineState is AssistantState.Paused) "Resume" else "Pause",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = StealthSurface,
                            contentColor = StealthTextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, StealthBorder)
                    ) {
                        Text("Clear", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onStopService,
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = StealthStatusCoral.copy(alpha = 0.1f),
                            contentColor = StealthStatusCoral
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, StealthStatusCoral.copy(alpha = 0.35f))
                    ) {
                        Text("Stop", fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
