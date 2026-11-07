package com.edgecue.app.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgecue.app.ui.theme.*

/**
 * Metadata and terms specification for third-party open source dependencies and on-device models.
 */
data class LegalLicenseItem(
    val title: String,
    val badges: List<String>,
    val copyright: String,
    val url: String,
    val licenseText: String
)

/**
 * Open Source Licenses & Attributions View (Requirements R2, P1-02).
 * Displays full legal notices, open-source licenses, model terms of use, and third-party attributions
 * in compliance with Google Play Store policies and open-source license agreements.
 */
@Composable
fun OpenSourceLicensesDialog(
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val licenses = listOf(
        LegalLicenseItem(
            title = "Google Gemma Models (Gemma 2 / 4-E2B-it)",
            badges = listOf("Gemma Terms of Use", "Google LLC"),
            copyright = "Copyright 2024-2025 Google LLC",
            url = "https://ai.google.dev/gemma/terms",
            licenseText = "Gemma is provided by Google under the Gemma Terms of Use. By using this model, you agree to comply with the Google Generative AI Prohibited Use Policy (https://policies.google.com/terms/generative-ai/use-policy).\n\n" +
                    "Key Terms & Restrictions:\n" +
                    "• You may not use Gemma to generate hate speech, harassment, violence, sexually explicit material, dangerous instructions, cyberattacks, or unlawful content.\n" +
                    "• You may not use Gemma for examination cheating, fraudulent impersonation, or unauthorized surveillance.\n" +
                    "• Models and weights are provided 'AS IS' WITHOUT WARRANTY OR CONDITIONS OF ANY KIND, express or implied."
        ),
        LegalLicenseItem(
            title = "Sherpa-ONNX Speech-to-Text Engine",
            badges = listOf("Apache License 2.0", "Xiaomi & Next-gen Kaldi"),
            copyright = "Copyright (c) 2022-2024 Xiaomi Corporation / Next-gen Kaldi Contributors",
            url = "https://github.com/k2-fsa/sherpa-onnx",
            licenseText = "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:\n\n" +
                    "http://www.apache.org/licenses/LICENSE-2.0\n\n" +
                    "Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
        ),
        LegalLicenseItem(
            title = "Google LiteRT-LM Neural Inference Runtime",
            badges = listOf("Apache License 2.0", "Google LLC"),
            copyright = "Copyright 2024-2025 The LiteRT Authors, Google LLC",
            url = "https://ai.google.dev/edge/litert",
            licenseText = "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:\n\n" +
                    "http://www.apache.org/licenses/LICENSE-2.0\n\n" +
                    "Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
        ),
        LegalLicenseItem(
            title = "Silero Voice Activity Detector (VAD)",
            badges = listOf("MIT License", "Silero Team"),
            copyright = "Copyright (c) 2020-2024 Silero Team",
            url = "https://github.com/snakers4/silero-vad",
            licenseText = "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n" +
                    "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\n" +
                    "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT."
        ),
        LegalLicenseItem(
            title = "OpenAI Whisper (tiny.en Quantized Rescorer)",
            badges = listOf("MIT License", "OpenAI"),
            copyright = "Copyright (c) 2022 OpenAI",
            url = "https://github.com/openai/whisper",
            licenseText = "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n" +
                    "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\n" +
                    "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT."
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PrompterIcons.InfoCircle(color = OledAccentIndigoSoft, size = 20.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Open Source Licenses & Attributions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OledTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Third-party open source software, model terms, and notices",
                        fontSize = 11.sp,
                        color = OledTextMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                licenses.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OledSurfaceHigh),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, OledBorderHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = item.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OledTextPrimary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                item.badges.forEach { badge ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                OledAccentIndigo.copy(alpha = 0.15f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                0.5.dp,
                                                OledAccentIndigo.copy(alpha = 0.35f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = OledAccentIndigoSoft
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = item.copyright,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OledTextSecondary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(OledBackground, RoundedCornerShape(6.dp))
                                    .border(0.5.dp, OledBorder, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = item.licenseText,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = OledTextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        try {
                                            uriHandler.openUri(item.url)
                                        } catch (_: Exception) {}
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                ) {
                                    Text(
                                        text = "Official Terms & Website →",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OledAccentIndigoSoft
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OledPrimaryAction,
                    contentColor = OledTextInverted
                )
            ) {
                Text(
                    text = "Close",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OledTextInverted
                )
            }
        }
    )
}
