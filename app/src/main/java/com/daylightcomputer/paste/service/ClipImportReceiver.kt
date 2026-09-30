package com.daylightcomputer.paste.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_INSERT_CLIP) {
            val rawText = intent.getStringExtra(EXTRA_TEXT) ?: ""
            val rawHtml = intent.getStringExtra(EXTRA_HTML)
            val sourcePkg = intent.getStringExtra(EXTRA_SOURCE) ?: "System"
            val isPinned = intent.getBooleanExtra(EXTRA_IS_PINNED, false)

            if (rawText.isBlank() && rawHtml.isNullOrBlank()) return

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val markdownContent = when {
                        !rawHtml.isNullOrBlank() -> MarkdownTranspiler.transpileHtmlToMarkdown(rawHtml)
                        MarkdownTranspiler.looksLikeHtml(rawText) -> MarkdownTranspiler.transpileHtmlToMarkdown(rawText)
                        else -> MarkdownTranspiler.stripInlineCitations(rawText)
                    }

                    val clipType = MarkdownTranspiler.detectClipType(markdownContent, rawHtml)
                    val title = MarkdownTranspiler.extractTitle(markdownContent.ifBlank { rawText })
                    val charCount = markdownContent.length
                    val wordCount = markdownContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size

                    val clip = DaylightClip(
                        textContent = rawText.ifBlank { markdownContent },
                        markdownContent = markdownContent,
                        htmlContent = rawHtml,
                        title = title,
                        clipType = clipType,
                        charCount = charCount,
                        wordCount = wordCount,
                        sourcePackage = sourcePkg,
                        isPinned = isPinned,
                        pinboard = if (isPinned) "PINNED" else "ALL",
                        createdAt = System.currentTimeMillis()
                    )

                    val db = ClipDatabase.getInstance(context)
                    db.insertClip(clip)

                    // Trigger SolOS Amber Pill HUD
                    DaylightClipboardHud(context.applicationContext).show(clip)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_INSERT_CLIP = "com.daylightcomputer.paste.ACTION_INSERT_CLIP"
        const val EXTRA_TEXT = "text"
        const val EXTRA_HTML = "html"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_IS_PINNED = "is_pinned"
    }
}
