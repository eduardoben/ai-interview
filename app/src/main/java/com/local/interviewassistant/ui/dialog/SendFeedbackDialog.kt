package com.local.interviewassistant.ui.dialog

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.ui.theme.*

/**
 * User-initiated feedback dialog allowing the user to compose feedback and explicitly opt in
 * to attaching diagnostic logs and/or session transcripts before launching Android's share chooser.
 */
@Composable
fun SendFeedbackDialog(
    hardwareProfile: HardwareProfile? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(FeedbackCategory.GENERAL) }
    var userMessage by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(false) }
    var includeTranscripts by remember { mutableStateOf(false) }

    val canSend = userMessage.isNotBlank() || includeLogs || includeTranscripts

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrompterIcons.Prompter(color = OledAccentIndigoSoft, size = 18.dp)
                Text(
                    text = "Send Feedback",
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
                    text = "Share bug reports, feature requests, or quality feedback. Nothing is transmitted automatically—your feedback opens in your email or sharing app so you can review everything before sending.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = OledTextSecondary
                )

                Text(
                    text = "Category",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OledTextPrimary
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FeedbackCategory.values().forEach { category ->
                        val isSelected = (category == selectedCategory)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = category }
                                .background(
                                    color = if (isSelected) OledSurfaceHigh else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = OledAccentIndigo,
                                    unselectedColor = OledTextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = category.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) OledTextPrimary else OledTextSecondary
                                )
                                Text(
                                    text = category.description,
                                    fontSize = 10.sp,
                                    color = OledTextMuted,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = userMessage,
                    onValueChange = { userMessage = it },
                    label = { Text("Your feedback or issue description", fontSize = 11.sp) },
                    placeholder = { Text("What went well, or what can we improve?", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = OledTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OledAccentIndigo,
                        unfocusedBorderColor = OledBorder,
                        focusedLabelColor = OledAccentIndigoSoft,
                        unfocusedLabelColor = OledTextMuted
                    )
                )

                Text(
                    text = "Optional Attachments (Opt-In)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OledTextPrimary
                )

                FeedbackAttachmentOptionRow(
                    title = "Include Diagnostic Logs",
                    subtitle = "Attach hardware profile, model status, and recent process logs",
                    checked = includeLogs,
                    onCheckedChange = { includeLogs = it }
                )

                FeedbackAttachmentOptionRow(
                    title = "Include Session Transcripts",
                    subtitle = "Attach recorded Q&A history and latency metrics from local storage",
                    checked = includeTranscripts,
                    onCheckedChange = { includeTranscripts = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val subject = FeedbackPayloadBuilder.buildSubject(selectedCategory)
                    val body = FeedbackPayloadBuilder.buildBody(
                        category = selectedCategory,
                        userMessage = userMessage,
                        includeLogs = includeLogs,
                        includeTranscripts = includeTranscripts,
                        diagnosticLogsProvider = {
                            FeedbackPayloadBuilder.collectDeviceDiagnostics(context, hardwareProfile)
                        },
                        transcriptsProvider = {
                            FeedbackPayloadBuilder.collectSessionTranscripts(context)
                        }
                    )

                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConfig.FEEDBACK_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Send Feedback"))
                    onDismiss()
                },
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(containerColor = OledAccentIndigo),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Send Feedback",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = OledTextSecondary,
                    fontSize = 13.sp
                )
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun FeedbackAttachmentOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .background(
                color = if (checked) OledSurfaceHigh else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                width = 0.5.dp,
                color = if (checked) OledAccentIndigo.copy(alpha = 0.5f) else OledBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = OledAccentIndigo,
                uncheckedColor = OledTextMuted,
                checkmarkColor = Color.White
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = OledTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = OledTextMuted
            )
        }
    }
}

