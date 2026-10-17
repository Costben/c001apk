package app.awavenue.c001apk.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class LocalFollowUser(val uid: String, val name: String, val avatar: String)

/**
 * Users followed on this device only. Deliberately independent of the active account: signing in,
 * out or switching keeps the list, and nothing is ever sent to the account.
 */
class LocalFollowStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("local_follow", Context.MODE_PRIVATE)

    var users by mutableStateOf(read())
        private set

    /** 悄悄关注: lets a signed-in account long-press 关注 to add to this list instead. */
    var secretEnabled by mutableStateOf(prefs.getBoolean(KEY_ENABLED, false))
        private set

    fun contains(uid: String): Boolean = users.any { it.uid == uid }

    fun follow(uid: String, name: String, avatar: String) {
        require(validUid(uid)) { "uid" }
        save(listOf(LocalFollowUser(uid, name, avatar)) + users.filterNot { it.uid == uid })
    }

    fun unfollow(uid: String) = save(users.filterNot { it.uid == uid })

    fun updateSecretEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        secretEnabled = value
    }

    private fun save(entries: List<LocalFollowUser>) {
        val array = JsonArray(
            entries.map {
                JsonObject(
                    mapOf(
                        "uid" to JsonPrimitive(it.uid),
                        "name" to JsonPrimitive(it.name),
                        "avatar" to JsonPrimitive(it.avatar),
                    ),
                )
            },
        )
        if (!prefs.edit().putString(KEY_USERS, array.toString()).commit()) error("本地关注保存失败，请重试")
        users = entries
    }

    private fun read(): List<LocalFollowUser> {
        val raw = prefs.getString(KEY_USERS, null) ?: return emptyList()
        val array = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = (obj[key] as? JsonPrimitive)?.content.orEmpty()
            text("uid").takeIf(::validUid)?.let { LocalFollowUser(it, text("name"), text("avatar")) }
        }
    }

    private companion object {
        const val KEY_USERS = "localFollowUsers"
        const val KEY_ENABLED = "secretFollowEnabled"

        fun validUid(uid: String) = uid.isNotEmpty() && uid.all(Char::isDigit) && (uid.toLongOrNull() ?: 0L) > 0
    }
}
