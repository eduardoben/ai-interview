package com.local.interviewassistant.ui.usage

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.local.interviewassistant.data.history.AggregateTelemetry
import com.local.interviewassistant.data.history.QuestionAnswerRecord
import com.local.interviewassistant.ui.prompter.FormattedTeleprompterAnswer
import com.local.interviewassistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    telemetry: AggregateTelemetry,
    records: List<QuestionAnswerRecord>,
    onExportMarkdown: () -> String,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    hardwareSocName: String? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val sdf = remember(configuration) {
        val locale = ConfigurationCompat.getLocales(configuration).get(0) ?: Locale.US
        SimpleDateFormat("MMM dd, HH:mm", locale)
    }
    var selectedRecord by remember { mutableStateOf<QuestionAnswerRecord?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    "Clear Session History?",
                    color = StealthTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "This permanently deletes all saved interview Q&A records and resets local telemetry metrics.",
                    color = StealthTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearHistory()
                }) {
                    Text("Clear All", color = StealthStatusCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = StealthTextSecondary)
                }
            },
            containerColor = StealthSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (selectedRecord != null) {
        val record = selectedRecord!!
        ModalBottomSheet(
            onDismissRequest = { selectedRecord = null },
            containerColor = StealthSurface,
            contentColor = StealthTextPrimary,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(StealthAccentIndigo.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = record.category.displayName,
                            fontSize = 11.sp,
                            color = StealthAccentIndigoSoft,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${sdf.format(Date(record.timestamp))} • ${record.ttftMs}ms TTFT",
                        fontSize = 11.sp,
                        color = StealthTextMuted
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = record.question,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                    color = StealthTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = StealthBorder)
                Spacer(modifier = Modifier.height(12.dp))

                FormattedTeleprompterAnswer(
                    text = record.answer,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        shareText(context, "**Question:** ${record.question}\n\n**Answer:**\n${record.answer}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StealthAccentIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Share Q&A", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StealthBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Activity & Telemetry",
                    style = MaterialTheme.typography.headlineSmall,
                    color = StealthTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "On-device inference performance & saved Q&A log",
                    style = MaterialTheme.typography.bodySmall,
                    color = StealthTextMuted
                )
            }

            if (records.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val md = onExportMarkdown()
                            shareText(context, md)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Export", fontSize = 12.sp, color = StealthAccentIndigoSoft, fontWeight = FontWeight.Medium)
                    }
                    TextButton(
                        onClick = { showClearDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Clear", fontSize = 12.sp, color = StealthStatusCoral, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2x2 Telemetry Grid with honest baseline states
        val hasMetrics = telemetry.totalQuestionsAnswered > 0
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "AVG FIRST TOKEN",
                value = if (hasMetrics && telemetry.avgTtftMs > 0) "${telemetry.avgTtftMs} ms" else "—",
                subtitle = hardwareSocName ?: "On-Device Accelerator",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "DECODE SPEED",
                value = if (hasMetrics && telemetry.avgDecodeTps > 0f) String.format(Locale.US, "%.1f tok/s", telemetry.avgDecodeTps) else "—",
                subtitle = "Streaming Generation",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "QUESTIONS ANSWERED",
                value = "${telemetry.totalQuestionsAnswered}",
                subtitle = "${telemetry.totalTokensGenerated} total tokens",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "RUNTIME ISOLATION",
                value = "Serialized",
                subtitle = "Single-Thread JNI Guard",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "SAVED INTERVIEW HISTORY",
            style = MaterialTheme.typography.labelSmall,
            color = StealthTextMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(StealthSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, StealthBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(StealthSurfaceHigh, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        StealthIcons.ActivityBar(color = StealthTextMuted, size = 18.dp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Saved Responses Yet",
                        color = StealthTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Generated answers from live sessions will appear here.",
                        color = StealthTextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(records) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StealthSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(0.5.dp, StealthBorder, RoundedCornerShape(14.dp))
                            .clickable { selectedRecord = item }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.category.displayName.uppercase(),
                                    fontSize = 10.sp,
                                    letterSpacing = 0.6.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StealthAccentIndigoSoft
                                )
                                Text(
                                    text = sdf.format(Date(item.timestamp)),
                                    fontSize = 11.sp,
                                    color = StealthTextMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.question,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StealthTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.answer.replace("**", ""),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = StealthTextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StealthSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(0.5.dp, StealthBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = StealthTextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                color = StealthTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = StealthTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Interview Session"))
}
