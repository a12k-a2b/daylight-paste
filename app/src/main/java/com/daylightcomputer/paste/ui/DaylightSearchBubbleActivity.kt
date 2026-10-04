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

    private val queryState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val query = extractQuery(intent)
        if (query.isBlank()) {
            finish()
            return
        }
        queryState.value = query
        initContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val query = extractQuery(intent)
        if (query.isNotBlank()) {
            queryState.value = query
        }
    }

    private var webViewRef: WebView? = null

    override fun onDestroy() {
        // Explicitly tear down the WebView so its renderer process and timers stop immediately.
        webViewRef?.apply {
            stopLoading()
            loadUrl("about:blank")
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        webViewRef = null
        super.onDestroy()
    }

    private fun initContent() {
        // Single translucent window: no Dialog, no dim layer, no window animation.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setDimAmount(0f)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setWindowAnimations(0)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            val currentQuery by queryState
            val searchUrl = remember(currentQuery) {
                "https://lite.duckduckgo.com/lite/?q=${URLEncoder.encode(currentQuery, "UTF-8")}"
            }
            androidx.activity.compose.BackHandler {
                val wv = webViewRef
                if (wv != null && wv.canGoBack()) wv.goBack() else finish()
            }
            run {
                // Transparent click-outside backdrop (zero alpha scrim for pure LivePaper sunlight contrast)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    // Floating LivePaper stationery search card (strictly 2dp corners, 0dp elevation)
                    Surface(
                        modifier = Modifier
                            .width(580.dp)
                            .fillMaxHeight(0.82f)
                            .clickable(enabled = false) {}
                            .clip(RoundedCornerShape(2.dp))
                            .border(2.dp, DaylightColors.InkBlack, RoundedCornerShape(2.dp)),
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
                                        text = currentQuery,
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

                            HorizontalDivider(color = DaylightColors.BorderStrong, thickness = 1.dp)

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
                                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                                val targetUrl = request?.url?.toString() ?: return false
                                                // Keep DuckDuckGo Lite search queries and pagination inside the bubble
                                                if (targetUrl.contains("duckduckgo.com/lite") || targetUrl.contains("lite.duckduckgo.com")) {
                                                    return false
                                                }
                                                // External result clicks open in full browser
                                                return try {
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, request.url)
                                                    context.startActivity(browserIntent)
                                                    true
                                                } catch (e: Exception) {
                                                    false
                                                }
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                // Inject CSS for LivePaper high-contrast (#111111 ink on #FAF8F5)
                                                view?.evaluateJavascript(
                                                    """
                                                    document.body.style.backgroundColor = '#FAF8F5';
                                                    document.body.style.color = '#111111';
                                                    """.trimIndent(),
                                                    null
                                                )
                                            }
                                        }
                                        tag = searchUrl
                                        webViewRef = this
                                        loadUrl(searchUrl)
                                    }
                                },
                                update = { webView ->
                                    // Reload only for a new query; leave in-bubble pagination alone.
                                    if (webView.tag != searchUrl) {
                                        webView.tag = searchUrl
                                        webView.loadUrl(searchUrl)
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
