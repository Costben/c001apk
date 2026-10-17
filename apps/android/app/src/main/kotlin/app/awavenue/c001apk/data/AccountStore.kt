package app.awavenue.c001apk.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLDecoder

/**
 * One signed-in account kept on this device. Passwords are never kept, and the token must never be
 * shown or logged.
 */
class SavedAccount(
    val uid: String,
    val username: String,
    internal val token: String,
    val avatar: String = "",
    val level: Int = 0,
    val exp: Int = 0,
    val nextExp: Int = 1,
) {
    val needsLogin: Boolean get() = token.isEmpty()

    /** The cookie carries the name URL-encoded. */
    val displayName: String
        get() = runCatching { URLDecoder.decode(username, "UTF-8") }.getOrDefault(username)

    internal fun encode() = JsonObject(
        mapOf(
            "uid" to JsonPrimitive(uid),
            "username" to JsonPrimitive(username),
            "token" to JsonPrimitive(token),
            "avatar" to JsonPrimitive(avatar),
            "level" to JsonPrimitive(level),
            "exp" to JsonPrimitive(exp),
            "nextExp" to JsonPrimitive(nextExp),
        ),
    )

    internal companion object {
        private val UID = Regex("^\\d+$")

        fun decode(value: Any?): SavedAccount? {
            val obj = value as? JsonObject ?: return null
            fun text(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun number(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            val uid = text("uid")?.takeIf(UID::matches) ?: return null
            return SavedAccount(
                uid = uid,
                username = text("username").orEmpty(),
                token = text("token").orEmpty(),
                avatar = text("avatar").orEmpty(),
                level = number("level") ?: 0,
                exp = number("exp") ?: 0,
                nextExp = number("nextExp")?.takeIf { it > 0 } ?: 1,
            )
        }
    }
}

/**
 * The accounts signed in on this device, and which one is active. The vault record is the single
 * source of truth: it is written first, then mirrored into [SessionStore], which every request
 * reads. History, favourites, blocking and local follows stay shared across accounts.
 */
class AccountStore(context: Context, private val session: SessionStore) {
    private val prefs: SharedPreferences = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    var accounts by mutableStateOf(readAccounts())
        private set

    init {
        if (prefs.getString(KEY_VAULT, null) == null) {
            // First run with the vault: keep the login an earlier build left behind.
            persist(withCurrent(), current(), notify = false)
        } else {
            val activeUid = readVault()?.let { (it["activeUid"] as? JsonPrimitive)?.contentOrNull }
            val active = accounts.firstOrNull { it.uid == activeUid && !it.needsLogin }
            // Repairs the session mirror if a previous write was interrupted between the two stores.
            persist(accounts, active, notify = false)
        }
    }

    val activeUid: String? get() = current()?.uid

    /** Records a fresh login and makes it active, keeping every other saved account. */
    fun saveLogin(uid: String, username: String, token: String) {
        if (!UID.matches(uid) || token.isEmpty()) throw IllegalStateException("登录响应缺少有效账号信息")
        val all = withCurrent().toMutableList()
        val previous = all.firstOrNull { it.uid == uid }
        val account = SavedAccount(
            uid = uid,
            username = username,
            token = token,
            avatar = previous?.avatar.orEmpty(),
            level = previous?.level ?: 0,
            exp = previous?.exp ?: 0,
            nextExp = previous?.nextExp ?: 1,
        )
        all.removeAll { it.uid == uid }
        all.add(account)
        persist(all, account)
    }

    fun switchTo(uid: String) {
        val all = withCurrent()
        val account = all.firstOrNull { it.uid == uid }
        if (account == null || account.needsLogin) throw IllegalStateException("此账号需要重新登录")
        if (session.isLogin && session.uid == uid) return
        persist(all, account)
    }

    /** Browses as a guest while keeping every saved account for later. */
    fun useGuest() = persist(withCurrent(), null)

    fun remove(uid: String) {
        val all = withCurrent().filterNot { it.uid == uid }
        val removingCurrent = session.uid == uid
        persist(all, if (removingCurrent) null else current(), notify = removingCurrent && session.isLogin)
    }

    /** Keeps the profile the 我的 page just read, so the account list shows it too. */
    fun refreshCurrent(username: String?, avatar: String?, level: Int?, exp: Int?, nextExp: Int?) {
        val current = current() ?: return
        val updated = SavedAccount(
            uid = current.uid,
            username = username?.ifEmpty { null } ?: current.username,
            token = current.token,
            avatar = avatar?.ifEmpty { null } ?: current.avatar,
            level = level ?: current.level,
            exp = exp ?: current.exp,
            nextExp = nextExp?.takeIf { it > 0 } ?: current.nextExp,
        )
        persist(accounts.filterNot { it.uid == updated.uid } + updated, updated, notify = false)
    }

    private fun current(): SavedAccount? =
        if (session.isLogin && session.uid.isNotEmpty() && session.token.isNotEmpty()) {
            SavedAccount(
                uid = session.uid,
                username = session.username,
                token = session.token,
                avatar = session.userAvatar,
                level = session.level,
                exp = session.exp,
                nextExp = session.nextExp.takeIf { it > 0 } ?: 1,
            )
        } else {
            null
        }

    private fun withCurrent(): List<SavedAccount> {
        val current = current() ?: return accounts
        return accounts.filterNot { it.uid == current.uid } + current
    }

    private fun persist(all: List<SavedAccount>, active: SavedAccount?, notify: Boolean = true) {
        val vault = JsonObject(
            mapOf(
                "accounts" to JsonArray(all.map { it.encode() }),
                "activeUid" to (active?.uid?.let(::JsonPrimitive) ?: JsonNull),
            ),
        )
        prefs.edit().putString(KEY_VAULT, vault.toString()).commit()
        accounts = all
        session.activate(active, notify)
    }

    private fun readVault(): JsonObject? = prefs.getString(KEY_VAULT, null)?.let {
        runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull()
    }

    private fun readAccounts(): List<SavedAccount> =
        (readVault()?.get("accounts") as? JsonArray)?.mapNotNull(SavedAccount::decode).orEmpty()

    private companion object {
        const val KEY_VAULT = "accountVaultV1"
        val UID = Regex("^\\d+$")
    }
}
