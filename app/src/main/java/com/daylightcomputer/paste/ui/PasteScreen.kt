package com.daylightcomputer.paste.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.service.DaylightPasteManager
import com.daylightcomputer.paste.ui.components.ClipCard
import com.daylightcomputer.paste.ui.components.ImagePreviewDialog
import com.daylightcomputer.paste.ui.components.PinboardTabs
import com.daylightcomputer.paste.ui.components.ReaderDialog
import com.daylightcomputer.paste.ui.components.SearchBar
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PasteScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ClipDatabase.getInstance(context) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var clips by remember { mutableStateOf<List<DaylightClip>>(emptyList()) }
    var activeReaderClip by remember { mutableStateOf<DaylightClip?>(null) }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }

    // Handle Android system back gesture / button
    androidx.activity.compose.BackHandler(enabled = activeReaderClip != null) {
        activeReaderClip = null
    }

    androidx.activity.compose.BackHandler(enabled = activeReaderClip == null && searchQuery.isNotEmpty()) {
        searchQuery = ""
    }

    // Refresh clips helper
    fun refreshClips() {
        scope.launch(Dispatchers.IO) {
            val list = db.getClips(filterType = selectedFilter, searchQuery = searchQuery)
            withContext(Dispatchers.Main) {
                clips = list
            }
        }
    }

    LaunchedEffect(selectedFilter, searchQuery) {
        refreshClips()
    }

    // Auto-dismiss confirmation toast
    LaunchedEffect(confirmationMessage) {
        if (confirmationMessage != null) {
            delay(2200)
            confirmationMessage = null
        }
    }

    Scaffold(
        containerColor = DaylightColors.PaperBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp
                    )
            ) {
                // Header Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "DAYLIGHT PASTE",
                            fontFamily = DaylightFontFamilies.ArizonaMix,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = DaylightColors.InkBlack
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "UNLIMITED CLIPBOARD & MARKDOWN PRESERVATION",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 10.5.sp,
                            color = DaylightColors.TextMuted,
                            letterSpacing = 1.6.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                db.clearHistory(keepPinned = true)
                                refreshClips()
                                withContext(Dispatchers.Main) {
                                    confirmationMessage = "Cleared unpinned history"
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear unpinned history",
                            tint = DaylightColors.InkBlack,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Search Bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Pinboard Tabs
                PinboardTabs(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Clips Stream
                if (clips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No clippings match '$searchQuery'" else "Clipboard history is empty",
                                fontFamily = DaylightFontFamilies.ArizonaMix,
                                fontSize = 18.sp,
                                color = DaylightColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Copy any AI response, markdown, or code to begin",
                                fontFamily = DaylightFontFamilies.ArizonaSans,
                                fontSize = 14.sp,
                                color = DaylightColors.TextHint
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(clips, key = { it.id }) { clip ->
                            ClipCard(
                                clip = clip,
                                onCopyMarkdown = {
                                    DaylightPasteManager.copyAsMarkdown(context, clip)
                                    confirmationMessage = "Copied Clean Markdown to Clipboard"
                                },
                                onCopyPlain = {
                                    DaylightPasteManager.copyAsPlainText(context, clip)
                                    confirmationMessage = "Copied Plain Text to Clipboard"
                                },
                                onCopyImage = {
                                    DaylightPasteManager.copyImageToClipboard(context, clip)
                                    confirmationMessage = "Copied Image to Clipboard"
                                },
                                onTogglePin = {
                                    scope.launch(Dispatchers.IO) {
                                        db.togglePin(clip.id, !clip.isPinned)
                                        refreshClips()
                                    }
                                },
                                onDelete = {
                                    scope.launch(Dispatchers.IO) {
                                        db.deleteClip(clip.id)
                                        refreshClips()
                                    }
                                },
                                onOpenReader = {
                                    activeReaderClip = clip
                                }
                            )
                        }
                    }
                }
            }

            // Amber Confirmation Pill Toast
            AnimatedVisibility(
                visible = confirmationMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding() + 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(DaylightColors.Amber595nm)
                        .border(1.5.dp, DaylightColors.AmberDeep, RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = confirmationMessage ?: "",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Reader / Image Preview Modal
            activeReaderClip?.let { clip ->
                if (clip.isImage) {
                    ImagePreviewDialog(
                        clip = clip,
                        onDismiss = { activeReaderClip = null },
                        onCopyImage = {
                            DaylightPasteManager.copyImageToClipboard(context, clip)
                            confirmationMessage = "Copied Image to Clipboard"
                        }
                    )
                } else {
                    ReaderDialog(
                        clip = clip,
                        onDismiss = { activeReaderClip = null },
                        onCopyMarkdown = {
                            DaylightPasteManager.copyAsMarkdown(context, clip)
                            confirmationMessage = "Copied Clean Markdown to Clipboard"
                        },
                        onCopyPlain = {
                            DaylightPasteManager.copyAsPlainText(context, clip)
                            confirmationMessage = "Copied Plain Text to Clipboard"
                        }
                    )
                }
            }
        }
    }
}
