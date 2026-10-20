package app.awavenue.c001apk.ui.app

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.platform.InstalledApp
import app.awavenue.c001apk.platform.installedApps
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.toImageBitmap
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class AppRow(val app: InstalledApp, val icon: ImageBitmap?)

/** Applies the bottom bar's filter, keeping the loader's newest-first order when nothing is set. */
private fun List<AppRow>.matching(query: String, sort: AppSort, ascending: Boolean): List<AppRow> {
    val filtered = if (query.isEmpty()) {
        this
    } else {
        filter {
            it.app.appName.contains(query, ignoreCase = true) ||
                it.app.packageName.contains(query, ignoreCase = true)
        }
    }
    val ordered = when (sort) {
        AppSort.Name -> filtered.sortedBy { it.app.appName.lowercase() }
        AppSort.Recent -> filtered.sortedBy { it.app.lastUpdateTime }
        AppSort.Size -> filtered.sortedBy { it.app.apkSize }
    }
    return if (ascending) ordered else ordered.asReversed()
}

/** The card's surface for one row of the single long list card. */
private fun appCardShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(17.dp)
    index == 0 -> RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp)
    index == count - 1 -> RoundedCornerShape(bottomStart = 17.dp, bottomEnd = 17.dp)
    else -> RectangleShape
}

@Composable
fun AppListPage(
    returnTop: ReturnTopController?,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior? = null,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val filter = LocalAppFilter.current
    val listState = rememberLazyListState()

    var rows by remember { mutableStateOf<List<AppRow>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var attempts by remember { mutableStateOf(0) }

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
                coroutineScope {
                    // Bring a collapsed app bar back along with the list.
                    launch {
                        animate(scrollBehavior?.state?.heightOffset ?: 0f, 0f) { value, _ ->
                            scrollBehavior?.state?.heightOffset = value
                        }
                    }
                    listState.animateScrollToItem(0)
                }
                attempts++
            }
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
            else -> {
                val apps = rows.orEmpty()
                val query = filter.query.trim()
                val matched = remember(apps, query, filter.sort, filter.ascending) {
                    apps.matching(query, filter.sort, filter.ascending)
                }
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = { attempts++ },
                    pullToRefreshState = rememberPullToRefreshState(),
                    contentPadding = contentPadding,
                    topAppBarScrollBehavior = scrollBehavior,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .then(
                                if (scrollBehavior != null) {
                                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                                } else {
                                    Modifier
                                },
                            ),
                        contentPadding = contentPadding,
                    ) {
                        item(key = "app-list-title") {
                            SmallTitle(
                                if (query.isEmpty()) {
                                    "已安装应用 · ${matched.size}"
                                } else {
                                    "筛选结果 · ${matched.size}"
                                },
                            )
                        }
                        if (matched.isEmpty()) {
                            item(key = "app-list-empty") {
                                EmptyState(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                    title = "没有匹配的应用",
                                    subtitle = "换个关键词，或者清空筛选",
                                    actionText = "清空筛选",
                                    onActionClick = { filter.query = "" },
                                )
                            }
                        } else {
                            itemsIndexed(matched, key = { _, row -> row.app.packageName }) { index, row ->
                                AppRowItem(row, appCardShape(index, matched.size)) {
                                    navigator.push(Route.Apk(row.app.packageName))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRowItem(row: AppRow, shape: Shape, onClick: () -> Unit) {
    val icon = row.icon
    BasicComponent(
        // The rows stack without gaps and only the outer edges are rounded, so the whole list
        // reads as one long card while each row still composes and recycles on its own.
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(shape)
            .background(COUITheme.colorScheme.surfaceContainer),
        title = row.app.appName,
        summary = row.app.packageName,
        startAction = if (icon != null) {
            {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
        } else {
            null
        },
        endActions = {
            Text(
                row.app.versionName,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                COUIIcons.ChevronForward,
                contentDescription = null,
                tint = COUITheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(start = 4.dp),
            )
        },
        onClick = rememberHapticClick(onClick = onClick),
    )
}

@Composable
private fun CenterBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { content() }
}
