package app.awavenue.c001apk.ui.nav

import android.app.Activity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.account.AccountsScreen
import app.awavenue.c001apk.ui.app.ApkScreen
import app.awavenue.c001apk.ui.app.AppUpdateScreen
import app.awavenue.c001apk.ui.blacklist.BlacklistScreen
import app.awavenue.c001apk.ui.carousel.CarouselScreen
import app.awavenue.c001apk.ui.chat.ChatScreen
import app.awavenue.c001apk.ui.common.CopyScreen
import app.awavenue.c001apk.ui.common.openCoolapkLink
import app.awavenue.c001apk.ui.coolpic.CoolPicScreen
import app.awavenue.c001apk.ui.dyh.DyhScreen
import app.awavenue.c001apk.ui.event.EventScreen
import app.awavenue.c001apk.ui.feed.detail.FeedDetailScreen
import app.awavenue.c001apk.ui.feed.detail.Reply2ReplyScreen
import app.awavenue.c001apk.ui.feed.detail.WebScreen
import app.awavenue.c001apk.ui.ffflist.FFFListScreen
import app.awavenue.c001apk.ui.follow.LocalFollowListScreen
import app.awavenue.c001apk.ui.history.HistoryScreen
import app.awavenue.c001apk.ui.image.ImageOrigins
import app.awavenue.c001apk.ui.image.ImageViewScreen
import app.awavenue.c001apk.ui.image.LocalImageOrigins
import app.awavenue.c001apk.ui.login.LoginScreen
import app.awavenue.c001apk.ui.main.MainScreen
import app.awavenue.c001apk.ui.notification.NotificationScreen
import app.awavenue.c001apk.ui.search.SearchResultScreen
import app.awavenue.c001apk.ui.search.SearchScreen
import app.awavenue.c001apk.ui.settings.AboutScreen
import app.awavenue.c001apk.ui.settings.ParamsScreen
import app.awavenue.c001apk.ui.settings.SettingsScreen
import app.awavenue.c001apk.ui.topic.ProductScreen
import app.awavenue.c001apk.ui.topic.TopicScreen
import app.awavenue.c001apk.ui.user.UserScreen
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Hosts the back stack. The main screen stays composed underneath so its tabs keep their lists;
 * a route pushed with an [ExpandOrigin] grows out of that element — a post row does this, and the
 * image viewer draws its own growth from the tapped thumbnail — while every other route slides in
 * over the page as a whole opaque card and back slides it out the same way.
 *
 * The back gesture is predictive: while it runs, the top page follows the finger and the page under
 * it is revealed; releasing early puts the page back where it was.
 */
@Composable
fun AppNavHost(deepLinks: Flow<String> = emptyFlow()) {
    val navigator = rememberNavigator()
    val context = LocalContext.current
    val openInBrowser = LocalApp.current.settings.openInBrowser
    val scope = rememberCoroutineScope()
    val imageOrigins = remember { ImageOrigins() }

    // How far the back gesture has pulled the top page: 1 at rest, 0 once it is fully away. It is
    // read in layer blocks, so a running gesture never recomposes the page.
    var gestureProgress by remember { mutableFloatStateOf(1f) }
    var gestureEntry by remember { mutableStateOf<BackStackEntry?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Bumped by every back callback, so the ease-back started by one callback can tell that a later
    // one has taken the gesture over and stand down instead of fighting it.
    var gestureRun by remember { mutableStateOf(0) }
    val gesture: () -> Float = remember { { gestureProgress } }
    // The page under the top one is only worth drawing while a gesture actually has the top page
    // pulled away. Derived so a running gesture does not recompose the host on every event.
    val revealing by remember { derivedStateOf { gestureProgress < 1f } }

    CompositionLocalProvider(LocalNavigator provides navigator, LocalImageOrigins provides imageOrigins) {
        // One collector for the host's lifetime: a link is opened against whichever page is on top
        // when it arrives, and recomposition does not restart the collection and replay it.
        LaunchedEffect(deepLinks, navigator, context, openInBrowser) {
            deepLinks.collect { url -> openCoolapkLink(context, navigator, url, "", openInBrowser) }
        }

        PredictiveBackHandler(enabled = true) { progress ->
            // A gesture that starts while the last one is still settling takes it over.
            settleJob?.cancel()
            if (navigator.stack.size <= 1) {
                gestureEntry = null
                gestureProgress = 1f
                // Nothing to preview: back either returns the main screen to its first tab or
                // backgrounds the app, and neither happens until the gesture is released.
                progress.collect { }
                if (navigator.onMainBack?.invoke() != true) (context as? Activity)?.moveTaskToBack(true)
                return@PredictiveBackHandler
            }
            val run = ++gestureRun
            val top = navigator.stack.last()
            gestureEntry = top
            try {
                progress.collect { event -> gestureProgress = 1f - event.progress }
                // Released past the threshold: the page finishes leaving from wherever the finger
                // left it, then goes. Popping it while it is still only part way out would make it
                // disappear from mid-air.
                if (gestureProgress < 1f) {
                    animate(gestureProgress, 0f, animationSpec = tween(GestureCommitMillis)) { value, _ ->
                        gestureProgress = value
                    }
                    top.previewed = true
                }
                navigator.pop()
            } catch (e: CancellationException) {
                // Released early: the page eases back under the finger — unless a later callback
                // has already taken the gesture over, in which case easing back would fight it.
                settleJob = scope.launch {
                    withFrameNanos { }
                    if (run != gestureRun) return@launch
                    animate(gestureProgress, 1f, animationSpec = tween(GestureSettleMillis)) { value, _ ->
                        gestureProgress = value
                    }
                    if (gestureEntry === top) {
                        gestureEntry = null
                        gestureProgress = 1f
                    }
                }
                return@PredictiveBackHandler
            }
            gestureEntry = null
            gestureProgress = 1f
        }

        Box(Modifier.fillMaxSize().background(COUITheme.colorScheme.surface)) {
            MainScreen()

            // The page under the top one is drawn while a gesture is pulling the top one away; when
            // the main screen is what sits underneath it is already composed above.
            val below = gestureEntry?.takeIf { revealing }
                ?.let { navigator.stack.getOrNull(navigator.stack.lastIndex - 1) }
            if (below != null && below !== navigator.stack.firstOrNull()) {
                RevealedRoute(below)
            }

            val overlay = navigator.stack.lastOrNull()?.takeIf { navigator.hasOverlay }
            AnimatedContent(
                targetState = overlay,
                transitionSpec = {
                    val popped = initialState?.takeIf { it.popped }
                    val pushed = targetState?.takeIf { popped == null }
                    val transform = when {
                        // A gesture already carried this page away; animating it again would double it.
                        popped?.previewed == true ->
                            EnterTransition.None togetherWith ExitTransition.None
                        // The page draws its own growth and shrink; the page under it stays put.
                        pushed?.growsFromOrigin() == true || popped?.shrinksToOrigin() == true ->
                            EnterTransition.None togetherWith ExitTransition.None
                        popped == null ->
                            // The whole page slides in from the right edge, fully opaque; the page
                            // under it trails a quarter of the width, as the system's card stack
                            // does. No fade: one card simply slides over another.
                            slideInHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) { it } togetherWith
                                slideOutHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) {
                                    -it / RouteParallaxDivisor
                                }
                        else ->
                            slideInHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) {
                                -it / RouteParallaxDivisor
                            } togetherWith
                                slideOutHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) { it }
                    }
                    // Every page fills the screen, so the default size animation only ever grew the
                    // first page out of a zero-sized box at the top-left corner.
                    transform.using(null).apply { if (popped != null) targetContentZIndex = -1f }
                },
                label = "route",
            ) { entry ->
                // Keyed by the entry so a page keeps the same saveable-state keys whichever
                // composition renders it — the top slot here, or the reveal below.
                if (entry != null) key(entry) {
                    DisposableEffect(entry) {
                        entry.attach()
                        onDispose { entry.detach() }
                    }
                    val grows = remember(entry) { entry.growsFromOrigin() }
                    SideEffect { entry.shown = true }
                    fun target(state: EnterExitState): Float =
                        when (state) {
                            EnterExitState.PreEnter -> if (grows) 0f else 1f
                            EnterExitState.Visible -> 1f
                            EnterExitState.PostExit -> if (entry.popped && entry.shrinksToOrigin()) 0f else 1f
                        }
                    val progress = transition.animateFloat(
                        transitionSpec = { tween(ExpandDurationMillis, easing = ExpandEasing) },
                        label = "expand",
                    ) { target(it) }
                    val fade = transition.animateFloat(
                        // Linear both ways: the page rides the shrinking frame instead of vanishing
                        // early. A fast exit curve left a blank card-coloured rectangle shrinking
                        // over the list, which read as the list failing to draw after the pop.
                        transitionSpec = { tween(ExpandDurationMillis, easing = LinearEasing) },
                        label = "expand-fade",
                    ) { target(it) }
                    // While a gesture is pulling this page away it drives both curves, so the page
                    // leaves exactly the way a pop would have taken it. A page the gesture already
                    // carried away stays away: the pop keeps it composed for the length of its own
                    // animation, and letting that curve run again would play the exit a second time.
                    val previewing = gestureEntry === entry
                    val entryGesture = if (previewing) gesture else SettledGesture
                    val pageProgress: () -> Float = remember(progress, entryGesture) {
                        { if (entry.previewed) 0f else minOf(progress.value, entryGesture()) }
                    }
                    val pageFade: () -> Float = remember(fade, entryGesture) {
                        { if (entry.previewed) 0f else minOf(fade.value, entryGesture()) }
                    }
                    val routeTransition = remember(transition, pageProgress, entryGesture) {
                        RouteTransition(
                            progress = pageProgress,
                            exiting = { transition.targetState == EnterExitState.PostExit || entryGesture() < 1f },
                        )
                    }
                    // A screen under a route pushed on top leaves composition, and with it its
                    // list positions and tab selections; they are kept on the entry meanwhile.
                    val saveableState = remember(entry) { SaveableStateRegistry(entry.savedState) { true } }
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides entry,
                        LocalSaveableStateRegistry provides saveableState,
                        LocalRouteTransition provides routeTransition,
                    ) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                // A page that shrinks back into something draws its own exit; every
                                // other one leaves as a whole opaque page, the way the pop
                                // transition does. One a gesture already carried away stays fully
                                // away for as long as the pop keeps it composed.
                                val p = when {
                                    entry.previewed -> 1f
                                    previewing && !entry.shrinksToOrigin() -> 1f - entryGesture()
                                    else -> 0f
                                }
                                translationX = p * size.width
                                alpha = 1f
                            },
                        ) {
                            ExpandFrame(
                                origin = entry.origin,
                                progress = pageProgress,
                                fade = pageFade,
                                // A gesture pulling the page away is an exit too: the frame has to
                                // hand back to the card under the finger, not wait for the pop.
                                exiting = { entry.popped || entryGesture() < 1f },
                            ) {
                                LeavingPage(entry) {
                                    // Overlay sheets and dialogs draw in the outermost COUI Scaffold
                                    // above them. Several screens place theirs beside, not inside,
                                    // their own Scaffold, so every route gets a transparent host that
                                    // catches them.
                                    Scaffold(
                                        containerColor = Color.Transparent,
                                        contentWindowInsets = WindowInsets(0),
                                    ) {
                                        Box(Modifier.fillMaxSize()) { RouteHost(entry.route) }
                                    }
                                }
                            }
                        }
                    }
                    // After the content, so it is disposed first, while the screen's state is
                    // still registered to be saved.
                    DisposableEffect(saveableState) {
                        onDispose { entry.savedState = saveableState.performSave() }
                    }
                }
            }
        }
    }
}

/** The progress a page reports while no gesture is pulling it away. */
private val SettledGesture: () -> Float = { 1f }

/** How long a page takes to slide a full width in or out. */
private const val RouteSlideMillis = 320

/** How far the covered page trails the incoming one, as a fraction of the screen width. */
private const val RouteParallaxDivisor = 4

/** How long the page takes to ease back under the finger when a gesture is released early. */
private const val GestureSettleMillis = 200

/** How long the page takes to finish leaving once the gesture has committed. */
private const val GestureCommitMillis = 150

/**
 * How long the press state layer takes to fade out. COUI animates it with a critically damped spring
 * (stiffness 438.65, response 0.3 s), which is under 1% of its strength after ~0.22 s.
 */
internal const val SnapshotSettleMillis = 220L

/**
 * The page under the top one, drawn while a back gesture is pulling the top one away. It is drawn
 * from the picture taken when it was covered, so it is exactly as the user left it, lists and all.
 * A page without one is composed instead: it is not composed the rest of the time, so it restores
 * its own saved state on the way in and hands it back on the way out, as it does when a pop
 * animates it back in.
 */
@Composable
private fun RevealedRoute(entry: BackStackEntry) = key(entry) {
    val snapshot = entry.snapshot
    if (snapshot != null) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                if (snapshot.width == 0 || snapshot.height == 0) return@drawBehind
                drawImage(
                    snapshot,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            },
        )
    } else {
        ComposedRevealedRoute(entry)
    }
}

@Composable
private fun ComposedRevealedRoute(entry: BackStackEntry) {
    val saveableState = remember(entry) { SaveableStateRegistry(entry.savedState) { true } }
    val settled = remember { RouteTransition(progress = SettledGesture, exiting = { false }) }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides entry,
        LocalSaveableStateRegistry provides saveableState,
        LocalRouteTransition provides settled,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
        ) {
            Box(Modifier.fillMaxSize()) { RouteHost(entry.route) }
        }
    }
    DisposableEffect(saveableState) {
        onDispose { entry.savedState = saveableState.performSave() }
    }
}

/**
 * Draws [content] live while its page is on the stack, recording a picture of it as it goes.
 * Once the page is on its way out it is drawn from that picture instead of being interactive: the
 * page underneath is the one the user is on now, and they have to be able to scroll it while this
 * one finishes shrinking back into where it came from — which also lets that shrink follow the list
 * as it moves. The content stays composed (that is what the picture is taken from) but is clipped
 * out of both sight and hit testing, so nothing of it can swallow a touch.
 */
@Composable
private fun LeavingPage(entry: BackStackEntry, content: @Composable () -> Unit) {
    val navigator = LocalNavigator.current
    val leaving = !navigator.stack.contains(entry)
    val covered = !leaving && navigator.stack.lastOrNull() !== entry
    val picture = rememberGraphicsLayer()
    // The page stays composed while the one above it enters; taking a picture of it for the entry
    // is what lets a back gesture reveal exactly where the user left it.
    //
    // The tap that opened the page above is released on the same frame as the push, and COUI fades
    // its press state layer out over the following ~200 ms. The first picture therefore still
    // carries the press overlay, so a second one is taken once the spring has settled — otherwise
    // that overlay would come back with the reveal and blink away when the page is composed again.
    LaunchedEffect(covered) {
        if (covered) {
            withFrameNanos { }
            if (picture.size != IntSize.Zero) entry.snapshot = picture.toImageBitmap()
            delay(SnapshotSettleMillis)
            if (picture.size != IntSize.Zero) entry.snapshot = picture.toImageBitmap()
        } else {
            entry.snapshot = null
        }
    }
    Box(
        Modifier.fillMaxSize().then(
            if (leaving) {
                Modifier.drawWithContent { drawLayer(picture) }
            } else {
                Modifier
            },
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (leaving) {
                        Modifier.graphicsLayer {
                            clip = true
                            shape = OutOfReach
                        }
                    } else {
                        Modifier
                    },
                )
                // Recorded for as long as the page is shown, not only once it leaves: the picture
                // is also what is taken when another page is stacked over this one.
                .drawWithContent {
                    picture.record { this@drawWithContent.drawContent() }
                    if (!leaving) drawLayer(picture)
                },
        ) { content() }
    }
}

/** A clip that leaves nothing inside it, which also takes the node out of hit testing. */
private val OutOfReach = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect.Zero)
}

/** Renders one destination. */
@Composable
private fun RouteHost(route: Route) {
    val navigator = LocalNavigator.current
    when (route) {
        Route.Main -> Unit
        Route.Login -> LoginScreen(onDone = { navigator.pop() })
        Route.AppUpdate -> AppUpdateScreen()
        Route.Params -> ParamsScreen()
        Route.About -> AboutScreen()
        Route.Settings -> SettingsScreen()
        Route.Accounts -> AccountsScreen()
        Route.LocalFollowList -> LocalFollowListScreen()
        is Route.Feed -> FeedDetailScreen(id = route.id, preview = route.preview)
        is Route.Reply2Reply -> Reply2ReplyScreen(
            id = route.id,
            replyId = route.replyId,
            originUid = route.originUid,
            origin = route.origin,
        )
        is Route.User -> UserScreen(uid = route.uid)
        is Route.Apk -> ApkScreen(packageName = route.packageName)
        is Route.Topic -> TopicScreen(tag = route.tag)
        is Route.Product -> ProductScreen(id = route.id)
        is Route.Event -> EventScreen(id = route.id)
        is Route.Search -> SearchScreen(title = route.title, pageType = route.pageType, pageParam = route.pageParam)
        is Route.SearchResult -> SearchResultScreen(
            keyword = route.keyword,
            title = route.title,
            pageType = route.pageType,
            pageParam = route.pageParam,
        )

        is Route.Web -> WebScreen(url = route.url, isLogin = route.isLogin)
        is Route.Carousel -> CarouselScreen(url = route.url, title = route.title, isInit = route.isInit)
        is Route.Dyh -> DyhScreen(id = route.id, title = route.title)
        is Route.CoolPic -> CoolPicScreen(title = route.title)
        is Route.ImageView -> ImageViewScreen(images = route.images, initialPage = route.initialPage)
        is Route.Notification -> NotificationScreen(type = route.type)
        is Route.NotificationCenter -> NotificationScreen(
            type = NotificationType.System,
            title = "通知",
            showShortcuts = true,
        )
        is Route.FFFList -> FFFListScreen(type = route.type, uid = route.uid, id = route.id, title = route.title)
        is Route.History -> HistoryScreen(type = route.type)
        is Route.Blacklist -> BlacklistScreen(type = route.type)
        is Route.Chat -> ChatScreen(ukey = route.ukey, uid = route.uid, username = route.username)
        is Route.Copy -> CopyScreen(text = route.text)
    }
}
