package com.daylightcomputer.paste.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.daylightcomputer.paste.markdown.ClipType
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Daylight Paste Streaming ContentProvider for SolOS / Daylight DC-1.
 * 
 * Bypasses Android's 1MB Binder transaction ceiling (TransactionTooLargeException)
 * by streaming arbitrarily large text payloads (entire book chapters, massive AI prompts,
 * codebases) and high-resolution images via ParcelFileDescriptor pipe, mirroring iOS
 * lazy pasteboard streaming.
 * 
 * Supports streaming images directly into third-party apps (Day One, Noteshelf, Chrome, etc.)
 * via content://com.daylightcomputer.paste.provider/images/...
 */
open class DaylightPasteContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.daylightcomputer.paste.provider"
        val CONTENT_URI: Uri by lazy { Uri.parse("content://$AUTHORITY/clips") }
        val IMAGES_URI: Uri by lazy { Uri.parse("content://$AUTHORITY/images") }

        const val CODE_CLIPS = 1
        const val CODE_CLIP_ID = 2
        const val CODE_IMAGES = 3
        const val CODE_IMAGE_FILE = 4
        const val CODE_IMAGE_ID = 5

        val MATCHER by lazy {
            UriMatcher(UriMatcher.NO_MATCH).apply {
                addURI(AUTHORITY, "clips", CODE_CLIPS)
                addURI(AUTHORITY, "clips/#", CODE_CLIP_ID)
                addURI(AUTHORITY, "images", CODE_IMAGES)
                addURI(AUTHORITY, "images/#", CODE_IMAGE_ID)
                addURI(AUTHORITY, "images/*", CODE_IMAGE_FILE)
            }
        }

        fun matchPath(path: String): Int {
            val clean = path.trim('/').removePrefix("content://com.daylightcomputer.paste.provider/").trim('/')
            return when {
                clean == "clips" -> CODE_CLIPS
                clean.matches(Regex("clips/\\d+")) -> CODE_CLIP_ID
                clean == "images" -> CODE_IMAGES
                clean.matches(Regex("images/\\d+")) -> CODE_IMAGE_ID
                clean.startsWith("images/") -> CODE_IMAGE_FILE
                else -> -1
            }
        }

        fun getMimeTypeForPath(path: String?): String {
            val lower = path?.lowercase() ?: ""
            return when {
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                lower.endsWith(".webp") -> "image/webp"
                lower.endsWith(".gif") -> "image/gif"
                lower.contains("/images") -> "image/png"
                lower.contains("/clips") -> "text/plain"
                else -> "image/png"
            }
        }

        fun getClipUri(clipId: Long): Uri = Uri.parse("content://$AUTHORITY/clips/$clipId")

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

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val db = database.readableDatabase
        return when (MATCHER.match(uri)) {
            CODE_CLIP_ID -> {
                val id = uri.lastPathSegment ?: return null
                db.query(
                    ClipDatabase.TABLE_CLIPS,
                    projection,
                    "${ClipDatabase.COL_ID} = ?",
                    arrayOf(id),
                    null,
                    null,
                    null
                )
            }
            CODE_CLIPS -> {
                db.query(
                    ClipDatabase.TABLE_CLIPS,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    sortOrder
                )
            }
            CODE_IMAGE_ID -> {
                val id = uri.lastPathSegment ?: return null
                db.query(
                    ClipDatabase.TABLE_CLIPS,
                    projection,
                    "${ClipDatabase.COL_ID} = ? AND ${ClipDatabase.COL_CLIP_TYPE} = 'IMAGE'",
                    arrayOf(id),
                    null,
                    null,
                    null
                )
            }
            CODE_IMAGES -> {
                db.query(
                    ClipDatabase.TABLE_CLIPS,
                    projection,
                    "${ClipDatabase.COL_CLIP_TYPE} = 'IMAGE'",
                    null,
                    null,
                    null,
                    sortOrder ?: "${ClipDatabase.COL_CREATED_AT} DESC"
                )
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String {
        return when (MATCHER.match(uri)) {
            CODE_CLIPS -> "vnd.android.cursor.dir/com.daylightcomputer.paste.clip"
            CODE_CLIP_ID -> "text/plain"
            CODE_IMAGES -> "vnd.android.cursor.dir/image"
            CODE_IMAGE_ID -> {
                val id = uri.lastPathSegment?.toLongOrNull()
                val clip = id?.let { database.getClipById(it) }
                getMimeTypeForPath(clip?.imageUri ?: uri.path)
            }
            CODE_IMAGE_FILE -> getMimeTypeForPath(uri.path)
            else -> getMimeTypeForPath(uri.path)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.contains("r")) {
            throw SecurityException("DaylightPasteContentProvider only supports read mode")
        }

        val match = MATCHER.match(uri)
        when (match) {
            CODE_IMAGE_FILE, CODE_IMAGE_ID -> {
                val ctx = context ?: throw FileNotFoundException("Context is null")
                val imagesDir = File(ctx.filesDir, "clips/images")

                val file = if (match == CODE_IMAGE_ID) {
                    val id = uri.lastPathSegment?.toLongOrNull()
                        ?: throw FileNotFoundException("Invalid clip ID: $uri")
                    val clip = database.getClipById(id)
                        ?: throw FileNotFoundException("Clip #$id not found")
                    val imgUri = clip.imageUri ?: throw FileNotFoundException("Clip #$id has no imageUri")
                    val path = if (imgUri.startsWith("file://")) Uri.parse(imgUri).path ?: "" else imgUri
                    File(path)
                } else {
                    val filename = uri.lastPathSegment ?: throw FileNotFoundException("Missing filename: $uri")
                    // If filename is actually an integer ID, try looking up clip
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

                // Security check: ensure path is within app internal storage to prevent traversal
                val canonicalFilesDir = ctx.filesDir.canonicalPath
                val canonicalCacheDir = ctx.cacheDir.canonicalPath
                val canonicalFile = file.canonicalPath
                if (!canonicalFile.startsWith(canonicalFilesDir) && !canonicalFile.startsWith(canonicalCacheDir)) {
                    throw SecurityException("Access outside app internal storage is denied: $canonicalFile")
                }

                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            CODE_CLIP_ID -> {
                val id = uri.lastPathSegment?.toLongOrNull()
                    ?: throw FileNotFoundException("Invalid clip URI: $uri")

                val clip = database.getClipById(id)
                    ?: throw FileNotFoundException("Clip #$id not found")

                val textBytes = clip.markdownContent.toByteArray(Charsets.UTF_8)

                return openPipeHelper(uri, "text/plain", null, textBytes) { output, _, _, _, bytes ->
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
