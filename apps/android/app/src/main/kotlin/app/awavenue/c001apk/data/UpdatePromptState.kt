package app.awavenue.c001apk.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The per-process handshake between the update check and the announcement monitor.
 *
 * The update prompt has priority: an announcement never opens while the update dialog is up, and
 * once an update has been offered in this run the monitor stays quiet. The flags are deliberately
 * not persisted — the next cold start starts over.
 */
object UpdatePromptState {
    /** The once-per-launch update check has finished, whether or not it found a release. */
    var automaticSettled by mutableStateOf(false)
        private set

    /** An update dialog was shown in this process. */
    var promptShown by mutableStateOf(false)
        private set

    fun settleAutomatic() {
        automaticSettled = true
    }

    fun markPromptShown() {
        promptShown = true
    }
}
