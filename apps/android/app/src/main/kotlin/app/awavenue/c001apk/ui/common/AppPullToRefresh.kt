package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefreshTexts
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState

/**
 * The Chinese hint strings of [FullPagePullToRefresh]; coui ships English defaults.
 *
 * The four strings map to the four visual states: following the finger, past the threshold,
 * refreshing, and the success hold.
 */
private val AppRefreshTexts = FullPagePullToRefreshTexts(
    pulling = "下拉刷新",
    thresholdReached = "松开刷新",
    refreshing = "正在刷新…",
    complete = "刷新成功",
)

/**
 * The app's pull-to-refresh container: COUI's full-page variant, where the page itself follows the
 * finger and uncovers a full-screen refresh layer instead of growing a ball above the list.
 *
 * [contentPadding]'s top is the line the hint reveals from, so the scrollable content has to start
 * at the same top padding — every caller already passes its `innerPadding` to both.
 *
 * The gesture is only one way in: [FullPagePullToRefresh] also plays the same animation when
 * [isRefreshing] goes true without a pull, so a refresh started from a button or a timer still
 * slides the page down.
 */
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    topAppBarScrollBehavior: ScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    FullPagePullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = rememberFullPagePullToRefreshState(),
        contentPadding = contentPadding,
        topAppBarScrollBehavior = topAppBarScrollBehavior,
        refreshTexts = AppRefreshTexts,
        content = content,
    )
}
