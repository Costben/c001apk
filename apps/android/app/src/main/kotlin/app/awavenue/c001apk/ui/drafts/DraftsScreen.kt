package app.awavenue.c001apk.ui.drafts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.data.Draft
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.appDialogButtonColors
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.nav.LocalAppBarBounds
import app.awavenue.c001apk.ui.nav.LocalFeedListState
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import coil3.compose.AsyncImage
import io.github.suqi8.coui.kmp.basic.Card
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
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import java.io.File

/**
 * The draft box: every composition that was closed half-written, newest first. A row reopens the
 * composer it came from — same target, same text, same pictures — so publishing continues where
 * it stopped.
 */
@OptIn(ExperimentalCOUIApi::class)
@Composable
fun DraftsScreen() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberPageScrollBehavior("drafts")
    val backdrop = rememberGlassBackdrop()
    val appBarBounds = LocalAppBarBounds.current
    val listState = rememberPageListState("drafts")
    var editing by remember { mutableStateOf<Draft?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val drafts = app.drafts.drafts

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = "草稿箱",
                    modifier = Modifier.onGloballyPositioned {
                        appBarBounds.reportPage(
                            Route.Drafts,
                            it.positionInRoot().y + it.size.height,
                        )
                    },
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (drafts.isNotEmpty()) {
                            TopBarIconButton(onClick = { confirmClear = true }) {
                                Icon(COUIIcons.Delete, contentDescription = "清除全部")
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalFeedListState provides listState,
        ) {
            if (drafts.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding(),
                        )
                        .glassSource(backdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无草稿", color = COUITheme.colorScheme.onSurfaceVariantSummary)
                }
            } else {
                LazyColumn(
                    state = listState,
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
                    items(drafts, key = { it.id }) { draft ->
                        DraftCard(
                            draft = draft,
                            onOpen = { editing = draft },
                            onDelete = { app.drafts.remove(draft.id) },
                        )
                    }
                }
            }
        }
    }

    // The target comes with the draft, so the sheet is opened for a placeholder one.
    editing?.let { draft ->
        ReplyComposerSheet(
            target = ReplyTarget(),
            draft = draft,
            onDismiss = { editing = null },
            onPosted = { editing = null },
        )
    }

    if (confirmClear) {
        OverlayAlertDialog(
            buttonColors = appDialogButtonColors(),
            show = true,
            title = "确定清除全部草稿？",
            confirmText = "确定",
            cancelText = "取消",
            onConfirm = {
                confirmClear = false
                app.drafts.clear()
            },
            onCancel = { confirmClear = false },
        )
    }
}

@Composable
private fun DraftCard(draft: Draft, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = rememberHapticClick(onClick = onOpen),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                draft.label,
                modifier = Modifier.weight(1f),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                fromToday(draft.updatedAt / 1000),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(
                    COUIIcons.Delete,
                    contentDescription = "删除草稿",
                    modifier = Modifier.size(16.dp),
                    tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            draft.preview,
            style = COUITheme.textStyles.body2,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (draft.images.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                draft.images.forEach { image ->
                    AsyncImage(
                        model = File(image.path),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }
}

/** The 我的 page preview: one draft, as a row of the card that lists the newest ones. */
@Composable
fun DraftPreviewRow(draft: Draft, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = rememberHapticClick(onClick = onClick))
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            COUIIcons.Notes,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(draft.preview, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                draft.label,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            fromToday(draft.updatedAt / 1000),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}
