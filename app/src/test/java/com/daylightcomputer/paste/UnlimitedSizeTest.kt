package com.daylightcomputer.paste

import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlimitedSizeTest {

    @Test
    fun testMassiveStringProcessingWithoutTruncation() {
        // Test 100,000 character AI generation
        val targetSize = 100_000
        val sb = StringBuilder(targetSize)
        var wordCount = 0
        while (sb.length < targetSize) {
            sb.append("This is paragraph ").append(wordCount).append(" of a massive AI response discussing deep philosophy. \n\n")
            wordCount += 12
        }
        val massiveText = sb.toString()
        assertTrue("Text must exceed target length", massiveText.length >= targetSize)

        val clipType = MarkdownTranspiler.detectClipType(massiveText)
        val title = MarkdownTranspiler.extractTitle(massiveText)
        val stripped = MarkdownTranspiler.stripFormatting(massiveText)

        assertEquals("Title should extract first paragraph summary", "This is paragraph 0 of a massive AI response discussing deep philosophy.", title)
        assertEquals("Stripped length should match raw text length since no tokens", massiveText.trim().length, stripped.length)
    }

    @Test
    fun testMassiveHtmlTranspilation() {
        // Test 50,000 character HTML string with hundreds of markdown elements
        val sb = StringBuilder()
        sb.append("<h1>The Architecture of Mind</h1>\n")
        for (i in 1..200) {
            sb.append("<p>Section $i contains <b>important insights</b> and <code>key_$i()</code>.</p>\n")
            sb.append("<ul><li>Point A-$i</li><li>Point B-$i with <a href=\"https://daylight.computer/$i\">Link</a></li></ul>\n")
        }
        val rawHtml = sb.toString()
        val markdown = MarkdownTranspiler.transpileHtmlToMarkdown(rawHtml)

        assertTrue(markdown.contains("# The Architecture of Mind"))
        assertTrue(markdown.contains("**important insights**"))
        assertTrue(markdown.contains("`key_1()`"))
        assertTrue(markdown.contains("- Point A-1"))
        assertTrue(markdown.contains("- Point B-1 with [Link](https://daylight.computer/1)"))
        assertTrue("Markdown must be substantially populated", markdown.length > 20_000)
    }

    @Test
    fun testHalfMillionCharacterStressWithoutCatastrophicBacktracking() {
        // Test 500,000 character payload (equivalent to full book or extensive AI chat session)
        val targetSize = 500_000
        val sb = StringBuilder(targetSize + 1000)
        var i = 0
        while (sb.length < targetSize) {
            sb.append("Chapter ").append(i).append(": SolOS LivePaper provides distraction-free computing with 90Hz peak refresh rate.\n")
            sb.append("The DC dimming is purely analog constant current drive, preventing eye fatigue.\n\n")
            i++
        }
        val hugeText = sb.toString()
        assertTrue("Text must reach 500,000 chars", hugeText.length >= targetSize)

        val startTime = System.currentTimeMillis()
        val title = MarkdownTranspiler.extractTitle(hugeText)
        val clipType = MarkdownTranspiler.detectClipType(hugeText)
        val stripped = MarkdownTranspiler.stripFormatting(hugeText)
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals("Chapter 0: SolOS LivePaper provides distraction-free computing with 90Hz peak...", title)
        assertEquals(ClipType.TEXT, clipType)
        assertTrue("Stripped text must retain content", stripped.length > 400_000)
        assertTrue("Processing 500k characters should complete in under 5 seconds", elapsed < 5000)
    }

    @Test
    fun testImeStreamingChunkBoundariesAndSurrogatePairs() {
        // Construct a string with emoji surrogate pairs placed right on boundary edges
        // High surrogate \uD83D, low surrogate \uDE00 (😀)
        val emoji = "\uD83D\uDE00"
        val padding = "123456789" // 9 chars
        // 9 chars + 2 chars of emoji = 11 chars. With chunkSize = 10, index 9 falls between the surrogate pair!
        val testString = padding + emoji + padding + emoji

        val committedChunks = mutableListOf<String>()
        val fakeIc = java.lang.reflect.Proxy.newProxyInstance(
            android.view.inputmethod.InputConnection::class.java.classLoader,
            arrayOf(android.view.inputmethod.InputConnection::class.java)
        ) { _, method, args ->
            if (method.name == "commitText") {
                committedChunks.add(args[0].toString())
                true
            } else {
                null
            }
        } as android.view.inputmethod.InputConnection

        val success = com.daylightcomputer.paste.service.DaylightInputMethodService.streamTextInChunks(
            ic = fakeIc,
            text = testString,
            chunkSize = 10
        )

        assertTrue("Streaming must succeed", success)
        assertTrue("Must have multiple chunks", committedChunks.size > 1)

        // Verify that no chunk ends with an orphaned high surrogate
        for (chunk in committedChunks) {
            if (chunk.isNotEmpty()) {
                val lastChar = chunk.last()
                assertFalse("Chunk must not end with an orphaned high surrogate", Character.isHighSurrogate(lastChar))
            }
        }

        // Verify that concatenating all chunks reproduces the exact original string
        val reconstructed = committedChunks.joinToString("")
        assertEquals("Reconstructed string must match original perfectly", testString, reconstructed)
    }

    @Test
    fun testImeStreamingGracefulFailureHandling() {
        // 1. Null InputConnection returns false without crashing
        val nullResult = com.daylightcomputer.paste.service.DaylightInputMethodService.streamTextInChunks(
            ic = null,
            text = "Testing null connection"
        )
        assertFalse("Must return false on null InputConnection", nullResult)

        // 2. InputConnection commit failure aborts loop early
        var commitCount = 0
        val failingIc = java.lang.reflect.Proxy.newProxyInstance(
            android.view.inputmethod.InputConnection::class.java.classLoader,
            arrayOf(android.view.inputmethod.InputConnection::class.java)
        ) { _, method, args ->
            if (method.name == "commitText") {
                commitCount++
                commitCount < 2 // Fail on second chunk
            } else {
                null
            }
        } as android.view.inputmethod.InputConnection

        val longText = "A".repeat(50)
        val failResult = com.daylightcomputer.paste.service.DaylightInputMethodService.streamTextInChunks(
            ic = failingIc,
            text = longText,
            chunkSize = 10
        )
        assertFalse("Must return false when commitText fails", failResult)
        assertEquals("Must abort immediately after commitText returns false", 2, commitCount)
    }
}
