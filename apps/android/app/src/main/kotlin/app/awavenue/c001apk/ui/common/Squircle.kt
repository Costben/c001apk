package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled

/**
 * COUI's continuous corner as a [CornerBasedShape], for the places that can only take a `Shape`
 * (`clip`, `background`, `border`) instead of the shader-backed modifiers.
 *
 * Falls back to a plain rounded rectangle wherever the runtime shader is unavailable, matching what
 * `squircleSurface` does for cards, so a card and the artwork inside it never disagree on the
 * corner curve.
 */
@Composable
fun rememberSquircleShape(cornerRadius: Dp): CornerBasedShape {
    val enabled = isSquircleEnabled()
    return remember(cornerRadius, enabled) { SquircleShape(cornerRadius, squircleEnabled = enabled) }
}

/** Per-corner overload of [rememberSquircleShape], ordered like `RoundedCornerShape`. */
@Composable
fun rememberSquircleShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): CornerBasedShape {
    val enabled = isSquircleEnabled()
    return remember(topStart, topEnd, bottomEnd, bottomStart, enabled) {
        SquircleShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
            squircleEnabled = enabled,
        )
    }
}

/**
 * COUI's continuous-curvature capsule ([CapsuleOutlineShape]), tracing ColorOS 17's arc-plus-easing
 * curve whenever shaders are enabled and falling back to a plain stadium otherwise.
 */
@Composable
fun rememberCapsuleShape(): Shape {
    val enabled = isSquircleEnabled()
    return remember(enabled) { CapsuleOutlineShape(squircleEnabled = enabled) }
}
