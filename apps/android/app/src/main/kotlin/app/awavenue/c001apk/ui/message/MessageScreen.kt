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
import io.github.suqi8.coui.kmp.icon.extended.ContactsCircle
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Folder
import io.github.suqi8.coui.kmp.icon.extended.Location
import io.github.suqi8.coui.kmp.icon.extended.Recent
import io.github.suqi8.coui.kmp.icon.extended.Reply
import io.github.suqi8.coui.kmp.icon.extended.Settings
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

private val EntryTitles = listOf("@我的动态", "@我的评论", "我收到的赞", "好友关注", "私信与信息")

/** The entries the 我的 page lists, in [EntryTitles] order; 酷安小秘书 opens from inside 私信与信息. */
private val EntryTypes = listOf(
    NotificationType.At,
    NotificationType.Comment,
    NotificationType.Like,
    NotificationType.Follow,
    NotificationType.Message,
)

/**
 * 我的: the account header, its counters and shortcuts, and the notification entries. Conversations
 * live behind 私信与信息; switching or adding accounts happens on 账号管理.
 */
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
    // 酷安小秘书's own unread count; 私信与信息 shows it on its leading row.
    var systemCount by remember { mutableStateOf<Int?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun reload() {
        if (!app.session.isLogin) return
        try {
            val user = app.repository.profile(app.session.uid)
            if (user.uid.isNotEmpty() && user.uid != app.session.uid) return
            profile = user
            app.accounts.refreshCurrent(
                username = null,
                avatar = user.userAvatar,
                level = user.int("level"),
                exp = user.int("experience"),
                nextExp = user.int("nextLevelExperience"),
            )
            val count = app.repository.checkCount()
            val message = count.int("message")
            val notification = count.int("notification")
            systemCount = notification
            counts = listOf(
                count.int("atme"),
                count.int("atcommentme"),
                count.int("feedlike"),
                count.int("contacts_follow"),
                if (message != null || notification != null) (message ?: 0) + (notification ?: 0) else null,
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
            systemCount = null
        }
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                MessageHeader(
                    user = profile,
                    isLogin = isLogin,
                    onLogin = { navigator.push(Route.Login) },
                )
            }
        },
    ) { innerPadding ->
        MessageList(
            backdrop = backdrop,
            isLogin = isLogin,
            profile = profile,
            counts = counts,
            refreshing = refreshing,
            onOpen = { index ->
                val type = EntryTypes[index]
                navigator.push(
                    Route.Notification(type, systemCount = if (type == NotificationType.Message) systemCount ?: 0 else 0),
                )
                if (index < counts.size) counts = counts.toMutableList().also { it[index] = null }
                if (type == NotificationType.Message) systemCount = null
            },
            onRefresh = {
                scope.launch {
                    refreshing = true
                    reload()
                    refreshing = false
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
}

@Composable
private fun MessageHeader(
    user: Entity?,
    isLogin: Boolean,
    onLogin: () -> Unit,
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
            .padding(bottom = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的", modifier = Modifier.weight(1f), style = COUITheme.textStyles.title3)
            IconButton(onClick = { navigator.push(Route.Accounts) }) {
                Icon(COUIIcons.ContactsCircle, contentDescription = "账号管理")
            }
            IconButton(onClick = { navigator.push(Route.Settings) }) {
                Icon(COUIIcons.Settings, contentDescription = "设置")
            }
        }
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
                    Spacer(Modifier.width(20.dp))
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun MessageList(
    backdrop: LayerBackdrop?,
    isLogin: Boolean,
    profile: Entity?,
    counts: List<Int?>,
    refreshing: Boolean,
    onOpen: (Int) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    val navigator = LocalNavigator.current

    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        pullToRefreshState = rememberPullToRefreshState(),
        contentPadding = contentPadding,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .overScrollVertical(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // A guest has no counters to show, so the page starts at the shortcuts.
            if (isLogin) {
                item(contentType = "stats") {
                    CellCard(
                        values = listOf(profile?.int("feed"), profile?.int("follow"), profile?.int("fans")),
                        titles = listOf("动态", "关注", "粉丝"),
                        icons = null,
                        onClicks = listOf(
                            { navigator.push(Route.FFFList(FFFListType.Feed)) },
                            { navigator.push(Route.FFFList(FFFListType.Follow)) },
                            { navigator.push(Route.FFFList(FFFListType.Fan)) },
                        ),
                    )
                }
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
            items(count = EntryTypes.size) { index ->
                NotificationEntry(
                    color = EntryColors[index],
                    icon = EntryIcons[index],
                    title = EntryTitles[index],
                    badge = counts.getOrNull(index),
                    onClick = { if (isLogin) onOpen(index) },
                )
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
