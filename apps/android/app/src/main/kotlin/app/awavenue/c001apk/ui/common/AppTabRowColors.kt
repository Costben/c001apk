package app.awavenue.c001apk.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.suqi8.coui.kmp.basic.TabRowColors
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The opaque segment capsule ColorOS Settings uses: the container hides the cards behind it
 * instead of letting the page show through. The dark pair is sampled from the system page.
 */
@Composable
fun appTabRowColors(): TabRowColors {
    val dark = COUITheme.colorScheme.background.luminance() < 0.5f
    return TabRowDefaults.tabRowColors(
        backgroundColor = if (dark) Color(0xFF373737) else Color(0xFFE6E6E6),
        selectedBackgroundColor = if (dark) Color(0xFF595959) else Color.White,
    )
}
