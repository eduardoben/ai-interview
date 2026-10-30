package com.local.interviewassistant.ui.prompter.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.ui.theme.*

/**
 * Top prompter bar featuring active listening indicator, TTFT telemetry badge,
 * hardware chip context, stealth dimmer toggle, and focus mode expander.
 */
@Composable
fun PrompterHeader(
    pipelineState: AssistantState,
    isServiceRunning: Boolean,
    lastTtftMs: Long,
    hardwareSocName: String?,
    stealthDimmerAlpha: Float,
    isFocusMode: Boolean,
    onNavigateToSettings: () -> Unit,
    onToggleDimmer: () -> Unit,
    onToggleFocusMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (rawStatusColor, statusText) = when (pipelineState) {
        is AssistantState.Listening -> StealthStatusEmerald to "Listening"
        is AssistantState.DetectingSpeech -> StealthStatusEmerald to "Speech Active"
        is AssistantState.GeneratingAnswer -> StealthStatusAmber to "Synthesizing"
        is AssistantState.AnswerReady -> StealthAccentIndigoSoft to "Answer Ready"
        is AssistantState.Paused -> StealthStatusAmber to "Paused"
        is AssistantState.Error -> StealthStatusCoral to "Error"
        is AssistantState.Initializing -> StealthStatusAmber to "Loading Models"
        is AssistantState.Idle -> StealthTextMuted to (if (isServiceRunning) "Starting" else "Standby")
    }

    val statusDotColor by animateColorAsState(
        targetValue = rawStatusColor,
        animationSpec = tween(durationMillis = 300),
        label = "StatusDotColor"
    )

    val isActivelyListening = pipelineState is AssistantState.Listening ||
            pipelineState is AssistantState.DetectingSpeech

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isActivelyListening) 0.85f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Status indicator pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(StealthSurface, RoundedCornerShape(50))
                .border(0.5.dp, StealthBorder, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
                if (isActivelyListening) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusDotColor.copy(alpha = pulseAlpha * 0.35f), CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(statusDotColor, CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = statusText.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (pipelineState is AssistantState.Idle && !isServiceRunning) {
                    StealthTextSecondary
                } else {
                    statusDotColor
                }
            )
            if (lastTtftMs > 0L) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• ${lastTtftMs}ms",
                    fontSize = 10.sp,
                    color = StealthTextMuted
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Hardware & Persona Context Pill
            val socBadge = hardwareSocName?.takeIf { it.isNotBlank() } ?: "On-Device AI"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(StealthSurface)
                    .border(0.5.dp, StealthBorder, RoundedCornerShape(50))
                    .clickable { onNavigateToSettings() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                StealthIcons.CpuChip(color = StealthAccentIndigoSoft, size = 13.dp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = socBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = StealthTextSecondary
                )
            }

            // Stealth Dimmer Action Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (stealthDimmerAlpha > 0f) StealthAccentIndigo.copy(alpha = 0.16f) else StealthSurface)
                    .border(0.5.dp, if (stealthDimmerAlpha > 0f) StealthAccentIndigo.copy(alpha = 0.5f) else StealthBorder, CircleShape)
                    .clickable { onToggleDimmer() },
                contentAlignment = Alignment.Center
            ) {
                StealthIcons.DimmerMoon(
                    color = if (stealthDimmerAlpha > 0f) StealthAccentIndigoSoft else StealthTextSecondary,
                    isDimmed = stealthDimmerAlpha > 0f,
                    size = 15.dp
                )
            }

            // Focus Mode Toggle Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isFocusMode) StealthAccentIndigo.copy(alpha = 0.16f) else StealthSurface)
                    .border(0.5.dp, if (isFocusMode) StealthAccentIndigo.copy(alpha = 0.5f) else StealthBorder, CircleShape)
                    .clickable { onToggleFocusMode() },
                contentAlignment = Alignment.Center
            ) {
                StealthIcons.FocusExpand(
                    color = if (isFocusMode) StealthAccentIndigoSoft else StealthTextSecondary,
                    isExpanded = isFocusMode,
                    size = 15.dp
                )
            }
        }
    }
}

