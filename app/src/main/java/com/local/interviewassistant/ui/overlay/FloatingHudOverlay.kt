package com.local.interviewassistant.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.CopilotUiState
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.core.model.TranscriptSegment

@OptIn(ExperimentalMaterial3Api::class)
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
    val statusColor = when (uiState.pipelineState) {
        is AssistantState.Listening, is AssistantState.DetectingSpeech -> Color.Green
        is AssistantState.GeneratingAnswer, is AssistantState.AnswerReady -> Color.Cyan
        is AssistantState.Paused -> Color(0xFFFFC107) // Amber
        is AssistantState.Error -> Color.Red
        else -> Color.White
    }

    if (uiState.isMinimized) {
        Row(
            modifier = modifier
                .background(Color(0xDD121212), RoundedCornerShape(50))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { onToggleMinimize() }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text("AI Copilot", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        val listState = rememberLazyListState()
        val scrollState = rememberScrollState()

        LaunchedEffect(uiState.transcriptHistory.size, uiState.livePartialTranscript) {
            if (uiState.transcriptHistory.isNotEmpty() || uiState.livePartialTranscript.isNotBlank()) {
                listState.animateScrollToItem(uiState.transcriptHistory.size)
            }
        }

        LaunchedEffect(uiState.activeAnswer) {
            if (uiState.activeAnswer != null) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }

        Column(
            modifier = modifier
                .width(340.dp)
                .heightIn(max = 380.dp)
                .background(Color(0xD1121212), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = when (uiState.pipelineState) {
                    is AssistantState.Idle -> "IDLE"
                    is AssistantState.Initializing -> "INITIALIZING"
                    is AssistantState.Listening -> "LISTENING"
                    is AssistantState.DetectingSpeech -> "SPEECH DETECTED"
                    is AssistantState.GeneratingAnswer -> "GENERATING"
                    is AssistantState.AnswerReady -> "ANSWER READY"
                    is AssistantState.Paused -> "PAUSED"
                    is AssistantState.Error -> "ERROR"
                }

                Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$statusText | TTFT: ${uiState.lastTtftMs}ms",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleMinimize, modifier = Modifier.size(24.dp)) {
                    Text("_", color = Color.White, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onCloseService, modifier = Modifier.size(24.dp)) {
                    Text("X", color = Color.White, fontSize = 14.sp)
                }
            }

            // Quick-Action Mode Category Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InterviewCategory.entries.forEach { category ->
                    val isSelected = uiState.selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category.displayName, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF03DAC5),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF263238),
                            labelColor = Color.White
                        ),
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zone 1: Live Transcript Stream
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(state = listState) {
                    items(uiState.transcriptHistory) { segment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onSelectSegment(segment) }
                                .let {
                                    if (segment.isLikelyQuestion) {
                                        it.border(1.dp, Color.Cyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(4.dp)
                                    } else {
                                        it.padding(4.dp)
                                    }
                                }
                        ) {
                            if (segment.isLikelyQuestion) {
                                Text("[Q] ", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = segment.text,
                                color = Color(0xFFB0BEC5),
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (uiState.livePartialTranscript.isNotBlank()) {
                        item {
                            Text(
                                text = "Listening: ${uiState.livePartialTranscript}...",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zone 2: Auto-Detected Question & AI Copilot Response
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
            ) {
                Text(
                    text = uiState.activeQuestion ?: "Waiting for interviewer question...",
                    color = if (uiState.activeQuestion != null) Color.Cyan else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {
                    if (uiState.activeAnswer != null) {
                        Text(
                            text = uiState.activeAnswer,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    } else if (uiState.pipelineState is AssistantState.Error) {
                        Text(
                            text = uiState.pipelineState.message,
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "Speak or tap any transcript line above to generate a response.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onPauseToggle, contentPadding = PaddingValues(4.dp)) {
                    Text(if (uiState.pipelineState is AssistantState.Paused) "Resume" else "Pause", color = Color.White, fontSize = 12.sp)
                }

                TextButton(
                    onClick = onForceAnswer,
                    enabled = (uiState.pipelineState is AssistantState.DetectingSpeech) || (uiState.pipelineState is AssistantState.Listening),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text(
                        "Answer Now",
                        color = if ((uiState.pipelineState is AssistantState.DetectingSpeech) || (uiState.pipelineState is AssistantState.Listening)) Color.White else Color.Gray,
                        fontSize = 12.sp,
                    )
                }

                TextButton(
                    onClick = onClear,
                    enabled = (uiState.activeQuestion != null) || (uiState.activeAnswer != null) || (uiState.pipelineState is AssistantState.Error) || uiState.transcriptHistory.isNotEmpty(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text("Clear", color = if ((uiState.activeQuestion != null) || (uiState.activeAnswer != null) || (uiState.pipelineState is AssistantState.Error) || uiState.transcriptHistory.isNotEmpty()) Color.White else Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
