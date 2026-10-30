package com.local.interviewassistant.ui.prompter.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.ui.theme.*

/**
 * Collapsible Live Transcript Bar previewing live speech or opening the prompt sheet.
 */
@Composable
fun LiveTranscriptBar(
    livePartialTranscript: String,
    transcriptHistory: List<TranscriptSegment>,
    isServiceRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StealthSurface)
            .border(
                0.5.dp,
                if (livePartialTranscript.isNotBlank()) StealthStatusEmerald.copy(alpha = 0.55f) else StealthBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            StealthIcons.Microphone(
                color = if (livePartialTranscript.isNotBlank()) StealthStatusEmerald else StealthTextMuted,
                size = 14.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            val transcriptPreview = when {
                livePartialTranscript.isNotBlank() -> "$livePartialTranscript..."
                transcriptHistory.isNotEmpty() -> transcriptHistory.last().text
                isServiceRunning -> "Listening... tap to type or view transcript"
                else -> "Tap to type a custom question or view history"
            }
            Text(
                text = transcriptPreview,
                color = if (livePartialTranscript.isNotBlank()) StealthStatusEmerald else StealthTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Prompt",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = StealthAccentIndigoSoft
            )
            Spacer(modifier = Modifier.width(4.dp))
            StealthIcons.ChevronUp(color = StealthAccentIndigoSoft, size = 12.dp)
        }
    }
}

