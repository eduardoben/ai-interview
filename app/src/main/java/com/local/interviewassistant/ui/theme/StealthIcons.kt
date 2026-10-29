package com.local.interviewassistant.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimal, geometric line-art icons crafted specifically for the Stealth OLED design system.
 * Eliminates inconsistent OEM color emojis while maintaining zero external icon-font overhead.
 */
object StealthIcons {

    @Composable
    fun Prompter(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.14f, h * 0.16f),
                size = Size(w * 0.72f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                style = stroke
            )
            drawLine(
                color = color,
                start = Offset(w * 0.30f, h * 0.36f),
                end = Offset(w * 0.70f, h * 0.36f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.30f, h * 0.52f),
                end = Offset(w * 0.54f, h * 0.52f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.38f, h * 0.86f),
                end = Offset(w * 0.62f, h * 0.86f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Sliders(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
        Canvas(modifier = modifier.size(size)) {
            val strokeWidth = 1.75.dp.toPx()
            val w = this.size.width
            val h = this.size.height

            // Row 1
            drawLine(color, Offset(w * 0.15f, h * 0.28f), Offset(w * 0.52f, h * 0.28f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.85f, h * 0.28f), strokeWidth, StrokeCap.Round)
            drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.62f, h * 0.28f), style = Stroke(strokeWidth))

            // Row 2
            drawLine(color, Offset(w * 0.15f, h * 0.50f), Offset(w * 0.28f, h * 0.50f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.48f, h * 0.50f), Offset(w * 0.85f, h * 0.50f), strokeWidth, StrokeCap.Round)
            drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.38f, h * 0.50f), style = Stroke(strokeWidth))

            // Row 3
            drawLine(color, Offset(w * 0.15f, h * 0.72f), Offset(w * 0.58f, h * 0.72f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.78f, h * 0.72f), Offset(w * 0.85f, h * 0.72f), strokeWidth, StrokeCap.Round)
            drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.68f, h * 0.72f), style = Stroke(strokeWidth))
        }
    }

    @Composable
    fun ActivityBar(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
        Canvas(modifier = modifier.size(size)) {
            val strokeWidth = 1.85.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            drawLine(color, Offset(w * 0.22f, h * 0.80f), Offset(w * 0.22f, h * 0.54f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.41f, h * 0.80f), Offset(w * 0.41f, h * 0.24f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.60f, h * 0.80f), Offset(w * 0.60f, h * 0.42f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.79f, h * 0.80f), Offset(w * 0.79f, h * 0.30f), strokeWidth, StrokeCap.Round)
        }
    }

    @Composable
    fun InfoCircle(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
        Canvas(modifier = modifier.size(size)) {
            val strokeWidth = 1.75.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            drawCircle(
                color = color,
                radius = w * 0.38f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = Stroke(strokeWidth)
            )
            drawLine(
                color = color,
                start = Offset(w * 0.5f, h * 0.46f),
                end = Offset(w * 0.5f, h * 0.68f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = color,
                radius = strokeWidth * 0.65f,
                center = Offset(w * 0.5f, h * 0.33f)
            )
        }
    }

    @Composable
    fun CpuChip(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.24f, h * 0.24f),
                size = Size(w * 0.52f, h * 0.52f),
                cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
                style = stroke
            )
            // Pins
            val pins = listOf(0.38f, 0.62f)
            pins.forEach { p ->
                drawLine(color, Offset(w * p, h * 0.10f), Offset(w * p, h * 0.24f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * p, h * 0.76f), Offset(w * p, h * 0.90f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.10f, h * p), Offset(w * 0.24f, h * p), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.76f, h * p), Offset(w * 0.90f, h * p), stroke.width, StrokeCap.Round)
            }
        }
    }

    @Composable
    fun DimmerMoon(color: Color, isDimmed: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
        Canvas(modifier = modifier.size(size)) {
            val strokeWidth = 1.5.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            if (isDimmed) {
                val path = Path().apply {
                    moveTo(w * 0.65f, h * 0.18f)
                    cubicTo(w * 0.32f, h * 0.20f, w * 0.18f, h * 0.55f, w * 0.42f, h * 0.78f)
                    cubicTo(w * 0.60f, h * 0.92f, w * 0.82f, h * 0.80f, w * 0.84f, h * 0.64f)
                    cubicTo(w * 0.62f, h * 0.68f, w * 0.46f, h * 0.46f, w * 0.65f, h * 0.18f)
                    close()
                }
                drawPath(path, color = color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(strokeWidth))
                val rays = 8
                for (i in 0 until rays) {
                    val angle = (i * 45.0) * (Math.PI / 180.0)
                    val r1 = w * 0.34f
                    val r2 = w * 0.44f
                    drawLine(
                        color = color,
                        start = Offset((w * 0.5f + r1 * kotlin.math.cos(angle)).toFloat(), (h * 0.5f + r1 * kotlin.math.sin(angle)).toFloat()),
                        end = Offset((w * 0.5f + r2 * kotlin.math.cos(angle)).toFloat(), (h * 0.5f + r2 * kotlin.math.sin(angle)).toFloat()),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }

    @Composable
    fun FocusExpand(color: Color, isExpanded: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                if (!isExpanded) {
                    // Top-left corner
                    moveTo(w * 0.18f, h * 0.38f); lineTo(w * 0.18f, h * 0.18f); lineTo(w * 0.38f, h * 0.18f)
                    // Top-right corner
                    moveTo(w * 0.62f, h * 0.18f); lineTo(w * 0.82f, h * 0.18f); lineTo(w * 0.82f, h * 0.38f)
                    // Bottom-right corner
                    moveTo(w * 0.82f, h * 0.62f); lineTo(w * 0.82f, h * 0.82f); lineTo(w * 0.62f, h * 0.82f)
                    // Bottom-left corner
                    moveTo(w * 0.38f, h * 0.82f); lineTo(w * 0.18f, h * 0.82f); lineTo(w * 0.18f, h * 0.62f)
                } else {
                    // Inward corners
                    moveTo(w * 0.16f, h * 0.36f); lineTo(w * 0.36f, h * 0.36f); lineTo(w * 0.36f, h * 0.16f)
                    moveTo(w * 0.64f, h * 0.16f); lineTo(w * 0.64f, h * 0.36f); lineTo(w * 0.84f, h * 0.36f)
                    moveTo(w * 0.84f, h * 0.64f); lineTo(w * 0.64f, h * 0.64f); lineTo(w * 0.64f, h * 0.84f)
                    moveTo(w * 0.36f, h * 0.84f); lineTo(w * 0.36f, h * 0.64f); lineTo(w * 0.16f, h * 0.64f)
                }
            }
            drawPath(path, color = color, style = stroke)
        }
    }

    @Composable
    fun Microphone(color: Color, modifier: Modifier = Modifier, size: Dp = 15.dp) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.34f, h * 0.12f),
                size = Size(w * 0.32f, h * 0.46f),
                cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.20f, h * 0.26f),
                size = Size(w * 0.60f, h * 0.46f),
                style = stroke
            )
            drawLine(color, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.50f, h * 0.88f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.34f, h * 0.88f), Offset(w * 0.66f, h * 0.88f), stroke.width, StrokeCap.Round)
        }
    }

    @Composable
    fun Check(color: Color, modifier: Modifier = Modifier, size: Dp = 15.dp) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.18f, h * 0.52f)
                lineTo(w * 0.42f, h * 0.74f)
                lineTo(w * 0.82f, h * 0.26f)
            }
            drawPath(path, color = color, style = stroke)
        }
    }

    @Composable
    fun AlertCircle(color: Color, modifier: Modifier = Modifier, size: Dp = 15.dp) {
        Canvas(modifier = modifier.size(size)) {
            val strokeWidth = 1.6.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            drawCircle(color, radius = w * 0.40f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(strokeWidth))
            drawLine(color, Offset(w * 0.5f, h * 0.30f), Offset(w * 0.5f, h * 0.54f), strokeWidth, StrokeCap.Round)
            drawCircle(color, radius = strokeWidth * 0.65f, center = Offset(w * 0.5f, h * 0.69f))
        }
    }

    @Composable
    fun MinimizeLine(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
        Canvas(modifier = modifier.size(size)) {
            val strokeWidth = 1.75.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            drawLine(color, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), strokeWidth, StrokeCap.Round)
        }
    }

    @Composable
    fun CloseCross(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
        Canvas(modifier = modifier.size(size)) {
            val strokeWidth = 1.75.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            drawLine(color, Offset(w * 0.24f, h * 0.24f), Offset(w * 0.76f, h * 0.76f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.76f, h * 0.24f), Offset(w * 0.24f, h * 0.76f), strokeWidth, StrokeCap.Round)
        }
    }

    @Composable
    fun ChevronUp(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.65.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.64f)
                lineTo(w * 0.50f, h * 0.36f)
                lineTo(w * 0.78f, h * 0.64f)
            }
            drawPath(path, color = color, style = stroke)
        }
    }
}

