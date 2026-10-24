package app.awavenue.c001apk.ui.feed.detail

import android.graphics.BitmapFactory
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.text.MarkdownContent
import app.awavenue.c001apk.ui.text.escapeHtml
import app.awavenue.c001apk.ui.text.markdownHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

enum class ExportFormat(val label: String, val detail: String, val extension: String, val mimeType: String) {
    Markdown("Markdown (.md)", "可编辑，图片保留在线链接", "md", "text/markdown"),
    Html("离线 HTML (.html)", "图文排版，图片内嵌至单个文件", "html", "text/html"),
}

data class PreparedExport(
    val fileName: String,
    val bytes: ByteArray,
    val warnings: List<String>,
    val commentIds: List<String>,
)

/**
 * One export, generated from a snapshot of the detail page. Nothing is written back to the feed,
 * the history or a favourite: the file is the only output.
 */
class ContentExportService(
    private val loadPopular: suspend (String) -> List<Entity>,
    private val blockedUids: () -> Set<String> = { emptySet() },
    private val loadImage: suspend (String) -> ByteArray? = { downloadImage(it) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun prepare(
        feed: Entity,
        articles: List<FeedArticle>,
        format: ExportFormat,
    ): PreparedExport {
        coroutineContext.ensureActive()
        val warnings = mutableListOf<String>()
        val notices = mutableListOf<String>()
        var popular = emptyList<Entity>()
        try {
            popular = loadPopular(feed.id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            notices += "未能取得热门评论，本文件仅含已取得的置顶评论。"
            warnings += "热门评论读取失败，本次无法保证保存前三条评论。"
        }
        coroutineContext.ensureActive()
        val pinned = feed.list("topReplyRows").map { it.asPinned() }
        val comments = selectExportComments(pinned, popular, blockedUids())
        var document = ExportDocument.from(feed, articles, comments, notices)
        if (format == ExportFormat.Markdown) {
            if (document.imageUrls.isNotEmpty()) {
                notices += "Markdown 图片保留在线链接，离线阅读图片请另存为 HTML。"
                document = ExportDocument.from(feed, articles, comments, notices)
            }
            return PreparedExport(
                fileName = "${document.fileName}.md",
                bytes = document.markdown().toByteArray(Charsets.UTF_8),
                warnings = warnings,
                commentIds = comments.map { it.id },
            )
        }

        val images = mutableMapOf<String, String>()
        var bytesUsed = 0L
        val deadline = clock() + IMAGE_DEADLINE_MILLIS
        val urls = document.imageUrls
        for (url in urls.take(MAX_IMAGES)) {
            coroutineContext.ensureActive()
            if (clock() > deadline || bytesUsed >= MAX_TOTAL_BYTES) break
            val bytes = try {
                loadImage(url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            coroutineContext.ensureActive()
            val mime = bytes?.let(::imageMime) ?: continue
            if (bytes.size > MAX_IMAGE_BYTES || bytesUsed + bytes.size > MAX_TOTAL_BYTES) continue
            if (!withContext(Dispatchers.Default) { decodes(bytes) }) continue
            bytesUsed += bytes.size
            images[url] = "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
        }
        coroutineContext.ensureActive()
        val missing = urls.size - images.size
        if (missing > 0) warnings += "$missing 张图片未能保存，将以来源链接代替；这些图片无法离线查看。"
        return PreparedExport(
            fileName = "${document.fileName}.html",
            bytes = document.offlineHtml(images).toByteArray(Charsets.UTF_8),
            warnings = warnings,
            commentIds = comments.map { it.id },
        )
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 32 * 1024 * 1024
        const val MAX_IMAGES = 60
        const val MAX_PIXELS = 32L * 1024 * 1024
        const val IMAGE_DEADLINE_MILLIS = 45_000L

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
            if (!MarkdownContent.allowsUrl(url, image = true)) return@withContext null
            val request = Request.Builder()
                .url(url)
                .header("Accept", "image/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val declared = response.body.contentLength()
                if (declared > MAX_IMAGE_BYTES) return@withContext null
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                response.body.byteStream().use { stream ->
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        if (output.size() + read > MAX_IMAGE_BYTES) return@withContext null
                        output.write(buffer, 0, read)
                    }
                }
                output.toByteArray()
            }
        }

        fun imageMime(data: ByteArray): String? {
            fun starts(vararg signature: Int) =
                data.size >= signature.size && signature.indices.all { data[it] == signature[it].toByte() }
            return when {
                starts(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
                starts(0xFF, 0xD8, 0xFF) -> "image/jpeg"
                starts(0x47, 0x49, 0x46, 0x38, 0x37, 0x61) ||
                    starts(0x47, 0x49, 0x46, 0x38, 0x39, 0x61) -> "image/gif"
                data.size >= 12 &&
                    String(data, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    String(data, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                else -> null
            }
        }

        fun decodes(data: ByteArray): Boolean {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            if (bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) return false
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return false
            bitmap.recycle()
            return true
        }
    }
}

/** A pinned reply always precedes the popular list, and the same reply is never written twice. */
fun selectExportComments(
    pinned: List<Entity>,
    popular: List<Entity>,
    blockedUids: Set<String> = emptySet(),
): List<Entity> {
    val seen = HashSet<String>()
    val result = mutableListOf<Entity>()
    for (reply in pinned + popular.filter { it.isTop } + popular) {
        val id = reply.id
        if (id.isEmpty() || reply.uid in blockedUids || !seen.add(id)) continue
        result += reply
        if (result.size == 3) break
    }
    return result
}

private fun Entity.asPinned(): Entity =
    Entity(JsonObject(json + ("isTop" to JsonPrimitive(1))))

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun publishDate(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return ""
    return runCatching {
        Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).format(DateFormatter)
    }.getOrDefault("")
}

private fun authorOf(entity: Entity): String = entity.username.ifEmpty { "未知作者" }

private fun plainText(html: String): String = decodeEntities(
    html.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), ""),
).trim()

/**
 * The saved document: the same body rendered twice, once as Markdown for editing and once as a
 * self-contained HTML page for reading offline.
 */
class ExportDocument private constructor(
    val title: String,
    val id: String,
    private val markdownBody: String,
    private val htmlBody: String,
) {
    val imageUrls: List<String> by lazy {
        Regex("""<img\b[^>]*\bsrc="([^"]+)"""", RegexOption.IGNORE_CASE)
            .findAll(htmlBody)
            .map { unescapeHtml(it.groupValues[1]) }
            .distinct()
            .toList()
    }

    val fileName: String
        get() {
            var safe = title.replace(Regex("""[<>:"/\\|?*\x00-\x1f]"""), "_")
                .replace(Regex("""[.\s]+$"""), "")
                .trim()
            safe = safe.take(70)
            if (safe.isEmpty() || Regex("""^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)""", RegexOption.IGNORE_CASE).containsMatchIn(safe)) {
                safe = "动态"
            }
            return "$safe-${id.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")}"
        }

    fun markdown(): String = buildString {
        append("# ").append(title.replace(Regex("""\s+"""), " ").trim()).append("\n\n")
        append(markdownBody.trim()).append('\n')
    }

    fun html(): String = page(htmlBody)

    fun offlineHtml(images: Map<String, String>): String {
        val body = Regex("""<img\b([^>]*?)\bsrc="([^"]+)"([^>]*)>""", RegexOption.IGNORE_CASE)
            .replace(htmlBody) { match ->
                val url = unescapeHtml(match.groupValues[2])
                val altMatch = Regex("""\balt="([^"]*)"""").find(match.value)
                val alt = altMatch?.groupValues?.get(1).orEmpty()
                val data = images[url]
                if (data != null && data.startsWith("data:image/")) {
                    """<img${match.groupValues[1]}src="$data"${match.groupValues[3]}>"""
                } else {
                    """<a href="${escapeHtml(url)}">图片未保存：${escapeHtml(unescapeHtml(alt).ifEmpty { "查看来源" })}</a>"""
                }
            }
        return page(body)
    }

    private fun page(body: String): String = buildString {
        append("<!doctype html><html lang=\"zh-CN\"><head>")
        append("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; ")
        append("style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">")
        append("<title>").append(escapeHtml(title)).append("</title><style>")
        append("body{max-width:840px;margin:32px auto;padding:0 20px;color:#202124;background:#fff;")
        append("font:16px/1.7 system-ui,sans-serif;overflow-wrap:anywhere}")
        append("img{max-width:100%;height:auto}a{color:#1756a9}pre{overflow:auto;padding:16px;")
        append("background:#f2f3f5;white-space:pre}code{font-family:monospace}")
        append("table{border-collapse:collapse;display:block;overflow:auto}td,th{border:1px solid #bbb;padding:6px 12px}")
        append("blockquote{border-left:4px solid #bbb;padding-left:16px;margin-left:0;color:#555}")
        append("figcaption{color:#555}section{border-bottom:1px solid #ddd;padding-bottom:16px}")
        append("@media print{body{margin:0;max-width:none}pre{white-space:pre-wrap}img{break-inside:avoid}}")
        append("</style></head><body>").append(body).append("</body></html>")
    }

    companion object {
        fun from(
            feed: Entity,
            articles: List<FeedArticle> = emptyList(),
            comments: List<Entity> = emptyList(),
            notices: List<String> = emptyList(),
        ): ExportDocument {
            val id = feed.id
            val name = plainText(feed.messageTitle).trim()
            val title = when {
                name.isNotEmpty() -> name
                plainText(feed.title).trim().isNotEmpty() -> plainText(feed.title).trim()
                else -> "${authorOf(feed)}的动态"
            }
            val markdown = buildString {
                append(metadataMarkdown(feed))
                append("\n\n")
                append("https://www.coolapk.com/feed/").append(id).append("\n\n")
                append("https://www.coolapk1s.com/feed/").append(id).append("\n\n")
                append(bodyMarkdown(feed, articles, depth = 0))
                if (comments.isNotEmpty()) {
                    append("\n\n## 精选评论（置顶优先，其余按热门排序）\n")
                    comments.take(3).forEach { reply ->
                        append("\n### ").append(if (reply.isTop) "【置顶】" else "").append(authorOf(reply)).append("\n\n")
                        append(metadataMarkdown(reply)).append("\n\n")
                        append("赞：").append(reply.likeNum.ifEmpty { "0" }).append("\n\n")
                        append(bodyMarkdown(reply, emptyList(), depth = 0)).append('\n')
                    }
                }
                notices.forEach { append("\n> 保存说明：").append(it).append('\n') }
            }
            val html = buildString {
                append("<h1>").append(escapeHtml(title)).append("</h1>")
                append("<p>作者：").append(escapeHtml(authorOf(feed)))
                publishDate(feed.dateline).takeIf { it.isNotEmpty() }?.let {
                    append("<br>发布时间：").append(escapeHtml(it))
                }
                append("</p>")
                append("<p><a href=\"https://www.coolapk.com/feed/").append(escapeHtml(id)).append("\">")
                append("https://www.coolapk.com/feed/").append(escapeHtml(id)).append("</a><br>")
                append("<a href=\"https://www.coolapk1s.com/feed/").append(escapeHtml(id)).append("\">")
                append("https://www.coolapk1s.com/feed/").append(escapeHtml(id)).append("</a></p>")
                append(bodyHtml(feed, articles, depth = 0))
                if (comments.isNotEmpty()) {
                    append("<hr><h2>精选评论（置顶优先，其余按热门排序）</h2>")
                    comments.take(3).forEach { reply ->
                        append("<section><h3>").append(if (reply.isTop) "【置顶】" else "")
                        append(escapeHtml(authorOf(reply))).append("</h3>")
                        append("<p>作者：").append(escapeHtml(authorOf(reply)))
                        publishDate(reply.dateline).takeIf { it.isNotEmpty() }?.let {
                            append("<br>发布时间：").append(escapeHtml(it))
                        }
                        append("</p><p>赞：").append(escapeHtml(reply.likeNum.ifEmpty { "0" })).append("</p>")
                        append(bodyHtml(reply, emptyList(), depth = 0)).append("</section>")
                    }
                }
                notices.forEach {
                    append("<p>保存说明：").append(escapeHtml(it)).append("</p>")
                }
            }
            return ExportDocument(title, id, markdown, html)
        }

        private fun metadataMarkdown(entity: Entity): String = buildString {
            append("作者：").append(authorOf(entity))
            publishDate(entity.dateline).takeIf { it.isNotEmpty() }?.let {
                append("\n\n发布时间：").append(it)
            }
        }

        private fun bodyMarkdown(entity: Entity, articles: List<FeedArticle>, depth: Int): String = buildString {
            val parts = articles.ifEmpty { parseArticles(entity) }
            if (parts.isNotEmpty()) {
                parts.forEach { part ->
                    when (part.type) {
                        "text" -> append(contentMarkdown(part.message)).append("\n\n")
                        "image" -> {
                            append("![").append(escapeMarkdown(plainText(part.description))).append("](")
                            append(part.url).append(")\n\n")
                        }
                        "shareUrl" -> {
                            append("[").append(escapeMarkdown(part.title.ifEmpty { part.url })).append("](")
                            append(part.url).append(")\n\n")
                        }
                    }
                }
            } else {
                append(contentMarkdown(entity.message)).append("\n\n")
                entity.picArr.forEach { url -> append("![](").append(url).append(")\n\n") }
            }
            val forward = entity.forwardSourceFeed
            if (forward != null && depth < 3) {
                val inner = buildString {
                    append("转发自：").append(authorOf(forward)).append("\n\n")
                    if (forward.messageTitle.isNotEmpty()) {
                        append("## ").append(plainText(forward.messageTitle)).append("\n\n")
                    }
                    append(bodyMarkdown(forward, emptyList(), depth + 1))
                }
                append("\n\n")
                inner.trim().lineSequence().forEach { append("> ").append(it).append('\n') }
            } else if (forward == null && entity.forwardSourceType.isNotEmpty()) {
                append("\n\n转发内容已不可用\n")
            }
        }

        private fun bodyHtml(entity: Entity, articles: List<FeedArticle>, depth: Int): String = buildString {
            val parts = articles.ifEmpty { parseArticles(entity) }
            if (parts.isNotEmpty()) {
                parts.forEach { part ->
                    when (part.type) {
                        "text" -> append(contentHtml(part.message))
                        "image" -> append(imageHtml(part.url, part.description))
                        "shareUrl" -> {
                            append("<p><a href=\"").append(escapeHtml(part.url)).append("\">")
                            append(escapeHtml(part.title.ifEmpty { part.url })).append("</a></p>")
                        }
                    }
                }
            } else {
                append(contentHtml(entity.message))
                entity.picArr.forEach { url -> append(imageHtml(url, "")) }
            }
            val forward = entity.forwardSourceFeed
            if (forward != null && depth < 3) {
                append("<blockquote><p>转发自：").append(escapeHtml(authorOf(forward))).append("</p>")
                if (forward.messageTitle.isNotEmpty()) {
                    append("<h2>").append(escapeHtml(plainText(forward.messageTitle))).append("</h2>")
                }
                append(bodyHtml(forward, emptyList(), depth + 1))
                append("</blockquote>")
            } else if (forward == null && entity.forwardSourceType.isNotEmpty()) {
                append("<p>转发内容已不可用</p>")
            }
        }

        /** The body blocks as the detail page already parsed them, so both views agree. */
        private fun parseArticles(entity: Entity): List<FeedArticle> {
            val parsed = runCatching { FeedArticle.parse(entity.string("message_raw_output")) }.getOrDefault(emptyList())
            val parts = parsed.filter { it.type in setOf("text", "image", "shareUrl") }.toMutableList()
            val cover = entity.string("message_cover")
            if (parts.isNotEmpty() && cover.isNotEmpty()) {
                parts.add(0, FeedArticle(type = "image", url = cover))
            }
            return parts
        }

        private fun contentMarkdown(value: String): String {
            val parsed = MarkdownContent.parse(value)
            return if (parsed.isMarkdown) parsed.source.replace("<!--break-->", "") else htmlToMarkdown(value)
        }

        private fun contentHtml(value: String): String {
            val parsed = MarkdownContent.parse(value)
            return if (parsed.isMarkdown) markdownHtml(parsed.blocks) else htmlToHtml(value)
        }

        private fun imageHtml(url: String, caption: String): String {
            if (!MarkdownContent.allowsUrl(url, image = true)) return ""
            return buildString {
                append("<figure><img src=\"").append(escapeHtml(url)).append("\" alt=\"")
                append(escapeHtml(caption)).append("\">")
                if (caption.isNotEmpty()) append("<figcaption>").append(escapeHtml(caption)).append("</figcaption>")
                append("</figure>")
            }
        }
    }
}

// ---- Coolapk HTML → Markdown / HTML ---------------------------------------------------------

private enum class TextMode { Markdown, Html }

/**
 * Coolapk bodies are HTML fragments with `<br>`, anchors and pictures. The export renders them
 * either as Markdown or as escaped HTML; raw server markup never reaches the file.
 */
private fun convertHtml(html: String, mode: TextMode): String {
    val out = StringBuilder()
    var position = 0
    while (position < html.length) {
        val char = html[position]
        if (char != '<') {
            val next = html.indexOf('<', position).let { if (it < 0) html.length else it }
            val text = decodeEntities(html.substring(position, next))
            out.append(if (mode == TextMode.Markdown) escapeMarkdown(text) else escapeHtml(text))
            position = next
            continue
        }
        val close = html.indexOf('>', position)
        if (close < 0) break
        val tag = html.substring(position + 1, close).trim()
        val name = tag.trimStart('/').substringBefore(' ').substringBefore('/').lowercase()
        when {
            name == "br" -> out.append(if (mode == TextMode.Markdown) "\n" else "<br>")
            name == "p" -> if (tag.startsWith("/")) {
                out.append(if (mode == TextMode.Markdown) "\n\n" else "")
            }
            name == "a" && !tag.startsWith("/") -> {
                val end = html.indexOf("</a>", close, ignoreCase = true)
                val href = Regex("""href\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.get(1).orEmpty()
                val innerEnd = if (end < 0) html.length else end
                val label = plainText(html.substring(close + 1, innerEnd))
                if (MarkdownContent.allowsUrl(href)) {
                    if (mode == TextMode.Markdown) {
                        out.append('[').append(escapeMarkdown(label)).append("](").append(markdownUrl(href)).append(')')
                    } else {
                        out.append("<a href=\"").append(escapeHtml(href)).append("\">")
                        out.append(escapeHtml(label)).append("</a>")
                    }
                } else {
                    out.append(if (mode == TextMode.Markdown) escapeMarkdown(label) else escapeHtml(label))
                }
                position = if (end < 0) html.length else end + 4
                continue
            }
            name == "img" -> {
                val src = Regex("""src\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.get(1).orEmpty()
                val alt = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.get(1).orEmpty()
                if (MarkdownContent.allowsUrl(src, image = true)) {
                    if (mode == TextMode.Markdown) {
                        out.append("![").append(escapeMarkdown(decodeEntities(alt))).append("](")
                            .append(markdownUrl(src)).append(')')
                    } else {
                        out.append("<img src=\"").append(escapeHtml(src)).append("\" alt=\"")
                        out.append(escapeHtml(decodeEntities(alt))).append("\">")
                    }
                }
            }
            name in setOf("b", "strong") -> out.append(
                if (mode == TextMode.Markdown) "**" else if (tag.startsWith("/")) "</strong>" else "<strong>",
            )
            name in setOf("i", "em") -> out.append(
                if (mode == TextMode.Markdown) "*" else if (tag.startsWith("/")) "</em>" else "<em>",
            )
            name in setOf("del", "s", "strike") -> out.append(
                if (mode == TextMode.Markdown) "~~" else if (tag.startsWith("/")) "</del>" else "<del>",
            )
            name == "code" -> out.append(
                if (mode == TextMode.Markdown) "`" else if (tag.startsWith("/")) "</code>" else "<code>",
            )
            name == "u" -> if (mode == TextMode.Html) out.append(if (tag.startsWith("/")) "</u>" else "<u>")
        }
        position = close + 1
    }
    return out.toString()
}

private fun htmlToMarkdown(html: String): String =
    convertHtml(html, TextMode.Markdown).replace(Regex("\n{3,}"), "\n\n").trim()

private fun htmlToHtml(html: String): String =
    "<div>" + convertHtml(html, TextMode.Html) + "</div>"

private fun escapeMarkdown(value: String): String = buildString(value.length) {
    value.forEach { char ->
        if (char in "\\`*_[]<>") append('\\')
        append(char)
    }
}

private val ENTITY = Regex("""&(?:#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);""")

private fun decodeEntities(value: String): String = ENTITY.replace(value) { match ->
    when (val entity = match.value) {
        "&nbsp;" -> " "
        "&amp;" -> "&"
        "&lt;" -> "<"
        "&gt;" -> ">"
        "&quot;" -> "\""
        "&apos;" -> "'"
        "&#39;" -> "'"
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

private fun markdownUrl(value: String): String = value.replace("(", "%28").replace(")", "%29")

private fun unescapeHtml(value: String): String = decodeEntities(value)
