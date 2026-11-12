package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Long-press anywhere on this node, including over children that consume their own down (tab
 * items, COUI chips), which a parent combinedClickable never sees; the Initial pass does. Nothing
 * is consumed until the press actually turns into a long press, which keeps taps and drags intact;
 * after it, the rest of the gesture is swallowed so the child does not also click on release.
 */
fun Modifier.longPressAnywhere(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val touchSlop = viewConfiguration.touchSlop
        var canceled = false
        val releasedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                if (!change.pressed || change.isConsumed ||
                    (change.position - down.position).getDistance() > touchSlop
                ) {
                    canceled = true
                    return@withTimeoutOrNull
                }
            }
        }
        if (releasedEarly == null && !canceled) {
            onLongPress()
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                pressed = event.changes.any { it.pressed }
            }
        }
    }
}
