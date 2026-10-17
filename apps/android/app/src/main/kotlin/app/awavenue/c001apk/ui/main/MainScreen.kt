package app.awavenue.c001apk.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.home.HomeScreen
import app.awavenue.c001apk.ui.home.HomeTab
import app.awavenue.c001apk.ui.message.MessageScreen
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.settings.SettingsScreen
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Home
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

private enum class MainTab(val label: String, val icon: ImageVector) {
    Home("首页", COUIIcons.Home),
    Message("消息", COUIIcons.Messages),
    Settings("设置", COUIIcons.Settings),
}

@Composable
fun MainScreen() {
    // A pager without swipe keeps every tab composed, so lists hold their position and data.
    val pagerState = rememberPagerState { MainTab.entries.size }
    val scope = rememberCoroutineScope()
    val backdrop = rememberGlassBackdrop()
    val returnTop = remember { ReturnTopController() }
    val navigator = LocalNavigator.current
    val onHome = pagerState.currentPage == MainTab.Home.ordinal
    val session = LocalApp.current.session
    val homeTab = LocalApp.current.settings.homeTab
    val homePagerState = rememberPagerState(initialPage = homeTab.coerceIn(HomeTab.entries.indices)) { HomeTab.entries.size }
    var compose by remember { mutableStateOf(false) }

    // Back returns to the first tab; on it the host backgrounds the app, as upstream does.
    DisposableEffect(navigator, onHome, pagerState) {
        navigator.onMainBack = {
            if (onHome) {
                false
            } else {
                scope.launch { pagerState.scrollToPage(MainTab.Home.ordinal) }
                true
            }
        }
        onDispose { navigator.onMainBack = null }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // The floating capsule paints its own glass; the Scaffold only reserves its height.
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    BottomFloatingNavigationBar(
                        // The publish button melts out of the capsule. Publishing is only offered on
                        // 动态, and only to a signed-in account, as upstream does.
                        expanded = onHome && session.isLogin && homePagerState.currentPage == HomeTab.Feed.ordinal,
                        expansion = {
                            // The bar already gives the ball its round press feedback; a ripple here
                            // would paint a square over it.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                        compose = true
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = COUIIcons.Add,
                                    contentDescription = "发布动态",
                                    tint = COUITheme.colorScheme.onSurface,
                                )
                            }
                        },
                    ) {
                        MainTab.entries.forEachIndexed { index, tab ->
                            BottomFloatingNavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    // Tapping the tab already shown scrolls its list to the top.
                                    if (pagerState.currentPage == index) {
                                        if (tab == MainTab.Home) returnTop.requestReturn()
                                    } else {
                                        scope.launch { pagerState.scrollToPage(index) }
                                    }
                                },
                                icon = tab.icon,
                                label = tab.label,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            userScrollEnabled = false,
            beyondViewportPageCount = MainTab.entries.size,
        ) { page ->
            when (MainTab.entries[page]) {
                MainTab.Home -> HomeScreen(outerPadding = padding, pagerState = homePagerState, returnTop = returnTop)
                MainTab.Message -> MessageScreen(outerPadding = padding)
                MainTab.Settings -> SettingsScreen(outerPadding = padding)
            }
        }

        // Inside the Scaffold: COUI overlays only draw in a Scaffold that encloses them.
        if (compose) {
            ReplyComposerSheet(
                target = ReplyTarget(),
                onDismiss = { compose = false },
                onPosted = { compose = false },
            )
        }
    }
}
