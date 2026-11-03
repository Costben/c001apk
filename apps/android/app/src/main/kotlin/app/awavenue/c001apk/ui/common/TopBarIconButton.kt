package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.awavenue.c001apk.ui.theme.ChromeDensity
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.HighlightedIconButtonDefaults

/**
 * A toolbar icon in the COUI 17 form: a 40dp circle with the radial fill, angled highlight and
 * halo ColorOS gives the back and overflow keys, laid out as the 40dp action box ColorOS 17 uses
 * (`coui_action_menu_item_min_width` under `values-v37`) instead of the 48dp box of older builds.
 *
 * The box is the disc, so callers space the keys apart with `coui_actionbar_menuitemview_item_spacing`
 * (10dp) exactly as the native toolbar does — a 48dp box would add 4dp on each side of every key
 * and widen the gaps and the end margin the calculator and Settings toolbars show.
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
            modifier = modifier.size(HighlightedIconButtonDefaults.Size),
            enabled = enabled,
            content = content,
        )
    }
}
