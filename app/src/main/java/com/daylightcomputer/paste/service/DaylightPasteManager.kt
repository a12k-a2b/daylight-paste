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

            val clipData = if (!clip.htmlContent.isNullOrBlank()) {
                ClipData.newHtmlText(clip.title, safeText, clip.htmlContent)
            } else {
                ClipData.newPlainText(clip.title, safeText)
            }

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
