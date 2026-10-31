package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog

/**
 * A delete that waits for a second tap.
 *
 * The more-menu of a feed or a reply is a popup of three to five one-line entries, and 删除 sits
 * right next to 屏蔽; the official client deletes on that first tap, which is how posts get lost.
 * Every delete entry now only *asks*: [DeleteConfirmationDialog] draws the prompt and runs the
 * action when the red 删除 button is pressed.
 */
@Stable
class DeleteConfirmation {
    internal var pending: Pair<String, () -> Unit>? by mutableStateOf(null)
        private set

    /** Asks before deleting [what] — "动态", "评论" or "回复". */
    fun ask(what: String, onConfirm: () -> Unit) {
        pending = what to onConfirm
    }

    fun dismiss() {
        pending = null
    }
}

@Composable
fun rememberDeleteConfirmation(): DeleteConfirmation = remember { DeleteConfirmation() }

/**
 * Draws the prompt [state] holds, if any. Place it once in the composable that owns [state].
 *
 * [OverlayDialog] with a [DialogButtonBar] rather than [io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog]:
 * the alert dialog builds its confirm action without the bar's `recommended` flag, so its
 * `recommendColors` — the only way to give one of the two buttons its own label colour — never
 * reaches the screen. The panel is the same one [io.github.suqi8.coui.kmp.settings]' update dialog
 * uses, frosted and mounted on the root scaffold.
 */
@Composable
fun DeleteConfirmationDialog(state: DeleteConfirmation) {
    val pending = state.pending ?: return
    // `show` starts false and flips on the next frame: the panel only runs its scale + fade when it
    // observes the change, so opening it already-visible would pop it in.
    var show by remember(pending) { mutableStateOf(false) }
    LaunchedEffect(pending) { show = true }
    val dismiss = { show = false }
    OverlayDialog(
        show = show,
        title = "确定删除这条${pending.first}？",
        summary = "删除后无法恢复。",
        // Without this the panel takes the bottom-anchored tier and sits half off the screen edge;
        // the alert dialogs it replaces are the centered ones, so pin the same presentation.
        largeScreen = true,
        onDismissRequest = dismiss,
        onDismissFinished = { state.dismiss() },
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(text = "取消", onClick = dismiss),
            positive = DialogButtonBarAction(
                text = "删除",
                recommended = true,
                onClick = {
                    dismiss()
                    pending.second()
                },
            ),
            colors = appDialogButtonColors(),
            recommendColors = appDialogDestructiveColors(),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
