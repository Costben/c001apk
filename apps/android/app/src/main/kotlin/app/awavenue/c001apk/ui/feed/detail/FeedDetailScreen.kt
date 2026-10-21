package app.awavenue.c001apk.ui.feed.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailureKind
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppFloatingActionButton
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.CounterButton
import app.awavenue.c001apk.ui.common.appTabRowColors
import app.awavenue.c001apk.ui.common.MoreMenu
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.PinnedHeader
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.common.shareText
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ImageGrid
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.ReplyCard
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import app.awavenue.c001apk.ui.text.HtmlText
import app.awavenue.c001apk.ui.text.HtmlTextDefaults
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Reply
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private val CardPadding = 12.dp
private val InsetShape = RoundedCornerShape(12.dp)

/** [preview] is the list entry the post was opened from; it stands in for the post while it loads. */
@Composable
fun FeedDetailScreen(id: String, preview: Entity? = null) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = viewModel(key = "feed-detail-$id") {
        FeedDetailViewModel(id, app.repository, app.settings, app.history, app.blacklist)
    }
    val backdrop = rememberGlassBackdrop()
    val scrollBehavior = rememberPageScrollBehavior("feed-detail")
    val listState = rememberPageListState("feed-detail")

    var menuOpen by remember { mutableStateOf(false) }
    var accountDialog by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<ReplyTarget?>(null) }
    var fabVisible by remember { mutableStateOf(true) }

    LaunchedEffect(viewModel.notice) {
        viewModel.notice?.let {
            context.toast(it)
            viewModel.notice = null
        }
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
            layout.totalItemsCount > 0 && last >= layout.totalItemsCount - 3
        }.distinctUntilChanged().filter { it }.collect { viewModel.loadMoreReplies() }
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

    val scrolledPastHeader by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 50 }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = when {
                        scrolledPastHeader -> viewModel.feedUsername
                        viewModel.feed == null -> preview?.string("feedTypeName").orEmpty()
                        else -> viewModel.feedTypeName.orEmpty()
                    },
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (viewModel.feed != null) {
                            if (viewModel.isPublic && app.session.isLogin) {
                                TopBarIconButton(
                                    onClick = { accountDialog = true },
                                    enabled = !viewModel.isLoadingDetail,
                                ) {
                                    if (viewModel.isLoadingDetail) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(COUIIcons.Info, contentDescription = "账号信息暂不可用")
                                    }
                                }
                            }
                            Box {
                                TopBarIconButton(onClick = { menuOpen = true }) {
                                    Icon(COUIIcons.More, contentDescription = "更多")
                                }
                                MoreMenu(
                                    show = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    entries = listOf(
                                        "复制" to { context.copyText(viewModel.shareUrl) },
                                        "分享" to { context.shareText(viewModel.shareUrl) },
                                        (if (viewModel.isFav) "取消收藏" else "收藏") to viewModel::toggleFav,
                                        (if (viewModel.isBlocked) "取消屏蔽" else "屏蔽") to viewModel::toggleBlock,
                                        "举报" to {
                                            if (viewModel.feedUid.isNotEmpty()) {
                                                navigator.push(Route.Web(viewModel.reportUrl))
                                            }
                                        },
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            AppFloatingActionButton(
                visible = app.session.isLogin && viewModel.feed != null && fabVisible,
                backdrop = backdrop,
                onClick = {
                    replyTarget = ReplyTarget(
                        id = viewModel.id,
                        type = "feed",
                        targetType = "",
                        targetId = "",
                        title = viewModel.feedUsername,
                        feedType = "feed",
                    )
                },
                icon = COUIIcons.Reply,
                contentDescription = "回复",
            )
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize().glassSource(backdrop)) {
                when (val status = viewModel.feedStatus) {
                    // The page grows out of the tapped card, so it shows that card's post until the full
                    // one arrives instead of an empty page with a spinner.
                    ListStatus.Loading -> if (preview == null) {
                        CenterBox(innerPadding) { InfiniteProgressIndicator() }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                            ),
                            userScrollEnabled = false,
                        ) {
                            item(contentType = "feed") {
                                FeedDetailBody(preview, Modifier.padding(horizontal = CardPadding))
                            }
                            item(contentType = "reply-state") { ReplyStateBox { InfiniteProgressIndicator() } }
                        }
                    }

                    ListStatus.Empty -> CenterBox(innerPadding) {
                        EmptyState(
                            title = "暂无内容",
                            actionText = "刷新",
                            onActionClick = { viewModel.loadFeed() },
                        )
                    }

                    is ListStatus.Error -> {
                        val challenge = viewModel.feedFailureKind in setOf(ApiFailureKind.Captcha, ApiFailureKind.Authentication)
                        CenterBox(innerPadding) {
                            EmptyState(
                                title = status.message,
                                actionText = if (challenge) "验证" else "重试",
                                onActionClick = { viewModel.loadFeed(verify = challenge) },
                            )
                        }
                    }

                    ListStatus.Content -> {
                        val feed = viewModel.feed ?: return@Box
                        PullToRefresh(
                            isRefreshing = viewModel.isRefreshing,
                            onRefresh = viewModel::refreshReplies,
                            pullToRefreshState = rememberPullToRefreshState(),
                            contentPadding = innerPadding,
                            topAppBarScrollBehavior = scrollBehavior,
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .overScrollVertical()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                                ),
                            ) {
                                feedBody(feed, viewModel)
                                item(key = "reply-header", contentType = "reply-header") {
                                    ReplyHeader(viewModel)
                                }
                                when (val replyStatus = viewModel.replyStatus) {
                                    ListStatus.Loading -> item(contentType = "reply-state") {
                                        ReplyStateBox { InfiniteProgressIndicator() }
                                    }
                                    ListStatus.Empty -> item(contentType = "reply-state") {
                                        ReplyStateBox { Text("暂无内容") }
                                    }
                                    is ListStatus.Error -> item(contentType = "reply-state") {
                                        ReplyStateBox(onClick = viewModel::refreshReplies) { Text(replyStatus.message) }
                                    }
                                    ListStatus.Content -> {
                                        itemsIndexed(
                                            viewModel.replies,
                                            key = { index, reply -> reply.entityId.ifEmpty { "reply-$index" } },
                                            contentType = { _, _ -> "reply" },
                                        ) { index, reply ->
                                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                                ReplyCard(
                                                    reply = reply,
                                                    onOpenFeed = {
                                                        val url = reply.url.ifEmpty { "${Protocol.URL_COOLAPK}/feed/$id" }
                                                        navigator.push(Route.Web(url))
                                                    },
                                                    isTopReply = viewModel.showsPinnedReply && index == 0,
                                                    canPin = viewModel.feedUid.isNotEmpty() &&
                                                        viewModel.feedUid == app.session.uid,
                                                    onTogglePin = {
                                                        val pinned = viewModel.pinnedReplyId == reply.id
                                                        viewModel.onTogglePinReply(reply.id, !pinned)
                                                    },
                                                    onBlock = viewModel::onBlockReply,
                                                    onReply = { replyId, username, _ ->
                                                        replyTarget = ReplyTarget(
                                                            id = replyId,
                                                            type = "reply",
                                                            title = username,
                                                            feedType = "feed",
                                                        )
                                                    },
                                                    onDelete = viewModel::onDeleteReply,
                                                    onLike = { replyId, liked ->
                                                        viewModel.onLike(replyId, liked, isFeed = false)
                                                    },
                                                    onShowReplies = {
                                                        navigator.push(
                                                            Route.Reply2Reply(
                                                                id = it.id,
                                                                replyId = viewModel.id,
                                                                originUid = it.uid,
                                                                origin = it,
                                                            ),
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                        item(contentType = "footer") {
                                            // Reaching the footer asks for the next page, as upstream's footer does.
                                            // The scroll trigger alone misses a list that already ends on screen:
                                            // it never changes from "near the end", so it never fires again.
                                            LaunchedEffect(viewModel.replies) { viewModel.loadMoreReplies() }
                                            ReplyFooter(viewModel.replyFooter, viewModel::retryReplyFooter)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (viewModel.feedStatus == ListStatus.Content) {
                    PinnedHeader(
                        listState = listState,
                        headerIndex = if (viewModel.articles.isEmpty()) 1 else viewModel.articles.size + 2,
                        topInset = innerPadding.calculateTopPadding(),
                    ) {
                        ReplyHeader(viewModel)
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = accountDialog,
        title = "账号信息暂不可用",
        summary = viewModel.accountDetailNotice,
        onDismissRequest = { accountDialog = false },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(text = "关闭", onClick = { accountDialog = false })
            Spacer(Modifier.width(8.dp))
            TextButton(
                text = "重试账号详情",
                onClick = {
                    accountDialog = false
                    viewModel.loadFeed(verify = true)
                },
            )
        }
    }

    replyTarget?.let { target ->
        ReplyComposerSheet(
            target = target,
            onDismiss = { replyTarget = null },
            onPosted = {
                replyTarget = null
                viewModel.refreshReplies()
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.feedBody(feed: Entity, viewModel: FeedDetailViewModel) {
    if (viewModel.articles.isEmpty()) {
        item(contentType = "feed") {
            FeedDetailBody(feed, Modifier.padding(horizontal = CardPadding))
        }
        return
    }
    item(contentType = "feed-header") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            FeedAuthorRow(feed, showDate = true)
        }
    }
    itemsIndexed(viewModel.articles, contentType = { _, article -> article.type }) { _, article ->
        ArticleBody(article, viewModel.articleImages, Modifier.padding(top = 12.dp))
    }
    item(contentType = "feed-footer") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            FeedCounters(
                feed,
                onLike = { viewModel.onLike(feed.id, feed.isLiked, isFeed = true) },
            )
            SectionBadgeRow(feed, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ReplyHeader(viewModel: FeedDetailViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(COUITheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "共 ${viewModel.replyCount} 回复",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            // The same segmented capsule as the home bar, in the tiny style: four labels plus
            // the reply count have to share one line, so the count is measured first and the
            // capsule takes what is left.
            TabRowWithContour(
                tabs = ReplySortType.entries.map { it.label },
                selectedTabIndex = viewModel.replySort.ordinal,
                onTabSelected = { viewModel.selectReplySort(ReplySortType.entries[it]) },
                colors = appTabRowColors(),
                height = TabRowDefaults.TabRowWithContourTinyHeight,
                cornerRadius = TabRowDefaults.TabRowWithContourTinyCornerRadius,
                contourPadding = TabRowDefaults.TabRowWithContourTinyPadding,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The body of the detail page without its reply list, reused wherever a feed is shown in full. */
@Composable
fun FeedDetailBody(feed: Entity, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    // Upstream's feed content card copies its text on a long press but has no tap or ripple.
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = {},
                onLongClick = { navigator.push(Route.Copy(feed.message)) },
            ),
    ) {
        Spacer(Modifier.height(12.dp))
        FeedAuthorRow(feed, showDate = true)
        if (feed.messageTitle.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            HtmlText(
                feed.messageTitle,
                style = HtmlTextDefaults.style().copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
        if (feed.message.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            HtmlText(feed.message, style = HtmlTextDefaults.style().copy(fontSize = 16.sp))
        }
        if (feed.picArr.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ImageGrid(feed.picArr, onClick = { index -> navigator.push(Route.ImageView(feed.picArr, index)) })
        }
        if (feed.forwardSourceType.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ForwardSource(feed.forwardSourceFeed)
        }
        if (feed.extraUrl.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ExtraLink(feed)
        }
        val hotReply = feed.replyRows.firstOrNull()
        if (hotReply != null) {
            Spacer(Modifier.height(10.dp))
            val hotReplyPics = hotReply.picArr
            val hotReplyPic = if (hotReplyPics.isEmpty()) {
                ""
            } else {
                " <a href=\"${hotReply.pic}\">查看图片(${hotReplyPics.size})</a>"
            }
            InsetBlock(onLongClick = { navigator.push(Route.Copy(hotReply.message)) }) {
                HtmlText(
                    "<a href=\"/u/${hotReply.uid}\">${hotReply.username}</a>: ${hotReply.message}$hotReplyPic",
                    style = COUITheme.textStyles.footnote1,
                    maxLines = 3,
                    onShowTotalReply = { navigator.push(Route.Feed(feed.id)) },
                    picArr = hotReplyPics,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FeedCounters(feed, onLike = null)
        SectionBadgeRow(feed, Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun FeedAuthorRow(feed: Entity, showDate: Boolean) {
    val navigator = LocalNavigator.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        NetworkImage(
            feed.userAvatar,
            Modifier
                .size(35.dp)
                .clip(CircleShape)
                .clickable { navigator.push(Route.User(feed.uid)) },
            contentDescription = feed.username,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(feed.username, style = COUITheme.textStyles.body1.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val info = listOfNotNull(
                fromToday(feed.dateline).takeIf { showDate && it.isNotEmpty() },
                stripHtml(feed.infoHtml).takeIf { it.isNotEmpty() },
                stripHtml(feed.deviceTitle).takeIf { it.isNotEmpty() },
            )
            if (info.isNotEmpty()) {
                Text(
                    info.joinToString("  "),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FeedCounters(feed: Entity, modifier: Modifier = Modifier, onLike: (() -> Unit)?) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            feed.string("ip_location").let { if (it.isEmpty()) "" else "发布于 $it" },
            modifier = Modifier.weight(1f),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (feed.replyNum.isNotEmpty()) {
            CounterButton(AppIcons.ChatBubbleOutlined, feed.replyNum, highlighted = false, onClick = null)
        }
        if (feed.likeNum.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            CounterButton(
                if (feed.isLiked) AppIcons.ThumbUp else AppIcons.ThumbUpOutlined,
                feed.likeNum,
                feed.isLiked,
                onLike,
            )
        }
    }
}

@Composable
private fun ForwardSource(source: Entity?) {
    val navigator = LocalNavigator.current
    InsetBlock(
        onClick = source?.let { { navigator.push(Route.Feed(it.id)) } },
        onLongClick = source?.let { { navigator.push(Route.Copy(it.message)) } },
    ) {
        if (source == null) {
            Text("动态已被删除", color = COUITheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
            return@InsetBlock
        }
        val author = "<a href=\"/u/${source.uid}\">@${source.username}</a>: "
        if (source.messageTitle.isNotEmpty()) {
            HtmlText(author + source.messageTitle, maxLines = 2)
            HtmlText(source.message, maxLines = 6)
        } else {
            HtmlText(author + source.message, maxLines = 6)
        }
        if (source.picArr.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ImageGrid(source.picArr, onClick = { navigator.push(Route.Feed(source.id)) })
        }
    }
}

@Composable
private fun ExtraLink(feed: Entity) {
    val openLink = rememberLinkOpener()
    InsetBlock(onClick = { openLink(feed.extraUrl, feed.extraTitle) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(feed.extraPic, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(feed.extraTitle.ifEmpty { feed.extraUrl }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    feed.extraUrl,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InsetBlock(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(InsetShape)
            .background(COUITheme.colorScheme.surface)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .padding(10.dp),
    ) { content() }
}

@Composable
private fun ReplyFooter(status: FooterStatus, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (status) {
            FooterStatus.Idle, FooterStatus.Loading -> InfiniteProgressIndicator()
            FooterStatus.End -> Text(
                "没有更多了",
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            is FooterStatus.Error -> TextButton(text = "${status.message}，点击重试", onClick = onRetry)
        }
    }
}

/** The 80dp slot the reply list shows instead of rows while it loads, is empty or failed. */
@Composable
private fun ReplyStateBox(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CenterBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val tagPattern = Regex("<[^>]*>")

private fun stripHtml(html: String): String =
    tagPattern.replace(html, "").replace("&nbsp;", " ").replace("&amp;", "&").trim()

/**
 * The topic or product this post belongs to, right under the publish location the way the official
 * client shows it. Tapping it opens the section.
 */
@Composable
private fun SectionBadgeRow(feed: Entity, modifier: Modifier = Modifier) {
    val url = feed.string("turl")
    if (url.isEmpty()) return
    val title = feed.string("ttitle")
    val icon = feed.string("tpic")
    val openLink = rememberLinkOpener()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(COUITheme.colorScheme.surfaceContainerHigh)
            .clickable { openLink(url, title) }
            .padding(start = 5.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon.isNotEmpty()) {
            NetworkImage(icon, Modifier.size(22.dp).clip(CircleShape), contentDescription = title)
        } else {
            Text(
                "#",
                modifier = Modifier.size(22.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
