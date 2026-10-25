package app.awavenue.c001apk.ui.carousel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.appTabRowColors
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.ItemPadding
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import kotlinx.coroutines.launch

private const val NESTED_TEMPLATE = "iconTabLinkGridCard"

@Composable
fun CarouselScreen(url: String, title: String, isInit: Boolean) {
    if (!isInit) {
        CarouselBody(
            url = url,
            title = title,
            isInit = false,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        )
        return
    }

    val navigator = LocalNavigator.current
    val scrollBehavior = rememberPageScrollBehavior("carousel")
    val backdrop = rememberGlassBackdrop()
    var pageTitle by remember(url, title) { mutableStateOf("") }

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = pageTitle.ifEmpty { title },
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            CarouselContent(
                url = url,
                title = title,
                modifier = Modifier.fillMaxSize().glassSource(backdrop),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 12.dp,
                ),
                scrollBehavior = scrollBehavior,
                onPageTitle = { pageTitle = it },
            )
        }
    }
}

@Composable
fun CarouselContent(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp),
    scrollBehavior: ScrollBehavior = COUIScrollBehavior(),
    onPageTitle: (String) -> Unit = {},
    itemPadding: Dp = ItemPadding,
) = CarouselBody(
    url = url,
    title = title,
    isInit = true,
    modifier = modifier,
    contentPadding = contentPadding,
    scrollBehavior = scrollBehavior,
    onPageTitle = onPageTitle,
    itemPadding = itemPadding,
)

@Composable
private fun CarouselBody(
    url: String,
    title: String,
    isInit: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp),
    scrollBehavior: ScrollBehavior = COUIScrollBehavior(),
    onPageTitle: (String) -> Unit = {},
    itemPadding: Dp = ItemPadding,
) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "carousel-$isInit-$url-$title") {
        FeedListViewModel(
            PageLoader { page, first, last ->
                app.repository.dataList(
                    url = url,
                    title = title,
                    page = page,
                    firstItem = first,
                    lastItem = last,
                    // The nested tab card travels with the page's own config card only.
                    includeConfigCard = isInit,
                )
            },
        )
    }
    var titleReported by remember(url, title) { mutableStateOf(false) }

    // The card's page title overrides the one the caller passed, and only on the first page.
    LaunchedEffect(viewModel.items) {
        if (titleReported || viewModel.items.isEmpty()) return@LaunchedEffect
        titleReported = true
        viewModel.items.lastOrNull()?.obj("extraDataArr")?.string("pageTitle")
            ?.takeIf { it.isNotEmpty() }
            ?.let(onPageTitle)
    }

    val nested = viewModel.items.firstOrNull { it.entityTemplate == NESTED_TEMPLATE }
        ?.takeIf { isInit && viewModel.status == ListStatus.Content }

    Box(modifier) {
        if (nested == null) {
            FeedList(
                viewModel = viewModel,
                scrollBehavior = scrollBehavior,
                contentPadding = contentPadding,
                itemPadding = itemPadding,
            )
        } else {
            NestedTabs(nested, contentPadding, scrollBehavior)
        }
    }
}

@Composable
private fun NestedTabs(card: Entity, contentPadding: PaddingValues, scrollBehavior: ScrollBehavior) {
    val entries = card.entities
    val pagerState = rememberPagerState(
        initialPage = pageMemoryPage("carousel", fallback = 0),
    ) { entries.size }
    rememberPageMemoryRecorder("carousel", pagerState.currentPage)
    val scope = rememberCoroutineScope()

    // The caller's bar changes [contentPadding] in the measure phase, so both derived paddings
    // delegate and are read in measure instead of recomposing this page on every scroll frame.
    val tabRowPadding = remember(contentPadding) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
            override fun calculateTopPadding() = contentPadding.calculateTopPadding()
            override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
            override fun calculateBottomPadding() = 0.dp
        }
    }
    val pagePadding = remember(contentPadding) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
            override fun calculateTopPadding() = 12.dp
            override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
            override fun calculateBottomPadding() = contentPadding.calculateBottomPadding()
        }
    }
    // The pager fills the screen behind the caller's bar, so the tab row takes the top inset and
    // the child lists start right below it instead of applying that inset a second time.
    Column(Modifier.fillMaxSize().padding(tabRowPadding)) {
        TabRowWithContour(
            tabs = entries.map { it.title },
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = appTabRowColors(),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val entry = entries[page]
            CarouselBody(
                url = entry.url,
                title = entry.title,
                isInit = false,
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
            )
        }
    }
}
