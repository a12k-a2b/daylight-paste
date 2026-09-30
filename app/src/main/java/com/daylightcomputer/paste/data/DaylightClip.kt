package com.daylightcomputer.paste.data

import com.daylightcomputer.paste.markdown.ClipType

data class DaylightClip(
    val id: Long = 0,
    val textContent: String = "",
    val markdownContent: String = "",
    val htmlContent: String? = null,
    val imageUri: String? = null,
    val summary: String? = null,
    val embedding: ByteArray? = null,
    val title: String,
    val clipType: ClipType,
    val charCount: Int = 0,
    val wordCount: Int = 0,
    val sourcePackage: String = "unknown",
    val isPinned: Boolean = false,
    val pinboard: String = "ALL",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isImage: Boolean
        get() = clipType == ClipType.IMAGE || !imageUri.isNullOrBlank()

    val imageDimensions: String?
        get() = summary?.substringBefore(" • ")

    val imageFileSize: String?
        get() = if (summary?.contains(" • ") == true) summary.substringAfter(" • ") else summary
}

