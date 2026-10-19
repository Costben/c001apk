package app.awavenue.c001apk.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The bottom edge of each main tab's app bar, in the route host's coordinates.
 *
 * A post row can sit partly under the bar, so a page shrinking back into it would leave the row's
 * header — avatar, name, device — hidden behind the bar. When a card returns, the host scrolls the
 * list just enough to bring the row's top below this line.
 *
 * The fields are plain vars: the bar's height follows its collapse during scroll, and a snapshot
 * write would invalidate composition every frame. They are written from layout callbacks and read
 * once the return begins.
 */
class AppBarBounds {
    private val heights = FloatArray(3)

    /** Index of the tab whose bar is on screen; set by the main screen. */
    var currentTab: Int = 0

    /** Called by the tab at [slot] whenever its bar is measured. Zero means "nothing reported". */
    fun report(slot: Int, bottom: Float) {
        if (slot in heights.indices) heights[slot] = bottom
    }

    val bottom: Float
        get() = heights.getOrElse(currentTab) { 0f }
}

val LocalAppBarBounds = staticCompositionLocalOf { AppBarBounds() }

/** The list that hosts the card currently being opened, so a return can scroll it into view. */
val LocalFeedListState = staticCompositionLocalOf<androidx.compose.foundation.lazy.LazyListState?> { null }
