package app.awavenue.c001apk.ui.dyh

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import kotlinx.coroutines.launch

private val DyhTabs = listOf("精选" to "all", "广场" to "square")

@Composable
fun DyhScreen(id: String, title: String) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val decodedTitle = runCatching { Uri.decode(title) }.getOrDefault(title)
    val pagerState = rememberPagerState(
        initialPage = pageMemoryPage("dyh", fallback = 0),
    ) { DyhTabs.size }
    rememberPageMemoryRecorder("dyh", pagerState.currentPage)
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberPageScrollBehavior("dyh")
    val backdrop = rememberGlassBackdrop()

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = decodedTitle,
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    bottomContent = {
                        TabRowWithContour(
                            tabs = DyhTabs.map { it.first },
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        )
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
                val type = DyhTabs[page].second
                val viewModel = viewModel(key = "dyh-$id-$type") {
                    FeedListViewModel(
                        PageLoader { p, first, last -> app.repository.dyhDetail(id, type, p, first, last) },
                    )
                }
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
