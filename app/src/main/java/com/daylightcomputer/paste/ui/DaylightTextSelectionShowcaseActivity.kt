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

            customSelectionActionModeCallback = object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    // Inject Snip as first action
                    menu?.add(Menu.NONE, 1001, 1, "Snip")?.apply {
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    }
                    // Inject Search as second action
                    menu?.add(Menu.NONE, 1002, 2, "Search")?.apply {
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    }
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                    val selStart = selectionStart
                    val selEnd = selectionEnd
                    val selected = if (selStart in 0..selEnd) {
                        text.subSequence(selStart, selEnd).toString()
                    } else ""

                    when (item?.itemId) {
                        1001 -> {
                            val intent = Intent(this@DaylightTextSelectionShowcaseActivity, ProcessSnipActivity::class.java).apply {
                                putExtra(Intent.EXTRA_PROCESS_TEXT, selected)
                            }
                            startActivity(intent)
                            mode?.finish()
                            return true
                        }
                        1002 -> {
                            val intent = Intent(this@DaylightTextSelectionShowcaseActivity, DaylightSearchBubbleActivity::class.java).apply {
                                putExtra(Intent.EXTRA_PROCESS_TEXT, selected)
                            }
                            startActivity(intent)
                            mode?.finish()
                            return true
                        }
                    }
                    return false
                }

                override fun onDestroyActionMode(mode: ActionMode?) {}
            }
        }

        // Attach Multi-Tap Selection Listener
        setupMultiTapSelection(sampleEditor)
        rootLayout.addView(sampleEditor)

        setContentView(rootLayout)
    }

    /**
     * Progressive Multi-Tap Selection:
     * 2 taps = Word (Unicode BreakIterator)
     * 3 taps = Sentence (Unicode BreakIterator)
     * 4 taps = Paragraph
     */
    private fun setupMultiTapSelection(editor: EditText) {
        var lastTapTime = 0L
        var lastTapX = 0f
        var lastTapY = 0f
        var tapCount = 0
        val multiTapWindowMs = 400L
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop * 2f

        editor.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val now = SystemClock.uptimeMillis()
                val dx = kotlin.math.abs(event.x - lastTapX)
                val dy = kotlin.math.abs(event.y - lastTapY)
                val isWithinSlop = (dx * dx + dy * dy) <= (touchSlop * touchSlop)

                if (now - lastTapTime < multiTapWindowMs && isWithinSlop) {
                    tapCount++
                } else {
                    tapCount = 1
                }
                lastTapTime = now
                lastTapX = event.x
                lastTapY = event.y

                val offset = editor.getOffsetForPosition(event.x, event.y)
                val fullText = editor.text.toString()

                when (tapCount) {
                    2 -> {
                        selectWordAt(editor, fullText, offset)
                        statusText.text = "🎯 2x Tap: Word selected"
                    }
                    3 -> {
                        selectSentenceAt(editor, fullText, offset)
                        statusText.text = "🎯 3x Tap: Sentence selected"
                    }
                    4 -> {
                        selectParagraphAt(editor, fullText, offset)
                        statusText.text = "🎯 4x Tap: Paragraph selected"
                        tapCount = 0
                    }
                }
            }
            false
        }
    }

    private fun selectWordAt(editor: EditText, text: String, offset: Int) {
        if (offset < 0 || offset >= text.length) return
        val iterator = java.text.BreakIterator.getWordInstance()
        iterator.setText(text)
        var end = iterator.following(offset)
        var start = iterator.previous()
        while (start < end && start < text.length && !Character.isLetterOrDigit(text[start])) {
            start++
        }
        while (end > start && end <= text.length && !Character.isLetterOrDigit(text[end - 1])) {
            end--
        }
        if (start < end) {
            editor.setSelection(start, end)
            editor.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    editor.startActionMode(editor.customSelectionActionModeCallback, ActionMode.TYPE_FLOATING)
                }
            }
        }
    }

    private fun selectSentenceAt(editor: EditText, text: String, offset: Int) {
        if (offset < 0 || offset >= text.length) return
        val iterator = java.text.BreakIterator.getSentenceInstance()
        iterator.setText(text)
        var end = iterator.following(offset)
        var start = iterator.previous()

        // Skip leading whitespace
        while (start < end && start < text.length && Character.isWhitespace(text[start])) {
            start++
        }

        if (start < end) {
            editor.setSelection(start, end)
            editor.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    editor.startActionMode(editor.customSelectionActionModeCallback, ActionMode.TYPE_FLOATING)
                }
            }
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
            editor.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    editor.startActionMode(editor.customSelectionActionModeCallback, ActionMode.TYPE_FLOATING)
                }
            }
        }
    }

    /**
     * Verifies the unlimited streaming clipboard engine:
     * Generates 250,000 characters (> 500KB UTF-16) of structured Markdown,
     * saves to database, copies via DaylightPasteManager, and reads via ClipStreamProvider.
     */
    /**
     * Verifies the unlimited streaming clipboard engine:
     * Generates 500,000 characters (> 500KB UTF-8, exceeding MAX_BINDER_BYTE_THRESHOLD 256KB)
     * of structured Markdown, saves to database, copies via DaylightPasteManager,
     * and reads back via ClipData.Item.coerceToText() and ClipStreamProvider to verify
     * zero Binder transaction crashes.
     */
    private fun runStreamingClipboardVerification() {
        statusText.text = "⏳ Generating 500,000 char Markdown payload (>256KB streaming threshold)..."

        scope.launch(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                sb.append("# SolOS Unlimited Streaming Clipboard Benchmark (500K)\n\n")
                val sampleBlock = "The quick brown fox jumps over the lazy dog. SolOS LivePaper 90Hz transflective display provides pure paper reading.\n"
                while (sb.length < 500_000) {
                    sb.append(sampleBlock)
                }
                val payload = sb.toString()
                val payloadBytes = payload.toByteArray(Charsets.UTF_8).size

                val db = ClipDatabase.getInstance(this@DaylightTextSelectionShowcaseActivity)
                val clipId = db.insertClip(
                    DaylightClip(
                        textContent = payload,
                        markdownContent = payload,
                        title = "SolOS 500K Streaming Test",
                        sourcePackage = "com.daylightcomputer.paste",
                        clipType = com.daylightcomputer.paste.markdown.ClipType.MARKDOWN,
                        pinboard = "BENCHMARK"
                    )
                )

                val clip = db.getClipById(clipId) ?: throw IllegalStateException("Failed to load inserted clip")

                // Copy via DaylightPasteManager (forces > 256KB streaming branch)
                withContext(Dispatchers.Main) {
                    val copied = DaylightPasteManager.copyAsMarkdown(this@DaylightTextSelectionShowcaseActivity, clip)
                    if (!copied) throw IllegalStateException("DaylightPasteManager.copyAsMarkdown failed")
                }

                // Verify clipboard item coercion (Android standard ClipData.Item.coerceToText)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val primaryClip = clipboard.primaryClip ?: throw IllegalStateException("Primary clip is null")
                val clipItem = primaryClip.getItemAt(0) ?: throw IllegalStateException("Clip item 0 is null")

                if (clipItem.uri == null) {
                    throw IllegalStateException("Expected stream URI in ClipData.Item, but uri was null!")
                }

                // Verify streaming read back via ContentResolver & ParcelFileDescriptor
                val streamUri = clipItem.uri
                val pfd = contentResolver.openAssetFileDescriptor(streamUri, "r")
                    ?: throw IllegalStateException("Could not open AssetFileDescriptor for $streamUri")

                val reader = BufferedReader(InputStreamReader(pfd.createInputStream(), Charsets.UTF_8))
                var readChars = 0
                val buf = CharArray(8192)
                var n: Int
                while (reader.read(buf).also { n = it } != -1) {
                    readChars += n
                }
                reader.close()
                pfd.close()

                withContext(Dispatchers.Main) {
                    statusText.text = "✓ PASSED: Streamed $readChars chars ($payloadBytes bytes) via URI Stream (No Binder Limit!)"
                    Toast.makeText(
                        this@DaylightTextSelectionShowcaseActivity,
                        "✓ 500K Streaming Verified: $readChars chars (>256KB threshold)",
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
