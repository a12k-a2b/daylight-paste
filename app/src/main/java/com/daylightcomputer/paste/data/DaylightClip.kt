package com.daylightcomputer.paste.data

import com.daylightcomputer.paste.markdown.ClipType

data class DaylightClip(
    val id: Long = 0,
    val textContent: String,
    val markdownContent: String,
    val htmlContent: String? = null,
    val title: String,
    val clipType: ClipType,
    val charCount: Int,
    val wordCount: Int,
    val sourcePackage: String = "unknown",
    val isPinned: Boolean = false,
    val pinboard: String = "ALL",
    val createdAt: Long = System.currentTimeMillis()
)
