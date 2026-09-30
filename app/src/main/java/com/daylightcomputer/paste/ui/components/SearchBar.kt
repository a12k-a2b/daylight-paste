package com.daylightcomputer.paste.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isAiMode: Boolean = true,
    onToggleAiMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DaylightColors.CardBg, RoundedCornerShape(12.dp))
            .border(
                1.5.dp,
                if (isAiMode) DaylightColors.BorderStrong else DaylightColors.BorderSubtle,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = DaylightColors.InkBlack,
                modifier = Modifier.padding(end = 10.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = if (isAiMode) {
                            "Ask or search semantically (e.g. 'wifi password', 'recipe')..."
                        } else {
                            "Search exact keywords..."
                        },
                        fontFamily = DaylightFontFamilies.ArizonaSans,
                        fontSize = 14.sp,
                        color = DaylightColors.TextHint
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = DaylightFontFamilies.ArizonaSans,
                        fontSize = 14.5.sp,
                        color = DaylightColors.InkBlack
                    ),
                    cursorBrush = SolidColor(DaylightColors.Amber595nm),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = DaylightColors.InkBlack
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Semantic AI Mode Toggle Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isAiMode) DaylightColors.Amber595nm else DaylightColors.SurfaceCream)
                    .border(
                        1.dp,
                        if (isAiMode) DaylightColors.BorderStrong else DaylightColors.BorderSubtle,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleAiMode() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isAiMode) "✨ AI SEMANTIC" else "🔍 KEYWORD",
                    fontFamily = DaylightFontFamilies.RomExtendedLight,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = DaylightColors.InkBlack,
                    letterSpacing = 1.0.sp
                )
            }
        }
    }
}
