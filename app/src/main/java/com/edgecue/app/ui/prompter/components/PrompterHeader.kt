package com.edgecue.app.ui.prompter.components

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
import com.edgecue.app.core.model.AssistantState
import com.edgecue.app.ui.theme.*

/**
 * Top prompter bar featuring active listening indicator, TTFT telemetry badge,
 * hardware chip context, display dimmer toggle, and focus mode expander.
 */
@Composable
fun PrompterHeader(
    pipelineState: AssistantState,
    isServiceRunning: Boolean,
    lastTtftMs: Long,
    hardwareSocName: String?,
    displayDimmerAlpha: Float,
    isFocusMode: Boolean,
    onNavigateToSettings: () -> Unit,
    onToggleDimmer: () -> Unit,
    onToggleFocusMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (rawStatusColor, statusText) = when (pipelineState) {
        is AssistantState.Listening -> OledStatusEmerald to "Listening"
        is AssistantState.DetectingSpeech -> OledStatusEmerald to "Speech Active"
        is AssistantState.GeneratingAnswer -> OledStatusAmber to "Synthesizing"
        is AssistantState.AnswerReady -> OledAccentIndigoSoft to "Answer Ready"
        is AssistantState.Paused -> OledStatusAmber to "Paused"
        is AssistantState.Error -> OledStatusCoral to "Error"
        is AssistantState.Initializing -> OledStatusAmber to "Loading Models"
        is AssistantState.Idle -> OledTextMuted to (if (isServiceRunning) "Starting" else "Standby")
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
        // EdgeCue brand logo & status indicator pill
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrompterIcons.EdgeCueLogo(color = OledTextPrimary, size = 36.dp)
            Spacer(modifier = Modifier.width(12.dp))

            // Status indicator pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(OledSurface, RoundedCornerShape(50))
                    .border(0.5.dp, OledBorder, RoundedCornerShape(50))
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
                        OledTextSecondary
                    } else {
                        statusDotColor
                    }
                )
                if (lastTtftMs > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${lastTtftMs}ms",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = OledTextMuted
                    )
                }
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
                    .background(OledSurface)
                    .border(0.5.dp, OledBorder, RoundedCornerShape(50))
                    .clickable { onNavigateToSettings() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                PrompterIcons.CpuChip(color = OledAccentIndigoSoft, size = 13.dp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = socBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OledTextSecondary
                )
            }

            // Display Dimmer Action Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (displayDimmerAlpha > 0f) OledSurfaceElevated else OledSurface)
                    .border(0.5.dp, if (displayDimmerAlpha > 0f) OledBorderHigh else OledBorder, CircleShape)
                    .clickable { onToggleDimmer() },
                contentAlignment = Alignment.Center
            ) {
                PrompterIcons.DimmerMoon(
                    color = if (displayDimmerAlpha > 0f) OledAccentIndigoSoft else OledTextSecondary,
                    isDimmed = displayDimmerAlpha > 0f,
                    size = 15.dp
                )
            }

            // Focus Mode Toggle Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isFocusMode) OledSurfaceElevated else OledSurface)
                    .border(0.5.dp, if (isFocusMode) OledBorderHigh else OledBorder, CircleShape)
                    .clickable { onToggleFocusMode() },
                contentAlignment = Alignment.Center
            ) {
                PrompterIcons.FocusExpand(
                    color = if (isFocusMode) OledAccentIndigoSoft else OledTextSecondary,
                    isExpanded = isFocusMode,
                    size = 15.dp
                )
            }
        }
    }
}

