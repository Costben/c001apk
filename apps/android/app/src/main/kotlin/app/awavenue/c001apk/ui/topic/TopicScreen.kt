package app.awavenue.c001apk.ui.topic

import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import app.awavenue.c001apk.ui.common.AppFloatingActionButton
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.appTabRowColors
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberTopRowScrollBehavior
import app.awavenue.c001apk.ui.common.shareText
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.EntityItem
import app.awavenue.c001apk.ui.feed.FeedActions
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ItemPadding
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.feed.SortSelectCard
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.nav.LocalAppBarBounds
import app.awavenue.c001apk.ui.nav.LocalFeedListState
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.LocalPageMemory
import app.awavenue.c001apk.ui.nav.Navigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
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

/** Page-memory key for the discussion sort the user picked on a product page. */
private const val TOPIC_SORT_KEY = "topic-sort"

private enum class TopicSortType(val label: String) {
    Default("默认"),
    Dateline("最新发布"),
    Hot("热门"),
}

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
        Scaffold(topBar = { TopAppBar(title = "", navigationIcon = { BackIcon(navigator) }) }) { innerPadding ->
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
    val tabList = topic.list("tabList")
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
    var showSort by remember { mutableStateOf(false) }
    var showCompose by remember { mutableStateOf(false) }
    // A tab can carry a `sortSelectCard` whose options point at the same list with a different
    // `sortField`. Remember the chosen url per tab so the list reloads in place.
    val sortUrls = remember(topic) { mutableStateMapOf<Int, String>() }

    val sortable = entityType == "product" && tabList.getOrNull(pagerState.currentPage)?.title == "讨论"
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
                // The material is measured to the bar and draws its fade past it, the way the home
                // header does; only the title row collapses.
                val barHeightDp = statusBar + TopAppBarDefaults.CollapsedHeight +
                    TabRowDefaults.TabRowWithContourHeight + TAB_ROW_VERTICAL_PADDING * 2
                val materialExtra = AppBarBlurOverlayDefaults.ExtraHeight
                val materialScrollFraction = remember(scrolledPx, scrollBehavior, density) {
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
                        AppBarBlurOverlayDefaults.scrollAlpha(scrolled, density)
                    }
                }
                AppBarBlurOverlay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            appBarBounds.reportPage(
                                if (isTopic) Route.Topic(tag.orEmpty()) else Route.Product(id.orEmpty()),
                                it.positionInRoot().y + it.size.height,
                            )
                        },
                    containerColor = COUITheme.colorScheme.surface,
                    backdrop = backdrop,
                    scrollFraction = materialScrollFraction,
                    extraHeight = materialExtra,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(statusBar))
                        // The row keeps its full height and slides out of a box that shrinks with
                        // the scroll, so the tab row rises into its place instead of leaving a gap.
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
                                .graphicsLayer { clip = collapseFraction() > 0f },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(TopAppBarDefaults.CollapsedHeight)
                                    .graphicsLayer {
                                        translationY = -topRowHeightPx * collapseFraction()
                                        alpha = 1f - collapseFraction()
                                    }
                                    .padding(start = 16.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BackIcon(navigator)
                                Text(
                                    text = title,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = TopAppBarDefaults.NavigationIconGap),
                                    fontSize = with(density) { AppBarTitleSize.toSp() },
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!isBlocked) {
                                    TopBarIconButton(onClick = {
                                        navigator.push(
                                            Route.Search(
                                                title = title,
                                                pageType = if (isTopic) "tag" else "product_phone",
                                                pageParam = if (isTopic) title else topic.string("id"),
                                            ),
                                        )
                                    }) {
                                        Icon(COUIIcons.Search, contentDescription = "搜索")
                                    }
                                }
                                Box(
                                    modifier = Modifier.padding(
                                        start = TopAppBarDefaults.ActionMenuItemSpacing,
                                    ),
                                ) {
                                    TopBarIconButton(onClick = { menu = true }) {
                                        Icon(COUIIcons.More, contentDescription = "菜单")
                                    }
                                    OverlayListPopup(
                                        show = menu,
                                        alignment = PopupPositionProvider.Align.End,
                                        onDismissRequest = { menu = false },
                                    ) {
                                        ListPopupColumn {
                                            val entries = buildList {
                                                add("复制" to { context.copyText(shareUrl) })
                                                add("分享" to { context.shareText(shareUrl) })
                                                if (sortable) add("排序" to { showSort = true })
                                                add((if (isFollow) "取消关注" else "关注") to { onFollowClick() })
                                                add(
                                                    (if (isBlocked) "取消屏蔽" else "屏蔽") to {
                                                        if (isBlocked) {
                                                            app.blacklist.unblockTopic(title)
                                                        } else {
                                                            app.blacklist.blockTopic(title)
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
                                                    groupKey = "topic-menu",
                                                    role = Role.Button,
                                                    onSelectedIndexChange = {
                                                        menu = false
                                                        entry.second()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    OverlayListPopup(
                                        show = showSort,
                                        alignment = PopupPositionProvider.Align.BottomEnd,
                                        onDismissRequest = { showSort = false },
                                    ) {
                                        ListPopupColumn {
                                            TopicSortType.entries.forEachIndexed { index, item ->
                                                DropdownImpl(
                                                    item = DropdownItem(text = item.label),
                                                    optionSize = TopicSortType.entries.size,
                                                    isSelected = sortType == item,
                                                    index = index,
                                                    groupKey = "topic-sort",
                                                    onSelectedIndexChange = {
                                                        showSort = false
                                                        sortType = TopicSortType.entries[it]
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!isBlocked && tabList.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = TAB_ROW_VERTICAL_PADDING)
                                        .height(TabRowDefaults.TabRowWithContourHeight),
                                ) {
                                    TabRowWithContour(
                                        tabs = tabList.map { it.title },
                                        selectedTabIndex = pagerState.currentPage,
                                        onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = appTabRowColors(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            AppFloatingActionButton(
                visible = app.session.isLogin && !isBlocked,
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().glassSource(backdrop),
                beyondViewportPageCount = 1,
            ) { page ->
                val tab = tabList[page]
                val discuss = entityType == "product" && tab.title == "讨论"
                val url = if (discuss) discussUrl(topic.string("id"), sortType) else sortUrls[page] ?: tab.url
                val listTitle = if (discuss) discussTitle(sortType) else tab.title
                // A topic tab keeps one view model while the sort card swaps its order, so the card
                // survives the reload instead of coming back only if the server sends it again.
                val viewModelKey = if (discuss) "topic-$url-$listTitle" else "topic-tab-$page"
                val viewModel = viewModel(key = viewModelKey) {
                    FeedListViewModel(
                        PageLoader { p, first, last -> app.repository.dataList(url, listTitle, p, first, last) },
                    )
                }
                val onSortSelected: ((Entity) -> Unit)? = if (discuss) {
                    null
                } else {
                    { option ->
                        val newUrl = sortOptionUrl(option.url)
                        sortUrls[page] = newUrl
                        viewModel.selectSort(option.title)
                        viewModel.switchList(
                            PageLoader { p, first, last ->
                                app.repository.dataList(newUrl, listTitle, p, first, last)
                            },
                        )
                    }
                }
                val padding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 12.dp,
                )
                if (page == headerPage) {
                    TopicFeedList(
                        viewModel = viewModel,
                        scrollBehavior = scrollBehavior,
                        contentPadding = padding,
                        onSortSelected = onSortSelected,
                    ) {
                        TopicHeader(
                            topic = topic,
                            isFollow = isFollow,
                            isBlocked = isBlocked,
                            onFollow = { onFollowClick() },
                            onBlock = {
                                if (isBlocked) {
                                    app.blacklist.unblockTopic(title)
                                } else {
                                    app.blacklist.blockTopic(title)
                                }
                                isBlocked = !isBlocked
                            },
                            productConfigCount = productConfigs.size,
                            comparisonCount = comparisons.size,
                            onProductConfigs = productConfigAction,
                            onProductComparison = productCompareAction,
                        )
                    }
                } else {
                    FeedList(
                        viewModel = viewModel,
                        scrollBehavior = scrollBehavior,
                        contentPadding = padding,
                        onSortSelected = onSortSelected,
                    )
                }
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
    TopicSortType.Hot -> "/page?url=/product/feedList?type=feed&id=$id&listType=rank_score"
}

private fun discussTitle(sort: TopicSortType) = when (sort) {
    TopicSortType.Default -> "默认"
    TopicSortType.Dateline -> "最新"
    TopicSortType.Hot -> "热度"
}

@Composable
private fun TopicHeader(
    topic: Entity,
    isFollow: Boolean,
    isBlocked: Boolean,
    onFollow: () -> Unit,
    onBlock: () -> Unit,
    productConfigCount: Int = 0,
    comparisonCount: Int = 0,
    onProductConfigs: (() -> Unit)? = null,
    onProductComparison: (() -> Unit)? = null,
) {
    // The API scores products out of 10; the header shows the five-star scale.
    val score = if (topic.entityType == "product") {
        topic.double("star_average_score")
            ?.takeIf { it > 0.0 }
            ?.div(2.0)
            ?.let { String.format(Locale.US, "%.1f/5.0", it) }
    } else {
        null
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = if (score != null) Alignment.Top else Alignment.CenterVertically) {
            NetworkImage(
                topic.logo.ifEmpty { topic.pic },
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                contentDescription = topic.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .then(if (score != null) Modifier.height(56.dp) else Modifier),
                verticalArrangement = if (score != null) Arrangement.SpaceBetween else Arrangement.Top,
            ) {
                Column {
                    Text(
                        topic.title,
                        maxLines = if (score != null) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (topic.followNum.isNotEmpty()) {
                        Text(
                            "${topic.followNum}人关注",
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (score != null) {
                    Text(
                        score,
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                text = if (isFollow) "取消关注" else "关注",
                onClick = onFollow,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = if (isBlocked) "取消屏蔽" else "屏蔽",
                onClick = onBlock,
                modifier = Modifier.weight(1f),
            )
        }
        if (onProductConfigs != null || onProductComparison != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                onProductConfigs?.let {
                    TextButton(
                        text = "参数（$productConfigCount）",
                        onClick = it,
                        modifier = Modifier.weight(1f),
                    )
                }
                onProductComparison?.let {
                    TextButton(
                        text = "对比（$comparisonCount）",
                        onClick = it,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** [FeedList] with a header item, which the shared list has no slot for. */
@Composable
private fun TopicFeedList(
    viewModel: FeedListViewModel,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    onSortSelected: ((Entity) -> Unit)? = null,
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
                viewModel.sortCard?.let { card ->
                    item(contentType = "sortCard") {
                        SortSelectCard(
                            card,
                            Modifier.padding(horizontal = ItemPadding),
                            viewModel.selectedSort,
                        ) { option -> onSortSelected?.invoke(option) }
                    }
                }
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
            is FooterStatus.Error -> TextButton(
                text = "${status.message}，点击重试",
                onClick = viewModel::retryFooter,
            )
        }
    }
}
