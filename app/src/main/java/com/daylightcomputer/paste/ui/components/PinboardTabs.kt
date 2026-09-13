package com.daylightcomputer.paste.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies

@Composable
fun PinboardTabs(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        "ALL" to "ALL",
        "PINNED" to "⭐ PINNED",
        "AI_NOTES" to "🤖 AI & WRITING",
        "MARKDOWN" to "📝 MARKDOWN",
        "CODE" to "💻 CODE",
        "LINKS" to "🔗 LINKS"
    )

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (key, label) ->
            val isSelected = selectedFilter.equals(key, ignoreCase = true)
            val bgColor = if (isSelected) DaylightColors.InkBlack else DaylightColors.SurfaceCream
            val textColor = if (isSelected) DaylightColors.PaperBg else DaylightColors.InkBlack
            val borderColor = if (isSelected) DaylightColors.InkBlack else DaylightColors.BorderSubtle

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onFilterSelected(key) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = DaylightFontFamilies.RomExtendedLight,
                    fontSize = 11.sp,
                    color = textColor,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}
