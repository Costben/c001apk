package app.awavenue.c001apk.data

import app.awavenue.c001apk.model.ContentFilterEngine
import app.awavenue.c001apk.model.ContentFilterPreferences
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.model.FilterScope

/**
 * The one decision every list asks per row: does the block list or a content rule hide it?
 *
 * It used to live inside the response parser, which meant a hidden row still consumed a page slot
 * and nothing could count what disappeared. Built from the preferences in effect when a page is
 * loaded, so a rule added mid-session applies on the next refresh — and, in the list itself, at
 * once to the rows already on screen.
 */
class ContentGate(
    private val blacklist: BlacklistStore,
    preferences: ContentFilterPreferences,
) {
    private val engine = ContentFilterEngine(preferences)

    val isEnabled get() = engine.isEnabled

    fun isVisible(item: Entity, scope: FilterScope? = null): Boolean {
        if (blacklist.isUserBlocked(item.uid)) return false
        if (blacklist.isTopicBlocked(item.tags, item.ttitle, item.relationRows.firstOrNull()?.title.orEmpty())) {
            return false
        }
        return engine.match(item, scope) == null
    }
}
