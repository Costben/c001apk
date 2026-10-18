package app.awavenue.c001apk.ui.notification

import app.awavenue.c001apk.model.Entity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentTest {
    private fun entity(json: String) = Entity(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun atCommentKeepsCommentBodyAndFeedTarget() {
        val display = notificationDisplay(
            entity(
                """
                {
                  "entityType":"notification","type":"at_comment_me",
                  "username":"小明","userAvatar":"avatar","uid":"11",
                  "note":"评论了你的动态",
                  "message":"这条评论才是通知的内容",
                  "extra_title":"被评论的动态正文",
                  "url":"/feed/123"
                }
                """,
            ),
        )
        assertEquals("小明", display.actorName)
        assertEquals("11", display.actorUid)
        assertEquals("avatar", display.actorAvatar)
        assertEquals("这条评论才是通知的内容", display.content)
        assertEquals("被评论的动态正文", display.target)
        assertEquals("/feed/123", display.link)
    }

    @Test
    fun likeUsesLikeFieldsAndSynthesisesTheAction() {
        val display = notificationDisplay(
            entity(
                """
                {
                  "entityType":"notification","type":"feed_like",
                  "likeUsername":"小红","likeUid":"22","likeAvatar":"like-avatar",
                  "message":"被赞的动态正文","feedTypeName":"动态","url":"/feed/9"
                }
                """,
            ),
        )
        assertEquals("小红", display.actorName)
        assertEquals("22", display.actorUid)
        assertEquals("like-avatar", display.actorAvatar)
        assertEquals("赞了你的动态", display.action)
        assertEquals("被赞的动态正文", display.content)
        assertEquals("/feed/9", display.link)
    }

    @Test
    fun replyPrefersThePostAnchorOverTheSenderProfileUrl() {
        val display = notificationDisplay(
            entity(
                """
                {
                  "entityType":"notification","type":"feed_reply",
                  "fromusername":"奥菲利娅的影子","fromuid":"2975822",
                  "url":"/u/2975822",
                  "note":"评论了你的动态：<a href=\"/feed/74441925?rid=612261250\">来个链接</a>"
                }
                """,
            ),
        )
        assertEquals("/feed/74441925?rid=612261250", display.link)
    }

    @Test
    fun systemNotePrefersContentLinkOverProfileLink() {
        val display = notificationDisplay(
            entity(
                """
                {
                  "entityType":"notification","type":"notify_system",
                  "fromusername":"酷安小秘书","fromuid":"1",
                  "note":"<a href=\"/u/1\">酷安小秘书</a> 你的动态已通过审核 <a href=\"/feed/77\">查看</a>"
                }
                """,
            ),
        )
        assertEquals("/feed/77", display.link)
        assertEquals("酷安小秘书", display.actorName)
    }

    @Test
    fun summaryFallsBackAndStripsHtml() {
        val display = notificationDisplay(
            entity("""{"fromusername":"酷友","note":"<b>回复了你</b>&nbsp;一条评论"}"""),
        )
        assertEquals("回复了你 一条评论", plainNotificationText(display.action))
        assertEquals("回复了你 一条评论", display.summary("备用文案"))
        assertEquals("备用文案", entity("""{"fromusername":"酷友"}""").let(::notificationDisplay).summary("备用文案"))
        assertTrue(plainNotificationText("<a href=\"x\">文本</a>").contains("文本"))
    }

    @Test
    fun feedLikeRowReadsTheNestedLikerAndFeedType() {
        val display = notificationDisplay(
            entity(
                """
                {
                  "entityType":"feed","entityId":74441925,"feedTypeName":"动态","infoHtml":"",
                  "message":"被赞的动态正文","url":"/feed/74441925",
                  "likeUid":"1063928","likeUsername":"Moscaver","likeTime":1792308601,
                  "likeAvatar":"like-avatar",
                  "likeUserInfo":{"uid":"1063928","username":"Moscaver","userAvatar":"like-avatar"},
                  "likenum":4,"recent_like_list":"Moscaver,凝白舟,江戸川コナン,月半月半白勺MAN"
                }
                """,
            ),
        )
        assertEquals("Moscaver", display.actorName)
        assertEquals("1063928", display.actorUid)
        assertEquals("like-avatar", display.actorAvatar)
        assertEquals("赞了你的动态", display.action)
        assertEquals("被赞的动态正文", display.content)
        assertEquals("/feed/74441925", display.link)
    }
}
