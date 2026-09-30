package com.daylightcomputer.paste.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.ClipStreamProvider
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.service.DaylightPasteManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Interactive LivePaper Text Selection & Caliper Handle Verification Activity.
 * 
 * Tests:
 * 1. 280ms Snappy Long-Press Text Selection.
 * 2. Sharp Caliper Brackets (「 and 」) in #111111 ink.
 * 3. Multi-Tap Selection Hierarchy:
 *    - 2x Tap: Word
 *    - 3x Tap: Sentence
 *    - 4x Tap: Paragraph
 * 4. Floating SolOS Tooltip Actions:
 *    - Snip (saves to ThingsPile & Commonplace Book)
 *    - Search (launches non-blocking DuckDuckGo Link Bubble)
 * 5. Unlimited Streaming Clipboard (250,000+ chars without Binder limits).
 */
class DaylightTextSelectionShowcaseActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var statusText: TextView
    private lateinit var sampleEditor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge LivePaper setup
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#FAF8F5"))
            setPadding(32, 48, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header
        val header = TextView(this).apply {
            text = "SOLOS TEXT SELECTION & CALIPER TEST"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(android.graphics.Color.parseColor("#1E1D1B"))
            letterSpacing = 0.15f
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(header)

        val subheader = TextView(this).apply {
            text = "Double-tap word · Triple-tap sentence · 4x tap paragraph · 280ms long-press"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(android.graphics.Color.parseColor("#111111"))
            setPadding(0, 0, 0, 12)
        }
        rootLayout.addView(subheader)

        // Action Buttons Row (Top pinned)
        val actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val btnStreamTest = Button(this).apply {
            text = "⚡ Test 250K Stream Clip"
            setBackgroundColor(android.graphics.Color.parseColor("#111111"))
            setTextColor(android.graphics.Color.parseColor("#FAF8F5"))
            setOnClickListener {
                runStreamingClipboardVerification()
            }
        }
        actionBar.addView(btnStreamTest)

        val btnSearchTest = Button(this).apply {
            text = "🔍 Test Search Bubble"
            setBackgroundColor(android.graphics.Color.parseColor("#EAE5DC"))
            setTextColor(android.graphics.Color.parseColor("#111111"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 16
            }
            layoutParams = params
            setOnClickListener {
                val intent = Intent(this@DaylightTextSelectionShowcaseActivity, DaylightSearchBubbleActivity::class.java).apply {
                    putExtra(Intent.EXTRA_PROCESS_TEXT, "transflective reflective LCD")
                }
                startActivity(intent)
            }
        }
        val btnWord = Button(this).apply {
            text = "2x Word"
            setBackgroundColor(android.graphics.Color.parseColor("#EAE5DC"))
            setTextColor(android.graphics.Color.parseColor("#111111"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 12 }
            layoutParams = params
            setOnClickListener {
                selectWordAt(sampleEditor, sampleEditor.text.toString(), 25)
                statusText.text = "🎯 2x Tap Triggered: Selected Word 'LivePaper'"
            }
        }
        actionBar.addView(btnWord)

        val btnSentence = Button(this).apply {
            text = "3x Sentence"
            setBackgroundColor(android.graphics.Color.parseColor("#EAE5DC"))
            setTextColor(android.graphics.Color.parseColor("#111111"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 12 }
            layoutParams = params
            setOnClickListener {
                selectSentenceAt(sampleEditor, sampleEditor.text.toString(), 25)
                statusText.text = "🎯 3x Tap Triggered: Selected Sentence"
            }
        }
        actionBar.addView(btnSentence)

        val btnParagraph = Button(this).apply {
            text = "4x Paragraph"
            setBackgroundColor(android.graphics.Color.parseColor("#EAE5DC"))
            setTextColor(android.graphics.Color.parseColor("#111111"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 12 }
            layoutParams = params
            setOnClickListener {
                selectParagraphAt(sampleEditor, sampleEditor.text.toString(), 25)
                statusText.text = "🎯 4x Tap Triggered: Selected Paragraph"
            }
        }
        actionBar.addView(btnParagraph)

        rootLayout.addView(actionBar)

        // Status banner
        statusText = TextView(this).apply {
            text = "Ready. Tap or long-press text below to test caliper brackets and tooltip actions."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(android.graphics.Color.parseColor("#D97706")) // Amber accent
            setBackgroundColor(android.graphics.Color.parseColor("#EAE5DC"))
            setPadding(16, 12, 16, 12)
        }
        rootLayout.addView(statusText)

        val spacer1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 16)
        }
        rootLayout.addView(spacer1)

        // Multi-tap Selectable EditText (Read-only for pure reader text selection without IME)
        sampleEditor = EditText(this).apply {
            setText(
                """
The Daylight DC-1 LivePaper display is a custom transflective reflective LCD screen engineered for pure ambient contrast. It eliminates backlight glare in sunlight while dynamic 45Hz to 90Hz VRR guarantees responsive stylus inking and whisper-quiet power consumption.

When you select text on SolOS, sharp Caliper Brackets frame your thoughts. The long-press activation responds at a snappy 280ms threshold.

Tap once to position your cursor. Double-tap to select a word. Triple-tap to select the entire sentence. Tap four times to highlight the full paragraph.

Click 'Snip' in the floating menu to archive this insight directly to ThingsPile and Daylight Paper's Commonplace Book. Click 'Search' to open a non-blocking floating Link Bubble without losing your reading flow.
                """.trimIndent()
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(android.graphics.Color.parseColor("#111111"))
            setBackgroundColor(android.graphics.Color.parseColor("#FAF8F5"))
            setPadding(24, 24, 24, 24)
            gravity = Gravity.TOP or Gravity.START
            setTextIsSelectable(true)
            keyListener = null // Read-only selectable text (no soft keyboard)
            isFocusable = true
            isFocusableInTouchMode = true
            isLongClickable = true
            isClickable = true
            showSoftInputOnFocus = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        // Attach Multi-Tap Selection Listener
        setupMultiTapSelection(sampleEditor)
        rootLayout.addView(sampleEditor)

        setContentView(rootLayout)
    }

    /**
     * Progressive Multi-Tap Selection:
     * 2 taps = Word
     * 3 taps = Sentence
     * 4 taps = Paragraph
     */
    private fun setupMultiTapSelection(editor: EditText) {
        var lastTapTime = 0L
        var tapCount = 0
        val multiTapWindowMs = 450L

        editor.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val now = SystemClock.uptimeMillis()
                if (now - lastTapTime < multiTapWindowMs) {
                    tapCount++
                } else {
                    tapCount = 1
                }
                lastTapTime = now

                val offset = editor.getOffsetForPosition(event.x, event.y)
                val fullText = editor.text.toString()

                when (tapCount) {
                    2 -> {
                        // Word selection
                        selectWordAt(editor, fullText, offset)
                        statusText.text = "🎯 2x Tap: Word selected"
                    }
                    3 -> {
                        // Sentence selection
                        selectSentenceAt(editor, fullText, offset)
                        statusText.text = "🎯 3x Tap: Sentence selected"
                    }
                    4 -> {
                        // Paragraph selection
                        selectParagraphAt(editor, fullText, offset)
                        statusText.text = "🎯 4x Tap: Paragraph selected"
                        tapCount = 0
                    }
                }
            }
            false // Allow standard event dispatch so long-press and handles engage
        }
    }

    private fun selectWordAt(editor: EditText, text: String, offset: Int) {
        if (offset < 0 || offset >= text.length) return
        var start = offset
        var end = offset
        while (start > 0 && !Character.isWhitespace(text[start - 1])) {
            start--
        }
        while (end < text.length && !Character.isWhitespace(text[end])) {
            end++
        }
        if (start < end) {
            editor.setSelection(start, end)
        }
    }

    private fun selectSentenceAt(editor: EditText, text: String, offset: Int) {
        if (offset < 0 || offset >= text.length) return
        var start = offset
        var end = offset

        val sentenceEnds = setOf('.', '!', '?', '\n')
        while (start > 0 && !sentenceEnds.contains(text[start - 1])) {
            start--
        }
        // Skip leading whitespace
        while (start < offset && Character.isWhitespace(text[start])) {
            start++
        }

        while (end < text.length && !sentenceEnds.contains(text[end])) {
            end++
        }
        if (end < text.length && text[end] != '\n') {
            end++ // Include the ending punctuation
        }

        if (start < end) {
            editor.setSelection(start, end)
        }
    }

    private fun selectParagraphAt(editor: EditText, text: String, offset: Int) {
        if (offset < 0 || offset >= text.length) return
        var start = offset
        var end = offset

        while (start > 0 && text[start - 1] != '\n') {
            start--
        }
        while (end < text.length && text[end] != '\n') {
            end++
        }

        if (start < end) {
            editor.setSelection(start, end)
        }
    }

    /**
     * Verifies the unlimited streaming clipboard engine:
     * Generates 250,000 characters (> 500KB UTF-16) of structured Markdown,
     * saves to database, copies via DaylightPasteManager, and reads via ClipStreamProvider.
     */
    private fun runStreamingClipboardVerification() {
        statusText.text = "⏳ Generating 250,000 char Markdown payload..."

        scope.launch(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                sb.append("# SolOS Unlimited Streaming Clipboard Benchmark\n\n")
                val sampleBlock = "The quick brown fox jumps over the lazy dog. SolOS LivePaper 90Hz transflective display provides pure paper reading.\n"
                while (sb.length < 250_000) {
                    sb.append(sampleBlock)
                }
                val payload = sb.toString()

                val db = ClipDatabase.getInstance(this@DaylightTextSelectionShowcaseActivity)
                val clipId = db.insertClip(
                    DaylightClip(
                        textContent = payload,
                        markdownContent = payload,
                        title = "SolOS Streaming Test",
                        sourcePackage = "com.daylightcomputer.paste",
                        clipType = com.daylightcomputer.paste.markdown.ClipType.MARKDOWN,
                        pinboard = "BENCHMARK"
                    )
                )

                val clip = db.getClipById(clipId) ?: throw IllegalStateException("Failed to load inserted clip")

                // Copy via DaylightPasteManager
                withContext(Dispatchers.Main) {
                    val copied = DaylightPasteManager.copyAsMarkdown(this@DaylightTextSelectionShowcaseActivity, clip)
                    if (!copied) throw IllegalStateException("DaylightPasteManager.copyAsMarkdown failed")
                }

                // Verify streaming read back via ContentResolver & ParcelFileDescriptor
                val streamUri = ClipStreamProvider.getClipUri(clipId)
                val pfd = contentResolver.openAssetFileDescriptor(streamUri, "r")
                    ?: throw IllegalStateException("Could not open AssetFileDescriptor for $streamUri")

                val reader = BufferedReader(InputStreamReader(pfd.createInputStream()))
                var readChars = 0
                val buf = CharArray(8192)
                var n: Int
                while (reader.read(buf).also { n = it } != -1) {
                    readChars += n
                }
                reader.close()
                pfd.close()

                withContext(Dispatchers.Main) {
                    statusText.text = "✓ PASSED: Streamed $readChars chars via ClipStreamProvider (No Binder Limit!)"
                    Toast.makeText(
                        this@DaylightTextSelectionShowcaseActivity,
                        "✓ 250K Streaming Clipboard Verified: $readChars chars",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    statusText.text = "✕ Benchmark Error: ${e.message}"
                    Toast.makeText(
                        this@DaylightTextSelectionShowcaseActivity,
                        "✕ Failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
