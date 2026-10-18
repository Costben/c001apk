package app.awavenue.c001apk.ui.nav

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The element a pushed route grows out of, and shrinks back into when it is popped. It follows the
 * element's live position and keeps a live copy of its drawing, so the page starts out as exactly
 * what was tapped — pressed tint included.
 */
@Stable
class ExpandOrigin internal constructor(internal val layer: GraphicsLayer) {
    internal var cornerRadius: Dp = 0.dp
    internal var color: Color = Color.Transparent
    internal var coordinates: LayoutCoordinates? = null

    /** False once the element is gone, e.g. its list item was recycled or its screen disposed. */
    internal val isAvailable: Boolean
        get() = coordinates?.isAttached == true && !layer.isReleased

    /**
     * The element's bounds the last time they could be read. Pushing a page over the one that owns
     * the element disposes that page, which detaches the element; freezing its position here lets a
     * pop still shrink into where it was instead of falling back to a plain slide.
     */
    internal var lastBounds: Rect? = null

    /** Whether a page can still grow out of, or shrink back into, this element. */
    internal val hasBounds: Boolean
        get() = isAvailable || lastBounds != null

    /**
     * A still of the element, taken once its press tint has faded. The page it was on is disposed
     * when another is stacked over it, and with it the live layer; this is what the page shrinks
     * back into then.
     */
    internal var picture: ImageBitmap? = null
}

/** [cornerRadius] and [color] describe the element's own surface, which the frame starts from. */
@Composable
fun rememberExpandOrigin(cornerRadius: Dp, color: Color): ExpandOrigin {
    val layer = rememberGraphicsLayer()
    val origin = remember(layer) { ExpandOrigin(layer) }
    SideEffect {
        origin.cornerRadius = cornerRadius
        origin.color = color
    }
    return origin
}

/** Marks the element [origin] stands for; apply it outside the element's surface. */
fun Modifier.expandOrigin(origin: ExpandOrigin): Modifier =
    onPlaced { origin.coordinates = it }
        .drawWithContent {
            origin.layer.record { this@drawWithContent.drawContent() }
            drawLayer(origin.layer)
        }

/** The display's own corner radius, or zero on a square display or below Android 12. */
@Composable
private fun rememberScreenCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        val px = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        } else {
            0
        }
        with(density) { px.toDp() }
    }
}

/** COUI's move curve: a quick start that settles softly, the way system cards open. */
internal val ExpandEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
internal const val ExpandDurationMillis = 420

private const val ScrimAlpha = 0.3f

// The card and the page lay out the same post differently, so they never show at once: the card
// is gone by CardFadeEnd and the page only starts at it, over the frame's own fill.
private const val CardFadeEnd = 0.25f
private const val PageFadeLength = 0.3f

// On the way out the card comes back over the page as the frame closes in on it, between these two
// points of the frame's travel. Unlike the way in, the two cross-fade instead of taking turns: a
// back gesture can hold the frame anywhere, and a gap between them would leave the frame's bare
// fill on screen for as long as the finger stays there.
private const val ExitCardStart = 0.4f
private const val ExitCardEnd = 0.08f

// Without any drawing of the card to come back to, the page stays on screen until the frame is all
// but at the card's bounds and hands over to the real card in the last couple of percent. Fading it
// earlier leaves the frame's own fill as a blank card-coloured rectangle shrinking over the list.
private const val ExitHandoverProgress = 0.02f

/**
 * Hosts a page that grows out of [origin]. [progress] drives the frame from the element's bounds
 * (0) to the full screen (1); [fade] runs linearly over the same time and cross-fades the element's
 * drawing into the page. At 1, or without an origin, the page draws untouched. The node structure
 * never changes with either value, so the page keeps its state when the transition ends.
 */
@Composable
internal fun ExpandFrame(
    origin: ExpandOrigin?,
    progress: () -> Float,
    fade: () -> Float,
    exiting: () -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    val pageColor = COUITheme.colorScheme.surface
    val squircle = isSquircleEnabled()
    val statusBars = WindowInsets.statusBars
    val frame = remember { ExpandGeometry() }
    val screenCornerRadius = rememberScreenCornerRadius()

    fun active(): ExpandOrigin? = origin?.takeIf { progress() < 1f && it.hasBounds }

    // How much of the card shows over the page on the way out, or null when nothing of it is left
    // to show and the page has to hand over to the real one in place.
    fun exitCardAlpha(source: ExpandOrigin): Float? =
        if (source.isAvailable || source.picture != null) {
            ((ExitCardStart - progress()) / (ExitCardStart - ExitCardEnd)).coerceIn(0f, 1f)
        } else {
            null
        }

    // Taken while the page underneath is still composed: it is disposed once this one is fully in.
    if (origin != null) {
        LaunchedEffect(origin) {
            withFrameNanos { }
            delay(SnapshotSettleMillis)
            if (origin.isAvailable && origin.layer.size != IntSize.Zero) {
                origin.picture = origin.layer.toImageBitmap()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { frame.host = it }
            .drawBehind {
                if (active() != null) drawRect(Color.Black, alpha = ScrimAlpha * progress())
            }
            .graphicsLayer {
                val source = active()
                val bounds = source?.let { frame.bounds(it) }
                if (source == null || bounds == null) {
                    translationX = 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                    shape = RectangleShape
                    clip = false
                    return@graphicsLayer
                }
                val p = progress()
                val left = lerp(bounds.left, 0f, p)
                val top = lerp(bounds.top, 0f, p)
                val width = lerp(bounds.width, size.width, p)
                val height = lerp(bounds.height, size.height, p)
                // Scaled by width only, so the page keeps its own layout and just reads smaller.
                val scale = width / size.width
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = left
                translationY = top
                scaleX = scale
                scaleY = scale
                val radius = lerp(source.cornerRadius.toPx(), screenCornerRadius.toPx(), p) / scale
                shape = FrameShape(height / scale, (radius / density).dp, squircle)
                clip = true
            }
            .drawWithContent {
                val source = active()
                val bounds = source?.let { frame.bounds(it) }
                if (source == null || bounds == null) {
                    drawContent()
                    return@drawWithContent
                }
                val f = fade()
                val exitCard = if (exiting()) exitCardAlpha(source) else null
                // Only a page leaving with no drawing of the card left hands over without one: the
                // fill goes once the frame has arrived, and the real card underneath shows through.
                val handover = if (exiting() && exitCard == null) {
                    (progress() / ExitHandoverProgress).coerceIn(0f, 1f)
                } else {
                    1f
                }
                // The frame stays solid while the page is on it. A dark card is a translucent white,
                // and fading it into the page let the list — the card itself included — show through
                // the frame as a bright ghost.
                drawRect(
                    color = lerp(source.color.compositeOver(pageColor), pageColor, f),
                    alpha = handover,
                )
                drawContent()
                // The tapped element rides on top at the frame's width and fades out early, so the
                // page appears to grow out of it rather than replace it. It is drawn over the page
                // colour it sits on in the list, so once the frame is back at its bounds it looks
                // exactly like the card it hands over to. On the way out it fades back in over the
                // page as the frame closes in.
                val alpha = when {
                    exitCard != null -> exitCard
                    exiting() -> 1f - handover
                    else -> 1f - (f / CardFadeEnd).coerceIn(0f, 1f)
                }
                // Live while the element is still on screen, so it follows what changed on it;
                // otherwise the still taken on the way in.
                val picture = source.picture
                if (alpha > 0f && (source.isAvailable || picture != null)) {
                    val k = size.width / bounds.width
                    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(Rect(Offset.Zero, bounds.size), Paint().apply { this.alpha = alpha })
                            drawRect(pageColor, size = bounds.size)
                            if (source.isAvailable) {
                                drawLayer(source.layer)
                            } else if (picture != null) {
                                drawImage(
                                    picture,
                                    dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()),
                                )
                            }
                            canvas.restore()
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (active() == null) {
                        alpha = 1f
                        translationY = 0f
                    } else {
                        // On the way out the page gives way to the card as it fades back in, so there
                        // is always one of the two on the frame and never a bare fill. With no card
                        // to fade to it keeps drawing until the frame reaches the real one.
                        val source = active()
                        val exitCard = source?.takeIf { exiting() }?.let { exitCardAlpha(it) }
                        alpha = when {
                            exitCard != null -> 1f - exitCard
                            exiting() -> (progress() / ExitHandoverProgress).coerceIn(0f, 1f)
                            else -> ((fade() - CardFadeEnd) / PageFadeLength).coerceIn(0f, 1f)
                        }
                        // A full-screen page starts below the status bar; inside a card-sized frame
                        // that gap is just an empty band, so the page rides up into it.
                        translationY = -statusBars.getTop(this) * (1f - progress())
                    }
                },
        ) { content() }
    }
}

private class ExpandGeometry {
    var host: LayoutCoordinates? = null

    /** The element's bounds in the page host, read live so a pop shrinks into where it is now. */
    fun bounds(origin: ExpandOrigin): Rect? {
        val host = host?.takeIf { it.isAttached } ?: return origin.lastBounds
        val element = origin.coordinates?.takeIf { it.isAttached } ?: return origin.lastBounds
        return host.localBoundingBoxOf(element, clipBounds = false)
            .takeIf { it.width > 0f && it.height > 0f }
            ?.also { origin.lastBounds = it }
    }
}

/** The frame's outline: the element's corner shape over the top [height] of the unscaled page. */
private class FrameShape(
    private val height: Float,
    private val cornerRadius: Dp,
    private val squircle: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        SquircleShape(cornerRadius, squircleEnabled = squircle)
            .createOutline(Size(size.width, height.coerceAtMost(size.height)), layoutDirection, density)
}

/**
 * Whether a route entering for the first time with [origin] should grow out of it. The image viewer
 * always counts: it finds the thumbnail it opens from by itself and draws its own growth.
 */
internal fun BackStackEntry.growsFromOrigin(): Boolean =
    (origin?.isAvailable == true || route is Route.ImageView) && !shown

/** Whether a popped route can shrink back into its origin. */
internal fun BackStackEntry.shrinksToOrigin(): Boolean = origin?.hasBounds == true || route is Route.ImageView
