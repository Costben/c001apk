package app.awavenue.c001apk.ui.topic

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppFloatingActionButton
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.shareText
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.EntityItem
import app.awavenue.c001apk.ui.feed.FeedActions
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Navigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private enum class TopicSortType(val label: String) {
    Default("默认"),
    Dateline("最新发布"),
    Hot("热门"),
}

@Composable
fun TopicScreen(tag: String) = TopicPage(tag = tag, id = null)

@Composable
fun ProductScreen(id: String) = TopicPage(tag = null, id = id)

@Composable
private fun TopicPage(tag: String?, id: String?) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember(tag, id) { mutableStateOf<Entity?>(null) }
    var failure by remember(tag, id) { mutableStateOf<String?>(null) }
    var attempts by remember(tag, id) { mutableStateOf(0) }

    val decodedTag = tag?.let { runCatching { Uri.decode(it) }.getOrDefault(it) }

    LaunchedEffect(decodedTag, id, attempts) {
        failure = null
        try {
            detail = if (!decodedTag.isNullOrEmpty()) {
                app.repository.topicDetail(decodedTag)
            } else {
                app.repository.productDetail(id.orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = ApiFailure.from(e).message
        }
    }

    val topic = detail
    if (topic == null) {
        Scaffold(topBar = { TopAppBar(title = "", navigationIcon = { BackIcon(navigator) }) }) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                if (failure == null) {
                    InfiniteProgressIndicator()
                } else {
                    EmptyState(
                        title = "加载失败",
                        subtitle = failure,
                        actionText = "重试",
                        onActionClick = { attempts++ },
                    )
                }
            }
        }
        return
    }

    val title = topic.title
    val entityType = topic.entityType
    val isTopic = entityType == "topic"
    val tabList = topic.list("tabList")
    val selected = tabList.indexOfFirst { it.string("page_name") == topic.string("selectedTab") }
    val scrollBehavior = COUIScrollBehavior()
    val backdrop = rememberGlassBackdrop()
    val pagerState = rememberPagerState(initialPage = selected.coerceAtLeast(0)) { tabList.size }

    var isFollow by remember(topic) { mutableStateOf(topic.obj("userAction")?.string("follow") == "1") }
    var isBlocked by remember(topic) { mutableStateOf(app.blacklist.topicBlacklist.any { title.contains(it) }) }
    var sortType by remember { mutableStateOf(TopicSortType.Default) }
    var menu by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showCompose by remember { mutableStateOf(false) }

    val sortable = entityType == "product" && tabList.getOrNull(pagerState.currentPage)?.title == "讨论"
    val shareUrl = Protocol.shareLink(if (isTopic) "t" else "product", if (isTopic) title else topic.string("id"))

    fun onFollowClick() {
        if (!app.session.isLogin) return
        val followed = isFollow
        scope.launch {
            try {
                postFollow(app.repository, context, isTopic, title, topic.string("id"), followed)
                isFollow = !followed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toast(ApiFailure.from(e).message)
            }
        }
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackIcon(navigator) },
                    actions = {
                        if (!isBlocked) {
                            IconButton(onClick = {
                                navigator.push(
                                    Route.Search(
                                        title = title,
                                        pageType = if (isTopic) "tag" else "product_phone",
                                        pageParam = if (isTopic) title else topic.string("id"),
                                    ),
                                )
                            }) {
                                Icon(COUIIcons.Search, contentDescription = "搜索")
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(COUIIcons.More, contentDescription = "菜单")
                            }
                            OverlayListPopup(
                                show = menu,
                                alignment = PopupPositionProvider.Align.BottomEnd,
                                onDismissRequest = { menu = false },
                            ) {
                                ListPopupColumn {
                                    val entries = buildList {
                                        add("复制" to { context.copyText(shareUrl) })
                                        add("分享" to { context.shareText(shareUrl) })
                                        if (sortable) add("排序" to { showSort = true })
                                        add((if (isFollow) "取消关注" else "关注") to { onFollowClick() })
                                        add(
                                            (if (isBlocked) "取消屏蔽" else "屏蔽") to {
                                                if (isBlocked) {
                                                    app.blacklist.unblockTopic(title)
                                                } else {
                                                    app.blacklist.blockTopic(title)
                                                }
                                                isBlocked = !isBlocked
                                            },
                                        )
                                    }
                                    entries.forEachIndexed { index, entry ->
                                        DropdownImpl(
                                            text = entry.first,
                                            optionSize = entries.size,
                                            isSelected = false,
                                            index = index,
                                            onSelectedIndexChange = {
                                                menu = false
                                                entry.second()
                                            },
                                        )
                                    }
                                }
                            }
                            OverlayListPopup(
                                show = showSort,
                                alignment = PopupPositionProvider.Align.BottomEnd,
                                onDismissRequest = { showSort = false },
                            ) {
                                ListPopupColumn {
                                    TopicSortType.entries.forEachIndexed { index, item ->
                                        DropdownImpl(
                                            text = item.label,
                                            optionSize = TopicSortType.entries.size,
                                            isSelected = sortType == item,
                                            index = index,
                                            onSelectedIndexChange = {
                                                showSort = false
                                                sortType = TopicSortType.entries[it]
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    bottomContent = {
                        if (!isBlocked && tabList.size > 1) {
                            TabRowWithContour(
                                tabs = tabList.map { it.title },
                                selectedTabIndex = pagerState.currentPage,
                                onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            AppFloatingActionButton(
                visible = app.session.isLogin && !isBlocked,
                backdrop = backdrop,
                onClick = { showCompose = true },
                icon = COUIIcons.Add,
                contentDescription = "发布动态",
            )
        },
    ) { innerPadding ->
        if (isBlocked) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("已屏蔽 $title")
            }
            return@Scaffold
        }
        if (tabList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(title = "暂无内容")
            }
            return@Scaffold
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            beyondViewportPageCount = 1,
        ) { page ->
            val tab = tabList[page]
            val discuss = entityType == "product" && tab.title == "讨论"
            val url = if (discuss) discussUrl(topic.string("id"), sortType) else tab.url
            val listTitle = if (discuss) discussTitle(sortType) else tab.title
            val viewModel = viewModel(key = "topic-$url-$listTitle") {
                FeedListViewModel(
                    PageLoader { p, first, last -> app.repository.dataList(url, listTitle, p, first, last) },
                )
            }
            val padding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 12.dp,
            )
            if (page == 0) {
                TopicFeedList(viewModel = viewModel, scrollBehavior = scrollBehavior, contentPadding = padding) {
                    TopicHeader(
                        topic = topic,
                        isFollow = isFollow,
                        isBlocked = isBlocked,
                        onFollow = { onFollowClick() },
                        onBlock = {
                            if (isBlocked) {
                                app.blacklist.unblockTopic(title)
                            } else {
                                app.blacklist.blockTopic(title)
                            }
                            isBlocked = !isBlocked
                        },
                    )
                }
            } else {
                FeedList(viewModel = viewModel, scrollBehavior = scrollBehavior, contentPadding = padding)
            }
        }
    }

    if (showCompose) {
        ReplyComposerSheet(
            target = ReplyTarget(
                targetType = if (isTopic) "tag" else "product_phone",
                targetId = topic.string("id"),
                title = title,
            ),
            onDismiss = { showCompose = false },
            onPosted = { showCompose = false },
        )
    }
}

private suspend fun postFollow(
    repository: CoolapkRepository,
    context: Context,
    isTopic: Boolean,
    tag: String,
    id: String,
    followed: Boolean,
) {
    if (isTopic) {
        repository.followTag(tag, followed)
    } else {
        repository.changeProductFollow(id, followed)
    }
    context.toast(if (followed) "取消关注成功" else "关注成功")
}

@Composable
private fun BackIcon(navigator: Navigator) {
    IconButton(onClick = { navigator.pop() }) {
        Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
    }
}

private fun discussUrl(id: String, sort: TopicSortType) = when (sort) {
    TopicSortType.Default -> "/page?url=/product/feedList?type=feed&id=$id&ignoreEntityById=1"
    TopicSortType.Dateline -> "/page?url=/product/feedList?type=feed&id=$id&ignoreEntityById=1&listType=dateline_desc"
    TopicSortType.Hot -> "/page?url=/product/feedList?type=feed&id=$id&listType=rank_score"
}

private fun discussTitle(sort: TopicSortType) = when (sort) {
    TopicSortType.Default -> "默认"
    TopicSortType.Dateline -> "最新"
    TopicSortType.Hot -> "热度"
}

@Composable
private fun TopicHeader(
    topic: Entity,
    isFollow: Boolean,
    isBlocked: Boolean,
    onFollow: () -> Unit,
    onBlock: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                topic.logo.ifEmpty { topic.pic },
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                contentDescription = topic.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(topic.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (topic.followNum.isNotEmpty()) {
                    Text(
                        "${topic.followNum}人关注",
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                text = if (isFollow) "取消关注" else "关注",
                onClick = onFollow,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = if (isBlocked) "取消屏蔽" else "屏蔽",
                onClick = onBlock,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** [FeedList] with a header item, which the shared list has no slot for. */
@Composable
private fun TopicFeedList(
    viewModel: FeedListViewModel,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

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

    PullToRefresh(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = viewModel::refresh,
        pullToRefreshState = rememberPullToRefreshState(),
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
            item(contentType = "header") { header() }
            when (val status = viewModel.status) {
                ListStatus.Loading -> item(contentType = "state") { StateBox { InfiniteProgressIndicator() } }
                ListStatus.Empty -> item(contentType = "state") {
                    StateBox {
                        EmptyState(title = "暂无内容", actionText = "刷新", onActionClick = viewModel::refresh)
                    }
                }
                is ListStatus.Error -> item(contentType = "state") {
                    StateBox {
                        EmptyState(
                            title = "加载失败",
                            subtitle = status.message,
                            actionText = "重试",
                            onActionClick = viewModel::refresh,
                        )
                    }
                }
                ListStatus.Content -> {
                    itemsIndexed(
                        viewModel.items.filterNot(FeedActions::isHidden),
                        contentType = { _, item -> item.entityTemplate.ifEmpty { item.entityType } },
                    ) { _, item -> EntityItem(item) }
                    item(contentType = "footer") { TopicFooter(viewModel) }
                }
            }
        }
    }
}

@Composable
private fun StateBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun TopicFooter(viewModel: FeedListViewModel) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (val status = viewModel.footer) {
            FooterStatus.Idle, FooterStatus.Loading -> InfiniteProgressIndicator()
            FooterStatus.End -> Text(
                "没有更多了",
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            is FooterStatus.Error -> TextButton(
                text = "${status.message}，点击重试",
                onClick = viewModel::retryFooter,
            )
        }
    }
}
