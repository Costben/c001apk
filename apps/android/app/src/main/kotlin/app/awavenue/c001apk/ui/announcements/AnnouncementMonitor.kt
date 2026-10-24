package app.awavenue.c001apk.ui.announcements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.awavenue.c001apk.data.Announcement
import app.awavenue.c001apk.data.AnnouncementSync
import app.awavenue.c001apk.data.UpdatePromptState
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The automatic side of c001apk 小助手: one cold-start check, a re-check after six hours in the
 * background, and at most one prompt per reminder revision.
 *
 * The update check has priority. Nothing is prompted while the onboarding, a route, the captcha
 * page or the update dialog is up; an announcement that cannot be loaded leaves its unread and
 * prompted receipts untouched so the next run can try again.
 */
@Composable
fun AnnouncementMonitor() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf<Announcement?>(null) }
    var promptBody by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }

    suspend fun maybePrompt() {
        if (shown || prompting || prompt != null) return
        val service = app.announcements
        if (!service.automaticEnabled || !app.settings.onboardingDone) return
        if (UpdatePromptState.promptShown) return
        if (navigator.stack.size != 1 || navigator.stack.first().route != Route.Main) return
        val item = service.pendingImportant ?: return
        prompting = true
        try {
            val body = runCatching { service.loadBody(item) }.getOrNull() ?: return
            if (!service.isCurrent(item)) return
            val pending = service.pendingImportant
            if (pending?.cacheKey != item.cacheKey || pending.reminderRevision != item.reminderRevision) return
            shown = true
            promptBody = body
            prompt = item
        } finally {
            prompting = false
        }
    }

    LaunchedEffect(Unit) {
        app.announcements.sync(AnnouncementSync.ColdStart)
        // The update dialog wins the launch; wait for its check to settle before asking for the
        // foreground. The wait is bounded so a hanging update request cannot silence notices.
        withTimeoutOrNull(20_000) {
            while (!UpdatePromptState.automaticSettled) delay(100)
        }
        maybePrompt()
    }

    // Onboarding covers the app on first launch; the notice waits until it is gone.
    LaunchedEffect(app.settings.onboardingDone) {
        if (app.settings.onboardingDone) maybePrompt()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    app.announcements.sync(AnnouncementSync.Resume)
                    maybePrompt()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val item = prompt
    if (item != null) {
        AnnouncementOverlay(
            service = app.announcements,
            item = item,
            automatic = true,
            initialBody = promptBody,
            onDismiss = {
                prompt = null
                promptBody = null
            },
        )
    }
}
