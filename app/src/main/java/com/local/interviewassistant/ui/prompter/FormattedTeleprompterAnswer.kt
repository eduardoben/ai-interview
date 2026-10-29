package com.local.interviewassistant.ui.prompter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.ui.theme.StealthAccentIndigo
import com.local.interviewassistant.ui.theme.StealthBorder
import com.local.interviewassistant.ui.theme.StealthTextPrimary
import com.local.interviewassistant.ui.theme.StealthTextSecondary

@Composable
fun FormattedTeleprompterAnswer(
    text: String,
    fontSize: TextUnit = 20.sp,
    modifier: Modifier = Modifier
) {
    val lines = text.split("\n")
    var inCodeBlock = false
    val currentCode = StringBuilder()

    Column(modifier = modifier.fillMaxWidth()) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    val codeContent = currentCode.toString().trimEnd()
                    currentCode.clear()
                    inCodeBlock = false
                    CodeBlockCard(codeContent)
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                currentCode.append(line).append("\n")
                continue
            }

            if (trimmed.isBlank()) {
                Box(modifier = Modifier.padding(vertical = 4.dp))
                continue
            }

            val isBullet = trimmed.startsWith("•") || trimmed.startsWith("- ") || trimmed.startsWith("* ")
            val bulletCleaned = if (isBullet) {
                if (trimmed.startsWith("•")) trimmed.substring(1).trim()
                else trimmed.substring(2).trim()
            } else trimmed

            val annotatedText = buildAnnotatedString {
                parseMarkdownSpans(bulletCleaned)
            }

            if (isBullet) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = (fontSize.value * 0.52f).dp)
                            .size(5.dp)
                            .background(StealthAccentIndigo, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = annotatedText,
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.48f).sp,
                        letterSpacing = 0.1.sp,
                        color = StealthTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = annotatedText,
                    fontSize = fontSize,
                    lineHeight = (fontSize.value * 1.48f).sp,
                    letterSpacing = 0.1.sp,
                    color = StealthTextPrimary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        if (inCodeBlock && currentCode.isNotEmpty()) {
            CodeBlockCard(currentCode.toString().trimEnd())
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.parseMarkdownSpans(text: String) {
    var i = 0
    while (i < text.length) {
        val nextBold = text.indexOf("**", i)
        if (nextBold == -1) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, nextBold))
        val endBold = text.indexOf("**", nextBold + 2)
        if (endBold == -1) {
            append(text.substring(nextBold))
            break
        }
        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(nextBold + 2, endBold))
        }
        i = endBold + 2
    }
}

@Composable
private fun CodeBlockCard(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF040508), RoundedCornerShape(10.dp))
            .border(0.5.dp, StealthBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        Text(
            text = code,
            color = StealthTextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
