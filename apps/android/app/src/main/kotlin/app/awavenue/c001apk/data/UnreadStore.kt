package app.awavenue.c001apk.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.awavenue.c001apk.model.Entity
import kotlinx.coroutines.CancellationException

/**
 * The account's unread counters behind the top-right badge and the notification shortcuts.
 *
 * `checkCount` is polled instead of being fetched by each entry point, so the bell, the shortcut
 * tiles and the system reminders all read the same snapshot. Counts arrive as old `notification` /
 * `badge` fields on some accounts and as `*_v18` on others; only the counters backing the five
 * visible lists are used here, so the bell can never exceed what the tiles show.
 *
 * The server keeps reporting counters that were already read on this device, so the store also
 * remembers the value each counter had when its list was last opened and hands out the difference.
 */
class UnreadStore(
    context: Context,
    private val repository: CoolapkRepository,
    private val session: SessionStore,
) {
    private val prefs = context.getSharedPreferences("unread", Context.MODE_PRIVATE)

    var counts by mutableStateOf<Entity?>(null)
        private set
    var total by mutableIntStateOf(0)
        private set

    /** Bumped whenever the unread snapshot changes; badges read it to recompose. */
    var revision by mutableIntStateOf(0)
        private set

    fun count(field: String): Int = counts?.int(field) ?: 0

    /** Counter growth since [markRead], so an opened list stops badging. */
    fun unread(field: String): Int {
        revision
        return (count(field) - prefs.getInt(seenKey(field), 0)).coerceAtLeast(0)
    }

    fun unread(vararg fields: String): Int = fields.sumOf(::unread)

    fun clear() {
        counts = null
        total = 0
        revision++
    }

    suspend fun refresh() {
        val uid = session.uid
        if (!session.isLogin) {
            clear()
            return
        }
        val result = try {
            // A counter refresh must never stop the UI for a captcha.
            repository.checkCount(skipCaptcha = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        // An account switch while the request was in flight owns the newer counters.
        if (uid != session.uid) return
        counts = result
        // A counter can fall when another device reads it; never leave its snapshot above it.
        val editor = prefs.edit()
        var clamped = false
        for (field in visibleFields) {
            val current = result.int(field) ?: continue
            if (current < prefs.getInt(seenKey(uid, field), 0)) {
                editor.putInt(seenKey(uid, field), current)
                clamped = true
            }
        }
        if (clamped) editor.apply()
        total = visibleTotal(result)
        revision++
    }

    /** Notes that the counters behind [fields] have been seen, so their badges drop until growth. */
    fun markRead(vararg fields: String) {
        val uid = session.uid
        if (uid.isEmpty()) return
        val current = counts ?: return
        val editor = prefs.edit()
        for (field in fields) {
            current.int(field)?.let { editor.putInt(seenKey(uid, field), it) }
        }
        editor.apply()
        total = visibleTotal(current)
        revision++
    }

    fun markAllRead() = markRead(*visibleFields.toTypedArray())

    private fun visibleTotal(count: Entity): Int = visibleFields.sumOf { field ->
        ((count.int(field) ?: 0) - prefs.getInt(seenKey(session.uid, field), 0)).coerceAtLeast(0)
    }

    private fun seenKey(field: String) = seenKey(session.uid, field)

    private fun seenKey(uid: String, field: String) = "$uid.seen.$field"

    companion object {
        /** Counters behind 点赞 / 评论 / @我 / 关注 / 私信; system streams are deliberately absent. */
        private val visibleFields = listOf(
            "atme",
            "atcommentme",
            "commentme",
            "feedlike",
            "contacts_follow",
            "message",
        )

        /** Sum of the counters the five shortcut tiles expose. */
        fun totalOf(count: Entity): Int = visibleFields.sumOf { count.int(it) ?: 0 }
    }
}
