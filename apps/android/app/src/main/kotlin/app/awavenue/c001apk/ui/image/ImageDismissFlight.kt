package app.awavenue.c001apk.ui.image

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import app.awavenue.c001apk.ui.nav.ExpandDurationMillis
import app.awavenue.c001apk.ui.nav.ExpandEasing
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 承载图片拖拽/甩出或预测性返回松手后的回缩视觉状态，挂载在导航宿主最顶层但不拦截任何触控事件。
 */
internal class ImageDismissFlight(
    val url: String?,
    val layer: GraphicsLayer,
    private val onReleaseLayer: (GraphicsLayer) -> Unit,
    val boxSize: IntSize,
    val density: Float,
    val squircleEnabled: Boolean,
    val startScale: Float,
    val startTranslation: Offset,
    val startBgAlpha: Float,
    val flyTarget: Offset,
    val hasThumb: Boolean,
    val initialThumb: ThumbBounds?,
    val aspect: Float?,
    val predictiveShrink: Boolean = false,
    val startProgress: Float = 0f,
    val remainingDurationMillis: Int = ExpandDurationMillis,
) {
    val exitBgAlpha: Animatable<Float, AnimationVector1D> = Animatable(startBgAlpha)
    val exitProgress: Animatable<Float, AnimationVector1D> = Animatable(startProgress)
    val exitTranslation: Animatable<Offset, AnimationVector2D> =
        Animatable(startTranslation, Offset.VectorConverter)

    var animJob: Job? = null
    private var released = false

    fun release() {
        if (!released) {
            released = true
            animJob?.cancel()
            onReleaseLayer(layer)
        }
    }
}

internal fun ImageOrigins.launchDismissFlight(
    flight: ImageDismissFlight,
    initialTargetTranslation: Offset,
    initialVelocity: Offset,
) {
    val scope = flightScope ?: run {
        flight.release()
        viewing = null
        return
    }
    dismissFlight = flight
    viewing = if (flight.hasThumb) flight.url else null

    flight.animJob = scope.launch {
        try {
            when {
                flight.predictiveShrink -> {
                    val duration = flight.remainingDurationMillis.coerceAtLeast(1)
                    coroutineScope {
                        launch {
                            flight.exitBgAlpha.animateTo(0f, tween(duration, easing = LinearEasing))
                        }
                        launch {
                            flight.exitProgress.animateTo(1f, tween(duration, easing = ExpandEasing))
                        }
                        if (flight.startTranslation != Offset.Zero || initialVelocity != Offset.Zero) {
                            launch {
                                if (flight.hasThumb) {
                                    flight.exitTranslation.animateTo(
                                        targetValue = Offset.Zero,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = 700f,
                                            visibilityThreshold = Offset(0.5f, 0.5f),
                                        ),
                                        initialVelocity = initialVelocity,
                                    )
                                } else {
                                    flight.exitTranslation.animateTo(
                                        targetValue = flight.startTranslation + initialVelocity * 0.14f,
                                        animationSpec = tween(duration, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
                                    )
                                }
                            }
                        }
                    }
                }
                flight.hasThumb -> {
                    coroutineScope {
                        launch {
                            flight.exitBgAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                        }
                        launch {
                            flight.exitProgress.animateTo(1f, tween(320, easing = ExpandEasing))
                        }
                        launch {
                            flight.exitTranslation.animateTo(
                                targetValue = initialTargetTranslation,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 700f,
                                    visibilityThreshold = Offset(0.5f, 0.5f),
                                ),
                                initialVelocity = initialVelocity,
                            )
                        }
                    }
                }
                else -> {
                    coroutineScope {
                        launch {
                            flight.exitBgAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                        }
                        launch {
                            flight.exitProgress.animateTo(
                                1f,
                                tween(240, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
                            )
                        }
                    }
                }
            }
        } finally {
            if (dismissFlight === flight) {
                dismissFlight = null
                viewing = null
            }
            flight.release()
        }
    }
}

@Composable
internal fun ImageDismissOverlay(
    origins: ImageOrigins,
    rootHost: LayoutCoordinates?,
) {
    val flight = origins.dismissFlight ?: return
    DisposableEffect(flight) {
        onDispose { flight.release() }
    }
    Box(Modifier.fillMaxSize()) {
        // 只做视觉绘制，不挂任何 pointerInput 或 clickable，触控直接穿透到底层页面。
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val bgAlpha = flight.exitBgAlpha.value
                    if (bgAlpha > 0f) drawRect(Color.Black, alpha = bgAlpha)
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    applyDismissFlight(flight, origins, rootHost)
                }
                .drawBehind {
                    drawLayer(flight.layer)
                },
        )
    }
}

private fun GraphicsLayerScope.applyDismissFlight(
    flight: ImageDismissFlight,
    origins: ImageOrigins,
    rootHost: LayoutCoordinates?,
) {
    val p = flight.exitProgress.value
    if (!flight.hasThumb || size.width <= 0f || size.height <= 0f) {
        if (flight.predictiveShrink) {
            val q = (1f - p).coerceIn(0f, 1f)
            val curScale = lerp(0.92f, 1f, q)
            alpha = q
            transformOrigin = TransformOrigin.Center
            scaleX = curScale
            scaleY = curScale
            translationX = flight.exitTranslation.value.x
            translationY = flight.exitTranslation.value.y
        } else {
            val curScale = lerp(flight.startScale, flight.startScale * 0.78f, p)
            alpha = (1f - p).coerceIn(0f, 1f)
            transformOrigin = TransformOrigin.Center
            scaleX = curScale
            scaleY = curScale
            translationX = lerp(flight.startTranslation.x, flight.flyTarget.x, p)
            translationY = lerp(flight.startTranslation.y, flight.flyTarget.y, p)
        }
        shape = RectangleShape
        clip = false
        return
    }
    val initialThumb = flight.initialThumb!!
    val liveThumb = flight.url?.let { url ->
        rootHost?.takeIf { it.isAttached }?.let { host ->
            origins.boundsOf(url, host, flight.density)
        }
    }
    val thumb = liveThumb ?: initialThumb
    val source = thumb.rect
    val rest = (flight.aspect ?: (source.width / source.height)).let {
        restSize(size.width, size.height, it)
    }
    val cover = max(source.width / rest.width, source.height / rest.height)
    val q = (1f - p).coerceIn(0f, 1f)
    val curScale = if (flight.predictiveShrink) lerp(cover, 1f, q) else lerp(flight.startScale, cover, p)
    val trans = if (flight.predictiveShrink) {
        val boxCenter = Offset(size.width / 2f, size.height / 2f)
        (source.center - boxCenter) * (1f - q) + flight.exitTranslation.value
    } else {
        val delta = if (liveThumb != null) liveThumb.rect.center - initialThumb.rect.center else Offset.Zero
        flight.exitTranslation.value + delta * p
    }
    alpha = if (liveThumb != null) 1f else (1f - ((p - 0.55f) / 0.45f)).coerceIn(0f, 1f)
    transformOrigin = TransformOrigin.Center
    scaleX = curScale
    scaleY = curScale
    translationX = trans.x
    translationY = trans.y
    val startWidth = if (flight.predictiveShrink) size.width else size.width * flight.startScale
    val startHeight = if (flight.predictiveShrink) size.height else size.height * flight.startScale
    val startT = if (flight.predictiveShrink) 1f - q else p
    shape = CenteredWindow(
        width = lerp(startWidth, source.width, startT) / curScale,
        height = lerp(startHeight, source.height, startT) / curScale,
        radius = lerp(0f, thumb.cornerRadius, startT) / curScale,
        squircleEnabled = flight.squircleEnabled,
    )
    clip = true
}
