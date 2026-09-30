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

            CoroutineScope(Dispatchers.IO).launch {
                // 1. Save to ThingsPile / DaylightPaste Database
                database.insertClip(clip)

                // 2. Dispatch to Daylight Paper Commonplace Book via system broadcast
                try {
                    val paperIntent = Intent("com.daylightcomputer.action.ADD_COMMONPLACE_ENTRY").apply {
                        putExtra("extra_quote", markdownContent)
                        putExtra("extra_title", title)
                        putExtra("extra_source", sourceApp)
                        putExtra("extra_timestamp", System.currentTimeMillis())
                        setPackage("com.daylightcomputer.paper")
                    }
                    sendBroadcast(paperIntent)
                } catch (ignored: Exception) {}
            }

            // 3. Ambient 595nm amber toast confirmation
            Toast.makeText(this, "✓ Snipped to ThingsPile · Commonplace Book", Toast.LENGTH_SHORT).show()
        }

        // 4. Dismiss immediately so user never loses their reading flow
        finish()
    }
}
