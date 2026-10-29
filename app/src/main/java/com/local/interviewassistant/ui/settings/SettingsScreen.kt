package com.local.interviewassistant.ui.settings

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.model.LlmGenerationConfig
import com.local.interviewassistant.core.model.PromptStyle
import com.local.interviewassistant.data.hardware.DelegateType
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.ui.prompter.FormattedTeleprompterAnswer
import com.local.interviewassistant.ui.theme.*
import java.util.Locale

@Composable
fun SettingsScreen(
    config: LlmGenerationConfig,
    hardwareProfile: HardwareProfile?,
    onConfigChange: (LlmGenerationConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTensorTpu = hardwareProfile?.preferredDelegate == DelegateType.NPU_GOOGLE_TENSOR

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StealthBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = StealthTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Configure persona, on-device acceleration, and stealth controls",
                style = MaterialTheme.typography.bodySmall,
                color = StealthTextMuted
            )
        }

        // Card 1: Context & Response Persona
        SettingsGroupCard(title = "Candidate Context & Persona") {
            Text(
                text = "RESPONSE STYLE",
                style = MaterialTheme.typography.labelSmall,
                color = StealthTextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Minimal Segmented Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StealthBackground, RoundedCornerShape(10.dp))
                    .border(0.5.dp, StealthBorder, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PromptStyle.entries.forEach { style ->
                    val isSelected = config.promptStyle == style
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) StealthAccentIndigo else Color.Transparent)
                            .clickable { onConfigChange(config.copy(promptStyle = style)) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = style.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else StealthTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = config.interviewContext,
                onValueChange = { onConfigChange(config.copy(interviewContext = it)) },
                label = { Text("Interview Context (Role, Tech Stack, Resume)", fontSize = 11.sp) },
                placeholder = { Text("e.g. Senior Android Engineer, Kotlin, Compose, System Design", fontSize = 12.sp, color = StealthTextMuted) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StealthAccentIndigo,
                    unfocusedBorderColor = StealthBorder,
                    focusedLabelColor = StealthAccentIndigoSoft,
                    unfocusedLabelColor = StealthTextMuted,
                    focusedTextColor = StealthTextPrimary,
                    unfocusedTextColor = StealthTextPrimary
                )
            )
        }

        // Card 2: On-Device AI & Hardware Delegate
        SettingsGroupCard(title = "On-Device LLM & Acceleration") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StealthSurfaceHigh, RoundedCornerShape(10.dp))
                    .border(0.5.dp, StealthBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(StealthAccentIndigo.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    StealthIcons.CpuChip(color = StealthAccentIndigoSoft, size = 18.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hardwareProfile?.socName ?: "Automatic Hardware Delegate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StealthTextPrimary
                    )
                    Text(
                        text = hardwareProfile?.recommendedLlmFileName ?: "Universal GPU Model",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = StealthTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isTensorTpu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StealthStatusEmerald.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, StealthStatusEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StealthIcons.Check(color = StealthStatusEmerald, size = 15.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "EdgeTPU AOT Mode Active — Sampler parameters are hardware-locked for sub-250ms first-token latency.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = StealthTextSecondary
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

            LabeledSlider(
                label = "Max Answer Length",
                valueText = "${config.maxTokens} tokens",
                value = config.maxTokens.toFloat(),
                valueRange = 64f..512f,
                onValueChange = { onConfigChange(config.copy(maxTokens = it.toInt())) }
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
                color = StealthBorder
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
                color = StealthBorder
            )

            SettingsToggleRow(
                title = "Hardware Audio Effects",
                subtitle = "Keep off to preserve unvoiced high-frequency consonants (3–8 kHz)",
                checked = config.enableHardwareAudioEffects,
                onCheckedChange = { onConfigChange(config.copy(enableHardwareAudioEffects = it)) }
            )
        }

        // Card 4: Teleprompter & Hardware Controls
        SettingsGroupCard(title = "Teleprompter & Blind Controls") {
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
                    .background(StealthBackground, RoundedCornerShape(10.dp))
                    .border(0.5.dp, StealthBorder, RoundedCornerShape(10.dp))
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
                color = StealthBorder
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
                color = StealthBorder
            )

            SettingsToggleRow(
                title = "Keep Screen Awake",
                subtitle = "Prevent display sleep during active interview sessions",
                checked = config.keepScreenOn,
                onCheckedChange = { onConfigChange(config.copy(keepScreenOn = it)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StealthSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, StealthBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = StealthTextPrimary
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
            Text(text = label, fontSize = 12.sp, color = StealthTextSecondary)
            Text(
                text = valueText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = StealthTextPrimary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = StealthAccentIndigo,
                activeTrackColor = StealthAccentIndigo,
                inactiveTrackColor = StealthSurfaceHigh
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
                color = StealthTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = StealthTextMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = StealthAccentIndigo,
                uncheckedThumbColor = StealthTextSecondary,
                uncheckedTrackColor = StealthSurfaceHigh,
                uncheckedBorderColor = StealthBorderHigh
            )
        )
    }
}
