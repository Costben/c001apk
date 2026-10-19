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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.ReturnTopController
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.home.HomeScreen
import app.awavenue.c001apk.ui.home.HomeTab
import app.awavenue.c001apk.ui.follow.FollowScreen
import app.awavenue.c001apk.ui.message.MessageScreen
import app.awavenue.c001apk.ui.nav.LocalAppBarBounds
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.settings.AutomaticUpdateCheck
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.icon.extended.NotesFill
import io.github.suqi8.coui.kmp.icon.extended.Update
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("主页", AppIcons.HomeOutlined, AppIcons.HomeFilled),
    Follow("关注", COUIIcons.Notes, COUIIcons.NotesFill),
    Mine("我的", AppIcons.PersonOutlined, AppIcons.PersonFilled),
}

/**
 * The main screen. Switching account rebuilds it from scratch, the way the Flutter client unmounts
 * its pages: routes opened under the previous account close, and every list on the tabs is read
 * again for the new one.
 */
@Composable
fun MainScreen() {
    val navigator = LocalNavigator.current
    val session = LocalApp.current.session
    var shown by remember { mutableIntStateOf(session.generation) }
    // The bottom tab outlives a switch, so adding an account from 我的 comes back to 我的.
    var tab by rememberSaveable { mutableIntStateOf(MainTab.Home.ordinal) }
    if (shown != session.generation) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() }
        LaunchedEffect(session.generation) {
            // One frame without the old pages, so they are gone before the next account's are built.
            withFrameNanos { }
            navigator.popToMain()
            shown = session.generation
        }
        return
    }
    key(shown) {
        // The tabs' ViewModels belong to one account: each generation gets its own store, and the
        // previous one is cleared once its pages have left.
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            MainContent(initialTab = tab, onTabChange = { tab = it })
        }
    }
}

@Composable
private fun MainContent(initialTab: Int, onTabChange: (Int) -> Unit) {
    // A pager without swipe keeps every tab composed, so lists hold their position and data.
    val pagerState = rememberPagerState(initialPage = initialTab) { MainTab.entries.size }
    LaunchedEffect(pagerState.currentPage) { onTabChange(pagerState.currentPage) }
    // The bar height changes as the home row collapses; the current tab's is the one a return uses.
    val appBarBounds = LocalAppBarBounds.current
    LaunchedEffect(pagerState.currentPage) { appBarBounds.currentTab = pagerState.currentPage }
    val scope = rememberCoroutineScope()
    val backdrop = rememberGlassBackdrop()
    val returnTop = remember { ReturnTopController() }
    val followReturnTop = remember { ReturnTopController() }
    val navigator = LocalNavigator.current
    val onHome = pagerState.currentPage == MainTab.Home.ordinal
    val app = LocalApp.current
    val session = app.session
    val settings = app.settings

    // The bell badge keeps itself current: a periodic read while signed in, plus one whenever a
    // page stacked over the main screen closes (that is when a notification list was just read).
    LaunchedEffect(session.isLogin, session.generation) {
        if (!session.isLogin) {
            app.unread.clear()
            return@LaunchedEffect
        }
        while (true) {
            app.unread.refresh()
            delay(30_000)
        }
    }
    LaunchedEffect(navigator.stack.size) {
        if (session.isLogin && navigator.stack.size == 1) app.unread.refresh()
    }
    val homeTab = settings.homeTab
    // The same visible-tab list HomeScreen builds: the pager, the ball checks and the tab bodies
    // must agree on what page N means.
    val homeTabs = HomeTab.entries.filterNot { it.name in settings.hiddenHomeTabs }.ifEmpty { HomeTab.entries }
    val homePagerState = rememberPagerState(
        initialPage = homeTabs.indexOfFirst { it.name == homeTab }
            .takeIf { it >= 0 } ?: homeTabs.indexOf(HomeTab.Feed).coerceAtLeast(0),
    ) { homeTabs.size }
    // Hiding tabs can leave the current page past the new end; pageCount follows the state, but the
    // position still has to be pulled back into range.
    LaunchedEffect(homeTabs) {
        if (homePagerState.currentPage > homeTabs.lastIndex) {
            homePagerState.scrollToPage(homeTabs.lastIndex)
        }
    }
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
                    // One ball melts out of the capsule's trailing end at a time: 发布动态 on 动态
                    // (signed in) and 更新 on 应用 (when the update check is on). The page index is
                    // an index into the visible tabs, not the HomeTab enum ordinal.
                    val currentHomeTab = homeTabs.getOrNull(homePagerState.currentPage)
                    val onFeed = onHome && currentHomeTab == HomeTab.Feed
                    val onApp = onHome && currentHomeTab == HomeTab.App
                    val composeBall = onFeed && session.isLogin
                    val updateBall = onApp && settings.checkUpdate
                    BottomFloatingNavigationBar(
                        expanded = composeBall || updateBall,
                        expansion = {
                            // The bar already gives the ball its round press feedback; a ripple here
                            // would paint a square over it.
                            val action: () -> Unit = if (updateBall) {
                                { navigator.push(Route.AppUpdate) }
                            } else {
                                { compose = true }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = action),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (updateBall) {
                                    Icon(
                                        imageVector = COUIIcons.Update,
                                        contentDescription = "更新",
                                        tint = COUITheme.colorScheme.onSurface,
                                    )
                                } else {
                                    Icon(
                                        imageVector = COUIIcons.Add,
                                        contentDescription = "发布动态",
                                        tint = COUITheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        },
                    ) {
                        MainTab.entries.forEachIndexed { index, tab ->
                            BottomFloatingNavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    // Tapping the tab already shown scrolls its list to the top.
                                    if (pagerState.currentPage == index) {
                                        when (tab) {
                                            MainTab.Home -> returnTop.requestReturn()
                                            MainTab.Follow -> followReturnTop.requestReturn()
                                            MainTab.Mine -> Unit
                                        }
                                    } else {
                                        scope.launch { pagerState.scrollToPage(index) }
                                    }
                                },
                                icon = tab.icon,
                                selectedIcon = tab.selectedIcon,
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
                MainTab.Home -> HomeScreen(
                    outerPadding = padding,
                    pagerState = homePagerState,
                    returnTop = returnTop,
                    barSlot = page,
                )
                MainTab.Follow -> FollowScreen(outerPadding = padding, returnTop = followReturnTop, barSlot = page)
                MainTab.Mine -> MessageScreen(outerPadding = padding, barSlot = page)
            }
        }

        // Inside the Scaffold: COUI overlays only draw in a Scaffold that encloses them.
        AutomaticUpdateCheck()
        if (compose) {
            ReplyComposerSheet(
                target = ReplyTarget(),
                onDismiss = { compose = false },
                onPosted = { compose = false },
            )
        }
    }
}
