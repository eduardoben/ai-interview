package com.local.interviewassistant.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.di.AppContainer
import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.LlmGenerationConfig
import com.local.interviewassistant.core.model.PromptStyle
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.data.hardware.HardwareTier
import com.local.interviewassistant.data.provisioning.ProvisioningState
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    readiness: AppContainer.ReadinessStatus?,
    hardwareProfile: HardwareProfile?,
    isServiceRunning: Boolean,
    provisioningState: ProvisioningState,
    llmConfig: LlmGenerationConfig,
    isMeteredNetwork: Boolean,
    onUpdateLlmConfig: (LlmGenerationConfig) -> Unit,
    onGrantPermissionsClick: () -> Unit,
    onOpenOverlaySettingsClick: () -> Unit,
    onRefreshDiskCheckClick: () -> Unit,
    onStartUnifiedDownloadClick: () -> Unit,
    onImportLlmFileClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onStartAssistantClick: (previewMode: Boolean) -> Unit,
    onStopAssistantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var allowPreviewMode by remember { mutableStateOf(false) }
    var showMeteredDialog by remember { mutableStateOf(false) }

    if (showMeteredDialog) {
        AlertDialog(
            onDismissRequest = { showMeteredDialog = false },
            title = { Text("Metered Network Detected", color = Color.White) },
            text = {
                Text(
                    "You are currently connected to a metered network (cellular data or metered Wi-Fi). Downloading the unified AI models bundle (~3.4 GB) may consume your data allowance. Do you wish to continue?",
                    color = Color(0xFFCFD8DC),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMeteredDialog = false
                        onStartUnifiedDownloadClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5), contentColor = Color.Black)
                ) {
                    Text("Download Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMeteredDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Interview Assistant", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "v${com.local.interviewassistant.BuildConfig.VERSION_NAME} (Android 17 / API 37)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF03DAC5)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                ),
            )
        },
        containerColor = Color(0xFF121212),
        modifier = modifier.fillMaxSize()
    ) { padding ->
        if (readiness == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            return@Scaffold
        }

        // Android 17 Adaptive Layout: Centered container constrained to 640.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HardwareProfileCard(profile = hardwareProfile)

                PermissionsCard(
                    readiness = readiness,
                    onGrantPermissionsClick = onGrantPermissionsClick,
                    onOpenOverlaySettingsClick = onOpenOverlaySettingsClick
                )

                UnifiedProvisioningCard(
                    readiness = readiness,
                    hardwareProfile = hardwareProfile,
                    provisioningState = provisioningState,
                    llmConfig = llmConfig,
                    isMeteredNetwork = isMeteredNetwork,
                    onUpdateLlmConfig = onUpdateLlmConfig,
                    onStartDownloadTapped = {
                        if (isMeteredNetwork) {
                            showMeteredDialog = true
                        } else {
                            onStartUnifiedDownloadClick()
                        }
                    },
                    onRefreshDiskCheckClick = onRefreshDiskCheckClick,
                    onImportLlmFileClick = onImportLlmFileClick,
                    onCancelDownloadClick = onCancelDownloadClick
                )

                ServiceControlCard(
                    readiness = readiness,
                    isServiceRunning = isServiceRunning,
                    allowPreviewMode = allowPreviewMode,
                    onPreviewModeToggle = { allowPreviewMode = it },
                    onStartAssistantClick = { onStartAssistantClick(allowPreviewMode) },
                    onStopAssistantClick = onStopAssistantClick
                )

                LlmConfigurationCard(
                    config = llmConfig,
                    onConfigChanged = onUpdateLlmConfig
                )
            }
        }
    }
}

@Composable
private fun HardwareProfileCard(profile: HardwareProfile?) {
    if (profile == null) return

    val (badgeTitle, badgeSubtitle, badgeColor, iconEmoji) = when (profile.tier) {
        HardwareTier.TPU -> Quadruple(
            "Google Tensor NPU Detected (${profile.socName})",
            "Ultra-Low Power AOT EdgeTPU Mode • Rescorer Active",
            Color(0xFF00E676),
            "⚡"
        )
        HardwareTier.GPU -> Quadruple(
            "High-Memory GPU Tier (${profile.totalRamMb} MB RAM)",
            "Hardware Accelerated Inference • Rescorer Active",
            Color(0xFF00B0FF),
            "🚀"
        )
        HardwareTier.CPU -> Quadruple(
            "Memory-Safe CPU Tier (mmap)",
            "Optimized for Android 17 Memory Limits • Whisper Standby",
            Color(0xFFFFD600),
            "🛡️"
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(iconEmoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = badgeTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = badgeSubtitle,
                    fontSize = 11.sp,
                    color = Color(0xFFB0BEC5)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "AOT Model: ${profile.recommendedLlmFileName}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF78909C)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun UnifiedProvisioningCard(
    readiness: AppContainer.ReadinessStatus,
    hardwareProfile: HardwareProfile?,
    provisioningState: ProvisioningState,
    llmConfig: LlmGenerationConfig,
    isMeteredNetwork: Boolean,
    onUpdateLlmConfig: (LlmGenerationConfig) -> Unit,
    onStartDownloadTapped: () -> Unit,
    onRefreshDiskCheckClick: () -> Unit,
    onImportLlmFileClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
) {
    val localModels = remember(readiness) {
        val parentDir = readiness.modelReport.files.firstOrNull()?.absolutePath?.let { File(it).parentFile }
        if (parentDir != null && parentDir.exists()) {
            parentDir.walkTopDown()
                .filter { file -> file.isFile && AppConfig.isSupportedLlmModel(file) && file.length() > 0L }
                .toList()
        } else emptyList()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "2. Unified Model Provisioning (9 Pipeline Assets)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Zero-config auto-download with HTTP 206 Range resume and FGS timeout resilience",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Unified Action Button & State Handling
            when (provisioningState) {
                is ProvisioningState.Checking -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF03DAC5),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Checking hardware profile & models on disk...",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }

                is ProvisioningState.Downloading -> {
                    UnifiedProgressCard(
                        state = provisioningState,
                        onCancelClick = onCancelDownloadClick
                    )
                }

                is ProvisioningState.NeedsDownload -> {
                    val remainingText = formatBytes(provisioningState.remainingBytes)
                    val buttonLabel = if (provisioningState.hasPartialResume) {
                        "Resume AI Models Download ($remainingText remaining)"
                    } else {
                        "Download All AI Models (~$remainingText)"
                    }

                    Button(
                        onClick = onStartDownloadTapped,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (provisioningState.hasPartialResume) Color(0xFFFF9800) else Color(0xFF6200EE)
                        )
                    ) {
                        Text(
                            text = buttonLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (isMeteredNetwork) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠ Metered connection detected. Tap will prompt confirmation.",
                            fontSize = 10.sp,
                            color = Color(0xFFFFCC80)
                        )
                    }
                }

                is ProvisioningState.Ready -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1B5E20), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✔", fontSize = 18.sp, color = Color(0xFF00E676))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "All AI Pipeline Models Ready",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "VAD + Zipformer STT + Whisper Rescorer + Gemma 4 LLM",
                                fontSize = 10.sp,
                                color = Color(0xFFC8E6C9)
                            )
                        }
                    }
                }

                is ProvisioningState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3E2723), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Download Issue: ${provisioningState.message}",
                            fontSize = 12.sp,
                            color = Color(0xFFFF8A80)
                        )
                        if (provisioningState.canResume) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onStartDownloadTapped,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043))
                            ) {
                                Text("Resume Download", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Models On Disk List
            Text("Models Status on Disk:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))

            readiness.modelReport.files.forEach { fileStatus ->
                val sizeMb = fileStatus.sizeBytes / (1024f * 1024f)
                val statusText = if (fileStatus.exists && fileStatus.sizeBytes > 0L) {
                    String.format(Locale.US, "[OK] %.1f MB", sizeMb)
                } else {
                    "[MISSING]"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileStatus.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFCFD8DC),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = statusText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (fileStatus.exists && fileStatus.sizeBytes > 0L) Color(0xFF00E676) else Color(0xFFFF5252)
                    )
                }
            }

            if (localModels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Installed LLM Models:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF03DAC5))
                localModels.forEach { modelFile ->
                    val isSelected = llmConfig.selectedModelPath == modelFile.absolutePath ||
                            (llmConfig.selectedModelPath.isBlank() && modelFile.name == hardwareProfile?.recommendedLlmFileName)
                    val sizeMb = modelFile.length() / (1024f * 1024f)

                    OutlinedButton(
                        onClick = { onUpdateLlmConfig(llmConfig.copy(selectedModelPath = modelFile.absolutePath)) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) Color(0xFF1B5E20) else Color(0xFF121212),
                            contentColor = if (isSelected) Color.Green else Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "${if (isSelected) "[ACTIVE] " else ""}${modelFile.name} (${String.format(Locale.US, "%.1f", sizeMb)} MB)",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onImportLlmFileClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import .litertlm", fontSize = 11.sp, color = Color.White)
                }

                Button(
                    onClick = onRefreshDiskCheckClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh Status", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun UnifiedProgressCard(
    state: ProvisioningState.Downloading,
    onCancelClick: () -> Unit
) {
    val totalBytes = state.totalBytesRequired
    val progress = if (totalBytes > 0) {
        (state.bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val percent = (progress * 100).toInt()
    val speedMb = state.speedBytesPerSec / (1024f * 1024f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF263238), RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloading [${state.currentFileIndex}/${state.totalFiles}]:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF03DAC5)
            )
            Text(
                text = String.format(Locale.US, "%.1f MB/s", speedMb),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        }

        Text(
            text = state.currentFileName,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color.White,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF00E676),
            trackColor = Color(0xFF37474F)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatBytes(state.bytesDownloaded)} / ${formatBytes(totalBytes)} ($percent%)",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFB0BEC5)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onCancelClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Pause / Cancel", fontSize = 11.sp, color = Color.White)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1000.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

@Composable
private fun PermissionsCard(
    readiness: AppContainer.ReadinessStatus,
    onGrantPermissionsClick: () -> Unit,
    onOpenOverlaySettingsClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "1. Permissions Status",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))

            StatusRow("Microphone", readiness.hasAudioPermission)
            StatusRow("Notifications", readiness.hasNotificationPermission)
            StatusRow("System Overlay", readiness.hasOverlayPermission)

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGrantPermissionsClick,
                    enabled = !readiness.hasAudioPermission || !readiness.hasNotificationPermission,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3700B3))
                ) {
                    Text("Grant Base", fontSize = 11.sp, color = Color.White)
                }
                Button(
                    onClick = onOpenOverlaySettingsClick,
                    enabled = !readiness.hasOverlayPermission,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3700B3))
                ) {
                    Text("Settings (Overlay)", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ServiceControlCard(
    readiness: AppContainer.ReadinessStatus,
    isServiceRunning: Boolean,
    allowPreviewMode: Boolean,
    onPreviewModeToggle: (Boolean) -> Unit,
    onStartAssistantClick: () -> Unit,
    onStopAssistantClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "3. Assistant Service Control",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = allowPreviewMode,
                    onCheckedChange = onPreviewModeToggle,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF03DAC5),
                        uncheckedColor = Color.Gray
                    )
                )
                Text(
                    text = "Allow Start Without Models (UI Preview)",
                    fontSize = 13.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val canStart = (readiness.canStartAssistant || (readiness.allPermissionsGranted && allowPreviewMode)) && !isServiceRunning

            Button(
                onClick = onStartAssistantClick,
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), disabledContainerColor = Color(0xFF2E3A2F))
            ) {
                Text(
                    if (isServiceRunning) "Assistant is Running" else "Start Interview Assistant",
                    color = if (canStart) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onStopAssistantClick,
                enabled = isServiceRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679), disabledContainerColor = Color(0xFF5A3138))
            ) {
                Text("Stop Assistant", color = if (isServiceRunning) Color.White else Color.Gray)
            }
        }
    }
}

@Composable
private fun LlmConfigurationCard(
    config: LlmGenerationConfig,
    onConfigChanged: (LlmGenerationConfig) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "4. LLM & Prompt Configuration",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("System Prompt Style", fontSize = 13.sp, color = Color.White)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PromptStyle.entries.forEach { style ->
                    val isSelected = config.promptStyle == style
                    Button(
                        onClick = { onConfigChanged(config.copy(promptStyle = style)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF03DAC5) else Color(0xFF37474F),
                            contentColor = if (isSelected) Color.Black else Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(style.displayName, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }

            Text(
                text = config.promptStyle.systemInstruction,
                fontSize = 11.sp,
                color = Color(0xFFB0BEC5),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            OutlinedTextField(
                value = config.interviewContext,
                onValueChange = { onConfigChanged(config.copy(interviewContext = it)) },
                label = { Text("Interview Context (Role, Tech Stack, Seniority)", color = Color.Gray, fontSize = 11.sp) },
                placeholder = { Text("e.g., Staff Android Architect, Kotlin, LiteRT", color = Color(0xFF546E7A), fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConfigSlider(
                label = "Temperature: ${String.format(Locale.US, "%.2f", config.temperature)}",
                value = config.temperature,
                range = 0.0f..1.0f,
                steps = 10,
                onValueChange = { onConfigChanged(config.copy(temperature = it)) }
            )

            ConfigSlider(
                label = "Top-K: ${config.topK}",
                value = config.topK.toFloat(),
                range = 1f..100f,
                steps = 99,
                onValueChange = { onConfigChanged(config.copy(topK = it.toInt())) }
            )

            ConfigSlider(
                label = "Max Tokens: ${config.maxTokens}",
                value = config.maxTokens.toFloat(),
                range = 64f..512f,
                steps = ((512 - 64) / 16) - 1,
                onValueChange = { onConfigChanged(config.copy(maxTokens = it.toInt())) }
            )

            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = { onConfigChanged(LlmGenerationConfig()) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Reset to Defaults", color = Color(0xFF03DAC5), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF03DAC5),
                activeTrackColor = Color(0xFF03DAC5)
            )
        )
    }
}

@Composable
private fun StatusRow(label: String, isGranted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isGranted) "✔" else "⚠",
            color = if (isGranted) Color(0xFF00E676) else Color(0xFFFF5252),
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (isGranted) "GRANTED" else "MISSING",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isGranted) Color(0xFF00E676) else Color(0xFFFF5252)
        )
    }
}
