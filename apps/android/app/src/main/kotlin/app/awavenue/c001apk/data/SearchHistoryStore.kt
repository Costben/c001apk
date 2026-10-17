package app.awavenue.c001apk.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/** Keywords typed into the search box, newest first, capped at the Flutter client's 20. */
class SearchHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("searchHistory", Context.MODE_PRIVATE)

    var keywords by mutableStateOf(read())
        private set

    fun add(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        keywords = (listOf(trimmed) + keywords.filterNot { it == trimmed }).take(LIMIT)
        write()
    }

    fun remove(keyword: String) {
        keywords = keywords - keyword
        write()
    }

    fun clear() {
        keywords = emptyList()
        write()
    }

    private fun read(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { (it as? JsonPrimitive)?.content }
        }.getOrDefault(emptyList())
    }

    private fun write() {
        val array = buildJsonArray { keywords.forEach { add(JsonPrimitive(it)) } }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "searchHistory"
        const val LIMIT = 20
    }
}
