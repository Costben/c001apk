package app.awavenue.c001apk.ui.nav

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarState

/**
 * What a screen has to keep to come back looking the way the user left it.
 *
 * `rememberSaveable` files a value under its place in the composition, and the page under a back
 * gesture is composed outside the slot it came from. Its saved values are therefore never found,
 * and the page starts at the top while the pop lands on the scrolled one — a jump the user sees as
 * the background changing under the returning page. The few things that decide what is on screen
 * are kept here, on the entry, so every composition of the page starts from them.
 */
class PageMemory {
    /** The last position of each list on the page, keyed by the list's own identity. */
    val lists = HashMap<Any, Pair<Int, Int>>()

    /** The scroll offset of each plain scrolling column, keyed like [lists]. */
    val scrolls = HashMap<Any, Int>()

    /** The page a pager was showing, keyed like [lists]. */
    val pages = HashMap<Any, Int>()

    /** Choices the page made (a sort order, a filter), keyed by name, so it reopens with them. */
    val selections = HashMap<String, Any>()

    /** Top bar offsets: the collapse limit, how far it is collapsed, and the content offset. */
    val bars = HashMap<Any, List<Float>>()

    /** A bar that collapses with the scroll: how far it is collapsed and how far content went. */
    var barHeightOffset = 0f
    var barContentOffset = 0f
}

val LocalPageMemory = staticCompositionLocalOf<PageMemory?> { null }

/**
 * [rememberLazyListState] whose position lives on the entry instead of in the saved state.
 *
 * [key] has to identify the list across rebuilds — its view model when it has one, or the page
 * itself when the page only shows one list. The position is applied before the first layout, so
 * the first frame is already the one the user left, not a frame at the top followed by a jump.
 */
@Composable
fun rememberPageListState(key: Any = Unit): LazyListState {
    val state = rememberLazyListState()
    val memory = LocalPageMemory.current ?: return state
    val applied = remember { mutableStateOf(false) }
    SideEffect {
        if (applied.value) return@SideEffect
        applied.value = true
        val (index, offset) = memory.lists[key] ?: return@SideEffect
        if (state.firstVisibleItemIndex != index || state.firstVisibleItemScrollOffset != offset) {
            state.requestScrollToItem(index, offset)
        }
    }
    LaunchedEffect(state, memory, key) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { memory.lists[key] = it }
    }
    return state
}

/**
 * [rememberScrollState] whose offset lives on the entry, for a page that scrolls as one column.
 *
 * The offset is applied in the first side effect, before the frame is drawn, so the reveal starts
 * where the page will land instead of at the top.
 */
@Composable
fun rememberPageScrollState(key: Any = Unit): ScrollState {
    val state = rememberScrollState()
    val memory = LocalPageMemory.current ?: return state
    val applied = remember { mutableStateOf(false) }
    SideEffect {
        if (applied.value) return@SideEffect
        applied.value = true
        val saved = memory.scrolls[key] ?: return@SideEffect
        if (state.value != saved) state.dispatchRawDelta((saved - state.value).toFloat())
    }
    LaunchedEffect(state, memory, key) {
        snapshotFlow { state.value }.collect { memory.scrolls[key] = it }
    }
    return state
}

/**
 * [COUIScrollBehavior] whose collapsed bar state lives on the entry.
 *
 * A page with a sliding title row is revealed outside its own composition too, where its bar would
 * otherwise be built expanded under content that is already scrolled: the row fills in during the
 * gesture and slides away the moment the pop lands. [key] identifies the page, like [lists].
 */
@Composable
fun rememberPageScrollBehavior(key: Any = Unit): ScrollBehavior {
    val memory = LocalPageMemory.current
    val saved = memory?.bars?.get(key)
    val state = rememberSaveable(saver = TopAppBarState.Saver) {
        TopAppBarState(
            initialHeightOffsetLimit = saved?.get(0) ?: -Float.MAX_VALUE,
            initialHeightOffset = saved?.get(1) ?: 0f,
            initialContentOffset = saved?.get(2) ?: 0f,
        )
    }
    if (memory != null) {
        LaunchedEffect(state, memory, key) {
            snapshotFlow {
                listOf(state.heightOffsetLimit, state.heightOffset, state.contentOffset)
            }.collect { memory.bars[key] = it }
        }
    }
    return COUIScrollBehavior(state = state)
}

/**
 * The page a pager opens on in this composition: the one the user last chose on the entry, or
 * [fallback] the first time.
 */
@Composable
fun pageMemoryPage(key: Any = Unit, fallback: Int): Int =
    LocalPageMemory.current?.pages?.get(key) ?: fallback

/** Keeps [page] as the one the user chose, so a rebuild opens on it again. */
@Composable
fun rememberPageMemoryRecorder(key: Any = Unit, page: Int) {
    val memory = LocalPageMemory.current ?: return
    SideEffect { memory.pages[key] = page }
}
