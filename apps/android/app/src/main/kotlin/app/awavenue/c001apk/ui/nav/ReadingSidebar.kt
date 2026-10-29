package app.awavenue.c001apk.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The list pane beside a wide-screen detail. A page that reads as a column of text — a feed or an
 * article — can give the list its width back and take the whole window, then return to the split
 * without losing where the reader was.
 */
@Stable
class ReadingSidebar {
    var hidden by mutableStateOf(false)
        private set

    fun toggle() {
        hidden = !hidden
    }
}

/**
 * Null on a phone, and on any page that is not hosted in the detail pane; the reader's bar only
 * shows the toggle when there is a list beside it to hide.
 */
val LocalReadingSidebar = staticCompositionLocalOf<ReadingSidebar?> { null }
