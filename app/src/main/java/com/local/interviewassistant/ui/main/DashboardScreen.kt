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
import com.local.interviewassistant.data.provisioning.ProvisioningState
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    readiness: AppContainer.ReadinessStatus?,
    isServiceRunning: Boolean,
    provisioningState: ProvisioningState,
    llmConfig: LlmGenerationConfig,
    onUpdateLlmConfig: (LlmGenerationConfig) -> Unit,
    onGrantPermissionsClick: () -> Unit,
    onOpenOverlaySettingsClick: () -> Unit,
    onRefreshDiskCheckClick: () -> Unit,
    onDownloadSpeechModelsClick: () -> Unit,
    onDownloadLlmModelClick: (customUrl: String, token: String?) -> Unit,
    onImportLlmFileClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onStartAssistantClick: (previewMode: Boolean) -> Unit,
    onStopAssistantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var allowPreviewMode by remember { mutableStateOf(value = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interview Assistant Dashboard", color = Color.White) },
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PermissionsCard(
                readiness = readiness,
                onGrantPermissionsClick = onGrantPermissionsClick,
                onOpenOverlaySettingsClick = onOpenOverlaySettingsClick
            )

            ModelsCard(
                readiness = readiness,
                provisioningState = provisioningState,
                llmConfig = llmConfig,
                onUpdateLlmConfig = onUpdateLlmConfig,
                onRefreshDiskCheckClick = onRefreshDiskCheckClick,
                onDownloadSpeechModelsClick = onDownloadSpeechModelsClick,
                onDownloadLlmModelClick = onDownloadLlmModelClick,
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("System Prompt Style", fontSize = 14.sp, color = Color.White)
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
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            OutlinedTextField(
                value = config.interviewContext,
                onValueChange = { onConfigChanged(config.copy(interviewContext = it)) },
                label = { Text("Interview Context (Role, Tech Stack, Seniority)", color = Color.Gray, fontSize = 12.sp) },
                placeholder = { Text("e.g., Senior Android & QA Engineer, Kotlin, Playwright", color = Color(0xFF546E7A), fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(8.dp))
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 12.sp, color = Color.White)
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))

            StatusRow("Microphone", readiness.hasAudioPermission)
            StatusRow("Notifications", readiness.hasNotificationPermission)
            StatusRow("System Overlay", readiness.hasOverlayPermission)

            Spacer(modifier = Modifier.height(16.dp))
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
                    Text("Grant Base", fontSize = 12.sp, color = Color.White)
                }
                Button(
                    onClick = onOpenOverlaySettingsClick,
                    enabled = !readiness.hasOverlayPermission,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3700B3))
                ) {
                    Text("Settings (Overlay)", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ModelsCard(
    readiness: AppContainer.ReadinessStatus,
    provisioningState: ProvisioningState,
    llmConfig: LlmGenerationConfig,
    onUpdateLlmConfig: (LlmGenerationConfig) -> Unit,
    onRefreshDiskCheckClick: () -> Unit,
    onDownloadSpeechModelsClick: () -> Unit,
    onDownloadLlmModelClick: (customUrl: String, token: String?) -> Unit,
    onImportLlmFileClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
) {
    var isLlmSectionExpanded by remember { mutableStateOf(false) }
    var customLlmUrl by remember { mutableStateOf(AppConfig.DEFAULT_LLM_URL) }
    var hfBearerToken by remember { mutableStateOf("") }

    val localModels = remember(readiness) {
        val parentDir = readiness.modelReport.files.firstOrNull()?.absolutePath?.let { File(it).parentFile }
        if (parentDir != null && parentDir.exists()) {
            val extensions = listOf(".litertlm", ".bin", ".task", ".tflite")
            parentDir.walkTopDown()
                .filter { file -> file.isFile && extensions.any { ext -> file.name.endsWith(ext, ignoreCase = true) } && file.length() > 0L }
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
                text = "2. Model Provisioning & Storage Manager",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            val targetPath = readiness.modelReport.files.firstOrNull()?.absolutePath?.let {
                File(it).parent
            } ?: "Unknown Path"

            Text("Target Path:", fontSize = 12.sp, color = Color.Gray)
            SelectionContainer {
                Text(
                    text = targetPath,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFB0BEC5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121212), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                )
            }

            if (localModels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Select Active Local Model:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF03DAC5))
                localModels.forEach { modelFile ->
                    val isSelected = llmConfig.selectedModelPath == modelFile.absolutePath ||
                        (llmConfig.selectedModelPath.isBlank() && modelFile.name == "gemma-4-E2B-it.litertlm")
                    val sizeMb = modelFile.length() / (1024f * 1024f)
                    
                    OutlinedButton(
                        onClick = { onUpdateLlmConfig(llmConfig.copy(selectedModelPath = modelFile.absolutePath)) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) Color(0xFF1B5E20) else Color(0xFF121212),
                            contentColor = if (isSelected) Color.Green else Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "${if (isSelected) "[ACTIVE] " else ""}${modelFile.name} (${String.format(Locale.US, "%.1f", sizeMb)} MB)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (provisioningState) {
                is ProvisioningState.InProgress -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Downloading (${provisioningState.completedFilesCount + 1}/${provisioningState.totalFilesCount}): ${provisioningState.currentFileName}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (provisioningState.totalBytes > 0) {
                            val progress = provisioningState.downloadedBytes.toFloat() / provisioningState.totalBytes.toFloat()
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF03DAC5),
                                trackColor = Color(0xFF37474F),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val downloadedMb = provisioningState.downloadedBytes / (1024f * 1024f)
                            val totalMb = provisioningState.totalBytes / (1024f * 1024f)
                            val percent = (progress * 100).toInt()
                            Text(
                                text = String.format(Locale.US, "%.2f MB / %.2f MB (%d%%)", downloadedMb, totalMb, percent),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFB0BEC5)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF03DAC5),
                                trackColor = Color(0xFF37474F),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val downloadedMb = provisioningState.downloadedBytes / (1024f * 1024f)
                            Text(
                                text = String.format(Locale.US, "%.2f MB downloaded", downloadedMb),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFB0BEC5)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onCancelDownloadClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel Download", fontSize = 12.sp, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is ProvisioningState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3E2723), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Error (${provisioningState.fileName}): ${provisioningState.message}",
                            fontSize = 12.sp,
                            color = Color(0xFFFF8A80)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                else -> {}
            }

            readiness.modelReport.files.forEach { (name, _, exists, sizeBytes) ->
                val sizeMb = sizeBytes / (1024f * 1024f)
                val statusText = if (exists && (sizeBytes > 0)) {
                    String.format(Locale.US, "[OK] %.2f MB", sizeMb)
                } else {
                    "[MISSING]"
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = statusText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (exists && (sizeBytes > 0)) Color.Green else Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isDownloading = provisioningState is ProvisioningState.InProgress

            Button(
                onClick = onDownloadSpeechModelsClick,
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3700B3))
            ) {
                Text("Auto-Download Speech Models (VAD + STT)", fontSize = 12.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { isLlmSectionExpanded = !isLlmSectionExpanded },
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isLlmSectionExpanded) "Hide Gemma 4 LLM Config" else "Download Gemma 4 LLM",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            if (isLlmSectionExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "GPU Variant: Universal model for all Pixel & Android devices.\nPixel TPU Variant: Pre-compiled AOT model for Google Tensor TPU.",
                        fontSize = 10.sp,
                        color = Color(0xFFB0BEC5)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                customLlmUrl = AppConfig.DEFAULT_LLM_URL
                                onDownloadLlmModelClick(AppConfig.DEFAULT_LLM_URL, hfBearerToken.ifBlank { null })
                            },
                            enabled = !isDownloading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                        ) {
                            Text("GPU Variant", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                customLlmUrl = AppConfig.DEFAULT_TPU_LLM_URL
                                onDownloadLlmModelClick(AppConfig.DEFAULT_TPU_LLM_URL, hfBearerToken.ifBlank { null })
                            },
                            enabled = !isDownloading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                        ) {
                            Text("Pixel TPU Variant", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    OutlinedTextField(
                        value = customLlmUrl,
                        onValueChange = { customLlmUrl = it },
                        label = { Text("Custom LLM URL", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hfBearerToken,
                        onValueChange = { hfBearerToken = it },
                        label = { Text("HF Bearer Token (Optional)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                        singleLine = true
                    )
                    Button(
                        onClick = { onDownloadLlmModelClick(customLlmUrl, hfBearerToken.ifBlank { null }) },
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3700B3))
                    ) {
                        Text("Start Custom LLM Download", fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onImportLlmFileClick,
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF018786))
            ) {
                Text("Import LLM File from Device Storage", fontSize = 12.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRefreshDiskCheckClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5), contentColor = Color.Black)
            ) {
                Text("Refresh Disk Check")
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))

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
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val canStart = (readiness.canStartAssistant || (readiness.allPermissionsGranted && allowPreviewMode)) && !isServiceRunning
            
            Button(
                onClick = onStartAssistantClick,
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), disabledContainerColor = Color(0xFF2E3A2F))
            ) {
                Text(if (isServiceRunning) "Assistant is Running" else "Start Interview Assistant", color = if (canStart) Color.White else Color.Gray)
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
private fun StatusRow(label: String, isGranted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isGranted) "✔" else "⚠",
            color = if (isGranted) Color.Green else Color.Red,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (isGranted) "GRANTED" else "MISSING",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isGranted) Color.Green else Color.Red
        )
    }
}
