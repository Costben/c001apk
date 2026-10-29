package app.awavenue.c001apk.ui.feed

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalFeedListState
import app.awavenue.c001apk.ui.nav.rememberPageListState
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun FeedList(
    viewModel: FeedListViewModel,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    returnTop: ReturnTopController? = null,
    itemPadding: Dp = ItemPadding,
    onSortSelected: ((Entity) -> Unit)? = null,
    itemFilter: ((Entity) -> Boolean)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val openLink = rememberLinkOpener()
    val listState = rememberPageListState(viewModel)
    // Hidden cards stay in the fetched page, so a switch flipped in 设置 takes effect on the list
    // already on screen and needs no refresh to come back. The content gate is rebuilt when the
    // rules change for the same reason: a new rule drops the rows it matches without a reload.
    val gate = remember(app.blacklist.userBlacklist, app.blacklist.topicBlacklist, app.contentFilter.preferences) {
        app.contentGate()
    }
    val shownItems = remember(viewModel.items, itemFilter, gate) {
        viewModel.items
            .filterNot { FeedActions.isHidden(it) }
            .filter { gate.isVisible(it) }
            .filter { itemFilter?.invoke(it) ?: true }
    }

    val onBlock: (String, String?) -> Unit = { uid, _ ->
        app.blacklist.blockUser(uid)
        viewModel.removeUserFeeds(uid)
    }
    val onLike: (String, Boolean) -> Unit = { id, liked ->
        scope.launch {
            try {
                val result = app.repository.like(id, liked, isFeed = false)
                if (result.message.isNotEmpty()) {
                    context.toast(result.message)
                } else {
                    viewModel.applyLike(id, liked, result.count)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toast(ApiFailure.from(e).message)
            }
        }
    }
    val onDelete: (String, String?) -> Unit = { id, fid ->
        scope.launch {
            try {
                val result = app.repository.deleteReply(id)
                if (result.message.isNotEmpty()) {
                    context.toast(result.message)
                } else {
                    viewModel.removeReply(id, fid)
                    if (result.count.isNotEmpty()) context.toast(result.count)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toast(ApiFailure.from(e).message)
            }
        }
    }

    // Only the list on screen takes part, so the bar always talks to the page the user is looking at.
    if (returnTop != null) {
        LaunchedEffect(listState, returnTop) {
            returnTop.events.collect {
                coroutineScope {
                    // Bring a collapsed app bar back along with the list.
                    launch {
                        animate(scrollBehavior.state.heightOffset, 0f) { value, _ ->
                            scrollBehavior.state.heightOffset = value
                        }
                    }
                    listState.animateScrollToItem(0)
                }
            }
        }
    }

    LaunchedEffect(viewModel.refreshError) {
        viewModel.refreshError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
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

    AnimatedContent(
        targetState = viewModel.status,
        modifier = Modifier.fillMaxSize(),
        // A changed error message is still the same screen, so it must not re-run the whole fade.
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "feedListStatus",
    ) { status ->
        when (status) {
            ListStatus.Loading -> CenterBox(contentPadding) { InfiniteProgressIndicator() }
            ListStatus.Empty -> CenterBox(contentPadding) {
                val filtered = viewModel.filteredCount
                if (filtered > 0) {
                    EmptyState(
                        title = "内容已被过滤",
                        subtitle = "已过滤 $filtered 条，可调整规则后刷新",
                        actionText = "刷新",
                        onActionClick = viewModel::refresh,
                    )
                } else {
                    EmptyState(title = "暂无内容", actionText = "刷新", onActionClick = viewModel::refresh)
                }
            }
            is ListStatus.Error -> CenterBox(contentPadding) {
                EmptyState(title = "加载失败", subtitle = status.message, actionText = "重试", onActionClick = viewModel::refresh)
            }
            ListStatus.Content -> AppPullToRefresh(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = viewModel::refresh,
                contentPadding = contentPadding,
                topAppBarScrollBehavior = scrollBehavior,
            ) {
                CompositionLocalProvider(LocalFeedListState provides listState) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        viewModel.sortCard?.let { card ->
                            item(contentType = "sortCard") {
                                SortSelectCard(
                                    card,
                                    Modifier.padding(horizontal = itemPadding),
                                    viewModel.selectedSort,
                                ) { option ->
                                    if (onSortSelected != null) {
                                        onSortSelected(option)
                                    } else {
                                        openLink(option.url, "")
                                    }
                                }
                            }
                        }
                        if (header != null) {
                            item(key = "catalog-header", contentType = "catalog-header") { header() }
                        }
                        itemsIndexed(
                            shownItems,
                            contentType = { _, item -> item.entityTemplate.ifEmpty { item.entityType } },
                        ) { _, item ->
                            EntityItem(
                                item,
                                onBlock = onBlock,
                                onDelete = onDelete,
                                onLike = onLike,
                                horizontalPadding = itemPadding,
                            )
                        }
                        item(contentType = "footer") {
                            Footer(
                                status = viewModel.footer,
                                onRetry = viewModel::retryFooter,
                                onContinue = viewModel::continueFiltered,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Footer(status: FooterStatus, onRetry: () -> Unit, onContinue: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        // The retry button is the tallest state; reserving its height keeps the footer, and thus the
        // list end, from moving when the state changes.
        AnimatedContent(
            targetState = status,
            modifier = Modifier.heightIn(min = 44.dp),
            // Idle and Loading both show the spinner, so they must share a key to keep it spinning
            // when a page starts loading.
            contentKey = {
                when (it) {
                    FooterStatus.Idle, FooterStatus.Loading -> "loading"
                    FooterStatus.End -> "end"
                    is FooterStatus.Filtered -> "filtered"
                    is FooterStatus.Error -> "error"
                }
            },
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(175)) },
            contentAlignment = Alignment.Center,
            label = "feedFooter",
        ) { current ->
            when (current) {
                FooterStatus.Idle, FooterStatus.Loading -> InfiniteProgressIndicator()
                FooterStatus.End -> Text(
                    "没有更多了",
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
                is FooterStatus.Filtered -> TextButton(
                    text = "已过滤 ${current.count} 条，点击继续加载",
                    onClick = onContinue,
                )
                is FooterStatus.Error -> TextButton(text = "${current.message}，点击重试", onClick = onRetry)
            }
        }
    }
}
