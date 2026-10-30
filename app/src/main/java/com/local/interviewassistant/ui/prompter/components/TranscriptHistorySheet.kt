package com.local.interviewassistant.ui.prompter.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.ui.theme.*

/**
 * Bottom sheet displaying full conversation speech transcript segments and allowing
 * manual custom prompt submission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptHistorySheet(
    transcriptHistory: List<TranscriptSegment>,
    customQuestionInput: String,
    onCustomQuestionInputChange: (String) -> Unit,
    onCustomQuestionSubmit: (String) -> Unit,
    onSelectTranscriptSegment: (TranscriptSegment) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    "${transcriptHistory.size} segments",
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
                    onValueChange = onCustomQuestionInputChange,
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
            if (transcriptHistory.isEmpty()) {
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
                    items(transcriptHistory) { segment ->
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
                                .clickable { onSelectTranscriptSegment(segment) }
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

