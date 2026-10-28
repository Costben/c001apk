package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
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
