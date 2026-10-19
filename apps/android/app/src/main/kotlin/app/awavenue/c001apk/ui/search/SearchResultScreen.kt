package app.awavenue.c001apk.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.couiSearchMaterial
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.MaterialStrokeParams
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

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

@Composable
fun SearchResultScreen(keyword: String, title: String, pageType: String?, pageParam: String?) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val tabs = if (title.isEmpty()) SearchContentType.entries else listOf(SearchContentType.Feed)
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val scrollBehavior = COUIScrollBehavior()
    val backdrop = rememberGlassBackdrop()

    var searchType by remember { mutableStateOf(SearchType.All) }
    var sortType by remember { mutableStateOf(SearchSortType.Dateline) }
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

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = keyword,
                    gradientGlass = true,
                    subtitle = if (title.isEmpty()) "" else "${scopeLabel(pageType)}：$title",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
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
                    },
                    bottomContent = {
                        if (title.isEmpty()) {
                            // The same capsule as the home tab row: the shared search material, a
                            // transparent tab container and the framed indicator inside it.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .height(TabRowDefaults.TabRowWithContourHeight)
                                    .couiSearchMaterial(
                                        RoundedCornerShape(50),
                                        dropShadow = false,
                                        fillAlpha = 0.8f,
                                    ),
                            ) {
                                TabRowWithContour(
                                    tabs = tabs.map { it.label },
                                    selectedTabIndex = pagerState.currentPage,
                                    onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
                                    // Six labels share the narrower capsule, as on the home bar.
                                    minWidth = 44.dp,
                                    segmentPadding = 8.dp,
                                    indicatorMaterial = MaterialStrokeParams.frameworkCapsule13(
                                        COUITheme.colorScheme.background.luminance() < 0.5f,
                                    ),
                                )
                            }
                        }
                    },
                )
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
                val viewModel = viewModel(key = "search-$keyword-$pageType-$pageParam-${contentType.name}-$feedType-$sort-$isStrict") {
                    FeedListViewModel(
                        PageLoader { p, first, last ->
                            app.repository.search(
                                type = contentType.type,
                                feedType = feedType,
                                sort = sort,
                                searchValue = keyword,
                                isStrict = isStrict,
                                pageType = pageType,
                                pageParam = pageParam,
                                page = p,
                                firstItem = first,
                                lastItem = last,
                            )
                        },
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
