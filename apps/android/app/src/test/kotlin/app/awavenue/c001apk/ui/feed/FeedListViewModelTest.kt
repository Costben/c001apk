package app.awavenue.c001apk.ui.feed

import app.awavenue.c001apk.model.Entity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedListViewModelTest {
    private fun feed(id: String, likes: Int = 0) = Entity(
        Json.parseToJsonElement(
            """{"entityType":"feed","entityId":"$id","id":"$id","likenum":"$likes"}""",
        ).jsonObject,
    )

    @Test
    fun mergeReturnsNullWhenTopItemsAreUnchanged() {
        val existing = listOf(feed("10"), feed("9"), feed("8"), feed("7"))
        val refreshed = listOf(feed("10"), feed("9"))

        assertNull(mergeRefreshedFeedItems(existing, refreshed))
    }

    @Test
    fun mergePrependsNewItemsAndPreservesPaginatedTailWhenOverlapping() {
        val existing = listOf(feed("10"), feed("9"), feed("8"))
        val refreshed = listOf(feed("11"), feed("10"), feed("9", likes = 5))

        val merged = mergeRefreshedFeedItems(existing, refreshed)!!
        assertEquals(listOf("11", "10", "9", "8"), merged.map { it.entityId })
        assertEquals("5", merged[2].likeNum)
    }

    @Test
    fun mergeReplacesListWhenNoOverlapExists() {
        val existing = listOf(feed("10"), feed("9"))
        val refreshed = listOf(feed("20"), feed("19"))

        val merged = mergeRefreshedFeedItems(existing, refreshed)!!
        assertEquals(listOf("20", "19"), merged.map { it.entityId })
    }
}
