package com.daylightcomputer.paste.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.data.DaylightPasteContentProvider
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles "Snip" from Android's floating text selection toolbar (PROCESS_TEXT).
 *
 * 1. Saves the selection to the ThingsPile pinboard inside DaylightPaste (off the main thread).
 * 2. If a Commonplace Book receiver is installed, hands it a read-granted content URI.
 * 3. Shows a toast that reports what actually happened, then finishes.
 *
 * Notes:
 * - PROCESS_TEXT delivers plain text (spans are stripped by the framework) and AOSP trims it to
 *   ~100,000 chars, so Snip does NOT preserve rich formatting and cannot capture unlimited length.
 * - The activity stays alive (invisible, translucent theme) until the insert completes, so the
 *   process is not killable mid-write.
 */
class ProcessSnipActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ProcessSnip"
        const val ACTION_ADD_COMMONPLACE_ENTRY = "com.daylightcomputer.action.ADD_COMMONPLACE_ENTRY"
        const val PAPER_PACKAGE = "com.daylightcomputer.paper"
        private const val INLINE_QUOTE_LIMIT_CHARS = 16_000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (selectedText.isBlank()) {
            finish()
            return
        }
        val sourceApp = callingPackage ?: referrer?.host ?: "unknown"

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { saveSnip(selectedText, sourceApp) }
            val message = when {
                result == null -> "✕ Snip failed — nothing was saved"
                result.sentToPaper -> "✓ Snipped to ThingsPile · sent to Commonplace Book"
                else -> "✓ Snipped to ThingsPile"
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private data class SnipResult(val clipId: Long, val sentToPaper: Boolean)

    private fun saveSnip(selectedText: String, sourceApp: String): SnipResult? {
        return try {
            val clipType = MarkdownTranspiler.detectClipType(selectedText)
            val markdownContent = if (clipType == ClipType.MARKDOWN || clipType == ClipType.CODE) {
                selectedText
            } else {
                MarkdownTranspiler.transpileHtmlToMarkdown(selectedText).ifBlank { selectedText }
            }
            val title = MarkdownTranspiler.extractTitle(markdownContent)
            val clip = DaylightClip(
                textContent = selectedText,
                markdownContent = markdownContent,
                title = title,
                clipType = clipType,
                charCount = markdownContent.length,
                wordCount = markdownContent.split(Regex("\\s+")).count { it.isNotBlank() },
                sourcePackage = sourceApp,
                isPinned = true,
                pinboard = "THINGS_PILE"
            )

            val clipId = ClipDatabase.getInstance(applicationContext).insertClip(clip)
            if (clipId <= 0) return null

            SnipResult(clipId, sendToCommonplaceBook(clipId, title, sourceApp, markdownContent))
        } catch (e: Exception) {
            Log.e(TAG, "Snip insertion failed", e)
            null
        }
    }

    /** Returns true only if a receiver actually exists to accept the entry. */
    private fun sendToCommonplaceBook(clipId: Long, title: String, sourceApp: String, markdown: String): Boolean {
        val probe = Intent(ACTION_ADD_COMMONPLACE_ENTRY).setPackage(PAPER_PACKAGE)
        if (packageManager.queryBroadcastReceivers(probe, 0).isEmpty()) {
            Log.i(TAG, "No Commonplace Book receiver installed; saved to ThingsPile only")
            return false
        }
        return try {
            val clipUri = DaylightPasteContentProvider.getClipUri(clipId)
            grantUriPermission(PAPER_PACKAGE, clipUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val paperIntent = Intent(ACTION_ADD_COMMONPLACE_ENTRY).apply {
                setPackage(PAPER_PACKAGE)
                data = clipUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra("extra_clip_id", clipId)
                putExtra("extra_clip_uri", clipUri.toString())
                putExtra("extra_title", title)
                putExtra("extra_source", sourceApp)
                putExtra("extra_timestamp", System.currentTimeMillis())
                if (markdown.length < INLINE_QUOTE_LIMIT_CHARS) putExtra("extra_quote", markdown)
            }
            sendBroadcast(paperIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Commonplace Book broadcast failed", e)
            false
        }
    }
}
