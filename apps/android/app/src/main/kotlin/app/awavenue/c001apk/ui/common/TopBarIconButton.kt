package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton

/**
 * A toolbar icon in the COUI 17 form: a 40dp circle with the radial fill, angled highlight and
 * halo ColorOS gives the back and overflow keys, instead of the flat 48dp touch target.
 */
@Composable
fun TopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    HighlightedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}
