package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Keeps a list header under the app bar once its item has scrolled above it.
 *
 * [LazyListScope.stickyHeader] pins to the top of the list, which is the top of the screen: the
 * header then sits behind the app bar and its status-bar band and shows through the glass as a
 * blurred ghost. The list item scrolls away normally instead, and this copy takes its place at the
 * bar's bottom edge, so the header never enters the bar.
 *
 * [headerIndex] is the index the header item has in the list. An item's offset is measured from the
 * bar's bottom edge, so the header has reached the bar when that offset is zero.
 */
@Composable
fun PinnedHeader(
    listState: LazyListState,
    headerIndex: Int,
    topInset: Dp,
    content: @Composable () -> Unit,
) {
    val pinned by remember(listState, headerIndex) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                ?: return@derivedStateOf false
            first.index > headerIndex || (first.index == headerIndex && first.offset <= 0)
        }
    }
    if (!pinned) return
    Box(Modifier.fillMaxWidth().padding(top = topInset)) { content() }
}
