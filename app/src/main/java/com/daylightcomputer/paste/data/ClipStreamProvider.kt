package com.daylightcomputer.paste.data

import android.net.Uri

/**
 * Backward-compatible alias/subclass for DaylightPasteContentProvider.
 */
class ClipStreamProvider : DaylightPasteContentProvider() {
    companion object {
        const val AUTHORITY = DaylightPasteContentProvider.AUTHORITY
        val CONTENT_URI: Uri = DaylightPasteContentProvider.CONTENT_URI

        fun getClipUri(clipId: Long): Uri = DaylightPasteContentProvider.getClipUri(clipId)
    }
}
