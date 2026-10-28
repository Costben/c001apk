package app.awavenue.c001apk.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftTest {
    private fun draft(
        text: String = "",
        images: List<DraftImage> = emptyList(),
        replyId: String = "",
        replyType: String = "feed",
        targetType: String = "",
        title: String = "",
    ) = Draft(
        id = "draft-1",
        text = text,
        images = images,
        replyId = replyId,
        replyType = replyType,
        targetType = targetType,
        targetId = if (targetType.isEmpty()) "" else "5000",
        title = title,
        feedType = "feed",
        onlySelf = false,
        updatedAt = 1_700_000_000_000,
    )

    @Test
    fun aStoredDraftReadsBackWithItsTextPicturesAndTarget() {
        val original = draft(
            text = "#小米15# 这代手感不错",
            images = listOf(
                DraftImage("/data/drafts/draft-1/a.jpg", "a.jpg", "1080x1920", "md5-a"),
                DraftImage("/data/drafts/draft-1/b.jpg", "b.jpg", "1080x1920", "md5-b"),
            ),
            targetType = "tag",
            title = "小米15",
        )

        val restored = Draft.fromJson(original.id, Json.parseToJsonElement(original.toJson().toString()).jsonObject)

        assertEquals(original, restored)
        assertEquals(listOf("md5-a", "md5-b"), restored.images.map { it.md5 })
        assertEquals("小米15", restored.title)
    }

    @Test
    fun aDraftWrittenBeforeAFieldExistedStillReads() {
        val restored = Draft.fromJson("draft-2", Json.parseToJsonElement("""{"text":"草稿"}""").jsonObject)

        assertEquals("草稿", restored.text)
        assertTrue(restored.images.isEmpty())
        assertEquals("feed", restored.replyType)
        assertEquals(false, restored.onlySelf)
        assertEquals(0L, restored.updatedAt)
    }

    @Test
    fun theLabelSaysWhereTheDraftWouldBePublished() {
        assertEquals("发布动态", draft().label)
        assertEquals("话题 #小米15#", draft(targetType = "tag", title = "小米15").label)
        assertEquals("评论 @酷安小编", draft(replyId = "123", title = "酷安小编").label)
        assertEquals("回复 @酷安小编", draft(replyId = "123", replyType = "reply", title = "酷安小编").label)
        assertEquals("应用评论 · 微信", draft(targetType = "apk", title = "微信").label)
    }

    @Test
    fun thePreviewFallsBackToWhatIsLeft() {
        assertEquals("这代 手感不错", draft(text = "  这代\n手感不错  ").preview)
        assertEquals("（2张图片）", draft(images = List(2) { DraftImage("/a/$it.jpg", "a", "", "") }).preview)
        assertEquals("（空白草稿）", draft().preview)
    }
}
