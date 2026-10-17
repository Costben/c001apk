package app.awavenue.c001apk

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import app.awavenue.c001apk.ui.App
import app.awavenue.c001apk.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class MainActivity : ComponentActivity() {
    // A cold start forwards its link from onCreate, before the UI is collecting, so the link waits
    // here until the first collector is ready. The cold flow below hands it over exactly once.
    private var pendingLink: String? = null
    private val links = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Every external link this activity was opened with, in the order they arrived. */
    private val deepLinks: Flow<String> = flow {
        pendingLink?.let {
            pendingLink = null
            emit(it)
        }
        emitAll(links)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as C001apkApp
        // A recreated activity still carries the intent it was started with; forwarding it again
        // would open the same page a second time on top of the restored back stack.
        if (savedInstanceState == null) forwardLink(intent)

        setContent {
            val dark = isAppInDarkTheme(app.settings.darkMode)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }
            App(app, deepLinks = deepLinks)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        forwardLink(intent)
    }

    private fun forwardLink(intent: Intent?) {
        val url = intent?.data?.toString() ?: return
        if (links.subscriptionCount.value == 0) pendingLink = url else links.tryEmit(url)
    }
}
