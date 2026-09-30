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
    fun testTableTranspilation() {
        val html = """
            <table>
                <thead>
                    <tr><th>Feature</th><th>SolOS</th><th>Stock Android</th></tr>
                </thead>
                <tbody>
                    <tr><td><b>Refresh Rate</b></td><td>45-90Hz VRR</td><td>Fixed 60Hz</td></tr>
                    <tr><td>Dimming</td><td>Pure DC</td><td>PWM strobing</td></tr>
                </tbody>
            </table>
        """.trimIndent()
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(html)
        assertTrue("Must contain Feature header", md.contains("| Feature | SolOS | Stock Android |"))
        assertTrue("Must contain separator", md.contains("| --- | --- | --- |"))
        assertTrue("Must contain formatted row", md.contains("| **Refresh Rate** | 45-90Hz VRR | Fixed 60Hz |"))
        assertTrue("Must contain second row", md.contains("| Dimming | Pure DC | PWM strobing |"))
    }

    @Test
    fun testTableWithoutThTags() {
        val html = "<table><tr><td>Col A</td><td>Col B</td></tr><tr><td>Val 1</td><td>Val 2</td></tr></table>"
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(html)
        assertTrue(md.contains("| Col A | Col B |"))
        assertTrue(md.contains("| --- | --- |"))
        assertTrue(md.contains("| Val 1 | Val 2 |"))
    }

    @Test
    fun testCitationStripping() {
        val textWithCitations = "Daylight DC-1 is a revolutionary computer[1] with a LivePaper display[2][3] and pure DC dimming[citation needed]."
        val cleaned = MarkdownTranspiler.stripInlineCitations(textWithCitations)
        assertEquals("Daylight DC-1 is a revolutionary computer with a LivePaper display and pure DC dimming.", cleaned)

        val htmlWithSupCitations = "<p>The theory of relativity<sup><a href=\"#cite_note-1\">[1]</a></sup> explains gravity<sup>[2]</sup>.</p>"
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(htmlWithSupCitations)
        assertEquals("The theory of relativity explains gravity.", md.trim())
    }

    @Test
    fun testCitationStrippingPreservesMarkdownLinksAndCheckboxes() {
        val mdWithLinksAndTasks = "- [ ] Task 1\n- [x] Completed task\nRead the [SolOS Design Guide](https://daylightcomputer.com) for details."
        val cleaned = MarkdownTranspiler.stripInlineCitations(mdWithLinksAndTasks)
        assertEquals(mdWithLinksAndTasks, cleaned)
    }

    @Test
    fun testCodeBlockSyntaxCleanup() {
        val syntaxHighlightedHtml = """
            <pre><code class="language-python">
            <span class="hljs-keyword">def</span> <span class="hljs-title function_">render_frame</span>():<br>
                <span class="hljs-keyword">return</span> <span class="hljs-string">"LivePaper 90Hz"</span>
            </code></pre>
        """.trimIndent()
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(syntaxHighlightedHtml)
        assertTrue(md.contains("```python"))
        assertTrue(md.contains("def render_frame():"))
        assertTrue(md.contains("return \"LivePaper 90Hz\""))
        assertTrue("Must not contain span tags", !md.contains("<span"))
    }

    @Test
    fun testOrderedAndUnorderedLists() {
        val olHtml = "<ol><li>First step</li><li>Second step</li><li>Third step</li></ol>"
        val olMd = MarkdownTranspiler.transpileHtmlToMarkdown(olHtml)
        assertTrue(olMd.contains("1. First step"))
        assertTrue(olMd.contains("2. Second step"))
        assertTrue(olMd.contains("3. Third step"))

        val ulHtml = "<ul><li>Item Alpha</li><li>Item Beta</li></ul>"
        val ulMd = MarkdownTranspiler.transpileHtmlToMarkdown(ulHtml)
        assertTrue(ulMd.contains("- Item Alpha"))
        assertTrue(ulMd.contains("- Item Beta"))
    }

    @Test
    fun testExpandedCodeDetection() {
        val kotlinSnippet = "fun calculateBudget(ms: Double): Boolean { return ms <= 11.1 }"
        assertEquals(ClipType.CODE, MarkdownTranspiler.detectClipType(kotlinSnippet))

        val pythonSnippet = "def solve_puzzle(grid):\n    import sys\n    return True"
        assertEquals(ClipType.CODE, MarkdownTranspiler.detectClipType(pythonSnippet))

        val sqlSnippet = "SELECT id, title, created_at FROM clips WHERE is_pinned = 1;"
        assertEquals(ClipType.CODE, MarkdownTranspiler.detectClipType(sqlSnippet))

        val bashSnippet = "adb shell cmd appops set com.daylightcomputer.paste SYSTEM_ALERT_WINDOW allow"
        assertEquals(ClipType.CODE, MarkdownTranspiler.detectClipType(bashSnippet))
    }

    @Test
    fun testStripFormatting() {
        val md = "# Title\n\nThis is **very important** with `some code` and [Link](https://daylightcomputer.com)."
        val stripped = MarkdownTranspiler.stripFormatting(md)
        assertEquals("Title\n\nThis is very important with some code and Link.", stripped)
    }
}
