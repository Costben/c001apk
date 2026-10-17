package app.awavenue.c001apk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import app.awavenue.c001apk.data.DarkMode
import app.awavenue.c001apk.data.SeedColors
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController

@Composable
fun isAppInDarkTheme(mode: DarkMode): Boolean = when (mode) {
    DarkMode.System -> isSystemInDarkTheme()
    DarkMode.Off -> false
    DarkMode.On -> true
}

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
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides if (fontScale == 1f) density else density.let {
            androidx.compose.ui.unit.Density(it.density, it.fontScale * fontScale)
        },
    ) {
        COUITheme(controller = controller, content = content)
    }
}
