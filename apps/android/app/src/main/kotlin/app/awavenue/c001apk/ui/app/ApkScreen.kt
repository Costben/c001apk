package app.awavenue.c001apk.ui.app

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.network.ApiFailureKind
import app.awavenue.c001apk.platform.InstalledApp
import app.awavenue.c001apk.platform.installedApp
import app.awavenue.c001apk.platform.rememberApkDownload
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppFloatingActionButton
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.PinnedHeader
import app.awavenue.c001apk.ui.common.appTabRowColors
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.shareText
import app.awavenue.c001apk.ui.common.toImageBitmap
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.EntityItem
import app.awavenue.c001apk.ui.feed.FeedActions
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.image.imageOrigin
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppCommentTab(val label: String) {
    Reply("最近回复"),
    Dateline("最新发布"),
    Hot("热度排序"),
    ;

    fun url(id: String): String = when (this) {
        Reply -> "/page?url=/feed/apkCommentList?id=$id"
        Dateline -> "/page?url=/feed/apkCommentList?id=$id&sort=dateline_desc"
        Hot -> "/page?url=/feed/apkCommentList?id=$id&sort=popular"
    }
}

@Composable
fun ApkScreen(packageName: String) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberGlassBackdrop()
    val scrollBehavior = rememberPageScrollBehavior("apk")
    val listState = rememberPageListState("apk")
    val download = rememberApkDownload()

    var detail by remember(packageName) { mutableStateOf<Entity?>(null) }
    var failure by remember(packageName) { mutableStateOf<String?>(null) }
    var failureKind by remember(packageName) { mutableStateOf<ApiFailureKind?>(null) }
    var localApp by remember(packageName) { mutableStateOf<InstalledApp?>(null) }
    var attempts by remember(packageName) { mutableStateOf(0) }
    var tab by remember(packageName) { mutableStateOf(AppCommentTab.Reply) }
    var menu by remember { mutableStateOf(false) }
    var compose by remember { mutableStateOf(false) }
    var downloadUrl by remember(packageName) { mutableStateOf<String?>(null) }

    LaunchedEffect(packageName) {
        localApp = withContext(Dispatchers.IO) { installedApp(context, packageName) }
    }

    LaunchedEffect(packageName, attempts) {
        failure = null
        failureKind = null
        try {
            detail = app.repository.appDetail(packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val apiFailure = ApiFailure.from(e)
            failure = apiFailure.message
            failureKind = apiFailure.kind
        }
    }

    val id = detail?.id.orEmpty()
    val appName = detail?.title.orEmpty()
    val commentStatus = detail?.int("commentStatus") ?: 0
    val commentStatusText = detail?.string("commentStatusText").orEmpty()
    var isFollow by remember(detail) { mutableStateOf(detail?.userAction?.string("follow") == "1") }
    var isBlocked by remember(detail) {
        mutableStateOf(detail != null && app.blacklist.topicBlacklist.any { appName.contains(it) })
    }
    val commentsOpen = !isBlocked && commentStatus == 1

    val listViewModel = if (commentsOpen) {
        viewModel(key = "apk-comments-$id-${tab.name}") {
            FeedListViewModel(
                PageLoader { page, first, last -> app.repository.dataList(tab.url(id), tab.label, page, first, last) },
            )
        }
    } else {
        null
    }

    LaunchedEffect(listState, listViewModel) {
        val current = listViewModel ?: return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && last >= layout.totalItemsCount - 3
        }.distinctUntilChanged().filter { it }.collect { current.loadMore() }
    }

    val scrolledPastHeader by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 50 }
    }

    fun onFollowClick() {
        if (!app.session.isLogin) return
        val followed = isFollow
        scope.launch {
            try {
                val result = app.repository.followApk(id, followed)
                context.toast(result.message.ifEmpty { if (followed) "取消关注成功" else "关注成功" })
                isFollow = !followed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toast(ApiFailure.from(e).message)
            }
        }
    }

    fun onDownloadClick() {
        val versionName = detail?.string("apkversionname").orEmpty()
        val versionCode = detail?.string("apkversioncode").orEmpty()
        scope.launch {
            val url = downloadUrl ?: app.repository.appDownloadUrl(
                packageName = packageName,
                id = id.toIntOrNull() ?: 0,
                versionCode = versionCode.toIntOrNull() ?: 0,
            )
            if (url.isNullOrEmpty()) {
                context.toast("无法获取下载链接，请稍后重试")
                return@launch
            }
            downloadUrl = url
            download(url, "$appName-$versionName-$versionCode.apk")
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = if (scrolledPastHeader) appName else "",
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = rememberHapticClick { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (detail != null) {
                            if (commentsOpen) {
                                TopBarIconButton(
                                    onClick = rememberHapticClick {
                                        navigator.push(
                                            Route.Search(title = appName, pageType = "apk", pageParam = id),
                                        )
                                    },
                                ) {
                                    Icon(COUIIcons.Search, contentDescription = "搜索")
                                }
                            }
                            Box {
                                TopBarIconButton(onClick = rememberHapticClick { menu = true }) {
                                    Icon(COUIIcons.More, contentDescription = "菜单")
                                }
                                OverlayListPopup(
                                    show = menu,
                                    alignment = PopupPositionProvider.Align.BottomEnd,
                                    onDismissRequest = { menu = false },
                                ) {
                                    ListPopupColumn {
                                        val entries = buildList {
                                            add("复制" to { context.copyText(Protocol.shareLink("apk", id)) })
                                            add("分享" to { context.shareText(Protocol.shareLink("apk", id)) })
                                            add((if (isFollow) "取消关注" else "关注") to { onFollowClick() })
                                            add(
                                                (if (isBlocked) "取消屏蔽" else "屏蔽") to {
                                                    if (isBlocked) {
                                                        app.blacklist.unblockTopic(appName)
                                                    } else {
                                                        app.blacklist.blockTopic(appName)
                                                    }
                                                    isBlocked = !isBlocked
                                                },
                                            )
                                        }
                                        entries.forEachIndexed { index, entry ->
                                            DropdownImpl(
                                                item = DropdownItem(text = entry.first),
                                                optionSize = entries.size,
                                                isSelected = false,
                                                index = index,
                                                groupKey = "apk-menu",
                                                onSelectedIndexChange = {
                                                    menu = false
                                                    entry.second()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            AppFloatingActionButton(
                visible = app.session.isLogin && detail != null && commentsOpen,
                backdrop = backdrop,
                onClick = rememberHapticClick { compose = true },
                icon = COUIIcons.Add,
                contentDescription = "发布动态",
            )
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize().glassSource(backdrop)) {
                val current = detail
                if (current == null) {
                    Box(
                        Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        val local = localApp
                        when {
                            failure == null -> InfiniteProgressIndicator()
                            local != null -> LocalApkFallback(
                                app = local,
                                notListed = failureKind == ApiFailureKind.Business,
                                message = failure.orEmpty(),
                                onRetry = { attempts++ },
                            )
                            else -> EmptyState(
                                title = "加载失败",
                                subtitle = failure,
                                actionText = "重试",
                                onActionClick = { attempts++ },
                            )
                        }
                    }
                    return@Box
                }

                AppPullToRefresh(
                    isRefreshing = listViewModel?.isRefreshing == true,
                    onRefresh = { if (listViewModel != null) listViewModel.refresh() else attempts++ },
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(contentType = "header") {
                            ApkHeader(
                                app = current,
                                onOpenIcon = rememberHapticClick {
                                    navigator.push(Route.ImageView(listOf(current.logo)))
                                },
                                onDownload = rememberHapticClick { onDownloadClick() },
                            )
                        }
                        item(key = "comments", contentType = "comments") {
                            CommentHeader(
                                commentsOpen = commentsOpen,
                                tab = tab,
                                onTabSelected = { tab = it },
                            )
                        }
                        when {
                            isBlocked -> item(contentType = "state") {
                                StateBox { Text("已屏蔽 $appName") }
                            }
                            commentStatus != 1 -> item(contentType = "state") {
                                StateBox { Text(commentStatusText) }
                            }
                            listViewModel != null -> when (val status = listViewModel.status) {
                                ListStatus.Loading -> item(contentType = "state") {
                                    StateBox { InfiniteProgressIndicator() }
                                }
                                ListStatus.Empty -> item(contentType = "state") {
                                    StateBox {
                                        EmptyState(title = "暂无内容", actionText = "刷新", onActionClick = listViewModel::refresh)
                                    }
                                }
                                is ListStatus.Error -> item(contentType = "state") {
                                    StateBox {
                                        EmptyState(
                                            title = "加载失败",
                                            subtitle = status.message,
                                            actionText = "重试",
                                            onActionClick = listViewModel::refresh,
                                        )
                                    }
                                }
                                ListStatus.Content -> {
                                    itemsIndexed(
                                        listViewModel.items.filterNot(FeedActions::isHidden),
                                        key = { index, item -> item.entityId.ifEmpty { "comment-$index" } },
                                        contentType = { _, item -> item.entityTemplate.ifEmpty { item.entityType } },
                                    ) { _, item -> EntityItem(item) }
                                    item(contentType = "footer") { CommentFooter(listViewModel) }
                                }
                            }
                        }
                    }
                }
                PinnedHeader(
                    listState = listState,
                    headerIndex = 1,
                    topInset = innerPadding.calculateTopPadding(),
                ) {
                    CommentHeader(commentsOpen = commentsOpen, tab = tab, onTabSelected = { tab = it })
                }
            }
        }
    }

    if (compose) {
        ReplyComposerSheet(
            target = ReplyTarget(
                targetType = "apk",
                targetId = ((id.toIntOrNull() ?: 4599) + 1000000000).toString(),
                title = appName,
            ),
            onDismiss = { compose = false },
            onPosted = {
                compose = false
                listViewModel?.refresh()
            },
        )
    }
}

/** The divider-and-tabs header above the comment list; drawn in the list and again pinned under the bar. */
@Composable
private fun CommentHeader(
    commentsOpen: Boolean,
    tab: AppCommentTab,
    onTabSelected: (AppCommentTab) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(COUITheme.colorScheme.surface)) {
        HorizontalDivider()
        if (commentsOpen) {
            TabRowWithContour(
                tabs = AppCommentTab.entries.map { it.label },
                selectedTabIndex = tab.ordinal,
                onTabSelected = { onTabSelected(AppCommentTab.entries[it]) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                colors = appTabRowColors(),
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun LocalApkFallback(
    app: InstalledApp,
    notListed: Boolean,
    message: String,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val icon = remember(app) { app.icon?.toImageBitmap(240) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.appName,
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(18.dp)),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        style = COUITheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        app.packageName,
                        style = COUITheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "版本: ${app.versionName}(${app.versionCode})",
                        style = COUITheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "大小: ${Formatter.formatShortFileSize(context, app.apkSize)}",
                        style = COUITheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "更新时间: ${if (app.lastUpdateTime > 0) fromToday(app.lastUpdateTime / 1000) else "未知"}",
                        style = COUITheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            if (notListed) "酷安未收录该应用" else "无法获取酷安详情",
            style = COUITheme.textStyles.body1,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (notListed) "已安装的 ${app.appName} 在酷安没有对应页面。" else message,
            style = COUITheme.textStyles.body2,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}

@Composable
private fun ApkHeader(app: Entity, onOpenIcon: () -> Unit, onDownload: () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                app.logo,
                Modifier
                    .size(80.dp)
                    .imageOrigin(app.logo, 18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenIcon),
                contentDescription = app.title,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.title,
                    style = COUITheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "版本: ${app.string("apkversionname")}(${app.string("apkversioncode")})",
                    style = COUITheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "大小: ${app.string("apksize")}",
                            style = COUITheme.textStyles.body2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "更新时间: ${app.long("lastupdate")?.let { fromToday(it) } ?: "未知"}",
                            style = COUITheme.textStyles.body2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (app.entityType == "apk") {
                        TextButton(text = "下载", onClick = onDownload)
                    }
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
private fun CommentFooter(viewModel: FeedListViewModel) {
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
