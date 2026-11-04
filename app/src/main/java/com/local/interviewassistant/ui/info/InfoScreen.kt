package com.local.interviewassistant.ui.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.data.provisioning.ProvisioningState
import com.local.interviewassistant.ui.dialog.OpenSourceLicensesDialog
import com.local.interviewassistant.ui.prompter.FormattedTeleprompterAnswer
import com.local.interviewassistant.ui.theme.*
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.local.interviewassistant.core.model.AppConfig
import java.util.Locale

@Composable
fun InfoScreen(
    readiness: AppContainer.ReadinessStatus?,
    hardwareProfile: HardwareProfile?,
    provisioningState: ProvisioningState,
    isMeteredNetwork: Boolean,
    onGrantPermissionsClick: () -> Unit,
    onOpenOverlaySettingsClick: () -> Unit,
    onStartDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onImportLlmFileClick: () -> Unit,
    onRefreshDiskCheckClick: () -> Unit,
    onPurgeModelsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showFilesTable by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OledBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "System & Diagnostics",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OledTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Hardware profile, permissions, and model provisioning",
                    style = MaterialTheme.typography.bodySmall,
                    color = OledTextMuted
                )
            }

            TextButton(
                onClick = onRefreshDiskCheckClick,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "Refresh",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OledAccentIndigoSoft
                )
            }
        }

        // 1. Hardware Profile Card
        if (hardwareProfile != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = OledSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, OledBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(OledAccentIndigo.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                            .border(0.5.dp, OledAccentIndigo.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        PrompterIcons.CpuChip(color = OledAccentIndigoSoft, size = 22.dp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hardwareProfile.socName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OledTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${hardwareProfile.totalRamMb} MB RAM • ${hardwareProfile.preferredDelegate.name}",
                            fontSize = 12.sp,
                            color = OledStatusEmerald
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = hardwareProfile.recommendedLlmFileName,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = OledTextMuted
                        )
                    }
                }
            }
        }

        // 2. System Readiness Checklist
        Card(
            colors = CardDefaults.cardColors(containerColor = OledSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, OledBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Readiness",
                    style = MaterialTheme.typography.titleSmall,
                    color = OledTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                val hasAudio = readiness?.hasAudioPermission == true
                val hasNotif = readiness?.hasNotificationPermission == true
                val hasOverlay = readiness?.hasOverlayPermission == true
                val modelsReady = readiness?.modelReport?.allPresent == true

                ReadinessRow("Microphone Capture", hasAudio)
                ReadinessRow("Foreground Service Notifications", hasNotif)
                ReadinessRow("Floating HUD Overlay Permission", hasOverlay)
                ReadinessRow("On-Device Neural Models (9 Assets)", modelsReady)

                if (!hasAudio || !hasNotif || !hasOverlay) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!hasAudio || !hasNotif) {
                            Button(
                                onClick = onGrantPermissionsClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OledAccentIndigo)
                            ) {
                                Text("Grant Mic & Notif", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (!hasOverlay) {
                            OutlinedButton(
                                onClick = onOpenOverlaySettingsClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = OledSurfaceHigh,
                                    contentColor = OledTextPrimary
                                ),
                                border = BorderStroke(0.5.dp, OledBorderHigh)
                            ) {
                                Text("Enable Overlay", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // 3. Model Provisioning Status
        Card(
            colors = CardDefaults.cardColors(containerColor = OledSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, OledBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "On-Device AI Assets",
                        style = MaterialTheme.typography.titleSmall,
                        color = OledTextPrimary
                    )
                    TextButton(
                        onClick = { showFilesTable = !showFilesTable },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (showFilesTable) "Hide Manifest" else "Inspect Files",
                            fontSize = 11.sp,
                            color = OledAccentIndigoSoft
                        )
                    }
                }

                if (isMeteredNetwork && provisioningState !is ProvisioningState.Ready) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OledStatusAmber.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, OledStatusAmber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrompterIcons.AlertCircle(color = OledStatusAmber, size = 14.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Metered connection detected. Wi-Fi is recommended for multi-GB model downloads.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = OledStatusAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (provisioningState) {
                    is ProvisioningState.Ready -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OledStatusEmerald.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, OledStatusEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(OledStatusEmerald.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                PrompterIcons.Check(color = OledStatusEmerald, size = 14.dp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "All 9 Neural Pipeline Assets Verified",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OledStatusEmerald
                                )
                                Text(
                                    "Silero VAD • Zipformer ASR • Whisper Rescorer • Gemma 4",
                                    fontSize = 11.sp,
                                    color = OledTextSecondary
                                )
                            }
                        }
                    }

                    is ProvisioningState.NeedsDownload -> {
                        val mb = provisioningState.remainingBytes / (1024 * 1024)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onStartDownloadClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OledAccentIndigo)
                            ) {
                                Text(
                                    "Download Missing Assets (~$mb MB)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            OutlinedButton(
                                onClick = onImportLlmFileClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = OledSurfaceHigh,
                                    contentColor = OledTextSecondary
                                ),
                                border = BorderStroke(0.5.dp, OledBorder)
                            ) {
                                Text("Import Local .litertlm Model File", fontSize = 12.sp)
                            }
                        }
                    }

                    is ProvisioningState.Downloading -> {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = provisioningState.currentFileName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OledTextPrimary
                                )
                                Text(
                                    text = "${provisioningState.currentFileIndex} / 9",
                                    fontSize = 11.sp,
                                    color = OledTextMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val pct = if (provisioningState.totalBytesRequired > 0) {
                                (provisioningState.bytesDownloaded.toFloat() / provisioningState.totalBytesRequired.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = OledAccentIndigo,
                                trackColor = OledSurfaceHigh
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${(pct * 100).toInt()}% completed",
                                    fontSize = 11.sp,
                                    color = OledTextMuted
                                )
                                OutlinedButton(
                                    onClick = onCancelDownloadClick,
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = OledStatusCoral.copy(alpha = 0.1f),
                                        contentColor = OledStatusCoral
                                    ),
                                    border = BorderStroke(0.5.dp, OledStatusCoral.copy(alpha = 0.35f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    is ProvisioningState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = provisioningState.message,
                                fontSize = 12.sp,
                                color = OledStatusCoral
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onStartDownloadClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OledAccentIndigo)
                                ) {
                                    Text("Retry Download", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = onImportLlmFileClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.5.dp, OledBorderHigh)
                                ) {
                                    Text("Import File", fontSize = 12.sp, color = OledTextSecondary)
                                }
                            }
                        }
                    }

                    else -> {}
                }

                if (showFilesTable && readiness != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = OledBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    readiness.modelReport.files.forEach { file ->
                        val sizeMb = file.sizeBytes / (1024f * 1024f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                file.name,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OledTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (file.exists && file.sizeBytes > 0) String.format(Locale.US, "%.1f MB", sizeMb) else "MISSING",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = if (file.exists && file.sizeBytes > 0) OledStatusEmerald else OledStatusCoral
                            )
                        }
                    }
                }
            }
        }

        // 4. Companion Placement & Hardware Key Guide
        Card(
            colors = CardDefaults.cardColors(containerColor = OledSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, OledBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ergonomic Placement Guide",
                    style = MaterialTheme.typography.titleSmall,
                    color = OledTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                FormattedTeleprompterAnswer(
                    text = "• **Ergonomic Reading Position:** Position the device at a comfortable reading angle near your display to maintain natural posture during practice.\n" +
                            "• **Eye Comfort:** Use the display dimmer in low-light environments to reduce eye strain and optimize power efficiency during extended rehearsal sessions.\n" +
                            "• **Physical Controls:** Use **Volume Up** to trigger an answer or **Volume Down** to pause without interrupting your rehearsal flow.",
                    fontSize = 13.sp
                )
            }
        }

        // 5. Legal & Third-Party Attributions Card (R2 / P1-02)
        Card(
            colors = CardDefaults.cardColors(containerColor = OledSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, OledBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Legal & Third-Party Attributions",
                    style = MaterialTheme.typography.titleSmall,
                    color = OledTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Open source software notices, model licenses, and terms of use.",
                    fontSize = 11.sp,
                    color = OledTextMuted
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showLicensesDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = OledSurfaceHigh),
                    border = BorderStroke(0.5.dp, OledBorderHigh)
                ) {
                    Text(
                        text = "View Open Source Licenses & Attributions",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = OledTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.PRIVACY_POLICY_URL))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = OledSurfaceHigh),
                    border = BorderStroke(0.5.dp, OledBorderHigh)
                ) {
                    Text(
                        text = "Public Privacy Policy",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = OledTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showPurgeConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = OledStatusCoral.copy(alpha = 0.08f),
                        contentColor = OledStatusCoral
                    ),
                    border = BorderStroke(0.5.dp, OledStatusCoral.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Purge Downloaded AI Models (~3.1 GB)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OledStatusCoral
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showLicensesDialog) {
        OpenSourceLicensesDialog(
            onDismiss = { showLicensesDialog = false }
        )
    }

    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            title = {
                Text(
                    text = "Purge Downloaded Models?",
                    color = OledTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "This will delete all on-device neural model weights (Silero VAD, Zipformer ASR, Whisper Rescorer, and Gemma 4) to reclaim ~3.1 GB of device storage.\n\nYou can re-download them anytime from this screen.",
                    color = OledTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPurgeConfirmDialog = false
                        onPurgeModelsClick()
                    }
                ) {
                    Text("Purge Models", color = OledStatusCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel", color = OledTextSecondary)
                }
            },
            containerColor = OledSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ReadinessRow(label: String, isReady: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isReady) {
            PrompterIcons.Check(color = OledStatusEmerald, size = 15.dp)
        } else {
            PrompterIcons.AlertCircle(color = OledStatusCoral, size = 15.dp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = OledTextPrimary, modifier = Modifier.weight(1f))
        Text(
            if (isReady) "READY" else "REQUIRED",
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isReady) OledStatusEmerald else OledStatusCoral
        )
    }
}
