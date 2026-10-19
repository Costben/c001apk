package app.awavenue.c001apk.ui.follow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.NotificationBell
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalAppBarBounds
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.common.glassSource
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ExpandMore
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme

/** The 关注 page's contents, in the order the Flutter menu lists them. */
enum class FollowSelection(val value: Int, val label: String) {
    Users(1, "用户关注"),
    Topics(2, "话题关注"),
    Products(3, "数码关注"),
    Apps(4, "应用关注"),
    All(0, "全部关注"),
    Local(5, "悄悄关注"),
    ;

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: Users
    }
}

private const val FOLLOW_BASE = "/page?url=V9_HOME_TAB_FOLLOW"

private fun followUrl(selection: FollowSelection) = when (selection) {
    FollowSelection.Users -> "$FOLLOW_BASE&type=circle"
    FollowSelection.Topics -> "$FOLLOW_BASE&type=topic"
    FollowSelection.Products -> "$FOLLOW_BASE&type=product"
    FollowSelection.Apps -> "$FOLLOW_BASE&type=apk"
    else -> FOLLOW_BASE
}

/** 悄悄关注 for a signed-in account, 本地关注 for a guest: the same list either way. */
@Composable
internal fun localFollowLabel(): String = if (LocalApp.current.session.isLogin) "悄悄关注" else "本地关注"

/**
 * The main 关注 tab. Account follows and local follows stay apart: a signed-in account picks one of
 * its follow streams or its local list from the title menu; a guest only has the local list.
 */
@Composable
fun FollowScreen(outerPadding: PaddingValues, returnTop: ReturnTopController, barSlot: Int) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val isLogin = app.session.isLogin
    val selection = FollowSelection.of(app.settings.followSelection)
    val showLocal = !isLogin || selection == FollowSelection.Local
    var menu by remember { mutableStateOf(false) }

    // The bar stays put, so the lists must not hand it any scroll.
    val scrollBehavior = COUIScrollBehavior(state = rememberTopAppBarState(initialHeightOffsetLimit = 0f))
    val backdrop = rememberGlassBackdrop()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val appBarBounds = LocalAppBarBounds.current
    val materialScrollFraction = remember(scrollBehavior, density) {
        {
            AppBarBlurOverlayDefaults.scrollAlpha(abs(scrollBehavior.state.contentOffset), density)
        }
    }

    Scaffold(
        topBar = {
            AppBarBlurOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        appBarBounds.report(barSlot, it.positionInRoot().y + it.size.height.toFloat())
                    },
                containerColor = COUITheme.colorScheme.surface,
                backdrop = backdrop,
                scrollFraction = materialScrollFraction,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(statusBar))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(BarHeight).padding(start = 8.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (isLogin) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable { menu = true }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        selection.label,
                                        style = COUITheme.textStyles.title3,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(COUIIcons.ExpandMore, contentDescription = "切换关注内容")
                                }
                                SelectionMenu(
                                    show = menu,
                                    selected = selection,
                                    onSelect = {
                                        menu = false
                                        app.settings.updateFollowSelection(it.value)
                                    },
                                    onDismiss = { menu = false },
                                )
                            } else {
                                Text(
                                    "本地关注",
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    style = COUITheme.textStyles.title3,
                                )
                            }
                        }
                        TopBarIconButton(onClick = { navigator.push(Route.Search(title = "")) }) {
                            Icon(COUIIcons.Search, contentDescription = "搜索")
                        }
                        NotificationBell(Modifier.padding(start = 10.dp))
                    }
                }
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = outerPadding.calculateBottomPadding() + 12.dp,
            )
            Box(Modifier.fillMaxSize().glassSource(backdrop)) {
                if (showLocal) {
                    LocalFollowFeed(returnTop = returnTop, scrollBehavior = scrollBehavior, contentPadding = contentPadding)
                } else {
                    // Each stream is filtered server-side and personalised, so its cached pages belong to
                    // one category of one account; switching starts again from the first page.
                    val viewModel = viewModel(key = "follow-${selection.name}-${app.session.uid}") {
                        FeedListViewModel(
                            PageLoader { page, first, last ->
                                app.repository.dataList(followUrl(selection), selection.label, page, first, last)
                            },
                        )
                    }
                    FeedList(
                        viewModel = viewModel,
                        scrollBehavior = scrollBehavior,
                        returnTop = returnTop,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionMenu(
    show: Boolean,
    selected: FollowSelection,
    onSelect: (FollowSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.BottomStart,
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            val entries = FollowSelection.entries
            entries.forEachIndexed { index, item ->
                val group = when (item) {
                    // The aggregate sits above the categories, the way 所有项目 does in Photos.
                    FollowSelection.All -> "all"
                    // Local follows are not server-side follows; keep them in their own group.
                    FollowSelection.Local -> "local"
                    else -> "categories"
                }
                DropdownImpl(
                    item = DropdownItem(text = item.label),
                    optionSize = entries.size,
                    isSelected = item == selected,
                    index = index,
                    groupKey = group,
                    onSelectedIndexChange = { onSelect(item) },
                )
            }
        }
    }
}

private val BarHeight = 56.dp
