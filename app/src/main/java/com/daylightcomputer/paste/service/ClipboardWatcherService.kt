package com.daylightcomputer.paste.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.daylightcomputer.paste.R
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipboardWatcherService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var clipboardManager: ClipboardManager? = null
    private lateinit var database: ClipDatabase

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipboardChanged()
    }

    override fun onCreate() {
        super.onCreate()
        database = ClipDatabase.getInstance(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)

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
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onClipboardChanged() {
        scope.launch {
            try {
                val clip = clipboardManager?.primaryClip ?: return@launch
                if (clip.itemCount == 0) return@launch

                val item = clip.getItemAt(0) ?: return@launch
                val rawText = item.text?.toString() ?: item.coerceToText(this@ClipboardWatcherService)?.toString() ?: ""
                val htmlText = item.htmlText

                if (rawText.isBlank() && htmlText.isNullOrBlank()) return@launch

                val content = if (rawText.isNotBlank()) rawText else htmlText ?: ""
                
                // Transpile HTML to Markdown if HTML is present
                val markdownContent = if (!htmlText.isNullOrBlank()) {
                    MarkdownTranspiler.transpileHtmlToMarkdown(htmlText)
                } else if (MarkdownTranspiler.detectClipType(content) == ClipType.MARKDOWN) {
                    content
                } else {
                    content
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
