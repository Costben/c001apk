package app.awavenue.c001apk.data

import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

typealias UserFeedFetcher = suspend (uid: String, page: Int, firstItem: String?, lastItem: String?) -> List<Entity>

/**
 * Merges several users' public timelines newest first, each user keeping its own cursor. At most
 * four users are read at once, and a user whose read failed stays retryable on the next call.
 */
class LocalFollowPager(uids: Iterable<String>, private val fetch: UserFeedFetcher) {
    private class Cursor(val uid: String) {
        var page = 1
        var first: String? = null
        var last: String? = null
        var end = false
        val buffer = mutableListOf<Entity>()
        val seen = HashSet<String>()
    }

    private val cursors = uids.distinct().map(::Cursor)
    private val seen = HashSet<String>()

    /** Why the last [next] returned fewer posts than it could have, or null when nothing failed. */
    var warning: String? = null
        private set

    val isEnd: Boolean get() = cursors.all { it.end && it.buffer.isEmpty() }

    suspend fun next(limit: Int = 20): List<Entity> {
        val output = mutableListOf<Entity>()
        val failures = LinkedHashMap<Cursor, String>()
        val deferred = HashSet<Cursor>()
        warning = null
        while (output.size < limit) {
            val pending = cursors.filter { !it.end && it.buffer.isEmpty() && it !in failures && it !in deferred }
            for (chunk in pending.chunked(4)) {
                coroutineScope {
                    chunk.map { cursor -> async { fill(cursor, failures, deferred) } }.awaitAll()
                }
            }
            val available = cursors.filter { it.buffer.isNotEmpty() }.maxByOrNull { it.buffer.first().time } ?: break
            val item = available.buffer.removeAt(0)
            if (seen.add(item.id)) output.add(item)
        }
        warning = when {
            failures.isNotEmpty() -> "${failures.size} 位用户的动态加载失败：${failures.values.first()}"
            deferred.isNotEmpty() -> "部分动态已过滤，请继续加载"
            else -> null
        }
        return output
    }

    private suspend fun fill(cursor: Cursor, failures: MutableMap<Cursor, String>, deferred: MutableSet<Cursor>) {
        // Bounded work even when every page holds only blocked or duplicate posts.
        repeat(3) {
            val items = try {
                fetch(cursor.uid, cursor.page, cursor.first, cursor.last)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(failures) { failures[cursor] = ApiFailure.from(e).message }
                return
            }
            if (items.isEmpty()) {
                cursor.end = true
                return
            }
            cursor.end = items.any { it.entityTemplate == "noMoreDataCard" }
            val feeds = items.filter { it.entityType == "feed" && it.id.isNotEmpty() && it.uid == cursor.uid }
            if (cursor.first == null) cursor.first = feeds.firstOrNull()?.id
            cursor.last = feeds.lastOrNull()?.id ?: cursor.last
            cursor.page++
            cursor.buffer.addAll(feeds.filter { cursor.seen.add(it.id) })
            cursor.buffer.sortByDescending { it.time }
            if (cursor.buffer.isNotEmpty() || cursor.end) return
        }
        synchronized(deferred) { deferred.add(cursor) }
    }

    private val Entity.time: Long get() = dateline ?: 0L
}
