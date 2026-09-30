package com.daylightcomputer.paste.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Unlimited Clipboard Streaming Provider for SolOS / Daylight DC-1.
 * 
 * Bypasses Android's 1MB Binder transaction ceiling (TransactionTooLargeException)
 * by streaming arbitrarily large text payloads (entire book chapters, massive AI prompts,
 * codebases) via ParcelFileDescriptor pipe, mirroring iOS lazy pasteboard streaming.
 */
class ClipStreamProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.daylightcomputer.paste.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/clips")

        private const val CODE_CLIPS = 1
        private const val CODE_CLIP_ID = 2

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "clips", CODE_CLIPS)
            addURI(AUTHORITY, "clips/#", CODE_CLIP_ID)
        }

        fun getClipUri(clipId: Long): Uri = Uri.parse("content://$AUTHORITY/clips/$clipId")
    }

    private lateinit var database: ClipDatabase

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
            else -> null
        }
    }

    override fun getType(uri: Uri): String {
        return "text/plain"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.contains("r")) {
            throw SecurityException("ClipStreamProvider only supports read mode")
        }

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
}
