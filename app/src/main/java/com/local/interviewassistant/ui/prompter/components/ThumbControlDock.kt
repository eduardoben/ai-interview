package com.local.interviewassistant.ui.prompter.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.ui.theme.*

/**
 * Bottom thumb-zone control bar for launching, answering, pausing, clearing, and stopping the assistant.
 */
@Composable
fun ThumbControlDock(
    isServiceRunning: Boolean,
    isPaused: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onForceAnswer: () -> Unit,
    onPauseToggle: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (!isServiceRunning) {
            Button(
                onClick = onStartService,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StealthAccentIndigo,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Start Live Copilot",
                    fontSize = 15.sp,
                    letterSpacing = 0.2.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onForceAnswer,
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StealthAccentIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Answer Now",
                        fontSize = 13.sp,
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = onPauseToggle,
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StealthSurface,
                        contentColor = StealthTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, StealthBorderHigh)
                ) {
                    Text(
                        text = if (isPaused) "Resume" else "Pause",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StealthSurface,
                        contentColor = StealthTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, StealthBorder)
                ) {
                    Text("Clear", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onStopService,
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StealthStatusCoral.copy(alpha = 0.1f),
                        contentColor = StealthStatusCoral
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, StealthStatusCoral.copy(alpha = 0.35f))
                ) {
                    Text("Stop", fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

