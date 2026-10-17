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
}
