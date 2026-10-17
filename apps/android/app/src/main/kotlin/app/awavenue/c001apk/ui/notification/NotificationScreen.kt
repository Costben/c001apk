package app.awavenue.c001apk.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.icon.extended.Help
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.chat.MessageRow
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.EntityItem
import app.awavenue.c001apk.ui.feed.FeedActions
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.NotificationType
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** The endpoint each notification list reads, as the Flutter client maps them. */
private fun notificationUrl(type: NotificationType): String = when (type) {
    NotificationType.At -> "/v6/notification/atMeList"
    NotificationType.Comment -> "/v6/notification/atCommentMeList"
    NotificationType.Like -> "/v6/notification/feedLikeList"
    NotificationType.Follow -> "/v6/notification/contactsFollowList"
    NotificationType.Message -> "/v6/message/list"
    NotificationType.System -> "/v6/notification/list"
}

@Composable
fun NotificationScreen(
    type: NotificationType,
    systemCount: Int = 0,
    title: String? = null,
    showShortcuts: Boolean = false,
) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val scrollBehavior = COUIScrollBehavior()
    val backdrop = rememberGlassBackdrop()

    // Opened from 私信与信息 the count is cleared by reading it, as the Flutter row does.
    var unreadSystem by rememberSaveable { mutableIntStateOf(systemCount) }

    val viewModel = viewModel(key = "notification-${type.name}") {
        FeedListViewModel(
            PageLoader { page, first, last ->
                app.repository.notificationList(notificationUrl(type), page, first, last)
            },
        )
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = title ?: type.label,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = rememberHapticClick { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NotificationList(
            viewModel = viewModel,
            scrollBehavior = scrollBehavior,
            backdrop = backdrop,
            leading = when {
                showShortcuts -> {
                    {
                        NotificationShortcuts { target ->
                            navigator.push(Route.Notification(target))
                        }
                    }
                }

                type == NotificationType.Message -> {
                    {
                        SystemEntry(unreadSystem) {
                            unreadSystem = 0
                            navigator.push(Route.Notification(NotificationType.System))
                        }
                    }
                }

                else -> null
            },
            contentPadding = PaddingValues(
                start = 10.dp,
                end = 10.dp,
                top = innerPadding.calculateTopPadding() + 10.dp,
                bottom = innerPadding.calculateBottomPadding() + 10.dp,
            ),
        )
    }
}

@Composable
private fun NotificationList(
    viewModel: FeedListViewModel,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    contentPadding: PaddingValues,
    leading: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val listState = rememberLazyListState()
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

        ListStatus.Content -> PullToRefresh(
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
                    .glassSource(backdrop)
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leading != null) item(contentType = "leading") { leading() }
                items(viewModel.items.filterNot(FeedActions::isHidden), key = { it.id.ifEmpty { it.entityId } }) { item ->
                    when (item.entityType) {
                        "notification" -> NotificationItem(item, onChanged = viewModel::refresh)

                        "message" -> MessageRow(item, showUnread = item.id !in clearedMessages) {
                            clearedMessages = clearedMessages + item.id
                            navigator.push(
                                Route.Chat(
                                    item.string("ukey"),
                                    item.string("messageUid"),
                                    item.string("messageUsername"),
                                ),
                            )
                        }

                        else -> EntityItem(item)
                    }
                }
                item(contentType = "footer") { Footer(viewModel.footer, viewModel::retryFooter) }
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
private fun Footer(status: FooterStatus, onRetry: () -> Unit) {
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

/** 酷安小秘书: the system notification stream, listed as its own conversation. */
@Composable
private fun SystemEntry(count: Int, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(10.dp),
        onClick = rememberHapticClick(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(COUITheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(COUIIcons.Help, contentDescription = null, tint = COUITheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("酷安小秘书", maxLines = 1)
                Text(
                    "系统通知与信息",
                    maxLines = 1,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (count > 0) Badge(count = count)
        }
    }
}

private val LikeTileColors = Color(0xFFFFE9E7) to Color(0xFFE8503A)
private val AtTileColors = Color(0xFFE8F1FF) to Color(0xFF2F7CF6)
private val MessageTileColors = Color(0xFFFFF1E2) to Color(0xFFF0A020)

/** The three stacks the notification centre opens with, as the official 通知 page lays them out. */
@Composable
private fun NotificationShortcuts(onOpen: (NotificationType) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortcutTile(AppIcons.ThumbUpOutlined, "点赞", LikeTileColors) { onOpen(NotificationType.Like) }
        ShortcutTile(AppIcons.ChatBubbleOutlined, "@我", AtTileColors) { onOpen(NotificationType.At) }
        ShortcutTile(COUIIcons.Email, "私信", MessageTileColors) { onOpen(NotificationType.Message) }
    }
}

@Composable
private fun RowScope.ShortcutTile(
    icon: ImageVector,
    label: String,
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = rememberHapticClick(onClick = onClick))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(19.dp)).background(colors.first),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp), tint = colors.second)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = COUITheme.textStyles.body2)
    }
}
