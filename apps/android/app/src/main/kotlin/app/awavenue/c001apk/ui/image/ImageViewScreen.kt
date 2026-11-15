package app.awavenue.c001apk.ui.image

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.ui.common.isGifUrl
import app.awavenue.c001apk.media.isLivePhotoUrl
import app.awavenue.c001apk.ui.common.openBrowser
import app.awavenue.c001apk.ui.common.originalImageUrl
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import app.awavenue.c001apk.ui.common.saveOriginalImage
import app.awavenue.c001apk.ui.common.saveLivePhoto
import app.awavenue.c001apk.ui.common.shareImage
import app.awavenue.c001apk.ui.common.SUFFIX_THUMBNAIL
import app.awavenue.c001apk.ui.common.thumbnail
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.ActionSheetItem
import app.awavenue.c001apk.ui.nav.ExpandEasing
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.LocalRouteTransition
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.icon.extended.Link
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ChromeColor = Color.Black.copy(alpha = 0.45f)
private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f

// A picture narrower than this (width over height) is a long strip. Fitted inside the screen it
// would shrink to a sliver, so it rests at the screen's width and is scrolled up and down, and it
// is decoded at that width so the detail is there when it is zoomed.
private const val LongImageAspect = 9f / 22f

// A flick down a long strip carries on after the finger is lifted; below this speed it is just a
// slow drag and stops where it is.
private const val FlingMinVelocity = 350f

// Pinching below the resting size is allowed so the picture does not feel walled in, but only by
// this much and only at this fraction of the pinch's pace; letting go springs it back.
private const val MinZoom = 0.5f
private const val ReboundResistance = 0.5f

// Ceiling on a long strip's decoded pixels, so that a very long one cannot exhaust the heap.
private const val MaxDecodePixels = 12_000_000

// The bars come in over the last part of the opening and are gone early in the closing, so they
// never ride along with the shrinking frame.
private const val ChromeFadeStart = 0.6f

// Below this the picture counts as unzoomed, so float residue from pinching never blocks a swipe.
private const val ZoomEpsilon = 0.01f

// The full picture fades in over the thumbnail so the transition is not a hard cut.
private const val LoadFadeMillis = 200

/**
 * Full-screen picture pager. The picture sits under translucent bars, fitted to the whole screen —
 * by width or by height, whichever binds — and a tap shows or hides the bars. A long strip is
 * fitted by width instead, so it opens at its top edge and scrolls up and down rather than being
 * shrunk into a sliver. The picture grows out of the thumbnail that was tapped and shrinks back
 * into the one it ends on.
 */
@Composable
fun ImageViewScreen(images: List<String>, initialPage: Int) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val transition = LocalRouteTransition.current
    val origins = LocalImageOrigins.current
    val pagerState = rememberPagerState(
        initialPage = pageMemoryPage(
            "image-view",
            fallback = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        ),
    ) { images.size }
    rememberPageMemoryRecorder("image-view", pagerState.currentPage)

    var chromeVisible by remember { mutableStateOf(true) }
    var sheetPage by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveHud by remember { mutableStateOf("") }
    val frame = remember { ViewerFrame() }
    val progress: () -> Float = remember(transition) { transition?.progress ?: { 1f } }
    val currentUrl = images.getOrNull(pagerState.currentPage).orEmpty()

    // 记下正在看的是哪一张：被它盖住的缩略图要等这一页撤走再恢复显示，避免半透明背景透出两张一样的图。
    SideEffect { origins.viewing = currentUrl.ifEmpty { null } }
    DisposableEffect(Unit) {
        onDispose {
            if (origins.dismissFlight == null) origins.viewing = null
        }
    }

    val leaving: () -> Boolean = { transition?.exiting?.invoke() == true }
    LightSystemBarIcons(leaving = leaving)
    // 控件收起时状态栏跟着收，长图才能顶到屏幕最上沿；从顶部划一下可以临时叫出来。
    ImmersiveStatusBar(
        hidden = !chromeVisible,
        leaving = leaving,
    )

    fun saveOriginals(urls: List<String>) {
        sheetPage = null
        if (saving || urls.isEmpty()) return
        scope.launch {
            saving = true
            var saved = 0
            urls.forEachIndexed { index, url ->
                saveHud = if (urls.size == 1) "正在保存原图…" else "正在保存 ${index + 1}/${urls.size}…"
                if (saveOriginalImage(context, url)) saved++
            }
            saving = false
            saveHud = when {
                urls.size == 1 && saved == 1 -> "已保存原图到相册"
                urls.size == 1 -> "原图保存失败"
                saved == urls.size -> "已保存 $saved 张原图到相册"
                else -> "已保存 $saved/${urls.size} 张原图"
            }
        }
    }

    // The status pill outlives the menu, but not the save: every update restarts the timer.
    LaunchedEffect(saveHud) {
        if (saveHud.isNotEmpty()) {
            delay(1800)
            saveHud = ""
        }
    }

    fun saveLive(url: String) {
        sheetPage = null
        scope.launch {
            saving = true
            val ok = saveLivePhoto(context, url)
            saving = false
            context.toast(if (ok) "已保存实况照片" else "实况照片保存失败")
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { frame.host = it }
            .onSizeChanged { frame.dismiss.boxSize = it },
    ) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                val d = frame.dismiss
                val bgAlpha = if (d.active) d.currentBgAlpha() else progress()
                if (bgAlpha > 0f) drawRect(Color.Black, alpha = bgAlpha)
            },
        )

        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无内容", color = Color.White)
            }
        } else {
            val squircleEnabled = isSquircleEnabled()
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = images.size > 1 && !frame.dismiss.active,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1,
            ) { page ->
                ZoomablePage(
                    url = images[page],
                    isCurrent = pagerState.currentPage == page,
                    isSettled = pagerState.settledPage == page,
                    isSettledElsewhere = pagerState.settledPage != page,
                    canPage = images.size > 1,
                    isPagerScrolling = { abs(pagerState.currentPageOffsetFraction) > 0.01f },
                    canStartDismiss = {
                        transition?.exiting?.invoke() != true && progress() >= 0.35f
                    },
                    progress = progress,
                    origins = origins,
                    squircleEnabled = squircleEnabled,
                    frame = frame,
                    onTap = { chromeVisible = !chromeVisible },
                    onLongPress = {
                        if (!saving && !frame.dismiss.active) {
                            sheetPage = page
                        }
                    },
                    onDismissRelease = { velocity ->
                        handleDismissRelease(
                            velocity = velocity,
                            dismiss = frame.dismiss,
                            frame = frame,
                            origins = origins,
                            url = images.getOrNull(pagerState.currentPage),
                            density = density,
                            squircleEnabled = squircleEnabled,
                            scope = scope,
                            onPopImmediate = { navigator.popImmediate() },
                        )
                    },
                )
            }
        }

        val dismissChromeAlpha by animateFloatAsState(
            targetValue = if (frame.dismiss.dragging) 0f else 1f,
            animationSpec = tween(150),
            label = "dismissChrome",
        )
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = ((progress() - ChromeFadeStart) / (1f - ChromeFadeStart)).coerceIn(0f, 1f) * dismissChromeAlpha
            },
        ) {
            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(ChromeFadeMillis)),
                exit = fadeOut(tween(ChromeFadeMillis)),
            ) {
                SmallTopAppBar(
                    title = if (images.isEmpty()) "" else "${pagerState.currentPage + 1}/${images.size}",
                    gradientGlass = true,
                    color = ChromeColor,
                    titleColor = Color.White,
                    showDivider = false,
                    navigationIcon = {
                        TopBarIconButton(
                            onClick = {
                                if (!frame.dismiss.dragging) {
                                    frame.dismiss.cancelActive()
                                    navigator.pop()
                                }
                            },
                        ) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
            // The system navigation bar is transparent, so it needs the same veil to stay legible
            // over a bright picture.
            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(ChromeFadeMillis)),
                exit = fadeOut(tween(ChromeFadeMillis)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(ChromeColor),
                )
            }
        }

        if (saveHud.isNotEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    text = saveHud,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 30.dp)
                        .clip(rememberSquircleShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }

    val page = sheetPage
    if (page != null && page in images.indices) {
        val url = images[page]
        OverlayBottomSheet(show = true, onDismissRequest = { sheetPage = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                if (url.isLivePhotoUrl()) {
                    ActionSheetItem(COUIIcons.Download, "保存实况照片", onClick = { saveLive(url) })
                }
                ActionSheetItem(COUIIcons.Download, "保存原图", onClick = { saveOriginals(listOf(url)) })
                if (images.size != 1) {
                    ActionSheetItem(
                        COUIIcons.Image,
                        "一键保存原图（${images.size} 张）",
                        onClick = { saveOriginals(images) },
                    )
                }
                ActionSheetItem(
                    COUIIcons.Share,
                    "分享",
                    onClick = {
                        sheetPage = null
                        scope.launch {
                            saving = true
                            val ok = shareImage(context, url)
                            saving = false
                            if (!ok) context.toast("图片分享失败，请检查网络")
                        }
                    },
                )
                ActionSheetItem(
                    COUIIcons.Copy,
                    "复制",
                    onClick = {
                        sheetPage = null
                        context.copyText(url)
                    },
                )
                ActionSheetItem(
                    COUIIcons.Link,
                    "在浏览器中打开",
                    onClick = {
                        sheetPage = null
                        context.openBrowser(url)
                    },
                )
            }
        }
    }

}

private const val ChromeFadeMillis = 180

/** What the pager's frame needs to know about the picture, outside any one page. */
@Stable
private class ViewerFrame {
    var host: LayoutCoordinates? = null

    /** True while the picture on screen is zoomed in. */
    var zoomed by mutableStateOf(false)

    /** 拖拽/甩出退出的共享状态。 */
    val dismiss = DragDismissState()

    /** Width over height per picture, once a page has read it from the decoded image. */
    val aspects = mutableStateMapOf<String, Float>()

    /** Where each thumbnail was last seen, kept for the closing frame while its page is not composed. */
    val thumbs = HashMap<String, ThumbBounds>()

    /** 每页当前帧的独立图元图层，松手退出时无缝移交给导航宿主的非拦截飞行层继续播放。 */
    val pageLayers = HashMap<String, GraphicsLayer>()
    val handedOffLayers = HashSet<GraphicsLayer>()
    var onReleaseLayer: ((GraphicsLayer) -> Unit)? = null

    fun aspectOf(url: String): Float? {
        aspects[url]?.let { return it }
        val (width, height) = imageSize(url)
        // 1×1 is what imageSize answers when the address carries no size.
        return if (width == 1f && height == 1f) null else width / height
    }
}

private fun GraphicsLayerScope.settle() {
    alpha = 1f
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    shape = RectangleShape
    clip = false
}

/**
 * Lays the whole pager over the thumbnail at [progress] 0 and over the screen at 1. The picture
 * rests at the same size it settles at, so scaling it up around the screen's centre until it covers
 * the thumbnail, then clipping to the thumbnail's bounds, reproduces the thumbnail's own centre crop
 * exactly; every step between is the same two moves interpolated. Without a thumbnail — and for a
 * long strip, which rests taller than the screen and so cannot be mapped back onto a thumbnail —
 * it just fades and settles in.
 */
private fun GraphicsLayerScope.openFrame(
    progress: Float,
    thumb: ThumbBounds?,
    aspect: Float?,
    squircleEnabled: Boolean,
) {
    val source = thumb?.rect
    val rest = (aspect ?: source?.let { it.width / it.height })
        ?.let { restSize(size.width, size.height, it) }
    if (source == null || rest == null || rest.height > size.height + 0.5f) {
        settle()
        alpha = progress
        scaleX = lerp(0.92f, 1f, progress)
        scaleY = scaleX
        return
    }
    val width = size.width
    val height = size.height
    val cover = max(source.width / rest.width, source.height / rest.height)
    val scale = lerp(cover, 1f, progress)
    alpha = 1f
    transformOrigin = TransformOrigin.Center
    scaleX = scale
    scaleY = scale
    translationX = lerp(source.center.x, width / 2f, progress) - width / 2f
    translationY = lerp(source.center.y, height / 2f, progress) - height / 2f
    // The visible window is centred on the layer, so it is the interpolated size divided by the
    // scale that now magnifies it.
    shape = CenteredWindow(
        width = lerp(source.width, width, progress) / scale,
        height = lerp(source.height, height, progress) / scale,
        radius = lerp(thumb.cornerRadius, 0f, progress) / scale,
        squircleEnabled = squircleEnabled,
    )
    clip = true
}

internal class CenteredWindow(
    private val width: Float,
    private val height: Float,
    private val radius: Float,
    private val squircleEnabled: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val path = Path().apply {
            addSquircleRect(width = width, height = height, cornerRadius = radius, squircleEnabled = squircleEnabled)
            translate(Offset(left, top))
        }
        return Outline.Generic(path)
    }
}

/** How a picture of [aspect] (width over height) sits in a box: the larger of the two it can fill. */
private fun fitSize(boxWidth: Float, boxHeight: Float, aspect: Float): Size =
    if (aspect > boxWidth / boxHeight) Size(boxWidth, boxWidth / aspect) else Size(boxHeight * aspect, boxHeight)

/**
 * The size a picture of [aspect] (width over height) rests at in a box: fitted inside it, or for a
 * long strip the box's full width, so that its height overruns the box and can be scrolled.
 */
internal fun restSize(boxWidth: Float, boxHeight: Float, aspect: Float): Size =
    if (aspect < LongImageAspect) Size(boxWidth, boxWidth / aspect) else fitSize(boxWidth, boxHeight, aspect)

@Composable
private fun ZoomablePage(
    url: String,
    isCurrent: Boolean,
    isSettled: Boolean,
    isSettledElsewhere: Boolean,
    canPage: Boolean,
    isPagerScrolling: () -> Boolean,
    canStartDismiss: () -> Boolean,
    progress: () -> Float,
    origins: ImageOrigins,
    squircleEnabled: Boolean,
    frame: ViewerFrame,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismissRelease: (Velocity) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val graphicsContext = LocalGraphicsContext.current
    val pageLayer = remember(url) { graphicsContext.createGraphicsLayer() }
    SideEffect {
        frame.pageLayers[url] = pageLayer
        frame.onReleaseLayer = { graphicsContext.releaseGraphicsLayer(it) }
    }
    DisposableEffect(url, pageLayer) {
        onDispose {
            if (frame.pageLayers[url] === pageLayer) frame.pageLayers.remove(url)
            if (!frame.handedOffLayers.remove(pageLayer)) {
                graphicsContext.releaseGraphicsLayer(pageLayer)
            }
        }
    }
    val currentCanPage by rememberUpdatedState(canPage)
    val currentIsPagerScrolling by rememberUpdatedState(isPagerScrolling)
    val currentCanStartDismiss by rememberUpdatedState(canStartDismiss)
    val currentOnDismissRelease by rememberUpdatedState(onDismissRelease)
    var box by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Whether the picture has been put at its resting position yet; until the box and the shape are
    // both known there is nothing to place it against.
    var placed by remember(url) { mutableStateOf(false) }
    // The animation running after the fingers leave: a flick's carry-on, or a rebound from a pinch
    // that went under the resting size.
    val glide = remember { mutableStateOf<Job?>(null) }
    val remote = url.startsWith("http://") || url.startsWith("https://")
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    // The list hands over a display variant of the picture. When that one will not load, the
    // original file behind it usually still does, so the viewer falls back instead of going blank.
    val baseUrl = remember(url) { if (url.contains(SUFFIX_THUMBNAIL)) originalImageUrl(url) else url }
    val originalUrl = remember(baseUrl) { originalImageUrl(baseUrl) }
    var useOriginal by remember(url) { mutableStateOf(false) }
    val source = if (useOriginal) originalUrl else baseUrl
    // A long strip is decoded at the screen's width rather than fitted to it, so zooming in shows
    // detail instead of a stretched handful of pixels.
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val decodeSize = frame.aspectOf(url)?.takeIf { it < LongImageAspect && !isGifUrl(url) }?.let { aspect ->
        val width = screenWidthPx.coerceAtLeast(1)
        val height = (width / aspect).toInt().coerceAtLeast(1)
        coil3.size.Size(width, minOf(height, (MaxDecodePixels / width).coerceAtLeast(1)))
    }
    // The full picture's own alpha, so it eases in over the thumbnail instead of snapping in.
    var reveal by remember(url) { mutableFloatStateOf(0f) }
    val live = remember(url) { LivePhotoState() }
    val zoomed = scale > 1f + ZoomEpsilon
    val player = rememberLivePhotoPlayer(url, isSettled, zoomed, live)

    // The picture as laid out at rest; a picture whose shape is not known yet fills the box.
    fun shown(): Size {
        val aspect = frame.aspectOf(url) ?: return Size(box.width.toFloat(), box.height.toFloat())
        return restSize(box.width.toFloat(), box.height.toFloat(), aspect)
    }

    // How far the picture may be dragged from centre: what overhangs the box, or nothing. A long
    // strip overhangs the box above and below, which is what lets it scroll.
    fun limit(at: Float): Offset {
        val size = shown()
        return Offset(
            max(0f, (size.width * at - box.width) / 2f),
            max(0f, (size.height * at - box.height) / 2f),
        )
    }

    fun clamp(value: Offset, at: Float): Offset {
        val max = limit(at)
        return Offset(value.x.coerceIn(-max.x, max.x), value.y.coerceIn(-max.y, max.y))
    }

    // A long picture rests showing its top edge, so it is read from the start; every other picture
    // rests centred, with nothing to scroll.
    fun restPosition(): Offset = Offset(0f, limit(1f).y)

    // Zooming by [by] about [focus] keeps the point under it where it is.
    fun zoomedOffset(from: Offset, by: Float, focus: Offset): Offset {
        val centre = Offset(box.width / 2f, box.height / 2f)
        return from * by + (focus - centre) * (1f - by)
    }

    fun toggleZoom(focus: Offset) {
        val startScale = scale
        val startOffset = offset
        val targetScale = if (startScale > 1f + ZoomEpsilon) 1f else DoubleTapZoom
        // Coming back to rest keeps a long strip where it was scrolled to instead of jumping to
        // its top; for everything else there is only the centre to return to.
        val targetOffset = if (targetScale == 1f) {
            clamp(startOffset, 1f)
        } else {
            clamp(zoomedOffset(startOffset, targetScale / startScale, focus), targetScale)
        }
        scope.launch {
            animate(0f, 1f, animationSpec = tween(ZoomMillis)) { t, _ ->
                scale = lerp(startScale, targetScale, t)
                offset = Offset(lerp(startOffset.x, targetOffset.x, t), lerp(startOffset.y, targetOffset.y, t))
            }
        }
    }

    // A flick runs on after the finger, the way a scroll view's does; a slow drag stops where it is
    // let go.
    fun flingBy(velocity: Float) {
        val max = limit(scale).y
        if (max <= 0f || abs(velocity) < FlingMinVelocity) return
        glide.value?.cancel()
        glide.value = scope.launch {
            AnimationState(initialValue = offset.y, initialVelocity = velocity)
                .animateDecay(exponentialDecay<Float>(frictionMultiplier = 1.2f)) {
                    offset = Offset(offset.x, value.coerceIn(-max, max))
                    if (value <= -max || value >= max) cancelAnimation()
                }
        }
    }

    // Pinched under the resting size, the picture goes straight back to it once the fingers leave:
    // a spring, but a critically damped one, so it never overshoots and comes back a second time.
    fun rebound() {
        val from = scale
        if (from >= 1f - ZoomEpsilon) return
        glide.value?.cancel()
        glide.value = scope.launch {
            val settleBack = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )
            animate(from, 1f, animationSpec = settleBack) { value, _ ->
                scale = value
                offset = clamp(offset, value)
            }
        }
    }

    // The picture is put at its resting position as soon as its shape and the box are both known,
    // and pushed back inside the box whenever the shape the decoder reports refines the one the
    // address carried.
    LaunchedEffect(isCurrent, box, frame.aspects[url]) {
        if (box == IntSize.Zero || frame.aspectOf(url) == null) return@LaunchedEffect
        if (isCurrent && !placed) {
            offset = restPosition()
            placed = true
        } else if (placed) {
            offset = clamp(offset, scale)
        }
    }

    // Swiping to another picture puts this one back at rest. The page being swiped to is current
    // before it is settled too, so it is left alone until it is the one being left behind.
    LaunchedEffect(isCurrent, isSettledElsewhere) {
        if (isSettledElsewhere && !isCurrent) {
            glide.value?.cancel()
            scale = 1f
            offset = Offset.Zero
            placed = false
        }
    }
    // The pager's frame only follows the picture on screen.
    LaunchedEffect(isCurrent) {
        if (isCurrent) snapshotFlow { scale > 1f + ZoomEpsilon }.collect { frame.zoomed = it }
    }
    LaunchedEffect(loaded) {
        if (loaded) animate(0f, 1f, animationSpec = tween(LoadFadeMillis)) { value, _ -> reveal = value }
    }

    Box(Modifier.fillMaxSize()) {
        // 视觉层独立做进场与拖拽退出变换；手势层留在原位不跟着移动，保证拖拽距离与甩出速度采样不失真。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!isCurrent) {
                        if (frame.dismiss.active || progress() < 1f) {
                            alpha = 0f
                        } else {
                            settle()
                        }
                        return@graphicsLayer
                    }
                    val d = frame.dismiss
                    when {
                        d.active -> applyDismissDrag(d)
                        else -> {
                            val p = progress()
                            if (p >= 1f) {
                                settle()
                            } else {
                                val host = frame.host
                                // A zoomed picture no longer sits where its thumbnail would crop it from.
                                val liveThumb = if (host != null && !frame.zoomed) {
                                    origins.boundsOf(url, host, density.density)?.also { frame.thumbs[url] = it }
                                } else {
                                    null
                                }
                                val thumb = liveThumb ?: url.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
                                openFrame(p, thumb, frame.aspectOf(url), squircleEnabled)
                            }
                        }
                    }
                },
        ) {
            // The picture and its live-photo video share the zoom transform.
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { box = it }
                    .drawWithContent {
                        pageLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(pageLayer)
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            ) {
                // The picture rests at the size it shows at. A long strip is taller than this box and
                // overhangs it, which is what the gesture layer below scrolls; every other picture
                // rests fitted inside the box.
                val rest = shown()
                val resting = if (rest.height > box.height + 0.5f) {
                    Modifier.requiredSize(
                        with(density) { rest.width.toDp() },
                        with(density) { rest.height.toDp() },
                    )
                } else {
                    Modifier.fillMaxSize()
                }
                Box(Modifier.align(Alignment.Center).then(resting)) {
                    // The thumbnail the list already decoded stands in until the full picture arrives,
                    // and stays under it while it fades in, so the growth out of the thumbnail shows the
                    // picture itself and not an empty frame.
                    if (!loaded || reveal < 1f) {
                        AsyncImage(
                            model = (if (url.isLivePhotoUrl() || isGifUrl(url)) url else thumbnail(url))
                                .takeIf { it.startsWith("http://") || it.startsWith("https://") },
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(source.takeIf { remote })
                            .apply { decodeSize?.let { size(it) } }
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal },
                        onSuccess = { state ->
                            loaded = true
                            val picture = state.painter.intrinsicSize
                            if (picture.width > 0f && picture.height > 0f) {
                                frame.aspects[url] = picture.width / picture.height
                            }
                        },
                        onError = {
                            if (!useOriginal && originalUrl != baseUrl) useOriginal = true else failed = true
                        },
                    )
                }
                if (live.playing && player != null) {
                    val fit = shown()
                    LivePhotoPlayerView(
                        player = player,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(
                                with(density) { fit.width.toDp() },
                                with(density) { fit.height.toDp() },
                            ),
                    )
                }
            }
        }

        // Gestures sit on an untransformed full-screen layer so screen-space drag and fling velocity stay exact.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (!frame.dismiss.active) onTap() },
                        onDoubleTap = { if (!frame.dismiss.active) toggleZoom(it) },
                        onLongPress = { if (!frame.dismiss.active) onLongPress() },
                    )
                }
                .pointerInput(Unit) {
                    var mode = GestureMode.UNDECIDED
                    detectZoomAndPan(
                        onStart = {
                            mode = GestureMode.UNDECIDED
                            glide.value?.cancel()
                            if (frame.dismiss.active) {
                                frame.dismiss.animJob?.cancel()
                            }
                        },
                        onRelease = { velocity, taken ->
                            if (mode == GestureMode.DISMISS || frame.dismiss.active) {
                                currentOnDismissRelease(velocity)
                            } else if (scale < 1f - ZoomEpsilon) {
                                rebound()
                            } else if (taken) {
                                flingBy(velocity.y)
                            }
                        },
                    ) { focus, pan, direction, zoom, pointers ->
                        if (mode == GestureMode.UNDECIDED) {
                            if (frame.dismiss.active) {
                                // 回弹中途被手指再次接住，按当前落点重算锚点，避免画面跳变。
                                mode = GestureMode.DISMISS
                                frame.dismiss.reanchor(focus)
                                frame.dismiss.dragging = true
                            } else if (pointers >= 2 || scale > 1f + ZoomEpsilon) {
                                mode = GestureMode.ZOOM_PAN
                            } else {
                                val moved = clamp(zoomedOffset(offset, 1f, focus) + pan, scale)
                                val longStripScrolls = abs(direction.y) > abs(direction.x) && moved.y != offset.y
                                if (longStripScrolls) {
                                    mode = GestureMode.ZOOM_PAN
                                } else if (
                                    currentCanPage &&
                                    (currentIsPagerScrolling() || abs(direction.x) > abs(direction.y) * 1.25f)
                                ) {
                                    mode = GestureMode.PAGER
                                    return@detectZoomAndPan false
                                } else if (currentCanStartDismiss()) {
                                    mode = GestureMode.DISMISS
                                    frame.dismiss.startDrag(focus)
                                } else {
                                    mode = GestureMode.PAGER
                                    return@detectZoomAndPan false
                                }
                            }
                        }

                        when (mode) {
                            GestureMode.PAGER -> false
                            GestureMode.DISMISS -> {
                                if (pointers == 1) {
                                    frame.dismiss.dragOffset += pan
                                }
                                true
                            }
                            GestureMode.ZOOM_PAN -> {
                                val current = scale
                                val target = (current * zoom).coerceIn(MinZoom, MaxZoom)
                                // Shrinking past the resting size only creeps along, so the picture can go
                                // under the screen's width but feels held; letting go springs it back.
                                val next = if (target < current && current <= 1f) {
                                    current + (target - current) * ReboundResistance
                                } else {
                                    target
                                }
                                val moved = clamp(zoomedOffset(offset, next / current, focus) + pan, next)
                                // A one-finger drag belongs to the picture only where it can actually
                                // travel — up and down a long strip, or around a zoomed one. Across a
                                // picture with nothing to show there, the swipe is the pager's.
                                if (pointers == 1) {
                                    val across = abs(pan.x) >= abs(pan.y)
                                    val travels = if (across) moved.x != offset.x else moved.y != offset.y
                                    if (!travels) return@detectZoomAndPan false
                                }
                                scale = next
                                offset = moved
                                true
                            }
                            GestureMode.UNDECIDED -> false
                        }
                    }
                },
        )

        if (url.isLivePhotoUrl()) {
            val controlsAlpha by animateFloatAsState(
                targetValue = if (frame.dismiss.dragging) 0f else 1f,
                animationSpec = tween(150),
                label = "liveControlsDismiss",
            )
            LivePhotoControls(
                state = live,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .graphicsLayer {
                        alpha = ((progress() - ChromeFadeStart) / (1f - ChromeFadeStart)).coerceIn(0f, 1f) * controlsAlpha
                    }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, bottom = 16.dp),
                onTogglePlayback = {
                    if (!frame.dismiss.dragging) {
                        if (live.playing) {
                            player?.pause()
                            live.playing = false
                        } else if (player != null) {
                            live.playRequest++
                        }
                    }
                },
                onToggleMute = {
                    if (!frame.dismiss.dragging) {
                        live.muted = !live.muted
                    }
                },
            )
        }
    }
}

private const val ZoomMillis = 220

/** 拖拽退出时的最小缩放比例（对齐相册下拉退出的 0.7×）。 */
private const val DragMinScale = 0.7f

/** 拖拽缩放曲线：跟手即刻开始缩小，越拖越柔和（对齐相册 PathInterpolator(0, 0, 0.4, 1)）。 */
private val DragScaleEasing = CubicBezierEasing(0f, 0f, 0.4f, 1f)

/** 拖拽退出时手势与背景状态：支持任意方向拖动与甩出退出，取消时平滑回弹、退出时接力回到原缩略图位置。 */
@Stable
private class DragDismissState {
    var active by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var dragOffset by mutableStateOf(Offset.Zero)
    var pivot by mutableStateOf(Offset.Zero)
    var boxSize by mutableStateOf(IntSize.Zero)
    var animJob: Job? = null

    private fun refSpan(): Float =
        minOf(boxSize.width, boxSize.height).toFloat().coerceAtLeast(1f)

    fun currentScale(): Float {
        val d = dragOffset.getDistance()
        val scaleRange = refSpan() * 0.9f
        val t = DragScaleEasing.transform((d / scaleRange).coerceIn(0f, 1f))
        return lerp(1f, DragMinScale, t)
    }

    fun currentBgAlpha(): Float {
        val d = dragOffset.getDistance()
        val alphaRange = refSpan() * 0.75f
        return (1f - d / alphaRange).coerceIn(0f, 1f)
    }

    fun currentTranslation(): Offset {
        val s = currentScale()
        return dragOffset + pivot * (1f - s)
    }

    fun startDrag(focus: Offset) {
        animJob?.cancel()
        pivot = focus - Offset(boxSize.width / 2f, boxSize.height / 2f)
        dragOffset = Offset.Zero
        active = true
        dragging = true
    }

    fun reanchor(focus: Offset) {
        val curScale = currentScale()
        val curTrans = currentTranslation()
        val newPivot = focus - Offset(boxSize.width / 2f, boxSize.height / 2f)
        pivot = newPivot
        dragOffset = curTrans - newPivot * (1f - curScale)
    }

    fun cancelActive() {
        animJob?.cancel()
        dragging = false
        active = false
        dragOffset = Offset.Zero
    }
}

private fun handleDismissRelease(
    velocity: Velocity,
    dismiss: DragDismissState,
    frame: ViewerFrame,
    origins: ImageOrigins,
    url: String?,
    density: Float,
    squircleEnabled: Boolean,
    scope: CoroutineScope,
    onPopImmediate: () -> Unit,
) {
    dismiss.dragging = false
    val dragPos = dismiss.dragOffset
    val dist = dragPos.getDistance()
    val vel = Offset(velocity.x, velocity.y)
    val speed = vel.getDistance()

    val flingThreshold = 200f * density
    val distanceThreshold = 80f * density
    val backGuard = 36f * density
    val backFlingThreshold = 320f * density
    val dot = dragPos.x * vel.x + dragPos.y * vel.y
    val isFlingBack = dist > backGuard && speed >= backFlingThreshold && dot < -0.6f * dist * speed
    val shouldDismiss = !isFlingBack && (speed >= flingThreshold || dist >= distanceThreshold)

    if (!shouldDismiss) {
        dismiss.animJob?.cancel()
        dismiss.animJob = scope.launch {
            animate(
                typeConverter = Offset.VectorConverter,
                initialValue = dismiss.dragOffset,
                targetValue = Offset.Zero,
                initialVelocity = vel,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 500f,
                    visibilityThreshold = Offset(0.5f, 0.5f),
                ),
            ) { value, _ ->
                dismiss.dragOffset = value
            }
            dismiss.dragOffset = Offset.Zero
            dismiss.active = false
        }
        return
    }

    dismiss.animJob?.cancel()
    val startScale = dismiss.currentScale()
    val startTranslation = dismiss.currentTranslation()
    val startBgAlpha = dismiss.currentBgAlpha()

    val host = frame.host
    val live = if (url != null && host != null && !frame.zoomed) {
        origins.boundsOf(url, host, density)?.also { frame.thumbs[url] = it }
    } else {
        null
    }
    val thumb = live ?: url?.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
    val aspect = url?.let { frame.aspectOf(it) ?: thumb?.let { b -> b.rect.width / b.rect.height } }

    val w = dismiss.boxSize.width.toFloat()
    val h = dismiss.boxSize.height.toFloat()
    val effectiveAspect = aspect ?: thumb?.rect?.let { if (it.height > 0f) it.width / it.height else null }
    val rest = if (w > 0f && h > 0f && effectiveAspect != null) restSize(w, h, effectiveAspect) else null
    val hasThumb = thumb != null && rest != null && rest.height <= h + 0.5f

    val layer = url?.let { frame.pageLayers[it] }
    val onRelease = frame.onReleaseLayer
    if (layer == null || onRelease == null) {
        origins.finishDismissFlight()
        origins.viewing = null
        onPopImmediate()
        return
    }

    val targetTranslation = if (hasThumb) {
        val source = thumb.rect
        Offset(source.center.x - w / 2f, source.center.y - h / 2f)
    } else {
        Offset.Zero
    }
    val maxFlingSpeed = 1200f * density
    val clampedVel = if (speed > maxFlingSpeed && speed > 0f) vel * (maxFlingSpeed / speed) else vel
    val dir = when {
        speed >= flingThreshold && speed > 0f -> vel / speed
        dist > 1f -> dragPos / dist
        else -> Offset(0f, 1f)
    }
    val flyDistance = minOf(w, h).coerceAtLeast(1f) * 0.42f
    val flyTarget = startTranslation + dir * flyDistance

    // 移交图层所有权给宿主飞行层并立刻退栈，底层页面无需等待回缩即可马上滑动或点击。
    origins.finishDismissFlight()
    frame.handedOffLayers.add(layer)
    onPopImmediate()

    val flight = ImageDismissFlight(
        url = url,
        layer = layer,
        onReleaseLayer = onRelease,
        boxSize = dismiss.boxSize,
        density = density,
        squircleEnabled = squircleEnabled,
        startScale = startScale,
        startTranslation = startTranslation,
        startBgAlpha = startBgAlpha,
        flyTarget = flyTarget,
        hasThumb = hasThumb,
        initialThumb = thumb,
        aspect = effectiveAspect,
    )
    origins.launchDismissFlight(
        flight = flight,
        initialTargetTranslation = targetTranslation,
        initialVelocity = clampedVel,
    )
}

private fun GraphicsLayerScope.applyDismissDrag(d: DragDismissState) {
    val scale = d.currentScale()
    val trans = d.currentTranslation()
    alpha = 1f
    transformOrigin = TransformOrigin.Center
    scaleX = scale
    scaleY = scale
    translationX = trans.x
    translationY = trans.y
    shape = RectangleShape
    clip = false
}

private enum class GestureMode {
    UNDECIDED,
    ZOOM_PAN,
    DISMISS,
    PAGER,
}

/**
 * Pinch and drag detection that only consumes what it uses. [onGesture] gets the centroid, the pan,
 * the cumulative direction before touch slop, the zoom factor and the number of fingers down, and
 * returns whether it took the movement; when it did not, the pointer events stay free for a parent
 * such as the pager. [onStart] runs as the fingers land; [onRelease] once they are all up, with the
 * speed the last one left at and whether the gesture took anything at all.
 */
private suspend fun PointerInputScope.detectZoomAndPan(
    onStart: () -> Unit = {},
    onRelease: (velocity: Velocity, taken: Boolean) -> Unit = { _, _ -> },
    onGesture: (focus: Offset, pan: Offset, direction: Offset, zoom: Float, pointers: Int) -> Boolean,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var taken = false
        val tracker = VelocityTracker()
        val touchSlop = viewConfiguration.touchSlop
        val down = awaitFirstDown(requireUnconsumed = false)
        var trackedId = down.id
        tracker.addPointerInputChange(down)
        onStart()
        do {
            val event = awaitPointerEvent()
            val tracked = event.changes.firstOrNull { it.id == trackedId } ?: event.changes.firstOrNull()
            if (tracked != null) {
                if (tracked.id != trackedId) {
                    tracker.resetTracking()
                    trackedId = tracked.id
                }
                tracker.addPointerInputChange(tracked)
            }
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                var direction = panChange
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val zoomMotion = abs(1f - zoom) * event.calculateCentroidSize(useCurrent = false)
                    if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) {
                        pastTouchSlop = true
                        direction = pan
                    }
                }
                if (pastTouchSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                    val pointers = event.changes.count { it.pressed }
                    val focus = event.calculateCentroid(useCurrent = false)
                    if (onGesture(focus, panChange, direction, zoomChange, pointers)) {
                        taken = true
                        event.changes.fastForEach { change: PointerInputChange ->
                            if (change.positionChanged()) change.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
        onRelease(tracker.calculateVelocity(), taken)
    }
}

/**
 * 控件收起时把状态栏一起收起来，长图才能真正顶到屏幕最上沿。隐藏期间从顶部边缘划一下可以
 * 临时叫出状态栏，划走又自动收回。离开这个页面时一定要还原，否则整个 App 都会顶到屏幕边上。
 */
@Composable
private fun ImmersiveStatusBar(hidden: Boolean, leaving: () -> Boolean) {
    val view = LocalView.current
    val closing by remember { derivedStateOf(leaving) }
    val controller = remember(view) {
        view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    // 离开这个页面（含返回手势）时状态栏必须还回去，否则整个 App 都顶到屏幕边上了。
    DisposableEffect(controller) {
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
    LaunchedEffect(controller, hidden, closing) {
        if (controller == null) return@LaunchedEffect
        if (hidden && !closing) {
            // 隐藏期间允许从屏幕边缘划出临时状态栏，划走自动收回。
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

/**
 * Keeps the status and navigation bar icons light while the viewer is up — it is black behind
 * translucent bars in either theme — and puts them back as soon as [leaving] turns true, so they
 * are right again before the page underneath is fully back.
 */
@Composable
private fun LightSystemBarIcons(leaving: () -> Boolean) {
    val view = LocalView.current
    val closing by remember { derivedStateOf(leaving) }
    DisposableEffect(view, closing) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        if (controller != null && !closing) {
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        onDispose {
            if (controller != null && lightStatus != null && lightNavigation != null) {
                controller.isAppearanceLightStatusBars = lightStatus
                controller.isAppearanceLightNavigationBars = lightNavigation
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
