package app.awavenue.c001apk.ui.feed.reply

import app.awavenue.c001apk.data.normalizeFeedFields
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.feed.detail.OssUploadModel
import app.awavenue.c001apk.ui.feed.detail.SelectedImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedEditTest {

    @Test
    fun unavailableReasonRejectsHtmlArticleAndNonEditableFeed() {
        val article = Entity(
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("100"),
                    "is_html_article" to JsonPrimitive("1"),
                    "enableModify" to JsonPrimitive(1),
                ),
            ),
        )
        assertNotNull(EditFeedInput.unavailableReason(article))

        val locked = Entity(
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("101"),
                    "is_html_article" to JsonPrimitive("0"),
                    "enableModify" to JsonPrimitive(0),
                ),
            ),
        )
        assertNotNull(EditFeedInput.unavailableReason(locked))

        val editable = Entity(
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("102"),
                    "is_html_article" to JsonPrimitive("0"),
                    "enableModify" to JsonPrimitive(1),
                ),
            ),
        )
        assertNull(EditFeedInput.unavailableReason(editable))
    }

    @Test
    fun fromChangeDetailReadsRawFieldsPicturesVisibilityAndTopic() {
        val detail = Entity(
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("75254839"),
                    "feedType" to JsonPrimitive("feed"),
                    "message" to JsonPrimitive("#ColorOS17#\n测试正文"),
                    "message_title" to JsonPrimitive("标题"),
                    "picArr" to JsonArray(
                        listOf(
                            JsonPrimitive("http://image.coolapk.com/a-livepic@1080x1920.jpg"),
                            JsonPrimitive("http://image.coolapk.com/b.jpg"),
                        ),
                    ),
                    "publish_status" to JsonPrimitive("1"),
                    "turl" to JsonPrimitive("/t/ColorOS17"),
                    "ttitle" to JsonPrimitive("ColorOS17"),
                    "tid" to JsonPrimitive("999"),
                    "extra_url" to JsonPrimitive("https://example.com"),
                    "disallow_reply" to JsonPrimitive("1"),
                ),
            ),
        )

        val input = EditFeedInput.fromChangeDetail(detail)
        assertEquals("75254839", input.id)
        assertEquals("feed", input.feedType)
        assertEquals("#ColorOS17#\n测试正文", input.message)
        assertEquals("标题", input.messageTitle)
        assertEquals(2, input.picUrls.size)
        assertTrue(input.onlySelf)
        assertEquals("tag", input.targetType)
        assertEquals("999", input.targetId)
        assertEquals("ColorOS17", input.targetTitle)
        assertTrue(input.extraFields.contains("extra_url" to "https://example.com"))
        assertTrue(input.extraFields.contains("disallow_reply" to "1"))
    }

    @Test
    fun mergeUploadedPicturesPreservesMixedOrder() {
        val images = listOf(
            SelectedImage("http://img/old1.jpg", OssUploadModel("", "", ""), remoteUrl = "http://img/old1.jpg"),
            SelectedImage("/tmp/local1.jpg", OssUploadModel("l1.jpg", "100x100", "md5a")),
            SelectedImage("http://img/old2.jpg", OssUploadModel("", "", ""), remoteUrl = "http://img/old2.jpg"),
            SelectedImage("/tmp/local2.jpg", OssUploadModel("l2.jpg", "100x100", "md5b")),
        )
        val merged = mergeUploadedPictures(images, listOf("http://img/new1.jpg", "http://img/new2.jpg"))
        assertEquals(
            listOf("http://img/old1.jpg", "http://img/new1.jpg", "http://img/old2.jpg", "http://img/new2.jpg"),
            merged,
        )
    }

    @Test
    fun buildEditFeedFieldsAndNormalizeVisibility() {
        val editing = EditFeedInput(
            id = "75265677",
            feedType = "feed",
            message = "旧正文",
            messageTitle = "原标题",
            onlySelf = true,
            targetType = "tag",
            targetId = "42",
            targetTitle = "ColorOS17",
            extraFields = listOf("extra_url" to "https://example.com"),
        )
        val rawFields = buildEditFeedFields(
            editing = editing,
            message = "新正文",
            pic = "http://img/a.jpg",
            onlySelf = true,
            activeTarget = ReplyTarget(targetType = "tag", targetId = "42", title = "ColorOS17"),
        )
        val normalized = normalizeFeedFields(rawFields).toMap()
        assertEquals("75265677", normalized["id"])
        assertEquals("feed", normalized["type"])
        assertEquals("新正文", normalized["message"])
        assertEquals("原标题", normalized["message_title"])
        assertEquals("http://img/a.jpg", normalized["pic"])
        assertEquals("1", normalized["status"])
        assertEquals("1", normalized["publish_status"])
        assertEquals("https://example.com", normalized["extra_url"])
    }
}
