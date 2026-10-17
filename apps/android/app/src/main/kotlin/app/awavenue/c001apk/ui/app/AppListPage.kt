package app.awavenue.c001apk.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.platform.InstalledApp
import app.awavenue.c001apk.platform.installedApps
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.common.toImageBitmap
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Update
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private class AppRow(val app: InstalledApp, val icon: ImageBitmap?)

@Composable
fun AppListPage(returnTop: ReturnTopController?, contentPadding: PaddingValues) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var rows by remember { mutableStateOf<List<AppRow>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var attempts by remember { mutableStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }

    LaunchedEffect(attempts) {
        // The app list is a slow start, and the Flutter client keeps it off the first frame too.
        if (attempts == 0) delay(500)
        refreshing = rows != null
        val result = withContext(Dispatchers.IO) {
            runCatching { installedApps(context) }
        }
        result
            .onSuccess { installed ->
                rows = withContext(Dispatchers.Default) {
                    installed.map { AppRow(it, it.icon?.toImageBitmap()) }
                }
                failure = null
            }
            .onFailure { failure = it.message ?: "加载失败，请重试" }
        refreshing = false
    }

    if (returnTop != null) {
        LaunchedEffect(listState, returnTop) {
            returnTop.events.collect {
                listState.animateScrollToItem(0)
                attempts++
            }
        }
    }

    LaunchedEffect(listState) {
        var previous = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { current ->
                val movedDown = current.first > previous.first ||
                    (current.first == previous.first && current.second > previous.second)
                if (movedDown != !fabVisible) fabVisible = !movedDown
                previous = current
            }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            failure != null -> CenterBox(contentPadding) {
                EmptyState(
                    title = "加载失败",
                    subtitle = failure,
                    actionText = "重试",
                    onActionClick = { attempts++ },
                )
            }
            rows == null -> CenterBox(contentPadding) { InfiniteProgressIndicator() }
            rows.isNullOrEmpty() -> CenterBox(contentPadding) {
                EmptyState(title = "暂无内容", actionText = "刷新", onActionClick = { attempts++ })
            }
            else -> PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = { attempts++ },
                pullToRefreshState = rememberPullToRefreshState(),
                contentPadding = contentPadding,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().overScrollVertical(),
                    contentPadding = contentPadding,
                ) {
                    items(rows.orEmpty(), key = { it.app.packageName }) { row ->
                        AppRowItem(row) { navigator.push(Route.Apk(row.app.packageName)) }
                    }
                }
            }
        }

        if (app.settings.checkUpdate) {
            AnimatedVisibility(
                visible = fabVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            ) {
                FloatingActionButton(onClick = { navigator.push(Route.AppUpdate) }) {
                    Icon(COUIIcons.Update, contentDescription = "更新")
                }
            }
        }
    }
}

@Composable
private fun AppRowItem(row: AppRow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = row.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(row.app.appName, style = COUITheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${row.app.packageName}\n${row.app.versionName}(${row.app.versionCode})",
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CenterBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { content() }
}
