package app.awavenue.c001apk.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One row of the account's cloud browse history (`/v6/user/hitHistoryList`). `id` is the paging
 * cursor and must be passed back unchanged; `dateline` is the server's own browse time.
 *
 * The cloud list is read-only: the client never uploads the local history, so an account that
 * browsed elsewhere shows what that device recorded.
 */
data class CloudHistoryEntry(
    val id: String,
    val title: String,
    val description: String,
    val logo: String,
    val url: String,
    val historyType: String,
    val typeName: String,
    val dateline: Long,
) {
    companion object {
        /** Reads `data` as the entry list; anything without an id or a target is dropped. */
        fun parse(root: JsonObject): List<CloudHistoryEntry> {
            val rows = root["data"] as? JsonArray ?: return emptyList()
            return rows.mapNotNull { row ->
                val obj = row as? JsonObject ?: return@mapNotNull null
                val id = obj.text("id").ifEmpty { return@mapNotNull null }
                val url = obj.text("url").ifEmpty { return@mapNotNull null }
                CloudHistoryEntry(
                    id = id,
                    title = obj.text("title"),
                    description = obj.text("description"),
                    logo = obj.text("logo"),
                    url = url,
                    historyType = obj.text("historyType"),
                    typeName = obj.text("typeName"),
                    dateline = obj.text("dateline").toLongOrNull() ?: 0L,
                )
            }
        }

        private fun JsonObject.text(key: String): String =
            (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
