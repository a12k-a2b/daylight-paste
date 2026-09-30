package com.daylightcomputer.paste.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImagePreviewDialog(
    clip: DaylightClip,
    onDismiss: () -> Unit,
    onCopyImage: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, DaylightColors.BorderStrong, RoundedCornerShape(16.dp)),
            color = DaylightColors.PaperBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = clip.title,
                            fontFamily = DaylightFontFamilies.ArizonaMix,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = DaylightColors.InkBlack
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${clip.summary ?: "Image"} • ${clip.sourcePackage}",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 11.sp,
                            color = DaylightColors.TextMuted,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = DaylightColors.InkBlack
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DaylightColors.BorderSubtle)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Image Preview Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DaylightColors.PaperBg)
                        .border(1.5.dp, DaylightColors.InkBlack, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val fullBitmap = rememberFullImageBitmap(clip.imageUri)
                    if (fullBitmap != null) {
                        Image(
                            bitmap = fullBitmap,
                            contentDescription = clip.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    } else {
                        Text(
                            text = "Loading Full Resolution Image...",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 12.sp,
                            color = DaylightColors.TextMuted,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DaylightColors.InkBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            text = "CLOSE",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            onCopyImage()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DaylightColors.Amber595nm,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            text = "COPY IMAGE",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun rememberFullImageBitmap(imageUriStr: String?): ImageBitmap? {
    var bitmap by remember(imageUriStr) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageUriStr) {
        if (imageUriStr.isNullOrBlank()) return@LaunchedEffect
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
                        // Subsample if image is extremely large (>1600px screen dimensions)
                        while (maxDim / (sample * 2) >= 1600) {
                            sample *= 2
                        }
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                        decoded?.let {
                            withContext(Dispatchers.Main) {
                                bitmap = it.asImageBitmap()
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
