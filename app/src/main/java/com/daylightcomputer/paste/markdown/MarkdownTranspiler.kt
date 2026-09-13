package com.daylightcomputer.paste.markdown

import java.util.regex.Pattern

enum class ClipType {
    MARKDOWN,
    CODE,
    URL,
    TEXT
}

/**
 * Daylight Markdown Transpiler & Content Normalizer.
 * Reconstructs clean CommonMark / GitHub Flavored Markdown from HTML fragments,
 * rich text DOM trees, and extracts structured metadata.
 */
object MarkdownTranspiler {

    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]+>", Pattern.DOTALL)
    private val CODE_BLOCK_PATTERN = Pattern.compile("<pre(?:\\s+[^>]*)?><code(?:\\s+class=[\"'](?:language-)?([a-zA-Z0-9_-]+)[\"'])?(?:\\s+[^>]*)?>(.*?)</code></pre>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val PRE_PATTERN = Pattern.compile("<pre(?:\\s+[^>]*)?>(.*?)</pre>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val INLINE_CODE_PATTERN = Pattern.compile("<code(?:\\s+[^>]*)?>(.*?)</code>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val LINK_PATTERN = Pattern.compile("<a(?:\\s+[^>]*)?\\s+href=[\"']([^\"']+)[\"'](?:\\s+[^>]*)?>(.*?)</a>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val BOLD_PATTERN = Pattern.compile("<(?:b|strong)(?:\\s+[^>]*)?>(.*?)</(?:b|strong)>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val ITALIC_PATTERN = Pattern.compile("<(?:i|em)(?:\\s+[^>]*)?>(.*?)</(?:i|em)>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val STRIKE_PATTERN = Pattern.compile("<(?:s|strike|del)(?:\\s+[^>]*)?>(.*?)</(?:s|strike|del)>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
    private val BLOCKQUOTE_PATTERN = Pattern.compile("<blockquote(?:\\s+[^>]*)?>(.*?)</blockquote>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

    fun transpileHtmlToMarkdown(html: String): String {
        if (html.isBlank()) return ""
        var md = html

        // 1. Code blocks (<pre><code>)
        val codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(md)
        val sbCode = StringBuffer()
        while (codeBlockMatcher.find()) {
            val lang = codeBlockMatcher.group(1)?.trim() ?: ""
            val rawCode = decodeHtmlEntities(codeBlockMatcher.group(2) ?: "").trim()
            val replacement = "\n\n```$lang\n$rawCode\n```\n\n"
            codeBlockMatcher.appendReplacement(sbCode, MatcherUtil.quoteReplacement(replacement))
        }
        codeBlockMatcher.appendTail(sbCode)
        md = sbCode.toString()

        // 2. Standalone <pre>
        val preMatcher = PRE_PATTERN.matcher(md)
        val sbPre = StringBuffer()
        while (preMatcher.find()) {
            val rawCode = decodeHtmlEntities(preMatcher.group(1) ?: "").trim()
            val replacement = "\n\n```\n$rawCode\n```\n\n"
            preMatcher.appendReplacement(sbPre, MatcherUtil.quoteReplacement(replacement))
        }
        preMatcher.appendTail(sbPre)
        md = sbPre.toString()

        // 3. Headings (h1 to h6)
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

        // 4. Horizontal Rules
        md = md.replace(Regex("<hr\\s*/?>", RegexOption.IGNORE_CASE), "\n\n---\n\n")

        // 5. Blockquotes
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

        // 6. Ordered & Unordered List Items
        md = md.replace(Regex("<ul(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("</ul>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("<ol(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("</ol>", RegexOption.IGNORE_CASE), "\n")
        
        // List items
        val liPattern = Pattern.compile("<li(?:\\s+[^>]*)?>(.*?)</li>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val liMatcher = liPattern.matcher(md)
        val sbLi = StringBuffer()
        var listIndex = 1
        while (liMatcher.find()) {
            val itemContent = liMatcher.group(1)?.trim() ?: ""
            val replacement = "\n- $itemContent"
            liMatcher.appendReplacement(sbLi, MatcherUtil.quoteReplacement(replacement))
            listIndex++
        }
        liMatcher.appendTail(sbLi)
        md = sbLi.toString()

        // 7. Inline Formatting
        // Bold
        md = replaceWithPattern(md, BOLD_PATTERN) { "**${it.trim()}**" }
        // Italic
        md = replaceWithPattern(md, ITALIC_PATTERN) { "*${it.trim()}*" }
        // Strike
        md = replaceWithPattern(md, STRIKE_PATTERN) { "~~${it.trim()}~~" }
        // Inline code
        md = replaceWithPattern(md, INLINE_CODE_PATTERN) { "`" + decodeHtmlEntities(it).trim() + "`" }
        // Links
        val linkMatcher = LINK_PATTERN.matcher(md)
        val sbLink = StringBuffer()
        while (linkMatcher.find()) {
            val url = linkMatcher.group(1)?.trim() ?: ""
            val label = linkMatcher.group(2)?.trim() ?: url
            val replacement = "[$label]($url)"
            linkMatcher.appendReplacement(sbLink, MatcherUtil.quoteReplacement(replacement))
        }
        linkMatcher.appendTail(sbLink)
        md = sbLink.toString()

        // 8. Paragraphs and Linebreaks
        md = md.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("<p(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE), "\n\n")
        md = md.replace(Regex("</p>", RegexOption.IGNORE_CASE), "")
        md = md.replace(Regex("<div(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("</div>", RegexOption.IGNORE_CASE), "")

        // 9. Strip any remaining unknown HTML tags
        md = HTML_TAG_PATTERN.matcher(md).replaceAll("")

        // 10. Decode remaining HTML entities
        md = decodeHtmlEntities(md)

        // 11. Normalize excessive whitespace & blank lines
        md = md.replace(Regex("\n{3,}"), "\n\n").trim()

        return md
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
        // Strip markdown syntax symbols
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
        // Remove headings #
        s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        // Remove blockquotes >
        s = s.replace(Regex("^>\\s+", RegexOption.MULTILINE), "")
        // Remove list bullets
        s = s.replace(Regex("^[\\*\\-\\+]\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
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
        if (trimmed.startsWith("```") || trimmed.startsWith("SELECT ") || trimmed.startsWith("curl ") || 
            trimmed.startsWith("adb ") || trimmed.startsWith("#!/") || 
            (trimmed.contains("class ") && trimmed.contains("{") && trimmed.contains("}")) ||
            (trimmed.contains("def ") && trimmed.contains(":") && trimmed.contains("return "))) {
            return ClipType.CODE
        }

        // Check if Markdown
        val hasMarkdownTokens = trimmed.contains("# ") || trimmed.contains("## ") ||
                trimmed.contains("**") || trimmed.contains("`") ||
                trimmed.contains("- [ ]") || trimmed.contains("- [x]") ||
                (trimmed.contains("[") && trimmed.contains("](") && trimmed.contains(")")) ||
                (trimmed.contains("\n- ") || trimmed.contains("\n* "))

        if (hasMarkdownTokens || (!rawHtml.isNullOrBlank() && rawHtml.contains("<"))) {
            return ClipType.MARKDOWN
        }

        return ClipType.TEXT
    }

    fun extractTitle(text: String): String {
        val firstLine = text.trim().lines().firstOrNull { it.isNotBlank() } ?: "Clipping"
        // Clean leading markdown tokens for title display
        val clean = firstLine.replace(Regex("^#{1,6}\\s+"), "")
            .replace(Regex("^\\-\\s+"), "")
            .replace(Regex("^>\\s+"), "")
            .replace(Regex("\\*\\*"), "")
            .trim()
        return if (clean.length > 80) clean.take(77) + "..." else clean
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
