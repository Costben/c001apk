package app.awavenue.c001apk.data

import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.network.ApiFailureKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFollowPagerTest {
    private fun feed(id: Int, uid: String, dateline: Long) = Entity(
        JsonObject(
            mapOf(
                "entityType" to JsonPrimitive("feed"),
                "id" to JsonPrimitive(id.toString()),
                "uid" to JsonPrimitive(uid),
                "dateline" to JsonPrimitive(dateline),
            ),
        ),
    )

    /** Each user's pages, newest first, the way the user feed endpoint answers. */
    private fun fetcher(pages: Map<String, List<List<Entity>>>, calls: MutableList<String> = mutableListOf()): UserFeedFetcher =
        { uid, page, _, _ ->
            calls += "$uid:$page"
            pages[uid]?.getOrNull(page - 1).orEmpty()
        }

    @Test
    fun mergesUsersNewestFirst() = runBlocking {
        val pager = LocalFollowPager(
            listOf("1", "2"),
            fetcher(
                mapOf(
                    "1" to listOf(listOf(feed(11, "1", 300), feed(12, "1", 100))),
                    "2" to listOf(listOf(feed(21, "2", 200), feed(22, "2", 50))),
                ),
            ),
        )
        assertEquals(listOf("11", "21", "12", "22"), pager.next().map { it.id })
        assertNull(pager.warning)
        assertTrue(pager.isEnd)
        assertEquals(emptyList<Entity>(), pager.next())
    }

    @Test
    fun keepsEachUsersCursorAcrossCalls() = runBlocking {
        val calls = mutableListOf<String>()
        val pager = LocalFollowPager(
            listOf("1"),
            fetcher(mapOf("1" to listOf(listOf(feed(1, "1", 3), feed(2, "1", 2)), listOf(feed(3, "1", 1)))), calls),
        )
        assertEquals(listOf("1"), pager.next(limit = 1).map { it.id })
        assertEquals(listOf("2", "3"), pager.next(limit = 2).map { it.id })
        assertEquals(listOf("1:1", "1:2"), calls)
    }

    @Test
    fun dropsOtherAuthorsAndRepeats() = runBlocking {
        val pager = LocalFollowPager(
            listOf("1", "1"),
            fetcher(mapOf("1" to listOf(listOf(feed(1, "1", 3), feed(9, "2", 2), feed(1, "1", 3))))),
        )
        assertEquals(listOf("1"), pager.next().map { it.id })
    }

    @Test
    fun aFailedUserLeavesTheOthersAndAWarning() = runBlocking {
        var failing = true
        val pages = mapOf("2" to listOf(listOf(feed(21, "2", 200))))
        val pager = LocalFollowPager(listOf("1", "2")) { uid, page, first, last ->
            if (uid == "1" && failing) throw ApiFailure(ApiFailureKind.Transport, "网络连接失败")
            if (uid == "1") listOf(feed(11, "1", 100)).takeIf { page == 1 }.orEmpty() else fetcher(pages)(uid, page, first, last)
        }
        assertEquals(listOf("21"), pager.next().map { it.id })
        assertEquals("1 位用户的动态加载失败：网络连接失败", pager.warning)
        failing = false
        assertEquals(listOf("11"), pager.next().map { it.id })
        assertNull(pager.warning)
    }
}
