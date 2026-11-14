package app.awavenue.c001apk.ui.topic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward

/**
 * The ranking of other products a 机型话题 points at, opened from the bar's 相关话题 entry.
 *
 * The list itself is the one the API labels 参数; the spec sheet is the bar's own 参数 sheet, so
 * this page is the only place that stream is shown.
 */
@Composable
fun RelatedTopicsScreen(url: String) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberPageScrollBehavior("related-topics")
    val backdrop = rememberGlassBackdrop()
    val viewModel = viewModel(key = "related-topics-$url") {
        FeedListViewModel(
            PageLoader { page, first, last -> app.repository.dataList(url, "相关话题", page, first, last) },
            app::contentGate,
        )
    }

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "相关话题",
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = rememberHapticClick { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
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
