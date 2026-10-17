package app.awavenue.c001apk.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * Blocked users and topic keywords. A user is matched on the exact uid; a topic keyword is
 * matched as a substring of the feed's tags, title and first relation row.
 */
class BlacklistStore(context: Context) {
    private val prefs = context.getSharedPreferences("blackList", Context.MODE_PRIVATE)

    var userBlacklist by mutableStateOf(read(KEY_USER))
        private set
    var topicBlacklist by mutableStateOf(read(KEY_TOPIC))
        private set

    fun blockUser(uid: String) {
        if (uid.isEmpty() || uid in userBlacklist) return
        userBlacklist = userBlacklist + uid
        write(KEY_USER, userBlacklist)
    }

    fun unblockUser(uid: String) {
        if (uid !in userBlacklist) return
        userBlacklist = userBlacklist - uid
        write(KEY_USER, userBlacklist)
    }

    fun blockTopic(keyword: String) {
        if (keyword.isEmpty() || keyword in topicBlacklist) return
        topicBlacklist = topicBlacklist + keyword
        write(KEY_TOPIC, topicBlacklist)
    }

    fun unblockTopic(keyword: String) {
        if (keyword !in topicBlacklist) return
        topicBlacklist = topicBlacklist - keyword
        write(KEY_TOPIC, topicBlacklist)
    }

    fun clear(type: BlacklistType) {
        when (type) {
            BlacklistType.User -> {
                userBlacklist = emptyList()
                write(KEY_USER, userBlacklist)
            }
            BlacklistType.Topic -> {
                topicBlacklist = emptyList()
                write(KEY_TOPIC, topicBlacklist)
            }
        }
    }

    fun replace(type: BlacklistType, values: List<String>) {
        when (type) {
            BlacklistType.User -> {
                userBlacklist = values
                write(KEY_USER, values)
            }
            BlacklistType.Topic -> {
                topicBlacklist = values
                write(KEY_TOPIC, values)
            }
        }
    }

    fun isUserBlocked(uid: String) = uid.isNotEmpty() && uid in userBlacklist

    fun isTopicBlocked(tags: String, title: String, relationTitle: String): Boolean {
        if (topicBlacklist.isEmpty()) return false
        val haystack = "$tags,$title,$relationTitle"
        return topicBlacklist.any { haystack.contains(it) }
    }

    private fun read(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { (it as? JsonPrimitive)?.content }
        }.getOrDefault(emptyList())
    }

    private fun write(key: String, values: List<String>) {
        val array = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val KEY_USER = "userBlackList"
        const val KEY_TOPIC = "topicBlackList"
    }
}

enum class BlacklistType(val label: String) { User("用户"), Topic("话题") }
