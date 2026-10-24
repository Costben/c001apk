package app.awavenue.c001apk.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun conservativeDetectionLeavesOrdinaryPostsAlone() {
        assertFalse(MarkdownContent.parse("#一加13# 今天发布了").isMarkdown)
        assertFalse(MarkdownContent.parse("POCO_X3_GT 用起来不错").isMarkdown)
        assertFalse(MarkdownContent.parse("- 只有一项").isMarkdown)
        assertFalse(MarkdownContent.parse("1. 只有一个编号").isMarkdown)
        assertFalse(MarkdownContent.parse("价格 1999。").isMarkdown)
    }

    @Test
    fun formattingSwitchesTheBodyToMarkdown() {
        assertTrue(MarkdownContent.parse("# 标题\n\n正文").isMarkdown)
        assertTrue(MarkdownContent.parse("看 **这个** 功能").isMarkdown)
        assertTrue(MarkdownContent.parse("用 `adb` 连接").isMarkdown)
        assertTrue(MarkdownContent.parse("- 一\n- 二").isMarkdown)
        assertTrue(MarkdownContent.parse("```\ncode\n```").isMarkdown)
        assertTrue(MarkdownContent.parse("| A | B |\n| --- | --- |\n| 1 | 2 |").isMarkdown)
    }

    @Test
    fun headingsRequireASpaceAndKeepTrailingHashes() {
        val heading = MarkdownContent.parse("# 标题 #", explicit = true)
        val block = heading.blocks.single() as MdBlock.Heading
        assertEquals(1, block.level)
        assertEquals("标题", (block.inlines.single() as MdInline.Text).text)

        val topic = MarkdownContent.parse("#一加13#", explicit = true)
        assertTrue(topic.blocks.single() is MdBlock.Paragraph)
    }

    @Test
    fun codeFencesKeepMarkupAndEmojiCodes() {
        val content = MarkdownContent.parse("```html\n<b>[doge]</b>\n```", explicit = true)
        val code = content.blocks.single() as MdBlock.Code
        assertEquals("html", code.language)
        assertEquals("<b>[doge]</b>", code.code)
    }

    @Test
    fun coolapkTransportRulesBecomeMarkdown() {
        val content = MarkdownContent.parse(
            "第一行<br>第二行\n<a href=\"/feed/123\">看这条动态</a>",
            explicit = true,
        )
        val paragraph = content.blocks.single() as MdBlock.Paragraph
        assertTrue(paragraph.inlines.any { it is MdInline.Break })
        val link = paragraph.inlines.filterIsInstance<MdInline.Link>().single()
        assertEquals("/feed/123", link.url)
        assertEquals("看这条动态", (link.children.single() as MdInline.Text).text)
    }

    @Test
    fun bareUrlsAloneDoNotSwitchTheBody() {
        assertFalse(MarkdownContent.parse("看 https://example.com/a 这个").isMarkdown)
        assertFalse(MarkdownContent.parse("看 <https://example.com/a> 这个").isMarkdown)
        assertTrue(MarkdownContent.parse("看 [这个](https://example.com/a)").isMarkdown)
    }

    @Test
    fun coolapkAnchorsAloneDoNotSwitchTheBody() {
        val topic = MarkdownContent.parse(
            "正文 <a class=\"feed-link-tag\" href=\"/t/x\">#话题#</a> [doge]",
        )
        assertFalse(topic.isMarkdown)
    }

    @Test
    fun breakCommentIsHiddenButSourceStays() {
        val source = "上半部分<!--break-->下半部分"
        val content = MarkdownContent.parse(source, explicit = true)
        assertEquals("上半部分下半部分", (content.blocks.single() as MdBlock.Paragraph).inlines
            .joinToString("") { (it as MdInline.Text).text })
        assertEquals(source, content.source)
    }

    @Test
    fun taskListsAndNestingParse() {
        val content = MarkdownContent.parse("- [x] 完成\n- [ ] 未完成", explicit = true)
        val list = content.blocks.single() as MdBlock.ListBlock
        assertEquals(2, list.items.size)
        assertEquals(true, list.items[0].checked)
        assertEquals(false, list.items[1].checked)

        val nested = MarkdownContent.parse("- 外层\n  - 内层一\n  - 内层二", explicit = true)
        val outer = nested.blocks.single() as MdBlock.ListBlock
        val inner = outer.items.single().blocks.filterIsInstance<MdBlock.ListBlock>().single()
        assertEquals(2, inner.items.size)
    }

    @Test
    fun tableCellsAndAlignmentParse() {
        val content = MarkdownContent.parse("| 左 | 中 | 右 |\n| :--- | :---: | ---: |\n| 1 | 2 | 3 |", explicit = true)
        val table = content.blocks.single() as MdBlock.Table
        assertEquals(listOf(MdAlign.Start, MdAlign.Center, MdAlign.End), table.aligns)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun standaloneImageBecomesABlock() {
        val content = MarkdownContent.parse("![截图](https://img.example.com/a.png)", explicit = true)
        val image = content.blocks.single() as MdBlock.Image
        assertEquals("https://img.example.com/a.png", image.url)
        assertEquals("截图", image.alt)
        assertEquals(listOf("https://img.example.com/a.png"), content.imageUrls)
    }

    @Test
    fun unsafeUrlsAreRejected() {
        assertFalse(MarkdownContent.allowsUrl("javascript:alert(1)"))
        assertFalse(MarkdownContent.allowsUrl("data:image/png;base64,AA==", image = true))
        assertFalse(MarkdownContent.allowsUrl("https://user@example.com/a"))
        assertFalse(MarkdownContent.allowsUrl("//example.com/a"))
        assertTrue(MarkdownContent.allowsUrl("https://example.com/a.png", image = true))
        assertTrue(MarkdownContent.allowsUrl("/feed/123"))
        assertTrue(MarkdownContent.allowsUrl("coolmarket://feed/123"))
    }

    @Test
    fun previewIsPlainTextAndBounded() {
        val content = MarkdownContent.parse("# 标题\n\n**粗体**正文", explicit = true)
        assertTrue(content.preview.startsWith("标题"))
        assertTrue(content.preview.contains("粗体正文"))
        assertFalse(content.preview.contains("**"))
    }

    @Test
    fun inlineFormattingParses() {
        val content = MarkdownContent.parse(
            "**粗** *斜* ~~删~~ `码` [链接](https://example.com)",
            explicit = true,
        )
        val inlines = (content.blocks.single() as MdBlock.Paragraph).inlines
        assertTrue(inlines.any { it is MdInline.Strong })
        assertTrue(inlines.any { it is MdInline.Emphasis })
        assertTrue(inlines.any { it is MdInline.Strikethrough })
        assertTrue(inlines.any { it is MdInline.Code })
        assertEquals("https://example.com", inlines.filterIsInstance<MdInline.Link>().single().url)
    }

    @Test
    fun quotesOrderedListsAndRulesParse() {
        val content = MarkdownContent.parse("> 引用\n\n1. 一\n2. 二\n\n---", explicit = true)
        assertTrue(content.blocks[0] is MdBlock.Quote)
        val list = content.blocks[1] as MdBlock.ListBlock
        assertTrue(list.ordered)
        assertEquals(1, list.start)
        assertEquals(2, list.items.size)
        assertEquals(MdBlock.Rule, content.blocks[2])
    }

    @Test
    fun autolinksAndBareUrlsBecomeLinks() {
        val angle = MarkdownContent.parse("见 <https://example.com/a>", explicit = true)
        assertTrue((angle.blocks.single() as MdBlock.Paragraph).inlines.any { it is MdInline.Link })

        val bare = MarkdownContent.parse("见 https://example.com/a。", explicit = true)
        val link = (bare.blocks.single() as MdBlock.Paragraph).inlines.filterIsInstance<MdInline.Link>().single()
        assertEquals("https://example.com/a", link.url)
    }

    @Test
    fun setextHeadingIsRecognised() {
        val content = MarkdownContent.parse("标题\n===", explicit = true)
        val heading = content.blocks.single() as MdBlock.Heading
        assertEquals(1, heading.level)
    }

    @Test
    fun htmlExportEscapesAndKeepsStructure() {
        val content = MarkdownContent.parse(
            "# 标题\n\n<script>alert(1)</script>\n\n- 一\n- 二\n\n| A |\n| --- |\n| 1 |",
            explicit = true,
            coolapk = false,
        )
        val html = markdownHtml(content.blocks)
        assertTrue(html.contains("<h1>标题</h1>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<table>"))
    }
}
