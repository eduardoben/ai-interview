package com.local.interviewassistant.ui.prompter.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.ui.theme.*

/**
 * Dialog for adjusting screen luminance reduction on OLED panels.
 */
@Composable
fun StealthDimmerDialog(
    stealthDimmerAlpha: Float,
    onDimmerChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Stealth OLED Dimmer",
                color = StealthTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    "Reduces screen luminance to eliminate glare on eyeglasses or webcams during low-light video calls.",
                    color = StealthTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Luminance Reduction", color = StealthTextSecondary, fontSize = 12.sp)
                    Text(
                        "${(stealthDimmerAlpha * 100).toInt()}%",
                        color = StealthTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = stealthDimmerAlpha,
                    onValueChange = onDimmerChange,
                    valueRange = 0f..0.70f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = StealthAccentIndigo,
                        activeTrackColor = StealthAccentIndigo,
                        inactiveTrackColor = StealthSurfaceHigh
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = StealthAccentIndigoSoft, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = StealthSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

