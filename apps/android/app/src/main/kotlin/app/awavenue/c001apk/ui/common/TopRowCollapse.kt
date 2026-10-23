package app.awavenue.c001apk.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarState

/**
 * Collapses the top row while scrolling up, but brings it back as soon as the content moves down,
 * even in the middle of a list. [COUIScrollBehavior] only expands once the content is back at the
 * top; here the row takes a downward drag before the list does.
 */
@Composable
internal fun rememberTopRowScrollBehavior(
    state: TopAppBarState,
    scrolledPx: MutableFloatState,
): ScrollBehavior {
    val settle = COUIScrollBehavior(state = state)
    return remember(state, settle, scrolledPx) {
        object : ScrollBehavior {
            override val state = state
            override val isPinned = false
            override val snapAnimationSpec = settle.snapAnimationSpec
            override val flingAnimationSpec = settle.flingAnimationSpec
            override val nestedScrollConnection = object : NestedScrollConnection {
                // The row takes the drag before the list in both directions: its height feeds the
                // Scaffold padding, so the content already follows the finger. Growing it after the
                // list scrolled moved the content twice, and the growth reported upward made the
                // overscroll pull the page back every few frames.
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y == 0f) return Offset.Zero
                    val previous = state.heightOffset
                    state.heightOffset += available.y
                    return Offset(0f, state.heightOffset - previous)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    state.contentOffset += consumed.y
                    scrolledPx.floatValue =
                        (scrolledPx.floatValue - consumed.y).coerceAtLeast(0f)
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (available.y > 0f) state.contentOffset = 0f
                    return settle.nestedScrollConnection.onPostFling(consumed, available)
                }
            }
        }
    }
}
