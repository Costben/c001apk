package app.awavenue.c001apk.data

/**
 * One picture a template points at: a path on this device or an http(s) URL. Captions ride along so
 * the preview can say what the picture is before it is fetched.
 */
data class TemplateImage(val caption: String, val source: String) {
    val isRemote: Boolean
        get() = source.startsWith("http://") || source.startsWith("https://")
}

/**
 * A long post written to the template spec: a title, the body, the topics it belongs to, the
 * pictures it shows and the scope it is published under.
 *
 * The body is the text the composer's field receives. The template is Markdown, but a feed is not
 * rendered as Markdown, so the parser keeps the words and drops the markers: heading hashes go,
 * pictures move out of the flow into the gallery, everything else stays literal.
 */
data class LongPostTemplate(
    val title: String,
    val onlySelf: Boolean,
    val topics: List<String>,
    val body: String,
    val images: List<TemplateImage>,
) {
    /** Exactly what the composer will hold: the title line, the body, then the topics line. */
    val message: String
        get() = buildString {
            if (title.isNotEmpty()) append(title).append("\n\n")
            append(body)
            if (topics.isNotEmpty()) {
                if (body.isNotEmpty()) append("\n\n")
                append(topics.joinToString(" ") { "#$it#" })
            }
        }

    val charCount: Int
        get() = message.length
}

sealed interface TemplateParse {
    data class Ok(val template: LongPostTemplate, val warnings: List<String>) : TemplateParse
    data class Failed(val reason: String) : TemplateParse
}

/** The spec's fixed parts. Anything else in the header is reported, not silently dropped. */
object LongPostTemplateSpec {
    /**
     * The composer's ceiling. The server takes longer bodies than the old 500-character form did —
     * an 1832-character feed posts fine — but the field is a sheet, not an editor, so 2000 is where
     * this client stops and asks the writer to split.
     */
    const val MAX_MESSAGE = 2000

    /** The composer's picture row, and the upload endpoint's, agree on nine. */
    const val MAX_IMAGES = 9

    const val TITLE = "title"
    const val VISIBILITY = "visibility"
    const val TOPICS = "topics"

    val KEYS = setOf(TITLE, VISIBILITY, TOPICS)

    /** The spec as it is shown in the app, so the rules and the parser cannot drift apart. */
    const val DOCUMENT = """---
title: 一加 Ace 7 上手体验
visibility: public
topics: 一加Ace7, 开箱
---
正文第一段。

![外观](https://example.com/front.jpg)

正文第二段。"""
}

private val IMAGE = Regex("""!\[([^\]]*)\]\(\s*([^)\s]+)\s*\)""")
private val HEADING = Regex("""^\s{0,3}#{1,6}\s+(.*)$""")
private val FRONT_MATTER_FENCE = Regex("""^\s*---\s*$""")
private val TOPIC_SEPARATOR = Regex("""[,，、;；]""")

/**
 * Reads a template document.
 *
 * The header is optional: without it the first `# ` heading is the title and the post is public.
 * With it, `title`, `visibility` (public / self) and `topics` are understood; any other key is
 * reported back so a typo does not disappear. Pictures are pulled out in document order, and the
 * body is left with the words only.
 */
fun parseLongPostTemplate(source: String): TemplateParse {
    val text = source.replace("\r\n", "\n").replace('\r', '\n')
    if (text.isBlank()) return TemplateParse.Failed("模板是空的")

    val lines = text.lines()
    var start = 0
    val header = mutableMapOf<String, String>()
    val warnings = mutableListOf<String>()

    val firstContent = lines.indexOfFirst { it.isNotBlank() }
    if (firstContent >= 0 && FRONT_MATTER_FENCE.matches(lines[firstContent])) {
        val close = (firstContent + 1 until lines.size).firstOrNull { FRONT_MATTER_FENCE.matches(lines[it]) }
            ?: return TemplateParse.Failed("头部元信息没有结束的 ---")
        for (index in firstContent + 1 until close) {
            val line = lines[index].trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val colon = line.indexOf(':')
            if (colon <= 0) {
                warnings += "第 ${index + 1} 行不是 key: value，已跳过"
                continue
            }
            val key = line.substring(0, colon).trim().lowercase()
            // Writers quote values out of habit; the quotes are not part of the title or the topic.
            val value = line.substring(colon + 1).trim().removeSurrounding("\"")
            if (key !in LongPostTemplateSpec.KEYS) {
                warnings += "不认识的字段 $key，已忽略"
                continue
            }
            header[key] = value
        }
        start = close + 1
    }

    var body = lines.drop(start).joinToString("\n")

    // Pictures leave the flow: a feed has one gallery, not a place in the text.
    val images = mutableListOf<TemplateImage>()
    body = IMAGE.replace(body) { match ->
        images += TemplateImage(
            caption = match.groupValues[1].trim(),
            source = match.groupValues[2].trim().removePrefix("file://"),
        )
        ""
    }

    var title = header[LongPostTemplateSpec.TITLE].orEmpty().trim()
    val bodyLines = body.lines().toMutableList()
    if (title.isEmpty()) {
        // No header title: a leading `# 标题` line still reads as one.
        val first = bodyLines.indexOfFirst { it.isNotBlank() }
        if (first >= 0 && bodyLines[first].trimStart().startsWith("# ")) {
            title = bodyLines[first].trim().removePrefix("#").trim()
            bodyLines.removeAt(first)
        }
    }

    // A feed is plain text, so the heading markers that are left carry no meaning.
    body = bodyLines.joinToString("\n") { line -> HEADING.replace(line) { it.groupValues[1] } }
    body = body.replace(Regex("""\n{3,}"""), "\n\n").trim()

    val onlySelf = when (val visibility = header[LongPostTemplateSpec.VISIBILITY].orEmpty().lowercase()) {
        "" -> false
        "public", "所有人", "公开" -> false
        "self", "private", "仅自己", "私密" -> true
        else -> {
            warnings += "visibility 只认 public / self，已按 public 处理"
            false
        }
    }

    val topics = header[LongPostTemplateSpec.TOPICS].orEmpty()
        .split(TOPIC_SEPARATOR)
        .map { it.trim().removePrefix("#").removeSuffix("#") }
        .filter { it.isNotEmpty() }

    if (body.isEmpty() && images.isEmpty()) return TemplateParse.Failed("正文是空的")
    if (images.size > LongPostTemplateSpec.MAX_IMAGES) {
        return TemplateParse.Failed("图片最多 ${LongPostTemplateSpec.MAX_IMAGES} 张，这份模板有 ${images.size} 张")
    }

    val template = LongPostTemplate(title = title, onlySelf = onlySelf, topics = topics, body = body, images = images)
    if (template.charCount > LongPostTemplateSpec.MAX_MESSAGE) {
        warnings += "正文 ${template.charCount} 字，超过 ${LongPostTemplateSpec.MAX_MESSAGE} 字上限，发布会被拒绝"
    }
    return TemplateParse.Ok(template, warnings)
}
