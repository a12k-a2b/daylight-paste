package com.daylightcomputer.paste.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ClipCard(
    clip: DaylightClip,
    onCopyMarkdown: () -> Unit,
    onCopyPlain: () -> Unit,
    onCopyImage: () -> Unit = {},
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeAgo = formatTimeAgo(clip.createdAt)
    val appName = formatAppName(clip.sourcePackage)
    val isImage = clip.isImage

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DaylightColors.CardBg)
            .border(1.5.dp, DaylightColors.BorderSubtle, RoundedCornerShape(14.dp))
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
                    ClipType.IMAGE -> Triple("IMAGE", DaylightColors.AmberSoft, DaylightColors.AmberDeep)
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpenReader() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content: Image Thumbnail or Text Snippet
            if (isImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DaylightColors.PaperBg)
                        .border(1.5.dp, DaylightColors.InkBlack, RoundedCornerShape(8.dp))
                        .clickable { onOpenReader() },
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = rememberThumbnailBitmap(clip.imageUri)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = clip.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = DaylightColors.TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "IMAGE PREVIEW",
                                fontFamily = DaylightFontFamilies.RomExtendedLight,
                                fontSize = 10.sp,
                                color = DaylightColors.TextMuted,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            } else {
                val isCode = clip.clipType == ClipType.CODE
                Text(
                    text = clip.markdownContent.ifBlank { clip.textContent },
                    fontFamily = if (isCode) FontFamily.Monospace else DaylightFontFamilies.ArizonaSans,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    color = DaylightColors.InkSubtle,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenReader() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer: Metadata + Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Word count / dimensions badge
                val metaLabel = if (isImage) {
                    clip.summary ?: "Image"
                } else {
                    "${formatNumber(clip.wordCount)} words • ${formatNumber(clip.charCount)} chars"
                }

                Text(
                    text = metaLabel,
                    fontFamily = DaylightFontFamilies.RomExtendedLight,
                    fontSize = 10.sp,
                    color = DaylightColors.TextMuted,
                    letterSpacing = 0.8.sp
                )

                if (isImage) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onOpenReader,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Full Preview",
                                tint = DaylightColors.InkBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Button(
                            onClick = onCopyImage,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DaylightColors.Amber595nm,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "COPY IMAGE",
                                fontFamily = DaylightFontFamilies.RomExtendedLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                } else {
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
}

object ThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val cache = object : android.util.LruCache<String, ImageBitmap>(cacheSize) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4) / 1024
        }
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

@Composable
fun rememberThumbnailBitmap(imageUriStr: String?): ImageBitmap? {
    val cached = remember(imageUriStr) {
        imageUriStr?.let { ThumbnailCache.get(it) }
    }
    var bitmap by remember(imageUriStr) { mutableStateOf<ImageBitmap?>(cached) }
    LaunchedEffect(imageUriStr) {
        if (imageUriStr.isNullOrBlank() || bitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(imageUriStr)
                val path = if (uri.scheme == "file") uri.path else imageUriStr
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, bounds)
                        var sample = 1
                        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                        while (maxDim / (sample * 2) >= 400) {
                            sample *= 2
                        }
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                        decoded?.let {
                            val imgBmp = it.asImageBitmap()
                            ThumbnailCache.put(imageUriStr, imgBmp)
                            withContext(Dispatchers.Main) {
                                bitmap = imgBmp
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return bitmap
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
