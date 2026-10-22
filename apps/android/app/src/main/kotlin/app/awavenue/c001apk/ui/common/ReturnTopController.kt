package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Lets a tapped tab ask the list on screen to jump back to its top. Mirrors the Flutter client's
 * `ReturnTopController`.
 */
@Stable
class ReturnTopController {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: Flow<Unit> get() = requests

    fun requestReturn() {
        requests.tryEmit(Unit)
    }
}

/**
 * Makes a bar tappable so a tap anywhere on it carries the list back to its top, the way tapping
 * the status bar does on ColorOS.
 *
 * The tap stays silent: the bar is a sheet of material, not a button, so it gets no ripple or
 * highlight — only the list moving is the feedback. [LazyListState.animateScrollToItem] runs the
 * whole distance as one scroll animation, which also drags the app bar's scroll-driven material
 * back to rest through the nested scroll connection the list already has.
 *
 * [enabled] is normally the list's own [LazyListState.canScrollBackward], so a bar over a list that
 * is already at the top is not a tap target at all.
 */
@Composable
fun Modifier.returnTopOnClick(
    listState: LazyListState,
    enabled: Boolean = listState.canScrollBackward,
): Modifier {
    val scope = rememberCoroutineScope()
    return clickable(
        interactionSource = null,
        indication = null,
        enabled = enabled,
        onClickLabel = "回到顶部",
    ) {
        scope.launch { listState.animateScrollToItem(0) }
    }
}
