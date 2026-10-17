package app.awavenue.c001apk.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A thumbnail as the viewer sees it: its bounds in the viewer's own coordinates and corner radius in px. */
internal class ThumbBounds(val rect: Rect, val cornerRadius: Float)

internal class ThumbSlot {
    var coordinates: LayoutCoordinates? = null
    var cornerRadius: Dp = 0.dp
}

/**
 * Where the thumbnails on screen sit, by image URL, so the viewer can grow out of the one that was
 * tapped and shrink back into the one it ends on. Keyed by URL rather than by route argument because
 * the page holding the thumbnails is composed anew when the viewer closes over it.
 */
@Stable
class ImageOrigins {
    private val slots = HashMap<String, ThumbSlot>()

    internal fun put(url: String, slot: ThumbSlot) {
        slots[url] = slot
    }

    internal fun remove(url: String, slot: ThumbSlot) {
        if (slots[url] === slot) slots.remove(url)
    }

    /** Read live, so a closing viewer shrinks into where the thumbnail is now. */
    internal fun boundsOf(url: String, host: LayoutCoordinates, density: Float): ThumbBounds? {
        val slot = slots[url] ?: return null
        val coordinates = slot.coordinates?.takeIf { it.isAttached } ?: return null
        if (!host.isAttached) return null
        val rect = host.localBoundingBoxOf(coordinates, clipBounds = false)
        if (rect.width <= 0f || rect.height <= 0f) return null
        // A circle asks for more radius than fits; the shorter side's half is as round as it gets.
        val radius = (slot.cornerRadius.value * density).coerceAtMost(minOf(rect.width, rect.height) / 2f)
        return ThumbBounds(rect, radius)
    }
}

val LocalImageOrigins = staticCompositionLocalOf { ImageOrigins() }

/**
 * Marks this element as the thumbnail of [url]: tapping it opens the viewer, which grows out of it.
 * Apply it after the element's size and before its clip, so the bounds are the element's own.
 */
@Composable
fun Modifier.imageOrigin(url: String, cornerRadius: Dp): Modifier {
    val origins = LocalImageOrigins.current
    val slot = remember(url) { ThumbSlot() }
    SideEffect { slot.cornerRadius = cornerRadius }
    DisposableEffect(origins, url, slot) { onDispose { origins.remove(url, slot) } }
    return onPlaced {
        slot.coordinates = it
        origins.put(url, slot)
    }
}
