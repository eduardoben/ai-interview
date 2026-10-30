package com.local.interviewassistant.ui.prompter.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.ui.theme.*

/**
 * Horizontal chip strip allowing quick selection of interview modes
 * (General, Technical Coding, System Design, Behavioral STAR, Quick Summary).
 * Collapses in Focus Mode to maximize reading space.
 */
@Composable
fun CategoryChipRow(
    selectedCategory: InterviewCategory,
    isFocusMode: Boolean,
    onSelectCategory: (InterviewCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = !isFocusMode, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InterviewCategory.entries.forEach { category ->
                val isSelected = selectedCategory == category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) StealthAccentIndigo.copy(alpha = 0.18f) else StealthSurface
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = if (isSelected) StealthAccentIndigo else StealthBorder,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onSelectCategory(category) }
                        .padding(horizontal = 13.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = category.displayName,
                        fontSize = 11.sp,
                        letterSpacing = 0.2.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) StealthTextPrimary else StealthTextSecondary
                    )
                }
            }
        }
    }
}

