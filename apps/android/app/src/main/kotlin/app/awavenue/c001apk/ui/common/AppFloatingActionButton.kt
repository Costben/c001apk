package app.awavenue.c001apk.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.MetaBallParticle
import io.github.suqi8.coui.kmp.basic.MetaBallSurface
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

private val BallSize = 56.dp

/** The Scaffold's inset of the floating button from the screen's trailing edge. */
private val EdgeMargin = 16.dp

/** The bead the ball buds out of, before the metaball field pinches it free. */
private val BudSize = 20.dp

/**
 * The floating action ball a page offers for its primary action.
 *
 * It is the bottom bar's melt ball: the same glass over the page's [backdrop], an on-surface glyph
 * and the bar's lift shadow. It also enters and leaves the way that ball melts out of the capsule —
 * as a bead welded to the screen's trailing edge that swells, pinches free and springs to its
 * resting place — rather than sliding in as a finished circle.
 *
 * @param visible Whether the page wants the ball. It stays composed while hidden so the melt can
 *   run in both directions.
 */
@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    backdrop: Backdrop? = LocalBackdrop.current,
    onLongClick: (() -> Unit)? = null,
) {
    // The bar's own spring, response 0.55 / bounce 0.35.
    val melt = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        melt.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 130f),
        )
    }
    val progress = melt.value
    if (!visible && progress <= 0f) return

    val colorScheme = COUITheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    // The glyph is a sibling of the touch target, so the label has to be put on the target itself.
    val label = contentDescription
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // The COUI press feedback spring (response 0.3 / bounce 0), as the bar's ball and the COUI FAB
    // both use.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 438.65f),
        label = "fabPressScale",
    )

    // The chrome buttons — the bar's back and more icons — answer a press that turns into a drag by
    // leaning towards the finger and stretching along it, then snapping back with a bounce. This
    // ball sits in the same layer as those buttons, so it answers the same way: the same dead zone,
    // the same 28dp of travel under the same rubber curve, and the same 28dp → 1.2x stretch budget.
    var leanX by remember { mutableFloatStateOf(0f) }
    var leanY by remember { mutableFloatStateOf(0f) }
    var stretchX by remember { mutableFloatStateOf(1f) }
    var stretchY by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    // The rebound is the hand-up spring: a bounce of 0.35, which is a damping ratio of 0.65.
    val rebound = spring<Float>(dampingRatio = 0.65f, stiffness = 330f)
    val settle: () -> Unit = {
        scope.springTo(0f, rebound, leanX) { leanX = it }
        scope.springTo(0f, rebound, leanY) { leanY = it }
        scope.springTo(1f, rebound, stretchX) { stretchX = it }
        scope.springTo(1f, rebound, stretchY) { stretchY = it }
    }

    // The backing surface the bead buds out of is the screen edge to the right of the button. Its
    // distance is measured rather than assumed, so the weld lands on the edge whatever inset the
    // page places the button at.
    val windowWidth = LocalWindowInfo.current.containerSize.width
    var boxRightPx by remember { mutableIntStateOf(0) }
    val edgeMargin = if (boxRightPx in 1 until windowWidth) {
        with(LocalDensity.current) { (windowWidth - boxRightPx).toDp() }
    } else {
        EdgeMargin
    }
    val edgeCenterX = BallSize + edgeMargin
    // The ball's centre starts on that edge and travels back to the button's own centre while it
    // swells, exactly as the bar's ball travels away from the capsule.
    val ballCenterX = edgeCenterX + (BallSize / 2 - edgeCenterX) * progress
    val ballCenterY = BallSize / 2
    val ballDiameter = BallSize * progress
    // The bead swells in first and is pinched off once the ball has pulled away from the edge.
    val budDiameter = BudSize *
        (1f - progress).coerceIn(0f, 1f) *
        (progress * 4f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(BallSize)
            .onGloballyPositioned {
                boxRightPx = it.positionInWindow().x.roundToInt() + it.size.width
            }
            .graphicsLayer {
                scaleX = pressScale * stretchX
                scaleY = pressScale * stretchY
                translationX = leanX
                translationY = leanY
            }
            .pointerInput(onLongClick) {
                val maxLeanPx = MaxLean.toPx()
                val maxStretchPx = MaxStretch.toPx()
                val deadZonePx = if (onLongClick != null) {
                    max(DeadZonePx, viewConfiguration.touchSlop)
                } else {
                    DeadZonePx
                }
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val start = down.position
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            val dx = change.position.x - start.x
                            val dy = change.position.y - start.y
                            if (!dragging && (abs(dx) >= deadZonePx || abs(dy) >= deadZonePx)) {
                                dragging = true
                            }
                            if (dragging) {
                                // Consuming the move is what keeps a drag from also firing the tap.
                                change.consume()
                                val lean = ballLean(dx, dy, maxLeanPx)
                                leanX = lean.x
                                leanY = lean.y
                                val stretch = ballStretch(dx, dy, maxStretchPx)
                                stretchX = stretch.x
                                stretchY = stretch.y
                            }
                        }
                        // Let go: the ball springs home from wherever the drag left it.
                        settle()
                    }
                }
            },
    ) {
        // The shadow follows the ball's size and position. It carries no alpha of its own: an alpha
        // layer would clip the shadow to its own box and turn the glow into a square.
        if (ballDiameter > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = ballCenterX - ballDiameter / 2, y = BallSize / 2 - ballDiameter / 2)
                    .size(ballDiameter)
                    .dropShadow(shape = CircleShape, shadow = BottomFloatingNavigationBarDefaults.BarShadow),
            )
        }
        MetaBallSurface(
            particles = buildList {
                if (ballDiameter > 0.dp) {
                    add(MetaBallParticle.circle(ballCenterX, ballCenterY, ballDiameter))
                }
                if (budDiameter > 0.dp) {
                    add(MetaBallParticle.circle(edgeCenterX, ballCenterY, budDiameter))
                }
            },
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.bar,
            backdrop = backdrop,
            blurRadius = BottomFloatingNavigationBarDefaults.blurRadius(),
            rimColor = if (backdrop == null) {
                null
            } else {
                // TYPE_FRAMEWORK_CAPSULE_3's near/far edge alpha.
                Color.White.copy(
                    alpha = if (isDark) {
                        BottomFloatingNavigationBarDefaults.RIM_ALPHA_DARK
                    } else {
                        BottomFloatingNavigationBarDefaults.RIM_ALPHA_LIGHT
                    },
                )
            },
            rimBottomScale = 1f,
            bottomGlowColor = BottomFloatingNavigationBarDefaults.CausticColor,
        )
        // The glyph rides and scales with the ball while it melts, like the bar's expansion icon.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val ramp = progress.coerceIn(0f, 1f)
                    scaleX = ramp
                    scaleY = ramp
                    alpha = ramp
                    translationX = (ballCenterX - BallSize / 2).toPx()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.onSurface,
            )
        }
        // The bar's own ball carries the press feedback; a ripple here would paint a square over it.
        // The touch target stays at the button's resting place, not under the travelling ball.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { this.contentDescription = label }
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    },
                ),
        )
    }
}

/** The chrome buttons' dead zone: 5px of travel before a press reads as a drag. */
private const val DeadZonePx = 5f

/** Furthest the ball leans towards the finger. */
private val MaxLean = 28.dp

/** The stretch budget the lean is spent against, from COS17's touch tier params. */
private val MaxStretch = 150.dp

private const val LeanCurve = 0.05f
private const val StretchCurve = 0.55f
private const val MaxScale = 1.2f

private fun rubberBand(distance: Float, curveRatio: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    return sign(distance) * (1f - 1f / ((abs(distance) * curveRatio / limit) + 1f)) * limit
}

/** Lean towards the finger, against the same rubber band the chrome buttons pull on. */
private fun ballLean(dx: Float, dy: Float, maxLeanPx: Float): Offset = Offset(
    rubberBand(dx, LeanCurve, maxLeanPx),
    rubberBand(dy, LeanCurve, maxLeanPx),
)

/**
 * Stretch along the drag and squeeze across it. The ball is drawn as a circle, so the ellipse it
 * becomes under the finger is the one the chrome buttons become: the stretch grows with the rubber
 * band and the cross axis gives back exactly the area the stretch takes.
 */
private fun ballStretch(dx: Float, dy: Float, maxStretchPx: Float): Offset {
    fun along(distance: Float): Float {
        if (distance <= 0f || maxStretchPx <= 0f) return 1f
        return 1f + (rubberBand(distance, StretchCurve, maxStretchPx) / maxStretchPx) * (MaxScale - 1f)
    }
    val alongX = along(abs(dx))
    val alongY = along(abs(dy))
    val total = abs(dx) + abs(dy)
    if (total <= 0f) return Offset(1f, 1f)
    // Both axes at once: blend in log space, which keeps the shape's area through the transition.
    val wx = abs(dx) / total
    val wy = abs(dy) / total
    val x = exp(ln(max(alongX, 0.001f)) * wx + ln(max(1f / alongY, 0.001f)) * wy)
    val y = exp(ln(max(1f / alongX, 0.001f)) * wx + ln(max(alongY, 0.001f)) * wy)
    return Offset(x, y)
}

/** Springs a value back to [target], writing every frame into [write]. */
private fun CoroutineScope.springTo(
    target: Float,
    spec: AnimationSpec<Float>,
    from: Float,
    write: (Float) -> Unit,
) {
    launch {
        Animatable(from).animateTo(target, spec) { write(value) }
    }
}
