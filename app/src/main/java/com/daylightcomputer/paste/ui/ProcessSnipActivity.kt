package com.daylightcomputer.paste.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles "Snip" directly from Android's floating text selection toolbar (tooltip menu).
 * 
 * Captures selected text, transpiles formatting to clean Markdown, stores in the
 * universal ThingsPile stash, dispatches to Daylight Paper's Commonplace Book,
 * and displays an ambient 595nm amber toast confirmation before finishing immediately.
 */
class ProcessSnipActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim() ?: ""
        if (selectedText.isNotBlank()) {
            val database = ClipDatabase.getInstance(applicationContext)
            val clipType = MarkdownTranspiler.detectClipType(selectedText)
            val markdownContent = if (clipType == ClipType.MARKDOWN || clipType == ClipType.CODE) {
                selectedText
            } else {
                MarkdownTranspiler.transpileHtmlToMarkdown(selectedText).ifBlank { selectedText }
            }
            val title = MarkdownTranspiler.extractTitle(markdownContent)
            val charCount = markdownContent.length
            val wordCount = markdownContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            val sourceApp = callingPackage ?: "SolOS Reader"

            val clip = DaylightClip(
                textContent = selectedText,
                markdownContent = markdownContent,
                title = title,
                clipType = clipType,
                charCount = charCount,
                wordCount = wordCount,
                sourcePackage = sourceApp,
                isPinned = true,
                pinboard = "THINGS_PILE"
            )

            var insertSuccess = false
            try {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    val clipId = database.insertClip(clip)
                    if (clipId > 0) {
                        insertSuccess = true
                        val clipUri = com.daylightcomputer.paste.data.DaylightPasteContentProvider.getClipUri(clipId)

                        // 2. Dispatch to Daylight Paper Commonplace Book via system broadcast with URI grant
                        try {
                            val paperIntent = Intent("com.daylightcomputer.action.ADD_COMMONPLACE_ENTRY").apply {
                                putExtra("extra_clip_id", clipId)
                                putExtra("extra_clip_uri", clipUri.toString())
                                putExtra("extra_title", title)
                                putExtra("extra_source", sourceApp)
                                putExtra("extra_timestamp", System.currentTimeMillis())
                                // Only attach inline text if safely under 64KB Binder threshold
                                if (markdownContent.length < 16_000) {
                                    putExtra("extra_quote", markdownContent)
                                }
                                data = clipUri
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                setPackage("com.daylightcomputer.paper")
                            }
                            grantUriPermission("com.daylightcomputer.paper", clipUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            sendBroadcast(paperIntent)
                        } catch (e: Exception) {
                            android.util.Log.e("ProcessSnip", "Failed to broadcast to Daylight Paper: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProcessSnip", "Snip insertion failed: ${e.message}")
            }

            if (insertSuccess) {
                Toast.makeText(this, "✓ Snipped to ThingsPile · Commonplace Book", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "✕ Failed to save snip", Toast.LENGTH_SHORT).show()
            }
        }

        // Dismiss immediately so user never loses their reading flow
        finish()
    }
}
