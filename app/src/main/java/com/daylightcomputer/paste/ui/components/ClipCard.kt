package com.daylightcomputer.paste.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies

@Composable
fun ClipCard(
    clip: DaylightClip,
    onCopyMarkdown: () -> Unit,
    onCopyPlain: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeAgo = formatTimeAgo(clip.createdAt)
    val appName = formatAppName(clip.sourcePackage)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DaylightColors.CardBg)
            .border(1.5.dp, DaylightColors.BorderSubtle, RoundedCornerShape(14.dp))
            .clickable { onOpenReader() }
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Type Badge + Source + Time + Pin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Type Badge
                val (badgeText, badgeBg, badgeTextColor) = when (clip.clipType) {
                    ClipType.MARKDOWN -> Triple("MARKDOWN", DaylightColors.AmberSoft, DaylightColors.AmberDeep)
                    ClipType.CODE -> Triple("CODE", DaylightColors.SurfaceCream, DaylightColors.InkBlack)
                    ClipType.URL -> Triple("LINK", DaylightColors.SurfaceCream, DaylightColors.Indigo)
                    ClipType.TEXT -> Triple("TEXT", DaylightColors.SurfaceCream, DaylightColors.InkSubtle)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .border(1.dp, badgeTextColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontFamily = DaylightFontFamilies.RomExtendedLight,
                        fontSize = 10.sp,
                        color = badgeTextColor,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "$appName • $timeAgo",
                    fontFamily = DaylightFontFamilies.ArizonaSans,
                    fontSize = 12.sp,
                    color = DaylightColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (clip.isPinned) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Pin",
                        tint = if (clip.isPinned) DaylightColors.Amber595nm else DaylightColors.TextHint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = DaylightColors.TextHint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = clip.title,
                fontFamily = DaylightFontFamilies.ArizonaMix,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = DaylightColors.InkBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Content Snippet
            val isCode = clip.clipType == ClipType.CODE
            Text(
                text = clip.markdownContent.ifBlank { clip.textContent },
                fontFamily = if (isCode) FontFamily.Monospace else DaylightFontFamilies.ArizonaSans,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = DaylightColors.InkSubtle,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Footer: Word count pill + Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Word count badge
                val countLabel = "${formatNumber(clip.wordCount)} words • ${formatNumber(clip.charCount)} chars"
                Text(
                    text = countLabel,
                    fontFamily = DaylightFontFamilies.RomExtendedLight,
                    fontSize = 10.sp,
                    color = DaylightColors.TextMuted,
                    letterSpacing = 0.8.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Reader View Button
                    IconButton(
                        onClick = onOpenReader,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Read Full",
                            tint = DaylightColors.InkBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Copy Plain Button
                    OutlinedButton(
                        onClick = onCopyPlain,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DaylightColors.InkBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "PLAIN",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    // Copy Markdown Button (Amber Accent)
                    Button(
                        onClick = onCopyMarkdown,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DaylightColors.Amber595nm,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "COPY MARKDOWN",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        else -> "${days}d ago"
    }
}

private fun formatAppName(pkg: String): String {
    return when {
        pkg.contains("chrome", true) -> "Chrome"
        pkg.contains("chatgpt", true) || pkg.contains("openai", true) -> "ChatGPT"
        pkg.contains("claude", true) || pkg.contains("anthropic", true) -> "Claude"
        pkg.contains("dayone", true) -> "Day One"
        pkg.contains("paper", true) -> "Daylight Paper"
        pkg.contains("system", true) -> "System"
        else -> pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
    }
}

private fun formatNumber(num: Int): String {
    return java.text.NumberFormat.getNumberInstance().format(num)
}
