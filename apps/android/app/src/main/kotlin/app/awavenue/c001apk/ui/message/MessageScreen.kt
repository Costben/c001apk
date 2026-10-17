package app.awavenue.c001apk.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.HistoryType
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.chat.MessageRow
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.EntityItem
import app.awavenue.c001apk.ui.feed.FeedActions
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.FFFListType
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.NotificationType
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.notification.NotificationItem
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.VerticalDivider
import io.github.suqi8.coui.kmp.basic.couiGlassSurface
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.ContactsBook
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Folder
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Location
import io.github.suqi8.coui.kmp.icon.extended.Recent
import io.github.suqi8.coui.kmp.icon.extended.Reply
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val EntryColors = listOf(
    Color(0xFF2196F3),
    Color(0xFF00BCD4),
    Color(0xFF4CAF50),
    Color(0xFFF44336),
    Color(0xFFFF9800),
)

private val EntryTitles = listOf("@我的动态", "@我的评论", "我收到的赞", "好友关注", "私信")

@Composable
fun MessageScreen(outerPadding: PaddingValues) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberGlassBackdrop()

    val isLogin = app.session.isLogin
    var profile by remember { mutableStateOf<Entity?>(null) }
    var counts by remember { mutableStateOf<List<Int?>>(emptyList()) }
    var clearedMessages by remember { mutableStateOf(emptySet<String>()) }
    var confirmLogout by remember { mutableStateOf(false) }

    // The conversation stream needs an account; asking for it while logged out would only raise a
    // challenge.
    val viewModel = if (isLogin) {
        // Keyed by account, so a re-login never shows the previous account's conversations.
        viewModel(key = "message-list-${app.session.uid}") {
            FeedListViewModel(
                PageLoader { page, first, last ->
                    app.repository.notificationList("/v6/notification/list", page, first, last)
                },
            )
        }
    } else {
        null
    }

    suspend fun reload() {
        if (!app.session.isLogin) return
        try {
            val user = app.repository.profile(app.session.uid)
            profile = user
            app.session.saveProfile(
                avatar = user.userAvatar,
                level = user.int("level") ?: 0,
                exp = user.int("experience") ?: 0,
                nextExp = user.int("nextLevelExperience") ?: 1,
            )
            val count = app.repository.checkCount()
            counts = listOf(
                count.int("atme"),
                count.int("atcommentme"),
                count.int("feedlike"),
                count.int("contacts_follow"),
                count.int("message"),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.toast(ApiFailure.from(e).message)
        }
    }

    LaunchedEffect(isLogin) {
        if (isLogin) {
            reload()
        } else {
            profile = null
            counts = emptyList()
        }
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                MessageHeader(
                    user = profile,
                    isLogin = isLogin,
                    onLogin = { navigator.push(Route.Login) },
                    onLogout = { confirmLogout = true },
                )
            }
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()

        if (viewModel != null) {
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
        }

        MessageList(
            listState = listState,
            viewModel = viewModel,
            backdrop = backdrop,
            isLogin = isLogin,
            profile = profile,
            counts = counts,
            clearedMessages = clearedMessages,
            onClearMessage = { clearedMessages = clearedMessages + it },
            onClearCount = { index ->
                if (index < counts.size) {
                    counts = counts.toMutableList().also { it[index] = null }
                }
            },
            onRefresh = {
                scope.launch {
                    reload()
                    viewModel?.refresh()
                }
            },
            contentPadding = PaddingValues(
                start = 10.dp,
                end = 10.dp,
                top = innerPadding.calculateTopPadding() + 10.dp,
                bottom = outerPadding.calculateBottomPadding() + 10.dp,
            ),
        )
    }

    if (confirmLogout) {
        LogoutDialog(
            onConfirm = {
                confirmLogout = false
                clearedMessages = emptySet()
                counts = emptyList()
                profile = null
                app.session.logout()
            },
            onCancel = { confirmLogout = false },
        )
    }
}

@Composable
private fun MessageHeader(
    user: Entity?,
    isLogin: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val backdrop = LocalBackdrop.current
    val level = user?.int("level") ?: app.session.level
    val exp = user?.int("experience") ?: app.session.exp
    val nextExp = user?.int("nextLevelExperience") ?: app.session.nextExp
    val username = user?.username?.ifEmpty { app.session.username } ?: app.session.username

    Column(
        Modifier
            .fillMaxWidth()
            .couiGlassSurface(
                backdrop = backdrop,
                shape = RectangleShape,
                containerColor = COUITheme.colorScheme.surface,
                fallback = Modifier.background(COUITheme.colorScheme.surface),
            )
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .padding(top = 10.dp)
            .padding(bottom = 10.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!isLogin) {
                TextButton(text = "点击登录", onClick = onLogin)
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(20.dp))
                    NetworkImage(
                        user?.userAvatar?.ifEmpty { app.session.userAvatar } ?: app.session.userAvatar,
                        Modifier
                            .size(45.dp)
                            .clip(CircleShape)
                            .clickable {
                                val uid = user?.uid.orEmpty().ifEmpty { app.session.uid }
                                if (uid.isNotEmpty()) navigator.push(Route.User(uid))
                            },
                        contentDescription = username,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(3f), verticalArrangement = Arrangement.Center) {
                        Text(
                            username,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Lv.$level", modifier = Modifier.weight(1f), fontSize = 13.sp)
                            Text("$exp/$nextExp", fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = if (nextExp > 0) (exp.toFloat() / nextExp).coerceIn(0f, 1f) else 0f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onLogout) {
                        Icon(COUIIcons.Lock, contentDescription = "退出登录")
                    }
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun MessageList(
    listState: LazyListState,
    viewModel: FeedListViewModel?,
    backdrop: LayerBackdrop?,
    isLogin: Boolean,
    profile: Entity?,
    counts: List<Int?>,
    clearedMessages: Set<String>,
    onClearMessage: (String) -> Unit,
    onClearCount: (Int) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current

    PullToRefresh(
        isRefreshing = viewModel?.isRefreshing == true,
        onRefresh = onRefresh,
        pullToRefreshState = rememberPullToRefreshState(),
        contentPadding = contentPadding,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .overScrollVertical(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(contentType = "stats") {
                CellCard(
                    values = listOf(profile?.int("feed"), profile?.int("follow"), profile?.int("fans")),
                    titles = listOf("动态", "关注", "粉丝"),
                    icons = null,
                    onClicks = listOf(
                        { if (isLogin) navigator.push(Route.FFFList(FFFListType.Feed)) },
                        { if (isLogin) navigator.push(Route.FFFList(FFFListType.Follow)) },
                        { if (isLogin) navigator.push(Route.FFFList(FFFListType.Fan)) },
                    ),
                )
            }
            item(contentType = "shortcuts") {
                Column {
                    CellCard(
                        values = null,
                        titles = listOf("本地收藏", "浏览历史", "我的常去"),
                        icons = listOf(COUIIcons.Folder, COUIIcons.Recent, COUIIcons.Location),
                        onClicks = listOf(
                            { navigator.push(Route.History(HistoryType.Fav)) },
                            { navigator.push(Route.History(HistoryType.History)) },
                            { if (isLogin) navigator.push(Route.FFFList(FFFListType.Recent)) },
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    CellCard(
                        values = null,
                        titles = listOf("我的收藏", "我的赞", "我的回复"),
                        icons = listOf(COUIIcons.Favorites, AppIcons.ThumbUpOutlined, COUIIcons.Reply),
                        onClicks = listOf(
                            { if (isLogin) navigator.push(Route.FFFList(FFFListType.Collection)) },
                            { if (isLogin) navigator.push(Route.FFFList(FFFListType.Like)) },
                            { if (isLogin) navigator.push(Route.FFFList(FFFListType.Reply)) },
                        ),
                    )
                }
            }
            items(count = NotificationType.entries.size) { index ->
                NotificationEntry(
                    color = EntryColors[index],
                    icon = EntryIcons[index],
                    title = EntryTitles[index],
                    badge = counts.getOrNull(index),
                    onClick = {
                        if (!isLogin) return@NotificationEntry
                        navigator.push(Route.Notification(NotificationType.entries[index]))
                        onClearCount(index)
                    },
                )
            }

            if (viewModel != null) {
                when (val status = viewModel.status) {
                    ListStatus.Loading -> item(contentType = "loading") {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            InfiniteProgressIndicator()
                        }
                    }

                    ListStatus.Empty -> item(contentType = "empty") {
                        MessageNotice("暂无内容", viewModel::refresh)
                    }

                    is ListStatus.Error -> item(contentType = "error") {
                        MessageNotice(status.message, viewModel::refresh)
                    }

                    ListStatus.Content -> {
                        items(viewModel.items.filterNot(FeedActions::isHidden), key = { it.id.ifEmpty { it.entityId } }) { item ->
                            when (item.entityType) {
                                "message" -> MessageRow(item, showUnread = item.id !in clearedMessages) {
                                    onClearMessage(item.id)
                                    navigator.push(
                                        Route.Chat(
                                            item.string("ukey"),
                                            item.string("messageUid"),
                                            item.string("messageUsername"),
                                        ),
                                    )
                                }

                                "notification" -> NotificationItem(item, onChanged = viewModel::refresh)

                                else -> EntityItem(item)
                            }
                        }
                        item(contentType = "footer") {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when (val footer = viewModel.footer) {
                                    FooterStatus.Idle, FooterStatus.Loading -> InfiniteProgressIndicator()
                                    FooterStatus.End -> Text(
                                        "没有更多了",
                                        style = COUITheme.textStyles.footnote1,
                                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    is FooterStatus.Error -> TextButton(
                                        text = "${footer.message}，点击重试",
                                        onClick = viewModel::retryFooter,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The three-up card of the message tab: counters, or shortcuts when [icons] is given. */
@Composable
private fun CellCard(
    values: List<Int?>?,
    titles: List<String>,
    icons: List<ImageVector>?,
    onClicks: List<() -> Unit>,
) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            titles.indices.forEach { index ->
                if (index > 0) {
                    VerticalDivider(
                        Modifier
                            .padding(vertical = 10.dp)
                            .width(1.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onClicks[index])
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val icon = icons?.getOrNull(index)
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        Text(
                            values?.getOrNull(index)?.toString().orEmpty(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(titles[index], maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val EntryIcons = listOf(
    COUIIcons.Community,
    AppIcons.ChatBubbleOutlined,
    AppIcons.ThumbUpOutlined,
    COUIIcons.ContactsBook,
    COUIIcons.Email,
)

@Composable
private fun NotificationEntry(
    color: Color,
    icon: ImageVector,
    title: String,
    badge: Int?,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(10.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (badge != null && badge > 0) {
                Badge(count = badge)
                Spacer(Modifier.width(6.dp))
            }
            Icon(COUIIcons.ChevronForward, contentDescription = null, tint = COUITheme.colorScheme.outline)
        }
    }
}

@Composable
private fun MessageNotice(message: String, onReload: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onReload)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message)
    }
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    OverlayAlertDialog(
        show = true,
        title = "确定退出登录？",
        confirmText = "确定",
        cancelText = "取消",
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}
