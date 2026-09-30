package com.daylightcomputer.paste.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.daylightcomputer.paste.R
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipboardWatcherService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var clipboardManager: ClipboardManager? = null
    private lateinit var database: ClipDatabase
    private var clipboardHud: DaylightClipboardHud? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipboardChanged()
    }

    override fun onCreate() {
        super.onCreate()
        database = ClipDatabase.getInstance(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
        clipboardHud = DaylightClipboardHud(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Initial clip capture on startup
        onClipboardChanged()
        return START_STICKY
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        clipboardHud?.dismiss()
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onClipboardChanged() {
        scope.launch {
            try {
                val clip = clipboardManager?.primaryClip ?: return@launch
                if (clip.itemCount == 0) return@launch

                // Detect if any clip item contains an image
                var handledAsImage = false
                for (i in 0 until clip.itemCount) {
                    val candidate = clip.getItemAt(i) ?: continue
                    if (isImageClip(clip.description, candidate)) {
                        if (handleImageClip(candidate, clip.description)) {
                            handledAsImage = true
                            break
                        }
                    }
                }
                if (handledAsImage) return@launch

                val item = clip.getItemAt(0) ?: return@launch

                val rawText = item.text?.toString() ?: item.coerceToText(this@ClipboardWatcherService)?.toString() ?: ""
                val htmlText = item.htmlText

                if (rawText.isBlank() && htmlText.isNullOrBlank()) return@launch

                val content = if (rawText.isNotBlank()) rawText else htmlText ?: ""
                
                // Transpile HTML to Markdown if HTML is present, otherwise clean citations
                val markdownContent = when {
                    !htmlText.isNullOrBlank() -> MarkdownTranspiler.transpileHtmlToMarkdown(htmlText)
                    MarkdownTranspiler.detectClipType(content) == ClipType.MARKDOWN -> content
                    MarkdownTranspiler.looksLikeHtml(content) -> MarkdownTranspiler.transpileHtmlToMarkdown(content)
                    else -> MarkdownTranspiler.stripInlineCitations(content)
                }

                val clipType = MarkdownTranspiler.detectClipType(markdownContent, htmlText)
                val title = MarkdownTranspiler.extractTitle(markdownContent.ifBlank { content })
                val charCount = markdownContent.length
                val wordCount = markdownContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                
                var sourcePackage = "System"
                try {
                    val method = clipboardManager?.javaClass?.getMethod("getPrimaryClipSource")
                    val src = method?.invoke(clipboardManager) as? String
                    if (!src.isNullOrBlank()) {
                        sourcePackage = src
                    }
                } catch (ignored: Exception) {}

                val daylightClip = DaylightClip(
                    textContent = content,
                    markdownContent = markdownContent,
                    htmlContent = htmlText,
                    title = title,
                    clipType = clipType,
                    charCount = charCount,
                    wordCount = wordCount,
                    sourcePackage = sourcePackage,
                    isPinned = false,
                    pinboard = "ALL",
                    createdAt = System.currentTimeMillis()
                )

                database.insertClip(daylightClip)

                // Background vectorization for semantic AI search
                scope.launch {
                    try {
                        com.daylightcomputer.paste.ai.SemanticSearchManager.getInstance(this@ClipboardWatcherService).vectorizeMissingClips()
                    } catch (ignored: Exception) {}
                }

                // Show SolOS LivePaper HUD if copied from an external app
                val isInternal = (sourcePackage == packageName) ||
                        DaylightPasteManager.isRecentInternalCopy(content) ||
                        DaylightPasteManager.isRecentInternalCopy(markdownContent)
                if (!isInternal) {
                    clipboardHud?.show(daylightClip)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isImageClip(desc: ClipDescription?, item: ClipData.Item): Boolean {
        if (desc != null) {
            for (i in 0 until desc.mimeTypeCount) {
                val mime = desc.getMimeType(i).lowercase()
                if (mime.startsWith("image/")) return true
            }
        }
        val uri = item.uri ?: item.intent?.data ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme == "content") {
            try {
                val type = contentResolver.getType(uri)
                if (type?.startsWith("image/") == true) return true
            } catch (ignored: Exception) {}
        }
        val path = uri.path?.lowercase() ?: ""
        return path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") || path.endsWith(".gif")
    }

    private fun handleImageClip(item: ClipData.Item, desc: ClipDescription?): Boolean {
        val uri = item.uri ?: item.intent?.data ?: return false
        val uriStr = uri.toString()

        // Prevent self-capture loops
        if (DaylightPasteManager.isRecentInternalCopy(uriStr) ||
            uriStr.contains("com.daylightcomputer.paste.provider")
        ) {
            return true
        }

        return try {
            val imagesDir = File(filesDir, "clips/images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            val filename = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.png"
            val destFile = File(imagesDir, filename)

            val sourceFile = if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) File(path) else null
            } else null

            if (sourceFile != null && sourceFile.exists()) {
                java.io.FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return false
            }

            if (!destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return false
            }

            // Decode image dimensions without loading entire bitmap into RAM
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(destFile.absolutePath, boundsOptions)
            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            val fileSize = destFile.length()
            val formattedSize = formatFileSize(fileSize)
            val metadataSummary = if (width > 0 && height > 0) {
                "$width × $height • $formattedSize"
            } else {
                formattedSize
            }

            val rawLabel = desc?.label?.toString()?.takeIf { it.isNotBlank() }
            val title = when {
                !rawLabel.isNullOrBlank() -> rawLabel
                width > 0 && height > 0 -> "Image (${width}×${height})"
                else -> "Captured Image"
            }

            var sourcePackage = "System"
            try {
                val method = clipboardManager?.javaClass?.getMethod("getPrimaryClipSource")
                val src = method?.invoke(clipboardManager) as? String
                if (!src.isNullOrBlank()) {
                    sourcePackage = src
                }
            } catch (ignored: Exception) {}

            val localUri = "file://${destFile.absolutePath}"
            val daylightClip = DaylightClip(
                textContent = "[Image: $metadataSummary]",
                markdownContent = "![$title]($localUri)",
                imageUri = localUri,
                summary = metadataSummary,
                title = title,
                clipType = ClipType.IMAGE,
                charCount = 0,
                wordCount = 0,
                sourcePackage = sourcePackage,
                isPinned = false,
                pinboard = "IMAGES",
                createdAt = System.currentTimeMillis()
            )

            database.insertClip(daylightClip)

            // Background vectorization for semantic AI search
            scope.launch {
                try {
                    com.daylightcomputer.paste.ai.SemanticSearchManager.getInstance(this@ClipboardWatcherService).vectorizeMissingClips()
                } catch (ignored: Exception) {}
            }

            val isInternal = (sourcePackage == packageName) ||
                    DaylightPasteManager.isRecentInternalCopy(localUri)
            if (!isInternal) {
                clipboardHud?.show(daylightClip)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daylight Clipboard Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Monitors clipboard and normalizes Markdown for SolOS"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Daylight Clipboard")
            .setContentText("Preserving Markdown and clipboard history")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "daylight_clipboard_channel"
        private const val NOTIFICATION_ID = 5951
    }
}
