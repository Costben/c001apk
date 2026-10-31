package app.awavenue.c001apk.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How far the route being shown is into its transition, for pages that draw their own entrance and
 * exit instead of being moved by the host. [progress] runs from 0 (not yet entered, or fully left) to
 * 1 (settled); both reads are meant for draw and layer blocks, so a running transition never
 * recomposes the page.
 *
 * [preview] is true when the page was composed only to be revealed under a back gesture: it is a
 * preview of the page the pop will land on, not the page the user is on, so it must not take focus
 * or start anything the user has not asked for. It is constant for the lifetime of a composition,
 * which lets a page pick a different initial state than the real page would.
 */
@Stable
class RouteTransition(val progress: () -> Float, val exiting: () -> Boolean, val preview: Boolean = false)

val LocalRouteTransition = staticCompositionLocalOf<RouteTransition?> { null }
