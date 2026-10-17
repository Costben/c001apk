package app.awavenue.c001apk.ui.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.shareText
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.EntityItem
import app.awavenue.c001apk.ui.feed.FeedActions
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.FFFListType
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class UserMenuItem(val label: String) {
    Copy("复制"),
    Share("分享"),
    Block("屏蔽"),
    Unblock("取消屏蔽"),
    Report("举报"),
    Info("用户信息"),
}

@Composable
fun UserScreen(uid: String) {
    val app = LocalApp.current
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = COUIScrollBehavior()
    val backdrop = rememberGlassBackdrop()
    val listState = rememberLazyListState()

    var user by remember(uid) { mutableStateOf<Entity?>(null) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var loading by remember(uid) { mutableStateOf(true) }
    var blocked by remember(uid) { mutableStateOf(false) }
    var resolvedUid by remember(uid) { mutableStateOf(uid) }
    var username by remember(uid) { mutableStateOf("") }
    var menuOpen by remember(uid) { mutableStateOf(false) }
    var infoOpen by remember(uid) { mutableStateOf(false) }

    val loadUser: suspend () -> Unit = {
        loading = true
        error = null
        runCatching { app.repository.userSpace(uid) }
            .onSuccess { data ->
                resolvedUid = data.uid.ifEmpty { uid }
                username = data.username
                blocked = app.blacklist.isUserBlocked(resolvedUid)
                user = data
            }
            .onFailure { error = ApiFailure.from(it).message }
        loading = false
    }

    LaunchedEffect(uid) { loadUser() }

    val hasFeed = user != null && !blocked
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 105 }
    }
    val opaque = collapsed || !hasFeed

    var followBusy by remember(uid) { mutableStateOf(false) }

    val onLocalFollow: () -> Unit = onLocalFollow@{
        val data = user ?: return@onLocalFollow
        if (followBusy || (app.session.isLogin && !app.localFollow.secretEnabled)) return@onLocalFollow
        val following = app.localFollow.contains(resolvedUid)
        val label = if (app.session.isLogin) "悄悄关注" else "本地关注"
        runCatching {
            if (following) {
                app.localFollow.unfollow(resolvedUid)
            } else {
                app.localFollow.follow(resolvedUid, data.username, data.userAvatar)
            }
        }.onSuccess {
            context.toast(if (following) "已取消$label，仅修改本机名单" else "已$label，仅保存在本机，不会同步到账号")
        }.onFailure {
            context.toast("本地关注保存失败，请重试")
        }
    }

    val onFollow: () -> Unit = onFollow@{
        if (!app.session.isLogin) {
            onLocalFollow()
            return@onFollow
        }
        if (followBusy) return@onFollow
        followBusy = true
        val isFollow = user?.string("isFollow") == "1"
        scope.launch {
            runCatching { app.repository.followUser(resolvedUid, isFollow) }
                .onSuccess { result ->
                    val current = user
                    when {
                        result.message.isNotEmpty() -> context.toast(result.message)
                        result.data != null && current != null -> {
                            val value = result.count.ifEmpty { if (isFollow) "0" else "1" }
                            user = Entity(JsonObject(current.json + ("isFollow" to JsonPrimitive(value))))
                            context.toast(if (isFollow) "取消关注成功" else "关注成功")
                        }
                    }
                }
                .onFailure { context.toast(ApiFailure.from(it).message) }
            followBusy = false
        }
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = if (collapsed) username else "",
                    color = if (opaque) COUITheme.colorScheme.surface else Color.Transparent,
                    dividerColor = if (opaque) COUITheme.colorScheme.dividerLine else Color.Transparent,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (user != null) {
                            if (!blocked) {
                                IconButton(
                                    onClick = {
                                        navigator.push(
                                            Route.Search(title = username, pageType = "user", pageParam = resolvedUid),
                                        )
                                    },
                                ) {
                                    Icon(COUIIcons.Search, contentDescription = "搜索")
                                }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(COUIIcons.More, contentDescription = "更多")
                                }
                                UserMenu(
                                    show = menuOpen,
                                    blocked = blocked,
                                    onDismiss = { menuOpen = false },
                                    onSelect = { item ->
                                        menuOpen = false
                                        when (item) {
                                            UserMenuItem.Copy -> context.copyText(Protocol.shareLink("u", resolvedUid))
                                            UserMenuItem.Share -> context.shareText(Protocol.shareLink("u", resolvedUid))
                                            UserMenuItem.Block -> {
                                                app.blacklist.blockUser(resolvedUid)
                                                blocked = true
                                            }
                                            UserMenuItem.Unblock -> {
                                                app.blacklist.unblockUser(resolvedUid)
                                                blocked = false
                                            }
                                            UserMenuItem.Report ->
                                                navigator.push(Route.Web(Protocol.reportLink(resolvedUid, isUser = true)))
                                            UserMenuItem.Info -> infoOpen = true
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 10.dp)
        Box(Modifier.fillMaxSize().glassSource(backdrop)) {
            when {
                loading -> CenterState(innerPadding) { InfiniteProgressIndicator() }
                user == null -> CenterState(innerPadding) {
                    TextButton(text = error ?: "暂无内容", onClick = { scope.launch { loadUser() } })
                }
                blocked -> CenterState(innerPadding) {
                    Text("已屏蔽用户 $username", color = COUITheme.colorScheme.onSurfaceVariantSummary)
                }
                else -> {
                    val feed = viewModel(key = "user-feed-$resolvedUid") {
                        FeedListViewModel(
                            PageLoader { page, first, last -> app.repository.userFeed(resolvedUid, page, first, last) },
                        )
                    }
                    UserFeed(
                        viewModel = feed,
                        listState = listState,
                        scrollBehavior = scrollBehavior,
                        contentPadding = contentPadding,
                        onReloadUser = { scope.launch { loadUser() } },
                        header = {
                            user?.let { data ->
                                UserInfoCard(
                                    user = data,
                                    onFollowClick = onFollow,
                                    onLocalFollow = onLocalFollow,
                                    onStatClick = { index ->
                                        val target = when (index) {
                                            UserStat.Feed -> FFFListType.Feed
                                            UserStat.Like -> FFFListType.Like
                                            UserStat.Follow -> FFFListType.UserFollow
                                            else -> FFFListType.Fan
                                        }
                                        navigator.push(Route.FFFList(target, uid = resolvedUid))
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    user?.takeIf { infoOpen }?.let { data ->
        OverlayDialog(
            show = true,
            title = data.username,
            onDismissRequest = { infoOpen = false },
            content = {
                Text(
                    userDetails(data),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            },
        )
    }
}

@Composable
private fun UserMenu(
    show: Boolean,
    blocked: Boolean,
    onDismiss: () -> Unit,
    onSelect: (UserMenuItem) -> Unit,
) {
    val items = UserMenuItem.entries.map { if (it == UserMenuItem.Block && blocked) UserMenuItem.Unblock else it }
    OverlayListPopup(
        show = show,
        onDismissRequest = onDismiss,
        alignment = PopupPositionProvider.Align.End,
    ) {
        ListPopupColumn {
            items.forEachIndexed { index, item ->
                DropdownImpl(
                    item = DropdownItem(text = item.label),
                    optionSize = items.size,
                    isSelected = false,
                    index = index,
                    groupKey = "user-menu",
                    role = Role.Button,
                    onSelectedIndexChange = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun UserFeed(
    viewModel: FeedListViewModel,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    onReloadUser: () -> Unit,
    header: @Composable () -> Unit,
) {
    val context = LocalContext.current

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
        onRefresh = {
            onReloadUser()
            viewModel.refresh()
        },
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
            item(key = "header", contentType = "header") { header() }
            when (val status = viewModel.status) {
                ListStatus.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        InfiniteProgressIndicator()
                    }
                }
                ListStatus.Empty -> item(key = "empty") {
                    FeedMessage("暂无内容") { viewModel.refresh() }
                }
                is ListStatus.Error -> item(key = "error") {
                    FeedMessage(status.message) { viewModel.refresh() }
                }
                ListStatus.Content -> {
                    itemsIndexed(
                        viewModel.items.filterNot(FeedActions::isHidden),
                        contentType = { _, item -> item.entityTemplate.ifEmpty { item.entityType } },
                    ) { _, item ->
                        EntityItem(item)
                    }
                    item(key = "footer", contentType = "footer") { FeedFooter(viewModel) }
                }
            }
        }
    }
}

@Composable
private fun FeedMessage(message: String, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 24.dp).clickable(onClick = onRetry),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = COUITheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun FeedFooter(viewModel: FeedListViewModel) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (val status = viewModel.footer) {
            FooterStatus.Idle, FooterStatus.Loading -> InfiniteProgressIndicator()
            FooterStatus.End -> Text(
                "没有更多了",
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            is FooterStatus.Error -> TextButton(text = "${status.message}，点击重试", onClick = viewModel::retryFooter)
        }
    }
}

@Composable
private fun CenterState(innerPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding()),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun userDetails(user: Entity): String {
    val gender = when (user.int("gender")) {
        1 -> "男"
        0 -> "女"
        else -> "未知"
    }
    val regdate = user.long("regdate") ?: 0L
    val nowSeconds = System.currentTimeMillis() / 1000
    val days = if (regdate == 0L) 0L else (nowSeconds - regdate) / 24 / 3600
    val registered = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA).format(Date(regdate * 1000))
    return """
        |
        |uid: ${user.uid}
        |
        |等级: Lv.${user.string("level")}
        |
        |性别: $gender
        |
        |注册时长: $days 天
        |
        |注册时间: $registered
        |
    """.trimMargin()
}
