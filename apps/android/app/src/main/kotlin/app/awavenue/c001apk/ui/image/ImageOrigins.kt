package app.awavenue.c001apk.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

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
    private val slots = HashMap<String, MutableList<ThumbSlot>>()

    /**
     * 看图页此刻正在显示的那张图，没有打开看图页时为空。
     *
     * 看图页正在展示或拖拽这张图时隐藏底层原图，避免半透明背景透出两张一样的图。
     */
    var viewing by mutableStateOf<String?>(null)

    /** 退场回缩飞行态：接管手势松手后的视觉飞行，原页面立刻出栈让底层立即可滑动与点击。 */
    internal var dismissFlight by mutableStateOf<ImageDismissFlight?>(null)

    /** 宿主提供的协程作用域，确保 ImageView 出栈销毁后飞行动画仍可继续。 */
    internal var flightScope: CoroutineScope? = null

    internal fun finishDismissFlight() {
        val flight = dismissFlight ?: return
        dismissFlight = null
        flight.release()
        viewing = null
    }

    internal fun put(url: String, slot: ThumbSlot) {
        val list = slots.getOrPut(url) { ArrayList(2) }
        if (!list.contains(slot)) list.add(slot)
    }

    internal fun remove(url: String, slot: ThumbSlot) {
        val list = slots[url] ?: return
        list.remove(slot)
        if (list.isEmpty()) slots.remove(url)
    }

    /** Read live, so a closing viewer shrinks into where the thumbnail is now. */
    internal fun boundsOf(url: String, host: LayoutCoordinates, density: Float): ThumbBounds? {
        val list = slots[url] ?: return null
        if (!host.isAttached) return null
        for (i in list.lastIndex downTo 0) {
            val slot = list[i]
            val coordinates = slot.coordinates?.takeIf { it.isAttached } ?: continue
            val rect = host.localBoundingBoxOf(coordinates, clipBounds = false)
            if (rect.width <= 0f || rect.height <= 0f) continue
            // A circle asks for more radius than fits; the shorter side's half is as round as it gets.
            val radius = (slot.cornerRadius.value * density).coerceAtMost(minOf(rect.width, rect.height) / 2f)
            return ThumbBounds(rect, radius)
        }
        return null
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
    return this
        .onPlaced {
            slot.coordinates = it
            origins.put(url, slot)
        }
        // 看图页正在展示或拖拽这张图时隐藏底层原图，避免半透明背景透出两张一样的图。
        .graphicsLayer {
            alpha = if (origins.viewing == url) 0f else 1f
        }
}
