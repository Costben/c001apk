package app.awavenue.c001apk.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The bottom edge of each main tab's app bar, in the route host's coordinates.
 *
 * A route that shrinks back into a card draws its frame over the whole screen, which put the
 * shrinking card above the home bar even though the card in the list sits under it. The route host
 * clips an exiting shrink at the current tab's line, so the card returns underneath the bar.
 *
 * The fields are plain vars: the bar's height follows its collapse during scroll, and a snapshot
 * write would invalidate composition every frame. They are read in the draw phase instead.
 */
class AppBarBounds {
    private val heights = FloatArray(3)

    /** Index of the tab whose bar is on screen; set by the main screen. */
    var currentTab: Int = 0

    /** Called by the tab at [slot] whenever its bar is measured. Zero means "protect nothing". */
    fun report(slot: Int, bottom: Float) {
        if (slot in heights.indices) heights[slot] = bottom
    }

    val bottom: Float
        get() = heights.getOrElse(currentTab) { 0f }
}

val LocalAppBarBounds = staticCompositionLocalOf { AppBarBounds() }

/**
 * How far the route leaving in front of this page has travelled: 1 at rest, 0 once it is gone.
 *
 * A page returning under a shrinking card is revealed from the card's edge outward. Its app bar can
 * be uncovered early — the post may sit right below it — which made the bar snap in at full strength
 * the instant the card moved. A page can hold the bar back and fade it in over the last part of the
 * exit instead. The value is written by the route host and read from draw blocks, so following the
 * exit costs no recomposition of the page.
 */
class RouteReveal {
    var active by mutableStateOf(false)
    var progress by mutableFloatStateOf(1f)

    /** True while a back gesture is pulling the page away; its app bar stays hidden throughout. */
    var gesturing by mutableStateOf(false)

    /**
     * Opacity of the app bar once the page is on top again. A gesture keeps it at 0 and lets it
     * fade in on release, so the bar does not sit at full strength under the finger.
     */
    var barAlpha by mutableFloatStateOf(1f)

    /**
     * True while a page's covered snapshot is being taken with its app bar hidden. The page is
     * covered at that point, so the only effect is on the picture handed to the back gesture.
     */
    var snapshotWithoutBar by mutableStateOf(false)
}

val LocalRouteReveal = staticCompositionLocalOf { RouteReveal() }
