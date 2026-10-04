package com.daylightcomputer.paste.service

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.daylightcomputer.paste.data.ClipDatabase
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.data.DaylightPasteContentProvider
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DaylightPasteManager {

    // Maximum byte payload allowed directly across Binder IPC before streaming via ContentProvider.
    // Standard Binder buffer is 1MB per process shared across all concurrent transactions; 256KB is safe.
    const val MAX_BINDER_BYTE_THRESHOLD = 256 * 1024 // 256 KB

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
     * Diagnostic health check: tests if the app can read from the system clipboard.
     * On Android 10+ (SolOS / Android 13), ordinary background processes are denied
     * unless focused, default IME, or platform-privileged with READ_CLIPBOARD_IN_BACKGROUND.
     */
    fun checkClipboardAccessState(context: Context): ClipboardAccessState {
        // NOTE: probing clipboard.primaryClip from our own Activity is meaningless — the focused
        // app is always allowed to read. The real question is whether reads will succeed while
        // ANOTHER app is focused. On Android 13 that requires us to be the default IME.
        return try {
            if (isDefaultInputMethod(context)) {
                ClipboardAccessState.BACKGROUND_VIA_IME
            } else {
                ClipboardAccessState.FOREGROUND_ONLY
            }
        } catch (e: Exception) {
            ClipboardAccessState.UNAVAILABLE
        }
    }

    fun isDefaultInputMethod(context: Context): Boolean {
        val current = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        return current.startsWith("${context.packageName}/")
    }

    enum class ClipboardAccessState(val label: String, val isPrivileged: Boolean) {
        BACKGROUND_VIA_IME("Background capture ON (Daylight Paste keyboard is active)", true),
        FOREGROUND_ONLY("Background capture OFF — switch keyboard to Daylight Paste", false),
        UNAVAILABLE("Clipboard service unavailable", false)
    }

    /**
     * Copies clean Markdown to system clipboard.
     *
     * Streaming Architecture:
     * - Small text (<= 256KB): directly placed on clipboard as text/plain to preserve Markdown syntax.
     * - Large text (> 256KB): streamed via DaylightPasteContentProvider URI backed by ParcelFileDescriptor,
     *   bypassing Android 1MB Binder ceiling without silent truncation.
     */
    fun copyAsMarkdown(context: Context, clip: DaylightClip): Boolean {
        return try {
            val fullClip = if (clip.id > 0 && (clip.markdownContent.isEmpty() || clip.textContent.length <= 300)) {
                ClipDatabase.getInstance(context).getClipById(clip.id) ?: clip
            } else {
                clip
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val textToCopy = if (fullClip.markdownContent.isNotBlank()) fullClip.markdownContent else fullClip.textContent
            val byteSize = textToCopy.toByteArray(Charsets.UTF_8).size

            markInternalCopy(textToCopy)

            val clipData = if (byteSize <= MAX_BINDER_BYTE_THRESHOLD || fullClip.id <= 0) {
                // Direct in-memory copy for normal text
                ClipData.newPlainText(fullClip.title, textToCopy)
            } else {
                // Large text: stream via ContentProvider ParcelFileDescriptor
                val streamUri = DaylightPasteContentProvider.getClipUri(fullClip.id, asMarkdown = true)

                val grantIntent = Intent().apply {
                    data = streamUri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // In Android's ClipData.Item.coerceToText(), if text is non-null, it returns text directly
                // and ignores the URI. Passing null for text guarantees that coerceToText() resolves the URI
                // stream via openInputStream(), delivering the full payload without 1MB Binder crash.
                val item = ClipData.Item(null, null, grantIntent, streamUri)
                val description = ClipDescription(fullClip.title, arrayOf("text/markdown", "text/plain"))
                ClipData(description, item).also { cd ->
                    grantStreamingPermissions(context, streamUri)
                }
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
            val fullClip = if (clip.id > 0 && (clip.textContent.isEmpty() || clip.textContent.length <= 300)) {
                ClipDatabase.getInstance(context).getClipById(clip.id) ?: clip
            } else {
                clip
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val raw = if (fullClip.textContent.isNotBlank()) {
                fullClip.textContent
            } else {
                MarkdownTranspiler.stripFormatting(fullClip.markdownContent)
            }
            val byteSize = raw.toByteArray(Charsets.UTF_8).size

            markInternalCopy(raw)

            val clipData = if (byteSize <= MAX_BINDER_BYTE_THRESHOLD || fullClip.id <= 0) {
                ClipData.newPlainText(fullClip.title, raw)
            } else {
                val streamUri = DaylightPasteContentProvider.getClipUri(fullClip.id, asMarkdown = false)
                val preview = if (raw.length > 500) {
                    raw.take(500) + "\n\n... [Streamed via Daylight Paste: ${fullClip.wordCount} words, ${fullClip.charCount} chars]"
                } else {
                    raw
                }

                val grantIntent = Intent().apply {
                    data = streamUri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val item = ClipData.Item(null, null, grantIntent, streamUri)
                ClipData(ClipDescription(fullClip.title, arrayOf("text/plain")), item).also {
                    grantStreamingPermissions(context, streamUri)
                }
            }

            clipboard.setPrimaryClip(clipData)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Copies image content URI to system clipboard backed by DaylightPasteContentProvider.
     */
    fun copyImageToClipboard(context: Context, clip: DaylightClip): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val contentUri = DaylightPasteContentProvider.getImageContentUri(context, clip)
                ?: return false

            markInternalCopy(contentUri.toString())
            if (!clip.imageUri.isNullOrBlank()) {
                markInternalCopy(clip.imageUri)
            }

            val grantIntent = Intent().apply {
                data = contentUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val itemWithPermission = ClipData.Item(null, null, grantIntent, contentUri)
            val clipData = ClipData(ClipDescription(clip.title, arrayOf("image/png", "image/jpeg")), itemWithPermission)

            grantStreamingPermissions(context, contentUri)
            clipboard.setPrimaryClip(clipData)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun grantStreamingPermissions(context: Context, uri: Uri) {
        listOf(
            "com.daylightcomputer.paper",
            "com.daylightcomputer.launcher",
            "com.android.shell",
            "com.daylightcomputer.reader.m3"
        ).forEach { pkg ->
            try {
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Prepares clipboard for pasting.
     * Note: Direct cross-process input event injection (input keyevent 279) is removed
     * because ordinary app UIDs lack the signature-only INJECT_EVENTS permission.
     * Pasting is performed via OS Paste, PROCESS_TEXT, or user interaction.
     */
    suspend fun preparePasteAction(context: Context, clip: DaylightClip, asMarkdown: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        when {
            clip.clipType == com.daylightcomputer.paste.markdown.ClipType.IMAGE || !clip.imageUri.isNullOrBlank() -> {
                copyImageToClipboard(context, clip)
            }
            asMarkdown -> {
                copyAsMarkdown(context, clip)
            }
            else -> {
                copyAsPlainText(context, clip)
            }
        }
    }
}
