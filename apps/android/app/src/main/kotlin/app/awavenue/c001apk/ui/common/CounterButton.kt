package app.awavenue.c001apk.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

// COUI press state layer: #1F000000 in light, #33FFFFFF in dark.
private const val PRESS_ALPHA_LIGHT = 0x1F / 255f
private const val PRESS_ALPHA_DARK = 0x33 / 255f
private val PressSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

/**
 * An icon + count under a feed or reply. Clickable counters get a capsule press layer instead of
 * COUI's rectangular one, and a quick tap still flashes it long enough to be seen.
 */
@Composable
fun CounterButton(
    icon: ImageVector,
    value: String,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val color = if (highlighted) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurfaceVariantSummary
    if (onClick == null) {
        CounterContent(icon, value, color, modifier)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha = remember { Animatable(0f) }
    // A tap can end before the fade-in starts, so a click holds the layer on for a moment.
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash) {
            // Light up immediately, hold long enough to register, then spring back.
            alpha.snapTo(1f)
            delay(180)
            flash = false
        }
    }
    LaunchedEffect(pressed, flash) {
        alpha.animateTo(if (pressed || flash) 1f else 0f, PressSpring)
    }
    val dark = COUITheme.colorScheme.background.luminance() < 0.5f
    val overlay = if (dark) Color.White else Color.Black
    val strength = if (dark) PRESS_ALPHA_DARK else PRESS_ALPHA_LIGHT
    CounterContent(
        icon = icon,
        value = value,
        color = color,
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    flash = true
                    onClick()
                },
            )
            // Clip inside the touch target so the layer's own size, not the 48dp minimum, is the
            // capsule. The padding keeps the rounded ends clear of the icon and the digits.
            .clip(RoundedCornerShape(50))
            .drawBehind {
                if (alpha.value > 0f) drawRect(overlay, alpha = alpha.value * strength)
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun CounterContent(icon: ImageVector, value: String, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(value, style = COUITheme.textStyles.footnote1, color = color)
    }
}
