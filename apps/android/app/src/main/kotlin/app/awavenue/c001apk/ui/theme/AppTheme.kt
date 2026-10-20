package app.awavenue.c001apk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
 * ColorOS system apps keep the phone's default layout width even after 显示大小 is raised: 相册 on
 * this 620-dpi override still measures 360dp across, so its 56dp bar, 52dp header and 24dp icons
 * come out about a tenth smaller than this app's. Compose otherwise takes the override as is.
 * Capping the density at the value that keeps the window this wide restores the system-app scale.
 */
private const val DefaultLogicalWidthDp = 360f

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
    // 默认 keeps COUI's own blue-and-grey scheme; only the other seeds tint the palette. A seed
    // only reaches the palette through the Monet modes, because ThemeController ignores keyColor
    // in the plain Light/Dark/System schemes. Monet modes without a keyColor fall back to the
    // wallpaper palette, which is what dynamic colour wants.
    val keyColor = SeedColors.colorAt(seedColor).takeUnless { dynamicColor || seedColor == 0 }
    val monet = dynamicColor || keyColor != null
    val mode = when (darkMode) {
        DarkMode.System -> if (monet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        DarkMode.Off -> if (monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        DarkMode.On -> if (monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }
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

/**
 * The density the floating bottom bar and the toolbar keys are measured in.
 *
 * ColorOS system apps keep the phone's default layout width even after 显示大小 is raised: 相册 on
 * a 620-dpi override still measures 360dp across, so its 56dp bar stays 198px while an app's own
 * pages grow with the setting. The bar and the toolbar keys therefore opt out of the override on
 * their own — capping their density at the value that keeps the window [DefaultLogicalWidthDp]
 * wide — and leave every page around them following the display-size setting.
 */
@Composable
fun rememberChromeDensity(): Density {
    val system = LocalDensity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val overridden = remember(context, configuration.screenWidthDp) {
        android.provider.Settings.Secure.getInt(
            context.contentResolver,
            "display_density_forced",
            0,
        ) > 0
    }
    return remember(system, configuration.screenWidthDp, overridden) {
        if (!overridden) {
            system
        } else {
            val widthPx = configuration.screenWidthDp * system.density
            Density(
                density = minOf(system.density, widthPx / DefaultLogicalWidthDp),
                fontScale = system.fontScale,
            )
        }
    }
}

/**
 * Runs [content] in [rememberChromeDensity], so the dp sizes inside it stay at the system-app
 * scale while the page around it follows 显示大小.
 */
@Composable
fun ChromeDensity(content: @Composable () -> Unit) {
    val density = rememberChromeDensity()
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
