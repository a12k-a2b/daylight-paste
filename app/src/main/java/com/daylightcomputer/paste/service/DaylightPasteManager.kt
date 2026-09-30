package com.daylightcomputer.paste.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DaylightPasteManager {

    private const val MAX_BINDER_SAFE_CHARS = 400_000 // ~800KB UTF-16, safe under 1MB Binder ceiling

    @Volatile
    private var lastInternalCopiedText: String? = null
    @Volatile
    private var lastInternalCopiedTime: Long = 0L

    fun markInternalCopy(text: String) {
        lastInternalCopiedText = text
        lastInternalCopiedTime = System.currentTimeMillis()
    }

    fun isRecentInternalCopy(text: String): Boolean {
        val copied = lastInternalCopiedText ?: return false
        val elapsed = System.currentTimeMillis() - lastInternalCopiedTime
        return elapsed < 3000L && (copied == text || text.startsWith(copied))
    }

    /**
     * Copies clean Markdown to system clipboard.
     * Guaranteed to preserve Markdown formatting in Day One, Obsidian, Noteshelf, etc.
     */
    fun copyAsMarkdown(context: Context, clip: DaylightClip): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val textToCopy = clip.markdownContent
            
            // Safe parcel sizing to prevent TransactionTooLargeException
            val safeText = if (textToCopy.length > MAX_BINDER_SAFE_CHARS) {
                textToCopy.take(MAX_BINDER_SAFE_CHARS)
            } else {
                textToCopy
            }

            markInternalCopy(safeText)

            // When copying as Markdown, we must put the clean CommonMark text as plain text
            // so rich editors (Day One, Obsidian, Claude, etc.) do NOT paste the original messy HTML.
            val clipData = ClipData.newPlainText(clip.title, safeText)

            clipboard.setPrimaryClip(clipData)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Strips all formatting and copies pure flat plain text.
     */
    fun copyAsPlainText(context: Context, clip: DaylightClip): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val raw = MarkdownTranspiler.stripFormatting(clip.markdownContent)
            val safeText = if (raw.length > MAX_BINDER_SAFE_CHARS) raw.take(MAX_BINDER_SAFE_CHARS) else raw
            markInternalCopy(safeText)
            val clipData = ClipData.newPlainText(clip.title, safeText)
            clipboard.setPrimaryClip(clipData)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Automatically injects paste (KEYCODE_PASTE / Ctrl+V) into active focused app window.
     */
    suspend fun injectPasteAction(context: Context, clip: DaylightClip, asMarkdown: Boolean = true) {
        withContext(Dispatchers.IO) {
            if (asMarkdown) {
                copyAsMarkdown(context, clip)
            } else {
                copyAsPlainText(context, clip)
            }
            try {
                // Execute keyevent paste via shell/root for zero-friction paste into Day One / notes
                Runtime.getRuntime().exec(arrayOf("input", "keyevent", "279")).waitFor()
            } catch (ignored: Exception) {}
        }
    }
}
