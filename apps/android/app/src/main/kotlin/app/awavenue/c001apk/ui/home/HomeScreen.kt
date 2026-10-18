package app.awavenue.c001apk.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.app.AppListPage
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.TopAppBarState
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** The home tabs in the order the Flutter client lays them out; 关注 has its own main tab. */
enum class HomeTab(val label: String) {
    App("应用"),
    Feed("动态"),
    Hot("热门"),
    Topic("话题"),
    Product("数码"),
    CoolPic("酷图"),
    ;

    /**
     * The tabs whose body is a plain feed. 话题 and 数码 own their own layout and 应用 its own page, so
     * they have no loader here.
     */
    fun loader(repository: CoolapkRepository): PageLoader? = when (this) {
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

private val TAB_ROW_VERTICAL_PADDING = 6.dp
private val TOP_BAR_ROW_HEIGHT = 52.dp
private val SEARCH_FIELD_HEIGHT = 38.dp

/** How far the material reaches past the bar, so the list fades out under it. */
private val BAR_EXTRA_HEIGHT = 14.dp

/**
 * Long-press anywhere on the bar, including over the tabs. The tab items consume their own down,
 * so a parent combinedClickable never sees those touches; the Initial pass does. Nothing is
 * consumed until the press actually turns into a long press, which keeps taps and drags intact.
 */
private fun Modifier.longPressAnywhere(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val touchSlop = viewConfiguration.touchSlop
        var canceled = false
        val releasedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                if (!change.pressed || change.isConsumed ||
                    (change.position - down.position).getDistance() > touchSlop
                ) {
                    canceled = true
                    return@withTimeoutOrNull
                }
            }
        }
        if (releasedEarly == null && !canceled) {
            onLongPress()
            // Swallow the rest of the gesture so the tab does not also click on release.
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                pressed = event.changes.any { it.pressed }
            }
        }
    }
}

/**
 * Reports a mostly-vertical drag before the page's list sees it, so the pager can stand down for
 * that gesture. It only observes — nothing is consumed, so the list still scrolls. A diagonal drag
 * counts as vertical once its vertical component is within a quarter of the horizontal one, which
 * is what makes "I swiped up" mean up instead of the pager taking the gesture sideways.
 */
private fun Modifier.verticalDragWins(onVerticalDrag: (Boolean) -> Unit): Modifier = pointerInput(onVerticalDrag) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var vertical: Boolean? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (vertical == null) {
                val dx = abs(change.position.x - down.position.x)
                val dy = abs(change.position.y - down.position.y)
                if (dx > viewConfiguration.touchSlop || dy > viewConfiguration.touchSlop) {
                    vertical = dy * 1.25f >= dx
                    if (vertical == true) onVerticalDrag(true)
                }
            }
        }
        if (vertical == true) onVerticalDrag(false)
    }
}

/**
 * Collapses the top row while scrolling up, but brings it back as soon as the content moves down,
 * even in the middle of a list. [COUIScrollBehavior] only expands once the content is back at the
 * top; here the downward scroll the list already consumed counts too.
 */
@Composable
private fun rememberHomeScrollBehavior(state: TopAppBarState): ScrollBehavior {
    val settle = COUIScrollBehavior(state = state)
    return remember(state, settle) {
        object : ScrollBehavior {
            override val state = state
            override val isPinned = false
            override val snapAnimationSpec = settle.snapAnimationSpec
            override val flingAnimationSpec = settle.flingAnimationSpec
            override val nestedScrollConnection = object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y >= 0f) return Offset.Zero
                    val previous = state.heightOffset
                    state.heightOffset += available.y
                    return Offset(0f, state.heightOffset - previous)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    state.contentOffset += consumed.y
                    val delta = if (consumed.y > 0f) consumed.y else available.y
                    if (delta <= 0f) return Offset.Zero
                    val previous = state.heightOffset
                    state.heightOffset += delta
                    return Offset(0f, state.heightOffset - previous)
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (available.y > 0f) state.contentOffset = 0f
                    return settle.nestedScrollConnection.onPostFling(consumed, available)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(outerPadding: PaddingValues, pagerState: PagerState, returnTop: ReturnTopController) {
    val app = LocalApp.current
    val settings = app.settings
    val navigator = LocalNavigator.current
    // Hiding every tab would leave the pager with no page, so an empty set falls back to all.
    val tabs = HomeTab.entries.filterNot { it.name in settings.hiddenHomeTabs }.ifEmpty { HomeTab.entries }
    val selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.lastIndex)
    var showTabEditor by remember { mutableStateOf(false) }
    val openTabEditor = rememberHapticClick { showTabEditor = true }
    // The top bar mirrors the official home header: own avatar, search, notifications.
    val openProfile = rememberHapticClick {
        if (app.session.isLogin) {
            navigator.push(Route.User(app.session.uid))
        } else {
            navigator.push(Route.Login)
        }
    }
    val openSearch = rememberHapticClick {
        navigator.push(Route.Search(title = ""))
    }
    val openNotifications = rememberHapticClick {
        if (app.session.isLogin) {
            navigator.push(Route.NotificationCenter)
        } else {
            navigator.push(Route.Login)
        }
    }
    val scope = rememberCoroutineScope()
    // Only the top row collapses. The limit is exactly its height, so the tab row stays pinned
    // under the status bar once the header has scrolled away.
    val topRowHeightPx = with(LocalDensity.current) { TOP_BAR_ROW_HEIGHT.toPx() }
    val scrollBehavior = rememberHomeScrollBehavior(
        rememberTopAppBarState(initialHeightOffsetLimit = -topRowHeightPx),
    )
    SideEffect {
        // The saved state keeps the limit it was created with, so a density change has to reapply it.
        scrollBehavior.state.heightOffsetLimit = -topRowHeightPx
    }
    val collapsedPx = (-scrollBehavior.state.heightOffset).coerceIn(0f, topRowHeightPx)
    val collapseFraction = if (topRowHeightPx > 0f) collapsedPx / topRowHeightPx else 0f
    val topRowHeight = TOP_BAR_ROW_HEIGHT * (1f - collapseFraction)
    // A mostly-vertical drag belongs to the list, even when it starts diagonally; see
    // [verticalDragWins]. The pager is switched off for the rest of such a gesture.
    var pagerScrollEnabled by remember { mutableStateOf(true) }

    // The pager belongs to MainScreen and keeps the full tab count, so a hidden selection or a page
    // past the visible end must be pulled back before its settled page is written back.
    LaunchedEffect(tabs) {
        val target = tabs.indexOfFirst { it.name == settings.homeTab }.takeIf { it >= 0 } ?: 0
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = tabs.getOrNull(page)
            // A page the filtered list no longer has: bounce back to the last visible tab.
            if (tab == null) {
                pagerState.scrollToPage(tabs.lastIndex)
            } else {
                settings.updateHomeTab(tab.name)
            }
        }
    }

    val backdrop = rememberGlassBackdrop()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = statusBar + topRowHeight + TabRowDefaults.TabRowWithContourHeight +
        TAB_ROW_VERTICAL_PADDING * 2
    val extraHeight = BAR_EXTRA_HEIGHT

    Scaffold(
        topBar = {
            // The material is sized to the bar plus the band below it, so the list keeps fading out
            // past the bar's edge instead of meeting a hard line.
            Box(modifier = Modifier.fillMaxWidth().height(barHeight + extraHeight)) {
                HomeBarMaterial(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxSize(),
                    extraHeight = extraHeight,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(statusBar))
                    // The row keeps its full height and slides out of a box that shrinks with the
                    // scroll, so the tab row rises into its place instead of leaving a gap behind.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topRowHeight)
                            .clipToBounds(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TOP_BAR_ROW_HEIGHT)
                                .graphicsLayer {
                                    translationY = -collapsedPx
                                    alpha = 1f - collapseFraction
                                }
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NetworkImage(
                                app.session.userAvatar,
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = openProfile),
                                contentDescription = "我的",
                            )
                            Spacer(Modifier.width(10.dp))
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(SEARCH_FIELD_HEIGHT)
                                    .clip(RoundedCornerShape(50))
                                    .background(COUITheme.colorScheme.surfaceContainerHigh)
                                    .clickable(onClick = openSearch)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "搜索",
                                    modifier = Modifier.weight(1f),
                                    style = COUITheme.textStyles.body2,
                                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                )
                                Icon(
                                    COUIIcons.Search,
                                    contentDescription = "搜索",
                                    tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            IconButton(onClick = openNotifications) {
                                Icon(COUIIcons.Email, contentDescription = "通知")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Six labels only fit the bar with a narrow side margin: COUI's segments
                            // carry 12dp of padding each, so 16dp sides leave the labels ellipsised.
                            .padding(horizontal = 4.dp)
                            // Long-press anywhere on the bar, including the tabs themselves,
                            // opens the editor; taps and drags still reach the tab row below.
                            .longPressAnywhere(openTabEditor),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The contour form keeps the sliding indicator's shadow inside a container
                        // capsule; the bare TabRow has no container, so the shadow was clipped.
                        TabRowWithContour(
                            tabs = tabs.map { it.label },
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = {
                                if (it == selectedTabIndex) returnTop.requestReturn()
                                scope.launch { pagerState.animateScrollToPage(it) }
                            },
                            modifier = Modifier.weight(1f).padding(vertical = TAB_ROW_VERTICAL_PADDING),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .verticalDragWins { vertical -> pagerScrollEnabled = !vertical },
            userScrollEnabled = pagerScrollEnabled,
            beyondViewportPageCount = 1,
        ) { page ->
            // MainScreen's pager keeps the full tab count; pages past the filtered end show the
            // last visible tab until the settle effect bounces them back.
            val tab = tabs.getOrNull(page) ?: tabs.last()
            val returnTopHere = returnTop.takeIf { page == pagerState.currentPage }
            val contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = outerPadding.calculateBottomPadding() + 12.dp,
            )
            when (tab) {
                HomeTab.App -> AppListPage(
                    returnTop = returnTopHere,
                    contentPadding = contentPadding,
                    scrollBehavior = scrollBehavior,
                )
                HomeTab.Topic -> HomeTopicPage(HomeTopicTab.Topic, scrollBehavior, contentPadding)
                HomeTab.Product -> HomeTopicPage(HomeTopicTab.Product, scrollBehavior, contentPadding)
                else -> {
                    // Every stream is personalised, so its cached page is only valid for one account.
                    val key = "home-${tab.name}-${app.session.uid}"
                    val loader = tab.loader(app.repository)
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

    if (showTabEditor) {
        HomeTabEditorDialog(
            hidden = settings.hiddenHomeTabs,
            onConfirm = {
                settings.updateHiddenHomeTabs(it)
                showTabEditor = false
            },
            onDismiss = { showTabEditor = false },
        )
    }
}
