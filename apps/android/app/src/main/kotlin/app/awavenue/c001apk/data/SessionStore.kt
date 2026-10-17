package app.awavenue.c001apk.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The account identity sent as the Cookie on API requests. */
data class Session(
    val uid: String = "",
    val username: String = "",
    val token: String = "",
    val sessid: String = "",
) {
    val isLogin: Boolean get() = uid.isNotEmpty() && token.isNotEmpty()
}

/**
 * Credentials and the cached profile counters, persisted so a restart keeps the session.
 * SESSID comes from the login page's Set-Cookie and is only needed before a real login.
 */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var uid by mutableStateOf(prefs.getString(KEY_UID, "").orEmpty())
        private set
    var username by mutableStateOf(prefs.getString(KEY_USERNAME, "").orEmpty())
        private set
    var token by mutableStateOf(prefs.getString(KEY_TOKEN, "").orEmpty())
        private set
    var sessid by mutableStateOf(prefs.getString(KEY_SESSID, "").orEmpty())
        private set
    var isLogin by mutableStateOf(prefs.getBoolean(KEY_IS_LOGIN, false))
        private set
    var userAvatar by mutableStateOf(prefs.getString(KEY_AVATAR, "").orEmpty())
        private set
    var level by mutableIntStateOf(prefs.getInt(KEY_LEVEL, 0))
        private set
    var exp by mutableIntStateOf(prefs.getInt(KEY_EXP, 0))
        private set
    var nextExp by mutableIntStateOf(prefs.getInt(KEY_NEXT_EXP, 0))
        private set

    fun snapshot() = Session(uid, username, token, sessid)

    fun updateSessid(value: String) {
        if (value.isEmpty() || value == sessid) return
        sessid = value
        prefs.edit { putString(KEY_SESSID, value) }
    }

    /**
     * Bumped whenever the active account changes, so pages built for the previous account are
     * rebuilt and its late responses can be told apart. Not persisted: it only orders this process.
     */
    var generation by mutableIntStateOf(0)
        private set

    /**
     * Makes [account] the active session, or switches to guest when it is null. Only
     * [AccountStore] calls this, after it has recorded the change in the account vault.
     */
    internal fun activate(account: SavedAccount?, notify: Boolean) {
        uid = account?.uid.orEmpty()
        username = account?.username.orEmpty()
        token = account?.token.orEmpty()
        isLogin = account != null
        userAvatar = account?.avatar.orEmpty()
        level = account?.level ?: 0
        exp = account?.exp ?: 0
        nextExp = account?.nextExp ?: 1
        // The pre-login SESSID belongs to whoever was signing in; a new identity starts without it.
        if (notify || account == null) sessid = ""
        prefs.edit {
            putString(KEY_UID, uid)
            putString(KEY_USERNAME, username)
            putString(KEY_TOKEN, token)
            putBoolean(KEY_IS_LOGIN, isLogin)
            putString(KEY_AVATAR, userAvatar)
            putInt(KEY_LEVEL, level)
            putInt(KEY_EXP, exp)
            putInt(KEY_NEXT_EXP, nextExp)
            putString(KEY_SESSID, sessid)
        }
        if (notify) generation++
    }

    fun saveProfile(avatar: String, level: Int, exp: Int, nextExp: Int) {
        if (avatar.isNotEmpty()) {
            userAvatar = avatar
            prefs.edit { putString(KEY_AVATAR, avatar) }
        }
        this.level = level
        this.exp = exp
        this.nextExp = nextExp
        prefs.edit {
            putInt(KEY_LEVEL, level)
            putInt(KEY_EXP, exp)
            putInt(KEY_NEXT_EXP, nextExp)
        }
    }

    private inline fun SharedPreferences.edit(block: SharedPreferences.Editor.() -> Unit) {
        edit().apply(block).apply()
    }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_USERNAME = "username"
        const val KEY_TOKEN = "token"
        const val KEY_SESSID = "sessid"
        const val KEY_IS_LOGIN = "isLogin"
        const val KEY_AVATAR = "userAvatar"
        const val KEY_LEVEL = "level"
        const val KEY_EXP = "exp"
        const val KEY_NEXT_EXP = "nextExp"
    }
}
