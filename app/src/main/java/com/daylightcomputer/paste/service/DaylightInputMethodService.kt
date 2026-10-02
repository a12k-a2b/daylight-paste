package com.daylightcomputer.paste.service

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Daylight Paste IME (Input Method Service).
 * 
 * Provides:
 * 1. Universal Unlimited Paste: Streams multi-megabyte clips into any generic editor
 *    via bounded, surrogate-pair-safe InputConnection.commitText() chunks (8KB).
 * 2. Official AOSP Clipboard Read Access: As an active/enabled IME, Android 13's
 *    ClipboardService natively grants background clipboard read permissions without root.
 */
class DaylightInputMethodService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var database: ClipDatabase
    private var clipsContainer: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        database = ClipDatabase.getInstance(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAF8F5"))
            setPadding(dp(12), dp(8), dp(12), dp(12))
            
            // Top border
            val border = View(context).apply {
                setBackgroundColor(Color.parseColor("#111111"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(2)
                )
            }
            addView(border)
        }

        // Header bar with status and keyboard switcher
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(8))

            val title = TextView(context).apply {
                text = "DAYLIGHT PASTE · UNLIMITED STREAMING"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#111111"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(title)

            // Switch back to normal keyboard button
            val switchBtn = Button(context).apply {
                text = "⌨ Switch Keyboard"
                textSize = 11f
                setTextColor(Color.parseColor("#FAF8F5"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#111111"))
                    cornerRadius = dp(6).toFloat()
                }
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        switchToNextInputMethod(false)
                    }
                }
            }
            addView(switchBtn)
        }
        root.addView(header)

        // Horizontal scroll container for recent clips
        val scrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        clipsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        scrollView.addView(clipsContainer)
        root.addView(scrollView)

        loadRecentClips()

        return root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        loadRecentClips()
    }

    private fun loadRecentClips() {
        val container = clipsContainer ?: return
        serviceScope.launch {
            val clips = withContext(Dispatchers.IO) {
                database.getClips(limit = 15)
            }
            container.removeAllViews()
            if (clips.isEmpty()) {
                val emptyTv = TextView(this@DaylightInputMethodService).apply {
                    text = "No clipboard history yet."
                    setTextColor(Color.parseColor("#777777"))
                    textSize = 13f
                    setPadding(dp(8), dp(16), dp(8), dp(16))
                }
                container.addView(emptyTv)
                return@launch
            }

            for (clip in clips) {
                val card = createClipCardView(clip)
                container.addView(card)
            }
        }
    }

    private fun createClipCardView(clip: DaylightClip): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EAE5DC"))
                setStroke(dp(1), Color.parseColor("#CDC6B8"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val lp = LinearLayout.LayoutParams(dp(220), dp(100)).apply {
                marginEnd = dp(8)
            }
            layoutParams = lp

            val meta = TextView(context).apply {
                text = "${clip.clipType.name} · ${clip.wordCount} words"
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#D97706")) // SolOS Amber
            }
            addView(meta)

            val titleView = TextView(context).apply {
                text = clip.title.ifBlank { "Untitled Clip" }
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#111111"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, dp(4))
            }
            addView(titleView)

            val snippet = TextView(context).apply {
                val raw = clip.textContent.take(120).replace("\n", " ")
                text = raw
                textSize = 11f
                setTextColor(Color.parseColor("#333333"))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(snippet)

            setOnClickListener {
                streamClipToEditor(clip)
            }
        }
        return card
    }

    /**
     * Streams clip content into the active editor via bounded InputConnection chunks.
     * Prevents Binder buffer exhaustion and UI freezes on 100k+ word payloads.
     */
    private fun streamClipToEditor(clip: DaylightClip) {
        val ic = currentInputConnection ?: return
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                // Fetch full text from database
                val fullClip = database.getClipById(clip.id) ?: clip
                val text = if (fullClip.markdownContent.isNotBlank()) {
                    fullClip.markdownContent
                } else {
                    fullClip.textContent
                }

                streamTextInChunks(ic, text)
            }
        }
    }

    companion object {
        private const val CHUNK_SIZE = 8192 // 8KB code-point-safe chunks

        /**
         * Streams arbitrary-length text into an InputConnection without splitting surrogate pairs.
         */
        fun streamTextInChunks(ic: InputConnection, text: String, chunkSize: Int = CHUNK_SIZE) {
            var offset = 0
            val len = text.length
            while (offset < len) {
                var nextEnd = (offset + chunkSize).coerceAtMost(len)
                // If the boundary falls between a surrogate pair, adjust backwards
                if (nextEnd < len && Character.isHighSurrogate(text[nextEnd - 1])) {
                    nextEnd--
                }
                val chunk = text.substring(offset, nextEnd)
                ic.commitText(chunk, 1)
                offset = nextEnd
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
