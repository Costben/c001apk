package app.awavenue.c001apk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.awavenue.c001apk.C001apkApp
import app.awavenue.c001apk.platform.MessageNotifier
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.feed.detail.CaptchaVerifierHost
import app.awavenue.c001apk.ui.nav.AppNavHost
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.rememberNavigator
import app.awavenue.c001apk.ui.onboarding.OnboardingScreen
import app.awavenue.c001apk.ui.theme.AppTheme
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

val LocalApp = staticCompositionLocalOf<C001apkApp> { error("C001apkApp not provided") }

@Composable
fun App(app: C001apkApp, deepLinks: Flow<String> = emptyFlow()) {
    CompositionLocalProvider(LocalApp provides app) {
        AppTheme(
            darkMode = app.settings.darkMode,
            dynamicColor = app.settings.dynamicColor,
            seedColor = app.settings.seedColor,
            fontScale = app.settings.fontScale,
        ) {
            // COUI 17 frosts every panel and dialog and rims it with a lit edge, and both passes
            // read a backdrop. The whole UI is recorded into one here, and the host that draws
            // those overlays is given it: the host is a sibling of the recorded content, so a
            // panel blurs the page under it instead of itself. Below Android 13, where the blur
            // cannot run, the backdrop is null and the panels keep their flat fill.
            val backdrop = rememberGlassBackdrop()
            // The popup host is a sibling of the nav host, so anything it draws — the update
            // dialog's Markdown links, for one — reads these locals from here rather than from
            // inside the nav host. The host and the stack share one navigator.
            val navigator = rememberNavigator()
            CompositionLocalProvider(LocalNavigator provides navigator) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0),
                    popupHost = {
                        CompositionLocalProvider(LocalBackdrop provides backdrop) {
                            COUIPopupHost()
                        }
                    },
                ) {
                    Box(Modifier.fillMaxSize().glassSource(backdrop)) {
                        AppNavHost(deepLinks = deepLinks, navigator = navigator)
                        MessageNotifier.RequestPermission()
                        // The guide covers the app on first launch and returns from 设置, until
                        // dismissed.
                        if (!app.settings.onboardingDone) {
                            OnboardingScreen(onClose = app.settings::completeOnboarding)
                        }
                        // The captcha page mounts as the last child so it draws over the whole app.
                        CaptchaVerifierHost()
                    }
                }
            }
        }
    }
}
