package com.daylightcomputer.paste.markdown

import java.util.regex.Pattern

enum class ClipType {
    MARKDOWN,
    CODE,
    URL,
    TEXT,
    IMAGE
}

/**
 * Daylight Markdown Transpiler & Content Normalizer.
 * Reconstructs clean CommonMark / GitHub Flavored Markdown from HTML fragments,
 * rich text DOM trees, strips web artifacts (citations), and extracts structured metadata.
 */
object MarkdownTranspiler {

    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]+>", Pattern.DOTALL)
    private val CODE_BLOCK_PATTERN = Pattern.compile("<pre(?:\\s+[^>]*)?>\\s*<code(?:\\s+class=[\"'](?:language-)?([a-zA-Z0-9_-]+)[\"'])?(?:\\s+[^>]*)?>(.*?)</code>\\s*</pre>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val PRE_PATTERN = Pattern.compile("<pre(?:\\s+[^>]*)?>(.*?)</pre>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val TABLE_PATTERN = Pattern.compile("<table(?:\\s+[^>]*)?>(.*?)</table>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val TR_PATTERN = Pattern.compile("<tr(?:\\s+[^>]*)?>(.*?)</tr>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val TH_OR_TD_PATTERN = Pattern.compile("<(th|td)(?:\\s+[^>]*)?>(.*?)</\\1>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val INLINE_CODE_PATTERN = Pattern.compile("<code(?:\\s+[^>]*)?>(.*?)</code>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val LINK_PATTERN = Pattern.compile("<a(?:\\s+[^>]*)?\\s+href=[\"']([^\"']+)[\"'](?:\\s+[^>]*)?>(.*?)</a>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val IMG_PATTERN = Pattern.compile("<img(?:\\s+[^>]*)?\\s+src=[\"']([^\"']+)[\"'](?:\\s+alt=[\"']([^\"']*)[\"'])?[^>]*>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val BOLD_PATTERN = Pattern.compile("<(?:b|strong)(?:\\s+[^>]*)?>(.*?)</(?:b|strong)>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val ITALIC_PATTERN = Pattern.compile("<(?:i|em)(?:\\s+[^>]*)?>(.*?)</(?:i|em)>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val STRIKE_PATTERN = Pattern.compile("<(?:s|strike|del)(?:\\s+[^>]*)?>(.*?)</(?:s|strike|del)>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val BLOCKQUOTE_PATTERN = Pattern.compile("<blockquote(?:\\s+[^>]*)?>(.*?)</blockquote>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val OL_PATTERN = Pattern.compile("<ol(?:\\s+[^>]*)?>(.*?)</ol>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val UL_PATTERN = Pattern.compile("<ul(?:\\s+[^>]*)?>(.*?)</ul>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val LI_PATTERN = Pattern.compile("<li(?:\\s+[^>]*)?>(.*?)</li>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val SUP_CITATION_PATTERN = Pattern.compile("<sup(?:\\s+[^>]*)?>\\s*(?:<a[^>]*>)?\\s*\\[?(\\d+|citation needed|note\\s*\\d+)\\]?\\s*(?:</a>)?\\s*</sup>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

    fun transpileHtmlToMarkdown(html: String): String {
        if (html.isBlank()) return ""
        var md = html

        // 0. Clean web citations and footnotes (e.g., Wikipedia sup tags)
        md = SUP_CITATION_PATTERN.matcher(md).replaceAll("")

        // 1. Code blocks (<pre><code>)
        val codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(md)
        val sbCode = StringBuffer()
        while (codeBlockMatcher.find()) {
            val lang = codeBlockMatcher.group(1)?.trim() ?: ""
            val rawInside = codeBlockMatcher.group(2) ?: ""
            val cleanCode = cleanCodeBlockContent(rawInside)
            val replacement = "\n\n```$lang\n$cleanCode\n```\n\n"
            codeBlockMatcher.appendReplacement(sbCode, MatcherUtil.quoteReplacement(replacement))
        }
        codeBlockMatcher.appendTail(sbCode)
        md = sbCode.toString()

        // 2. Standalone <pre>
        val preMatcher = PRE_PATTERN.matcher(md)
        val sbPre = StringBuffer()
        while (preMatcher.find()) {
            val rawInside = preMatcher.group(1) ?: ""
            val cleanCode = cleanCodeBlockContent(rawInside)
            val replacement = "\n\n```\n$cleanCode\n```\n\n"
            preMatcher.appendReplacement(sbPre, MatcherUtil.quoteReplacement(replacement))
        }
        preMatcher.appendTail(sbPre)
        md = sbPre.toString()

        // 3. Tables (convert <table>...</table> to GFM Markdown table)
        md = transpileTables(md)

        // 4. Headings (h1 to h6)
        for (i in 1..6) {
            val hPattern = Pattern.compile("<h$i(?:\\s+[^>]*)?>(.*?)</h$i>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val hMatcher = hPattern.matcher(md)
            val sbH = StringBuffer()
            val prefix = "#".repeat(i) + " "
            while (hMatcher.find()) {
                val content = hMatcher.group(1)?.trim() ?: ""
                val replacement = "\n\n$prefix$content\n\n"
                hMatcher.appendReplacement(sbH, MatcherUtil.quoteReplacement(replacement))
            }
            hMatcher.appendTail(sbH)
            md = sbH.toString()
        }

        // 5. Horizontal Rules
        md = md.replace(Regex("<hr\\s*/?>", RegexOption.IGNORE_CASE), "\n\n---\n\n")

        // 6. Blockquotes
        val bqMatcher = BLOCKQUOTE_PATTERN.matcher(md)
        val sbBq = StringBuffer()
        while (bqMatcher.find()) {
            val content = bqMatcher.group(1)?.trim() ?: ""
            val lines = content.lines().joinToString("\n") { line -> "> " + line.trim() }
            val replacement = "\n\n$lines\n\n"
            bqMatcher.appendReplacement(sbBq, MatcherUtil.quoteReplacement(replacement))
        }
        bqMatcher.appendTail(sbBq)
        md = sbBq.toString()

        // 7. Ordered Lists (<ol>)
        val olMatcher = OL_PATTERN.matcher(md)
        val sbOl = StringBuffer()
        while (olMatcher.find()) {
            val olBody = olMatcher.group(1) ?: ""
            val liMatcher = LI_PATTERN.matcher(olBody)
            val listItems = mutableListOf<String>()
            var idx = 1
            while (liMatcher.find()) {
                val item = liMatcher.group(1)?.trim() ?: ""
                listItems.add("$idx. $item")
                idx++
            }
            val replacement = "\n\n" + listItems.joinToString("\n") + "\n\n"
            olMatcher.appendReplacement(sbOl, MatcherUtil.quoteReplacement(replacement))
        }
        olMatcher.appendTail(sbOl)
        md = sbOl.toString()

        // 8. Unordered Lists (<ul>)
        val ulMatcher = UL_PATTERN.matcher(md)
        val sbUl = StringBuffer()
        while (ulMatcher.find()) {
            val ulBody = ulMatcher.group(1) ?: ""
            val liMatcher = LI_PATTERN.matcher(ulBody)
            val listItems = mutableListOf<String>()
            while (liMatcher.find()) {
                val item = liMatcher.group(1)?.trim() ?: ""
                listItems.add("- $item")
            }
            val replacement = "\n\n" + listItems.joinToString("\n") + "\n\n"
            ulMatcher.appendReplacement(sbUl, MatcherUtil.quoteReplacement(replacement))
        }
        ulMatcher.appendTail(sbUl)
        md = sbUl.toString()

        // Loose <li> tags outside of ul/ol
        val looseLiMatcher = LI_PATTERN.matcher(md)
        val sbLooseLi = StringBuffer()
        while (looseLiMatcher.find()) {
            val item = looseLiMatcher.group(1)?.trim() ?: ""
            looseLiMatcher.appendReplacement(sbLooseLi, MatcherUtil.quoteReplacement("\n- $item"))
        }
        looseLiMatcher.appendTail(sbLooseLi)
        md = sbLooseLi.toString()

        // 9. Images (<img src="..." alt="..." />)
        val imgMatcher = IMG_PATTERN.matcher(md)
        val sbImg = StringBuffer()
        while (imgMatcher.find()) {
            val src = imgMatcher.group(1)?.trim() ?: ""
            val alt = imgMatcher.group(2)?.trim() ?: "Image"
            val replacement = "![$alt]($src)"
            imgMatcher.appendReplacement(sbImg, MatcherUtil.quoteReplacement(replacement))
        }
        imgMatcher.appendTail(sbImg)
        md = sbImg.toString()

        // 10. Links (<a href="...">...</a>)
        val linkMatcher = LINK_PATTERN.matcher(md)
        val sbLink = StringBuffer()
        while (linkMatcher.find()) {
            val url = linkMatcher.group(1)?.trim() ?: ""
            val label = linkMatcher.group(2)?.trim() ?: url
            val replacement = if (label.isNotBlank()) "[$label]($url)" else url
            linkMatcher.appendReplacement(sbLink, MatcherUtil.quoteReplacement(replacement))
        }
        linkMatcher.appendTail(sbLink)
        md = sbLink.toString()

        // 11. Inline Formatting
        // Bold
        md = replaceWithPattern(md, BOLD_PATTERN) {
            val t = it.trim()
            if (t.isNotEmpty()) "**$t**" else ""
        }
        // Italic
        md = replaceWithPattern(md, ITALIC_PATTERN) {
            val t = it.trim()
            if (t.isNotEmpty()) "*$t*" else ""
        }
        // Strike
        md = replaceWithPattern(md, STRIKE_PATTERN) {
            val t = it.trim()
            if (t.isNotEmpty()) "~~$t~~" else ""
        }
        // Inline code
        md = replaceWithPattern(md, INLINE_CODE_PATTERN) {
            "`" + decodeHtmlEntities(it).trim() + "`"
        }

        // 12. Paragraphs and Linebreaks
        md = md.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("<p(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE), "\n\n")
        md = md.replace(Regex("</p>", RegexOption.IGNORE_CASE), "")
        md = md.replace(Regex("<div(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("</div>", RegexOption.IGNORE_CASE), "")

        // 13. Strip any remaining unknown HTML tags
        md = HTML_TAG_PATTERN.matcher(md).replaceAll("")

        // 14. Decode remaining HTML entities
        md = decodeHtmlEntities(md)

        // 15. Strip remaining Wikipedia-style inline citations like [1], [2], [1, 2] that are not markdown links or checkboxes
        md = stripInlineCitations(md)

        // 16. Normalize excessive whitespace & blank lines
        md = md.replace(Regex("\n{3,}"), "\n\n").trim()

        return md
    }

    private fun cleanCodeBlockContent(raw: String): String {
        // Strip syntax highlighting spans e.g. <span class="hljs-keyword">
        val noSpans = raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        return decodeHtmlEntities(noSpans).trim()
    }

    private fun transpileTables(html: String): String {
        val tableMatcher = TABLE_PATTERN.matcher(html)
        val sb = StringBuffer()
        while (tableMatcher.find()) {
            val tableBody = tableMatcher.group(1) ?: ""
            val rows = mutableListOf<List<String>>()

            val trMatcher = TR_PATTERN.matcher(tableBody)
            while (trMatcher.find()) {
                val trContent = trMatcher.group(1) ?: ""
                val cellMatcher = TH_OR_TD_PATTERN.matcher(trContent)
                val rowCells = mutableListOf<String>()
                while (cellMatcher.find()) {
                    val rawCell = cellMatcher.group(2) ?: ""
                    // Quick inline cleaning for cells
                    val cleanCell = transpileInlineTags(rawCell)
                        .replace("\n", " ")
                        .replace("|", "\\|")
                        .trim()
                    rowCells.add(cleanCell)
                }
                if (rowCells.isNotEmpty()) {
                    rows.add(rowCells)
                }
            }

            if (rows.isEmpty()) {
                tableMatcher.appendReplacement(sb, "")
                continue
            }

            val maxCols = rows.maxOf { it.size }
            val mdTable = StringBuilder("\n\n")

            // First row as header
            val headerRow = rows[0]
            val paddedHeader = (0 until maxCols).map { col ->
                headerRow.getOrElse(col) { "" }
            }
            mdTable.append("| ").append(paddedHeader.joinToString(" | ")).append(" |\n")

            // Separator row
            val separator = (0 until maxCols).map { "---" }
            mdTable.append("| ").append(separator.joinToString(" | ")).append(" |\n")

            // Data rows (from row 1 onwards)
            for (r in 1 until rows.size) {
                val row = rows[r]
                val paddedRow = (0 until maxCols).map { col ->
                    row.getOrElse(col) { "" }
                }
                mdTable.append("| ").append(paddedRow.joinToString(" | ")).append(" |\n")
            }
            mdTable.append("\n")

            tableMatcher.appendReplacement(sb, MatcherUtil.quoteReplacement(mdTable.toString()))
        }
        tableMatcher.appendTail(sb)
        return sb.toString()
    }

    private fun transpileInlineTags(input: String): String {
        var s = input
        s = replaceWithPattern(s, BOLD_PATTERN) { "**${it.trim()}**" }
        s = replaceWithPattern(s, ITALIC_PATTERN) { "*${it.trim()}*" }
        s = replaceWithPattern(s, INLINE_CODE_PATTERN) { "`" + decodeHtmlEntities(it).trim() + "`" }
        s = HTML_TAG_PATTERN.matcher(s).replaceAll("")
        return decodeHtmlEntities(s)
    }

    /**
     * Strips bracketed citation markers e.g. [1], [2], [1, 2], [1-3], [citation needed]
     * while preserving markdown links [text](url), task list checkboxes [ ], [x], and array indexes.
     */
    fun stripInlineCitations(text: String): String {
        // Matches [1], [42], [1, 2], [1-3], [citation needed], [note 1] when NOT immediately followed by (
        // and NOT a markdown task checkbox [ ] or [x]
        val citationPattern = Pattern.compile("(?<!\\[)(?<!\\!)\\[(\\d+(?:[-,]\\s*\\d+)*|citation needed|note\\s*\\d+)\\](?!\\()", Pattern.CASE_INSENSITIVE)
        return citationPattern.matcher(text).replaceAll("")
    }

    private fun replaceWithPattern(input: String, pattern: Pattern, transform: (String) -> String): String {
        val matcher = pattern.matcher(input)
        val sb = StringBuffer()
        while (matcher.find()) {
            val content = matcher.group(1) ?: ""
            val replacement = transform(content)
            matcher.appendReplacement(sb, MatcherUtil.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#x2F;", "/")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
    }

    fun stripFormatting(text: String): String {
        var s = text
        // Remove code blocks
        s = s.replace(Regex("```[a-zA-Z0-9_-]*\n?"), "")
        // Remove inline code
        s = s.replace(Regex("`([^`]+)`"), "$1")
        // Remove bold/italics
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        s = s.replace(Regex("\\*([^*]+)\\*"), "$1")
        s = s.replace(Regex("~~([^~]+)~~"), "$1")
        // Remove links [text](url) -> text
        s = s.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
        // Remove images ![alt](url) -> alt
        s = s.replace(Regex("!\\[([^\\]]*)\\]\\([^\\)]+\\)"), "$1")
        // Remove headings #
        s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        // Remove blockquotes >
        s = s.replace(Regex("^>\\s+", RegexOption.MULTILINE), "")
        // Remove list bullets & numbers
        s = s.replace(Regex("^[\\*\\-\\+]\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
        // Remove table pipes
        s = s.replace(Regex("^\\|\\s*", RegexOption.MULTILINE), "")
        s = s.replace(Regex("\\s*\\|\\s*", RegexOption.MULTILINE), " ")
        s = s.replace(Regex("^---.*", RegexOption.MULTILINE), "")
        return s.trim()
    }

    fun detectClipType(text: String, rawHtml: String? = null): ClipType {
        val trimmed = text.trim()
        
        // Check if URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            if (!trimmed.contains(" ") && trimmed.length < 2048) {
                return ClipType.URL
            }
        }

        // Check if Code block or programming snippet
        val isCode = trimmed.startsWith("```") ||
                trimmed.startsWith("SELECT ") || trimmed.startsWith("CREATE TABLE ") ||
                trimmed.startsWith("curl ") || trimmed.startsWith("adb ") ||
                trimmed.startsWith("#!/") || trimmed.startsWith("git ") ||
                trimmed.startsWith("docker ") || trimmed.startsWith("npm ") ||
                (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.contains("\":")) ||
                (trimmed.startsWith("<!DOCTYPE html") || trimmed.startsWith("<html")) ||
                (trimmed.contains("fun ") && trimmed.contains("(") && trimmed.contains(")")) ||
                (trimmed.contains("def ") && trimmed.contains(":") && (trimmed.contains("return ") || trimmed.contains("import "))) ||
                (trimmed.contains("function ") && trimmed.contains("{") && trimmed.contains("}")) ||
                (trimmed.contains("class ") && trimmed.contains("{") && trimmed.contains("}")) ||
                (trimmed.contains("public static void main")) ||
                (trimmed.contains("import ") && (trimmed.contains("from ") || trimmed.contains(";")))

        if (isCode) {
            return ClipType.CODE
        }

        // Check if Markdown
        val hasMarkdownTokens = trimmed.contains("# ") || trimmed.contains("## ") ||
                trimmed.contains("**") || trimmed.contains("`") ||
                trimmed.contains("- [ ]") || trimmed.contains("- [x]") ||
                (trimmed.contains("[") && trimmed.contains("](") && trimmed.contains(")")) ||
                (trimmed.contains("\n- ") || trimmed.contains("\n* ")) ||
                (trimmed.contains("| ") && trimmed.contains(" |") && trimmed.contains("---"))

        if (hasMarkdownTokens || (!rawHtml.isNullOrBlank() && rawHtml.contains("<"))) {
            return ClipType.MARKDOWN
        }

        return ClipType.TEXT
    }

    fun extractTitle(text: String): String {
        val firstLine = text.trim().lines().firstOrNull { it.isNotBlank() } ?: "Clipping"
        val clean = firstLine.replace(Regex("^#{1,6}\\s+"), "")
            .replace(Regex("^\\-\\s+"), "")
            .replace(Regex("^\\d+\\.\\s+"), "")
            .replace(Regex("^>\\s+"), "")
            .replace(Regex("\\*\\*"), "")
            .replace(Regex("^\\|\\s*"), "")
            .trim()
        return if (clean.length > 80) clean.take(77).trimEnd() + "..." else clean
    }
}

/**
 * Matcher replacement quoting helper compatible across Android versions.
 */
object MatcherUtil {
    fun quoteReplacement(s: String): String {
        if (!s.contains('\\') && !s.contains('$')) {
            return s
        }
        val sb = StringBuilder()
        for (c in s) {
            if (c == '\\' || c == '$') {
                sb.append('\\')
            }
            sb.append(c)
        }
        return sb.toString()
    }
}
