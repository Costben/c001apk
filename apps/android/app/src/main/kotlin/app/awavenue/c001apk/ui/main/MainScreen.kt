package app.awavenue.c001apk.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.app.AppFilterBall
import app.awavenue.c001apk.ui.app.AppFilterSearchPanel
import app.awavenue.c001apk.ui.app.AppFilterState
import app.awavenue.c001apk.ui.app.LocalAppFilter
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
import app.awavenue.c001apk.ui.theme.rememberChromeDensity
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBar
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tapping a bottom tab fades the page out, jumps, and fades the new page in. */
private const val TabFadeOutMillis = 60
private const val TabFadeInMillis = 100

/** The accessory ball swaps its mark by shrinking the old one, then growing the new one. */
private const val AccessoryShrinkMillis = 90
private const val AccessoryGrowMillis = 170
private const val AccessoryFadeMillis = 70

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
    // The pager itself keeps every tab composed; only its layer fades, so lists keep their state.
    val fade = remember { Animatable(1f) }
    val switchJob = remember { mutableStateOf<Job?>(null) }
    val navigator = LocalNavigator.current
    // A wide window shows the detail beside the list, so leaving the list's section closes it:
    // the feed on the right would otherwise belong to a section the user has already left.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val switchTo: (Int) -> Unit = { index ->
        if (pagerState.currentPage != index) {
            if (wide && navigator.hasOverlay) navigator.popToMain()
            switchJob.value?.cancel()
            switchJob.value = scope.launch {
                fade.animateTo(0f, tween(TabFadeOutMillis, easing = LinearEasing))
                pagerState.scrollToPage(index)
                fade.animateTo(1f, tween(TabFadeInMillis, easing = LinearEasing))
            }
        }
    }
    val backdrop = rememberGlassBackdrop()
    val returnTop = remember { ReturnTopController() }
    val followReturnTop = remember { ReturnTopController() }
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
    // Shared with the 应用 tab: the ball that edits this sits in the bar, the list one page deeper.
    val appFilter = remember { AppFilterState() }
    // The menu and the field belong to the 应用 page, so leaving it must not leave them armed.
    LaunchedEffect(homePagerState.currentPage, homeTabs, onHome) {
        val onApp = onHome && homeTabs.getOrNull(homePagerState.currentPage) == HomeTab.App
        if (!onApp) {
            appFilter.menuOpen = false
            appFilter.searchOpen = false
        }
    }

    // Back returns to the first tab; on it the host backgrounds the app, as upstream does.
    DisposableEffect(navigator, onHome, pagerState) {
        navigator.onMainBack = {
            if (onHome) {
                false
            } else {
                switchTo(MainTab.Home.ordinal)
                true
            }
        }
        onDispose { navigator.onMainBack = null }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // The floating capsule paints its own glass; the Scaffold only reserves its height.
            // It is measured in the system-app density so 显示大小 leaves the bar the size 相册's
            // is, while every page inside keeps following that setting.
            val barDensity = rememberChromeDensity()
            CompositionLocalProvider(
                LocalBackdrop provides backdrop,
                LocalDensity provides barDensity,
            ) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    // Both side slots stay configured, so the capsule keeps its width and stays in
                    // the middle whether a ball is out or not; a ball only melts out of the capsule
                    // into its slot, the way 相册's five-slot bar behaves. 筛选 takes the slot before
                    // the capsule on 应用, 发布动态 / 更新 the one after it.
                    val currentHomeTab = homeTabs.getOrNull(homePagerState.currentPage)
                    val onFeed = onHome && currentHomeTab == HomeTab.Feed
                    val onApp = onHome && currentHomeTab == HomeTab.App
                    val composeBall = onFeed && session.isLogin
                    val updateBall = onApp && settings.checkUpdate
                    BottomFloatingAccessoryNavigationBar(
                        startAccessoryVisible = onApp,
                        startAccessory = { AppFilterBall(filter = appFilter) },
                        endAccessoryVisible = composeBall || updateBall,
                        endAccessory = {
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
                                // The ball keeps its place between pages, so ColorOS swaps the mark
                                // inside it: the old one shrinks to nothing, then the new one grows
                                // out of the same spot instead of cutting over.
                                AnimatedContent(
                                    targetState = updateBall,
                                    transitionSpec = {
                                        (
                                            scaleIn(
                                                tween(
                                                    AccessoryGrowMillis,
                                                    delayMillis = AccessoryShrinkMillis,
                                                ),
                                            ) + fadeIn(
                                                tween(
                                                    AccessoryFadeMillis,
                                                    delayMillis = AccessoryShrinkMillis,
                                                ),
                                            )
                                            ).togetherWith(
                                            scaleOut(tween(AccessoryShrinkMillis)) +
                                                fadeOut(tween(AccessoryFadeMillis)),
                                        )
                                    },
                                    label = "accessory-mark",
                                ) { update ->
                                    Icon(
                                        imageVector = if (update) COUIIcons.Update else COUIIcons.Add,
                                        contentDescription = if (update) "更新" else "发布动态",
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
                                        switchTo(index)
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
        CompositionLocalProvider(LocalAppFilter provides appFilter) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    // Outside the glass source, so the bar's backdrop keeps the page while it fades.
                    .graphicsLayer { alpha = fade.value }
                    .glassSource(backdrop),
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

            AppFilterSearchPanel(
                filter = appFilter,
                bottomPadding = padding.calculateBottomPadding(),
                modifier = Modifier.fillMaxSize(),
            )

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
}
