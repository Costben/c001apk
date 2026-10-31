package app.awavenue.c001apk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongPostTemplateTest {
    private fun ok(source: String): LongPostTemplate {
        val parsed = parseLongPostTemplate(source)
        assertTrue("expected a parse, got $parsed", parsed is TemplateParse.Ok)
        return (parsed as TemplateParse.Ok).template
    }

    private fun warnings(source: String): List<String> =
        (parseLongPostTemplate(source) as TemplateParse.Ok).warnings

    private fun failure(source: String): String =
        (parseLongPostTemplate(source) as TemplateParse.Failed).reason

    @Test
    fun readsTheHeaderAndRendersTheMessage() {
        val template = ok(
            """
            ---
            title: 一加 Ace 7 上手体验
            visibility: self
            topics: 一加Ace7, 开箱
            ---
            第一段。

            第二段。
            """.trimIndent(),
        )
        assertEquals("一加 Ace 7 上手体验", template.title)
        assertTrue(template.onlySelf)
        assertEquals(listOf("一加Ace7", "开箱"), template.topics)
        assertEquals("第一段。\n\n第二段。", template.body)
        assertEquals(
            "一加 Ace 7 上手体验\n\n第一段。\n\n第二段。\n\n#一加Ace7# #开箱#",
            template.message,
        )
    }

    @Test
    fun withoutAHeaderTheFirstHeadingIsTheTitle() {
        val template = ok(
            """
            # 屏幕观感

            亮度够用，调光也舒服。
            """.trimIndent(),
        )
        assertEquals("屏幕观感", template.title)
        assertEquals("亮度够用，调光也舒服。", template.body)
        assertTrue(!template.onlySelf)
    }

    @Test
    fun picturesLeaveTheBodyInDocumentOrder() {
        val template = ok(
            """
            ---
            title: 开箱
            ---
            先看正面。

            ![正面](https://example.com/a.jpg)

            再看背面。

            ![背面](file:///sdcard/DCIM/b.png)
            """.trimIndent(),
        )
        assertEquals(listOf("正面", "背面"), template.images.map { it.caption })
        assertEquals(listOf("https://example.com/a.jpg", "/sdcard/DCIM/b.png"), template.images.map { it.source })
        assertTrue(template.images[0].isRemote)
        assertTrue(!template.images[1].isRemote)
        assertEquals("先看正面。\n\n再看背面。", template.body)
        assertEquals("开箱\n\n先看正面。\n\n再看背面。", template.message)
    }

    @Test
    fun headingMarkersAreDroppedBecauseAFeedIsPlainText() {
        val template = ok(
            """
            ---
            title: 上手
            ---
            ## 外观

            手感不错。
            """.trimIndent(),
        )
        assertEquals("外观\n\n手感不错。", template.body)
    }

    @Test
    fun unknownKeysAndUnknownVisibilityAreReported() {
        val reported = warnings(
            """
            ---
            title: 上手
            visiblity: self
            visibility: 偶尔
            ---
            正文。
            """.trimIndent(),
        )
        assertEquals(2, reported.size)
        assertTrue(reported[0].contains("visiblity"))
        assertTrue(reported[1].contains("visibility"))
    }

    @Test
    fun anUnclosedHeaderIsAFailure() {
        assertEquals("头部元信息没有结束的 ---", failure("---\ntitle: 上手\n正文。"))
    }

    @Test
    fun anEmptyDocumentIsAFailure() {
        assertEquals("模板是空的", failure("   \n\n"))
        assertEquals("正文是空的", failure("---\ntitle: 只有标题\n---\n"))
    }

    @Test
    fun moreThanNinePicturesIsAFailure() {
        val pictures = (1..10).joinToString("\n\n") { "![图$it](https://example.com/$it.jpg)" }
        assertEquals("图片最多 9 张，这份模板有 10 张", failure("$pictures\n\n正文。"))
    }

    @Test
    fun overlongBodiesAreReported() {
        val long = "字".repeat(LongPostTemplateSpec.MAX_MESSAGE + 1)
        val reported = warnings(long)
        assertEquals(1, reported.size)
        assertTrue(reported[0].contains("${LongPostTemplateSpec.MAX_MESSAGE}"))
    }

    @Test
    fun quotedHeaderValuesLoseTheirQuotes() {
        val template = ok(
            """
            ---
            title: "带引号的标题"
            topics: "开箱"
            ---
            正文。
            """.trimIndent(),
        )
        assertEquals("带引号的标题", template.title)
        assertEquals(listOf("开箱"), template.topics)
    }

    @Test
    fun topicsAcceptChineseSeparatorsAndStrayHashes() {
        val template = ok(
            """
            ---
            title: 上手
            topics: #一加#、开箱，数码
            ---
            正文。
            """.trimIndent(),
        )
        assertEquals(listOf("一加", "开箱", "数码"), template.topics)
    }
}
