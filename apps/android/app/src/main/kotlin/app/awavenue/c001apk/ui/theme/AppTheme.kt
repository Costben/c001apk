package app.awavenue.c001apk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import app.awavenue.c001apk.data.DarkMode
import app.awavenue.c001apk.data.SeedColors
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.ThemeController

@Composable
fun isAppInDarkTheme(mode: DarkMode): Boolean = when (mode) {
    DarkMode.System -> isSystemInDarkTheme()
    DarkMode.Off -> false
    DarkMode.On -> true
}

/**
 * ColorOS's settings surfaces, sampled from the system settings on a ColorOS 17 phone: the
 * #F0F1F2 page behind white cards in light, and the 柔和 (soft) dark pair #303030 / #454545.
 * Accent roles stay with the caller, so dynamic colour and the seed palette keep tinting buttons
 * and highlights instead of the page.
 */
private fun Colors.withColorOsSurfaces(dark: Boolean): Colors =
    if (dark) copy(
        background = Color(0xFF303030),
        surface = Color(0xFF303030),
        surfaceVariant = Color(0xFF454545),
        surfaceContainer = Color(0xFF454545),
        surfaceContainerHigh = Color(0xFF595959),
        surfaceContainerHighest = Color(0xFF454545),
        bar = Color(0xFF303030),
    ) else copy(
        background = Color.White,
        surface = Color(0xFFF0F1F2),
        surfaceVariant = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFE6E6E6),
        surfaceContainerHighest = Color.White,
        bar = Color(0xFFFAFAFA),
    )

/**
 * COUI theme driven by the user's settings: dynamic colour follows the wallpaper, a static
 * palette follows the chosen seed, and the font scale applies above every screen.
 */
@Composable
fun AppTheme(
    darkMode: DarkMode,
    dynamicColor: Boolean,
    seedColor: Int,
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val mode = when (darkMode) {
        DarkMode.System -> if (dynamicColor) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        DarkMode.Off -> if (dynamicColor) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        DarkMode.On -> if (dynamicColor) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }
    // Without dynamic colour the seed drives the palette, the way the Flutter client seeds
    // Material from Constants.seedColors.
    val keyColor = SeedColors.colorAt(seedColor).takeUnless { dynamicColor }
    val controller = remember(mode, keyColor) {
        ThemeController(colorSchemeMode = mode, keyColor = keyColor)
    }
    val dark = when (mode) {
        ColorSchemeMode.System, ColorSchemeMode.MonetSystem -> isSystemInDarkTheme()
        ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
        ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
    }
    // The accent roles come from the controller; the page and card surfaces stay ColorOS's, so a
    // wallpaper or seed changes the highlights without tinting the backgrounds.
    val base = controller.currentColors()
    val colors = remember(base, dark) { base.withColorOsSurfaces(dark) }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides if (fontScale == 1f) density else density.let {
            androidx.compose.ui.unit.Density(it.density, it.fontScale * fontScale)
        },
    ) {
        COUITheme(controller = controller) {
            COUITheme(colors = colors, content = content)
        }
    }
}
