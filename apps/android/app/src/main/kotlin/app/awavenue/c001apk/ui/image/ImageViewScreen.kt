package app.awavenue.c001apk.ui.image

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect as AndroidRect
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.awavenue.c001apk.R
import app.awavenue.c001apk.data.ImageBrowseQuality
import app.awavenue.c001apk.media.isLivePhotoUrl
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.SUFFIX_THUMBNAIL
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.ui.common.isGifUrl
import app.awavenue.c001apk.ui.common.openBrowser
import app.awavenue.c001apk.ui.common.originalImageUrl
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.saveLivePhoto
import app.awavenue.c001apk.ui.common.saveOriginalImage
import app.awavenue.c001apk.ui.common.shareImage
import app.awavenue.c001apk.ui.common.thumbnail
import app.awavenue.c001apk.ui.nav.ExpandDurationMillis
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.LocalRouteTransition
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import io.github.suqi8.coui.kmp.basic.BottomFloatingToolbarDefaults
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.HighlightedIconButtonDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.MetaBallParticle
import io.github.suqi8.coui.kmp.basic.MetaBallSurface
import io.github.suqi8.coui.kmp.basic.SpotLightDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.couiTouchMotion
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberCOUITouchMotionState
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f

// A flick down a long strip carries on after the finger is lifted; below this speed it is just a
// slow drag and stops where it is.
private const val FlingMinVelocity = 350f

// Pinching below the resting size is allowed so the picture does not feel walled in, but only by
// this much and only at this fraction of the pinch's pace; letting go springs it back.
private const val MinZoom = 0.5f
private const val ReboundResistance = 0.5f

// Ceiling on a long strip's decoded pixels, so that a very long one cannot exhaust the heap.
private const val MaxDecodePixels = 12_000_000

// 顶栏进场只在最后这一段出现，退场开头就消失，不跟着画面一起缩。
private const val ChromeFadeStart = 0.6f

// Below this the picture counts as unzoomed, so float residue from pinching never blocks a swipe.
private const val ZoomEpsilon = 0.01f

// Double tap and toggle zoom animation duration.
private const val ZoomMillis = 220

// 整图淡入、缩略图淡出用掉的时长，两边都不会硬切。
private const val LoadFadeMillis = 200

// 看图页控件统一使用与 Cobo COUI 相同的 36dp 高度与 HighlightedIconButton 材质体系。
private val MediaControlHeight = 36.dp

/**
 * 顶栏渐变衬底（取自 Cobo COUI）：纯白图片顶到状态栏时仍能稳住顶部控件与状态栏的对比度。
 */
private val ChromeScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.55f),
    0.62f to Color.Black.copy(alpha = 0.32f),
    1f to Color.Transparent,
)

/** How far the scrim keeps fading below the controls before it reaches transparent. */
private val ScrimTail = 34.dp

/**
 * 与 Cobo COUI [HighlightedIconButton] 完全统一材质的胶囊控件：复用 [HighlightedIconButtonDefaults]
 * 的填充色、边缘高光、暗色焦散/亮色投影、[couiTouchMotion] 聚光与按压缩放。
 */
@Composable
private fun MediaMaterialPill(
    modifier: Modifier = Modifier,
    height: Dp = MediaControlHeight,
    horizontalPadding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val squircleEnabled = isSquircleEnabled()
    val capsuleShape = remember(squircleEnabled) { CapsuleOutlineShape(squircleEnabled = squircleEnabled) }
    val touchMotion = rememberCOUITouchMotionState()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 1f else 0f,
        animationSpec = HighlightedIconButtonDefaults.PressSpring,
        label = "mediaMaterialPillPress",
    )
    val scale = 1f - (1f - HighlightedIconButtonDefaults.PressedScale) * pressProgress
    val brighten = HighlightedIconButtonDefaults.PressedFillBrighten * pressProgress
    val fillColor = colorLerp(
        HighlightedIconButtonDefaults.fillCenter(isDark),
        Color.White,
        brighten,
    ).copy(alpha = HighlightedIconButtonDefaults.FillAlpha)
    val contentColor = colorLerp(
        scheme.onBackground,
        Color.White,
        HighlightedIconButtonDefaults.PressedContentBrighten * pressProgress,
    )
    val currentOnClick by rememberUpdatedState(onClick)
    var pillWidthPx by remember { mutableIntStateOf(0) }
    val pillWidth = with(density) { pillWidthPx.toDp() }
    val clickableModifier = remember(onClick != null, interactionSource) {
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { currentOnClick?.invoke() },
            )
        } else {
            Modifier
        }
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .height(height)
                .onSizeChanged { pillWidthPx = it.width }
                .couiTouchMotion(
                    enabled = onClick != null,
                    motionState = touchMotion,
                    pressScaleEnabled = false,
                    shape = capsuleShape,
                    lightParams = SpotLightDefaults.circleParams1(isDark),
                    lightEnabled = onClick != null,
                    extraTransform = {
                        scaleX += scale - 1f
                        scaleY += scale - 1f
                    },
                )
                .then(clickableModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (!isDark && pillWidthPx > 0) {
                Box(
                    modifier = Modifier
                        .size(pillWidth, height)
                        .dropShadow(shape = capsuleShape, shadow = BottomFloatingToolbarDefaults.Shadow),
                )
            }
            MetaBallSurface(
                particles = if (pillWidthPx > 0) {
                    listOf(
                        MetaBallParticle(
                            centerX = pillWidth / 2,
                            centerY = height / 2,
                            width = pillWidth,
                            height = height,
                            cornerRadius = height / 2,
                        ),
                    )
                } else {
                    emptyList()
                },
                modifier = Modifier.matchParentSize(),
                color = fillColor,
                backdrop = null,
                rimColor = Color.White.copy(
                    alpha = HighlightedIconButtonDefaults.stroke(isDark).edgeAlpha * (1f - pressProgress).coerceIn(0f, 1f),
                ),
                rimBottomScale = 1f,
                bottomGlowColor = if (isDark) {
                    HighlightedIconButtonDefaults.DarkHaloColor.copy(
                        alpha = HighlightedIconButtonDefaults.DarkHaloColor.alpha * (1f - pressProgress).coerceIn(0f, 1f),
                    )
                } else {
                    null
                },
                continuousCurve = squircleEnabled,
            )
            Box(
                modifier = Modifier
                    .height(height)
                    .padding(horizontal = horizontalPadding),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

/** 看图页左下角那颗「实况」胶囊（与 Cobo COUI 完全一致）。 */
@Composable
private fun LivePill() {
    MediaMaterialPill {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_live_photo),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = LocalContentColor.current,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "实况",
                color = LocalContentColor.current,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Full-screen picture pager ported from Cobo COUI, integrated with Coolapk's live-photo &
 * non-blocking dismiss-flight pipeline.
 */
@Composable
fun ImageViewScreen(images: List<String>, initialPage: Int) {
    val app = LocalApp.current
    val settings = app.settings
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

    var chrome by remember { mutableStateOf(true) }
    var original by remember { mutableStateOf(settings.imageBrowseQuality == ImageBrowseQuality.Original) }
    var menu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf("") }
    var hudTick by remember { mutableIntStateOf(0) }
    val precise = rememberPreciseClickState()
    val frame = remember { ViewerFrame() }
    val squircleEnabled = isSquircleEnabled()
    val progress: () -> Float = remember(transition) { transition?.progress ?: { 1f } }
    val transitionOffset: () -> Offset = remember(transition) { transition?.offset ?: { Offset.Zero } }
    val currentUrl = images.getOrNull(pagerState.currentPage).orEmpty()
    val currentIsLive = currentUrl.isLivePhotoUrl()
    val viewerOwner = remember { Any() }

    fun flash(text: String) {
        hud = text
        hudTick++
    }

    LaunchedEffect(hudTick) {
        if (hud.isNotEmpty()) {
            delay(1600)
            hud = ""
        }
    }

    val commitBackHandler: (Float, Offset, Offset, () -> Unit) -> Boolean =
        remember(images, pagerState, origins, frame, density, squircleEnabled, progress) {
            handler@{ remaining, offset, velocity, onPop ->
                if (frame.dismiss.active) return@handler false
                val url = images.getOrNull(pagerState.currentPage)
                launchBackExitFlight(
                    remaining = minOf(remaining, progress()),
                    offset = offset,
                    velocity = velocity,
                    frame = frame,
                    origins = origins,
                    url = url,
                    density = density,
                    squircleEnabled = squircleEnabled,
                    onPop = onPop,
                )
            }
        }

    // 记下正在看的是哪一张：被它盖住的缩略图和实况角标要等这一页撤走再恢复显示。
    SideEffect {
        origins.beginViewing(viewerOwner, currentUrl.ifEmpty { null })
        origins.onCommitBack = commitBackHandler
    }
    DisposableEffect(viewerOwner) {
        onDispose {
            if (origins.onCommitBack === commitBackHandler) origins.onCommitBack = null
            origins.endViewing(viewerOwner)
        }
    }

    val leaving: () -> Boolean = { transition?.exiting?.invoke() == true }
    LightSystemBarIcons(leaving = leaving)
    ImmersiveStatusBar(
        hidden = !chrome,
        leaving = leaving,
    )

    // 长按菜单打开时，优先拦截返回键以收起菜单
    BackHandler(enabled = menu) {
        menu = false
    }

    fun saveOne(url: String, asLive: Boolean = url.isLivePhotoUrl()) {
        if (saving || url.isBlank()) return
        scope.launch {
            saving = true
            val saved = if (asLive) {
                flash("正在保存实况图…")
                saveLivePhoto(context, url)
            } else {
                flash("正在保存原图…")
                saveOriginalImage(context, url)
            }
            saving = false
            flash(
                when {
                    saved && asLive -> "已保存实况图到相册"
                    saved -> "已保存到相册"
                    asLive -> "实况图保存失败"
                    else -> "保存失败"
                },
            )
        }
    }

    fun saveAll(urls: List<String>) {
        if (saving || urls.isEmpty()) return
        scope.launch {
            saving = true
            var ok = 0
            var liveOk = 0
            urls.forEachIndexed { idx, url ->
                val isLiveItem = url.isLivePhotoUrl()
                flash(
                    if (isLiveItem) {
                        "正在保存实况图 ${idx + 1}/${urls.size}…"
                    } else {
                        "正在保存 ${idx + 1}/${urls.size}…"
                    },
                )
                val saved = if (isLiveItem) saveLivePhoto(context, url) else saveOriginalImage(context, url)
                if (saved) {
                    ok++
                    if (isLiveItem) liveOk++
                }
            }
            saving = false
            flash(
                when {
                    ok == urls.size && liveOk == urls.size -> "已保存 $ok 张实况图到相册"
                    ok == urls.size && liveOk > 0 -> "已保存 $ok 张到相册（含 $liveOk 张实况图）"
                    ok == urls.size -> "已保存 $ok 张到相册"
                    else -> "已保存 $ok/${urls.size} 张"
                },
            )
        }
    }

    val backdrop = rememberGlassBackdrop()

    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { frame.host = it }
            .onSizeChanged { frame.dismiss.boxSize = it },
    ) {
        // 下面那一页要一直看得见，黑色底随进度或拖拽距离变化。
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
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
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = images.size > 1 && !frame.dismiss.active,
                modifier = Modifier
                    .fillMaxSize()
                    .glassSource(backdrop),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1,
            ) { page ->
                val pageUrl = images[page]
                Box(
                    Modifier
                        .fillMaxSize()
                        .preciseClickAnchor(precise),
                ) {
                    ZoomablePage(
                        url = pageUrl,
                        thumbModel = origins.modelOf(pageUrl),
                        thumbCacheKey = origins.cacheKeyOf(pageUrl),
                        original = original,
                        isCurrent = pagerState.currentPage == page,
                        isSettledElsewhere = pagerState.settledPage != page,
                        canPage = images.size > 1,
                        isPagerScrolling = { abs(pagerState.currentPageOffsetFraction) > 0.01f },
                        canStartDismiss = {
                            transition?.exiting?.invoke() != true &&
                                progress() >= 0.35f &&
                                transitionOffset() == Offset.Zero
                        },
                        isOpening = { transition?.exiting?.invoke() != true },
                        progress = progress,
                        transitionOffset = transitionOffset,
                        origins = origins,
                        squircleEnabled = squircleEnabled,
                        frame = frame,
                        chromeVisible = chrome && !frame.dismiss.dragging,
                        onTap = { if (!frame.dismiss.active) chrome = !chrome },
                        onLongPress = {
                            if (!saving && !frame.dismiss.active) {
                                menu = true
                                chrome = true
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
        }

        if (chrome) {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
                val dismissChromeAlpha by animateFloatAsState(
                    targetValue = if (frame.dismiss.dragging) 0f else 1f,
                    animationSpec = tween(150),
                    label = "dismissChrome",
                )
                // 顶栏与底部实况控件共用同一条曲线：进场后半段出现、退场开头立刻隐去。
                val chromeFade: () -> Float = {
                    ((progress() - ChromeFadeStart) / (1f - ChromeFadeStart)).coerceIn(0f, 1f) * dismissChromeAlpha
                }

                // 顶栏：左侧关闭、居中页码、右侧「原图」+ 下载，全部对齐 Cobo COUI。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = chromeFade() }
                        .align(Alignment.TopCenter)
                        .background(ChromeScrim)
                        .windowInsetsPadding(topInsets)
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = ScrimTail),
                ) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        HighlightedIconButton(
                            size = MediaControlHeight,
                            onClick = {
                                if (!frame.dismiss.dragging) {
                                    frame.dismiss.cancelActive()
                                    val handled = commitBackHandler(1f, Offset.Zero, Offset.Zero) {
                                        navigator.popImmediate()
                                    }
                                    if (!handled) navigator.pop()
                                }
                            },
                        ) {
                            Icon(COUIIcons.Close, "关闭", Modifier.size(18.dp))
                        }
                    }

                    if (images.isNotEmpty()) {
                        Box(Modifier.align(Alignment.Center)) {
                            MediaMaterialPill {
                                Text(
                                    "${pagerState.currentPage + 1} / ${images.size}",
                                    color = LocalContentColor.current,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MediaMaterialPill(
                                onClick = {
                                    if (!frame.dismiss.dragging) {
                                        original = !original
                                        flash(if (original) "已切换到原图" else "已切回标准尺寸")
                                    }
                                },
                            ) {
                                Text(
                                    "原图",
                                    color = if (original) COUITheme.colorScheme.primary else LocalContentColor.current,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            HighlightedIconButton(
                                size = MediaControlHeight,
                                onClick = {
                                    if (!frame.dismiss.dragging && currentUrl.isNotBlank()) {
                                        saveOne(currentUrl, asLive = currentIsLive)
                                    }
                                },
                            ) {
                                Icon(
                                    COUIIcons.Download,
                                    if (currentIsLive) "保存实况图" else "保存原图",
                                    Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                // 底部实况控件（左下角实况胶囊 + 右下角声音开关）：与顶部控件共用同一套 HighlightedIconButton 材质。
                if (currentIsLive) {
                    val liveSound = settings.livePhotoSound
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .graphicsLayer { alpha = chromeFade() }
                            .navigationBarsPadding()
                            .padding(start = 12.dp, bottom = 12.dp),
                    ) {
                        LivePill()
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .graphicsLayer { alpha = chromeFade() }
                            .navigationBarsPadding()
                            .padding(end = 12.dp, bottom = 12.dp),
                    ) {
                        HighlightedIconButton(
                            size = MediaControlHeight,
                            onClick = {
                                if (!frame.dismiss.dragging) {
                                    val next = !liveSound
                                    settings.updateLivePhotoSound(next)
                                    flash(if (next) "已开启实况声音" else "已静音实况")
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (liveSound) AppIcons.VolumeOn else AppIcons.VolumeOff,
                                contentDescription = if (liveSound) "实况静音" else "开启实况声音",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        // 长按呼出 COUI 原生玻璃悬浮菜单（OverlayListPopup + PreciseClickState），与 Cobo COUI 一致。
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val menuActions = remember(images, pagerState.currentPage, currentUrl, currentIsLive, saving) {
                val hasLive = images.any { it.isLivePhotoUrl() }
                buildList<Pair<String, () -> Unit>> {
                    if (currentUrl.isNotBlank()) {
                        if (currentIsLive) {
                            add("保存实况图" to { saveOne(currentUrl, asLive = true) })
                        }
                        add("保存原图" to { saveOne(currentUrl, asLive = false) })
                        if (images.size > 1) {
                            val batchLabel = if (hasLive) {
                                "一键保存全部（${images.size} 张，含实况）"
                            } else {
                                "一键保存原图（${images.size} 张）"
                            }
                            add(batchLabel to { saveAll(images) })
                        }
                        add(
                            "分享" to {
                                if (!saving) {
                                    scope.launch {
                                        saving = true
                                        val ok = shareImage(context, currentUrl)
                                        saving = false
                                        if (!ok) flash("图片分享失败，请检查网络")
                                    }
                                }
                            },
                        )
                        add(
                            "复制链接" to {
                                context.copyText(currentUrl)
                                flash("已复制链接")
                            },
                        )
                        add("在浏览器中打开" to { context.openBrowser(currentUrl) })
                    }
                }
            }
            if (menuActions.isNotEmpty()) {
                OverlayListPopup(
                    show = menu,
                    onDismissRequest = { menu = false },
                    preciseClickState = precise,
                ) {
                    val dismiss = LocalDismissState.current
                    ListPopupColumn {
                        menuActions.forEachIndexed { idx, (label, action) ->
                            DropdownImpl(
                                text = label,
                                optionSize = menuActions.size,
                                isSelected = false,
                                index = idx,
                                onSelectedIndexChange = {
                                    action()
                                    dismiss?.invoke()
                                },
                            )
                        }
                    }
                }
            }
        }

        // 状态与保存进度浮字（对齐 Cobo COUI SaveHud）。
        if (hud.isNotEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    text = hud,
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
}

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

    fun aspectOf(url: String, origins: ImageOrigins? = null): Float? {
        aspects[url]?.let { return it }
        origins?.aspectOf(url)?.let { return it }
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

private fun GraphicsLayerScope.openFrame(
    progress: Float,
    thumb: ThumbBounds?,
    aspect: Float?,
    squircleEnabled: Boolean,
    opening: Boolean = false,
) {
    val source = thumb?.rect
    val rest = (aspect ?: source?.let { it.width / it.height })
        ?.let { restSize(size.width, size.height, it) }
    val isLongImage = rest != null && rest.height > size.height + 0.5f
    if (source == null || rest == null || (opening && isLongImage)) {
        settle()
        alpha = progress
        scaleX = lerp(0.92f, 1f, progress)
        scaleY = scaleX
        return
    }
    val width = size.width
    val height = size.height
    // 长图预测性返回跟手期间，直接把当前可见视口当成满屏画面向原缩略图中心裁切缩回。
    val targetRest = if (isLongImage) Size(width, height) else rest
    val cover = max(source.width / targetRest.width, source.height / targetRest.height)
    val scale = lerp(cover, 1f, progress)
    alpha = 1f
    transformOrigin = TransformOrigin.Center
    scaleX = scale
    scaleY = scale
    translationX = lerp(source.center.x, width / 2f, progress) - width / 2f
    translationY = lerp(source.center.y, height / 2f, progress) - height / 2f
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
    thumbModel: String?,
    thumbCacheKey: MemoryCache.Key?,
    original: Boolean,
    isCurrent: Boolean,
    isSettledElsewhere: Boolean,
    canPage: Boolean,
    isPagerScrolling: () -> Boolean,
    canStartDismiss: () -> Boolean,
    isOpening: () -> Boolean,
    progress: () -> Float,
    transitionOffset: () -> Offset,
    origins: ImageOrigins,
    squircleEnabled: Boolean,
    frame: ViewerFrame,
    chromeVisible: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismissRelease: (Velocity) -> Unit,
) {
    val app = LocalApp.current
    val settings = app.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val transition = LocalRouteTransition.current
    val haptic = LocalHapticFeedback.current
    val couiHaptic = rememberCouiHaptic()
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
    var placed by remember(url) { mutableStateOf(false) }
    val glide = remember { mutableStateOf<Job?>(null) }
    var edgeHit by remember(url) { mutableIntStateOf(0) }

    val remote = url.startsWith("http://") || url.startsWith("https://")
    val isLive = url.isLivePhotoUrl()
    val isGif = isGifUrl(url)
    val baseUrl = remember(url) { if (url.contains(SUFFIX_THUMBNAIL)) originalImageUrl(url) else url }
    val originalUrl = remember(baseUrl) { originalImageUrl(baseUrl) }
    var fallbackToOriginal by remember(url) { mutableStateOf(false) }
    val targetUrl = if (original || fallbackToOriginal) originalUrl else baseUrl

    var loaded by remember(url) { mutableStateOf(false) }
    // 保留上一张已解码的 Painter 与缓存键，切原图或重解码时垫在下面，不出现空白闪烁。
    var settledPainter by remember(url) { mutableStateOf<Painter?>(null) }
    var lastCacheKey by remember(url) { mutableStateOf<MemoryCache.Key?>(null) }
    // 首帧同步从 Coil 内存缓存取缩略图 Bitmap，避免 AsyncImage 等布局测量导致 1~2 帧白/黑闪。
    val syncThumbPainter = remember(url, thumbCacheKey, context) {
        thumbCacheKey?.let { key ->
            val image = context.imageLoader.memoryCache?.get(key)?.image
            if (image is BitmapImage) BitmapPainter(image.bitmap.asImageBitmap()) else null
        }
    }
    var localImageFile by remember(url, targetUrl) {
        val pre = if (!remote) {
            File(targetUrl).takeIf { it.exists() && it.length() > 0L }
        } else {
            mediaCacheFile(context, targetUrl, "img").takeIf { it.length() > 0L }
        }
        mutableStateOf(pre)
    }

    // 实况图播放状态机与缓存（直接采用 Cobo COUI 的状态机与交互逻辑）
    val playback = remember(url) { LivePhotoPlaybackState() }
    val liveAutoPlay = settings.livePhotoAutoPlay
    val liveSound = settings.livePhotoSound
    var liveReady by remember(url) { mutableStateOf(false) }
    var liveFailed by remember(url) { mutableStateOf(false) }
    var liveFile by remember(url) {
        mutableStateOf(
            if (isLive) mediaCacheFile(context, url, "mp4").takeIf { it.length() > 0L } else null,
        )
    }

    LaunchedEffect(url, targetUrl, isLive, isGif, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        if (!isGif) {
            launch {
                localImageFile = cacheImageFile(context, app, targetUrl)
            }
        }
        if (isLive && liveFile == null && !liveFailed) {
            val cached = cacheLivePhotoVideo(context, app, url)
            if (cached != null) {
                liveFile = cached
            } else {
                liveFailed = true
            }
        }
    }

    val canLivePlay = liveFile != null &&
        !liveFailed &&
        isCurrent &&
        scale <= 1f + ZoomEpsilon &&
        !frame.dismiss.active &&
        transition?.exiting?.invoke() != true
    val playing = canLivePlay && playback.isPlaying
    val livePlayer = remember(liveFile) {
        val file = liveFile
        if (file == null) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                // 不主动抢占系统音频焦点，避免滑过实况图时打断后台音乐；仅按开关控制音量。
                volume = if (settings.livePhotoSound) 1f else 0f
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        liveReady = true
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            playback.onPlaybackEnded()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        liveFailed = true
                        playback.onInterrupted()
                        Log.e("LivePhoto", "${error.errorCodeName} ${file.name}", error)
                    }
                })
                prepare()
            }
        }
    }
    LaunchedEffect(livePlayer, liveSound) {
        livePlayer?.volume = if (liveSound) 1f else 0f
    }
    DisposableEffect(livePlayer) {
        onDispose { livePlayer?.release() }
    }
    DisposableEffect(lifecycleOwner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                playback.onInterrupted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            playback.onLeftPage()
        }
    }
    LaunchedEffect(canLivePlay) {
        if (!canLivePlay) {
            playback.onInterrupted()
        }
    }
    LaunchedEffect(
        isLive,
        liveAutoPlay,
        livePlayer,
        liveFailed,
        isCurrent,
        isSettledElsewhere,
        playback.autoPlayedForVisit,
    ) {
        if (!isLive ||
            !liveAutoPlay ||
            livePlayer == null ||
            liveFailed ||
            !isCurrent ||
            isSettledElsewhere ||
            playback.autoPlayedForVisit
        ) {
            return@LaunchedEffect
        }
        snapshotFlow {
            progress() >= 0.98f &&
                !isPagerScrolling() &&
                !frame.dismiss.active &&
                transition?.exiting?.invoke() != true &&
                scale <= 1f + ZoomEpsilon
        }.first { it }
        playback.tryAutoPlay(enabled = true)
    }
    LaunchedEffect(livePlayer, playing, playback.holdSession) {
        val player = livePlayer ?: return@LaunchedEffect
        if (playing) {
            if (playback.holding) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            player.seekTo(0)
            player.play()
        } else {
            // 停在松手或播完这一帧上淡回静图。先跳回开头再淡出，第一帧会在过渡里闪一下。
            player.pause()
            delay(LiveFadeMillis.toLong())
            if (!playing) player.seekTo(0)
        }
    }

    var reveal by remember(url) { mutableFloatStateOf(if (syncThumbPainter != null) 0f else 1f) }
    var skipNextFade by remember(url) { mutableStateOf(false) }

    val effectiveAspect = frame.aspectOf(url, origins)
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val decodeSize = effectiveAspect?.takeIf { it < LongImageAspect && !isGif }?.let { aspect ->
        val width = screenWidthPx.coerceAtLeast(1)
        val height = (width / aspect).toInt().coerceAtLeast(1)
        coil3.size.Size(width, minOf(height, (MaxDecodePixels / width).coerceAtLeast(1)))
    }

    val model: Any = remember(url, targetUrl, original, isGif, context, decodeSize) {
        ImageRequest.Builder(context)
            .data(targetUrl.takeIf { remote } ?: targetUrl)
            .apply {
                (lastCacheKey ?: thumbCacheKey)?.let { placeholderMemoryCacheKey(it) }
                if (original && !isGif) {
                    size(coil3.size.Size.ORIGINAL)
                    maxBitmapSize(coil3.size.Size(8192, 8192))
                } else if (decodeSize != null) {
                    size(decodeSize)
                    maxBitmapSize(coil3.size.Size(decodeSize.width, 8192))
                }
            }
            .build()
    }

    fun shown(): Size {
        val aspect = effectiveAspect ?: return Size(box.width.toFloat(), box.height.toFloat())
        return restSize(box.width.toFloat(), box.height.toFloat(), aspect)
    }

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

    fun restPosition(): Offset = Offset(0f, limit(1f).y)

    fun zoomedOffset(from: Offset, by: Float, focus: Offset): Offset {
        val centre = Offset(box.width / 2f, box.height / 2f)
        return from * by + (focus - centre) * (1f - by)
    }

    fun toggleZoom(focus: Offset) {
        val startScale = scale
        val startOffset = offset
        val targetScale = if (startScale > 1f + ZoomEpsilon) 1f else DoubleTapZoom
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

    fun flingBy(velocity: Float) {
        val max = limit(scale).y
        if (max <= 0f || abs(velocity) < FlingMinVelocity) return
        if ((offset.y <= -max + 0.5f && velocity < 0f) || (offset.y >= max - 0.5f && velocity > 0f)) return
        glide.value?.cancel()
        glide.value = scope.launch {
            AnimationState(initialValue = offset.y, initialVelocity = velocity)
                .animateDecay(exponentialDecay(frictionMultiplier = 1.2f)) {
                    offset = Offset(offset.x, value.coerceIn(-max, max))
                    when {
                        value <= -max -> {
                            if (edgeHit != -1) {
                                edgeHit = -1
                                couiHaptic(CouiHapticEffect.EdgeList)
                            }
                            cancelAnimation()
                        }
                        value >= max -> {
                            if (edgeHit != 1) {
                                edgeHit = 1
                                couiHaptic(CouiHapticEffect.EdgeList)
                            }
                            cancelAnimation()
                        }
                        value > -max + 2f && value < max - 2f -> edgeHit = 0
                    }
                }
        }
    }

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

    LaunchedEffect(isCurrent, box, effectiveAspect) {
        if (box == IntSize.Zero || effectiveAspect == null) return@LaunchedEffect
        if (isCurrent && !placed) {
            offset = restPosition()
            placed = true
        } else if (placed) {
            offset = clamp(offset, scale)
        }
    }

    LaunchedEffect(isCurrent, isSettledElsewhere) {
        if (isSettledElsewhere && !isCurrent) {
            glide.value?.cancel()
            scale = 1f
            offset = Offset.Zero
            placed = false
        }
    }

    LaunchedEffect(isCurrent) {
        if (isCurrent) snapshotFlow { scale > 1f + ZoomEpsilon }.collect { frame.zoomed = it }
    }

    LaunchedEffect(loaded, skipNextFade) {
        if (!loaded) return@LaunchedEffect
        if (skipNextFade || reveal >= 1f) {
            reveal = 1f
        } else {
            animate(0f, 1f, animationSpec = tween(LoadFadeMillis)) { value, _ -> reveal = value }
        }
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
                            val off = transitionOffset()
                            if (p >= 1f && off == Offset.Zero) {
                                settle()
                            } else {
                                if (p >= 1f) {
                                    settle()
                                } else {
                                    val host = frame.host
                                    val liveThumb = if (host != null && !frame.zoomed) {
                                        origins.boundsOf(url, host, density.density)?.also { frame.thumbs[url] = it }
                                    } else {
                                        null
                                    }
                                    val thumb = liveThumb ?: url.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
                                    openFrame(
                                        progress = p,
                                        thumb = thumb,
                                        aspect = frame.aspectOf(url, origins) ?: thumb?.aspect,
                                        squircleEnabled = squircleEnabled,
                                        opening = isOpening(),
                                    )
                                }
                                translationX += off.x
                                translationY += off.y
                            }
                        }
                    }
                },
        ) {
            val rest = shown()
            val restPos = if (box != IntSize.Zero && effectiveAspect != null) restPosition() else Offset.Zero
            val baseOffset = if (isCurrent && !placed && effectiveAspect != null) restPos else offset
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { box = it }
                    .clipToBounds()
                    .drawWithContent {
                        // 裁剪到屏幕视口尺寸，确保长图或放大后只截取当前屏幕可见部分用于退场动画。
                        pageLayer.clip = true
                        pageLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(pageLayer)
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = baseOffset.x
                        translationY = baseOffset.y
                    },
            ) {
                val resting = if (rest.height > box.height + 0.5f) {
                    Modifier.requiredSize(
                        with(density) { rest.width.toDp() },
                        with(density) { rest.height.toDp() },
                    )
                } else {
                    Modifier.fillMaxSize()
                }
                Box(Modifier.align(Alignment.Center).then(resting)) {
                    // 信息流里那张图先顶上：优先同步画出内存缓存里的缩略图，首帧零空白；
                    // 整图到了再盖上去，切原图时也用 settledPainter 垫底，不出现闪烁。
                    val thumbScale = if (rest.height > box.height + 0.5f) ContentScale.FillWidth else ContentScale.Fit
                    val thumbAlign = if (rest.height > box.height + 0.5f) Alignment.TopCenter else Alignment.Center
                    val fallbackThumbModel = thumbModel
                        ?: (if (isLive || isGif) url else thumbnail(url))
                            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    if (!loaded || reveal < 1f) {
                        if (syncThumbPainter != null) {
                            Image(
                                painter = syncThumbPainter,
                                contentDescription = null,
                                contentScale = thumbScale,
                                alignment = thumbAlign,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (fallbackThumbModel != null) {
                            AsyncImage(
                                model = fallbackThumbModel,
                                contentDescription = null,
                                contentScale = thumbScale,
                                alignment = thumbAlign,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    settledPainter?.let { prev ->
                        Image(
                            painter = prev,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal },
                        onSuccess = { state ->
                            if (!loaded && (state.result.dataSource == DataSource.MEMORY_CACHE ||
                                    state.result.dataSource == DataSource.MEMORY)) {
                                skipNextFade = true
                            }
                            settledPainter = state.painter
                            lastCacheKey = state.result.memoryCacheKey
                            loaded = true
                            val picture = state.painter.intrinsicSize
                            if (picture.width > 0f && picture.height > 0f) {
                                frame.aspects[url] = picture.width / picture.height
                            }
                        },
                        onError = {
                            if (!fallbackToOriginal && originalUrl != baseUrl) {
                                fallbackToOriginal = true
                            }
                        },
                    )
                    val file = localImageFile
                    if (file != null && !isGif && box != IntSize.Zero) {
                        TiledLongImageOverlay(
                            file = file,
                            original = original,
                            screenWidthPx = screenWidthPx,
                            box = box,
                            scale = scale,
                            offset = baseOffset,
                            rest = rest,
                            onAspect = { aspect ->
                                if (url !in frame.aspects) frame.aspects[url] = aspect
                            },
                        )
                    }
                }
            }

            if (livePlayer != null) {
                val liveAlpha by animateFloatAsState(
                    targetValue = if (playing && liveReady) 1f else 0f,
                    animationSpec = tween(LiveFadeMillis),
                    label = "live",
                )
                FittedVideoTextureOverlay(
                    player = livePlayer,
                    alpha = liveAlpha,
                    rest = shown(),
                    box = box,
                    scale = scale,
                    offset = baseOffset,
                )
            }
        }

        // 常态由左下角那枚「实况」胶囊表达，这里只报下载和失败这两种临时状态。
        val liveStatus = if (!isLive) {
            ""
        } else {
            when {
                liveFailed -> "实况播放失败"
                liveFile == null -> "实况下载中"
                else -> ""
            }
        }
        if (liveStatus.isNotEmpty() && chromeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .height(MediaControlHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = liveStatus,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Gestures sit on an untransformed full-screen layer so screen-space drag and fling velocity stay exact.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(isLive) {
                    if (!isLive) {
                        detectTapGestures(
                            onTap = { if (!frame.dismiss.active) onTap() },
                            onDoubleTap = { if (!frame.dismiss.active) toggleZoom(it) },
                            onLongPress = { if (!frame.dismiss.active) onLongPress() },
                        )
                    } else {
                        detectLivePress(
                            onTap = { if (!frame.dismiss.active) onTap() },
                            onDoubleTap = { if (!frame.dismiss.active) toggleZoom(it) },
                            onPlay = { if (!frame.dismiss.active) playback.onLongPressStart() },
                            onStop = { playback.onLongPressEnd() },
                        )
                    }
                }
                .pointerInput(Unit) {
                    var mode = GestureMode.UNDECIDED
                    detectZoomAndPan(
                        onStart = {
                            mode = GestureMode.UNDECIDED
                            glide.value?.cancel()
                            playback.onLongPressEnd()
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
                            playback.onInterrupted()
                            if (frame.dismiss.active) {
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
                                    playback.onInterrupted()
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
                                val next = if (target < current && current <= 1f) {
                                    current + (target - current) * ReboundResistance
                                } else {
                                    target
                                }
                                val moved = clamp(zoomedOffset(offset, next / current, focus) + pan, next)
                                val maxY = limit(next).y
                                if (maxY > 0f) {
                                    when {
                                        moved.y >= maxY - 0.5f && pan.y > 0f -> {
                                            if (edgeHit != 1) {
                                                edgeHit = 1
                                                couiHaptic(CouiHapticEffect.EdgeList)
                                            }
                                        }
                                        moved.y <= -maxY + 0.5f && pan.y < 0f -> {
                                            if (edgeHit != -1) {
                                                edgeHit = -1
                                                couiHaptic(CouiHapticEffect.EdgeList)
                                            }
                                        }
                                        moved.y > -maxY + 2f && moved.y < maxY - 2f -> edgeHit = 0
                                    }
                                }
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
    }
}

private const val RegionTilePx = 2048
private const val MaxCachedRegionTiles = 16

private data class TileKey(val col: Int, val row: Int, val sampleSize: Int)

/**
 * 超过 GPU 单张纹理上限（4096px）的超长图 / 超大图分块渲染层（直接取自 Cobo COUI）：
 * 用 [BitmapRegionDecoder] 只把当前可视区域附近的 2048px 切片按原图或屏幕宽度解码出来，
 * 既不会受 Coil 默认 4096 上限压缩变糊，也不会爆硬件加速纹理尺寸或内存。
 */
@Composable
private fun TiledLongImageOverlay(
    file: File,
    original: Boolean,
    screenWidthPx: Int,
    box: IntSize,
    scale: Float,
    offset: Offset,
    rest: Size,
    onAspect: (Float) -> Unit,
) {
    var bounds by remember(file) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(file) {
        val measured = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
            }.getOrNull()
        }
        bounds = measured
        if (measured != null) {
            onAspect(measured.first.toFloat() / measured.second.toFloat())
        }
    }

    val (imgW, imgH) = bounds ?: return
    val isLong = imgW.toFloat() / imgH < LongImageAspect
    val needsTiling = (imgH > 4096 || imgW > 4096) && (original || isLong)
    if (!needsTiling) return

    val sampleSize = remember(imgW, original, screenWidthPx) {
        if (original) {
            1
        } else {
            var s = 1
            val targetW = screenWidthPx.coerceAtLeast(1)
            while (imgW / (s * 2) >= targetW) s *= 2
            s
        }
    }

    val decoderHolder = remember(file) { arrayOfNulls<BitmapRegionDecoder>(1) }
    var decoderReady by remember(file) { mutableStateOf(false) }
    val decoderMutex = remember(file) { Mutex() }
    val tiles = remember(file, sampleSize) { mutableStateMapOf<TileKey, ImageBitmap>() }
    val lruOrder = remember(file, sampleSize) { ArrayList<TileKey>() }

    DisposableEffect(file) {
        onDispose {
            decoderHolder[0]?.recycle()
            decoderHolder[0] = null
        }
    }

    LaunchedEffect(file) {
        val dec = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    file.inputStream().use { BitmapRegionDecoder.newInstance(it) }
                } else {
                    @Suppress("DEPRECATION")
                    file.inputStream().use { BitmapRegionDecoder.newInstance(it, false) }
                }
            }.getOrNull()
        }
        decoderHolder[0] = dec
        decoderReady = dec != null
    }

    if (!decoderReady || rest.width <= 0f || rest.height <= 0f) return

    val cols = (imgW + RegionTilePx - 1) / RegionTilePx
    val rows = (imgH + RegionTilePx - 1) / RegionTilePx
    val visLeft = (0f - box.width / 2f - offset.x) / scale + rest.width / 2f
    val visRight = (box.width - box.width / 2f - offset.x) / scale + rest.width / 2f
    val visTop = (0f - box.height / 2f - offset.y) / scale + rest.height / 2f
    val visBottom = (box.height - box.height / 2f - offset.y) / scale + rest.height / 2f

    val srcLeft = (visLeft / rest.width * imgW).toInt().coerceIn(0, imgW)
    val srcRight = (visRight / rest.width * imgW).toInt().coerceIn(0, imgW)
    val srcTop = (visTop / rest.height * imgH).toInt().coerceIn(0, imgH)
    val srcBottom = (visBottom / rest.height * imgH).toInt().coerceIn(0, imgH)

    val minCol = (srcLeft / RegionTilePx - 1).coerceIn(0, cols - 1)
    val maxCol = (srcRight / RegionTilePx + 1).coerceIn(0, cols - 1)
    val minRow = (srcTop / RegionTilePx - 1).coerceIn(0, rows - 1)
    val maxRow = (srcBottom / RegionTilePx + 1).coerceIn(0, rows - 1)

    val needed = remember(minCol, maxCol, minRow, maxRow, sampleSize) {
        buildList {
            for (r in minRow..maxRow) {
                for (c in minCol..maxCol) {
                    add(TileKey(c, r, sampleSize))
                }
            }
        }
    }

    LaunchedEffect(decoderReady, needed, sampleSize) {
        val decoder = decoderHolder[0] ?: return@LaunchedEffect
        for (key in needed) {
            if (key in tiles) {
                lruOrder.remove(key)
                lruOrder.add(key)
                continue
            }
            val decoded = withContext(Dispatchers.IO) {
                decoderMutex.withLock {
                    if (decoder.isRecycled) return@withLock null
                    runCatching {
                        val sx0 = key.col * RegionTilePx
                        val sy0 = key.row * RegionTilePx
                        val sx1 = minOf(imgW, sx0 + RegionTilePx)
                        val sy1 = minOf(imgH, sy0 + RegionTilePx)
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = key.sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        decoder.decodeRegion(AndroidRect(sx0, sy0, sx1, sy1), opts)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            if (decoded != null) {
                tiles[key] = decoded
                lruOrder.remove(key)
                lruOrder.add(key)
                while (lruOrder.size > MaxCachedRegionTiles) {
                    val oldest = lruOrder.removeAt(0)
                    if (oldest !in needed) tiles.remove(oldest)
                }
            }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val fitted = fitSize(size.width, size.height, imgW.toFloat() / imgH.toFloat())
        val left0 = (size.width - fitted.width) / 2f
        val top0 = (size.height - fitted.height) / 2f
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val bmp = tiles[TileKey(c, r, sampleSize)] ?: continue
                val sx0 = c * RegionTilePx
                val sy0 = r * RegionTilePx
                val sx1 = minOf(imgW, sx0 + RegionTilePx)
                val sy1 = minOf(imgH, sy0 + RegionTilePx)
                val dx0 = (left0 + sx0.toFloat() / imgW * fitted.width).roundToInt()
                val dy0 = (top0 + sy0.toFloat() / imgH * fitted.height).roundToInt()
                val dx1 = (left0 + sx1.toFloat() / imgW * fitted.width).roundToInt()
                val dy1 = (top0 + sy1.toFloat() / imgH * fitted.height).roundToInt()
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(dx0, dy0),
                    dstSize = IntSize((dx1 - dx0).coerceAtLeast(1), (dy1 - dy0).coerceAtLeast(1)),
                )
            }
        }
    }
}

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

private class FlightTargetInfo(
    val thumb: ThumbBounds?,
    val aspect: Float?,
    val hasThumb: Boolean,
    val isLongImage: Boolean,
    val layer: GraphicsLayer?,
    val onRelease: ((GraphicsLayer) -> Unit)?,
)

private fun resolveFlightTargetInfo(
    url: String?,
    frame: ViewerFrame,
    origins: ImageOrigins,
    density: Float,
    boxSize: IntSize,
): FlightTargetInfo {
    val host = frame.host
    val live = if (url != null && host != null && !frame.zoomed) {
        origins.boundsOf(url, host, density)?.also { frame.thumbs[url] = it }
    } else {
        null
    }
    val thumb = live ?: url?.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
    val aspect = url?.let { frame.aspectOf(it, origins) ?: thumb?.let { b -> b.rect.width / b.rect.height } }
    val w = boxSize.width.toFloat()
    val h = boxSize.height.toFloat()
    val effectiveAspect = aspect ?: thumb?.rect?.let { if (it.height > 0f) it.width / it.height else null }
    val rest = if (w > 0f && h > 0f && effectiveAspect != null) restSize(w, h, effectiveAspect) else null
    val isLongImage = rest != null && rest.height > h + 0.5f
    val hasThumb = thumb != null && rest != null
    val layer = url?.let { frame.pageLayers[it] }?.takeUnless { it.isReleased || it.size == IntSize.Zero }
    return FlightTargetInfo(
        thumb = thumb,
        aspect = effectiveAspect,
        hasThumb = hasThumb,
        isLongImage = isLongImage,
        layer = layer,
        onRelease = frame.onReleaseLayer,
    )
}

/**
 * 在预测性返回或点击返回键提交退出时，将当前画面平滑缩回原缩略图的逻辑移交给顶部非拦截飞行层，
 * 确保页面可立刻出栈并将触控权实时交还底层界面。
 */
private fun launchBackExitFlight(
    remaining: Float,
    offset: Offset,
    velocity: Offset,
    frame: ViewerFrame,
    origins: ImageOrigins,
    url: String?,
    density: Float,
    squircleEnabled: Boolean,
    onPop: () -> Unit,
): Boolean {
    val clamped = remaining.coerceIn(0f, 1f)
    if (clamped <= 0.01f && offset == Offset.Zero) {
        origins.finishDismissFlight()
        origins.viewing = null
        onPop()
        return true
    }
    val info = resolveFlightTargetInfo(url, frame, origins, density, frame.dismiss.boxSize)
    val layer = info.layer ?: return false
    val onRelease = info.onRelease ?: return false
    origins.finishDismissFlight()
    frame.handedOffLayers.add(layer)
    onPop()
    val baseDuration = (ExpandDurationMillis * clamped).roundToInt().coerceAtLeast(1)
    val duration = if (offset != Offset.Zero || velocity != Offset.Zero) maxOf(baseDuration, 240) else baseDuration
    val flight = ImageDismissFlight(
        url = url,
        layer = layer,
        onReleaseLayer = onRelease,
        boxSize = frame.dismiss.boxSize,
        density = density,
        squircleEnabled = squircleEnabled,
        startScale = 1f,
        startTranslation = offset,
        startBgAlpha = clamped,
        flyTarget = Offset.Zero,
        hasThumb = info.hasThumb,
        isLongImage = info.isLongImage,
        initialThumb = info.thumb,
        aspect = info.aspect,
        predictiveShrink = true,
        startProgress = 1f - clamped,
        remainingDurationMillis = duration,
    )
    origins.launchDismissFlight(
        flight = flight,
        initialTargetTranslation = Offset.Zero,
        initialVelocity = velocity,
    )
    return true
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

    val info = resolveFlightTargetInfo(url, frame, origins, density, dismiss.boxSize)
    val thumb = info.thumb
    val hasThumb = info.hasThumb
    val layer = info.layer
    val onRelease = info.onRelease
    if (layer == null || onRelease == null) {
        origins.finishDismissFlight()
        origins.viewing = null
        onPopImmediate()
        return
    }

    val w = dismiss.boxSize.width.toFloat()
    val h = dismiss.boxSize.height.toFloat()
    val targetTranslation = if (hasThumb && thumb != null) {
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
        isLongImage = info.isLongImage,
        initialThumb = thumb,
        aspect = info.aspect,
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

@Composable
private fun ImmersiveStatusBar(hidden: Boolean, leaving: () -> Boolean) {
    val view = LocalView.current
    val closing by remember { derivedStateOf(leaving) }
    val controller = remember(view) {
        view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    DisposableEffect(controller) {
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
    LaunchedEffect(controller, hidden, closing) {
        if (controller == null) return@LaunchedEffect
        if (hidden && !closing) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

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
