package app.awavenue.c001apk.ui.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.data.Announcement
import app.awavenue.c001apk.data.AnnouncementSync
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.MoreMenu
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.launch

/** c001apk 小助手: the public announcement list, readable without a Coolapk account. */
@Composable
fun AnnouncementInboxScreen() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = app.announcements
    val scrollBehavior = rememberPageScrollBehavior("announcements")
    val backdrop = rememberGlassBackdrop()

    val revision = service.revision
    val items = remember(revision) { service.items }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Announcement?>(null) }

    suspend fun refresh(reason: AnnouncementSync) {
        refreshing = true
        error = service.sync(reason)
        refreshing = false
    }

    LaunchedEffect(Unit) { refresh(AnnouncementSync.Inbox) }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "c001apk 小助手",
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = rememberHapticClick { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TopBarIconButton(onClick = { scope.launch { refresh(AnnouncementSync.Manual) } }) {
                            Icon(COUIIcons.Refresh, contentDescription = "刷新公告")
                        }
                        Box {
                            TopBarIconButton(onClick = { menu = true }) {
                                Icon(COUIIcons.More, contentDescription = "公告设置")
                            }
                            MoreMenu(
                                show = menu,
                                onDismissRequest = { menu = false },
                                entries = listOf(
                                    "清理公告正文缓存" to {
                                        scope.launch {
                                            service.clearBodyCache()
                                            context.toast("公告正文缓存已清理，阅读记录已保留")
                                        }
                                    },
                                ),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Column(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                if (service.isSyncing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                error?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.error,
                    )
                }
                AppPullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = { scope.launch { refresh(AnnouncementSync.Manual) } },
                    contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 10.dp),
                    topAppBarScrollBehavior = scrollBehavior,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassSource(backdrop)
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (items.isEmpty()) {
                            item(contentType = "empty") {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 80.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EmptyState(
                                        title = "暂无公告",
                                        actionText = "刷新",
                                        onActionClick = { scope.launch { refresh(AnnouncementSync.Manual) } },
                                    )
                                }
                            }
                        } else {
                            items(items, key = { it.id }) { item ->
                                AnnouncementRow(
                                    item = item,
                                    unread = !service.isRead(item),
                                    onClick = { opened = item },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    opened?.let { item ->
        AnnouncementOverlay(
            service = service,
            item = item,
            automatic = false,
            onDismiss = { opened = null },
        )
    }
}

@Composable
private fun AnnouncementRow(item: Announcement, unread: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        onClick = rememberHapticClick(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "未读",
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(COUITheme.colorScheme.primaryContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    announcementDate(item.publishedAt),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
                if (item.important) {
                    Text(
                        "重要公告",
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(COUITheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (item.summary.isNotEmpty()) {
                Text(
                    item.summary,
                    modifier = Modifier.padding(top = 8.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
