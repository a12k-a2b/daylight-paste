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

    private val HTML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z][^>]*>", Pattern.DOTALL)
    private val SCRIPT_PATTERN = Pattern.compile("<script(?:\\s+[^>]*)?>.*?</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val STYLE_PATTERN = Pattern.compile("<style(?:\\s+[^>]*)?>.*?</style>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val NOSCRIPT_PATTERN = Pattern.compile("<noscript(?:\\s+[^>]*)?>.*?</noscript>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
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
    private val SUP_CITATION_PATTERN = Pattern.compile("<sup(?:\\s+[^>]*)?>\\s*(?:<a[^>]*>)?\\s*\\[?([1-9]\\d*(?:[-,]\\s*[1-9]\\d*)*|citation needed|note\\s*\\d+)\\]?\\s*(?:</a>)?\\s*</sup>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    
    // Precompiled heading patterns for h1..h6
    private val HEADING_PATTERNS = (1..6).map { i ->
        Pattern.compile("<h$i(?:\\s+[^>]*)?>(.*?)</h$i>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    }

    private val HTML_SNIFF_REGEX = Regex("<(html|body|div|p|span|a|h[1-6]|ul|ol|li|table|tr|td|th|pre|code|b|strong|i|em|br|hr)[^>]*>", RegexOption.IGNORE_CASE)

    fun looksLikeHtml(text: String): Boolean {
        return HTML_SNIFF_REGEX.containsMatchIn(text)
    }

    fun transpileHtmlToMarkdown(html: String): String {
        if (html.isBlank()) return ""
        var md = html

        // 0a. Strip non-content script, style, and noscript blocks completely
        md = SCRIPT_PATTERN.matcher(md).replaceAll("")
        md = STYLE_PATTERN.matcher(md).replaceAll("")
        md = NOSCRIPT_PATTERN.matcher(md).replaceAll("")

        // 0b. Clean web citations and footnotes (e.g. Wikipedia sup tags)
        md = SUP_CITATION_PATTERN.matcher(md).replaceAll("")

        // Vault away code blocks and inline code so they are protected from all subsequent transforms
        val codeBlocks = mutableListOf<String>()
        val inlineCodes = mutableListOf<String>()

        // 1. Code blocks (<pre><code>)
        val codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(md)
        val sbCode = StringBuffer()
        while (codeBlockMatcher.find()) {
            val lang = codeBlockMatcher.group(1)?.trim() ?: ""
            val rawInside = codeBlockMatcher.group(2) ?: ""
            val cleanCode = cleanCodeBlockContent(rawInside)
            val placeholder = "%%%DAYLIGHT_CODE_BLOCK_${codeBlocks.size}%%%"
            codeBlocks.add("```$lang\n$cleanCode\n```")
            codeBlockMatcher.appendReplacement(sbCode, java.util.regex.Matcher.quoteReplacement("\n\n$placeholder\n\n"))
        }
        codeBlockMatcher.appendTail(sbCode)
        md = sbCode.toString()

        // 2. Standalone <pre>
        val preMatcher = PRE_PATTERN.matcher(md)
        val sbPre = StringBuffer()
        while (preMatcher.find()) {
            val rawInside = preMatcher.group(1) ?: ""
            val cleanCode = cleanCodeBlockContent(rawInside)
            val placeholder = "%%%DAYLIGHT_CODE_BLOCK_${codeBlocks.size}%%%"
            codeBlocks.add("```\n$cleanCode\n```")
            preMatcher.appendReplacement(sbPre, java.util.regex.Matcher.quoteReplacement("\n\n$placeholder\n\n"))
        }
        preMatcher.appendTail(sbPre)
        md = sbPre.toString()

        // 2b. Inline code <code>...</code>
        val inlineCodeMatcher = INLINE_CODE_PATTERN.matcher(md)
        val sbInline = StringBuffer()
        while (inlineCodeMatcher.find()) {
            val raw = inlineCodeMatcher.group(1) ?: ""
            val clean = decodeHtmlEntities(raw.replace(Regex("<[^>]+>"), "")).trim()
            val placeholder = "%%%DAYLIGHT_INLINE_CODE_${inlineCodes.size}%%%"
            inlineCodes.add("`$clean`")
            inlineCodeMatcher.appendReplacement(sbInline, java.util.regex.Matcher.quoteReplacement(placeholder))
        }
        inlineCodeMatcher.appendTail(sbInline)
        md = sbInline.toString()

        // 3. Tables (convert <table>...</table> to GFM Markdown table)
        md = transpileTables(md)

        // 4. Headings (h1 to h6) using precompiled patterns
        for (i in 1..6) {
            val hPattern = HEADING_PATTERNS[i - 1]
            val hMatcher = hPattern.matcher(md)
            val sbH = StringBuffer()
            val prefix = "#".repeat(i) + " "
            while (hMatcher.find()) {
                val content = hMatcher.group(1)?.trim() ?: ""
                val replacement = "\n\n$prefix$content\n\n"
                hMatcher.appendReplacement(sbH, java.util.regex.Matcher.quoteReplacement(replacement))
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
            bqMatcher.appendReplacement(sbBq, java.util.regex.Matcher.quoteReplacement(replacement))
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
            olMatcher.appendReplacement(sbOl, java.util.regex.Matcher.quoteReplacement(replacement))
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
            ulMatcher.appendReplacement(sbUl, java.util.regex.Matcher.quoteReplacement(replacement))
        }
        ulMatcher.appendTail(sbUl)
        md = sbUl.toString()

        // Loose <li> tags outside of ul/ol
        val looseLiMatcher = LI_PATTERN.matcher(md)
        val sbLooseLi = StringBuffer()
        while (looseLiMatcher.find()) {
            val item = looseLiMatcher.group(1)?.trim() ?: ""
            looseLiMatcher.appendReplacement(sbLooseLi, java.util.regex.Matcher.quoteReplacement("\n- $item"))
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
            imgMatcher.appendReplacement(sbImg, java.util.regex.Matcher.quoteReplacement(replacement))
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
            linkMatcher.appendReplacement(sbLink, java.util.regex.Matcher.quoteReplacement(replacement))
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

        // 12. Paragraphs and Linebreaks
        md = md.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("<p(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE), "\n\n")
        md = md.replace(Regex("</p>", RegexOption.IGNORE_CASE), "")
        md = md.replace(Regex("<div(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("</div>", RegexOption.IGNORE_CASE), "")

        // 13. Strip any remaining HTML tags safely (matching real HTML tag names starting with a letter or /)
        md = HTML_TAG_PATTERN.matcher(md).replaceAll("")

        // 14. Decode remaining HTML entities
        md = decodeHtmlEntities(md)

        // 15. Strip remaining Wikipedia-style prose citations (code blocks/inline codes are vaulted)
        md = stripInlineCitations(md)

        // 16. Restore vaulted inline codes and code blocks
        inlineCodes.forEachIndexed { index, code ->
            md = md.replace("%%%DAYLIGHT_INLINE_CODE_${index}%%%", code)
        }
        codeBlocks.forEachIndexed { index, block ->
            md = md.replace("%%%DAYLIGHT_CODE_BLOCK_${index}%%%", block)
        }

        // 17. Normalize excessive whitespace & blank lines
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

            tableMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(mdTable.toString()))
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
     * Strips bracketed prose citation markers e.g. [1], [42], [1, 2], [1-3], [citation needed]
     * while strictly preserving:
     * - Markdown links [text](url)
     * - Task list checkboxes [ ], [x]
     * - Programming array/index accesses (e.g. arr[0], items[1])
     * - Fenced or inline code blocks (vaulted or delimited with backticks)
     */
    fun stripInlineCitations(text: String): String {
        // Citations in prose are:
        // 1. Bracketed non-zero digits e.g. [1], [2], [1, 2], [1-3] or [citation needed] or [note 1]
        // 2. NOT immediately followed by ( (which would be a markdown link)
        // 3. NOT an array access (i.e. not immediately preceded by an identifier character [a-zA-Z0-9_])
        // 4. Citation numbers start at 1 (not 0, which is universally code index)
        // 5. Handles optional leading space so "word [1]." cleans cleanly to "word."
        val citationWithLeadingSpace = Pattern.compile("(?<![a-zA-Z0-9_\\[\\!])\\s*\\[([1-9]\\d*(?:[-,]\\s*[1-9]\\d*)*|citation needed|note\\s*\\d+)\\](?!\\()", Pattern.CASE_INSENSITIVE)
        
        // Clean citations that have backtick-delimited code on lines:
        // We only strip citations outside backtick segments
        val lines = text.split("\n")
        val processedLines = lines.map { line ->
            if (!line.contains("`")) {
                citationWithLeadingSpace.matcher(line).replaceAll("")
            } else {
                // Split line by backticks: even index = outside code, odd index = inside code
                val parts = line.split("`")
                val sb = StringBuilder()
                for (i in parts.indices) {
                    if (i % 2 == 0) {
                        sb.append(citationWithLeadingSpace.matcher(parts[i]).replaceAll(""))
                    } else {
                        sb.append("`").append(parts[i]).append("`")
                    }
                }
                sb.toString()
            }
        }
        return processedLines.joinToString("\n")
    }

    private fun replaceWithPattern(input: String, pattern: Pattern, transform: (String) -> String): String {
        val matcher = pattern.matcher(input)
        val sb = StringBuffer()
        while (matcher.find()) {
            val content = matcher.group(1) ?: ""
            val replacement = transform(content)
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
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

        if (hasMarkdownTokens || (!rawHtml.isNullOrBlank() && looksLikeHtml(rawHtml))) {
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

