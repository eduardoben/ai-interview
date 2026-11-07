package com.edgecue.app.ui.dialog

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgecue.app.ui.theme.*

/**
 * Pre-Permission Audio Disclosure & Wiretapping Legal Consent Gate (Requirements R2, P0-04, P0-10).
 *
 * Mandated by Google Play User Data policies for microphone access and federal/state wiretapping statutes.
 * Explains ephemeral RAM-only processing and requires affirmative agreement to wiretapping consent laws
 * before requesting the Android runtime RECORD_AUDIO permission.
 */
@Composable
fun MicrophoneDisclosureDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var isConsentChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledSurface,
        title = {
            Text(
                text = "Microphone Access & Legal Consent Notice",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OledTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Ephemeral Processing Disclosure (Google Play User Data Policy)
                Card(
                    colors = CardDefaults.cardColors(containerColor = OledSurfaceHigh),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Ephemeral On-Device Processing",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OledAccentIndigoSoft
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Microphone audio is captured strictly for live speech-to-text dictation and practice rehearsal feedback. All speech is processed ephemerally on-device in volatile device memory (RAM) by local neural networks. Spoken audio is never stored to disk or persistent storage, and never transmitted over any network (zero cloud egress).",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = OledTextSecondary
                        )
                    }
                }

                // Section 2: Two-Party Wiretapping Legal Notice (18 U.S.C. § 2511, State Wiretap Acts)
                Card(
                    colors = CardDefaults.cardColors(containerColor = OledSurfaceHigh),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Two-Party Recording & Wiretapping Notice",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OledStatusAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Under US federal and state wiretapping laws (including California, Florida, Massachusetts, Pennsylvania, and other all-party consent jurisdictions) as well as international privacy regulations, recording or transcribing oral communications involving other individuals requires the consent of all parties.\n\nYou agree to use this application strictly for personal rehearsal, or to ensure that all required legal consents are obtained prior to transcribing conversations involving others.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = OledTextSecondary
                        )
                    }
                }

                // Section 3: Affirmative Consent Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isConsentChecked = !isConsentChecked }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isConsentChecked,
                        onCheckedChange = { isConsentChecked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = OledAccentIndigo,
                            uncheckedColor = OledTextMuted,
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I understand and agree to comply with all applicable recording and wiretapping consent laws.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                        color = OledTextPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isConsentChecked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OledPrimaryAction,
                    contentColor = OledTextInverted,
                    disabledContainerColor = OledSurfaceHigh,
                    disabledContentColor = OledTextMuted
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "I Understand & Agree",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (isConsentChecked) OledTextInverted else OledTextMuted
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
