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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.app.AppListPage
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.NotificationBell
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.rememberTopRowScrollBehavior
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalAppBarBounds
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.theme.ChromeDensity
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.MaterialStrokeParams
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

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

/**
 * The bar material's alpha when the top row is fully expanded. The material stays on at rest so the
 * bar reads as a translucent surface rather than as icons sitting directly on the list.
 */
private const val MATERIAL_MIN_ALPHA = 0.55f

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

@Composable
fun HomeScreen(
    outerPadding: PaddingValues,
    pagerState: PagerState,
    returnTop: ReturnTopController,
    barSlot: Int,
) {
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
    val scope = rememberCoroutineScope()
    // Only the top row collapses. The limit is exactly its height, so the tab row stays pinned
    // under the status bar once the header has scrolled away.
    val topRowHeightPx = with(LocalDensity.current) { TOP_BAR_ROW_HEIGHT.toPx() }
    // How far the list has actually moved below its top. The top row's own collapse is not part of
    // it: pulling the row back down moves nothing in the list, so the material must not fade there.
    val scrolledPx = remember { mutableFloatStateOf(0f) }
    val scrollBehavior = rememberTopRowScrollBehavior(
        rememberTopAppBarState(initialHeightOffsetLimit = -topRowHeightPx),
        scrolledPx,
    )
    SideEffect {
        // The saved state keeps the limit it was created with, so a density change has to reapply it.
        scrollBehavior.state.heightOffsetLimit = -topRowHeightPx
    }
    // Collapse state is read from [collapseFraction] inside layout, draw and the material, never
    // during composition: a scroll frame must move the row without recomposing the pager.
    val collapseFraction: () -> Float = remember(scrollBehavior, topRowHeightPx) {
        {
            if (topRowHeightPx > 0f) {
                (-scrollBehavior.state.heightOffset).coerceIn(0f, topRowHeightPx) / topRowHeightPx
            } else {
                0f
            }
        }
    }
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
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Same profile as the Settings homepage's scroll-driven overlay: the blur is at full strength
    // at the bar's top edge and fades to clear over the bar plus the native extra band below it.
    // The gradient starts below the status bar, because the bar's own rows sit there.
    val barHeightDp = statusBar + TOP_BAR_ROW_HEIGHT +
        TabRowDefaults.TabRowWithContourHeight + TAB_ROW_VERTICAL_PADDING * 2
    val materialExtra = AppBarBlurOverlayDefaults.ExtraHeight
    val blurStartFraction = statusBar.value / (barHeightDp + materialExtra).value
    // Where the bar ends, so a return can scroll the list enough to bring the row below it.
    val appBarBounds = LocalAppBarBounds.current
    // The material never goes fully dark: at rest the bar keeps a translucent floor so the top row
    // and the tab capsule still sit on a blurred layer instead of directly on the list. Strength
    // follows the distance the list has scrolled, not the row's collapse: pulling the row back down
    // changes no content position, and tying the material to it would visibly thin the blur exactly
    // when the row reappears. The draw-phase read keeps the shader composed across the gesture.
    val materialScrollFraction = remember(scrolledPx, density) {
        {
            val ramp = AppBarBlurOverlayDefaults.scrollAlpha(scrolledPx.floatValue, density)
            MATERIAL_MIN_ALPHA + (1f - MATERIAL_MIN_ALPHA) * ramp
        }
    }

    Scaffold(
        topBar = {
            // COUI's own app bar material: progressive blur plus the gradient surface ramp. The
            // overlay measures the bar and draws the material past it without adding that band to
            // the Scaffold's content padding.
            AppBarBlurOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        appBarBounds.report(barSlot, it.positionInRoot().y + it.size.height)
                    },
                containerColor = COUITheme.colorScheme.surface,
                backdrop = backdrop,
                scrollFraction = materialScrollFraction,
                // The bar carries a second row (the segment row) below the top row. The native
                // gradient is at full strength only at the top edge and fades to clear at the
                // node's bottom, so once the bar grew the content under the segments was left
                // sharp. The blur now stays full down to the bar's bottom edge and fades over the
                // extra band below it.
                extraHeight = materialExtra,
                blurStartFraction = blurStartFraction,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(statusBar))
                    // The row keeps its full height and slides out of a box that shrinks with the
                    // scroll, so the tab row rises into its place instead of leaving a gap behind.
                    // The height is resolved in the measure phase and the clip in the draw phase,
                    // both from [collapseFraction], so scrolling never recomposes the bar.
                    // A circular button's halo reaches 15.5dp past its 40dp circle, while the row only
                    // leaves 6dp above and below, so clipping at rest would slice the halo off; the
                    // clip only engages while the row is sliding out.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val full = TOP_BAR_ROW_HEIGHT.roundToPx()
                                val placeable = measurable.measure(
                                    constraints.copy(minHeight = full, maxHeight = full),
                                )
                                val visible = (full * (1f - collapseFraction())).roundToInt()
                                    .coerceIn(0, full)
                                layout(placeable.width, visible) { placeable.placeRelative(0, 0) }
                            }
                            .graphicsLayer { clip = collapseFraction() > 0f },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TOP_BAR_ROW_HEIGHT)
                                .graphicsLayer {
                                    translationY = -topRowHeightPx * collapseFraction()
                                    alpha = 1f - collapseFraction()
                                }
                                .padding(start = 16.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChromeDensity {
                                NetworkImage(
                                    app.session.userAvatar,
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = openProfile),
                                    contentDescription = "我的",
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            TopBarIconButton(onClick = openSearch) {
                                Icon(COUIIcons.Search, contentDescription = "搜索")
                            }
                            NotificationBell(Modifier.padding(start = 10.dp))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The capsule shares the top row's 16dp margins, so its edges line up
                            // with the avatar's left edge and the bell's right edge.
                            .padding(horizontal = 16.dp)
                            // Long-press anywhere on the bar, including the tabs themselves,
                            // opens the editor; taps and drags still reach the tab row below.
                            .longPressAnywhere(openTabEditor),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // ColorOS 17's segment control (COUISegmentButtonLayout) paints its own flat
                        // translucent container — #0F000000 light / #1FFFFFFF dark — with no glass,
                        // rim or shadow, so the tabs sit straight on the app bar material.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = TAB_ROW_VERTICAL_PADDING)
                                .height(TabRowDefaults.TabRowWithContourHeight),
                        ) {
                            TabRowWithContour(
                                tabs = tabs.map { it.label },
                                selectedTabIndex = selectedTabIndex,
                                onTabSelected = {
                                    if (it == selectedTabIndex) returnTop.requestReturn()
                                    scope.launch { pagerState.animateScrollToPage(it) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                // Six labels have to share the narrower capsule: COUI's 52dp floor and
                                // 12dp segment padding overflow it, so the segments give up some of
                                // both instead of the capsule growing.
                                minWidth = 44.dp,
                                segmentPadding = 8.dp,
                                indicatorMaterial = MaterialStrokeParams.frameworkCapsule13(
                                    COUITheme.colorScheme.background.luminance() < 0.5f,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
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
