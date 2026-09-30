package com.daylightcomputer.paste.ui

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies
import java.net.URLEncoder

/**
 * Flinx-Style Floating Link Bubble & Non-Blocking Search for SolOS.
 * 
 * Replaces the jarring full-screen Chrome task switch with a lightweight,
 * zero-elevation LivePaper floating card that hovers directly over the active reading screen.
 * Dismissible with a single tap outside or '✕'.
 */
class DaylightSearchBubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract query from WEB_SEARCH or PROCESS_TEXT
        val query = extractQuery(intent)
        if (query.isBlank()) {
            finish()
            return
        }

        val searchUrl = "https://html.duckduckgo.com/html/?q=${URLEncoder.encode(query, "UTF-8")}"

        setContent {
            Dialog(
                onDismissRequest = { finish() },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                // Dimmed ambient backdrop (tapping outside closes)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DaylightColors.InkBlack.copy(alpha = 0.35f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    // Floating LivePaper stationery search card
                    Surface(
                        modifier = Modifier
                            .width(580.dp)
                            .fillMaxHeight(0.82f)
                            .clickable(enabled = false) {}
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, DaylightColors.InkBlack, RoundedCornerShape(8.dp)),
                        color = DaylightColors.PaperBg,
                        shadowElevation = 0.dp // Strict zero-elevation on LivePaper
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DaylightColors.SurfaceCream)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SEARCH BUBBLE",
                                        fontFamily = DaylightFontFamilies.RomExtendedLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DaylightColors.InkSubtle,
                                        letterSpacing = 1.2.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = query,
                                        fontFamily = DaylightFontFamilies.ArizonaMix,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DaylightColors.InkBlack,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = DaylightColors.InkBlack
                                    )
                                }
                            }

                            Divider(color = DaylightColors.BorderStrong, thickness = 1.dp)

                            // Embedded WebView with high-contrast e-paper search
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(DaylightColors.PaperBg),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            cacheMode = WebSettings.LOAD_DEFAULT
                                            useWideViewPort = true
                                            loadWithOverviewMode = true
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                // Inject CSS for LivePaper high-contrast (#111111 ink on #FFFFFF)
                                                view?.evaluateJavascript(
                                                    """
                                                    document.body.style.backgroundColor = '#FAF8F5';
                                                    document.body.style.color = '#111111';
                                                    """.trimIndent(),
                                                    null
                                                )
                                            }
                                        }
                                        loadUrl(searchUrl)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun extractQuery(intent: Intent): String {
        return intent.getStringExtra(SearchManager.QUERY)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getStringExtra("query")
            ?: ""
    }
}
