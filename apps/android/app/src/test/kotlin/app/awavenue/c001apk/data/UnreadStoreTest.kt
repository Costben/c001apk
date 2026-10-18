package app.awavenue.c001apk.data

import app.awavenue.c001apk.model.Entity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadStoreTest {
    private fun entity(json: String) = Entity(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun sumsTheCountersBehindTheFiveShortcutTiles() {
        val total = UnreadStore.totalOf(
            entity(
                """
                {"atme":1,"atcommentme":2,"commentme":3,"feedlike":4,
                 "contacts_follow":5,"message":6,"notification":7}
                """,
            ),
        )
        assertEquals(21, total)
    }

    @Test
    fun ignoresTheSystemStreamAndTheServerBadge() {
        val total = UnreadStore.totalOf(
            entity(
                """
                {"atme":1,"feedlike":2,"notification":0,"badge":0,
                 "notification_v18":9,"badge_v18":40}
                """,
            ),
        )
        assertEquals(3, total)
    }

    @Test
    fun missingCountersReadAsZero() {
        assertEquals(0, UnreadStore.totalOf(entity("""{"badge":12}""")))
    }
}
