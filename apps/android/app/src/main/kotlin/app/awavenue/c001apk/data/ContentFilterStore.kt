package app.awavenue.c001apk.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.awavenue.c001apk.model.ContentFilterPreferences
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

    private fun read(): ContentFilterPreferences =
        prefs.getString(KEY, null)?.let(::decodeContentFilter) ?: ContentFilterPreferences()

    private companion object {
        const val KEY = "preferences"
    }
}
