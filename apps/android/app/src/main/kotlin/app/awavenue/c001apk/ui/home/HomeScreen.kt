package app.awavenue.c001apk.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import app.awavenue.c001apk.ui.common.longPressAnywhere
import app.awavenue.c001apk.ui.search.SearchContentType
import app.awavenue.c001apk.ui.search.SearchResultTabs
import app.awavenue.c001apk.ui.search.SearchHistorySection
import app.awavenue.c001apk.ui.search.SearchResultPager
import app.awavenue.c001apk.ui.search.SearchSortType
import app.awavenue.c001apk.ui.search.SearchType
import androidx.compose.foundation.pager.rememberPagerState
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.app.AppListPage
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.NotificationBell
import app.awavenue.c001apk.ui.common.ReturnTopController
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
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
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
/** How far a top-row key's halo reaches past the row; the row's clip leaves this much room. */
private val TOP_ROW_HALO = 16.dp

/**
 * The header cards the 首页内容 switches own: the banner, the channel shortcuts, and the rows of
 * recommended topics and 数码 entries. Everything else in the stream always shows.
 */
private fun homeHeaderVisible(item: Entity, carousel: Boolean, channels: Boolean, topics: Boolean): Boolean =
    when (item.entityTemplate) {
        "imageCarouselCard_1" -> carousel
        "iconLinkGridCard" -> channels
        "iconMiniScrollCard", "iconMiniGridCard" -> topics
        else -> true
    }

/**
 * The bar material's alpha when the top row is fully expanded. The material stays on at rest so the
 * bar reads as a translucent surface rather than as icons sitting directly on the list.
 */
private const val MATERIAL_MIN_ALPHA = 0.55f

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
    // Flipping a 首页内容 switch redraws the stream at once: the fetched page keeps its cards, only
    // the draw filter changes, so turning one back on needs no refresh. Only 动态 has these headers.
    val homeHeaderFilter: (Entity) -> Boolean = remember(
        settings.showHomeCarousel,
        settings.showHomeChannels,
        settings.showHomeTopics,
    ) {
        val carousel = settings.showHomeCarousel
        val channels = settings.showHomeChannels
        val topics = settings.showHomeTopics
        { item: Entity -> homeHeaderVisible(item, carousel, channels, topics) }
    }
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
    // Search runs in place: the bar's round search key stretches into the field, the history fades
    // in under it, and a submitted keyword lists its results right there instead of pushing a page.
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // The keyword the results are showing; null while the history is.
    var searchSubmitted by rememberSaveable { mutableStateOf<String?>(null) }
    var searchType by rememberSaveable { mutableStateOf(SearchType.All) }
    var searchSort by rememberSaveable { mutableStateOf(SearchSortType.Dateline) }
    val resultTabs = SearchContentType.entries
    val resultPagerState = rememberPagerState { resultTabs.size }
    // The results never collapse the bar; the state only tracks how far the list has scrolled, which
    // drives the bar material the way the home stream's scroll does.
    val resultScrollState = rememberTopAppBarState(initialHeightOffsetLimit = 0f)
    val resultScrollBehavior = COUIScrollBehavior(state = resultScrollState)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val closeSearch = {
        searchQuery = ""
        searchSubmitted = null
        searchExpanded = false
    }
    val submitSearch: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            // The results are what the user is reading now: a field that kept the focus would
            // leave the caret blinking over them.
            focusManager.clearFocus()
            keyboard?.hide()
            app.searchHistory.add(text)
            searchQuery = text
            if (text != searchSubmitted) resultScrollState.contentOffset = 0f
            searchSubmitted = text
        }
    }
    // True while the search is open and no pushed page covers it. The bar itself stays expanded
    // under a page, so that only decides which back steps may run.
    val searchActive = searchExpanded && !navigator.hasOverlay
    // Back from the history closes the search. Back from the results is handled after the bar.
    BackHandler(enabled = searchActive) { closeSearch() }
    // Switching to 关注 / 我的 leaves the search behind instead of reopening it on the way back.
    val onOtherMainTab = navigator.mainBackAvailable
    LaunchedEffect(onOtherMainTab) { if (onOtherMainTab) closeSearch() }
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
    // The opaque face the bar turns into while the results scroll under it.
    val barFace = COUITheme.colorScheme.surface
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Same profile as the Settings homepage's scroll-driven overlay: the blur is at full strength
    // at the bar's top edge and fades to clear over the bar plus the native extra band below it.
    val barHeightDp = statusBar + TOP_BAR_ROW_HEIGHT +
        TabRowDefaults.TabRowWithContourHeight + TAB_ROW_VERTICAL_PADDING * 2
    val materialExtra = AppBarBlurOverlayDefaults.ExtraHeight
    // Where the bar ends, so a return can scroll the list enough to bring the row below it.
    val appBarBounds = LocalAppBarBounds.current
    // The material never goes fully dark: at rest the bar keeps a translucent floor so the top row
    // and the tab capsule still sit on a blurred layer instead of directly on the list. Strength
    // follows the distance the list has scrolled, not the row's collapse: pulling the row back down
    // changes no content position, and tying the material to it would visibly thin the blur exactly
    // when the row reappears. The draw-phase read keeps the shader composed across the gesture.
    // 0 → 1 as the search opens; read only in draw lambdas, so the fade never recomposes the bar.
    val searchFade = remember { Animatable(if (searchExpanded) 1f else 0f) }
    LaunchedEffect(searchExpanded) { searchFade.animateTo(if (searchExpanded) 1f else 0f, tween(220)) }
    val segmentsGone by remember(searchFade) { derivedStateOf { searchFade.value >= 1f } }
    // 0 → 1 as results replace the history.
    val resultsFade = remember { Animatable(if (searchSubmitted != null) 1f else 0f) }
    LaunchedEffect(searchSubmitted != null) {
        resultsFade.animateTo(if (searchSubmitted != null) 1f else 0f, tween(220))
    }
    val resultsShown by remember(resultsFade) { derivedStateOf { resultsFade.value > 0f } }
    val historyGone by remember(resultsFade) { derivedStateOf { resultsFade.value >= 1f } }
    val materialScrollFraction = remember(scrolledPx, density, searchFade, resultsFade, resultScrollState) {
        {
            val ramp = AppBarBlurOverlayDefaults.scrollAlpha(scrolledPx.floatValue, density)
            val resultRamp = AppBarBlurOverlayDefaults.scrollAlpha(abs(resultScrollState.contentOffset), density)
            // The history panel shares the bar's surface colour, so the material clears while it
            // is open: no bar edge is left drawn across the panel. The results scroll under the
            // bar like the stream does, so the material returns with them.
            (MATERIAL_MIN_ALPHA + (1f - MATERIAL_MIN_ALPHA) * ramp) * (1f - searchFade.value) +
                (MATERIAL_MIN_ALPHA + (1f - MATERIAL_MIN_ALPHA) * resultRamp) * resultsFade.value
        }
    }

    Scaffold(
        edgeToEdge = true,
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
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // BiliCO's detail bar: once the results scroll under the bar, its whole
                            // face becomes the page's own surface, so no screenshot bleeds through
                            // the chips. Below the threshold the bar stays the glass it always is.
                            val covered = resultsFade.value * AppBarBlurOverlayDefaults.scrollAlpha(
                                abs(resultScrollState.contentOffset),
                                this,
                            )
                            if (covered > 0f) drawRect(barFace.copy(alpha = covered))
                        },
                ) {
                    Spacer(modifier = Modifier.height(statusBar))
                    // The row keeps its full height and slides out of a box that shrinks with the
                    // scroll, so the tab row rises into its place instead of leaving a gap behind.
                    // The height is resolved in the measure phase and the clip in the draw phase,
                    // both from [collapseFraction], so scrolling never recomposes the bar.
                    // A circular button's halo reaches 15.5dp past its 40dp circle, while the row only
                    // leaves 6dp above and below. The clip therefore keeps a halo-wide margin on every
                    // side: at rest it cuts nothing, and while the row slides out the glow under the
                    // buttons stays drawn instead of switching off at the first pixel of collapse.
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
                            .drawWithContent {
                                val halo = TOP_ROW_HALO.toPx()
                                clipRect(-halo, -halo, size.width + halo, size.height + halo) {
                                    this@drawWithContent.drawContent()
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TOP_BAR_ROW_HEIGHT)
                                .graphicsLayer {
                                    translationY = -topRowHeightPx * collapseFraction()
                                    alpha = 1f - collapseFraction()
                                    // The avatar and buttons barely overlap, so the fade is applied
                                    // to each draw instead of an offscreen copy of the whole row.
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                            ,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // COUI's toolbar search: the round key stretches left into the field
                            // and splits off the 取消 capsule, all inside this row. Transparent
                            // gradient = embedded, so it rides on the material drawn above.
                            ChromeDensity {
                                TopAppBarSearchBar(
                                    query = searchQuery,
                                    onQueryChange = {
                                        searchQuery = it
                                        // Clearing the field goes back to the history.
                                        if (it.isEmpty()) searchSubmitted = null
                                    },
                                    // The field stays open while a page is pushed over it: coming back
                                    // from a feed must land on the results exactly as they were, not
                                    // on a bar that collapsed and reopened underneath.
                                    expanded = searchExpanded,
                                    onExpandedChange = { searchExpanded = it },
                                    placeholder = "搜索",
                                    cancelText = "取消",
                                    onSearch = submitSearch,
                                    onCancel = closeSearch,
                                    searchButtonContentDescription = "搜索",
                                    backdrop = backdrop,
                                    gradientColor = Color.Transparent,
                                    extraActions = { NotificationBell() },
                                    titleContent = {
                                        NetworkImage(
                                            app.session.userAvatar,
                                            Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .clickable(onClick = openProfile),
                                            contentDescription = "我的",
                                        )
                                    },
                                )
                                // Back from the results returns to the history first. The bar's own
                                // back handler closes the whole search, and the newest registered
                                // handler wins, so this one has to come after the bar.
                                BackHandler(enabled = searchActive && searchSubmitted != null) {
                                    searchSubmitted = null
                                }
                                // The field stays open while a page is pushed over it, so the bar's
                                // own back handler would close the search instead of leaving that
                                // page. Being registered after the bar, this one runs first and pops.
                                BackHandler(enabled = searchExpanded && navigator.hasOverlay) {
                                    navigator.pop()
                                }
                            }
                        }
                    }
                    // The segments only fade while the search is open; their slot keeps its height,
                    // so the bar and the pages under it stay exactly where they were. Once faded
                    // they leave composition, and taps in the slot reach the history underneath.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(TabRowDefaults.TabRowWithContourHeight + TAB_ROW_VERTICAL_PADDING * 2),
                    ) {
                    if (!segmentsGone) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = 1f - searchFade.value }
                            // The capsule shares the top row's 16dp margins, so its edges line up
                            // with the avatar's left edge and the bell's right edge.
                            .padding(horizontal = 16.dp)
                            // Long-press anywhere on the bar, including the tabs themselves,
                            // opens the editor; taps and drags still reach the tab row below.
                            .longPressAnywhere(openTabEditor),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // ColorOS 17's segment control (COUISegmentButtonLayout) paints an opaque
                        // capsule: the container hides the cards behind it instead of letting the
                        // app bar material show through, with no glass or rim on top.
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
                                // COUI's transparent blur highlight: the capsule blurs the page
                                // under it, with the library's own colours and indicator material.
                                // Six labels have to share the narrower capsule: COUI's 52dp floor and
                                // 12dp segment padding overflow it, so the segments give up some of
                                // both instead of the capsule growing.
                                minWidth = 44.dp,
                                segmentPadding = 8.dp,
                                backdrop = backdrop,
                            )
                        }
                    }
                    }
                    // While results show, the slot carries their content types instead: COUI's
                    // chip tabs, led by the round grid chip that opens the type / filter menu.
                    if (resultsShown) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = resultsFade.value },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            SearchResultTabs(
                                tabs = resultTabs,
                                selectedIndex = resultPagerState.currentPage,
                                onSelectedChange = { scope.launch { resultPagerState.animateScrollToPage(it) } },
                                searchType = searchType,
                                onSearchType = { searchType = it },
                                sortType = searchSort,
                                onSortType = { searchSort = it },
                            )
                        }
                    }
                    }
                }
            }
        },
    ) { innerPadding ->
        // Scaffold writes the bar's height into [innerPadding] while it measures, so the pages must
        // read it in their own measure to land in the same frame as the bar. Reading the top
        // padding here, in composition, would hand them last frame's height and recompose every
        // page on each scroll frame, so the object delegates and is read lazily.
        val pageContentPadding = remember(innerPadding, outerPadding) {
            object : PaddingValues {
                override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
                override fun calculateTopPadding() = innerPadding.calculateTopPadding()
                override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
                override fun calculateBottomPadding() = outerPadding.calculateBottomPadding() + 12.dp
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            // The search panel sits in the same glass layer as the pages, so the bar blurs whichever
            // of them is showing.
            Box(Modifier.fillMaxSize().glassSource(backdrop)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalDragWins { vertical -> pagerScrollEnabled = !vertical },
                userScrollEnabled = pagerScrollEnabled,
                beyondViewportPageCount = 1,
            ) { page ->
                // MainScreen's pager keeps the full tab count; pages past the filtered end show the
                // last visible tab until the settle effect bounces them back.
                val tab = tabs.getOrNull(page) ?: tabs.last()
                val returnTopHere = returnTop.takeIf { page == pagerState.currentPage }
                when (tab) {
                    HomeTab.App -> AppListPage(
                        returnTop = returnTopHere,
                        contentPadding = pageContentPadding,
                        scrollBehavior = scrollBehavior,
                    )
                    HomeTab.Topic -> HomeTopicPage(HomeTopicTab.Topic, scrollBehavior, pageContentPadding)
                    HomeTab.Product -> HomeTopicPage(HomeTopicTab.Product, scrollBehavior, pageContentPadding)
                    else -> {
                        // Every stream is personalised, so its cached page is only valid for one account.
                        val key = "home-${tab.name}-${app.session.uid}"
                        val loader = tab.loader(app.repository)
                        val viewModel = viewModel(key = key) { FeedListViewModel(loader!!, app::contentGate) }
                        FeedList(
                            viewModel = viewModel,
                            scrollBehavior = scrollBehavior,
                            returnTop = returnTopHere,
                            contentPadding = pageContentPadding,
                            itemFilter = if (tab == HomeTab.Feed) homeHeaderFilter else null,
                        )
                    }
                }
            }
            // The history drops in under the expanded field and covers the stream, the way the
            // search page used to; picking a keyword opens its results on top.
            AnimatedVisibility(
                visible = searchExpanded,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(160)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(COUITheme.colorScheme.surface)
                        // Swallow taps and drags so nothing reaches the stream underneath.
                        .clickable(interactionSource = null, indication = null) {},
                ) {
                    if (!historyGone) {
                        SearchHistorySection(
                            onPick = submitSearch,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 1f - resultsFade.value }
                                // Straight under the search field: the segment slot above is empty
                                // while searching, and the bar's material is cleared.
                                .padding(top = statusBar + TOP_BAR_ROW_HEIGHT)
                                .verticalScroll(rememberScrollState()),
                            bottomPadding = outerPadding.calculateBottomPadding(),
                        )
                    }
                    val submitted = searchSubmitted
                    if (submitted != null) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = resultsFade.value }
                                .background(COUITheme.colorScheme.surface),
                        ) {
                            SearchResultPager(
                                query = submitted,
                                tabs = resultTabs,
                                pagerState = resultPagerState,
                                searchType = searchType,
                                sortType = searchSort,
                                pageType = null,
                                pageParam = null,
                                scrollBehavior = resultScrollBehavior,
                                contentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding() + 12.dp,
                                    bottom = outerPadding.calculateBottomPadding() + 12.dp,
                                ),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
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
