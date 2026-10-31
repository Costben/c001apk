package app.awavenue.c001apk.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CoolapkRepositoryTest {
    @Test
    fun flattensListCardsAndDropsUnknownKinds() {
        val response = Json.parseToJsonElement(
            """
            {"data":[
              {"entityType":"feed","id":"1","entityId":"1"},
              {"entityType":"card","entityTemplate":"listCard","entities":[
                {"entityType":"card","entityTemplate":"titleCard","title":"热门"},
                {"entityType":"card","entityTemplate":"listCard","entities":[{"entityType":"user","uid":"7"}]}
              ]},
              {"entityType":"card","entityTemplate":"configCard"},
              {"entityType":"card","entityTemplate":"refreshCard","title":"没有更多了"},
              {"entityType":"unknownType"}
            ]}
            """,
        ).jsonObject
        val items = CoolapkRepository.parseList(response)
        assertEquals(listOf("feed", "card", "user", "card"), items.map { it.entityType })
        assertEquals(listOf("", "titleCard", "", "refreshCard"), items.map { it.entityTemplate })
    }

    @Test
    fun readsLikeCountFromObjectAndBareNumber() {
        val feedLike = Json.parseToJsonElement("""{"count":"12","likeStatus":1}""")
        val replyLike = Json.parseToJsonElement("7")
        assertEquals("12", ActionResult("", feedLike).count)
        assertEquals("7", ActionResult("", replyLike).count)
        assertEquals("", ActionResult("", null).count)
    }

    @Test
    fun mapsVisibilityScopeIntoPublishStatus() {
        // 仅自己可见: the composer marks the scope on status, the form carries publish_status.
        val selfOnly = normalizeFeedFields(listOf("message" to "正文", "status" to "-1")).toMap()
        assertEquals("1", selfOnly["status"])
        assertEquals("1", selfOnly["publish_status"])

        val public = normalizeFeedFields(listOf("message" to "正文", "status" to "1")).toMap()
        assertEquals("1", public["status"])
        assertEquals("0", public["publish_status"])

        val untouched = normalizeFeedFields(listOf("message" to "正文")).toMap()
        assertEquals("0", untouched["publish_status"])
    }

    @Test
    fun keepsAnExplicitPublishStatus() {
        val fields = normalizeFeedFields(listOf("status" to "-1", "publish_status" to "0")).toMap()
        assertEquals("0", fields["publish_status"])
        assertEquals("1", fields["status"])
    }
}
