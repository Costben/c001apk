package app.awavenue.c001apk.ui.feed.detail

import app.awavenue.c001apk.model.Entity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentExportTest {
    private fun feed(
        title: String = "标题",
        message: String = "正文第一行<br>第二行",
        pics: List<String> = emptyList(),
    ) = Entity(
        buildJsonObject {
            put("id", "12345")
            put("message", message)
            put("message_title", title)
            put("username", "张三")
            put("dateline", 1_700_000_000L)
            put("likenum", "12")
            if (pics.isNotEmpty()) put("picArr", buildJsonArray { pics.forEach { add(it) } })
        },
    )

    private fun reply(
        id: String,
        uid: String = "u$id",
        top: Boolean = false,
        message: String = "回复 $id",
    ) = Entity(
        buildJsonObject {
            put("id", id)
            put("uid", uid)
            put("username", "用户$id")
            put("message", message)
            put("likenum", "3")
            if (top) put("isTop", 1)
        },
    )

    @Test
    fun markdownKeepsTitleAuthorAndSourceLinks() {
        val document = ExportDocument.from(feed())
        val markdown = document.markdown()
        assertTrue(markdown.startsWith("# 标题"))
        assertTrue(markdown.contains("作者：张三"))
        assertTrue(markdown.contains("发布时间："))
        assertTrue(markdown.contains("https://www.coolapk.com/feed/12345"))
        assertTrue(markdown.contains("https://www.coolapk1s.com/feed/12345"))
        assertTrue(markdown.contains("正文第一行"))
        assertTrue(markdown.contains("第二行"))
    }

    @Test
    fun htmlPageIsSelfContainedAndEscaped() {
        val document = ExportDocument.from(feed(message = "<script>alert(1)</script>正文"))
        val html = document.html()
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("Content-Security-Policy"))
        assertTrue(html.contains("<h1>标题</h1>"))
        // The tag itself is dropped; its text stays readable and no script reaches the file.
        assertTrue(html.contains("alert(1)"))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun commentsPreferPinnedAndSkipBlockedAndDuplicates() {
        val pinned = listOf(reply("1", top = true))
        val popular = listOf(reply("1"), reply("2"), reply("3"), reply("4"))
        val selected = selectExportComments(pinned, popular, blockedUids = setOf("u3"))
        assertEquals(listOf("1", "2", "4"), selected.map { it.id })
        assertTrue(selected.first().isTop)
    }

    @Test
    fun commentsAppearInBothFormats() {
        val comments = listOf(reply("1", top = true), reply("2"))
        val document = ExportDocument.from(feed(), comments = comments)
        assertTrue(document.markdown().contains("## 精选评论"))
        assertTrue(document.markdown().contains("### 【置顶】用户1"))
        assertTrue(document.html().contains("<h2>精选评论"))
        assertTrue(document.html().contains("【置顶】用户1"))
    }

    @Test
    fun picturesAreEmbeddedOrReplaced() {
        val url = "https://img.example.com/a.png"
        val document = ExportDocument.from(feed(pics = listOf(url)))
        assertEquals(listOf(url), document.imageUrls)

        val embedded = document.offlineHtml(mapOf(url to "data:image/png;base64,AA=="))
        assertTrue(embedded.contains("src=\"data:image/png;base64,AA==\""))

        val missing = document.offlineHtml(emptyMap())
        assertTrue(missing.contains("图片未保存"))
        assertTrue(missing.contains(url))
    }

    @Test
    fun numericEntitiesAreDecoded() {
        val document = ExportDocument.from(feed(message = "第一行&#10;第二行 &amp; &#39;x&#39;"))
        val markdown = document.markdown()
        assertTrue(markdown.contains("第一行\n第二行 & 'x'"))
        assertFalse(markdown.contains("&#10;"))
    }

    @Test
    fun fileNameIsSafeForTheSystemPicker() {
        val document = ExportDocument.from(feed(title = "A/B:C"))
        assertEquals("A_B_C-12345", document.fileName)

        val untitled = ExportDocument.from(Entity(buildJsonObject { put("id", "9") }))
        assertEquals("未知作者的动态-9", untitled.fileName)
    }

    @Test
    fun articlesAreExportedInOrderAndTitleIsNotRepeated() {
        val articles = listOf(
            FeedArticle(type = "text", message = "**粗体**"),
            FeedArticle(type = "image", url = "https://img.example.com/b.png", description = "配图"),
            FeedArticle(type = "shareUrl", url = "https://example.com/x", title = "链接"),
        )
        val document = ExportDocument.from(feed(), articles = articles)
        val markdown = document.markdown()
        assertTrue(markdown.contains("**粗体**"))
        assertTrue(markdown.contains("![配图](https://img.example.com/b.png)"))
        assertTrue(markdown.contains("[链接](https://example.com/x)"))
        assertFalse(markdown.contains("# 标题\n\n# 标题"))
    }

    @Test
    fun forwardedBodyIsQuoted() {
        val forward = Entity(
            JsonObject(
                mapOf(
                    "id" to kotlinx.serialization.json.JsonPrimitive("77"),
                    "username" to kotlinx.serialization.json.JsonPrimitive("李四"),
                    "message" to kotlinx.serialization.json.JsonPrimitive("被转发的正文"),
                ),
            ),
        )
        val source = Entity(
            buildJsonObject {
                put("id", "12345")
                put("message_title", "标题")
                put("username", "张三")
                put("message", "转发语")
                put("forwardSourceType", "feed")
                put("forwardSourceFeed", forward.json)
            },
        )
        val document = ExportDocument.from(source)
        assertTrue(document.markdown().contains("> 转发自：李四"))
        assertTrue(document.markdown().contains("> 被转发的正文"))
        assertTrue(document.html().contains("<blockquote>"))
    }
}
