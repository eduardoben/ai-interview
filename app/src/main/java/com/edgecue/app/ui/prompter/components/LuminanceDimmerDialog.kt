package com.edgecue.app.ui.prompter.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgecue.app.ui.theme.*

/**
 * Dialog for adjusting screen luminance reduction for eye comfort and power efficiency.
 */
@Composable
fun LuminanceDimmerDialog(
    displayDimmerAlpha: Float,
    onDimmerChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "OLED Display Dimmer",
                color = OledTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    "Reduces screen luminance for eye comfort and power efficiency during extended practice sessions in low-light environments.",
                    color = OledTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Luminance Reduction", color = OledTextSecondary, fontSize = 12.sp)
                    Text(
                        "${(displayDimmerAlpha * 100).toInt()}%",
                        color = OledTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = displayDimmerAlpha,
                    onValueChange = onDimmerChange,
                    valueRange = 0f..0.70f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = OledPrimaryAction,
                        activeTrackColor = OledAccentIndigoSoft,
                        inactiveTrackColor = OledSurfaceHigh
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = OledAccentIndigoSoft, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = OledSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
