package app.awavenue.c001apk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.awavenue.c001apk.C001apkApp
import app.awavenue.c001apk.ui.feed.detail.CaptchaVerifierHost
import app.awavenue.c001apk.ui.nav.AppNavHost
import app.awavenue.c001apk.ui.theme.AppTheme

val LocalApp = staticCompositionLocalOf<C001apkApp> { error("C001apkApp not provided") }

@Composable
fun App(app: C001apkApp) {
    CompositionLocalProvider(LocalApp provides app) {
        AppTheme(
            darkMode = app.settings.darkMode,
            dynamicColor = app.settings.dynamicColor,
            seedColor = app.settings.seedColor,
            fontScale = app.settings.fontScale,
        ) {
            Box(Modifier.fillMaxSize()) {
                AppNavHost()
                // The captcha page mounts as the last child so it draws over the whole app.
                CaptchaVerifierHost()
            }
        }
    }
}
