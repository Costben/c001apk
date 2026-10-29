package app.awavenue.c001apk.ui.follow

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.LocalFollowPager
import app.awavenue.c001apk.data.UserFeedFetcher
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.network.ApiFailureKind
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.EntityItem
import app.awavenue.c001apk.ui.feed.FeedActions
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Pages over the merged timelines of the local list. A refresh starts a new merge from the users
 * listed at that moment; a load that yields nothing because a read failed surfaces as an error, so
 * the footer offers a retry instead of claiming the list has ended.
 */
private class LocalFollowLoader(private val uids: () -> List<String>, private val fetch: UserFeedFetcher) : PageLoader {
    var warning by mutableStateOf<String?>(null)
        private set
    private var pager: LocalFollowPager? = null

    override suspend fun load(page: Int, firstItem: String?, lastItem: String?): List<Entity> {
        val current = pager?.takeIf { page > 1 } ?: LocalFollowPager(uids(), fetch).also { pager = it }
        val items = current.next()
        warning = current.warning
        current.warning?.let { if (items.isEmpty()) throw ApiFailure(ApiFailureKind.Transport, it) }
        return items
    }
}

/** Holds the loader beside the list's ViewModel, so both are cleared together. */
private class LocalFollowLoaderHolder(val loader: LocalFollowLoader) : ViewModel()

@Composable
fun LocalFollowFeed(returnTop: ReturnTopController, scrollBehavior: ScrollBehavior, contentPadding: PaddingValues) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val label = localFollowLabel()
    val users = app.localFollow.users

    val holder = viewModel(key = "local-follow-loader") {
        LocalFollowLoaderHolder(
            LocalFollowLoader(
                uids = { app.localFollow.users.map { it.uid }.filterNot(app.blacklist::isUserBlocked) },
                fetch = { uid, page, first, last -> app.repository.userFeed(uid, page, first, last, includeTop = false) },
            ),
        )
    }
    val viewModel = viewModel(key = "local-follow") { FeedListViewModel(holder.loader, app::contentGate) }
    val listState = rememberLazyListState()

    // The list is read from the device, so editing it or blocking someone re-reads from the top.
    LaunchedEffect(viewModel) {
        snapshotFlow { app.localFollow.users.map { it.uid } to app.blacklist.userBlacklist }
            .distinctUntilChanged()
            .drop(1)
            .collect { viewModel.refresh() }
    }
    LaunchedEffect(viewModel.refreshError) {
        viewModel.refreshError?.let {
            context.toast(it)
            viewModel.refreshError = null
        }
    }
    LaunchedEffect(listState, viewModel) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && last >= layout.totalItemsCount - 4
        }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
    }
    LaunchedEffect(listState, returnTop) {
        returnTop.events.collect {
            coroutineScope {
                launch {
                    animate(scrollBehavior.state.heightOffset, 0f) { value, _ -> scrollBehavior.state.heightOffset = value }
                }
                listState.animateScrollToItem(0)
            }
        }
    }

    AppPullToRefresh(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = viewModel::refresh,
        contentPadding = contentPadding,
        topAppBarScrollBehavior = scrollBehavior,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(contentType = "header") {
                Card(Modifier.padding(horizontal = 12.dp).padding(top = 6.dp)) {
                    BasicComponent(
                        title = "${label}动态",
                        summary = "读取本机名单中的用户动态，不会同步账号关注",
                        endActions = { TextButton(text = "管理", onClick = { navigator.push(Route.LocalFollowList) }) },
                    )
                    val warning = holder.loader.warning
                    if (warning != null && viewModel.status == ListStatus.Content) {
                        BasicComponent(
                            title = warning,
                            endActions = { TextButton(text = "重试", onClick = viewModel::retryFooter) },
                        )
                    }
                }
            }
            when {
                users.isEmpty() -> item(contentType = "empty") {
                    Text(
                        if (app.session.isLogin) {
                            "还没有悄悄关注的用户\n在设置中开启后，长按用户主页的关注按钮即可添加"
                        } else {
                            "还没有本地关注的用户\n在用户主页点击“本地关注”，即可在这里查看动态"
                        },
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                else -> when (val status = viewModel.status) {
                    ListStatus.Loading -> item(contentType = "loading") { Notice { InfiniteProgressIndicator() } }
                    ListStatus.Empty -> item(contentType = "empty") {
                        Notice { EmptyState(title = "暂无内容", actionText = "刷新", onActionClick = viewModel::refresh) }
                    }
                    is ListStatus.Error -> item(contentType = "error") {
                        Notice {
                            EmptyState(
                                title = "加载失败",
                                subtitle = status.message,
                                actionText = "重试",
                                onActionClick = viewModel::refresh,
                            )
                        }
                    }
                    ListStatus.Content -> {
                        items(
                            viewModel.items.filterNot(FeedActions::isRemoved),
                            key = { it.id },
                            contentType = { it.entityType },
                        ) { item -> EntityItem(item) }
                        item(contentType = "footer") { Footer(viewModel.footer, viewModel::retryFooter, viewModel::continueFiltered) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Footer(status: FooterStatus, onRetry: () -> Unit, onContinue: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (status) {
            FooterStatus.Idle, FooterStatus.Loading -> InfiniteProgressIndicator()
            FooterStatus.End -> Text(
                "没有更多了",
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            is FooterStatus.Filtered -> TextButton(
                text = "已过滤 ${status.count} 条，点击继续加载",
                onClick = onContinue,
            )
            is FooterStatus.Error -> TextButton(text = "${status.message}，点击重试", onClick = onRetry)
        }
    }
}
