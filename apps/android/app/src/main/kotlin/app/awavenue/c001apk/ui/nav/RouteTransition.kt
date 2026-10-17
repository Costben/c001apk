package app.awavenue.c001apk.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How far the route being shown is into its transition, for pages that draw their own entrance and
 * exit instead of being moved by the host. [progress] runs from 0 (not yet entered, or fully left) to
 * 1 (settled); both reads are meant for draw and layer blocks, so a running transition never
 * recomposes the page.
 */
@Stable
class RouteTransition(val progress: () -> Float, val exiting: () -> Boolean)

val LocalRouteTransition = staticCompositionLocalOf<RouteTransition?> { null }
