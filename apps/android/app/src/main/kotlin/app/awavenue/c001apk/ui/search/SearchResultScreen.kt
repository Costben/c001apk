package app.awavenue.c001apk.ui.search

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppBarTitleSize
import app.awavenue.c001apk.ui.common.appTabRowColors
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.LocalPageMemory
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.MaterialStrokeParams
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class SearchContentType(val label: String, val type: String) {
    Feed("动态", "feed"),
    App("应用", "apk"),
    Game("游戏", "game"),
    Topic("话题", "feedTopic"),
    Product("数码", "product"),
    User("用户", "user"),
}

private enum class SearchType(val label: String, val feedType: String) {
    All("全部", "all"),
    Feed("动态", "feed"),
    Article("文章", "feedArticle"),
    CoolPic("酷图", "picture"),
    Comment("评论", "comment"),
    Rating("评分", "rating"),
    Answer("回答", "answer"),
    Question("问答", "question"),
    Vote("投票", "vote"),
}

private enum class SearchSortType(val label: String, val sort: String, val isStrict: Int) {
    Dateline("最新发布", "dateline", 0),
    Default("默认", "none", 0),
    Hot("热门", "hot", 0),
    Reply("最新回复", "reply", 0),
    Strict("精确匹配", "", 1),
}

/**
 * The live query, the content type and the order the page is showing, kept on the back stack entry.
 *
 * The route only carries the keyword the search was opened with, and the page is disposed while a
 * result sits on top of it. Coming back therefore rebuilt it from that keyword, so an in-place
 * refinement ("APK" → "APK微博") fell back to the first search. These are choices about this page,
 * not transient ones, so they live in the entry's page memory like a topic's sort order.
 */
private const val SEARCH_QUERY_KEY = "search-query"
private const val SEARCH_CONTENT_TYPE_KEY = "search-content-type"
private const val SEARCH_SORT_KEY = "search-sort"

@Composable
fun SearchResultScreen(keyword: String, title: String, pageType: String?, pageParam: String?) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val pageMemory = LocalPageMemory.current
    val tabs = if (title.isEmpty()) SearchContentType.entries else listOf(SearchContentType.Feed)
    val pagerState = rememberPagerState(
        initialPage = pageMemoryPage("search-result", fallback = 0),
    ) { tabs.size }
    rememberPageMemoryRecorder("search-result", pagerState.currentPage)
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberPageScrollBehavior("search-result")
    val backdrop = rememberGlassBackdrop()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // The bar's title is the live query: editing it and submitting re-runs the search in place,
    // keeping the tab, the content type and the order the user already chose. The field keeps its
    // own draft so typing does not reload the list on every keystroke.
    var query by remember(keyword) {
        mutableStateOf(pageMemory?.selections?.get(SEARCH_QUERY_KEY) as? String ?: keyword)
    }
    var draft by remember(keyword) { mutableStateOf(query) }
    // Read in the composition so this page is subscribed to its own choices; the list reads them
    // too and would otherwise keep the ones it had when the page was last built.
    val recordedQuery = query

    fun submit(text: String) {
        val next = text.trim()
        if (next.isEmpty()) return
        focusManager.clearFocus()
        keyboard?.hide()
        if (next == query) return
        app.searchHistory.add(next)
        query = next
    }

    var searchType by remember {
        mutableStateOf(
            pageMemory?.selections?.get(SEARCH_CONTENT_TYPE_KEY) as? SearchType ?: SearchType.All,
        )
    }
    var sortType by remember {
        mutableStateOf(
            pageMemory?.selections?.get(SEARCH_SORT_KEY) as? SearchSortType ?: SearchSortType.Dateline,
        )
    }
    SideEffect {
        pageMemory?.selections?.put(SEARCH_QUERY_KEY, recordedQuery)
        pageMemory?.selections?.put(SEARCH_CONTENT_TYPE_KEY, searchType)
        pageMemory?.selections?.put(SEARCH_SORT_KEY, sortType)
    }
    var menuOpen by remember { mutableStateOf(false) }
    // Each overflow row owns its options as `children`, so the library shrinks the primary panel
    // and morphs a second panel out of the row, matching the native ColorOS cascade.
    val menuEntries = remember(searchType, sortType) {
        listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = "内容类型",
                        children = SearchType.entries.map { type ->
                            DropdownItem(
                                text = type.label,
                                selected = searchType == type,
                                onClick = { searchType = type },
                            )
                        },
                    ),
                    DropdownItem(
                        text = "排序方式",
                        children = SearchSortType.entries.map { sort ->
                            DropdownItem(
                                text = sort.label,
                                selected = sortType == sort,
                                onClick = { sortType = sort },
                            )
                        },
                    ),
                ),
            ),
        )
    }

    // The bar never collapses; pinning the range keeps the nested-scroll connection from eating
    // the list's drags for a height change this page does not draw.
    SideEffect { scrollBehavior.state.heightOffsetLimit = 0f }

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val density = LocalDensity.current
                val materialScrollFraction = remember(scrollBehavior, density) {
                    {
                        AppBarBlurOverlayDefaults.scrollAlpha(
                            abs(scrollBehavior.state.contentOffset),
                            density,
                        )
                    }
                }
                AppBarBlurOverlay(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = COUITheme.colorScheme.surface,
                    backdrop = backdrop,
                    scrollFraction = materialScrollFraction,
                    extraHeight = AppBarBlurOverlayDefaults.ExtraHeight,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(statusBar))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = TopAppBarDefaults.CollapsedHeight)
                                .padding(start = 16.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TopBarIconButton(onClick = { navigator.pop() }) {
                                Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = TopAppBarDefaults.NavigationIconGap, end = 4.dp),
                            ) {
                                // The title is the field itself, so tapping it puts the caret where
                                // the eye already is instead of opening a second page.
                                BasicTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { contentDescription = "搜索关键词，点击编辑" },
                                    textStyle = TextStyle(
                                        fontSize = with(density) { AppBarTitleSize.toSp() },
                                        fontWeight = FontWeight.Medium,
                                        color = COUITheme.colorScheme.onSurface,
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(COUITheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { submit(draft) }),
                                )
                                if (title.isNotEmpty()) {
                                    Text(
                                        text = "${scopeLabel(pageType)}：$title",
                                        modifier = Modifier.padding(top = TopAppBarDefaults.SubtitleMarginTop),
                                        style = COUITheme.textStyles.body2,
                                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (pagerState.currentPage == 0) {
                                Box {
                                    TopBarIconButton(onClick = { menuOpen = true }) {
                                        Icon(COUIIcons.More, contentDescription = "菜单")
                                    }
                                    OverlayCascadingListPopup(
                                        show = menuOpen,
                                        entries = menuEntries,
                                        alignment = PopupPositionProvider.Align.BottomEnd,
                                        onDismissRequest = { menuOpen = false },
                                    )
                                }
                            }
                        }
                        if (title.isEmpty()) {
                            // ColorOS 17's segment control paints an opaque capsule, so the tabs
                            // sit on their own surface instead of the page showing through.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .height(TabRowDefaults.TabRowWithContourHeight),
                            ) {
                                TabRowWithContour(
                                    tabs = tabs.map { it.label },
                                    selectedTabIndex = pagerState.currentPage,
                                    onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = appTabRowColors(),
                                    // Six labels share the narrower capsule, as on the home bar.
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
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().glassSource(backdrop),
                beyondViewportPageCount = 1,
            ) { page ->
                val contentType = tabs[page]
                val feedType = searchType.feedType
                val sort = sortType.sort
                val isStrict = sortType.isStrict
                val viewModel = viewModel(key = "search-$query-$pageType-$pageParam-${contentType.name}-$feedType-$sort-$isStrict") {
                    FeedListViewModel(
                        PageLoader { p, first, last ->
                            app.repository.search(
                                type = contentType.type,
                                feedType = feedType,
                                sort = sort,
                                searchValue = query,
                                isStrict = isStrict,
                                pageType = pageType,
                                pageParam = pageParam,
                                page = p,
                                firstItem = first,
                                lastItem = last,
                            )
                        },
                        app::contentGate,
                    )
                }
                // A new view model means a new search: start the list from the top instead of
                // keeping the previous filter's scroll position.
                key(viewModel) {
                    FeedList(
                        viewModel = viewModel,
                        scrollBehavior = scrollBehavior,
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 12.dp,
                            bottom = innerPadding.calculateBottomPadding() + 12.dp,
                        ),
                    )
                }
            }
        }
    }
}

private fun scopeLabel(pageType: String?): String = when (pageType) {
    "user" -> "用户"
    "apk" -> "应用"
    "tag" -> "话题"
    "product_phone" -> "数码"
    else -> "范围"
}
