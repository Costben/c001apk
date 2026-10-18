package app.awavenue.c001apk.ui.image

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.media.isLivePhotoUrl
import app.awavenue.c001apk.ui.common.openBrowser
import app.awavenue.c001apk.ui.common.saveImage
import app.awavenue.c001apk.ui.common.saveLivePhoto
import app.awavenue.c001apk.ui.common.shareImage
import app.awavenue.c001apk.ui.common.thumbnail
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.ActionSheetItem
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.LocalRouteTransition
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.icon.extended.Link
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.overlay.OverlayLoadingDialog
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.launch

private val ChromeColor = Color.Black.copy(alpha = 0.45f)
private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f

// The bars come in over the last part of the opening and are gone early in the closing, so they
// never ride along with the shrinking frame.
private const val ChromeFadeStart = 0.6f

// Below this the picture counts as unzoomed, so float residue from pinching never blocks a swipe.
private const val ZoomEpsilon = 0.01f

// The full picture fades in and the loading spinner fades out over this, so neither is a hard cut.
private const val LoadFadeMillis = 200

// A picture already in the cache arrives at once; hold the spinner back so it does not blink.
private const val IndicatorDelayMillis = 150

/**
 * Full-screen picture pager. The picture sits under translucent bars, fitted to the whole screen —
 * by width or by height, whichever binds — and a tap shows or hides the bars. It grows out of the
 * thumbnail that was tapped and shrinks back into the one it ends on.
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
        initialPage = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
    ) { images.size }

    var chromeVisible by remember { mutableStateOf(true) }
    var sheetPage by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val frame = remember { ViewerFrame() }
    val progress: () -> Float = remember(transition) { transition?.progress ?: { 1f } }

    LightSystemBarIcons(leaving = { transition?.exiting?.invoke() == true })

    fun save(urls: List<String>) {
        sheetPage = null
        scope.launch {
            saving = true
            var failed = false
            urls.forEachIndexed { index, url ->
                if (!saveImage(context, url)) {
                    failed = true
                    context.toast("第 ${index + 1} 张图片保存失败，请检查存储权限")
                }
            }
            saving = false
            if (!failed) context.toast("已保存")
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

    Box(Modifier.fillMaxSize().onPlaced { frame.host = it }) {
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = progress()) })

        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无内容", color = Color.White)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val p = progress()
                    if (p >= 1f) {
                        settle()
                        return@graphicsLayer
                    }
                    val url = images.getOrNull(pagerState.currentPage)
                    val host = frame.host
                    // A zoomed picture no longer sits where its thumbnail would crop it from.
                    val live = if (url != null && host != null && !frame.zoomed) {
                        origins.boundsOf(url, host, density)?.also { frame.thumbs[url] = it }
                    } else {
                        null
                    }
                    val thumb = live ?: url?.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
                    openFrame(p, thumb, url?.let { frame.aspectOf(it) })
                },
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1,
            ) { page ->
                ZoomablePage(
                    url = images[page],
                    isCurrent = pagerState.currentPage == page,
                    isSettled = pagerState.settledPage == page,
                    isSettledElsewhere = pagerState.settledPage != page,
                    frame = frame,
                    onTap = { chromeVisible = !chromeVisible },
                    onLongPress = { sheetPage = page },
                )
            }
        }

        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = ((progress() - ChromeFadeStart) / (1f - ChromeFadeStart)).coerceIn(0f, 1f)
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
                        TopBarIconButton(onClick = { navigator.pop() }) {
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
    }

    val page = sheetPage
    if (page != null && page in images.indices) {
        val url = images[page]
        OverlayBottomSheet(show = true, onDismissRequest = { sheetPage = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                if (url.isLivePhotoUrl()) {
                    ActionSheetItem(COUIIcons.Download, "保存实况照片", onClick = { saveLive(url) })
                }
                ActionSheetItem(COUIIcons.Download, "保存图片", onClick = { save(listOf(url)) })
                if (images.size != 1) {
                    ActionSheetItem(COUIIcons.Image, "保存全部图片", onClick = { save(images) })
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

    OverlayLoadingDialog(show = saving, text = "保存中")
}

private const val ChromeFadeMillis = 180

/** What the pager's frame needs to know about the picture, outside any one page. */
@Stable
private class ViewerFrame {
    var host: LayoutCoordinates? = null

    /** True while the picture on screen is zoomed in. */
    var zoomed by mutableStateOf(false)

    /** Width over height per picture, once a page has read it from the decoded image. */
    val aspects = mutableStateMapOf<String, Float>()

    /** Where each thumbnail was last seen, kept for the closing frame while its page is not composed. */
    val thumbs = HashMap<String, ThumbBounds>()

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
 * Lays the whole pager over the thumbnail at [progress] 0 and over the screen at 1. The picture is
 * fitted to the screen at 1, so scaling it up around the screen's centre until it covers the
 * thumbnail, then clipping to the thumbnail's bounds, reproduces the thumbnail's own centre crop
 * exactly; every step between is the same two moves interpolated. Without a thumbnail it just
 * fades and settles in.
 */
private fun GraphicsLayerScope.openFrame(progress: Float, thumb: ThumbBounds?, aspect: Float?) {
    if (thumb == null) {
        settle()
        alpha = progress
        scaleX = lerp(0.92f, 1f, progress)
        scaleY = scaleX
        return
    }
    val width = size.width
    val height = size.height
    val source = thumb.rect
    val fit = fitSize(width, height, aspect ?: (source.width / source.height))
    val cover = max(source.width / fit.width, source.height / fit.height)
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
    )
    clip = true
}

private class CenteredWindow(private val width: Float, private val height: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        return Outline.Rounded(RoundRect(left, top, left + width, top + height, CornerRadius(radius)))
    }
}

/** How a picture of [aspect] (width over height) sits in a box: the larger of the two it can fill. */
private fun fitSize(boxWidth: Float, boxHeight: Float, aspect: Float): Size =
    if (aspect > boxWidth / boxHeight) Size(boxWidth, boxWidth / aspect) else Size(boxHeight * aspect, boxHeight)

@Composable
private fun ZoomablePage(
    url: String,
    isCurrent: Boolean,
    isSettled: Boolean,
    isSettledElsewhere: Boolean,
    frame: ViewerFrame,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var box by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val remote = url.startsWith("http://") || url.startsWith("https://")
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    // The full picture's own alpha, so it eases in over the thumbnail instead of snapping in.
    var reveal by remember(url) { mutableFloatStateOf(0f) }
    val live = remember(url) { LivePhotoState() }
    val zoomed = scale > 1f + ZoomEpsilon
    val player = rememberLivePhotoPlayer(url, isSettled, zoomed, live)

    // The picture as laid out at scale 1; a picture whose shape is not known yet fills the box.
    fun shown(): Size {
        val aspect = frame.aspectOf(url) ?: return Size(box.width.toFloat(), box.height.toFloat())
        return fitSize(box.width.toFloat(), box.height.toFloat(), aspect)
    }

    // How far the picture may be dragged from centre: what overhangs the box, or nothing.
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

    // Zooming by [by] about [focus] keeps the point under it where it is.
    fun zoomedOffset(from: Offset, by: Float, focus: Offset): Offset {
        val centre = Offset(box.width / 2f, box.height / 2f)
        return from * by + (focus - centre) * (1f - by)
    }

    fun toggleZoom(focus: Offset) {
        val startScale = scale
        val startOffset = offset
        val size = shown()
        // From fitted, go to the other fit (a tall picture to its width, a wide one to its height)
        // when that is a real step up; otherwise a fixed magnification.
        val fill = max(box.width / size.width, box.height / size.height)
        val targetScale = if (startScale > 1f + ZoomEpsilon) 1f else if (fill > 1.1f) fill else DoubleTapZoom
        val targetOffset = if (targetScale == 1f) {
            Offset.Zero
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

    // Swiping to another picture puts this one back at fit.
    LaunchedEffect(isSettledElsewhere) {
        if (isSettledElsewhere) {
            scale = 1f
            offset = Offset.Zero
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
        // The picture and its live-photo video share the zoom transform.
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { box = it }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            // The thumbnail the list already decoded stands in until the full picture arrives, and
            // stays under it while it fades in, so the growth out of the thumbnail shows the picture
            // itself and not an empty frame.
            if (!loaded || reveal < 1f) {
                AsyncImage(
                    model = (if (url.isLivePhotoUrl()) url else thumbnail(url))
                        .takeIf { it.startsWith("http://") || it.startsWith("https://") },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url.takeIf { remote })
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal },
                onSuccess = { state ->
                    loaded = true
                    val picture = state.painter.intrinsicSize
                    if (picture.width > 0f && picture.height > 0f) frame.aspects[url] = picture.width / picture.height
                },
                onError = { failed = true },
            )
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

        // Gestures sit above the picture and the video surface, so neither can swallow a swipe.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { toggleZoom(it) },
                        onLongPress = { onLongPress() },
                    )
                }
                .pointerInput(Unit) {
                    detectZoomAndPan { focus, pan, zoom, pointers ->
                        val current = scale
                        // One finger on a fitted picture is a swipe between pictures, not ours.
                        if (pointers == 1 && current <= 1f + ZoomEpsilon) return@detectZoomAndPan false
                        val next = (current * zoom).coerceIn(1f, MaxZoom)
                        val moved = clamp(zoomedOffset(offset, next / current, focus) + pan, next)
                        // A single finger pushing on an edge it has already reached is the pager's.
                        val atEdge = pointers == 1 && abs(pan.x) >= abs(pan.y) && moved.x == offset.x
                        if (atEdge) return@detectZoomAndPan false
                        scale = next
                        offset = if (next <= 1f) Offset.Zero else moved
                        true
                    }
                },
        )

        // Nothing here takes pointer input, so zoom, pan and swipes still reach the gesture layer
        // below. Only the page on screen shows it, and never over a live photo already playing.
        AnimatedVisibility(
            visible = isCurrent && remote && !loaded && !failed && !live.playing,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(LoadFadeMillis, delayMillis = IndicatorDelayMillis)),
            exit = fadeOut(tween(LoadFadeMillis)),
        ) {
            InfiniteProgressIndicator(color = Color.White, size = 24.dp)
        }

        if (url.isLivePhotoUrl()) {
            LivePhotoControls(
                state = live,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, bottom = 16.dp),
                onTogglePlayback = {
                    if (live.playing) {
                        player?.pause()
                        live.playing = false
                    } else if (player != null) {
                        live.playRequest++
                    }
                },
                onToggleMute = { live.muted = !live.muted },
            )
        }
    }
}

private const val ZoomMillis = 220

/**
 * Pinch and drag detection that only consumes what it uses. [onGesture] gets the centroid, the pan,
 * the zoom factor and the number of fingers down, and returns whether it took the movement; when it
 * did not, the pointer events stay free for a parent such as the pager.
 */
private suspend fun PointerInputScope.detectZoomAndPan(
    onGesture: (focus: Offset, pan: Offset, zoom: Float, pointers: Int) -> Boolean,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val zoomMotion = abs(1f - zoom) * event.calculateCentroidSize(useCurrent = false)
                    if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) pastTouchSlop = true
                }
                if (pastTouchSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                    val pointers = event.changes.count { it.pressed }
                    val focus = event.calculateCentroid(useCurrent = false)
                    if (onGesture(focus, panChange, zoomChange, pointers)) {
                        event.changes.fastForEach { change: PointerInputChange ->
                            if (change.positionChanged()) change.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
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
