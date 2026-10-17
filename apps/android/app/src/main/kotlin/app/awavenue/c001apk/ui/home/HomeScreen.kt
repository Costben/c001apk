package app.awavenue.c001apk.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.app.AppListPage
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/** The home tabs in the order the Flutter client lays them out. */
enum class HomeTab(val label: String) {
    Follow("关注"),
    App("应用"),
    Feed("动态"),
    Hot("热门"),
    Topic("话题"),
    Product("数码"),
    CoolPic("酷图"),
    ;

    /**
     * The tabs whose body is a plain feed. 话题 and 数码 own their own layout and 应用 its own page, so
     * they have no loader here. 关注 is the one tab whose request depends on a setting.
     */
    fun loader(repository: CoolapkRepository, followType: Int): PageLoader? = when (this) {
        Follow -> PageLoader { page, first, last ->
            repository.dataList(followUrl(followType), followTitle(followType), page, first, last)
        }

        Feed -> PageLoader { page, first, last -> repository.homeFeed(page, first, last) }
        Hot -> PageLoader { page, first, last ->
            repository.dataList("/page?url=V9_HOME_TAB_RANKING", "热榜", page, first, last)
        }

        CoolPic -> PageLoader { page, first, last ->
            repository.dataList("/page?url=V11_FIND_COOLPIC", "酷图", page, first, last)
        }

        App, Topic, Product -> null
    }
}

private const val FOLLOW_BASE = "/page?url=V9_HOME_TAB_FOLLOW"

private fun followUrl(type: Int) = when (type) {
    1 -> "$FOLLOW_BASE&type=circle"
    2 -> "$FOLLOW_BASE&type=topic"
    3 -> "$FOLLOW_BASE&type=product"
    4 -> "$FOLLOW_BASE&type=apk"
    else -> FOLLOW_BASE
}

private fun followTitle(type: Int) = when (type) {
    1 -> "好友关注"
    2 -> "话题关注"
    3 -> "数码关注"
    4 -> "应用关注"
    else -> "全部关注"
}

private val TAB_ROW_VERTICAL_PADDING = 6.dp

@Composable
fun HomeScreen(outerPadding: PaddingValues, pagerState: PagerState, returnTop: ReturnTopController) {
    val app = LocalApp.current
    val settings = app.settings
    val navigator = LocalNavigator.current
    val tabs = HomeTab.entries
    val scope = rememberCoroutineScope()
    // The bar no longer collapses, so the lists must not hand it any scroll: the default limit lets
    // the behavior swallow every upward drag as bar travel.
    val scrollBehavior = COUIScrollBehavior(state = rememberTopAppBarState(initialHeightOffsetLimit = 0f))

    LaunchedEffect(pagerState.settledPage) { settings.updateHomeTab(pagerState.settledPage) }

    val backdrop = rememberGlassBackdrop()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = statusBar + TabRowDefaults.TabRowWithContourHeight + TAB_ROW_VERTICAL_PADDING * 2
    val extraHeight = AppBarBlurOverlayDefaults.ExtraHeight

    Scaffold(
        topBar = {
            // No title row: the tab row sits straight under the status bar on COUI 17's app bar
            // material, which is opaque at the top and ramps to clear past the bar's bottom edge, so
            // the list fades out under the tabs instead of meeting a hard edge. The overlay is sized
            // to the bar plus its extra band, the way the material is measured on the device.
            AppBarBlurOverlay(
                modifier = Modifier.fillMaxWidth().height(barHeight + extraHeight),
                containerColor = COUITheme.colorScheme.surface,
                backdrop = backdrop,
                extraHeight = extraHeight,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(statusBar))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The contour form keeps the sliding indicator's shadow inside a container
                        // capsule; the bare TabRow has no container, so the shadow was clipped.
                        TabRowWithContour(
                            tabs = tabs.map { it.label },
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = {
                                if (it == pagerState.currentPage) returnTop.requestReturn()
                                scope.launch { pagerState.animateScrollToPage(it) }
                            },
                            modifier = Modifier.weight(1f).padding(vertical = TAB_ROW_VERTICAL_PADDING),
                        )
                        IconButton(onClick = { navigator.push(Route.Search(title = "")) }) {
                            Icon(COUIIcons.Search, contentDescription = "搜索")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            beyondViewportPageCount = 1,
        ) { page ->
            val tab = tabs[page]
            val returnTopHere = returnTop.takeIf { page == pagerState.currentPage }
            val contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = outerPadding.calculateBottomPadding() + 12.dp,
            )
            when (tab) {
                HomeTab.App -> AppListPage(returnTop = returnTopHere, contentPadding = contentPadding)
                HomeTab.Topic -> HomeTopicPage(HomeTopicTab.Topic, scrollBehavior, contentPadding)
                HomeTab.Product -> HomeTopicPage(HomeTopicTab.Product, scrollBehavior, contentPadding)
                else -> {
                    // 关注 filters server-side, so its cached page is only valid for one follow type;
                    // every stream is personalised, so it is also only valid for one account.
                    val account = app.session.uid
                    val key = if (tab == HomeTab.Follow) "home-follow-${settings.followType}-$account" else "home-${tab.name}-$account"
                    val loader = tab.loader(app.repository, settings.followType)
                    val viewModel = viewModel(key = key) { FeedListViewModel(loader!!) }
                    FeedList(
                        viewModel = viewModel,
                        scrollBehavior = scrollBehavior,
                        returnTop = returnTopHere,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}
