package app.awavenue.c001apk.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
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
