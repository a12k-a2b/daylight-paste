package com.daylightcomputer.paste.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.daylightcomputer.paste.markdown.ClipType

class ClipDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "daylight_paste.db"
        const val DATABASE_VERSION = 2

        const val TABLE_CLIPS = "clips"
        const val COL_ID = "id"
        const val COL_TEXT_CONTENT = "text_content"
        const val COL_MARKDOWN_CONTENT = "markdown_content"
        const val COL_HTML_CONTENT = "html_content"
        const val COL_IMAGE_URI = "image_uri"
        const val COL_SUMMARY = "summary"
        const val COL_EMBEDDING = "embedding"
        const val COL_TITLE = "title"
        const val COL_CLIP_TYPE = "clip_type"
        const val COL_CHAR_COUNT = "char_count"
        const val COL_WORD_COUNT = "word_count"
        const val COL_SOURCE_PACKAGE = "source_package"
        const val COL_IS_PINNED = "is_pinned"
        const val COL_PINBOARD = "pinboard"
        const val COL_CREATED_AT = "created_at"

        @Volatile
        private var INSTANCE: ClipDatabase? = null

        fun getInstance(context: Context): ClipDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE IF NOT EXISTS $TABLE_CLIPS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TEXT_CONTENT TEXT NOT NULL,
                $COL_MARKDOWN_CONTENT TEXT NOT NULL,
                $COL_HTML_CONTENT TEXT,
                $COL_IMAGE_URI TEXT,
                $COL_SUMMARY TEXT,
                $COL_EMBEDDING BLOB,
                $COL_TITLE TEXT NOT NULL,
                $COL_CLIP_TYPE TEXT NOT NULL,
                $COL_CHAR_COUNT INTEGER NOT NULL,
                $COL_WORD_COUNT INTEGER NOT NULL,
                $COL_SOURCE_PACKAGE TEXT NOT NULL,
                $COL_IS_PINNED INTEGER NOT NULL DEFAULT 0,
                $COL_PINBOARD TEXT NOT NULL DEFAULT 'ALL',
                $COL_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableSql)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clips_created_at ON $TABLE_CLIPS($COL_CREATED_AT DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clips_pinned ON $TABLE_CLIPS($COL_IS_PINNED, $COL_CREATED_AT DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clips_type ON $TABLE_CLIPS($COL_CLIP_TYPE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_CLIPS ADD COLUMN $COL_IMAGE_URI TEXT")
            } catch (ignored: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_CLIPS ADD COLUMN $COL_SUMMARY TEXT")
            } catch (ignored: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_CLIPS ADD COLUMN $COL_EMBEDDING BLOB")
            } catch (ignored: Exception) {}
        }
    }

    fun insertClip(clip: DaylightClip): Long {
        val db = writableDatabase
        // Prevent duplicate consecutive clips
        val latest = getLatestClip()
        if (latest != null) {
            val isSameText = clip.textContent.isNotBlank() && latest.textContent == clip.textContent && clip.clipType != ClipType.IMAGE
            val isSameImage = clip.isImage && latest.isImage && (
                (!clip.imageUri.isNullOrBlank() && latest.imageUri == clip.imageUri) ||
                (!clip.summary.isNullOrBlank() && latest.summary == clip.summary && latest.title == clip.title)
            )
            if (isSameText || isSameImage) {
                // If it's a duplicate image, clean up newly written file to conserve disk space
                if (isSameImage && clip.imageUri != latest.imageUri && !clip.imageUri.isNullOrBlank()) {
                    try {
                        val path = android.net.Uri.parse(clip.imageUri).path
                        if (path != null) java.io.File(path).delete()
                    } catch (ignored: Exception) {}
                }
                // Touch timestamp
                val values = ContentValues().apply {
                    put(COL_CREATED_AT, System.currentTimeMillis())
                }
                db.update(TABLE_CLIPS, values, "$COL_ID = ?", arrayOf(latest.id.toString()))
                return latest.id
            }
        }

        val values = ContentValues().apply {
            put(COL_TEXT_CONTENT, clip.textContent)
            put(COL_MARKDOWN_CONTENT, clip.markdownContent)
            put(COL_HTML_CONTENT, clip.htmlContent)
            put(COL_IMAGE_URI, clip.imageUri)
            put(COL_SUMMARY, clip.summary)
            put(COL_EMBEDDING, clip.embedding)
            put(COL_TITLE, clip.title)
            put(COL_CLIP_TYPE, clip.clipType.name)
            put(COL_CHAR_COUNT, clip.charCount)
            put(COL_WORD_COUNT, clip.wordCount)
            put(COL_SOURCE_PACKAGE, clip.sourcePackage)
            put(COL_IS_PINNED, if (clip.isPinned) 1 else 0)
            put(COL_PINBOARD, clip.pinboard)
            put(COL_CREATED_AT, clip.createdAt)
        }
        return db.insert(TABLE_CLIPS, null, values)
    }

    fun getLatestClip(): DaylightClip? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CLIPS,
            null,
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC",
            "1"
        )
        cursor.use {
            if (it.moveToFirst()) {
                return parseCursor(it)
            }
        }
        return null
    }

    fun getClips(filterType: String = "ALL", searchQuery: String = "", limit: Int = 200): List<DaylightClip> {
        val db = readableDatabase
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        when (filterType.uppercase()) {
            "THINGS_PILE" -> {
                clauses.add("$COL_PINBOARD = 'THINGS_PILE'")
            }
            "PINNED" -> {
                clauses.add("$COL_IS_PINNED = 1")
            }
            "AI_NOTES", "MARKDOWN" -> {
                clauses.add("$COL_CLIP_TYPE = 'MARKDOWN'")
            }
            "CODE" -> {
                clauses.add("$COL_CLIP_TYPE = 'CODE'")
            }
            "LINKS" -> {
                clauses.add("$COL_CLIP_TYPE = 'URL'")
            }
            "IMAGES" -> {
                clauses.add("($COL_CLIP_TYPE = 'IMAGE' OR ($COL_IMAGE_URI IS NOT NULL AND $COL_IMAGE_URI != ''))")
            }
            else -> {
                // ALL: no type filter
            }
        }

        if (searchQuery.isNotBlank()) {
            clauses.add("($COL_TEXT_CONTENT LIKE ? OR $COL_TITLE LIKE ? OR $COL_SOURCE_PACKAGE LIKE ? OR $COL_CLIP_TYPE LIKE ? OR ($COL_SUMMARY IS NOT NULL AND $COL_SUMMARY LIKE ?))")
            val param = "%$searchQuery%"
            args.add(param)
            args.add(param)
            args.add(param)
            args.add(param)
            args.add(param)
        }

        val whereClause = if (clauses.isNotEmpty()) clauses.joinToString(" AND ") else null
        val cursor = db.query(
            TABLE_CLIPS,
            null,
            whereClause,
            if (args.isNotEmpty()) args.toTypedArray() else null,
            null,
            null,
            "$COL_IS_PINNED DESC, $COL_CREATED_AT DESC",
            limit.toString()
        )

        val results = mutableListOf<DaylightClip>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(parseCursor(it))
            }
        }
        return results
    }

    fun togglePin(clipId: Long, isPinned: Boolean): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_IS_PINNED, if (isPinned) 1 else 0)
        }
        val rows = db.update(TABLE_CLIPS, values, "$COL_ID = ?", arrayOf(clipId.toString()))
        return rows > 0
    }

    fun deleteClip(clipId: Long): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_CLIPS, "$COL_ID = ?", arrayOf(clipId.toString()))
        return rows > 0
    }

    fun getClipById(id: Long): DaylightClip? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CLIPS,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return parseCursor(it)
            }
        }
        return null
    }

    fun updateClipEmbedding(clipId: Long, embedding: ByteArray): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_EMBEDDING, embedding)
        }
        val rows = db.update(TABLE_CLIPS, values, "$COL_ID = ?", arrayOf(clipId.toString()))
        return rows > 0
    }

    fun getClipsNeedingEmbedding(limit: Int = 50): List<DaylightClip> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CLIPS,
            null,
            "$COL_EMBEDDING IS NULL",
            null,
            null,
            null,
            "$COL_CREATED_AT DESC",
            limit.toString()
        )
        val results = mutableListOf<DaylightClip>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(parseCursor(it))
            }
        }
        return results
    }

    fun clearHistory(keepPinned: Boolean = true): Int {
        val db = writableDatabase
        val where = if (keepPinned) "$COL_IS_PINNED = 0" else null
        return db.delete(TABLE_CLIPS, where, null)
    }

    private fun parseCursor(c: Cursor): DaylightClip {
        val id = c.getLong(c.getColumnIndexOrThrow(COL_ID))
        val text = c.getString(c.getColumnIndexOrThrow(COL_TEXT_CONTENT))
        val markdown = c.getString(c.getColumnIndexOrThrow(COL_MARKDOWN_CONTENT))
        val html = c.getString(c.getColumnIndexOrThrow(COL_HTML_CONTENT))

        val imgCol = c.getColumnIndex(COL_IMAGE_URI)
        val imageUri = if (imgCol != -1) c.getString(imgCol) else null

        val summaryCol = c.getColumnIndex(COL_SUMMARY)
        val summary = if (summaryCol != -1) c.getString(summaryCol) else null

        val embedCol = c.getColumnIndex(COL_EMBEDDING)
        val embedding = if (embedCol != -1) c.getBlob(embedCol) else null

        val title = c.getString(c.getColumnIndexOrThrow(COL_TITLE))
        val typeStr = c.getString(c.getColumnIndexOrThrow(COL_CLIP_TYPE))
        val charCount = c.getInt(c.getColumnIndexOrThrow(COL_CHAR_COUNT))
        val wordCount = c.getInt(c.getColumnIndexOrThrow(COL_WORD_COUNT))
        val sourcePkg = c.getString(c.getColumnIndexOrThrow(COL_SOURCE_PACKAGE))
        val isPinned = c.getInt(c.getColumnIndexOrThrow(COL_IS_PINNED)) == 1
        val pinboard = c.getString(c.getColumnIndexOrThrow(COL_PINBOARD))
        val createdAt = c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT))

        val clipType = try {
            ClipType.valueOf(typeStr)
        } catch (e: Exception) {
            ClipType.TEXT
        }

        return DaylightClip(
            id = id,
            textContent = text,
            markdownContent = markdown,
            htmlContent = html,
            imageUri = imageUri,
            summary = summary,
            embedding = embedding,
            title = title,
            clipType = clipType,
            charCount = charCount,
            wordCount = wordCount,
            sourcePackage = sourcePkg,
            isPinned = isPinned,
            pinboard = pinboard,
            createdAt = createdAt
        )
    }
}
