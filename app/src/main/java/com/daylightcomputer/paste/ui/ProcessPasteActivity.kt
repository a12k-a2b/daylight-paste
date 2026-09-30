package com.daylightcomputer.paste.ui

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.view.WindowCompat
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import com.daylightcomputer.paste.service.DaylightPasteManager
import com.daylightcomputer.paste.ui.components.SearchBar
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles "Daylight Paste" directly from Android's floating text selection toolbar (tooltip menu).
 * Shows a fast SolOS LivePaper picker sheet of recent clippings with instant search filtering.
 * Tapping any clip instantly pastes it into the active text field.
 */
class ProcessPasteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

        setContent {
            var allClips by remember { mutableStateOf<List<DaylightClip>>(emptyList()) }
            var searchQuery by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(true) }

            val db = remember { ClipDatabase.getInstance(applicationContext) }

            LaunchedEffect(searchQuery) {
                isLoading = true
                val loaded = withContext(Dispatchers.IO) {
                    db.getClips(searchQuery = searchQuery)
                }
                allClips = loaded
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
                        .background(DaylightColors.InkBlack.copy(alpha = 0.4f))
                        .clickable { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.82f)
                            .clickable(enabled = false) {}
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .border(1.5.dp, DaylightColors.BorderStrong, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        color = DaylightColors.PaperBg,
                        shadowElevation = 0.dp // LivePaper rule: zero elevation dropshadows
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "PASTE FROM DAYLIGHT",
                                        fontFamily = DaylightFontFamilies.ArizonaMix,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DaylightColors.InkBlack
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isReadOnly) "Select clip to copy to clipboard" else "Tap a clipping to paste into active field",
                                        fontFamily = DaylightFontFamilies.RomExtendedLight,
                                        fontSize = 11.sp,
                                        color = DaylightColors.TextMuted,
                                        letterSpacing = 0.8.sp
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

                            Spacer(modifier = Modifier.height(12.dp))

                            // Search bar in picker
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = DaylightColors.Amber595nm)
                                }
                            } else if (allClips.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "No clips match '$searchQuery'" else "Clipboard history is empty",
                                        fontFamily = DaylightFontFamilies.ArizonaSans,
                                        color = DaylightColors.TextMuted,
                                        fontSize = 15.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    items(allClips, key = { it.id }) { clip ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.5.dp, DaylightColors.BorderSubtle, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onClipSelected(clip.markdownContent, isReadOnly, clip)
                                                },
                                            color = DaylightColors.CardBg,
                                            shadowElevation = 0.dp
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
                                                        fontFamily = DaylightFontFamilies.ArizonaMix,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DaylightColors.InkBlack,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    
                                                    val badgeBg = if (clip.clipType == ClipType.MARKDOWN) DaylightColors.AmberSoft else DaylightColors.SurfaceCream
                                                    val badgeText = if (clip.clipType == ClipType.MARKDOWN) DaylightColors.AmberDeep else DaylightColors.InkBlack

                                                    Surface(
                                                        color = badgeBg,
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeText.copy(alpha = 0.3f)),
                                                        modifier = Modifier.padding(start = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = clip.clipType.name,
                                                            color = badgeText,
                                                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.8.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = clip.markdownContent.trim(),
                                                    fontFamily = DaylightFontFamilies.ArizonaSans,
                                                    fontSize = 13.sp,
                                                    color = DaylightColors.InkSubtle,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${clip.wordCount} words · ${clip.sourcePackage}",
                                                        fontFamily = DaylightFontFamilies.RomExtendedLight,
                                                        fontSize = 10.sp,
                                                        color = DaylightColors.TextMuted,
                                                        letterSpacing = 0.8.sp
                                                    )

                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        OutlinedButton(
                                                            onClick = {
                                                                val plain = MarkdownTranspiler.stripFormatting(clip.markdownContent)
                                                                onClipSelected(plain, isReadOnly, clip)
                                                            },
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DaylightColors.InkBlack),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(
                                                                text = "PLAIN",
                                                                fontFamily = DaylightFontFamilies.RomExtendedLight,
                                                                fontSize = 9.sp,
                                                                letterSpacing = 0.8.sp
                                                            )
                                                        }

                                                        Button(
                                                            onClick = {
                                                                onClipSelected(clip.markdownContent, isReadOnly, clip)
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = DaylightColors.Amber595nm,
                                                                contentColor = androidx.compose.ui.graphics.Color.White
                                                            ),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(
                                                                text = "MARKDOWN",
                                                                fontFamily = DaylightFontFamilies.RomExtendedLight,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                letterSpacing = 0.8.sp
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
            }
        }
    }

    private fun onClipSelected(textToPaste: String, isReadOnly: Boolean, originalClip: DaylightClip) {
        if (!isReadOnly) {
            // Cap at safe Binder payload size (100k UTF-16 chars ~ 200KB) to prevent TransactionTooLargeException
            val safeText = if (textToPaste.length > 100_000) textToPaste.take(100_000) else textToPaste
            val resultIntent = Intent().apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, safeText)
            }
            setResult(RESULT_OK, resultIntent)
        } else {
            // In read-only contexts, place the user's selected format (PLAIN or MARKDOWN) on clipboard
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val safeText = if (textToPaste.length > 400_000) textToPaste.take(400_000) else textToPaste
            DaylightPasteManager.markInternalCopy(safeText)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(originalClip.title, safeText))
            android.widget.Toast.makeText(this, "✓ Copied to Clipboard", android.widget.Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
