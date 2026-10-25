package app.awavenue.c001apk.ui.coolpic

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
import app.awavenue.c001apk.ui.common.appTabRowColors
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

private val CoolPicTabs = listOf("精选" to "recommend", "热门" to "hot", "最新" to "newest")

@Composable
fun CoolPicScreen(title: String) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val decodedTitle = runCatching { Uri.decode(title) }.getOrDefault(title)
    val pagerState = rememberPagerState(
        initialPage = pageMemoryPage("cool-pic", fallback = 0),
    ) { CoolPicTabs.size }
    rememberPageMemoryRecorder("cool-pic", pagerState.currentPage)
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberPageScrollBehavior("cool-pic")
    val backdrop = rememberGlassBackdrop()

    Scaffold(
        edgeToEdge = true,
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
                            tabs = CoolPicTabs.map { it.first },
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = appTabRowColors(),
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
                val type = CoolPicTabs[page].second
                val viewModel = viewModel(key = "coolpic-$decodedTitle-$type") {
                    FeedListViewModel(
                        PageLoader { p, first, last ->
                            app.repository.coolPic(decodedTitle, type, p, first, last)
                        },
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
