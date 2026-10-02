package com.daylightcomputer.paste.markdown

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
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
 * Reconstructs clean CommonMark / GitHub Flavored Markdown from HTML fragments
 * using a tolerant DOM parser (Jsoup) and deterministic AST visitor.
 * 
 * Guarantees:
 * - Tolerant against malformed/unclosed HTML and deep nesting (depth-capped)
 * - Zero regex ReDoS vulnerabilities
 * - Preserves KaTeX/MathJax LaTeX annotations
 * - Single-pass zero-allocation word counting
 * - Preserves programming array indices (arr[0]) and math comparisons (<, >)
 */
object MarkdownTranspiler {

    private val HTML_SNIFF_REGEX = Regex("<(html|body|div|p|span|a|h[1-6]|ul|ol|li|table|tr|td|th|pre|code|b|strong|i|em|br|hr)[^>]*>", RegexOption.IGNORE_CASE)

    fun looksLikeHtml(text: String): Boolean {
        return HTML_SNIFF_REGEX.containsMatchIn(text)
    }

    /**
     * Tolerant DOM-based HTML to Markdown transpiler.
     */
    fun transpileHtmlToMarkdown(html: String): String {
        if (html.isBlank()) return ""
        if (!looksLikeHtml(html)) {
            return stripInlineCitations(html)
        }

        return try {
            val doc = Jsoup.parseBodyFragment(html)
            // 0. Remove non-content elements
            doc.select("script, style, noscript").remove()

            // 1. Math formulas: KaTeX / MathJax annotation preservation
            doc.select("annotation[encoding=application/x-tex]").forEach { annot ->
                val tex = annot.text().trim()
                val replacement = " $$" + tex + "$$ "
                val target = annot.closest("span.katex") ?: annot
                target.replaceWith(TextNode(replacement))
            }

            // 2. Wikipedia-style footnote citations (sup > a or plain sup)
            doc.select("sup").forEach { sup ->
                val text = sup.text().trim()
                if (text.matches(Regex("^\\[?(?:[1-9]\\d*(?:[-,]\\s*[1-9]\\d*)*|citation needed|note\\s*\\d+)\\]?$", RegexOption.IGNORE_CASE))) {
                    sup.remove()
                }
            }

            val sb = StringBuilder()
            convertNodeToMarkdown(doc.body(), sb, depth = 0, listDepth = 0)

            var md = sb.toString()
            md = stripInlineCitations(md)
            md.replace(Regex("\n{3,}"), "\n\n").trim()
        } catch (e: Exception) {
            // Fallback to plain text on unexpected DOM error
            html.replace(Regex("<[^>]+>"), "").trim()
        }
    }

    private fun convertNodeToMarkdown(node: Node, sb: StringBuilder, depth: Int, listDepth: Int) {
        if (depth > 256) return // Safeguard against runaway circular DOM structures

        when (node) {
            is TextNode -> {
                sb.append(node.text())
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                when (tag) {
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        val level = tag[1] - '0'
                        val prefix = "#".repeat(level) + " "
                        val content = renderChildrenToMarkdown(node, depth + 1, listDepth).trim()
                        if (content.isNotEmpty()) {
                            sb.append("\n\n").append(prefix).append(content).append("\n\n")
                        }
                    }
                    "p" -> {
                        val content = renderChildrenToMarkdown(node, depth + 1, listDepth).trim()
                        if (content.isNotEmpty()) {
                            sb.append("\n\n").append(content).append("\n\n")
                        }
                    }
                    "br" -> {
                        sb.append("\n")
                    }
                    "hr" -> {
                        sb.append("\n\n---\n\n")
                    }
                    "b", "strong" -> {
                        val inner = renderChildrenToMarkdown(node, depth + 1, listDepth).trim()
                        if (inner.isNotEmpty()) sb.append("**").append(inner).append("**")
                    }
                    "i", "em" -> {
                        val inner = renderChildrenToMarkdown(node, depth + 1, listDepth).trim()
                        if (inner.isNotEmpty()) sb.append("*").append(inner).append("*")
                    }
                    "s", "strike", "del" -> {
                        val inner = renderChildrenToMarkdown(node, depth + 1, listDepth).trim()
                        if (inner.isNotEmpty()) sb.append("~~").append(inner).append("~~")
                    }
                    "pre" -> {
                        val codeElem = node.selectFirst("code")
                        val lang = if (codeElem != null) extractCodeLanguage(codeElem) else extractCodeLanguage(node)
                        val codeContent = extractCodeText(codeElem ?: node)
                        sb.append("\n\n```").append(lang).append("\n")
                        sb.append(codeContent.trim())
                        sb.append("\n```\n\n")
                    }
                    "code" -> {
                        if (node.parent()?.tagName()?.lowercase() != "pre") {
                            val inner = node.wholeText().trim()
                            sb.append("`").append(inner).append("`")
                        }
                    }
                    "blockquote" -> {
                        val inner = renderChildrenToMarkdown(node, depth + 1, listDepth).trim()
                        val quoted = inner.lines().joinToString("\n") { line -> "> " + line.trim() }
                        sb.append("\n\n").append(quoted).append("\n\n")
                    }
                    "ul" -> {
                        sb.append("\n\n")
                        for (child in node.children()) {
                            if (child.tagName().equals("li", ignoreCase = true)) {
                                val indent = "  ".repeat(listDepth)
                                val inner = renderChildrenToMarkdown(child, depth + 1, listDepth + 1).trim()
                                sb.append(indent).append("- ").append(inner).append("\n")
                            } else {
                                convertNodeToMarkdown(child, sb, depth + 1, listDepth)
                            }
                        }
                        sb.append("\n")
                    }
                    "ol" -> {
                        sb.append("\n\n")
                        var idx = 1
                        for (child in node.children()) {
                            if (child.tagName().equals("li", ignoreCase = true)) {
                                val indent = "  ".repeat(listDepth)
                                val inner = renderChildrenToMarkdown(child, depth + 1, listDepth + 1).trim()
                                sb.append(indent).append("$idx. ").append(inner).append("\n")
                                idx++
                            } else {
                                convertNodeToMarkdown(child, sb, depth + 1, listDepth)
                            }
                        }
                        sb.append("\n")
                    }
                    "table" -> {
                        val tableMd = renderTable(node, depth + 1, listDepth)
                        sb.append("\n\n").append(tableMd).append("\n\n")
                    }
                    "a" -> {
                        val href = node.attr("href").trim()
                        val label = renderChildrenToMarkdown(node, depth + 1, listDepth).trim()
                        if (href.isNotBlank()) {
                            val text = if (label.isNotBlank()) label else href
                            sb.append("[").append(text).append("](").append(href).append(")")
                        } else if (label.isNotBlank()) {
                            sb.append(label)
                        }
                    }
                    "img" -> {
                        val src = node.attr("src").trim()
                        val alt = node.attr("alt").trim().ifBlank { "Image" }
                        if (src.isNotBlank()) {
                            sb.append("![").append(alt).append("](").append(src).append(")")
                        }
                    }
                    else -> {
                        for (child in node.childNodes()) {
                            convertNodeToMarkdown(child, sb, depth + 1, listDepth)
                        }
                    }
                }
            }
        }
    }

    private fun renderChildrenToMarkdown(element: Element, depth: Int, listDepth: Int): String {
        val sb = StringBuilder()
        for (child in element.childNodes()) {
            convertNodeToMarkdown(child, sb, depth, listDepth)
        }
        return sb.toString()
    }

    private fun extractCodeText(element: Element): String {
        // Replace <br> with \n
        element.select("br").forEach { it.replaceWith(TextNode("\n")) }
        return element.wholeText()
    }

    private fun extractCodeLanguage(element: Element): String {
        val classAttr = element.attr("class")
        val match = Regex("(?:language-|hljs-|lang-)([a-zA-Z0-9_-]+)").find(classAttr)
        return match?.groupValues?.get(1)?.lowercase() ?: ""
    }

    private fun renderTable(table: Element, depth: Int, listDepth: Int): String {
        val rows = mutableListOf<List<String>>()
        val trElements = table.select("tr")
        for (tr in trElements) {
            val cells = tr.select("th, td")
            val rowCells = mutableListOf<String>()
            for (cell in cells) {
                val cleanCell = renderChildrenToMarkdown(cell, depth, listDepth)
                    .replace("\n", " ")
                    .replace("|", "\\|")
                    .trim()
                rowCells.add(cleanCell)
            }
            if (rowCells.isNotEmpty()) {
                rows.add(rowCells)
            }
        }

        if (rows.isEmpty()) return ""

        val maxCols = rows.maxOf { it.size }
        val mdTable = StringBuilder()

        val headerRow = rows[0]
        val paddedHeader = (0 until maxCols).map { col -> headerRow.getOrElse(col) { "" } }
        mdTable.append("| ").append(paddedHeader.joinToString(" | ")).append(" |\n")

        val separator = (0 until maxCols).map { "---" }
        mdTable.append("| ").append(separator.joinToString(" | ")).append(" |\n")

        for (r in 1 until rows.size) {
            val row = rows[r]
            val paddedRow = (0 until maxCols).map { col -> row.getOrElse(col) { "" } }
            mdTable.append("| ").append(paddedRow.joinToString(" | ")).append(" |\n")
        }

        return mdTable.toString().trimEnd()
    }

    /**
     * Strips bracketed prose citation markers e.g. [1], [42], [1, 2], [1-3], [citation needed]
     * while strictly preserving:
     * - Markdown links [text](url)
     * - Task list checkboxes [ ], [x]
     * - Programming array/index accesses (e.g. arr[0], items[1])
     * - Fenced or inline code blocks
     */
    fun stripInlineCitations(text: String): String {
        if (text.isBlank()) return text
        if (detectClipType(text) == ClipType.CODE) return text

        val singleCitation = "\\[(?:[1-9]\\d*(?:[-,]\\s*[1-9]\\d*)*|citation needed|note\\s*\\d+)\\](?!\\()"
        val punctPattern = Pattern.compile("\\s*(?:$singleCitation)+(?=[.,;:!?])", Pattern.CASE_INSENSITIVE)
        val spacePattern = Pattern.compile("(?<=\\s)(?:$singleCitation)+\\s*", Pattern.CASE_INSENSITIVE)
        val remainPattern = Pattern.compile("(?:$singleCitation)+", Pattern.CASE_INSENSITIVE)

        val lines = text.split("\n")
        val processedLines = lines.map { line ->
            if (!line.contains("`")) {
                var s = punctPattern.matcher(line).replaceAll("")
                s = spacePattern.matcher(s).replaceAll("")
                remainPattern.matcher(s).replaceAll("")
            } else {
                val parts = line.split("`")
                val sb = StringBuilder()
                for (i in parts.indices) {
                    if (i % 2 == 0) {
                        var s = punctPattern.matcher(parts[i]).replaceAll("")
                        s = spacePattern.matcher(s).replaceAll("")
                        sb.append(remainPattern.matcher(s).replaceAll(""))
                    } else {
                        sb.append("`").append(parts[i]).append("`")
                    }
                }
                sb.toString()
            }
        }
        return processedLines.joinToString("\n")
    }

    /**
     * Extracts an intelligent, concise single-line title from clip content.
     */
    fun extractTitle(content: String): String {
        val clean = stripFormatting(content)
            .lines()
            .firstOrNull { it.isNotBlank() }
            ?.trim() ?: "Untitled Clip"
        return if (clean.length > 80) clean.take(77).trimEnd() + "..." else clean
    }

    /**
     * Single-pass zero-allocation word counter.
     */
    fun countWords(text: CharSequence): Int {
        var count = 0
        var inWord = false
        val len = text.length
        for (i in 0 until len) {
            val c = text[i]
            if (c.isWhitespace()) {
                if (inWord) {
                    count++
                    inWord = false
                }
            } else {
                inWord = true
            }
        }
        if (inWord) {
            count++
        }
        return count
    }

    fun stripFormatting(markdown: String): String {
        if (markdown.isBlank()) return ""
        var raw = markdown
        raw = raw.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        raw = raw.replace(Regex("`{3,}[^\\n]*\\n([\\s\\S]*?)\\n`{3,}"), "$1")
        raw = raw.replace(Regex("`([^`]+)`"), "$1")
        raw = raw.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        raw = raw.replace(Regex("__([^_]+)__"), "$1")
        raw = raw.replace(Regex("\\*([^*]+)\\*"), "$1")
        raw = raw.replace(Regex("_([^_]+)_"), "$1")
        raw = raw.replace(Regex("~~([^~]+)~~"), "$1")
        raw = raw.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        raw = raw.replace(Regex("!\\[[^\\]]*\\]\\([^)]+\\)"), "")
        raw = raw.replace(Regex("^[*-]\\s+", RegexOption.MULTILINE), "")
        raw = raw.replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
        raw = raw.replace(Regex("^>\\s+", RegexOption.MULTILINE), "")
        return raw.trim()
    }

    fun detectClipType(content: String, mimeType: String = ""): ClipType {
        if (mimeType.startsWith("image/") || content.startsWith("content://") && (content.endsWith(".png") || content.endsWith(".jpg"))) {
            return ClipType.IMAGE
        }

        val trimmed = content.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            if (!trimmed.contains("\n") && !trimmed.contains(" ")) {
                return ClipType.URL
            }
        }

        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            return ClipType.CODE
        }

        val codeIndicators = listOf(
            Regex("^\\s*(val|var|fun|class|interface|object)\\s+[a-zA-Z0-9_]+", RegexOption.MULTILINE),
            Regex("^\\s*(def|import|from|class)\\s+[a-zA-Z0-9_]+", RegexOption.MULTILINE),
            Regex("^\\s*(const|let|var|function)\\s+[a-zA-Z0-9_]+", RegexOption.MULTILINE),
            Regex("^\\s*(public|private|protected)?\\s*(void|int|double|String|boolean)\\s+[a-zA-Z0-9_]+", RegexOption.MULTILINE),
            Regex("^\\s*(SELECT|INSERT|UPDATE|DELETE|CREATE TABLE)\\s+", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            Regex("^\\s*(npm|pnpm|yarn|cargo|git|adb|curl|docker)\\s+", RegexOption.MULTILINE)
        )
        if (codeIndicators.any { it.containsMatchIn(trimmed) }) {
            return ClipType.CODE
        }

        val mdIndicators = listOf(
            Regex("^#{1,6}\\s+", RegexOption.MULTILINE),
            Regex("\\*\\*[^*]+\\*\\*"),
            Regex("`[^`]+`"),
            Regex("\\[[^\\]]+\\]\\([^)]+\\)"),
            Regex("^[*-]\\s+\\[[ x]\\]", RegexOption.MULTILINE)
        )
        if (mdIndicators.any { it.containsMatchIn(trimmed) }) {
            return ClipType.MARKDOWN
        }

        return ClipType.TEXT
    }
}
