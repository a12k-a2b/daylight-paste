package com.daylightcomputer.paste.data

import android.net.Uri

/**
 * Backward-compatible alias/subclass for DaylightPasteContentProvider.
 */
class ClipStreamProvider : DaylightPasteContentProvider() {
    companion object {
        const val AUTHORITY = DaylightPasteContentProvider.AUTHORITY

        fun getClipUri(clipId: Long, asMarkdown: Boolean = true): Uri =
            DaylightPasteContentProvider.getClipUri(clipId, asMarkdown)
    }
}
