package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.awavenue.c001apk.ui.theme.ChromeDensity
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton

/**
 * A toolbar icon in the COUI 17 form: a 40dp circle with the radial fill, angled highlight and
 * halo ColorOS gives the back and overflow keys, instead of the flat 48dp touch target.
 *
 * The circle and the glyph inside it are measured in the system-app density, so 显示大小 leaves
 * them the 141px key and 85px glyph 相册 shows on the same phone.
 */
@Composable
fun TopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    ChromeDensity {
        HighlightedIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}
