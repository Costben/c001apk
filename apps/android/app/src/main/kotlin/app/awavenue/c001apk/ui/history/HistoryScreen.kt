package app.awavenue.c001apk.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.data.FavHistoryItem
import app.awavenue.c001apk.data.HistoryType
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.feed.FeedCard
import app.awavenue.c001apk.ui.nav.LocalNavigator
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Composable
fun HistoryScreen(type: HistoryType) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val scrollBehavior = COUIScrollBehavior()
    val backdrop = rememberGlassBackdrop()
    var confirmClear by remember(type) { mutableStateOf(false) }
    val entries = app.history.list(type)

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = type.label,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (entries.isNotEmpty()) {
                            IconButton(onClick = { confirmClear = true }) {
                                Icon(COUIIcons.Delete, contentDescription = "清除全部")
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
                    .glassSource(backdrop),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无内容", color = COUITheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .glassSource(backdrop)
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 10.dp,
                    top = innerPadding.calculateTopPadding() + 10.dp,
                    end = 10.dp,
                    bottom = innerPadding.calculateBottomPadding() + 10.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { item ->
                    FeedCard(
                        feed = item.asEntity(),
                        isHistory = true,
                        onDelete = { app.history.remove(type, item.id) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ClearDialog(
            type = type,
            onConfirm = {
                confirmClear = false
                app.history.clear(type)
            },
            onCancel = { confirmClear = false },
        )
    }
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun ClearDialog(type: HistoryType, onConfirm: () -> Unit, onCancel: () -> Unit) {
    OverlayAlertDialog(
        show = true,
        title = "确定清除全部${if (type == HistoryType.Fav) "收藏" else "浏览历史"}？",
        confirmText = "确定",
        cancelText = "取消",
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

private fun FavHistoryItem.asEntity() = Entity(
    buildJsonObject {
        put("id", JsonPrimitive(id))
        put("uid", JsonPrimitive(uid))
        put("userInfo", buildJsonObject { put("username", JsonPrimitive(username)) })
        put("userAvatar", JsonPrimitive(userAvatar))
        put("message", JsonPrimitive(message))
        put("device_title", JsonPrimitive(device))
        put("dateline", JsonPrimitive(dateline))
    },
)
