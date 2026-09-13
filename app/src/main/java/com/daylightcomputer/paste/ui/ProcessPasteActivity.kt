package com.daylightcomputer.paste.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles "Daylight Paste" directly from Android's floating text selection toolbar (tooltip menu).
 * Shows a fast SolOS LivePaper picker sheet of recent clippings. Tapping any clip instantly
 * pastes it into the active text field.
 */
class ProcessPasteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

        setContent {
            var clips by remember { mutableStateOf<List<DaylightClip>>(emptyList()) }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                val db = ClipDatabase.getInstance(applicationContext)
                val loadedClips = withContext(Dispatchers.IO) {
                    db.getAllClips()
                }
                clips = loadedClips
                isLoading = false
            }

            Dialog(
                onDismissRequest = { finish() },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DaylightColors.InkBlack.copy(alpha = 0.5f))
                        .clickable { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.75f)
                            .clickable(enabled = false) {}
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        color = DaylightColors.PaperBg,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "PASTE FROM DAYLIGHT",
                                        fontFamily = DaylightTypography.ArizonaMix,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DaylightColors.InkBlack
                                    )
                                    Text(
                                        text = if (isReadOnly) "Select clip to copy clean Markdown" else "Tap a clipping to paste into active field",
                                        fontFamily = DaylightTypography.ArizonaSans,
                                        fontSize = 13.sp,
                                        color = DaylightColors.InkSubtle
                                    )
                                }

                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = DaylightColors.InkBlack
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = DaylightColors.AmberAccent)
                                }
                            } else if (clips.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No clippings stored yet",
                                        fontFamily = DaylightTypography.ArizonaSans,
                                        color = DaylightColors.InkSubtle,
                                        fontSize = 15.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(clips, key = { it.id }) { clip ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.5.dp, DaylightColors.BorderStrong, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onClipSelected(clip, isReadOnly)
                                                },
                                            color = DaylightColors.SurfaceCream
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = clip.title,
                                                        fontFamily = DaylightTypography.ArizonaMix,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DaylightColors.InkBlack,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    
                                                    Surface(
                                                        color = DaylightColors.AmberAccent,
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier.padding(start = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = clip.contentType,
                                                            color = DaylightColors.PaperBg,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = clip.markdownContent.trim(),
                                                    fontFamily = DaylightTypography.ArizonaSans,
                                                    fontSize = 13.sp,
                                                    color = DaylightColors.InkBlack,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Text(
                                                    text = "${clip.charCount} chars · ${clip.wordCount} words · ${clip.sourcePackage}",
                                                    fontFamily = DaylightTypography.RomExtended,
                                                    fontSize = 10.sp,
                                                    color = DaylightColors.InkSubtle
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onClipSelected(clip: DaylightClip, isReadOnly: Boolean) {
        if (!isReadOnly) {
            val resultIntent = Intent().apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, clip.markdownContent)
            }
            setResult(RESULT_OK, resultIntent)
        } else {
            // In read-only contexts, place on clipboard
            com.daylightcomputer.paste.service.DaylightPasteManager.copyCleanMarkdownToClipboard(
                this,
                clip.markdownContent,
                clip.title
            )
        }
        finish()
    }
}
