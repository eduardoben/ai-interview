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
import com.local.interviewassistant.ui.prompter.FormattedTeleprompterAnswer
import com.local.interviewassistant.ui.theme.*
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
    modifier: Modifier = Modifier
) {
    var showFilesTable by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StealthBackground)
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
                    color = StealthTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Hardware profile, permissions, and model provisioning",
                    style = MaterialTheme.typography.bodySmall,
                    color = StealthTextMuted
                )
            }

            TextButton(
                onClick = onRefreshDiskCheckClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "Refresh",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = StealthAccentIndigoSoft
                )
            }
        }

        // 1. Hardware Profile Card
        if (hardwareProfile != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = StealthSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, StealthBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(StealthAccentIndigo.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                            .border(0.5.dp, StealthAccentIndigo.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        StealthIcons.CpuChip(color = StealthAccentIndigoSoft, size = 22.dp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hardwareProfile.socName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StealthTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${hardwareProfile.totalRamMb} MB RAM • ${hardwareProfile.preferredDelegate.name}",
                            fontSize = 12.sp,
                            color = StealthStatusEmerald
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = hardwareProfile.recommendedLlmFileName,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = StealthTextMuted
                        )
                    }
                }
            }
        }

        // 2. System Readiness Checklist
        Card(
            colors = CardDefaults.cardColors(containerColor = StealthSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, StealthBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Readiness",
                    style = MaterialTheme.typography.titleSmall,
                    color = StealthTextPrimary
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
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StealthAccentIndigo)
                            ) {
                                Text("Grant Mic & Notif", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (!hasOverlay) {
                            OutlinedButton(
                                onClick = onOpenOverlaySettingsClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = StealthSurfaceHigh,
                                    contentColor = StealthTextPrimary
                                ),
                                border = BorderStroke(0.5.dp, StealthBorderHigh)
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
            colors = CardDefaults.cardColors(containerColor = StealthSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, StealthBorder, RoundedCornerShape(16.dp))
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
                        color = StealthTextPrimary
                    )
                    TextButton(
                        onClick = { showFilesTable = !showFilesTable },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (showFilesTable) "Hide Manifest" else "Inspect Files",
                            fontSize = 11.sp,
                            color = StealthAccentIndigoSoft
                        )
                    }
                }

                if (isMeteredNetwork && provisioningState !is ProvisioningState.Ready) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StealthStatusAmber.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, StealthStatusAmber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StealthIcons.AlertCircle(color = StealthStatusAmber, size = 14.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Metered connection detected. Wi-Fi is recommended for multi-GB model downloads.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = StealthStatusAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (provisioningState) {
                    is ProvisioningState.Ready -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StealthStatusEmerald.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, StealthStatusEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(StealthStatusEmerald.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                StealthIcons.Check(color = StealthStatusEmerald, size = 14.dp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "All 9 Neural Pipeline Assets Verified",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StealthStatusEmerald
                                )
                                Text(
                                    "Silero VAD • Zipformer ASR • Whisper Rescorer • Gemma 4",
                                    fontSize = 11.sp,
                                    color = StealthTextSecondary
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
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StealthAccentIndigo)
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
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = StealthSurfaceHigh,
                                    contentColor = StealthTextSecondary
                                ),
                                border = BorderStroke(0.5.dp, StealthBorder)
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
                                    color = StealthTextPrimary
                                )
                                Text(
                                    text = "${provisioningState.currentFileIndex} / 9",
                                    fontSize = 11.sp,
                                    color = StealthTextMuted
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
                                color = StealthAccentIndigo,
                                trackColor = StealthSurfaceHigh
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
                                    color = StealthTextMuted
                                )
                                OutlinedButton(
                                    onClick = onCancelDownloadClick,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = StealthStatusCoral.copy(alpha = 0.1f),
                                        contentColor = StealthStatusCoral
                                    ),
                                    border = BorderStroke(0.5.dp, StealthStatusCoral.copy(alpha = 0.35f)),
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
                                color = StealthStatusCoral
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onStartDownloadClick,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StealthAccentIndigo)
                                ) {
                                    Text("Retry Download", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = onImportLlmFileClick,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.5.dp, StealthBorderHigh)
                                ) {
                                    Text("Import File", fontSize = 12.sp, color = StealthTextSecondary)
                                }
                            }
                        }
                    }

                    else -> {}
                }

                if (showFilesTable && readiness != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = StealthBorder)
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
                                color = StealthTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (file.exists && file.sizeBytes > 0) String.format(Locale.US, "%.1f MB", sizeMb) else "MISSING",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = if (file.exists && file.sizeBytes > 0) StealthStatusEmerald else StealthStatusCoral
                            )
                        }
                    }
                }
            }
        }

        // 4. Companion Placement & Hardware Key Guide
        Card(
            colors = CardDefaults.cardColors(containerColor = StealthSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, StealthBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ergonomic Placement Guide",
                    style = MaterialTheme.typography.titleSmall,
                    color = StealthTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                FormattedTeleprompterAnswer(
                    text = "• **Eye-Line Alignment:** Rest device directly beneath your laptop webcam.\n" +
                            "• **Zero Glare:** Use the top-bar dimmer in low-light rooms to prevent eyeglass reflections.\n" +
                            "• **Tactile Controls:** Press **Volume Up** to trigger an answer or **Volume Down** to pause.",
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
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
            StealthIcons.Check(color = StealthStatusEmerald, size = 15.dp)
        } else {
            StealthIcons.AlertCircle(color = StealthStatusCoral, size = 15.dp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = StealthTextPrimary, modifier = Modifier.weight(1f))
        Text(
            if (isReady) "READY" else "REQUIRED",
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isReady) StealthStatusEmerald else StealthStatusCoral
        )
    }
}
