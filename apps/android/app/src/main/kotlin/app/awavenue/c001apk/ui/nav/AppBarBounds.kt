package app.awavenue.c001apk.ui.nav

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.positionInRoot

/** A row that must clear its page's bar once the page that owns it is composed again. */
class ReturnLift(val rowKey: Any, val barBottom: () -> Float) {
    internal val at: Long = android.os.SystemClock.uptimeMillis()
}

/** A return left unclaimed this long no longer belongs to the page being composed. */
private const val ReturnLiftTtlMillis = 1500L

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

    /** The non-tab page whose bar has been measured, and that bar's bottom edge. */
    private var pageRoute: Route? = null
    private var pageBottom: Float = 0f

    /**
     * Set when a row is popped back into a page that lost its composition while covered. That
     * page rebuilds its rows afterwards, so the row cannot be moved from the host — the rebuilt
     * card picks this up and scrolls its own list.
     */
    var pendingReturn: ReturnLift? = null
        private set

    fun requestReturn(rowKey: Any, barBottom: () -> Float) {
        pendingReturn = ReturnLift(rowKey, barBottom)
    }

    fun clearReturn(rowKey: Any) {
        if (pendingReturn?.rowKey == rowKey) pendingReturn = null
    }

    /** The request for [rowKey], unless it is stale. */
    fun pendingReturnFor(rowKey: Any): ReturnLift? {
        val pending = pendingReturn ?: return null
        if (pending.rowKey != rowKey) return null
        if (android.os.SystemClock.uptimeMillis() - pending.at > ReturnLiftTtlMillis) return null
        return pending
    }

    /** Scrolls [state] just enough to put [origin]'s row below the requested bar line. */
    suspend fun alignReturnedRow(rowKey: Any, origin: ExpandOrigin, state: LazyListState) {
        val pending = pendingReturnFor(rowKey) ?: return
        if (pending.rowKey != rowKey) return
        repeat(8) {
            withFrameNanos { }
            val target = pending.barBottom()
            if (target <= 0f) return
            val coordinates = origin.coordinates?.takeIf { it.isAttached }
            if (coordinates == null) {
                return@repeat
            }
            val delta = target - coordinates.positionInRoot().y
            if (delta <= 0.5f) return
            state.scrollBy(-delta)
            withFrameNanos { }
        }
    }

    /** Called by the tab at [slot] whenever its bar is measured. Zero means "nothing reported". */
    fun report(slot: Int, bottom: Float) {
        if (slot in heights.indices) heights[slot] = bottom
    }

    /** Called by a page with its own bar, e.g. the history list. */
    fun reportPage(route: Route, bottom: Float) {
        pageRoute = route
        pageBottom = bottom
    }

    val bottom: Float
        get() = heights.getOrElse(currentTab) { 0f }

    /** The bar's bottom edge for [route], in the route host's coordinates; zero when unknown. */
    fun bottomFor(route: Route): Float =
        if (route == Route.Main) bottom else if (route == pageRoute) pageBottom else 0f
}

val LocalAppBarBounds = staticCompositionLocalOf { AppBarBounds() }

/** The list that hosts the card currently being opened, so a return can scroll it into view. */
val LocalFeedListState = staticCompositionLocalOf<androidx.compose.foundation.lazy.LazyListState?> { null }
