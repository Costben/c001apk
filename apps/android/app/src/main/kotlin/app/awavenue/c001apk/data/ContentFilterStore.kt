package app.awavenue.c001apk.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.awavenue.c001apk.model.ContentFilterPreferences
import app.awavenue.c001apk.model.TwilightRuleSet
import app.awavenue.c001apk.model.decodeContentFilter
import app.awavenue.c001apk.model.toJson

/**
 * The user's content rules, stored the way the block lists are: one JSON value in
 * SharedPreferences. [preferences] is observable, so a list already on screen can re-apply the
 * rules without a network refresh.
 */
class ContentFilterStore(context: Context) {
    private val prefs = context.getSharedPreferences("contentFilter", Context.MODE_PRIVATE)

    var preferences by mutableStateOf(read())
        private set

    fun save(next: ContentFilterPreferences) {
        if (next == preferences) return
        preferences = next
        prefs.edit().putString(KEY, next.toJson().toString()).apply()
    }

    fun update(block: (ContentFilterPreferences) -> ContentFilterPreferences) = save(block(preferences))

    /**
     * Replaces the remote snapshot, keeping personal rules, scope choices and the per-rule picks.
     * Returns the reason it was rejected, or null once imported.
     */
    fun importTwilight(candidate: TwilightRuleSet): String? {
        val previous = preferences.twilight
        val problem = runCatching {
            candidate.validate()
            require(previous == null || candidate.revision > previous.revision || candidate == previous) {
                "远程版本过旧或同版本内容改变，保留已有规则"
            }
        }.exceptionOrNull()
        if (problem != null) return problem.message ?: "远程规则校验失败"
        update { it.copy(twilight = candidate) }
        return null
    }

    fun setTwilightStrict(value: Boolean) = update { it.copy(twilightStrict = value) }

    fun setTwilightRuleEnabled(key: String, enabled: Boolean) = update { current ->
        current.copy(
            disabledTwilightRules = if (enabled) {
                current.disabledTwilightRules - key
            } else {
                current.disabledTwilightRules + key
            },
        )
    }

    private fun read(): ContentFilterPreferences =
        prefs.getString(KEY, null)?.let(::decodeContentFilter) ?: ContentFilterPreferences()

    private companion object {
        const val KEY = "preferences"
    }
}
