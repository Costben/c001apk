package app.awavenue.c001apk.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.data.CloudHistoryEntry
import app.awavenue.c001apk.data.FavHistoryItem
import app.awavenue.c001apk.data.HistoryType
import app.awavenue.c001apk.data.asEntity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.appDialogButtonColors
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.openCoolapkLink
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.FeedCard
import app.awavenue.c001apk.ui.nav.LocalAppBarBounds
import app.awavenue.c001apk.ui.nav.LocalFeedListState
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.UploadCloud
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Locally kept favourites and browse history. 浏览历史 can also read the account's cloud record;
 * the two lists are separate — the client only reads the cloud one and never uploads the local
 * list, so a device that browsed elsewhere shows what that device recorded.
 */
@Composable
fun HistoryScreen(type: HistoryType) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberPageScrollBehavior("history")
    val backdrop = rememberGlassBackdrop()
    val appBarBounds = LocalAppBarBounds.current
    var cloud by rememberSaveable(type) { mutableStateOf(false) }
    val cloudMode = type == HistoryType.History && cloud
    val listState = rememberPageListState(if (cloudMode) "history-cloud" else "history-local")
    var confirmClear by remember(type) { mutableStateOf(false) }
    val entries = app.history.list(type)

    // Cloud paging. The cursor is the last entry's id, not a page number alone.
    var cloudItems by remember { mutableStateOf<List<CloudHistoryEntry>>(emptyList()) }
    var cloudPage by remember { mutableIntStateOf(1) }
    var cloudLastItem by remember { mutableStateOf<String?>(null) }
    var cloudLoading by remember { mutableStateOf(false) }
    var cloudRefreshing by remember { mutableStateOf(false) }
    var cloudEnd by remember { mutableStateOf(false) }
    var cloudFailure by remember { mutableStateOf<String?>(null) }
    val generation = app.session.generation

    suspend fun loadCloud(reset: Boolean) {
        if (cloudLoading) return
        cloudLoading = true
        cloudFailure = null
        if (reset) {
            cloudPage = 1
            cloudLastItem = null
            cloudEnd = false
        }
        try {
            val rows = app.repository.cloudHistory(cloudPage, null, cloudLastItem)
            cloudItems = if (reset) {
                rows
            } else {
                cloudItems + rows.filterNot { row -> cloudItems.any { it.id == row.id } }
            }
            cloudLastItem = rows.lastOrNull()?.id
            cloudEnd = rows.isEmpty()
            cloudPage += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cloudFailure = ApiFailure.from(e).message
        } finally {
            cloudLoading = false
            cloudRefreshing = false
        }
    }

    // Entering the cloud list, or switching accounts under it, starts from the newest row.
    LaunchedEffect(cloudMode, generation) {
        if (!cloudMode) return@LaunchedEffect
        cloudItems = emptyList()
        loadCloud(reset = true)
    }

    LaunchedEffect(cloudMode, listState) {
        if (!cloudMode) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && last >= layout.totalItemsCount - 3
        }.distinctUntilChanged().filter { it }.collect {
            if (!cloudEnd && !cloudLoading && cloudFailure == null) loadCloud(reset = false)
        }
    }

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = if (cloudMode) "云端浏览历史" else type.label,
                    modifier = Modifier.onGloballyPositioned {
                        appBarBounds.reportPage(
                            Route.History(type),
                            it.positionInRoot().y + it.size.height,
                        )
                    },
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (type == HistoryType.History) {
                            TopBarIconButton(onClick = {
                                cloud = !cloud
                                context.toast(if (cloud) "已切换到云端浏览历史" else "已切换到本地浏览历史")
                            }) {
                                Icon(
                                    COUIIcons.UploadCloud,
                                    contentDescription = if (cloud) "查看本地浏览历史" else "查看云端浏览历史",
                                )
                            }
                        }
                        when {
                            cloudMode -> TopBarIconButton(
                                onClick = {
                                    if (!cloudRefreshing && !cloudLoading) {
                                        cloudRefreshing = true
                                        scope.launch { loadCloud(reset = true) }
                                    }
                                },
                            ) {
                                Icon(COUIIcons.Refresh, contentDescription = "刷新")
                            }

                            entries.isNotEmpty() -> TopBarIconButton(onClick = { confirmClear = true }) {
                                Icon(COUIIcons.Delete, contentDescription = "清除全部")
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalFeedListState provides listState,
        ) {
            if (cloudMode) {
                CloudHistoryList(
                    items = cloudItems,
                    loading = cloudLoading && cloudItems.isEmpty(),
                    refreshing = cloudRefreshing,
                    failure = cloudFailure,
                    loggedIn = app.session.isLogin,
                    listState = listState,
                    scrollBehavior = scrollBehavior,
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        top = innerPadding.calculateTopPadding() + 10.dp,
                        end = 10.dp,
                        bottom = innerPadding.calculateBottomPadding() + 10.dp,
                    ),
                    onRefresh = {
                        cloudRefreshing = true
                        scope.launch { loadCloud(reset = true) }
                    },
                    onRetry = { scope.launch { loadCloud(reset = true) } },
                    onOpen = { entry ->
                        if (!openCoolapkLink(context, navigator, entry.url, entry.title, app.settings.openInBrowser)) {
                            navigator.push(Route.Web(entry.url))
                        }
                    },
                )
            } else if (entries.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
                        .glassSource(backdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无内容", color = COUITheme.colorScheme.onSurfaceVariantSummary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .glassSource(backdrop)
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        top = innerPadding.calculateTopPadding() + 10.dp,
                        end = 10.dp,
                        bottom = innerPadding.calculateBottomPadding() + 10.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries, key = { it.id }) { item ->
                        FeedCard(
                            feed = item.asEntity(),
                            isHistory = true,
                            onDelete = { app.history.remove(type, item.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ClearDialog(
            type = type,
            onConfirm = {
                confirmClear = false
                app.history.clear(type)
            },
            onCancel = { confirmClear = false },
        )
    }
}

@Composable
private fun CloudHistoryList(
    items: List<CloudHistoryEntry>,
    loading: Boolean,
    refreshing: Boolean,
    failure: String?,
    loggedIn: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: io.github.suqi8.coui.kmp.basic.ScrollBehavior?,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (CloudHistoryEntry) -> Unit,
) {
    when {
        !loggedIn -> CloudMessage(contentPadding, "登录后可查看云端浏览历史", "云端记录跟随酷安账号，仅读取，不会上传本地历史")
        loading -> Box(
            Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) { InfiniteProgressIndicator() }

        failure != null && items.isEmpty() -> CloudMessage(contentPadding, "加载失败", failure) {
            Text(
                "重试",
                color = COUITheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onRetry)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
            )
        }

        items.isEmpty() -> CloudMessage(contentPadding, "暂无云端浏览历史", "在任意设备登录同一账号浏览后，这里会同步出现记录")
        else -> AppPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            contentPadding = contentPadding,
            topAppBarScrollBehavior = scrollBehavior,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().overScrollVertical(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { entry ->
                    CloudHistoryRow(entry = entry, onClick = { onOpen(entry) })
                }
                if (failure != null) {
                    item(key = "failure") {
                        Text(
                            failure,
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudMessage(
    contentPadding: PaddingValues,
    title: String,
    subtitle: String?,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = COUITheme.textStyles.title4)
        subtitle?.let {
            Text(
                it,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
            )
        }
        action?.invoke()
    }
}

@Composable
private fun CloudHistoryRow(entry: CloudHistoryEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetworkImage(
                url = entry.logo,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifEmpty { entry.description },
                    style = COUITheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.title.isNotEmpty() && entry.description.isNotEmpty()) {
                    Text(
                        entry.description,
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.typeName.isNotEmpty()) {
                        Text(
                            entry.typeName,
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        fromToday(entry.dateline),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun ClearDialog(type: HistoryType, onConfirm: () -> Unit, onCancel: () -> Unit) {
    OverlayAlertDialog(
        buttonColors = appDialogButtonColors(),
        show = true,
        title = "确定清除全部${if (type == HistoryType.Fav) "收藏" else "浏览历史"}？",
        confirmText = "确定",
        cancelText = "取消",
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}
