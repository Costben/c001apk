package app.awavenue.c001apk.ui.image

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import app.awavenue.c001apk.ui.common.saveOriginalImage
import app.awavenue.c001apk.ui.common.saveLivePhoto
import app.awavenue.c001apk.ui.common.shareImage
import app.awavenue.c001apk.ui.common.SUFFIX_THUMBNAIL
import app.awavenue.c001apk.ui.common.thumbnail
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.ActionSheetItem
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.LocalRouteTransition
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
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
import kotlin.math.abs
import kotlin.math.max
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

// The full picture fades in and the loading spinner fades out over this, so neither is a hard cut.
private const val LoadFadeMillis = 200

// A picture already in the cache arrives at once; hold the spinner back so it does not blink.
private const val IndicatorDelayMillis = 150

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

    LightSystemBarIcons(leaving = { transition?.exiting?.invoke() == true })
    // 控件收起时状态栏跟着收，长图才能顶到屏幕最上沿；从顶部划一下可以临时叫出来。
    ImmersiveStatusBar(
        hidden = !chromeVisible,
        leaving = { transition?.exiting?.invoke() == true },
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
                    onLongPress = { if (!saving) sheetPage = page },
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

        if (saveHud.isNotEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    text = saveHud,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 30.dp)
                        .clip(RoundedCornerShape(10.dp))
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
 * Lays the whole pager over the thumbnail at [progress] 0 and over the screen at 1. The picture
 * rests at the same size it settles at, so scaling it up around the screen's centre until it covers
 * the thumbnail, then clipping to the thumbnail's bounds, reproduces the thumbnail's own centre crop
 * exactly; every step between is the same two moves interpolated. Without a thumbnail — and for a
 * long strip, which rests taller than the screen and so cannot be mapped back onto a thumbnail —
 * it just fades and settles in.
 */
private fun GraphicsLayerScope.openFrame(progress: Float, thumb: ThumbBounds?, aspect: Float?) {
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

/**
 * The size a picture of [aspect] (width over height) rests at in a box: fitted inside it, or for a
 * long strip the box's full width, so that its height overruns the box and can be scrolled.
 */
private fun restSize(boxWidth: Float, boxHeight: Float, aspect: Float): Size =
    if (aspect < LongImageAspect) Size(boxWidth, boxWidth / aspect) else fitSize(boxWidth, boxHeight, aspect)

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
                    detectZoomAndPan(
                        onStart = { glide.value?.cancel() },
                        onRelease = { velocity, taken ->
                            if (scale < 1f - ZoomEpsilon) rebound() else if (taken) flingBy(velocity.y)
                        },
                    ) { focus, pan, zoom, pointers ->
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
 * did not, the pointer events stay free for a parent such as the pager. [onStart] runs as the
 * fingers land; [onRelease] once they are all up, with the speed the last one left at and whether
 * the gesture took anything at all.
 */
private suspend fun PointerInputScope.detectZoomAndPan(
    onStart: () -> Unit = {},
    onRelease: (velocity: Velocity, taken: Boolean) -> Unit = { _, _ -> },
    onGesture: (focus: Offset, pan: Offset, zoom: Float, pointers: Int) -> Boolean,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var taken = false
        val tracker = VelocityTracker()
        val touchSlop = viewConfiguration.touchSlop
        val down = awaitFirstDown(requireUnconsumed = false)
        tracker.addPosition(down.uptimeMillis, down.position)
        onStart()
        do {
            val event = awaitPointerEvent()
            event.changes.fastForEach { tracker.addPosition(it.uptimeMillis, it.position) }
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
