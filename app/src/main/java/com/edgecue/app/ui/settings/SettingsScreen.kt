package com.edgecue.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgecue.app.core.model.LlmGenerationConfig
import com.edgecue.app.BuildConfig
import com.edgecue.app.core.model.PromptStyle
import com.edgecue.app.data.hardware.DelegateType
import com.edgecue.app.data.hardware.HardwareProfile
import com.edgecue.app.data.rag.RagDocumentSummary
import com.edgecue.app.data.rag.RagStatus
import com.edgecue.app.data.rag.db.RagSourceType
import com.edgecue.app.ui.dialog.OpenSourceLicensesDialog
import com.edgecue.app.ui.dialog.SendFeedbackDialog
import com.edgecue.app.ui.prompter.FormattedTeleprompterAnswer
import com.edgecue.app.ui.theme.*
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.edgecue.app.core.model.AppConfig
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        config = state.config,
        hardwareProfile = state.hardwareProfile,
        ragStatus = state.ragStatus,
        onConfigChange = viewModel::updateConfig,
        onImportRagDocument = viewModel::importDocument,
        onPasteRagDocument = viewModel::pasteDocument,
        onClearRagDocument = viewModel::clearDocument,
        modifier = modifier
    )
}

@Composable
fun SettingsScreen(
    config: LlmGenerationConfig,
    hardwareProfile: HardwareProfile?,
    ragStatus: RagStatus = RagStatus(),
    onConfigChange: (LlmGenerationConfig) -> Unit,
    onImportRagDocument: (Uri, RagSourceType) -> Unit = { _, _ -> },
    onPasteRagDocument: (String, RagSourceType, String) -> Unit = { _, _, _ -> },
    onClearRagDocument: (RagSourceType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isTensorTpu = hardwareProfile?.preferredDelegate == DelegateType.NPU_GOOGLE_TENSOR
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showLlmConfigGuideDialog by remember { mutableStateOf(false) }

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OledTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Configure persona, on-device acceleration, and display controls",
                    style = MaterialTheme.typography.bodySmall,
                    color = OledTextMuted
                )
            }

            // Interactive Info Icon Button for Recommended LLM Tuning Guide
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(OledAccentIndigo.copy(alpha = 0.14f))
                    .border(0.5.dp, OledAccentIndigo.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable { showLlmConfigGuideDialog = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PrompterIcons.InfoCircle(color = OledAccentIndigoSoft, size = 16.dp)
                Text(
                    text = "Tuning Guide",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OledAccentIndigoSoft
                )
            }
        }

        // Card 1: Context & Response Persona
        SettingsGroupCard(title = "Practice Context & Persona") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RESPONSE STYLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = OledTextMuted
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showLlmConfigGuideDialog = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PrompterIcons.InfoCircle(color = OledAccentIndigoSoft, size = 13.dp)
                    Text(
                        text = "Which style is best?",
                        fontSize = 10.sp,
                        color = OledAccentIndigoSoft
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Minimal Segmented Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledBackground, RoundedCornerShape(10.dp))
                    .border(0.5.dp, OledBorder, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PromptStyle.entries.forEach { style ->
                    val isSelected = config.promptStyle == style
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) OledAccentIndigo else Color.Transparent)
                            .clickable { onConfigChange(config.copy(promptStyle = style)) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = style.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else OledTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            var contextInput by remember { mutableStateOf(config.interviewContext) }
            LaunchedEffect(config.interviewContext) {
                if (config.interviewContext != contextInput.trim()) {
                    contextInput = config.interviewContext
                }
            }

            OutlinedTextField(
                value = contextInput,
                onValueChange = { newText ->
                    contextInput = newText
                    onConfigChange(config.copy(interviewContext = newText))
                },
                label = { Text("Interview Context (Role, Tech Stack, Resume)", fontSize = 11.sp) },
                placeholder = {
                    Text(
                        "e.g. Senior Android Engineer, Kotlin, Compose, System Design",
                        fontSize = 12.sp,
                        color = OledTextMuted
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OledAccentIndigo,
                    unfocusedBorderColor = OledBorder,
                    focusedLabelColor = OledAccentIndigoSoft,
                    unfocusedLabelColor = OledTextMuted,
                    focusedTextColor = OledTextPrimary,
                    unfocusedTextColor = OledTextPrimary,
                    cursorColor = OledAccentIndigoSoft
                )
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = OledBorder
            )

            // Lite RAG: Optional Curriculum & Job Description
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LITE RAG KNOWLEDGE (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall,
                        color = OledTextMuted
                    )
                    Text(
                        text = "Local-only reference context (≤ 700 chars/turn)",
                        fontSize = 11.sp,
                        color = OledTextSecondary
                    )
                }

                // Status Badge
                val isRAGActive = ragStatus.isConfigured
                Box(
                    modifier = Modifier
                        .background(
                            if (isRAGActive) OledStatusEmerald.copy(alpha = 0.15f) else OledSurfaceHigh,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            0.5.dp,
                            if (isRAGActive) OledStatusEmerald.copy(alpha = 0.4f) else OledBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isRAGActive) "Active (${ragStatus.totalChunks} chunks)" else "Not Loaded",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRAGActive) OledStatusEmerald else OledTextMuted
                    )
                }
            }

            if (ragStatus.isBusy && ragStatus.statusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = OledAccentIndigo
                )
                Text(
                    text = ragStatus.statusMessage,
                    fontSize = 11.sp,
                    color = OledAccentIndigoSoft,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Curriculum Slot
            RagDocumentSlot(
                title = "Curriculum / Resume",
                sourceType = RagSourceType.CURRICULUM,
                summary = ragStatus.curriculumDoc,
                onImport = { uri -> onImportRagDocument(uri, RagSourceType.CURRICULUM) },
                onPaste = { text, label -> onPasteRagDocument(text, RagSourceType.CURRICULUM, label) },
                onClear = { onClearRagDocument(RagSourceType.CURRICULUM) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Job Description Slot
            RagDocumentSlot(
                title = "Job Description (Target Role)",
                sourceType = RagSourceType.JOB_DESCRIPTION,
                summary = ragStatus.jobDescriptionDoc,
                onImport = { uri -> onImportRagDocument(uri, RagSourceType.JOB_DESCRIPTION) },
                onPaste = { text, label -> onPasteRagDocument(text, RagSourceType.JOB_DESCRIPTION, label) },
                onClear = { onClearRagDocument(RagSourceType.JOB_DESCRIPTION) }
            )
        }

        // Card 2: On-Device AI & Hardware Delegate
        SettingsGroupCard(title = "On-Device LLM & Acceleration") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledSurfaceHigh, RoundedCornerShape(10.dp))
                    .border(0.5.dp, OledBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(OledAccentIndigo.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    PrompterIcons.CpuChip(color = OledAccentIndigoSoft, size = 18.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hardwareProfile?.socName ?: "Automatic Hardware Delegate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OledTextPrimary
                    )
                    Text(
                        text = hardwareProfile?.recommendedLlmFileName ?: "Universal GPU Model",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = OledTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isTensorTpu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OledStatusEmerald.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, OledStatusEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrompterIcons.Check(color = OledStatusEmerald, size = 15.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "EdgeTPU AOT Mode Active — Sampler parameters are hardware-locked for sub-250ms first-token latency.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = OledTextSecondary
                    )
                }
            } else {
                LabeledSlider(
                    label = "Temperature",
                    valueText = String.format(Locale.US, "%.2f", config.temperature),
                    value = config.temperature,
                    valueRange = 0.0f..1.0f,
                    onValueChange = { onConfigChange(config.copy(temperature = it)) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                LabeledSlider(
                    label = "Top-K Sampling",
                    valueText = "${config.topK}",
                    value = config.topK.toFloat(),
                    valueRange = 1f..100f,
                    onValueChange = { onConfigChange(config.copy(topK = it.toInt())) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Max Answer Length", fontSize = 12.sp, color = OledTextSecondary)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .clickable { showLlmConfigGuideDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        PrompterIcons.InfoCircle(color = OledAccentIndigoSoft, size = 14.dp)
                    }
                }
                Text(
                    text = "${config.maxTokens} tokens",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OledTextPrimary
                )
            }
            Slider(
                value = config.maxTokens.toFloat(),
                onValueChange = { onConfigChange(config.copy(maxTokens = it.toInt())) },
                valueRange = 64f..512f,
                colors = SliderDefaults.colors(
                    thumbColor = OledAccentIndigo,
                    activeTrackColor = OledAccentIndigo,
                    inactiveTrackColor = OledSurfaceHigh
                )
            )
        }

        // Card 3: Audio & Speech Recognition
        SettingsGroupCard(title = "Audio Capture & Speech Endpointing") {
            SettingsToggleRow(
                title = "Whisper Pass-2 Rescorer",
                subtitle = "High-accuracy offline int8 pass when utterance finishes",
                checked = config.enableWhisperRescorer,
                onCheckedChange = { onConfigChange(config.copy(enableWhisperRescorer = it)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = OledBorder
            )

            LabeledSlider(
                label = "VAD Silence Endpoint",
                valueText = String.format(Locale.US, "%.1f s", config.minSilenceDurationSec),
                value = config.minSilenceDurationSec,
                valueRange = 0.6f..2.5f,
                onValueChange = { onConfigChange(config.copy(minSilenceDurationSec = it)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = OledBorder
            )

            SettingsToggleRow(
                title = "Hardware Audio Effects",
                subtitle = "Keep off to preserve unvoiced high-frequency consonants (3–8 kHz)",
                checked = config.enableHardwareAudioEffects,
                onCheckedChange = { onConfigChange(config.copy(enableHardwareAudioEffects = it)) }
            )
        }

        // Card 4: Teleprompter & Hardware Controls
        SettingsGroupCard(title = "Teleprompter & Hardware Button Navigation") {
            LabeledSlider(
                label = "Teleprompter Font Size",
                valueText = "${config.teleprompterFontSizeSp.toInt()} sp",
                value = config.teleprompterFontSizeSp,
                valueRange = 16f..28f,
                steps = 5,
                onValueChange = { onConfigChange(config.copy(teleprompterFontSizeSp = it)) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Typography Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledBackground, RoundedCornerShape(10.dp))
                    .border(0.5.dp, OledBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                FormattedTeleprompterAnswer(
                    text = "• **Key Concept:** Clear separation of concerns.\n• Present trade-offs and measurable outcomes concisely.",
                    fontSize = config.teleprompterFontSizeSp.sp
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = OledBorder
            )

            SettingsToggleRow(
                title = "Hardware Volume Shortcuts",
                subtitle = "Vol Up triggers Answer Now • Vol Down pauses microphone",
                checked = config.enableVolumeKeyShortcuts,
                onCheckedChange = { onConfigChange(config.copy(enableVolumeKeyShortcuts = it)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = OledBorder
            )

            SettingsToggleRow(
                title = "Keep Screen Awake",
                subtitle = "Prevent display sleep during active interview sessions",
                checked = config.keepScreenOn,
                onCheckedChange = { onConfigChange(config.copy(keepScreenOn = it)) }
            )
        }

        // Card 5: About & Legal (R2 / P1-02)
        SettingsGroupCard(title = "About & Legal") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(OledSurfaceHigh, RoundedCornerShape(10.dp))
                        .border(0.5.dp, OledBorderHigh, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    PrompterIcons.EdgeCueLogo(size = 28.dp, color = OledTextPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EdgeCue",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OledTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Local-First AI Interview Cue",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = OledAccentIndigoSoft
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        fontSize = 11.sp,
                        color = OledTextMuted
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = OledBorder
            )
            OutlinedButton(
                onClick = { showFeedbackDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = OledSurfaceHigh),
                border = BorderStroke(0.5.dp, OledBorderHigh)
            ) {
                Text(
                    text = "Send Feedback (Logs & Transcripts Optional)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OledTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                    text = "Open Source Licenses & Attributions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OledTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val context = LocalContext.current
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
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showFeedbackDialog) {
        SendFeedbackDialog(
            hardwareProfile = hardwareProfile,
            onDismiss = { showFeedbackDialog = false }
        )
    }

    if (showLicensesDialog) {
        OpenSourceLicensesDialog(
            onDismiss = { showLicensesDialog = false }
        )
    }

    if (showLlmConfigGuideDialog) {
        LlmConfigurationGuideDialog(
            onDismiss = { showLlmConfigGuideDialog = false },
            onApplyGoldenPreset = {
                onConfigChange(
                    config.copy(
                        promptStyle = PromptStyle.CONCISE,
                        maxTokens = 280,
                        temperature = 0.35f,
                        topK = 20
                    )
                )
                showLlmConfigGuideDialog = false
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OledSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, OledBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = OledTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 12.sp, color = OledTextSecondary)
            Text(
                text = valueText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OledTextPrimary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = OledAccentIndigo,
                activeTrackColor = OledAccentIndigo,
                inactiveTrackColor = OledSurfaceHigh
            )
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = OledTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = OledTextMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OledAccentIndigo,
                uncheckedThumbColor = OledTextSecondary,
                uncheckedTrackColor = OledSurfaceHigh,
                uncheckedBorderColor = OledBorderHigh
            )
        )
    }
}

@Composable
private fun RagDocumentSlot(
    title: String,
    sourceType: RagSourceType,
    summary: RagDocumentSummary?,
    onImport: (Uri) -> Unit,
    onPaste: (String, String) -> Unit,
    onClear: () -> Unit
) {
    var showPasteDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImport(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OledBackground, RoundedCornerShape(10.dp))
            .border(0.5.dp, OledBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = OledTextPrimary
            )
            if (summary != null) {
                Text(
                    text = "${summary.charCount} chars · ${summary.chunkCount} chunks",
                    fontSize = 10.sp,
                    color = OledTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (summary != null) {
            Text(
                text = summary.fileName,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = OledAccentIndigoSoft,
                maxLines = 1
            )
            if (summary.previewText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.previewText,
                    fontSize = 10.sp,
                    color = OledTextSecondary,
                    maxLines = 2
                )
            }
        } else {
            Text(
                text = "None uploaded (optional)",
                fontSize = 11.sp,
                color = OledTextMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    filePicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown"))
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = OledSurfaceHigh),
                border = BorderStroke(0.5.dp, OledBorderHigh),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = "Upload PDF/TXT",
                    fontSize = 11.sp,
                    color = OledTextPrimary,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = { showPasteDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = OledSurfaceHigh),
                border = BorderStroke(0.5.dp, OledBorderHigh),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = "Paste Text",
                    fontSize = 11.sp,
                    color = OledTextPrimary,
                    maxLines = 1
                )
            }

            if (summary != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(44.dp)
                ) {
                    PrompterIcons.CloseCross(color = OledStatusCoral, size = 16.dp)
                }
            }
        }
    }

    if (showPasteDialog) {
        RagPasteDialog(
            title = "Paste $title",
            initialText = summary?.rawText ?: "",
            onDismiss = { showPasteDialog = false },
            onSave = { text ->
                onPaste(text, "Direct Text Input")
                showPasteDialog = false
            }
        )
    }
}

@Composable
private fun RagPasteDialog(
    title: String,
    initialText: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledSurface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OledTextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Text will be parsed into local RAG chunks and never exported off-device.",
                    fontSize = 11.sp,
                    color = OledTextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Paste resume or job description content here...", fontSize = 12.sp, color = OledTextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = OledTextPrimary),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OledAccentIndigo,
                        unfocusedBorderColor = OledBorder,
                        focusedTextColor = OledTextPrimary,
                        unfocusedTextColor = OledTextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = OledAccentIndigo),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save to RAG", color = Color.White, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OledTextSecondary, fontSize = 12.sp)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun LlmConfigurationGuideDialog(
    onDismiss: () -> Unit,
    onApplyGoldenPreset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrompterIcons.InfoCircle(color = OledAccentIndigoSoft, size = 20.dp)
                Text(
                    text = "LLM Configuration Guide",
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
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Golden Config Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OledStatusEmerald.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, OledStatusEmerald.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "RECOMMENDED GOLDEN PRESET (STAR FORMAT)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OledStatusEmerald
                    )
                    Text(
                        text = "Style: Concise · Max Length: 280 tokens · Temp: 0.35 · Top-K: 20",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OledTextPrimary
                    )
                    Text(
                        text = "Delivers structured STAR (Situation, Task, Action, Result) talking points combining your CV projects with deep technical know-how and ~950 ms first-token latency on Tensor TPU.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = OledTextSecondary
                    )
                }

                // Style Breakdown
                Text(
                    text = "1. RESPONSE STYLES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OledTextMuted
                )

                GuideItemCard(
                    title = "Concise (Recommended for 90% of Interviews)",
                    badge = "220 – 260 tokens",
                    description = "Produces 3 punchy bullet points: (1) core concept/mechanism, (2) concrete trade-offs & tools, and (3) real metrics/projects from your CV. Fits on screen without scrolling so you can speak naturally right away."
                )

                GuideItemCard(
                    title = "Verbose (System Design & Deep Architecture)",
                    badge = "380 – 480 tokens",
                    description = "Generates 4–5 detailed sections covering architecture, edge cases, failure modes, and trade-offs. Best when asked to walk through an end-to-end system design."
                )

                GuideItemCard(
                    title = "Simple (Recruiter & Executive Screens)",
                    badge = "120 – 160 tokens",
                    description = "Outputs 2–3 plain-English sentences focused on the core takeaway without heavy code jargon. Ideal for rapid introductory or HR rounds."
                )

                // Max Answer Length Guide
                Text(
                    text = "2. MAX ANSWER LENGTH (TOKENS)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OledTextMuted
                )

                GuideItemCard(
                    title = "Why 250 Tokens is the Sweet Spot",
                    badge = "~190 words",
                    description = "• Below 150 tokens: Complex technical or math answers may get cut off mid-sentence.\n• 220 – 280 tokens: Gives enough room to finish all 3 bullets cleanly with a 30-token safety buffer.\n• 400+ tokens: Best reserved for Verbose mode on multi-part questions."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onApplyGoldenPreset,
                colors = ButtonDefaults.buttonColors(containerColor = OledAccentIndigo),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Apply Golden Preset", color = Color.White, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = OledTextSecondary, fontSize = 12.sp)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun GuideItemCard(
    title: String,
    badge: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OledBackground, RoundedCornerShape(10.dp))
            .border(0.5.dp, OledBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = OledTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = badge,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = OledAccentIndigoSoft
            )
        }
        Text(
            text = description,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = OledTextSecondary
        )
    }
}
