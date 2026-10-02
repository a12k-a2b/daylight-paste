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

    @Test
    fun testArrayIndexingNotMutilatedByCitationStripper() {
        // Critical adversarial test: Array index [0], [1], [1, 2] must NEVER be stripped as citations
        val codeLine = "val first = arr[0]\nval second = items[1]\nval elem = matrix[1, 2]"
        val cleaned = MarkdownTranspiler.stripInlineCitations(codeLine)
        assertEquals(codeLine, cleaned)

        val htmlWithCode = "<pre><code>val x = arr[0] + items[1];</code></pre>"
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(htmlWithCode)
        assertTrue("Code block must preserve arr[0]", md.contains("arr[0]"))
        assertTrue("Code block must preserve items[1]", md.contains("items[1]"))

        val inlineHtml = "<p>Use <code>data[0]</code> to access head element.</p>"
        val mdInline = MarkdownTranspiler.transpileHtmlToMarkdown(inlineHtml)
        assertTrue("Inline code must preserve data[0]", mdInline.contains("`data[0]`"))
    }

    @Test
    fun testScriptAndStyleTagsCompletelyStripped() {
        val dirtyHtml = """
            <style>
                .solos-theme { color: #111111; background: #FAF8F5; }
            </style>
            <script type="text/javascript">
                function trackingBeacon() { fetch('/telemetry'); }
            </script>
            <p>Distraction-free LivePaper reading.</p>
        """.trimIndent()
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(dirtyHtml)
        assertTrue("Must not contain CSS styles", !md.contains("solos-theme"))
        assertTrue("Must not contain JS code", !md.contains("trackingBeacon"))
        assertEquals("Distraction-free LivePaper reading.", md.trim())
    }

    @Test
    fun testMathComparisonsAndGenericsPreserved() {
        val mathText = "In math, 3 < 5 and 7 > 2 is always true."
        // HTML transpiler must not eat '< 5 and 7 >' as an HTML tag
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(mathText)
        assertTrue("Must keep '< 5' comparison", md.contains("3 < 5"))
        assertTrue("Must keep '7 > 2' comparison", md.contains("7 > 2"))

        val genericsHtml = "<p>Return type is <code>List&lt;String&gt;</code> with generics.</p>"
        val mdGenerics = MarkdownTranspiler.transpileHtmlToMarkdown(genericsHtml)
        assertTrue("Generics must be decoded cleanly in inline code", mdGenerics.contains("`List<String>`"))
    }

    @Test
    fun testProseCitationWhitespaceCleaning() {
        val textWithSpace = "The breakthrough was confirmed [1]. Following that, another test [2] succeeded."
        val cleaned = MarkdownTranspiler.stripInlineCitations(textWithSpace)
        assertEquals("The breakthrough was confirmed. Following that, another test succeeded.", cleaned)
    }

    @Test
    fun testNestedListsIndentation() {
        val html = "<ul><li>Parent item<ul><li>Child item 1</li><li>Child item 2</li></ul></li></ul>"
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(html)
        assertTrue(md.contains("- Parent item"))
        assertTrue(md.contains("  - Child item 1"))
        assertTrue(md.contains("  - Child item 2"))
    }

    @Test
    fun testKaTeXAnnotationPreserved() {
        val html = "<p>Formula: <span class=\"katex\"><span class=\"katex-mathml\"><math><semantics><annotation encoding=\"application/x-tex\">E = mc^2</annotation></semantics></math></span></span> is famous.</p>"
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(html)
        assertTrue(md.contains("\$\$E = mc^2\$\$"))
    }

    @Test
    fun testMalformedAndDeeplyNestedHtmlFuzz() {
        // Deeply nested 100 levels div bomb
        val deepHtml = (1..100).fold("<p>Payload inside deep tree</p>") { acc, _ -> "<div>$acc</div>" }
        val md = MarkdownTranspiler.transpileHtmlToMarkdown(deepHtml)
        assertTrue(md.contains("Payload inside deep tree"))

        // Malformed unclosed tags
        val malformedHtml = "<p>Unclosed <b>bold and <i>italic and <code>code without close"
        val mdMalformed = MarkdownTranspiler.transpileHtmlToMarkdown(malformedHtml)
        assertTrue(mdMalformed.contains("**bold and"))
    }
}
