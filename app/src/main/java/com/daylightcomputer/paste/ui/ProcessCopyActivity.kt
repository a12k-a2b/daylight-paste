package com.daylightcomputer.paste.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import com.daylightcomputer.paste.service.DaylightPasteManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles "Daylight Copy" directly from Android's floating text selection toolbar (tooltip menu).
 * Immediately captures selected text, transpiles formatting to Markdown, indexes into SQLite,
 * and sets the system clipboard with clean Markdown.
 */
class ProcessCopyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        if (selectedText.isNotBlank()) {
            val database = ClipDatabase.getInstance(applicationContext)
            val clipType = MarkdownTranspiler.detectClipType(selectedText)
            val markdownContent = if (clipType == ClipType.MARKDOWN || clipType == ClipType.CODE) {
                selectedText
            } else {
                MarkdownTranspiler.transpileHtmlToMarkdown(selectedText)
            }
            val title = MarkdownTranspiler.extractTitle(markdownContent)
            val charCount = markdownContent.length
            val wordCount = markdownContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size

            val clip = DaylightClip(
                textContent = selectedText,
                markdownContent = markdownContent,
                title = title,
                clipType = clipType,
                charCount = charCount,
                wordCount = wordCount,
                sourcePackage = callingPackage ?: "Tooltip Menu"
            )

            CoroutineScope(Dispatchers.IO).launch {
                database.insertClip(clip)
            }

            DaylightPasteManager.copyAsMarkdown(this, clip)
            Toast.makeText(this, "✓ Captured Clean Markdown to Daylight Paste", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
