package com.edgecue.app.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgecue.app.data.history.AiReportReason
import com.edgecue.app.ui.theme.*

/**
 * Standard Material 3 Dialog for reporting or flagging AI-generated answers (Requirement R2, P0-03).
 * Displayed in the activity-hosted UI (TeleprompterDisplay / PrompterScreen).
 */
@Composable
fun ReportAiContentDialog(
    question: String?,
    answer: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: AiReportReason, comment: String?) -> Unit
) {
    var selectedReason by remember { mutableStateOf(AiReportReason.FACTUAL_INACCURACY) }
    var userComment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrompterIcons.Flag(color = OledStatusAmber, size = 18.dp)
                Text(
                    text = "Report AI Response",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OledTextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Google Play requires user reporting mechanisms for AI-generated text. Reports are stored locally and open in your email addressed to the engineering team for prompt review.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = OledTextSecondary
                )

                // Response Snippet Preview
                Card(
                    colors = CardDefaults.cardColors(containerColor = OledSurfaceHigh),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        if (!question.isNullOrBlank()) {
                            Text(
                                text = "Q: $question",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OledAccentIndigoSoft,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = "AI: $answer",
                            fontSize = 11.sp,
                            color = OledTextPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "Reason for Report",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OledTextPrimary
                )

                // Reason Selection Options
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
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedReason = reason },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = OledAccentIndigo,
                                unselectedColor = OledTextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = reason.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) OledTextPrimary else OledTextSecondary
                            )
                            Text(
                                text = reason.description,
                                fontSize = 10.sp,
                                color = OledTextMuted,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                // Optional User Comment
                OutlinedTextField(
                    value = userComment,
                    onValueChange = { userComment = it },
                    label = { Text("Additional details (optional)", fontSize = 11.sp) },
                    placeholder = { Text("Describe the inaccuracy or issue...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = OledTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OledAccentIndigo,
                        unfocusedBorderColor = OledBorder,
                        focusedLabelColor = OledAccentIndigoSoft,
                        unfocusedLabelColor = OledTextMuted
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val commentOrNull = userComment.trim().ifEmpty { null }
                    onSubmit(selectedReason, commentOrNull)
                },
                colors = ButtonDefaults.buttonColors(containerColor = OledAccentIndigo),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Submit Report",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OledTextMuted, fontSize = 13.sp)
            }
        }
    )
}
