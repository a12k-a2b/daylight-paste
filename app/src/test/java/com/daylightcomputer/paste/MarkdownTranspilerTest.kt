package com.daylightcomputer.paste

import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.markdown.MarkdownTranspiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTranspilerTest {

    @Test
    fun testHeadingsAndFormatting() {
        val html = "<h1>Title</h1><p>This is <b>bold</b> and <i>italic</i> and <code>inline code</code>.</p>"
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(html)
        assertTrue(md.contains("# Title"))
        assertTrue(md.contains("**bold**"))
        assertTrue(md.contains("*italic*"))
        assertTrue(md.contains("`inline code`"))
    }

    @Test
    fun testCodeBlockWithLanguage() {
        val html = "<pre><code class=\"language-python\">def hello():\n    return \"world\"</code></pre>"
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(html)
        assertTrue(md.contains("```python"))
        assertTrue(md.contains("def hello():"))
        assertTrue(md.contains("```"))
    }

    @Test
    fun testListsAndLinks() {
        val html = "<ul><li>First item</li><li>Second with <a href=\"https://daylightcomputer.com\">Daylight Link</a></li></ul>"
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(html)
        assertTrue(md.contains("- First item"))
        assertTrue(md.contains("- Second with [Daylight Link](https://daylightcomputer.com)"))
    }

    @Test
    fun testDetectClipType() {
        assertEquals(ClipType.URL, MarkdownTranspiler.detectClipType("https://daylightcomputer.com"))
        assertEquals(ClipType.CODE, MarkdownTranspiler.detectClipType("```kotlin\nval x = 10\n```"))
        assertEquals(ClipType.MARKDOWN, MarkdownTranspiler.detectClipType("# Daylight Journal\n\n**Note** today."))
        assertEquals(ClipType.TEXT, MarkdownTranspiler.detectClipType("Just a quick reminder to buy milk."))
    }

    @Test
    fun testStripFormatting() {
        val md = "# Title\n\nThis is **very important** with `some code`."
        val stripped = MarkdownTranspiler.stripFormatting(md)
        assertEquals("Title\n\nThis is very important with some code.", stripped)
    }
}
