package app.awavenue.c001apk.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One remembered feed: enough to render the row without a request. */
data class FavHistoryItem(
    val id: String,
    val uid: String,
    val username: String,
    val userAvatar: String,
    val message: String,
    val device: String,
    val dateline: Long,
    val time: Long,
) {
    fun toJson() = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("uid", JsonPrimitive(uid))
        put("username", JsonPrimitive(username))
        put("userAvatar", JsonPrimitive(userAvatar))
        put("message", JsonPrimitive(message))
        put("device", JsonPrimitive(device))
        put("dateline", JsonPrimitive(dateline))
        put("time", JsonPrimitive(time))
    }

    companion object {
        fun fromJson(obj: JsonObject) = FavHistoryItem(
            id = obj.text("id"),
            uid = obj.text("uid"),
            username = obj.text("username"),
            userAvatar = obj.text("userAvatar"),
            message = obj.text("message"),
            device = obj.text("device"),
            dateline = obj.text("dateline").toLongOrNull() ?: 0L,
            time = obj.text("time").toLongOrNull() ?: 0L,
        )

        private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}

enum class HistoryType(val label: String) { Fav("本地收藏"), History("浏览历史") }

/**
 * Locally kept favourites and browse history, newest first. The Flutter client keeps these in
 * two Hive boxes; here they are two JSON maps in one preferences file.
 */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    var favFeeds by mutableStateOf(read(KEY_FAV))
        private set
    var historyFeeds by mutableStateOf(read(KEY_HISTORY))
        private set

    fun isFav(id: String) = favFeeds.containsKey(id)

    fun toggleFav(item: FavHistoryItem) {
        favFeeds = if (favFeeds.containsKey(item.id)) favFeeds - item.id else favFeeds + (item.id to item)
        write(KEY_FAV, favFeeds)
    }

    /** Records a browse, skipping one already present so the list does not reshuffle on reload. */
    fun record(item: FavHistoryItem) {
        if (historyFeeds.containsKey(item.id)) return
        historyFeeds = historyFeeds + (item.id to item)
        write(KEY_HISTORY, historyFeeds)
    }

    fun remove(type: HistoryType, id: String) {
        when (type) {
            HistoryType.Fav -> {
                favFeeds = favFeeds - id
                write(KEY_FAV, favFeeds)
            }
            HistoryType.History -> {
                historyFeeds = historyFeeds - id
                write(KEY_HISTORY, historyFeeds)
            }
        }
    }

    fun clear(type: HistoryType) {
        when (type) {
            HistoryType.Fav -> {
                favFeeds = emptyMap()
                write(KEY_FAV, favFeeds)
            }
            HistoryType.History -> {
                historyFeeds = emptyMap()
                write(KEY_HISTORY, historyFeeds)
            }
        }
    }

    fun list(type: HistoryType): List<FavHistoryItem> =
        (if (type == HistoryType.Fav) favFeeds else historyFeeds).values.sortedByDescending { it.time }

    private fun write(key: String, values: Map<String, FavHistoryItem>) {
        val json = buildJsonObject { values.forEach { (id, item) -> put(id, item.toJson()) } }
        prefs.edit().putString(key, json.toString()).apply()
    }

    private fun read(key: String): Map<String, FavHistoryItem> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject.mapValues { (_, value) -> FavHistoryItem.fromJson(value.jsonObject) }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val KEY_FAV = "favFeed"
        const val KEY_HISTORY = "historyFeed"
    }
}
