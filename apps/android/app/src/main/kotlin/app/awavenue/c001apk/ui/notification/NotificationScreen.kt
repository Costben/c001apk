package app.awavenue.c001apk.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.C001apkApp
import app.awavenue.c001apk.data.UnreadStore
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.chat.MessageRow
import app.awavenue.c001apk.ui.common.AppBarTitleSize
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.EntityItem
import app.awavenue.c001apk.ui.feed.FeedActions
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.LikeCard
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.LocalRouteTransition
import app.awavenue.c001apk.ui.nav.NotificationType
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SearchFilterTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.All
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.ContactsCircle
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Reply
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class NotificationCategory(
    val type: NotificationType,
    val label: String,
    val icon: ImageVector,
)

private val NotificationCategories = listOf(
    NotificationCategory(NotificationType.System, "全部", COUIIcons.All),
    NotificationCategory(NotificationType.Comment, "评论", COUIIcons.Reply),
    NotificationCategory(NotificationType.Like, "点赞", AppIcons.ThumbUpOutlined),
    NotificationCategory(NotificationType.At, "@我", AppIcons.ChatBubbleOutlined),
    NotificationCategory(NotificationType.Follow, "关注", COUIIcons.ContactsCircle),
    NotificationCategory(NotificationType.Message, "私信", COUIIcons.Email),
)

@Composable
fun NotificationScreen(
    type: NotificationType = NotificationType.System,
    title: String? = null,
    showShortcuts: Boolean = false,
) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberPageScrollBehavior("notification")
    val backdrop = rememberGlassBackdrop()
    val scope = rememberCoroutineScope()

    // True when this page was composed only to be revealed under a back gesture. It is a preview of
    // the page the pop will land on, not the page the user is on, so it must not fetch counters or
    // clear badges the user would still be looking at if the gesture is let go.
    val preview = LocalRouteTransition.current?.preview == true

    LaunchedEffect(Unit) {
        if (!preview) app.unread.refresh()
    }
    DisposableEffect(Unit) {
        onDispose { if (!preview) app.unread.markAllRead() }
    }

    val initialIndex = remember(type) {
        val found = NotificationCategories.indexOfFirst { it.type == type }
        if (found >= 0) found else 0
    }
    // The tab lives on the entry, not in the saved state: the page under a back gesture is composed
    // outside the slot its state was saved from, where `rememberPagerState` finds nothing and opens
    // on 全部, then jumps to the tab the user left once the pop lands.
    val pagerState = rememberPagerState(
        initialPage = pageMemoryPage("notification", initialIndex),
    ) { NotificationCategories.size }
    rememberPageMemoryRecorder("notification", pagerState.currentPage)

    LaunchedEffect(pagerState.currentPage) {
        if (preview) return@LaunchedEffect
        val currentCategory = NotificationCategories[pagerState.currentPage].type
        app.unread.markRead(*currentCategory.unreadFields)
    }

    // The bar never collapses; pinning the range keeps the nested-scroll connection from eating
    // the list's drags for a height change this page does not draw.
    SideEffect { scrollBehavior.state.heightOffsetLimit = 0f }

    Scaffold(
        edgeToEdge = true,
        topBar = {
            val density = LocalDensity.current
            val hapticFeedback = LocalHapticFeedback.current
            var menuOpen by remember { mutableStateOf(false) }
            val materialScrollFraction = remember(scrollBehavior, density) {
                {
                    AppBarBlurOverlayDefaults.scrollAlpha(
                        abs(scrollBehavior.state.contentOffset),
                        density,
                    )
                }
            }
            val unreadRevision = app.unread.revision
            val chipTabs = remember(unreadRevision) {
                NotificationCategories.map { category ->
                    val count = app.unread.unread(*category.type.unreadFields)
                    ChipTabData(
                        label = category.label,
                        icon = category.icon,
                        count = count.takeIf { it > 0 },
                    )
                }
            }
            val horizontalPadding = 16.dp
            val rowGap = 6.dp
            val positionProvider = remember(horizontalPadding) {
                ListPopupDefaults.dropdownPositionProvider(horizontalMargin = horizontalPadding)
            }
            val unreadTotal = app.unread.total
            val menuEntries = remember(pagerState.currentPage, unreadTotal, hapticFeedback) {
                val confirm = { hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm) }
                listOf(
                    DropdownEntry(
                        items = NotificationCategories.mapIndexed { index, category ->
                            DropdownItem(
                                text = category.label,
                                selected = index == pagerState.currentPage,
                                onClick = {
                                    confirm()
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                            )
                        },
                    ),
                    DropdownEntry(
                        items = listOf(
                            DropdownItem(
                                text = "全部已读",
                                enabled = unreadTotal > 0,
                                onClick = {
                                    confirm()
                                    app.unread.markAllRead()
                                },
                            ),
                        ),
                    ),
                )
            }
            Box(Modifier.fillMaxWidth()) {
                // 材质、两行布局与胶囊采样的顺序交给库：材质先录进栏自己的层，两行画在录制之外，
                // 胶囊采的是「模糊提白之后」的画面，与搜索结果页保持一致。
                SearchFilterTopAppBar(
                    filters = chipTabs,
                    selectedFilter = pagerState.currentPage,
                    onFilterSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    firstRowHeight = TopAppBarDefaults.CollapsedHeight,
                    rowGap = rowGap,
                    leadingIcon = COUIIcons.GridView,
                    onLeadingIconClick = { menuOpen = true },
                    containerColor = COUITheme.colorScheme.surface,
                    backdrop = backdrop,
                    scrollFraction = materialScrollFraction,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TopAppBarDefaults.CollapsedHeight)
                            .padding(start = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TopBarIconButton(onClick = rememberHapticClick { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                        Text(
                            text = title ?: "消息通知",
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = TopAppBarDefaults.NavigationIconGap),
                            color = COUITheme.colorScheme.onSurface,
                            fontSize = with(density) { AppBarTitleSize.toSp() },
                            fontWeight = FontWeight(550),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    }
                }
                // 锚点对准左侧 32dp 网格胶囊，菜单从胶囊下方展开而不是整栏居中。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                        .padding(
                            top = TopAppBarDefaults.CollapsedHeight + rowGap,
                            start = horizontalPadding,
                        )
                        .size(ChipTabDefaults.Height),
                ) {
                    OverlayCascadingListPopup(
                        show = menuOpen,
                        entries = menuEntries,
                        onDismissRequest = { menuOpen = false },
                        popupPositionProvider = positionProvider,
                        alignment = PopupPositionProvider.Align.Start,
                        collapseOnSelection = true,
                    )
                }
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().glassSource(backdrop),
            ) { page ->
                val categoryType = NotificationCategories[page].type
                NotificationPage(
                    type = categoryType,
                    scrollBehavior = scrollBehavior,
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        top = innerPadding.calculateTopPadding() + 10.dp,
                        bottom = innerPadding.calculateBottomPadding() + 10.dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NotificationPage(
    type: NotificationType,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "notification-${type.name}") {
        FeedListViewModel(notificationLoader(app, type), app::contentGate)
    }
    NotificationList(
        type = type,
        viewModel = viewModel,
        scrollBehavior = scrollBehavior,
        contentPadding = contentPadding,
    )
}

/**
 * The paging source behind one category. `@我` reads both mention streams — a mention in a feed
 * and a mention in a comment — because the server files them apart while users read them as one
 * list.
 */
private fun notificationLoader(app: C001apkApp, type: NotificationType): PageLoader =
    when (type) {
        NotificationType.At -> MergedNotificationLoader(
            listOf(NotificationType.At, NotificationType.AtComment),
        ) { source, page, first, last ->
            app.repository.notificationList(source.endpoint, page, first, last)
        }

        NotificationType.Comment -> CommentNotificationLoader { page, first, last ->
            app.repository.notificationList(type.endpoint, page, first, last)
        }

        else -> PageLoader { page, first, last ->
            app.repository.notificationList(type.endpoint, page, first, last)
        }
    }

/** The notification `type` of a comment on your feed or a reply to your comment. */
private const val REPLY_NOTIFICATION_TYPE = "feed_reply"

/** How many raw pages one batch may scan for comment rows before it gives up. */
private const val COMMENT_SCAN_PAGES = 5

/**
 * The 评论 category is the general notification stream with everything but replies dropped: likes,
 * follows and mentions share the same endpoint. A page therefore arrives half empty, so the loader
 * keeps its own cursor and keeps asking for further pages until it has rows to show. The cursor is
 * internal because the list above counts the rows it was handed, which skips the pages this filter
 * consumed.
 */
private class CommentNotificationLoader(
    private val fetch: suspend (Int, String?, String?) -> List<Entity>,
) : PageLoader {
    private var nextPage = 0
    private var first: String? = null
    private var last: String? = null

    override suspend fun load(page: Int, firstItem: String?, lastItem: String?): List<Entity> {
        if (page <= 1) {
            nextPage = 0
            first = null
            last = null
        }
        repeat(COMMENT_SCAN_PAGES) {
            nextPage++
            val rows = fetch(nextPage, if (nextPage == 1) null else first, last)
            if (rows.isEmpty()) return emptyList()
            if (first == null) first = rows.first().id
            last = rows.last().id
            val comments = rows.filter { it.string("type") == REPLY_NOTIFICATION_TYPE }
            if (comments.isNotEmpty()) return comments
        }
        return emptyList()
    }
}

/**
 * Joins several notification streams into one list. Each stream keeps its own cursor: Coolapk's
 * `firstItem` / `lastItem` are ids inside that stream, so handing one stream's ids to another
 * would skip or repeat rows. Pages are returned newest first.
 */
private class MergedNotificationLoader(
    private val sources: List<NotificationType>,
    private val fetch: suspend (NotificationType, Int, String?, String?) -> List<Entity>,
) : PageLoader {
    private val firsts = arrayOfNulls<String>(sources.size)
    private val lasts = arrayOfNulls<String>(sources.size)

    override suspend fun load(page: Int, firstItem: String?, lastItem: String?): List<Entity> {
        // A refresh starts every stream over; later pages continue each one from where it stopped.
        if (page <= 1) {
            firsts.fill(null)
            lasts.fill(null)
        }
        val merged = ArrayList<Entity>()
        sources.forEachIndexed { index, source ->
            val rows = fetch(
                source,
                page,
                if (page <= 1) null else firsts[index],
                if (page <= 1) null else lasts[index],
            )
            if (rows.isNotEmpty()) {
                if (firsts[index] == null) firsts[index] = rows.first().id
                lasts[index] = rows.last().id
                merged += rows
            }
        }
        return merged.sortedByDescending { it.dateline }
    }
}

@Composable
private fun NotificationList(
    type: NotificationType,
    viewModel: FeedListViewModel,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    leading: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val listState = rememberPageListState("notification-${type.name}")
    // Reading a conversation drops its unread badge, the way the Flutter list clears it on tap.
    var clearedMessages by remember { mutableStateOf(emptySet<String>()) }
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

    when (val status = viewModel.status) {
        ListStatus.Loading -> CenterBox(contentPadding) { InfiniteProgressIndicator() }

        ListStatus.Empty -> Column(Modifier.fillMaxSize().padding(contentPadding)) {
            leading?.invoke()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leading != null) item(contentType = "leading") { leading() }
                items(
                    // 私信与信息 also carries a server-side 酷安小秘书 conversation. Its content is
                    // the notification stream the centre already shows, so listing it here doubled
                    // the unread the user had just read.
                    viewModel.items.filterNot(FeedActions::isHidden).filterNot { item ->
                        item.entityType == "message" && item.string("messageUsername") == SYSTEM_CONVERSATION
                    },
                    key = { it.id.ifEmpty { it.entityId } },
                ) { item ->
                    when {
                        item.entityType == "notification" -> NotificationItem(item, onChanged = viewModel::refresh)

                        item.entityType == "message" -> MessageRow(item, showUnread = item.id !in clearedMessages) {
                            clearedMessages = clearedMessages + item.id
                            navigator.push(
                                Route.Chat(
                                    item.string("ukey"),
                                    item.string("messageUid"),
                                    item.string("messageUsername"),
                                ),
                            )
                        }

                        // feedLikeList aggregates a post's likers into the post row rather than
                        // sending one notification per like.
                        item.likeUserInfo != null || item.string("likeUid").isNotEmpty() -> LikeCard(item)

                        else -> EntityItem(item)
                    }
                }
                item(contentType = "footer") { Footer(viewModel.footer, viewModel::retryFooter, viewModel::continueFiltered) }
            }
        }
    }
}

@Composable
private fun CenterBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) { content() }
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

private const val SYSTEM_CONVERSATION = "酷安小秘书"

private val LikeTileColors = Color(0xFFFFE9E7) to Color(0xFFE8503A)
private val AtTileColors = Color(0xFFE8F1FF) to Color(0xFF2F7CF6)
private val MessageTileColors = Color(0xFFFFF1E2) to Color(0xFFF0A020)

/**
 * The stacks the notification centre opens with. Each tile badges the counters behind its list, so
 * a like or a comment points at the stack it belongs to instead of a single combined number.
 */
@Composable
private fun NotificationShortcuts(
    unread: (NotificationType) -> Int,
    onOpen: (NotificationType) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortcutTile(AppIcons.ThumbUpOutlined, "点赞", LikeTileColors, unread(NotificationType.Like)) {
            onOpen(NotificationType.Like)
        }
        ShortcutTile(COUIIcons.Email, "评论", AtTileColors, unread(NotificationType.Comment)) {
            onOpen(NotificationType.Comment)
        }
        ShortcutTile(AppIcons.ChatBubbleOutlined, "@我", AtTileColors, unread(NotificationType.At)) {
            onOpen(NotificationType.At)
        }
        ShortcutTile(COUIIcons.ContactsCircle, "关注", MessageTileColors, unread(NotificationType.Follow)) {
            onOpen(NotificationType.Follow)
        }
        ShortcutTile(COUIIcons.Email, "私信", MessageTileColors, unread(NotificationType.Message)) {
            onOpen(NotificationType.Message)
        }
    }
}

/** The counters a list owns; opening it marks exactly these read. */
private val NotificationType.unreadFields: Array<String>
    get() = when (this) {
        // The tab merges both mention streams, so both counters belong to it.
        NotificationType.At -> arrayOf("atme", "atcommentme")
        NotificationType.AtComment -> arrayOf("atcommentme")
        // Only the replies themselves: `atcommentme` belongs to `@我`.
        NotificationType.Comment -> arrayOf("commentme")
        NotificationType.Like -> arrayOf("feedlike")
        NotificationType.Follow -> arrayOf("contacts_follow")
        NotificationType.Message -> arrayOf("message")
        NotificationType.System -> arrayOf("commentme", "atcommentme")
    }

@Composable
private fun RowScope.ShortcutTile(
    icon: ImageVector,
    label: String,
    colors: Pair<Color, Color>,
    badge: Int,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .clip(rememberSquircleShape(16.dp))
            .clickable(onClick = rememberHapticClick(onClick = onClick))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(rememberSquircleShape(17.dp)).background(colors.first),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp), tint = colors.second)
            if (badge > 0) {
                Box(Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                    Badge(count = badge, stroke = true)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = COUITheme.textStyles.body2)
    }
}
