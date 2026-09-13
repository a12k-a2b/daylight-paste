package com.daylightcomputer.paste.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies

@Composable
fun ReaderDialog(
    clip: DaylightClip,
    onDismiss: () -> Unit,
    onCopyMarkdown: () -> Unit,
    onCopyPlain: () -> Unit
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
                            text = "${clip.wordCount} words • ${clip.charCount} characters • ${clip.sourcePackage}",
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

                // Scrollable Reader Body (Garamond for literary long-form reading)
                val isCode = clip.clipType == ClipType.CODE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = clip.markdownContent.ifBlank { clip.textContent },
                        fontFamily = if (isCode) FontFamily.Monospace else DaylightFontFamilies.Garamond,
                        fontSize = if (isCode) 14.sp else 18.sp,
                        lineHeight = if (isCode) 20.sp else 28.sp,
                        color = DaylightColors.InkBlack
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            onCopyPlain()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DaylightColors.InkBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            text = "COPY PLAIN TEXT",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            onCopyMarkdown()
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
                            text = "COPY CLEAN MARKDOWN",
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
