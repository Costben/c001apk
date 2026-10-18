package app.awavenue.c001apk.ui.common

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.GradientStrokeStyle
import io.github.suqi8.coui.kmp.basic.InnerShadowStyle
import io.github.suqi8.coui.kmp.basic.MaterialStrokeParams
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.materialShadowEdge
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Outer drop shadow of the search capsule. Measured on ColorOS 17 settings, see [couiCapsuleDropShadow]. */
private val SearchShadowColor = Color.Black.copy(alpha = 0.075f)
private val SearchShadowBlurRadius = 8.dp
private val SearchShadowOffsetY = 1.dp

/**
 * The COUI search-capsule material: the outer drop shadow, the capsule fill, the material rim and
 * the inner shadow. The home search field and the home tab row both wear it, so they stay one surface.
 *
 * The shadow is painted before [clip], so it is not cut off by the capsule's own bounds. Pass
 * `dropShadow = false` where the halo would read as extra width: the blur reaches ~8dp past the
 * outline, which pushes the capsule's visible edge outside the 16dp margins it is aligned to.
 *
 * @param shape The capsule outline. The material edge assumes a capsule (radius = height / 2).
 * @param dropShadow Whether to paint the outer drop shadow.
 */
@Composable
fun Modifier.couiSearchMaterial(shape: Shape, dropShadow: Boolean = true): Modifier {
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val fill = SearchBarDefaults.searchBarColors().backgroundColor
    return this
        .then(if (dropShadow) couiCapsuleDropShadow(shape) else Modifier)
        .clip(shape)
        .background(fill)
        .materialShadowEdge(
            stroke = MaterialStrokeParams.frameworkCapsule13(isDark),
            edgeStyle = GradientStrokeStyle.EDGE_STYLE_0,
            shadowStyle = InnerShadowStyle.SHADOW_STYLE_0,
        )
}

/**
 * A soft, unclipped drop shadow under [shape]: a blurred black outline at [SearchShadowColor],
 * shifted down by [SearchShadowOffsetY].
 */
private fun Modifier.couiCapsuleDropShadow(shape: Shape): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val paint = Paint()
    paint.nativePaint.apply {
        color = SearchShadowColor.toArgb()
        maskFilter = BlurMaskFilter(SearchShadowBlurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        val dy = SearchShadowOffsetY.toPx()
        canvas.translate(0f, dy)
        canvas.drawOutline(outline, paint)
        canvas.translate(0f, -dy)
    }
}
