package app.awavenue.c001apk.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.BlurDefaults
import io.github.suqi8.coui.kmp.blur.ProgressiveBlur
import io.github.suqi8.coui.kmp.blur.progressiveTextureBlur
import io.github.suqi8.coui.kmp.shader.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The home bar's material. COUI's app bar ramp reaches full transparency at the bar's bottom edge,
 * which is where the tab row sits once the header collapses: whatever scrolls under it stayed
 * legible and read as two layers of text. This one keeps the blur at full strength across the bar
 * and only fades over the band below it, with a stronger radius so nothing behind it resolves into
 * shapes.
 */
@Composable
internal fun HomeBarMaterial(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    extraHeight: Dp = 14.dp,
) {
    val surface = COUITheme.colorScheme.surface
    val dark = COUITheme.colorScheme.background.luminance() < 0.5f
    val blurRadius = if (dark) BarBlurRadiusDark else BarBlurRadiusLight
    val colors = BlurDefaults.blurColors()

    BoxWithConstraints(modifier) {
        val nodeHeight = constraints.maxHeight.toFloat()
        val extraPx = with(LocalDensity.current) { extraHeight.toPx() }
        // The ramp is anchored to the bar, not the node: the band below the bar fades out.
        val barFraction = if (nodeHeight > 0f) (1f - extraPx / nodeHeight).coerceIn(0f, 1f) else 1f
        val brush = remember(surface, dark, barFraction) {
            Brush.verticalGradient(
                0f to surface.copy(alpha = if (dark) 0.94f else 1f),
                barFraction * 0.55f to surface.copy(alpha = if (dark) 0.9f else 0.97f),
                barFraction to surface.copy(alpha = if (dark) 0.84f else 0.92f),
                1f to Color.Transparent,
            )
        }
        val material = if (backdrop != null && isRuntimeShaderSupported()) {
            Modifier
                .progressiveTextureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadiusX = blurRadius,
                    blurRadiusY = blurRadius,
                    // Stays at full strength most of the way down, then eases off in the band.
                    gradient = ProgressiveBlur(angle = 90f, startFraction = 0f, endFraction = 1f, curve = 2f),
                    colors = colors,
                )
                .background(brush)
        } else {
            Modifier.background(brush)
        }
        Box(Modifier.fillMaxSize().then(material))
    }
}

/** Blur radius in dp; the COUI bar rides 30px, this is several times that at any density. */
private val BarBlurRadiusLight = 20f
private val BarBlurRadiusDark = 16f
