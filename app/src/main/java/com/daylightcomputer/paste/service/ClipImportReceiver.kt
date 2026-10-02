package com.daylightcomputer.paste.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClipImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_INSERT_CLIP) {
            val rawText = intent.getStringExtra(EXTRA_TEXT) ?: ""
            val rawHtml = intent.getStringExtra(EXTRA_HTML)
            val sourcePkg = intent.getStringExtra(EXTRA_SOURCE) ?: "System"
            val isPinned = intent.getBooleanExtra(EXTRA_IS_PINNED, false)
            val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
            val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)

            if (rawText.isBlank() && rawHtml.isNullOrBlank() && imagePath.isNullOrBlank() && imageUri.isNullOrBlank()) {
                return
            }

            // Strict payload bounds to prevent memory/CPU denial-of-service
            if (rawText.length > 500_000 || (rawHtml != null && rawHtml.length > 500_000)) {
                android.util.Log.w("ClipImportReceiver", "Rejected oversized broadcast payload (>500k chars)")
                return
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = ClipDatabase.getInstance(context)

                    if (!imagePath.isNullOrBlank() || !imageUri.isNullOrBlank()) {
                        // Image Import Mode
                        val sourceFile = if (!imagePath.isNullOrBlank()) {
                            File(imagePath)
                        } else {
                            val u = Uri.parse(imageUri)
                            if (u.scheme == "file") File(u.path ?: "") else null
                        }

                        val imagesDir = File(context.filesDir, "clips/images").apply { mkdirs() }
                        val filename = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.png"
                        val destFile = File(imagesDir, filename)

                        if (sourceFile != null && sourceFile.exists()) {
                            FileInputStream(sourceFile).use { input ->
                                FileOutputStream(destFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } else if (!imageUri.isNullOrBlank()) {
                            context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { input ->
                                FileOutputStream(destFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }

                        if (destFile.exists() && destFile.length() > 0) {
                            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(destFile.absolutePath, boundsOptions)
                            val width = boundsOptions.outWidth
                            val height = boundsOptions.outHeight
                            val fileSize = destFile.length()
                            val formattedSize = formatFileSize(fileSize)
                            val metaSummary = if (width > 0 && height > 0) "$width × $height • $formattedSize" else formattedSize
                            val title = intent.getStringExtra(EXTRA_IMAGE_TITLE) ?: "Image (${width}×${height})"
                            val localUri = "file://${destFile.absolutePath}"

                            val clip = DaylightClip(
                                textContent = "[Image: $metaSummary]",
                                markdownContent = "![$title]($localUri)",
                                imageUri = localUri,
                                summary = metaSummary,
                                title = title,
                                clipType = ClipType.IMAGE,
                                charCount = 0,
                                wordCount = 0,
                                sourcePackage = sourcePkg,
                                isPinned = isPinned,
                                pinboard = if (isPinned) "PINNED" else "IMAGES",
                                createdAt = System.currentTimeMillis()
                            )

                            db.insertClip(clip)
                            DaylightClipboardHud(context.applicationContext).show(clip)
                        }
                    } else {
                        // Text / Markdown Import Mode
                        val markdownContent = when {
                            !rawHtml.isNullOrBlank() -> MarkdownTranspiler.transpileHtmlToMarkdown(rawHtml)
                            MarkdownTranspiler.looksLikeHtml(rawText) -> MarkdownTranspiler.transpileHtmlToMarkdown(rawText)
                            else -> MarkdownTranspiler.stripInlineCitations(rawText)
                        }

                        val clipType = MarkdownTranspiler.detectClipType(markdownContent, rawHtml ?: "")
                        val title = MarkdownTranspiler.extractTitle(markdownContent.ifBlank { rawText })
                        val charCount = markdownContent.length
                        val wordCount = MarkdownTranspiler.countWords(markdownContent)

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

                        db.insertClip(clip)
                        DaylightClipboardHud(context.applicationContext).show(clip)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    companion object {
        const val ACTION_INSERT_CLIP = "com.daylightcomputer.paste.ACTION_INSERT_CLIP"
        const val EXTRA_TEXT = "text"
        const val EXTRA_HTML = "html"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_IS_PINNED = "is_pinned"
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_IMAGE_TITLE = "image_title"
    }
}
