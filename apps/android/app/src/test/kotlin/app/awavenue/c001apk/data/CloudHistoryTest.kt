package app.awavenue.c001apk.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudHistoryTest {
    private fun parse(json: String) = CloudHistoryEntry.parse(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun readsRowsAndKeepsThePagingCursor() {
        val rows = parse(
            """
            {"data":[
              {"id":"88","title":"一条动态","description":"描述","logo":"https://example.com/a.jpg",
               "url":"https://www.coolapk.com/feed/1","historyType":"feed","typeName":"动态","dateline":"1700000000"},
              {"id":"87","title":"一个用户","url":"https://www.coolapk.com/u/2","dateline":1700000001}
            ]}
            """,
        )
        assertEquals(2, rows.size)
        assertEquals("88", rows[0].id)
        assertEquals("https://www.coolapk.com/feed/1", rows[0].url)
        assertEquals("动态", rows[0].typeName)
        assertEquals(1700000000L, rows[0].dateline)
        assertEquals(1700000001L, rows[1].dateline)
    }

    @Test
    fun dropsRowsWithoutAnIdOrTarget() {
        val rows = parse("""{"data":[{"title":"没有跳转"},{"id":"1","url":"https://example.com"}]}""")
        assertEquals(1, rows.size)
        assertEquals("1", rows[0].id)
    }

    @Test
    fun anUnexpectedShapeIsEmptyRatherThanFatal() {
        assertTrue(parse("""{"data":null}""").isEmpty())
        assertTrue(parse("""{}""").isEmpty())
    }
}
