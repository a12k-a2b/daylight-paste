package com.daylightcomputer.paste

import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.data.DaylightPasteContentProvider
import com.daylightcomputer.paste.markdown.ClipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageClipboardTest {

    @Test
    fun testDaylightClipImageProperties() {
        val imageClip = DaylightClip(
            id = 101,
            textContent = "[Image: 1200 × 1600 • 350.5 KB]",
            markdownContent = "![Diagram](file:///data/user/0/com.daylightcomputer.paste/files/clips/images/test.png)",
            imageUri = "file:///data/user/0/com.daylightcomputer.paste/files/clips/images/test.png",
            summary = "1200 × 1600 • 350.5 KB",
            title = "Diagram",
            clipType = ClipType.IMAGE
        )

        assertTrue("Must report isImage as true", imageClip.isImage)
        assertEquals("1200 × 1600", imageClip.imageDimensions)
        assertEquals("350.5 KB", imageClip.imageFileSize)
        assertEquals(ClipType.IMAGE, imageClip.clipType)
    }

    @Test
    fun testTextClipIsNotImage() {
        val textClip = DaylightClip(
            id = 102,
            textContent = "Hello world",
            markdownContent = "Hello world",
            imageUri = null,
            summary = null,
            title = "Greeting",
            clipType = ClipType.TEXT
        )

        assertFalse("Must report isImage as false", textClip.isImage)
        assertNull(textClip.imageDimensions)
        assertNull(textClip.imageFileSize)
    }

    @Test
    fun testContentProviderUriMatching() {
        val authority = DaylightPasteContentProvider.AUTHORITY
        assertEquals("com.daylightcomputer.paste.provider", authority)

        assertEquals(
            DaylightPasteContentProvider.CODE_CLIPS,
            DaylightPasteContentProvider.matchPath("content://$authority/clips")
        )

        assertEquals(
            DaylightPasteContentProvider.CODE_CLIP_ID,
            DaylightPasteContentProvider.matchPath("content://$authority/clips/42")
        )

        assertEquals(
            DaylightPasteContentProvider.CODE_IMAGES,
            DaylightPasteContentProvider.matchPath("content://$authority/images")
        )

        assertEquals(
            DaylightPasteContentProvider.CODE_IMAGE_FILE,
            DaylightPasteContentProvider.matchPath("content://$authority/images/screenshot_123.png")
        )

        assertEquals(
            DaylightPasteContentProvider.CODE_IMAGE_ID,
            DaylightPasteContentProvider.matchPath("content://$authority/images/999")
        )
    }

    @Test
    fun testContentProviderMimeTypes() {
        assertEquals("image/png", DaylightPasteContentProvider.getMimeTypeForPath("/images/sample.png"))
        assertEquals("image/jpeg", DaylightPasteContentProvider.getMimeTypeForPath("/images/photo.jpg"))
        assertEquals("image/jpeg", DaylightPasteContentProvider.getMimeTypeForPath("/images/photo.jpeg"))
        assertEquals("image/webp", DaylightPasteContentProvider.getMimeTypeForPath("/images/vector.webp"))
        assertEquals("text/plain", DaylightPasteContentProvider.getMimeTypeForPath("/clips/12"))
    }

    @Test
    fun testImageMetadataSummaryFormatting() {
        fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
                else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }

        assertEquals("500 B", formatSize(500))
        assertEquals("150.0 KB", formatSize(150 * 1024))
        assertEquals("4.5 MB", formatSize((4.5 * 1024 * 1024).toLong()))

        val width = 1200
        val height = 1600
        val fileSize = 245 * 1024L
        val summary = "$width × $height • ${formatSize(fileSize)}"
        assertEquals("1200 × 1600 • 245.0 KB", summary)
    }

    @Test
    fun testTextClipWithSummaryHasNullImageAccessors() {
        val textClip = DaylightClip(
            id = 103,
            textContent = "Note content",
            markdownContent = "Note content",
            summary = "Executive summary of long document",
            title = "Summary Note",
            clipType = ClipType.TEXT
        )
        assertFalse(textClip.isImage)
        assertNull("imageDimensions must be null for non-image clip with text summary", textClip.imageDimensions)
        assertNull("imageFileSize must be null for non-image clip with text summary", textClip.imageFileSize)
    }

    @Test
    fun testImageClipWithOnlyFileSizeSummary() {
        val imageClip = DaylightClip(
            id = 104,
            imageUri = "file:///clips/images/photo.png",
            summary = "850.5 KB",
            title = "Captured Image",
            clipType = ClipType.IMAGE
        )
        assertTrue(imageClip.isImage)
        assertNull("imageDimensions must be null when summary only has file size", imageClip.imageDimensions)
        assertEquals("850.5 KB", imageClip.imageFileSize)
    }

    @Test
    fun testImageContentUriGeneration() {
        val uriStr = DaylightPasteContentProvider.getImageContentUriString("screen_55.png")
        assertEquals("content://com.daylightcomputer.paste.provider/images/screen_55.png", uriStr)
    }

    @Test
    fun testMimeTypesWithGifAndClips() {
        assertEquals("image/png", DaylightPasteContentProvider.getMimeTypeForPath("/clips/images/photo.png"))
        assertEquals("image/png", DaylightPasteContentProvider.getMimeTypeForPath("/data/user/0/com.daylightcomputer.paste/files/clips/images/1790796093872_ab3d7045.png"))
        assertEquals("image/gif", DaylightPasteContentProvider.getMimeTypeForPath("/images/anim.gif"))
        assertEquals("text/plain", DaylightPasteContentProvider.getMimeTypeForPath("/clips"))
        assertEquals("text/plain", DaylightPasteContentProvider.getMimeTypeForPath("/clips/99"))
    }
}
