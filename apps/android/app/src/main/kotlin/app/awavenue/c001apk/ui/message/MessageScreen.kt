package app.awavenue.c001apk.ui.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.HistoryCardMode
import app.awavenue.c001apk.data.HistoryType
import app.awavenue.c001apk.data.asEntity
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.NotificationBell
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.drafts.DraftPreviewRow
import app.awavenue.c001apk.ui.follow.localFollowLabel
import app.awavenue.c001apk.ui.nav.ExpandOrigin
import app.awavenue.c001apk.ui.nav.FFFListType
import app.awavenue.c001apk.ui.nav.LocalAppBarBounds
import app.awavenue.c001apk.ui.nav.LocalFeedListState
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.expandOrigin
import app.awavenue.c001apk.ui.nav.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.glassHeader
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.VerticalDivider
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.ContactsCircle
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Folder
import io.github.suqi8.coui.kmp.icon.extended.Reply
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 我的: the account header, its counters and shortcuts, and preview cards for the lists kept
 * around it. Every card opens its full list; nothing expands or collapses in place.
 */
@Composable
fun MessageScreen(outerPadding: PaddingValues, barSlot: Int) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberGlassBackdrop()
    val appBarBounds = LocalAppBarBounds.current

    val isLogin = app.session.isLogin
    var profile by remember { mutableStateOf<Entity?>(null) }
    // Null until the first request lands, so a card does not flash its empty state.
    var recent by remember { mutableStateOf<List<Entity>?>(null) }
    var follows by remember { mutableStateOf<List<Entity>?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun loadPreview(url: String): List<Entity> = try {
        app.repository.userList(url, app.session.uid, null, 1, null, null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

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
                nextExp = user.int("next_level_experience"),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.toast(ApiFailure.from(e).message)
        }
        recent = loadPreview("/v6/user/recentHistoryList")
        follows = loadPreview("/v6/user/followList")
    }

    LaunchedEffect(isLogin) {
        if (isLogin) {
            reload()
        } else {
            profile = null
            recent = null
            follows = null
        }
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                Box(
                    Modifier.onGloballyPositioned {
                        appBarBounds.report(barSlot, it.positionInRoot().y + it.size.height.toFloat())
                    },
                ) {
                    MessageHeader(
                        user = profile,
                        isLogin = isLogin,
                        onLogin = { navigator.push(Route.Login) },
                    )
                }
            }
        },
    ) { innerPadding ->
        MessageList(
            backdrop = backdrop,
            isLogin = isLogin,
            profile = profile,
            recent = recent,
            follows = follows,
            refreshing = refreshing,
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
    val nextExp = user?.int("next_level_experience") ?: app.session.nextExp
    val username = user?.username?.ifEmpty { app.session.username } ?: app.session.username

    Column(
        Modifier
            .fillMaxWidth()
            .glassHeader(
                backdrop = backdrop,
                containerColor = COUITheme.colorScheme.surface,
                fadeHeight = 18.dp,
            )
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .padding(bottom = 18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的", modifier = Modifier.weight(1f), style = COUITheme.textStyles.title3)
            TopBarIconButton(onClick = rememberHapticClick { navigator.push(Route.Accounts) }) {
                Icon(COUIIcons.ContactsCircle, contentDescription = "账号管理")
            }
            TopBarIconButton(
                onClick = rememberHapticClick { navigator.push(Route.Settings) },
                modifier = Modifier.padding(start = 10.dp),
            ) {
                Icon(COUIIcons.Settings, contentDescription = "设置")
            }
            NotificationBell(Modifier.padding(start = 10.dp))
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!isLogin) {
                TextButton(text = "点击登录", onClick = rememberHapticClick(onClick = onLogin))
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
                            .clickable(
                                onClick = rememberHapticClick {
                                    val uid = user?.uid.orEmpty().ifEmpty { app.session.uid }
                                    if (uid.isNotEmpty()) navigator.push(Route.User(uid))
                                },
                            ),
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
    }
}

@Composable
private fun MessageList(
    backdrop: LayerBackdrop?,
    isLogin: Boolean,
    profile: Entity?,
    recent: List<Entity>?,
    follows: List<Entity>?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val listState = rememberLazyListState()
    // Parse and shorten once per history change, not every time the card scrolls back in.
    val history = remember(app.history.historyFeeds) {
        app.history.list(HistoryType.History).map { item ->
            val text = plainText(item.message)
            val snippet = when {
                text.length > 80 -> text.take(80) + "…"
                item.message.length >= 150 -> "$text…"
                else -> text
            }
            HistoryPreview(
                id = item.id,
                avatar = item.userAvatar,
                title = if (item.username.isEmpty()) "动态" else "${item.username}的动态",
                snippet = snippet,
                time = item.time,
                feed = item.asEntity(),
            )
        }
    }
    val localFollows = app.localFollow.users
    val drafts = app.drafts.drafts
    val historyRows = if (app.settings.historyCardMode == HistoryCardMode.Full) history else history.take(5)

    AppPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        contentPadding = contentPadding,
    ) {
        CompositionLocalProvider(LocalFeedListState provides listState) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .glassSource(backdrop)
                    .overScrollVertical(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // A guest has no counters to show, so the page starts at the shortcuts.
                if (isLogin) {
                    item(key = "stats", contentType = "stats") {
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
                item(key = "shortcuts", contentType = "shortcuts") {
                    CellCard(
                        values = null,
                        titles = listOf("本地收藏", "我的收藏", "我的赞", "我的回复"),
                        icons = listOf(COUIIcons.Folder, COUIIcons.Favorites, AppIcons.ThumbUpOutlined, COUIIcons.Reply),
                        onClicks = listOf(
                            { navigator.push(Route.History(HistoryType.Fav)) },
                            { if (isLogin) navigator.push(Route.FFFList(FFFListType.Collection)) },
                            { if (isLogin) navigator.push(Route.FFFList(FFFListType.Like)) },
                            { if (isLogin) navigator.push(Route.FFFList(FFFListType.Reply)) },
                        ),
                    )
                }
                item(key = "history", contentType = "history") {
                    PreviewCard(
                        title = "浏览历史",
                        onClick = { navigator.push(Route.History(HistoryType.History)) },
                    ) {
                        if (historyRows.isEmpty()) {
                            PreviewEmpty("暂无浏览记录")
                        } else {
                            historyRows.forEach { item -> HistoryPreviewRow(item) }
                        }
                    }
                }
                item(key = "drafts", contentType = "drafts") {
                    PreviewCard(
                        title = "草稿箱",
                        onClick = { navigator.push(Route.Drafts) },
                    ) {
                        if (drafts.isEmpty()) {
                            PreviewEmpty("暂无草稿")
                        } else {
                            drafts.take(5).forEach { draft ->
                                DraftPreviewRow(
                                    draft = draft,
                                    onClick = { navigator.push(Route.Drafts) },
                                )
                            }
                        }
                    }
                }
                if (isLogin) {
                    item(key = "recent", contentType = "recent") {
                        PreviewCard(
                            title = "我的常去",
                            onClick = { navigator.push(Route.FFFList(FFFListType.Recent)) },
                        ) {
                            when {
                                recent == null -> Unit
                                recent.isEmpty() -> PreviewEmpty("暂无最近访问")
                                else -> recent.take(5).forEach { item -> RecentRow(item) }
                            }
                        }
                    }
                    item(key = "follow", contentType = "follow") {
                        PreviewCard(
                            title = "我的关注",
                            onClick = { navigator.push(Route.FFFList(FFFListType.Follow)) },
                        ) {
                            when {
                                follows == null -> Unit
                                follows.isEmpty() -> PreviewEmpty("还没有关注任何人")
                                else -> follows.take(5).forEach { item -> FollowPreviewRow(item) }
                            }
                        }
                    }
                }
                item(key = "local-follow", contentType = "local-follow") {
                    PreviewCard(
                        title = localFollowLabel(),
                        onClick = { navigator.push(Route.LocalFollowList) },
                    ) {
                        if (localFollows.isEmpty()) {
                            PreviewEmpty("暂无${localFollowLabel()}")
                        } else {
                            localFollows.take(5).forEach { user ->
                                PreviewRow(
                                    avatar = user.avatar,
                                    title = user.name,
                                    subtitle = localFollowLabel(),
                                    circle = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One preview card: the title row opens the full list, and a few rows sit underneath. */
@Composable
private fun PreviewCard(
    title: String,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        onClick = rememberHapticClick(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    COUIIcons.ChevronForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            content()
        }
    }
}

/** A row of a [PreviewCard]: avatar, one-line title, optional meta line, optional trailing time. */
@Composable
private fun PreviewRow(
    avatar: String,
    title: String,
    subtitle: String,
    trailing: String? = null,
    circle: Boolean = false,
    onClick: (() -> Unit)? = null,
    origin: ExpandOrigin? = null,
) {
    val click = if (onClick != null) rememberHapticClick(onClick = onClick) else null
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (origin != null) Modifier.expandOrigin(origin) else Modifier)
            .then(if (click != null) Modifier.clickable(onClick = click) else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkImage(
            avatar,
            Modifier.size(42.dp).clip(if (circle) CircleShape else RoundedCornerShape(10.dp)),
            contentDescription = title,
            crossfade = false,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!trailing.isNullOrEmpty()) {
                    Text(
                        trailing,
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private class HistoryPreview(
    val id: String,
    val avatar: String,
    val title: String,
    val snippet: String,
    val time: Long,
    val feed: Entity,
)

@Composable
private fun HistoryPreviewRow(item: HistoryPreview) {
    val navigator = LocalNavigator.current
    val listState = LocalFeedListState.current
    // The row is transparent on the card, so its frame starts in the card's colour with square
    // corners; the post then grows out of the line the user actually tapped.
    val origin = rememberExpandOrigin(0.dp, CardDefaults.defaultColors().color)
    // This row lives in a page-level list too, so a return can move it clear of the bar.
    SideEffect {
        origin.listState = listState
        origin.rowKey = item.id
    }
    PreviewRow(
        avatar = item.avatar,
        title = item.title,
        subtitle = item.snippet,
        trailing = fromToday(item.time / 1000),
        // A preview row is the post itself: opening it should not require going through the full
        // history list first. The card preview keeps the page from having to load before it can
        // draw the row it grows out of.
        onClick = { navigator.push(Route.Feed(item.id, preview = item.feed), origin) },
        origin = origin,
    )
}

/** A 常去 entry opens its target directly, as the full 我的常去 list does. */
@Composable
private fun RecentRow(item: Entity) {
    val openLink = rememberLinkOpener()
    val isUser = item.string("target_type") == "user"
    PreviewRow(
        avatar = item.logo.ifEmpty { item.pic },
        title = if (isUser) item.title else "${item.string("target_type_title")}: ${item.title}",
        subtitle = if (isUser) "${item.string("fans_num")}粉丝" else "${item.string("comment_num")}讨论",
        circle = isUser,
        onClick = { openLink(item.url, item.title) },
    )
}

/** A follow entry opens the followed account, whichever side of the contact carries it. */
@Composable
private fun FollowPreviewRow(item: Entity) {
    val navigator = LocalNavigator.current
    val user = item.obj("fUserInfo") ?: item.obj("userInfo")
    val uid = item.contactUid
    val fans = user?.string("fans").orEmpty()
    val open = if (uid.isEmpty()) null else rememberHapticClick { navigator.push(Route.User(uid)) }
    PreviewRow(
        avatar = user?.string("userAvatar").orEmpty()
            .ifEmpty { item.string("fUserAvatar") },
        title = user?.string("username").orEmpty()
            .ifEmpty { item.string("fusername") },
        subtitle = if (fans.isEmpty()) "已关注" else "${fans}粉丝",
        circle = true,
        onClick = open,
    )
}

/** A stored message is HTML; the one-line preview only has room for its text. */
private fun plainText(html: String): String =
    AnnotatedString.fromHtml(html).text.replace(Regex("\\s+"), " ").trim()

@Composable
private fun PreviewEmpty(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        textAlign = TextAlign.Center,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
    )
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
                        .clickable(onClick = rememberHapticClick(onClick = onClicks[index]))
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
                    Text(
                        titles[index],
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
