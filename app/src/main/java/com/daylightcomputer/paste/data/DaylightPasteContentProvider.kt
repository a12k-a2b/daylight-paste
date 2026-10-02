package com.daylightcomputer.paste.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Daylight Paste Streaming ContentProvider for SolOS / Daylight DC-1.
 * 
 * Secure streaming architecture:
 * - android:exported="false" with narrow temporary URI grants (FLAG_GRANT_READ_URI_PERMISSION).
 * - Zero collection querying exposed to external callers (prevents enumeration).
 * - Distinguishes between explicit representations:
 *     content://.../clips/#/markdown -> streams clean UTF-8 CommonMark/GFM
 *     content://.../clips/#/plain    -> streams raw plain text
 *     content://.../images/#         -> streams high-res images from app-private storage
 * - Bypasses Android's 1MB Binder ceiling via kernel pipe streaming (ParcelFileDescriptor).
 */
open class DaylightPasteContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.daylightcomputer.paste.provider"

        const val CODE_CLIP_ID = 1
        const val CODE_CLIP_MARKDOWN = 2
        const val CODE_CLIP_PLAIN = 3
        const val CODE_IMAGE_ID = 4
        const val CODE_IMAGE_FILE = 5

        val MATCHER by lazy {
            UriMatcher(UriMatcher.NO_MATCH).apply {
                addURI(AUTHORITY, "clips/#/markdown", CODE_CLIP_MARKDOWN)
                addURI(AUTHORITY, "clips/#/plain", CODE_CLIP_PLAIN)
                addURI(AUTHORITY, "clips/#", CODE_CLIP_ID)
                addURI(AUTHORITY, "images/#", CODE_IMAGE_ID)
                addURI(AUTHORITY, "images/*", CODE_IMAGE_FILE)
            }
        }

        fun getMimeTypeForPath(path: String?): String {
            val lower = path?.lowercase() ?: ""
            return when {
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                lower.endsWith(".webp") -> "image/webp"
                lower.endsWith(".gif") -> "image/gif"
                lower.endsWith("/markdown") -> "text/markdown"
                lower.endsWith("/plain") -> "text/plain"
                lower.contains("/images") -> "image/png"
                lower.contains("/clips") -> "text/plain"
                else -> "image/png"
            }
        }

        fun getClipUri(clipId: Long, asMarkdown: Boolean = true): Uri {
            val rep = if (asMarkdown) "markdown" else "plain"
            return Uri.parse("content://$AUTHORITY/clips/$clipId/$rep")
        }

        fun getImageContentUri(context: Context, clip: DaylightClip): Uri? {
            val uriStr = clip.imageUri ?: return null
            val file = if (uriStr.startsWith("file://")) {
                File(Uri.parse(uriStr).path ?: "")
            } else {
                File(uriStr)
            }
            return if (file.exists()) {
                Uri.parse("content://$AUTHORITY/images/${file.name}")
            } else if (clip.id > 0) {
                Uri.parse("content://$AUTHORITY/images/${clip.id}")
            } else {
                null
            }
        }

        fun getImageContentUriString(filename: String): String =
            "content://$AUTHORITY/images/$filename"

        fun getImageContentUri(filename: String): Uri =
            Uri.parse(getImageContentUriString(filename))
    }

    protected lateinit var database: ClipDatabase

    override fun onCreate(): Boolean {
        context?.let {
            database = ClipDatabase.getInstance(it)
        }
        return true
    }

    /**
     * Rejects all external queries. The provider is not a queryable surface.
     * Content is accessed solely via openFile with explicit per-URI grants.
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String {
        return when (MATCHER.match(uri)) {
            CODE_CLIP_MARKDOWN -> "text/markdown"
            CODE_CLIP_PLAIN -> "text/plain"
            CODE_CLIP_ID -> "text/plain"
            CODE_IMAGE_ID, CODE_IMAGE_FILE -> getMimeTypeForPath(uri.path)
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.contains("r")) {
            throw SecurityException("DaylightPasteContentProvider only supports read-only mode")
        }

        val match = MATCHER.match(uri)
        when (match) {
            CODE_IMAGE_FILE, CODE_IMAGE_ID -> {
                val ctx = context ?: throw FileNotFoundException("Context is null")
                val imagesDir = File(ctx.filesDir, "clips/images")

                val file = if (match == CODE_IMAGE_ID) {
                    val id = uri.pathSegments.getOrNull(1)?.toLongOrNull()
                        ?: throw FileNotFoundException("Invalid clip ID: $uri")
                    val clip = database.getClipById(id)
                        ?: throw FileNotFoundException("Clip #$id not found")
                    val imgUri = clip.imageUri ?: throw FileNotFoundException("Clip #$id has no imageUri")
                    val path = if (imgUri.startsWith("file://")) Uri.parse(imgUri).path ?: "" else imgUri
                    File(path)
                } else {
                    val filename = uri.lastPathSegment ?: throw FileNotFoundException("Missing filename: $uri")
                    val id = filename.toLongOrNull()
                    if (id != null) {
                        val clip = database.getClipById(id)
                        val imgUri = clip?.imageUri
                        if (imgUri != null) {
                            val path = if (imgUri.startsWith("file://")) Uri.parse(imgUri).path ?: "" else imgUri
                            File(path)
                        } else {
                            File(imagesDir, filename)
                        }
                    } else {
                        File(imagesDir, filename)
                    }
                }

                if (!file.exists()) {
                    throw FileNotFoundException("Image file does not exist: ${file.absolutePath}")
                }

                // Security check: ensure path is within app internal storage
                val canonicalFilesDir = ctx.filesDir.canonicalPath
                val canonicalCacheDir = ctx.cacheDir.canonicalPath
                val canonicalFile = file.canonicalPath
                if (!canonicalFile.startsWith(canonicalFilesDir) && !canonicalFile.startsWith(canonicalCacheDir)) {
                    throw SecurityException("Access outside app internal storage is denied: $canonicalFile")
                }

                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            CODE_CLIP_MARKDOWN, CODE_CLIP_PLAIN, CODE_CLIP_ID -> {
                val idStr = uri.pathSegments.getOrNull(1) ?: throw FileNotFoundException("Invalid clip URI: $uri")
                val id = idStr.toLongOrNull() ?: throw FileNotFoundException("Invalid clip ID: $uri")

                val clip = database.getClipById(id)
                    ?: throw FileNotFoundException("Clip #$id not found")

                val text = when (match) {
                    CODE_CLIP_MARKDOWN -> {
                        if (clip.markdownContent.isNotBlank()) clip.markdownContent else clip.textContent
                    }
                    CODE_CLIP_PLAIN -> {
                        if (clip.textContent.isNotBlank()) {
                            clip.textContent
                        } else {
                            MarkdownTranspiler.stripFormatting(clip.markdownContent)
                        }
                    }
                    else -> {
                        if (clip.markdownContent.isNotBlank()) clip.markdownContent else clip.textContent
                    }
                }

                val textBytes = text.toByteArray(Charsets.UTF_8)
                val mimeType = if (match == CODE_CLIP_MARKDOWN) "text/markdown" else "text/plain"

                return openPipeHelper(uri, mimeType, null, textBytes) { output, _, _, _, bytes ->
                    var fos: FileOutputStream? = null
                    try {
                        fos = FileOutputStream(output.fileDescriptor)
                        fos.write(bytes)
                        fos.flush()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        try {
                            fos?.close()
                        } catch (ignored: Exception) {}
                    }
                }
            }
            else -> throw FileNotFoundException("Unsupported URI: $uri")
        }
    }
}
