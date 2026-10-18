package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.BlurDefaults
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.ProgressiveBlur
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.progressiveTextureBlur
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.basic.GlassDefaults
import io.github.suqi8.coui.kmp.shader.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The backdrop a glass bar blurs, or null where the platform cannot blur (below Android 13).
 *
 * The bars fall back to their opaque fill on null, so callers need no branch of their own. The layer
 * is filled with the Scaffold surface first, otherwise a bar over an empty list would blur nothing.
 */
@Composable
fun rememberGlassBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surface = COUITheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

/**
 * Records this content for [backdrop]. Apply it to a sibling of the bar, never to an ancestor: a bar
 * inside the layer it samples reads that layer while it is being recorded.
 */
fun Modifier.glassSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop == null) this else layerBackdrop(backdrop)

/**
 * A bar surface that fades into the content under its bottom edge instead of ending on a hard line.
 *
 * The blur is a top-weighted progressive one, so it reaches full strength at the bar's top and is
 * sharp by the bottom edge; the surface colour stays solid until [fadeHeight] from the bottom and
 * then ramps to transparent. The result is the gradient COUI's [AppBarBlurOverlay] gives a scanning
 * app bar, without the extra 24dp band that only suits a short bar.
 *
 * Without a backdrop, or below Android 13, the surface is drawn opaque.
 */
@Composable
fun Modifier.glassHeader(
    backdrop: Backdrop?,
    containerColor: Color,
    fadeHeight: Dp = 28.dp,
): Modifier {
    if (backdrop == null || !isRuntimeShaderSupported()) return background(containerColor)
    return progressiveTextureBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        blurRadius = GlassDefaults.BlurRadius,
        gradient = ProgressiveBlur.Top.copy(curve = 3f),
        colors = BlurDefaults.blurColors(),
    ).drawBehind {
        if (size.height <= 0f) return@drawBehind
        val solid = (1f - fadeHeight.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(
            Brush.verticalGradient(
                0f to containerColor,
                solid to containerColor,
                1f to containerColor.copy(alpha = 0f),
            ),
        )
    }
}
