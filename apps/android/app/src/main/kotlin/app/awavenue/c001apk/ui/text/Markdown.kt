package app.awavenue.c001apk.ui.text

import java.net.URI

/**
 * The block structure of a Coolapk body that turned out to be Markdown.
 *
 * The client only reads this: a parsed body is never written back to a feed, the history or a
 * favourite, and the original text stays the source of truth behind 查看原文.
 */
sealed interface MdBlock {
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock
    data class Code(val code: String, val language: String?) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<MdItem>) : MdBlock
    data class Table(
        val header: List<List<MdInline>>,
        val aligns: List<MdAlign>,
        val rows: List<List<List<MdInline>>>,
    ) : MdBlock

    data object Rule : MdBlock
    data class Image(val url: String, val alt: String) : MdBlock
}

data class MdItem(val blocks: List<MdBlock>, val checked: Boolean? = null)

enum class MdAlign { Start, Center, End }

sealed interface MdInline {
    data class Text(val text: String) : MdInline
    data class Strong(val children: List<MdInline>) : MdInline
    data class Emphasis(val children: List<MdInline>) : MdInline
    data class Strikethrough(val children: List<MdInline>) : MdInline
    data class Code(val text: String) : MdInline
    /**
     * [native] marks an anchor that came from Coolapk's HTML; [detect] says whether the link is
     * strong enough evidence to switch the whole body to Markdown. A bare URL is not: a normal
     * post that happens to contain one must keep its original layout.
     */
    data class Link(
        val url: String,
        val children: List<MdInline>,
        val native: Boolean = false,
        val detect: Boolean = true,
    ) : MdInline
    data class Image(val url: String, val alt: String) : MdInline
    data object Break : MdInline
}

/**
 * One body, parsed once per text and mode. [isMarkdown] is false when the conservative detector
 * decided the body is a normal Coolapk post, in which case [blocks] is empty and the caller keeps
 * rendering the original HTML.
 */
class MarkdownContent private constructor(
    val source: String,
    val blocks: List<MdBlock>,
    val isMarkdown: Boolean,
    val preview: String,
    val imageUrls: List<String>,
) {
    companion object {
        /** Longer bodies stay plain: parsing and keeping them would cost more than they are worth. */
        private const val MAX_SOURCE_LENGTH = 200_000
        private const val MAX_CACHE_ENTRIES = 64
        private const val MAX_CACHE_CHARACTERS = 500_000
        private const val PREVIEW_LENGTH = 240

        /** Strings compare by reference first, so a card's repeated lookup stays O(1). */
        private data class CacheKey(val source: String, val explicit: Boolean, val coolapk: Boolean)

        private val cache = object : LinkedHashMap<CacheKey, MarkdownContent>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, MarkdownContent>?) =
                size > MAX_CACHE_ENTRIES
        }
        private var cachedCharacters = 0

        /**
         * @param explicit the caller knows the text is Markdown (release notes), so the detector
         *   is skipped.
         * @param coolapk the body comes from a post: `<!--break-->`, inline HTML and single
         *   newlines follow Coolapk's transport rules instead of GitHub's.
         */
        fun parse(source: String, explicit: Boolean = false, coolapk: Boolean = true): MarkdownContent {
            if (source.length > MAX_SOURCE_LENGTH) return plain(source)
            val key = CacheKey(source, explicit, coolapk)
            synchronized(cache) {
                cache[key]?.let { return it }
            }
            val content = runCatching { build(source, explicit, coolapk) }.getOrElse { plain(source) }
            synchronized(cache) {
                cache[key]?.let { return it }
                cache[key] = content
                cachedCharacters += source.length
                val iterator = cache.entries.iterator()
                while ((cache.size > MAX_CACHE_ENTRIES || cachedCharacters > MAX_CACHE_CHARACTERS) && iterator.hasNext()) {
                    val entry = iterator.next()
                    cachedCharacters -= entry.key.source.length
                    iterator.remove()
                }
            }
            return content
        }

        private fun build(source: String, explicit: Boolean, coolapk: Boolean): MarkdownContent {
            val parser = MarkdownParser(source, coolapk)
            val blocks = parser.parse()
            if (!explicit && !hasMarkdownSyntax(blocks)) return plain(source)
            return MarkdownContent(
                source = source,
                blocks = blocks,
                isMarkdown = true,
                preview = previewOf(blocks),
                imageUrls = imagesOf(blocks),
            )
        }

        private fun plain(source: String) =
            MarkdownContent(source, emptyList(), false, source, emptyList())

        /** The detector from upstream: a lone list item or an ambiguous marker never switches modes. */
        internal fun hasMarkdownSyntax(blocks: List<MdBlock>): Boolean = blocks.any { block ->
            when (block) {
                is MdBlock.Heading, is MdBlock.Code, is MdBlock.Quote, is MdBlock.Table,
                MdBlock.Rule, is MdBlock.Image -> true
                is MdBlock.ListBlock -> block.items.size >= 2 || block.items.any { hasMarkdownSyntax(it.blocks) }
                is MdBlock.Paragraph -> block.inlines.any { it.isFormatting() }
            }
        }

        private fun MdInline.isFormatting(): Boolean = when (this) {
            is MdInline.Strong, is MdInline.Emphasis, is MdInline.Strikethrough,
            is MdInline.Code, is MdInline.Image -> true
            // A topic or user anchor is how every Coolapk post links; it is not Markdown.
            is MdInline.Link -> detect
            is MdInline.Text, MdInline.Break -> false
        }

        private fun previewOf(blocks: List<MdBlock>): String {
            val text = buildString { blocks.forEach { appendPlainText(it) } }
                .replace(Regex("[ \\t]+\\n"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
            if (text.length <= PREVIEW_LENGTH) return text
            return text.take(PREVIEW_LENGTH).trimEnd() + "…"
        }

        private fun StringBuilder.appendPlainText(block: MdBlock) {
            when (block) {
                is MdBlock.Paragraph -> {
                    append(inlineText(block.inlines))
                    append('\n')
                }
                is MdBlock.Heading -> {
                    append(inlineText(block.inlines))
                    append('\n')
                }
                is MdBlock.Code -> {
                    append(block.code)
                    append('\n')
                }
                is MdBlock.Quote -> block.blocks.forEach { appendPlainText(it) }
                is MdBlock.ListBlock -> block.items.forEach { item ->
                    append("• ")
                    item.blocks.forEach { appendPlainText(it) }
                }
                is MdBlock.Table -> {
                    append(inlineText(block.header.flatten()))
                    append('\n')
                    block.rows.forEach { row ->
                        append(row.joinToString(" | ") { inlineText(it) })
                        append('\n')
                    }
                }
                MdBlock.Rule -> Unit
                is MdBlock.Image -> {
                    append(block.alt)
                    append('\n')
                }
            }
        }

        private fun inlineText(inlines: List<MdInline>): String = buildString {
            inlines.forEach { inline ->
                when (inline) {
                    is MdInline.Text -> append(inline.text)
                    is MdInline.Code -> append(inline.text)
                    is MdInline.Strong -> append(inlineText(inline.children))
                    is MdInline.Emphasis -> append(inlineText(inline.children))
                    is MdInline.Strikethrough -> append(inlineText(inline.children))
                    is MdInline.Link -> append(inlineText(inline.children))
                    is MdInline.Image -> append(inline.alt)
                    MdInline.Break -> append('\n')
                }
            }
        }

        private fun imagesOf(blocks: List<MdBlock>): List<String> {
            val images = LinkedHashSet<String>()
            fun visit(inlines: List<MdInline>) {
                inlines.forEach {
                    when (it) {
                        is MdInline.Image -> images += it.url
                        is MdInline.Strong -> visit(it.children)
                        is MdInline.Emphasis -> visit(it.children)
                        is MdInline.Strikethrough -> visit(it.children)
                        is MdInline.Link -> visit(it.children)
                        else -> Unit
                    }
                }
            }
            fun walk(block: MdBlock) {
                when (block) {
                    is MdBlock.Paragraph -> visit(block.inlines)
                    is MdBlock.Heading -> visit(block.inlines)
                    is MdBlock.Quote -> block.blocks.forEach(::walk)
                    is MdBlock.ListBlock -> block.items.forEach { item -> item.blocks.forEach(::walk) }
                    is MdBlock.Table -> {
                        block.header.forEach(::visit)
                        block.rows.forEach { row -> row.forEach(::visit) }
                    }
                    is MdBlock.Image -> images += block.url
                    else -> Unit
                }
            }
            blocks.forEach(::walk)
            return images.toList()
        }

        /**
         * Only HTTP(S), app-relative links and Coolapk's own `coolmarket://` routes enter the
         * renderer. Anything carrying control characters, a backslash or user info is dropped.
         */
        fun allowsUrl(value: String, image: Boolean = false): Boolean {
            if (value.isEmpty() || value.startsWith("//")) return false
            if (value.any { it.code <= 0x20 || it.code == 0x7f || it == '\\' }) return false
            val uri = runCatching { URI(value) }.getOrNull() ?: return false
            if (!uri.rawUserInfo.isNullOrEmpty()) return false
            val scheme = uri.scheme?.lowercase()
            return when {
                scheme == null -> !image && value.startsWith("/")
                scheme == "http" || scheme == "https" -> !uri.host.isNullOrEmpty()
                scheme == "coolmarket" && !image -> {
                    val segments = if (uri.host in COOLAPK_HOSTS) {
                        uri.path.split('/').filter { it.isNotEmpty() }
                    } else {
                        listOf(uri.host.orEmpty()) + uri.path.split('/').filter { it.isNotEmpty() }
                    }
                    segments.size >= 2 && segments[0] in COOLMARKET_ROUTES && segments[1].isNotBlank()
                }
                else -> false
            }
        }

        private val COOLAPK_HOSTS =
            setOf("coolapk.com", "www.coolapk.com", "m.coolapk.com", "com.coolapk.market")
        private val COOLMARKET_ROUTES = setOf("feed", "event", "u", "apk", "game", "t", "product")
    }
}

// ---- parser ---------------------------------------------------------------------------------

private val FENCE_START = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")
private val ATX_HEADING = Regex("""^ {0,3}(#{1,6})[ \t]+(.*?)(?:[ \t]+#+)?[ \t]*$""")
private val SETEXT_UNDERLINE = Regex("""^ {0,3}(=+|-+)[ \t]*$""")
private val RULE = Regex("""^ {0,3}(?:(?:\*[ \t]*){3,}|(?:-[ \t]*){3,}|(?:_[ \t]*){3,})$""")
private val QUOTE_LINE = Regex("""^ {0,3}>(?:[ \t](.*))?$""")
private val LIST_MARKER = Regex("""^([ \t]*)([-+*]|\d{1,9}[.)])[ \t]+(.*)$""")
private val TASK_MARKER = Regex("""^\[([ xX])][ \t]+""")
private val TABLE_DELIMITER = Regex("""^[ \t]*\|?[ \t]*:?-{3,}:?[ \t]*(\|[ \t]*:?-{3,}:?[ \t]*)*\|?[ \t]*$""")
private val FENCE_INFO_LANGUAGE = Regex("""^[A-Za-z0-9_+.-]+$""")
private val AUTOLINK = Regex("""<((?:https?://|mailto:)[^>\s]+)>""")
private val BARE_URL = Regex("""(?:https?://|www\.)[^\s<>()\[\]"']+""")
private val HTML_ANCHOR = Regex(
    """<a\b[^>]*?href\s*=\s*["']([^"']*)["'][^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_IMAGE = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val HTML_ATTR = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*["']([^"']*)["']""")
private val HTML_TAG = Regex("""<(?!https?://|mailto:)[^>]+>""")
private val INLINE_CODE = Regex("""(`+)(.*?)\1""", RegexOption.DOT_MATCHES_ALL)
private val ENTITY = Regex("""&(?:#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);""")

private const val ESCAPABLE = "\\`*_{}[]()#+-.!|<>~\"'"

internal class MarkdownParser(
    source: String,
    private val coolapk: Boolean,
) {
    private val lines: List<String> = normalize(source, coolapk).split('\n')
    private var index = 0

    fun parse(): List<MdBlock> = parseBlocks(lines.size)

    private fun parseBlocks(end: Int): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        while (index < end) {
            val line = lines[index]
            if (line.isBlank()) {
                index++
                continue
            }
            blocks += when {
                FENCE_START.matches(line) -> parseFence(end)
                ATX_HEADING.matches(line) -> parseHeading()
                RULE.matches(line) -> {
                    index++
                    MdBlock.Rule
                }
                QUOTE_LINE.matches(line) -> parseQuote(end)
                LIST_MARKER.matches(line) -> parseList(end)
                isTableStart(end) -> parseTable(end)
                line.startsWith("    ") -> parseIndentedCode(end)
                else -> parseParagraph(end)
            }
        }
        return blocks
    }

    private fun parseFence(end: Int): MdBlock {
        val opening = FENCE_START.matchEntire(lines[index])!!
        val fence = opening.groupValues[1]
        val info = opening.groupValues[2].trim()
        index++
        val code = StringBuilder()
        while (index < end) {
            val line = lines[index]
            val closing = FENCE_START.matchEntire(line)
            if (closing != null &&
                closing.groupValues[1].first() == fence.first() &&
                closing.groupValues[1].length >= fence.length &&
                closing.groupValues[2].isBlank()
            ) {
                index++
                break
            }
            if (code.isNotEmpty()) code.append('\n')
            code.append(line)
            index++
        }
        val language = info.substringBefore(' ').takeIf { it.isNotEmpty() && FENCE_INFO_LANGUAGE.matches(it) }
        return MdBlock.Code(code.toString(), language)
    }

    private fun parseHeading(): MdBlock {
        val match = ATX_HEADING.matchEntire(lines[index])!!
        index++
        val level = match.groupValues[1].length
        return MdBlock.Heading(level, parseInlines(match.groupValues[2].trim()))
    }

    private fun parseQuote(end: Int): MdBlock {
        val inner = StringBuilder()
        while (index < end) {
            val match = QUOTE_LINE.matchEntire(lines[index]) ?: break
            if (inner.isNotEmpty()) inner.append('\n')
            inner.append(match.groupValues[1])
            index++
        }
        return MdBlock.Quote(MarkdownParser(inner.toString(), coolapk).parse())
    }

    private fun parseList(end: Int): MdBlock {
        val first = LIST_MARKER.matchEntire(lines[index])!!
        val baseIndent = first.groupValues[1].replace("\t", "    ").length
        val ordered = first.groupValues[2].first().isDigit()
        val start = if (ordered) first.groupValues[2].dropLast(1).toIntOrNull() ?: 1 else 1
        val items = mutableListOf<MdItem>()

        while (index < end) {
            val line = lines[index]
            if (line.isBlank()) {
                var next = index + 1
                while (next < end && lines[next].isBlank()) next++
                val following = lines.getOrNull(next)
                val followingMarker = following?.let { LIST_MARKER.matchEntire(it) }
                if (followingMarker != null &&
                    followingMarker.groupValues[1].length == baseIndent &&
                    followingMarker.groupValues[2].first().isDigit() == ordered
                ) {
                    index = next
                    continue
                }
                break
            }
            val marker = LIST_MARKER.matchEntire(line) ?: break
            val indent = marker.groupValues[1].length
            if (indent != baseIndent || marker.groupValues[2].first().isDigit() != ordered) break
            val contentIndent = indent + marker.groupValues[2].length + 1
            val content = StringBuilder(marker.groupValues[3])
            index++
            while (index < end) {
                val next = lines[index]
                if (next.isBlank()) {
                    var lookahead = index + 1
                    while (lookahead < end && lines[lookahead].isBlank()) lookahead++
                    if (lookahead < end && indentOf(lines[lookahead]) > baseIndent) {
                        content.append('\n')
                        index++
                    } else {
                        break
                    }
                } else if (indentOf(next) > baseIndent) {
                    content.append('\n').append(next.drop(minOf(indentOf(next), contentIndent).coerceAtMost(next.length)))
                    index++
                } else {
                    break
                }
            }
            val raw = content.toString()
            val task = TASK_MARKER.find(raw)
            val checked = task?.let { it.groupValues[1].lowercase() == "x" }
            val body = if (task != null) raw.substring(task.range.last + 1) else raw
            items += MdItem(MarkdownParser(body, coolapk).parse(), checked)
        }
        return MdBlock.ListBlock(ordered, start, items)
    }

    private fun isTableStart(end: Int, at: Int = index): Boolean {
        if (at + 1 >= end) return false
        val header = lines[at]
        val delimiter = lines[at + 1]
        if (!header.contains('|') || !delimiter.contains('|')) return false
        if (!TABLE_DELIMITER.matches(delimiter)) return false
        return splitRow(header).size == splitRow(delimiter).size
    }

    private fun parseTable(end: Int): MdBlock {
        val header = splitRow(lines[index]).map(::parseInlines)
        val aligns = splitRow(lines[index + 1]).map { cell ->
            val trimmed = cell.trim()
            when {
                trimmed.startsWith(":") && trimmed.endsWith(":") -> MdAlign.Center
                trimmed.endsWith(":") -> MdAlign.End
                else -> MdAlign.Start
            }
        }
        index += 2
        val rows = mutableListOf<List<List<MdInline>>>()
        while (index < end && lines[index].isNotBlank() && lines[index].contains('|')) {
            rows += splitRow(lines[index]).map(::parseInlines)
            index++
        }
        return MdBlock.Table(header, aligns, rows)
    }

    private fun parseIndentedCode(end: Int): MdBlock {
        val code = StringBuilder()
        while (index < end) {
            val line = lines[index]
            if (line.isBlank()) {
                code.append('\n')
                index++
                continue
            }
            if (!line.startsWith("    ")) break
            code.append(line.drop(4)).append('\n')
            index++
        }
        return MdBlock.Code(code.toString().trimEnd('\n'), null)
    }

    private fun parseParagraph(end: Int): MdBlock {
        val collected = mutableListOf<String>()
        while (index < end) {
            val line = lines[index]
            if (line.isBlank()) break
            if (collected.size == 1 && SETEXT_UNDERLINE.matches(line)) break
            if (collected.isNotEmpty() && startsBlock(line, end)) break
            collected += line
            index++
        }
        if (collected.size == 1) {
            val underline = lines.getOrNull(index)?.let { SETEXT_UNDERLINE.matchEntire(it) }
            if (underline != null) {
                index++
                val level = if (underline.groupValues[1].first() == '=') 1 else 2
                return MdBlock.Heading(level, parseInlines(collected[0].trim()))
            }
        }
        val inlines = parseInlines(collected.joinToString("\n"))
        val meaningful = inlines.filterNot { it is MdInline.Text && it.text.isBlank() }
        if (meaningful.size == 1) {
            val image = meaningful[0] as? MdInline.Image
            if (image != null) return MdBlock.Image(image.url, image.alt)
        }
        return MdBlock.Paragraph(inlines)
    }

    private fun startsBlock(line: String, end: Int): Boolean =
        FENCE_START.matches(line) ||
            ATX_HEADING.matches(line) ||
            RULE.matches(line) ||
            QUOTE_LINE.matches(line) ||
            LIST_MARKER.matches(line) ||
            line.startsWith("    ") ||
            isTableStart(end, index)

    private fun parseInlines(text: String): List<MdInline> =
        InlineParser(text, coolapk).parse()

    private fun indentOf(line: String): Int {
        var count = 0
        for (char in line) {
            when (char) {
                ' ' -> count++
                '\t' -> count += 4
                else -> return count
            }
        }
        return count
    }

    private fun splitRow(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (char in trimmed) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> {
                    current.append(char)
                    escaped = true
                }
                char == '|' -> {
                    cells += current.toString().trim()
                    current.setLength(0)
                }
                else -> current.append(char)
            }
        }
        cells += current.toString().trim()
        return cells
    }

    private companion object {
        /**
         * Coolapk bodies mix plain text, `<br>` and anchors, and use a lone newline as a line
         * break. Code fences are left alone; everything else is mapped onto Markdown before the
         * block parser sees it. Other HTML tags are dropped, keeping their text.
         */
        fun normalize(source: String, coolapk: Boolean): String {
            val input = source.replace("\r\n", "\n").replace('\r', '\n').replace("<!--break-->", "")
            val out = StringBuilder()
            var fence: String? = null
            for (line in input.split('\n')) {
                val opening = FENCE_START.matchEntire(line)
                if (fence != null) {
                    out.append(line).append('\n')
                    if (opening != null &&
                        opening.groupValues[1].first() == fence.first() &&
                        opening.groupValues[1].length >= fence.length &&
                        opening.groupValues[2].isBlank()
                    ) {
                        fence = null
                    }
                    continue
                }
                if (opening != null) {
                    fence = opening.groupValues[1]
                    out.append(line).append('\n')
                    continue
                }
                out.append(if (coolapk) coolapkLine(line) else decodeEntities(line)).append('\n')
            }
            return out.toString()
        }

        fun coolapkLine(line: String): String {
            // Inline code is a literal: tags and entities inside it must stay as written.
            val out = StringBuilder()
            var position = 0
            for (match in INLINE_CODE.findAll(line)) {
                out.append(coolapkHtml(line.substring(position, match.range.first)))
                out.append(match.value)
                position = match.range.last + 1
            }
            out.append(coolapkHtml(line.substring(position)))
            return out.toString()
        }

        fun nativeLinkToken(href: String, label: String): String {
            val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
            val url = encoder.encodeToString(href.toByteArray(Charsets.UTF_8))
            val text = encoder.encodeToString(label.toByteArray(Charsets.UTF_8))
            return "\uE000$url:$text\uE001"
        }

        fun coolapkHtml(text: String): String {
            var value = text
            value = value.replace(HTML_ANCHOR) { match ->
                val href = match.groupValues[1]
                val label = HTML_TAG.replace(match.groupValues[2], "").trim()
                if (href.isEmpty()) label else nativeLinkToken(href, label)
            }
            value = value.replace(HTML_IMAGE) { match ->
                val attrs = HTML_ATTR.findAll(match.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                val src = attrs["src"].orEmpty()
                val alt = attrs["alt"].orEmpty()
                if (src.isEmpty()) alt else "![$alt]($src)"
            }
            value = value.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            value = value.replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n\n")
            value = value.replace(Regex("""<p\b[^>]*>""", RegexOption.IGNORE_CASE), "")
            value = value.replace(Regex("""</?(?:b|strong)\s*>""", RegexOption.IGNORE_CASE), "**")
            value = value.replace(Regex("""</?(?:i|em)\s*>""", RegexOption.IGNORE_CASE), "*")
            value = value.replace(Regex("""</?(?:del|s|strike)\s*>""", RegexOption.IGNORE_CASE), "~~")
            value = value.replace(Regex("""</?code\s*>""", RegexOption.IGNORE_CASE), "`")
            value = value.replace(Regex("""</?u\s*>""", RegexOption.IGNORE_CASE), "")
            value = HTML_TAG.replace(value, "")
            return decodeEntities(value)
        }

        fun decodeEntities(text: String): String = text.replace(ENTITY) { match ->
            when (val entity = match.value) {
                "&amp;" -> "&"
                "&lt;" -> "<"
                "&gt;" -> ">"
                "&quot;" -> "\""
                "&apos;" -> "'"
                "&nbsp;" -> " "
                else -> {
                    val body = entity.removePrefix("&").removeSuffix(";")
                    val code = when {
                        body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(16)
                        body.startsWith("#") -> body.drop(1).toIntOrNull()
                        else -> null
                    }
                    code?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: entity
                }
            }
        }
    }
}

private class InlineParser(
    private val text: String,
    private val coolapk: Boolean,
) {
    private val out = mutableListOf<MdInline>()
    private val buffer = StringBuilder()
    private var position = 0

    fun parse(): List<MdInline> {
        while (position < text.length) {
            val char = text[position]
            when {
                char == '\\' && position + 1 < text.length && text[position + 1] in ESCAPABLE -> {
                    buffer.append(text[position + 1])
                    position += 2
                }
                char == '`' -> parseCodeSpan()
                char == '!' && text.getOrNull(position + 1) == '[' -> parseLink(image = true)
                char == '[' -> parseLink(image = false)
                char == '<' -> parseAutolink()
                char == '*' || char == '_' || char == '~' -> parseEmphasis()
                char == '\n' -> parseLineBreak()
                (char == 'h' || char == 'w') && atWordBoundary() -> parseBareUrl()
                char == '\uE000' -> parseNativeLink()
                else -> {
                    buffer.append(char)
                    position++
                }
            }
        }
        flush()
        return out
    }

    private fun parseCodeSpan() {
        var run = 1
        while (text.getOrNull(position + run) == '`') run++
        val closer = findRun(position + run, run)
        if (closer < 0) {
            buffer.append(text, position, position + run)
            position += run
            return
        }
        flush()
        out += MdInline.Code(text.substring(position + run, closer).replace('\n', ' '))
        position = closer + run
    }

    private fun findRun(from: Int, length: Int): Int {
        var cursor = from
        while (cursor < text.length) {
            val found = text.indexOf("`".repeat(length), cursor)
            if (found < 0) return -1
            val before = text.getOrNull(found - 1) == '`'
            val after = text.getOrNull(found + length) == '`'
            if (!before && !after) return found
            cursor = found + 1
        }
        return -1
    }

    private fun parseLink(image: Boolean) {
        val open = if (image) position + 1 else position
        if (text.getOrNull(open) != '[') {
            buffer.append(text[position])
            position++
            return
        }
        val close = findClosing(open, '[', ']') ?: run {
            buffer.append(text[position])
            position++
            return
        }
        val label = text.substring(open + 1, close)
        val paren = close + 1
        if (text.getOrNull(paren) != '(') {
            buffer.append(text[position])
            position++
            return
        }
        val end = findClosing(paren, '(', ')') ?: run {
            buffer.append(text[position])
            position++
            return
        }
        val destination = destinationOf(text.substring(paren + 1, end))
        if (destination == null || !MarkdownContent.allowsUrl(destination, image)) {
            // An unsafe or malformed target keeps its readable text instead of a dead link.
            flush()
            out += MdInline.Text(label)
            position = end + 1
            return
        }
        flush()
        out += if (image) {
            MdInline.Image(destination, label)
        } else {
            MdInline.Link(destination, InlineParser(label, coolapk = false).parse())
        }
        position = end + 1
    }

    private fun destinationOf(raw: String): String? {
        var value = raw.trim()
        if (value.startsWith("<")) {
            val close = value.indexOf('>')
            if (close < 0) return null
            value = value.substring(1, close)
        } else {
            // A title follows the destination after whitespace: [x](url "title").
            Regex("""\s+["'(]""").find(value)?.let { value = value.substring(0, it.range.first) }
        }
        return value.trim().takeIf { it.isNotEmpty() }
    }

    private fun findClosing(start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var cursor = start
        while (cursor < text.length) {
            when (text[cursor]) {
                '\\' -> cursor++
                '`' -> {
                    var run = 1
                    while (text.getOrNull(cursor + run) == '`') run++
                    val end = findRun(cursor + run, run)
                    if (end >= 0) cursor = end + run - 1
                }
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return cursor
                }
            }
            cursor++
        }
        return null
    }

    /** Resolves one anchor produced by the Coolapk normaliser; the token is never shown as text. */
    private fun parseNativeLink() {
        val end = text.indexOf('\uE001', position + 1)
        if (end < 0) {
            buffer.append(text[position])
            position++
            return
        }
        val parts = text.substring(position + 1, end).split(':', limit = 2)
        val url = parts.getOrNull(0)?.let(::decodeToken)
        val label = parts.getOrNull(1)?.let(::decodeToken)
        if (url == null || label == null || !MarkdownContent.allowsUrl(url)) {
            buffer.append(label ?: "")
            position = end + 1
            return
        }
        flush()
        out += MdInline.Link(url, listOf(MdInline.Text(label)), native = true, detect = false)
        position = end + 1
    }

    private fun decodeToken(value: String): String? = runCatching {
        String(java.util.Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()

    private fun parseAutolink() {
        val match = AUTOLINK.find(text, position)
        if (match != null && match.range.first == position) {
            flush()
            out += MdInline.Link(
                match.groupValues[1],
                listOf(MdInline.Text(match.groupValues[1])),
                detect = false,
            )
            position = match.range.last + 1
            return
        }
        buffer.append(text[position])
        position++
    }

    private fun parseEmphasis() {
        val char = text[position]
        var run = 1
        while (text.getOrNull(position + run) == char) run++
        val length = when {
            char == '~' -> if (run >= 2) 2 else 1
            run >= 2 -> 2
            else -> 1
        }
        if (char == '~' && run < 2) {
            buffer.append(text[position])
            position++
            return
        }
        val before = text.getOrNull(position - 1)
        val after = text.getOrNull(position + length)
        if (after == null || after.isWhitespace()) {
            buffer.append(text, position, position + length)
            position += length
            return
        }
        if (char == '_' && before?.isLetterOrDigit() == true && after.isLetterOrDigit()) {
            buffer.append(text, position, position + length)
            position += length
            return
        }
        val delimiter = char.toString().repeat(length)
        var search = position + length
        while (search <= text.length - length) {
            val close = text.indexOf(delimiter, search)
            if (close < 0) break
            val escaped = close > 0 && countBackslashes(close) % 2 == 1
            val previous = text.getOrNull(close - 1)
            val next = text.getOrNull(close + length)
            val rightFlanking = previous != null && !previous.isWhitespace()
            val intraword = char == '_' && previous?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true
            if (!escaped && rightFlanking && !intraword && close > position + length) {
                val children = InlineParser(text.substring(position + length, close), coolapk).parse()
                flush()
                out += when {
                    char == '~' -> MdInline.Strikethrough(children)
                    length == 2 -> MdInline.Strong(children)
                    else -> MdInline.Emphasis(children)
                }
                position = close + length
                return
            }
            search = close + 1
        }
        buffer.append(text, position, position + length)
        position += length
    }

    private fun countBackslashes(at: Int): Int {
        var count = 0
        var cursor = at - 1
        while (cursor >= 0 && text[cursor] == '\\') {
            count++
            cursor--
        }
        return count
    }

    private fun parseLineBreak() {
        flush()
        val hard = coolapk || (position >= 2 && text[position - 1] == ' ' && text[position - 2] == ' ') ||
            (position >= 1 && text[position - 1] == '\\')
        trimTrailingSpaces()
        out += if (hard) MdInline.Break else MdInline.Text(" ")
        position++
    }

    private fun parseBareUrl() {
        if (!atWordBoundary()) {
            buffer.append(text[position])
            position++
            return
        }
        val match = BARE_URL.find(text, position)
        if (match == null || match.range.first != position) {
            buffer.append(text[position])
            position++
            return
        }
        var url = match.value.trimEnd('.', ',', ';', ':', '!', '?', '，', '。', '；', '：', '！', '？', '、')
        while (url.endsWith(")") && url.count { it == '(' } < url.count { it == ')' }) url = url.dropLast(1)
        if (url.isEmpty()) {
            buffer.append(text[position])
            position++
            return
        }
        flush()
        val href = if (url.startsWith("www.")) "https://$url" else url
        out += MdInline.Link(href, listOf(MdInline.Text(url)), detect = false)
        position = match.range.first + url.length
    }

    private fun atWordBoundary(): Boolean {
        if (position == 0) return true
        val previous = text[position - 1]
        return previous.isWhitespace() || previous in "([（【<\"'"
    }

    private fun flush() {
        if (buffer.isNotEmpty()) {
            out += MdInline.Text(buffer.toString())
            buffer.setLength(0)
        }
    }

    private fun trimTrailingSpaces() {
        while (buffer.isNotEmpty() && buffer[buffer.length - 1] == ' ') buffer.setLength(buffer.length - 1)
    }
}

// ---- export ---------------------------------------------------------------------------------

/** Markdown → HTML for the offline export and for release notes, with the same URL rules. */
internal fun markdownHtml(blocks: List<MdBlock>): String = buildString {
    blocks.forEach { appendBlock(it) }
}

private fun StringBuilder.appendBlock(block: MdBlock) {
    when (block) {
        is MdBlock.Paragraph -> append("<p>").appendInlines(block.inlines).append("</p>")
        is MdBlock.Heading -> append("<h${block.level}>").appendInlines(block.inlines).append("</h${block.level}>")
        is MdBlock.Code -> {
            append("<pre><code")
            block.language?.let { append(" class=\"language-").append(escapeHtml(it)).append('"') }
            append('>').append(escapeHtml(block.code)).append("</code></pre>")
        }
        is MdBlock.Quote -> {
            append("<blockquote>")
            block.blocks.forEach { appendBlock(it) }
            append("</blockquote>")
        }
        is MdBlock.ListBlock -> {
            if (block.ordered) {
                append("<ol")
                if (block.start != 1) append(" start=\"").append(block.start).append('"')
                append('>')
            } else {
                append("<ul>")
            }
            block.items.forEach { item ->
                append("<li>")
                if (item.checked != null) {
                    append("<input type=\"checkbox\" disabled")
                    if (item.checked) append(" checked")
                    append('>')
                }
                item.blocks.forEach { appendBlock(it) }
                append("</li>")
            }
            append(if (block.ordered) "</ol>" else "</ul>")
        }
        is MdBlock.Table -> {
            append("<table><thead><tr>")
            block.header.forEachIndexed { index, cell ->
                append("<th")
                when (block.aligns.getOrNull(index)) {
                    MdAlign.Center -> append(" style=\"text-align:center\"")
                    MdAlign.End -> append(" style=\"text-align:right\"")
                    else -> Unit
                }
                append('>').appendInlines(cell).append("</th>")
            }
            append("</tr></thead><tbody>")
            block.rows.forEach { row ->
                append("<tr>")
                row.forEach { cell -> append("<td>").appendInlines(cell).append("</td>") }
                append("</tr>")
            }
            append("</tbody></table>")
        }
        MdBlock.Rule -> append("<hr>")
        is MdBlock.Image -> appendImage(block.url, block.alt)
    }
}

private fun StringBuilder.appendImage(url: String, alt: String) {
    if (!MarkdownContent.allowsUrl(url, image = true)) {
        append(escapeHtml(alt))
        return
    }
    append("<figure><img src=\"").append(escapeHtml(url)).append("\" alt=\"").append(escapeHtml(alt)).append("\">")
    if (alt.isNotEmpty()) append("<figcaption>").append(escapeHtml(alt)).append("</figcaption>")
    append("</figure>")
}

private fun StringBuilder.appendInlines(inlines: List<MdInline>): StringBuilder {
    inlines.forEach { inline ->
        when (inline) {
            is MdInline.Text -> append(escapeHtml(inline.text))
            is MdInline.Code -> append("<code>").append(escapeHtml(inline.text)).append("</code>")
            is MdInline.Strong -> append("<strong>").appendInlines(inline.children).append("</strong>")
            is MdInline.Emphasis -> append("<em>").appendInlines(inline.children).append("</em>")
            is MdInline.Strikethrough -> append("<del>").appendInlines(inline.children).append("</del>")
            is MdInline.Link -> {
                if (MarkdownContent.allowsUrl(inline.url)) {
                    append("<a href=\"").append(escapeHtml(inline.url)).append("\">")
                    appendInlines(inline.children)
                    append("</a>")
                } else {
                    appendInlines(inline.children)
                }
            }
            is MdInline.Image -> appendImage(inline.url, inline.alt)
            MdInline.Break -> append("<br>")
        }
    }
    return this
}

internal fun escapeHtml(value: String): String = buildString(value.length) {
    value.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}

/** The visible text of a block, used where only a one-line summary fits. */
internal fun mdPlainText(blocks: List<MdBlock>): String = buildString {
    fun inlineText(inlines: List<MdInline>): String = inlines.joinToString("") { inline ->
        when (inline) {
            is MdInline.Text -> inline.text
            is MdInline.Code -> inline.text
            is MdInline.Strong -> inlineText(inline.children)
            is MdInline.Emphasis -> inlineText(inline.children)
            is MdInline.Strikethrough -> inlineText(inline.children)
            is MdInline.Link -> inlineText(inline.children)
            is MdInline.Image -> inline.alt
            MdInline.Break -> "\n"
        }
    }
    fun blockText(block: MdBlock) {
        when (block) {
            is MdBlock.Paragraph -> append(inlineText(block.inlines)).append('\n')
            is MdBlock.Heading -> append(inlineText(block.inlines)).append('\n')
            is MdBlock.Code -> append(block.code).append('\n')
            is MdBlock.Quote -> block.blocks.forEach(::blockText)
            is MdBlock.ListBlock -> block.items.forEach { item -> item.blocks.forEach(::blockText) }
            is MdBlock.Table -> {
                block.header.forEach { append(inlineText(it)).append(' ') }
                append('\n')
                block.rows.forEach { row ->
                    row.forEach { append(inlineText(it)).append(' ') }
                    append('\n')
                }
            }
            MdBlock.Rule -> Unit
            is MdBlock.Image -> append(block.alt).append('\n')
        }
    }
    blocks.forEach(::blockText)
}.trim()
