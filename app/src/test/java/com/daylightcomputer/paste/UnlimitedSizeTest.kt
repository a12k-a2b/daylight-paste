package com.daylightcomputer.paste

import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import org.junit.Assert.assertEquals
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
}
