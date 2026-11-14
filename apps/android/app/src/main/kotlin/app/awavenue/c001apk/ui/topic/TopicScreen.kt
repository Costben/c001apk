package app.awavenue.c001apk.ui.topic

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.ActionResult
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.data.ProductComparisonSelection
import app.awavenue.c001apk.data.ProductComparisonStore
import app.awavenue.c001apk.data.ProductConfigInfo
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.data.ToggleResult
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppBarTitleSize
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.AppFloatingActionButton
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import app.awavenue.c001apk.ui.common.rememberTopRowScrollBehavior
import app.awavenue.c001apk.ui.common.shareText
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.EntityItem
import app.awavenue.c001apk.ui.feed.FeedActions
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.nav.LocalAppBarBounds
import app.awavenue.c001apk.ui.nav.LocalFeedListState
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.LocalPageMemory
import app.awavenue.c001apk.ui.nav.LocalRouteTransition
import app.awavenue.c001apk.ui.nav.Navigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import app.awavenue.c001apk.ui.search.InPlaceSearchBar
import app.awavenue.c001apk.ui.search.InPlaceSearchPanel
import app.awavenue.c001apk.ui.search.SearchResultTabs
import app.awavenue.c001apk.ui.search.rememberInPlaceSearchState
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.ButtonSize
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.LocalToolbarBackdropBlurFraction
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.isBlurEnabled
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Check
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val TAB_ROW_VERTICAL_PADDING = 6.dp

/**
 * How far a circular key's halo reaches past its 40dp circle, kept as the row's clip margin so the
 * glow survives the row sliding out. 首页's row carries the same value.
 */
private val TOP_ROW_HALO = 16.dp

/**
 * The bar material's alpha while the search shows its results. The floor keeps the bar a frosted
 * sheet over the panel instead of the panel's flat surface, the way 首页's bar stays.
 */
private const val MATERIAL_MIN_ALPHA = 0.55f

/**
 * The room the overlaid title leaves for the bar's actions: the bar's 16dp inset, the 40dp search
 * key, the 6dp gap before it, and the 40dp menu key. The title ends here instead of running under
 * them.
 */
private val TOP_BAR_ACTIONS_INSET = 102.dp

/** Keeps a measured three-character key from clipping the last glyph on hinting. */
private val KEY_SIDE_SLACK = 2.dp

/** The gap and the chip height the search's content types take when they ride on the panel. */
private val SEARCH_CHIP_GAP = 8.dp
private val SEARCH_CHIP_HEIGHT = ChipTabDefaults.Height

/** Page-memory key for the discussion sort the user picked on a product page. */
private const val TOPIC_SORT_KEY = "topic-sort"

private enum class TopicSortType(val label: String) {
    Default("默认"),
    Dateline("最新"),
}

/**
 * The order the list on screen offers, handed to the bar's 排序规则 menu.
 *
 * A topic list carries its orders as a `sortSelectCard`, which the page used to draw as a card of
 * its own; a product's discussion list has them in its url. Either way the menu shows the same
 * names and the list reloads in place.
 */
private class TopicSortChoices(
    val labels: List<String>,
    val selected: String?,
    val pick: (Int) -> Unit,
)

@Composable
fun TopicScreen(tag: String) = TopicPage(tag = tag, id = null)

@Composable
fun ProductScreen(id: String) = TopicPage(tag = null, id = id)

@Composable
private fun TopicPage(tag: String?, id: String?) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val appBarBounds = LocalAppBarBounds.current
    val scope = rememberCoroutineScope()

    val decodedTag = tag?.let { runCatching { Uri.decode(it) }.getOrDefault(it) }
    // The header lives on the entry's ViewModel store: a post opened on top of this page used to
    // take the loaded header with it, so coming back flashed a spinner while it reloaded.
    val viewModel = viewModel(key = "topic-$decodedTag-$id") {
        TopicViewModel(app.repository, decodedTag, id)
    }
    val detail = viewModel.detail
    val failure = viewModel.failure

    val topic = detail
    if (topic == null) {
        Scaffold(topBar = { SmallTopAppBar(title = "", navigationIcon = { BackIcon(navigator) }) }) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                if (failure == null) {
                    InfiniteProgressIndicator()
                } else {
                    EmptyState(
                        title = "加载失败",
                        subtitle = failure,
                        actionText = "重试",
                        onActionClick = { viewModel.retry() },
                    )
                }
            }
        }
        return
    }

    val title = topic.title
    val entityType = topic.entityType
    val isTopic = entityType == "topic"
    val allTabs = topic.list("tabList")
    // A product's first row is labelled 参数 but lists other 数码 entries — it is the related-topic
    // ranking, not the spec sheet the bar's 参数 entry opens. The bar carries it as 相关话题, so the
    // row itself is dropped here; a page that would be left with nothing keeps it.
    val relatedTab = if (entityType == "product" && allTabs.size > 1) {
        allTabs.firstOrNull { it.string("page_name") == "main" }
    } else {
        null
    }
    val tabList = if (relatedTab == null) allTabs else allTabs.filterNot { it === relatedTab }
    val selected = tabList.indexOfFirst { it.string("page_name") == topic.string("selectedTab") }
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Only the title row collapses. The limit is its own height, so the tab row stays pinned under
    // the status bar once the title has scrolled away, the way the home header behaves.
    val topRowHeightPx = with(density) { TopAppBarDefaults.CollapsedHeight.toPx() }
    val scrolledPx = remember { mutableFloatStateOf(0f) }
    val pageMemory = LocalPageMemory.current
    val barRestored = remember { mutableStateOf(false) }
    val scrollBehavior = rememberTopRowScrollBehavior(
        rememberTopAppBarState(initialHeightOffsetLimit = -topRowHeightPx),
        scrolledPx,
    )
    SideEffect {
        // The saved state keeps the limit it was created with, so a density change has to reapply it.
        scrollBehavior.state.heightOffsetLimit = -topRowHeightPx
        // A page rebuilt for a back gesture gets its saved bar state from nowhere, so the collapse
        // the user last saw comes from the entry instead. Both are set before the first layout,
        // otherwise the bar would be drawn expanded and snap shut on the next frame.
        if (!barRestored.value) {
            barRestored.value = true
            pageMemory?.let {
                scrollBehavior.state.heightOffset = it.barHeightOffset
                scrollBehavior.state.contentOffset = it.barContentOffset
            }
        }
    }
    if (pageMemory != null) {
        LaunchedEffect(scrollBehavior, pageMemory) {
            snapshotFlow { scrollBehavior.state.heightOffset to scrollBehavior.state.contentOffset }
                .collect { (height, content) ->
                    pageMemory.barHeightOffset = height
                    pageMemory.barContentOffset = content
                }
        }
    }
    val collapseFraction: () -> Float = remember(scrollBehavior, topRowHeightPx) {
        {
            if (topRowHeightPx > 0f) {
                (-scrollBehavior.state.heightOffset).coerceIn(0f, topRowHeightPx) / topRowHeightPx
            } else {
                0f
            }
        }
    }
    val backdrop = rememberGlassBackdrop()
    // Search runs in place, the way 首页's and 关注's do: the key in the title row stretches into the
    // field, the history drops in under it, and a keyword lists this topic's results there instead
    // of pushing the old search page. The scope is the page itself, as that page carried it.
    val search = rememberInPlaceSearchState(
        pageType = if (isTopic) "tag" else "product_phone",
        pageParam = if (isTopic) title else topic.string("id"),
        placeholder = "在 $title 中搜索",
    )
    val searchFade = search.searchFade
    val resultsFade = search.resultsFade
    val resultScrollState = search.scrollState
    val resultsShown = search.resultsShown
    // The tab row only fades once the field has fully taken over; its slot keeps its height, so the
    // bar and the list under it stay where they were.
    val segmentsGone by remember(searchFade) { derivedStateOf { searchFade.value >= 1f } }
    // True while the search is open and no page sits above this one. Back from the history closes
    // the search; the bar's own handlers run later and take the results and a pushed page first.
    // This page is itself a pushed route, so "above" means a deeper stack than the one it entered on
    // (or a revealed preview composition while an overlay route above it is being dragged away).
    val preview = LocalRouteTransition.current?.preview == true
    val entryDepth = remember { navigator.stack.size }
    val overlayAbove = preview || navigator.stack.size > entryDepth
    val searchActive = search.expanded && !overlayAbove
    BackHandler(enabled = searchActive) { search.close() }
    val pagerState = rememberPagerState(
        initialPage = pageMemoryPage(fallback = selected.coerceAtLeast(0)),
    ) { tabList.size }
    rememberPageMemoryRecorder(page = pagerState.currentPage)
    // The follow/block card belongs to the discussion list, not whichever tab happens to be first.
    val headerPage = tabList.indexOfFirst { it.title == "讨论" }.takeIf { it >= 0 } ?: 0

    var isFollow by remember(topic) { mutableStateOf(topic.obj("userAction")?.string("follow") == "1") }
    var followPending by remember(topic) { mutableStateOf(false) }
    var isBlocked by remember(topic) { mutableStateOf(app.blacklist.topicBlacklist.any { title.contains(it) }) }
    // The sort is a choice about this page, not a transient one: a post opened on top of the page
    // used to bring the discussion list back at its default order.
    var sortType by remember {
        mutableStateOf(pageMemory?.selections?.get(TOPIC_SORT_KEY) as? TopicSortType ?: TopicSortType.Default)
    }
    // Read here, in the composition, so this page is subscribed to the choice. The discussion list
    // is the only other reader, and it recomposes in its own scope: without this read the page
    // never recomposes when the sort changes, and the effect below keeps the old order on the entry.
    val recordedSort = sortType
    SideEffect { pageMemory?.selections?.put(TOPIC_SORT_KEY, recordedSort) }
    var menu by remember { mutableStateOf(false) }
    var showCompose by remember { mutableStateOf(false) }
    // A tab can carry a `sortSelectCard` whose options point at the same list with a different
    // `sortField`. Remember the chosen url per tab so the list reloads in place.
    val sortUrls = remember(topic) { mutableStateMapOf<Int, String>() }

    // 排序规则 shows the order the list on screen offers. The pager draws that list, and the bar
    // resolves the very same stream for the menu, so the two never disagree about the current order.
    val currentTab = tabList.getOrNull(pagerState.currentPage)
    val currentDiscuss = entityType == "product" && currentTab?.title == "讨论"
    val currentStream = if (currentTab != null && !isBlocked) {
        rememberTopicTabStream(
            page = pagerState.currentPage,
            tab = currentTab,
            topicId = topic.string("id"),
            discuss = currentDiscuss,
            sortType = sortType,
            sortUrls = sortUrls,
        )
    } else {
        null
    }
    val sortChoices: TopicSortChoices? = when {
        currentStream == null -> null
        // The discussion list's order lives in its url rather than in a card.
        currentDiscuss -> TopicSortChoices(
            labels = TopicSortType.entries.map { it.label },
            selected = sortType.label,
            pick = { index -> TopicSortType.entries.getOrNull(index)?.let { sortType = it } },
        )
        currentStream.sortOptions.isEmpty() -> null
        else -> {
            val options = currentStream.sortOptions
            TopicSortChoices(
                labels = options.map { it.title },
                selected = currentStream.selectedSort,
                pick = { index -> options.getOrNull(index)?.let(currentStream.applySort) },
            )
        }
    }
    // The search reuses this row for its content types when the bar carries one; a page with a
    // single tab has no slot, so its chips ride on the panel the way 关注's do.
    val hasTabSlot = !isBlocked && tabList.size > 1
    val shareUrl = Protocol.shareLink(if (isTopic) "t" else "product", if (isTopic) title else topic.string("id"))

    val comparisons by app.productComparison.selections.collectAsState()
    val selectedConfigIds: Set<String> = remember(comparisons) {
        comparisons.mapNotNullTo(linkedSetOf()) { it.config.id }
    }
    val productConfigs: List<ProductConfigInfo> = remember(topic, entityType) {
        if (entityType == "product") {
            topic.list("configRows").mapNotNull { ProductConfigInfo.from(it) }
        } else {
            emptyList()
        }
    }
    var productSheet by remember(topic) { mutableStateOf<ProductSheetState>(ProductSheetState.Hidden) }
    var productRequest by remember(topic) { mutableStateOf(0) }
    var productRetry by remember(topic) { mutableStateOf<(() -> Unit)?>(null) }

    fun dismissProductSheet() {
        productRequest++
        productSheet = ProductSheetState.Hidden
    }

    fun toggleProductConfig(config: ProductConfigInfo) {
        val result = app.productComparison.toggle(
            ProductComparisonSelection(
                productTitle = title,
                productLogo = topic.logo.ifEmpty { topic.pic }.takeIf { it.isNotEmpty() },
                config = config,
            ),
        )
        context.toast(
            when (result) {
                ToggleResult.ADDED -> "已加入对比"
                ToggleResult.REMOVED -> "已移出对比"
                ToggleResult.LIMIT_REACHED -> "最多选择 ${ProductComparisonStore.MAX_SELECTIONS} 个配置"
                ToggleResult.INVALID -> "该配置暂不支持对比"
            },
        )
    }

    fun loadProductConfigDetails(config: ProductConfigInfo) {
        val request = ++productRequest
        productSheet = ProductSheetState.Loading(config.title)
        productRetry = { loadProductConfigDetails(config) }
        scope.launch {
            try {
                val sections = productSpecSections(app.repository.productConfigInfo(config.id))
                if (request == productRequest) {
                    productSheet = ProductSheetState.Details(config.title, sections)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == productRequest) {
                    productSheet = ProductSheetState.Error("参数加载失败", ApiFailure.from(e).message)
                }
            }
        }
    }

    fun openProductComparison() {
        val selections = app.productComparison.selections.value
        if (selections.isEmpty()) {
            context.toast("请先选择要对比的配置")
            return
        }
        if (selections.size < 2) {
            context.toast("再选择一个配置即可对比")
            return
        }
        val request = ++productRequest
        productSheet = ProductSheetState.Loading("配置对比")
        productRetry = { openProductComparison() }
        scope.launch {
            try {
                val columns = coroutineScope {
                    selections.map { selection ->
                        async {
                            ProductComparisonColumn(
                                selection = selection,
                                sections = productSpecSections(
                                    app.repository.productConfigInfo(selection.config.id),
                                ),
                            )
                        }
                    }.map { it.await() }
                }
                if (request == productRequest) {
                    productSheet = ProductSheetState.Comparison(columns)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == productRequest) {
                    productSheet = ProductSheetState.Error("对比加载失败", ApiFailure.from(e).message)
                }
            }
        }
    }

    fun openProductConfigs() {
        if (productConfigs.isEmpty()) return
        productRequest++
        productSheet = ProductSheetState.Configs(title, productConfigs)
    }

    val productConfigAction: (() -> Unit)? =
        if (!isTopic && productConfigs.isNotEmpty()) ({ openProductConfigs() }) else null
    val productCompareAction: (() -> Unit)? =
        if (!isTopic && (productConfigs.isNotEmpty() || comparisons.isNotEmpty())) {
            ({ openProductComparison() })
        } else {
            null
        }
    // The related-topic ranking the bar offers under 相关话题. It is the row the tab strip dropped.
    val relatedUrl: String? = relatedTab?.string("url")?.takeIf { it.isNotBlank() }

    fun onFollowClick() {
        if (followPending) return
        if (!app.session.isLogin) {
            navigator.push(Route.Login)
            return
        }
        val followed = isFollow
        followPending = true
        scope.launch {
            try {
                val result = postFollow(app.repository, isTopic, title, topic.string("id"), followed)
                isFollow = !followed
                context.toast(result.message.ifEmpty { if (followed) "已取消关注" else "关注成功" })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toast(ApiFailure.from(e).message)
            } finally {
                followPending = false
            }
        }
    }

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                val barContainerColor = COUITheme.colorScheme.surface
                // The material is measured to the bar and draws its fade past it, the way the home
                // header does; only the title row collapses.
                val materialExtra = AppBarBlurOverlayDefaults.ExtraHeight
                val materialScrollFraction = remember(
                    scrolledPx,
                    scrollBehavior,
                    density,
                    searchFade,
                    resultsFade,
                    resultScrollState,
                ) {
                    {
                        // contentOffset lives in the bar's saved state, so a page rebuilt after a
                        // post detail closes still knows how far its list is scrolled and keeps the
                        // material on instead of dropping back to a bare tab row.
                        val scrolled = maxOf(
                            scrolledPx.floatValue,
                            abs(scrollBehavior.state.contentOffset),
                            // A collapsed bar means content is already under it, so the material must
                            // stay on even if the scroll counters were reset on the way back.
                            -scrollBehavior.state.heightOffset,
                        )
                        val ramp = AppBarBlurOverlayDefaults.scrollAlpha(scrolled, density)
                        val resultRamp = AppBarBlurOverlayDefaults.scrollAlpha(
                            abs(resultScrollState.contentOffset),
                            density,
                        )
                        // The history panel shares the bar's surface colour, so the material clears
                        // while it is open: no bar edge is left drawn across the panel. The results
                        // scroll under the bar like the stream does, so it returns with them.
                        //
                        // Both terms carry 首页's floor. The bar keeps a translucent sheet at rest
                        // instead of sitting bare on the list, and a page whose scroll counters were
                        // reset on the way back from a post still draws the same sheet from its first
                        // frame rather than dropping the material and fading it in.
                        (MATERIAL_MIN_ALPHA + (1f - MATERIAL_MIN_ALPHA) * ramp) * (1f - searchFade.value) +
                            (MATERIAL_MIN_ALPHA + (1f - MATERIAL_MIN_ALPHA) * resultRamp) * resultsFade.value
                    }
                }
                val blurEnabled = isBlurEnabled()
                // The results' chips ride in the tab slot of one two-row material, which reaches 6dp
                // past the bar instead of the single row's 8dp, the way 首页's bar does.
                val activeExtra = if (resultsShown) TwoRowTopAppBarDefaults.ExtraHeight else materialExtra
                val activeExtraPx = if (blurEnabled) with(density) { activeExtra.roundToPx() } else 0
                val activeFraction = remember(blurEnabled, materialScrollFraction) {
                    if (blurEnabled) materialScrollFraction else { { 0f } }
                }
                // The material is recorded into a layer of its own while the rows draw above it, so
                // the search's chips and field sample the frosted sheet instead of the flat panel
                // underneath — the same split 首页's bar makes.
                val barMaterialBackdrop = if (backdrop != null) {
                    rememberLayerBackdrop(upstream = backdrop) {
                        if (materialScrollFraction() < 0.999f) {
                            drawRect(barContainerColor)
                        }
                        drawContent()
                    }
                } else {
                    null
                }
                Layout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            appBarBounds.reportPage(
                                if (isTopic) Route.Topic(tag.orEmpty()) else Route.Product(id.orEmpty()),
                                it.positionInRoot().y + it.size.height,
                            )
                        },
                    content = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (barMaterialBackdrop != null) {
                                        Modifier.layerBackdrop(barMaterialBackdrop)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (blurEnabled) Modifier.padding(bottom = materialExtra) else Modifier),
                            ) {
                                AppBarBlurOverlay(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = barContainerColor,
                                    backdrop = backdrop,
                                    // The ColorOS gradient shape fades to nothing across the whole bar,
                                    // which leaves the row below the search field sharp and untinted.
                                    // COUI's own material keeps the bar's body blurred and tints the
                                    // band under it instead.
                                    platformRenderer = false,
                                    scrollFraction = materialScrollFraction,
                                    extraHeight = activeExtra,
                                ) {
                                    Spacer(Modifier.fillMaxSize())
                                }
                            }
                        }
                        // The rows draw above the material, never inside the layer it is recorded
                        // into: a chip sampling a layer it is drawn into reads it mid-record.
                        CompositionLocalProvider(LocalToolbarBackdropBlurFraction provides activeFraction) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(statusBar))
                                // The row keeps its full height and slides out of a box that shrinks
                                // with the scroll, so the tab row rises into its place instead of
                                // leaving a gap.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .layout { measurable, constraints ->
                                            val full = TopAppBarDefaults.CollapsedHeight.roundToPx()
                                            val placeable = measurable.measure(
                                                constraints.copy(minHeight = full, maxHeight = full),
                                            )
                                            val visible = (full * (1f - collapseFraction())).roundToInt()
                                                .coerceIn(0, full)
                                            layout(placeable.width, visible) { placeable.placeRelative(0, 0) }
                                        }
                                        .graphicsLayer { clip = collapseFraction() > 0f }
                                        // A circular key's halo reaches past the row's 6dp inset,
                                        // so the clip keeps a halo-wide margin on every side: the
                                        // glow stays drawn while the row slides out instead of
                                        // switching off at its first pixel. 首页's row cuts the same
                                        // margin.
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
                                            .height(TopAppBarDefaults.CollapsedHeight)
                                            .graphicsLayer {
                                                translationY = -topRowHeightPx * collapseFraction()
                                                alpha = 1f - collapseFraction()
                                                // The key and the title barely overlap, so the fade
                                                // lands on each draw instead of an offscreen copy of
                                                // the row — the strategy 首页's row uses.
                                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.fillMaxWidth()) {
                                            InPlaceSearchBar(
                                                state = search,
                                                // While the results are on screen the bar's own material
                                                // covers them, so the field frosts that sheet.
                                                backdrop = if (resultsShown) {
                                                    barMaterialBackdrop ?: backdrop
                                                } else {
                                                    backdrop
                                                },
                                                // A blocked page, or one with nothing to list, has
                                                // nothing to search: the key goes, the title stays.
                                                showSearchButton = !isBlocked && tabList.isNotEmpty(),
                                                overlayAbove = overlayAbove,
                                                // The key sits between the title and this menu at rest, and
                                                // the menu leaves with it once the field is over it.
                                                extraActions = {
                                                    Box {
                                                        TopBarIconButton(onClick = { menu = true }) {
                                                            Icon(COUIIcons.More, contentDescription = "菜单")
                                                        }
                                                        // The order this list offers runs one level deeper,
                                                        // in the same cascading submenu the search's 内容类型
                                                        // uses: the row carries a chevron and opens its
                                                        // choices beside the menu.
                                                        val choices = sortChoices
                                                        val menuEntries = listOf(
                                                            DropdownEntry(
                                                                items = buildList {
                                                                    add(
                                                                        DropdownItem(
                                                                            text = "排序规则",
                                                                            enabled = choices != null,
                                                                            children = choices?.labels?.mapIndexed { index, label ->
                                                                                DropdownItem(
                                                                                    text = label,
                                                                                    selected = choices.selected == label,
                                                                                    onClick = { choices.pick(index) },
                                                                                )
                                                                            },
                                                                        ),
                                                                    )
                                                                    productConfigAction?.let { action ->
                                                                        add(
                                                                            DropdownItem(
                                                                                text = "参数（${productConfigs.size}）",
                                                                                onClick = action,
                                                                            ),
                                                                        )
                                                                    }
                                                                    productCompareAction?.let { action ->
                                                                        add(
                                                                            DropdownItem(
                                                                                text = "对比（${comparisons.size}）",
                                                                                onClick = action,
                                                                            ),
                                                                        )
                                                                    }
                                                                    relatedUrl?.let { url ->
                                                                        add(
                                                                            DropdownItem(
                                                                                text = "相关话题",
                                                                                onClick = {
                                                                                    navigator.push(Route.RelatedTopics(url))
                                                                                },
                                                                            ),
                                                                        )
                                                                    }
                                                                    add(
                                                                        DropdownItem(
                                                                            text = "复制",
                                                                            onClick = { context.copyText(shareUrl) },
                                                                        ),
                                                                    )
                                                                    add(
                                                                        DropdownItem(
                                                                            text = "分享",
                                                                            onClick = { context.shareText(shareUrl) },
                                                                        ),
                                                                    )
                                                                    add(
                                                                        DropdownItem(
                                                                            text = if (isFollow) "取消关注" else "关注",
                                                                            onClick = { onFollowClick() },
                                                                        ),
                                                                    )
                                                                    add(
                                                                        DropdownItem(
                                                                            text = if (isBlocked) "取消屏蔽" else "屏蔽",
                                                                            onClick = {
                                                                                if (isBlocked) {
                                                                                    app.blacklist.unblockTopic(title)
                                                                                } else {
                                                                                    app.blacklist.blockTopic(title)
                                                                                }
                                                                                isBlocked = !isBlocked
                                                                            },
                                                                        ),
                                                                    )
                                                                },
                                                            ),
                                                        )
                                                        OverlayCascadingListPopup(
                                                            show = menu,
                                                            entries = menuEntries,
                                                            onDismissRequest = { menu = false },
                                                            alignment = PopupPositionProvider.Align.End,
                                                        )
                                                    }
                                                },
                                            )
                                            // 首页's bar carries no title of its own: the avatar is
                                            // laid over the empty title slot instead, and leaves with the
                                            // field. The back key and the name ride there the same way, so
                                            // both bars lay out through one component with one set of
                                            // parameters.
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = !search.expanded,
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = TOP_BAR_ACTIONS_INSET),
                                                enter = fadeIn(tween(120)),
                                                exit = fadeOut(tween(120)),
                                            ) {
                                                // The bar runs in the system-app density; the title keeps
                                                // the page's own scale, so it stays the size the row drew
                                                // before.
                                                CompositionLocalProvider(LocalDensity provides density) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        BackIcon(navigator)
                                                        Text(
                                                            text = title,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .padding(
                                                                    start = TopAppBarDefaults.NavigationIconGap,
                                                                ),
                                                            fontSize = with(density) { AppBarTitleSize.toSp() },
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (hasTabSlot) {
                                    // The slot keeps its height whether the tabs, nothing or the
                                    // search's content types are in it, so the bar and the pages
                                    // under it never move.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(
                                                TabRowDefaults.TabRowWithContourHeight +
                                                    TAB_ROW_VERTICAL_PADDING * 2,
                                            ),
                                    ) {
                                        if (!segmentsGone) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .graphicsLayer { alpha = 1f - searchFade.value }
                                                    .padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(vertical = TAB_ROW_VERTICAL_PADDING)
                                                        .height(TabRowDefaults.TabRowWithContourHeight),
                                                ) {
                                                    // 首页's capsule, 参数 and all: the segments give up
                                                    // the same width and padding there, and the
                                                    // translucent COUI fill samples the list under the
                                                    // bar instead of an opaque segment colour.
                                                    TabRowWithContour(
                                                        tabs = tabList.map { it.title },
                                                        selectedTabIndex = pagerState.currentPage,
                                                        onTabSelected = {
                                                            scope.launch { pagerState.animateScrollToPage(it) }
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        minWidth = 44.dp,
                                                        segmentPadding = 8.dp,
                                                        backdrop = backdrop,
                                                    )
                                                }
                                            }
                                        }
                                        // While results show, the slot carries the search's content
                                        // types instead: the one 动态 chip and the grid key that
                                        // opens its filters.
                                        if (resultsShown) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer { alpha = resultsFade.value },
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                SearchResultTabs(
                                                    tabs = search.tabs,
                                                    selectedIndex = search.pagerState.currentPage,
                                                    onSelectedChange = {
                                                        scope.launch { search.pagerState.animateScrollToPage(it) }
                                                    },
                                                    searchType = search.searchType,
                                                    onSearchType = { search.searchType = it },
                                                    sortType = search.sortType,
                                                    onSortType = { search.sortType = it },
                                                    backdrop = barMaterialBackdrop ?: backdrop,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                ) { measurables, constraints ->
                    val rows = measurables[1].measure(constraints.copy(minHeight = 0))
                    val material = measurables[0].measure(
                        Constraints.fixed(width = rows.width, height = rows.height + activeExtraPx),
                    )
                    layout(rows.width, rows.height) {
                        material.placeRelative(0, 0)
                        rows.placeRelative(0, 0)
                    }
                }
            }
        },
        floatingActionButton = {
            AppFloatingActionButton(
                // The search takes the page over: the compose key belongs to the list it covers.
                visible = app.session.isLogin && !isBlocked && !search.expanded,
                backdrop = backdrop,
                onClick = { showCompose = true },
                icon = COUIIcons.Add,
                contentDescription = "发布动态",
            )
        },
    ) { innerPadding ->
        if (isBlocked) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("已屏蔽 $title")
            }
            return@Scaffold
        }
        if (tabList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(title = "暂无内容")
            }
            return@Scaffold
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            // The streams and the search panel share one glass layer, the way 首页's do: while the
            // search is open the panel is what the bar's field sees, so it has to be the content
            // that layer records instead of the list the panel covers.
            Box(Modifier.fillMaxSize().glassSource(backdrop)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val tab = tabList[page]
                    val discuss = entityType == "product" && tab.title == "讨论"
                    val stream = rememberTopicTabStream(
                        page = page,
                        tab = tab,
                        topicId = topic.string("id"),
                        discuss = discuss,
                        sortType = sortType,
                        sortUrls = sortUrls,
                    )
                    val padding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 12.dp,
                    )
                    if (page == headerPage) {
                        TopicFeedList(
                            viewModel = stream.viewModel,
                            scrollBehavior = scrollBehavior,
                            contentPadding = padding,
                        ) {
                            TopicHeader(
                                topic = topic,
                                isFollow = isFollow,
                                onFollow = { onFollowClick() },
                            )
                        }
                    } else {
                        // This tab's order is offered from the bar's 排序规则 menu, not as a card.
                        FeedList(
                            viewModel = stream.viewModel,
                            scrollBehavior = scrollBehavior,
                            contentPadding = padding,
                            showSortCard = false,
                        )
                    }
                }
                // The history drops in under the expanded field and covers the streams, the way the
                // old search page did; picking a keyword opens its results on top.
                InPlaceSearchPanel(
                    state = search,
                    // Straight under the search field: the tab slot above is empty while searching.
                    historyTop = statusBar + TopAppBarDefaults.CollapsedHeight,
                    resultContentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() +
                            (if (hasTabSlot) 0.dp else SEARCH_CHIP_GAP + SEARCH_CHIP_HEIGHT) + 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 12.dp,
                    ),
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    topChromeHeight = if (hasTabSlot) 0.dp else SEARCH_CHIP_GAP + SEARCH_CHIP_HEIGHT,
                    // A page with no tab slot carries the content types on the panel itself: the
                    // results start below them and scroll out from under their band.
                    topChrome = if (hasTabSlot) {
                        null
                    } else {
                        ({ rowBackdrop ->
                            SearchResultTabs(
                                modifier = Modifier.padding(top = SEARCH_CHIP_GAP),
                                tabs = search.tabs,
                                selectedIndex = search.pagerState.currentPage,
                                onSelectedChange = {
                                    scope.launch { search.pagerState.animateScrollToPage(it) }
                                },
                                searchType = search.searchType,
                                onSearchType = { search.searchType = it },
                                sortType = search.sortType,
                                onSortType = { search.sortType = it },
                                // The panel records the results into a layer of their own: the row is
                                // drawn above it, so a chip frosts what scrolls behind its row.
                                backdrop = rowBackdrop,
                            )
                        })
                    },
                )
            }
        }
    }

    if (showCompose) {
        ReplyComposerSheet(
            target = ReplyTarget(
                targetType = if (isTopic) "tag" else "product_phone",
                targetId = topic.string("id"),
                title = title,
            ),
            onDismiss = { showCompose = false },
            onPosted = { showCompose = false },
        )
    }

    ProductConfigSheet(
        state = productSheet,
        selectedIds = selectedConfigIds,
        onDismiss = { dismissProductSheet() },
        onViewConfig = { loadProductConfigDetails(it) },
        onToggleCompare = { toggleProductConfig(it) },
        onOpenComparison = { openProductComparison() },
        onRetry = { productRetry?.invoke() },
        onRemoveComparison = { configId ->
            app.productComparison.remove(configId)
            val current = productSheet
            if (current is ProductSheetState.Comparison) {
                val remaining = current.columns.filterNot { it.selection.config.id == configId }
                productSheet = if (remaining.isEmpty()) {
                    ProductSheetState.Hidden
                } else {
                    ProductSheetState.Comparison(remaining)
                }
            }
        },
    )
}

private suspend fun postFollow(
    repository: CoolapkRepository,
    isTopic: Boolean,
    tag: String,
    id: String,
    followed: Boolean,
): ActionResult =
    if (isTopic) {
        repository.followTag(tag, followed)
    } else {
        repository.changeProductFollow(id, followed)
    }

@Composable
private fun BackIcon(navigator: Navigator) {
    TopBarIconButton(onClick = { navigator.pop() }) {
        Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
    }
}

/** A sort card option wraps its list as `/page?url=<encoded>`; the list wants the inner url. */
private fun sortOptionUrl(raw: String): String {
    val prefix = "/page?url="
    return if (raw.startsWith(prefix)) Uri.decode(raw.removePrefix(prefix)) else raw
}

private fun discussUrl(id: String, sort: TopicSortType) = when (sort) {
    TopicSortType.Default -> "/page?url=/product/feedList?type=feed&id=$id&ignoreEntityById=1"
    TopicSortType.Dateline -> "/page?url=/product/feedList?type=feed&id=$id&ignoreEntityById=1&listType=dateline_desc"
}

private fun discussTitle(sort: TopicSortType) = sort.label

/**
 * One tab's stream, and the way another order of it reloads in place.
 *
 * The pager page draws it, and the bar's 排序规则 menu resolves the very same list through the same
 * view model key, so the menu reads the orders that page offers instead of a stale copy.
 */
private class TopicTabStream(
    val viewModel: FeedListViewModel,
    /** The `sortSelectCard`'s choices; empty when this tab's list came without one. */
    val sortOptions: List<Entity>,
    val selectedSort: String?,
    val applySort: (Entity) -> Unit,
)

@Composable
private fun rememberTopicTabStream(
    page: Int,
    tab: Entity,
    topicId: String,
    discuss: Boolean,
    sortType: TopicSortType,
    sortUrls: MutableMap<Int, String>,
): TopicTabStream {
    val app = LocalApp.current
    val url = if (discuss) discussUrl(topicId, sortType) else sortUrls[page] ?: tab.url
    val listTitle = if (discuss) discussTitle(sortType) else tab.title
    // A topic tab keeps one view model while a sort option swaps its order, so the card survives
    // the reload instead of coming back only if the server sends it again.
    val viewModelKey = if (discuss) "topic-$url-$listTitle" else "topic-tab-$page"
    val viewModel = viewModel(key = viewModelKey) {
        FeedListViewModel(
            PageLoader { p, first, last -> app.repository.dataList(url, listTitle, p, first, last) },
            app::contentGate,
        )
    }
    val applySort: (Entity) -> Unit = { option ->
        val newUrl = sortOptionUrl(option.url)
        sortUrls[page] = newUrl
        viewModel.selectSort(option.title)
        viewModel.switchList(
            PageLoader { p, first, last -> app.repository.dataList(newUrl, listTitle, p, first, last) },
        )
    }
    return TopicTabStream(
        viewModel = viewModel,
        sortOptions = viewModel.sortCard?.entities.orEmpty(),
        selectedSort = viewModel.selectedSort,
        applySort = applySort,
    )
}

@Composable
private fun TopicHeader(
    topic: Entity,
    isFollow: Boolean,
    onFollow: () -> Unit,
) {
    // The API scores products and rated topics out of 10; the header shows the five-star scale.
    val score = topic.double("star_average_score")
        ?.takeIf { it > 0.0 }
        ?.div(2.0)
        ?.let { String.format(Locale.US, "%.1f/5.0", it) }
    val followers = topic.followNum
    // Both states of the follow key read as three characters — the plus and 关注 before following,
    // the three-character 已关注 after it — so the key is one box either way and the rating under it
    // lines up with both. The width is measured from the button style instead of fixed, so a raised
    // font scale widens the key rather than squeezing 已关注 down to 已关.
    val followKeyWidth = threeCharacterKeyWidth()
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            NetworkImage(
                topic.logo.ifEmpty { topic.pic },
                Modifier.size(56.dp).clip(rememberSquircleShape(14.dp)),
                contentDescription = topic.title,
            )
            Spacer(Modifier.width(12.dp))
            // The name starts at the picture's top edge and the follower count ends at its bottom
            // one, so the lines frame the picture instead of sitting in its middle. A name too long
            // for one line wraps at a word boundary and pushes the count down with it — the card
            // grows instead of cutting the name with an ellipsis. The rating rides under the follow
            // key, so the count never has to share the picture's height with a third line.
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        topic.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FollowAction(
                        isFollow = isFollow,
                        onClick = onFollow,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .width(followKeyWidth),
                    )
                }
                // The count keeps the picture's bottom edge, so the rating sits on that same line at
                // the key's end — under the key, and as wide as it — instead of adding a third line
                // that would push the count past the picture.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (followers.isNotEmpty()) {
                        Text(
                            "${followers}人关注",
                            modifier = Modifier.weight(1f),
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (score != null) {
                        Row(
                            modifier = Modifier.width(followKeyWidth),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                AppIcons.StarFilled,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                score,
                                style = COUITheme.textStyles.footnote1,
                                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The width of a key whose label is three characters of the button style: the small tier's inside
 * margin plus the measured glyph advance of one character times three. Measuring instead of fixing
 * a dp value is what keeps 已关注 whole when the system font scale goes up.
 */
@Composable
private fun threeCharacterKeyWidth(): Dp {
    val textMeasurer = rememberTextMeasurer()
    val keyStyle = COUITheme.textStyles.button
    val charWidth = textMeasurer.measure("国", keyStyle).size.width
    val inside = ButtonDefaults.InsideMarginSmall
    val insideWidth = inside.calculateLeftPadding(LayoutDirection.Ltr) +
        inside.calculateRightPadding(LayoutDirection.Ltr)
    return with(LocalDensity.current) {
        (charWidth * 3).toDp() + insideWidth + KEY_SIDE_SLACK
    }
}

/**
 * The header's one follow key: a blue plus and 关注 while this account does not follow the topic, and
 * a muted 已关注 once it does. Both keep a capsule outline of their own colour, so the key reads as a
 * button next to the name instead of as a stray line of text, and both are three characters wide so
 * switching state moves nothing.
 */
@Composable
private fun FollowAction(
    isFollow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The library's own two fills: the accent one before following, the plain secondary one after.
    // Both are the small tier of the same Button, so the key is an ordinary button in either state
    // and the header never has to draw a rim of its own.
    val colors = if (isFollow) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColorsPrimary()
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        size = ButtonSize.Small,
    ) {
        // The plus stands in for one of the three characters, so it is sized to a glyph's advance
        // and the pair of them fills the key exactly as 已关注 does.
        if (!isFollow) {
            Icon(
                COUIIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            if (isFollow) "已关注" else "关注",
            style = COUITheme.textStyles.button,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** [FeedList] with a header item, which the shared list has no slot for. */
@Composable
private fun TopicFeedList(
    viewModel: FeedListViewModel,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberPageListState(viewModel)

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

    AppPullToRefresh(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = viewModel::refresh,
        contentPadding = contentPadding,
        topAppBarScrollBehavior = scrollBehavior,
    ) {
        CompositionLocalProvider(LocalFeedListState provides listState) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(contentType = "header") { header() }
                when (val status = viewModel.status) {
                    ListStatus.Loading -> item(contentType = "state") { StateBox { InfiniteProgressIndicator() } }
                    ListStatus.Empty -> item(contentType = "state") {
                        StateBox {
                            EmptyState(title = "暂无内容", actionText = "刷新", onActionClick = viewModel::refresh)
                        }
                    }
                    is ListStatus.Error -> item(contentType = "state") {
                        StateBox {
                            EmptyState(
                                title = "加载失败",
                                subtitle = status.message,
                                actionText = "重试",
                                onActionClick = viewModel::refresh,
                            )
                        }
                    }
                    ListStatus.Content -> {
                        itemsIndexed(
                            viewModel.items.filterNot(FeedActions::isHidden),
                            contentType = { _, item -> item.entityTemplate.ifEmpty { item.entityType } },
                        ) { _, item -> EntityItem(item) }
                        item(contentType = "footer") { TopicFooter(viewModel) }
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
private fun TopicFooter(viewModel: FeedListViewModel) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (val status = viewModel.footer) {
            FooterStatus.Idle, FooterStatus.Loading -> InfiniteProgressIndicator()
            FooterStatus.End -> Text(
                "没有更多了",
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            is FooterStatus.Filtered -> TextButton(
                text = "已过滤 ${status.count} 条，点击继续加载",
                onClick = viewModel::continueFiltered,
            )
            is FooterStatus.Error -> TextButton(
                text = "${status.message}，点击重试",
                onClick = viewModel::retryFooter,
            )
        }
    }
}
