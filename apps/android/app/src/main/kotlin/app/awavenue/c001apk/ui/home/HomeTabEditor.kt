package app.awavenue.c001apk.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.ui.common.rememberHapticClick
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 编辑首页 tab: every [HomeTab] gets a switch, and the last visible one cannot be turned off.
 * [hidden] is the persisted set; [onConfirm] receives the edited copy so nothing changes until
 * the user confirms.
 */
@Composable
fun HomeTabEditorDialog(
    hidden: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(hidden) }
    val visibleCount = HomeTab.entries.count { it.name !in draft }

    OverlayDialog(
        show = true,
        title = "编辑首页 tab",
        summary = "关闭的 tab 会从首页隐藏，长按 tab 行可再次编辑",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                HomeTab.entries.forEach { tab ->
                    val checked = tab.name !in draft
                    // Hiding the last visible tab would leave the home screen with no page.
                    val canToggle = !checked || visibleCount > 1
                    // checked means the tab is visible, so switching it off adds the name to the
                    // hidden set and switching it on removes it.
                    val onToggle = rememberHapticClick {
                        if (canToggle) {
                            draft = if (checked) draft + tab.name else draft - tab.name
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                enabled = canToggle,
                                role = Role.Switch,
                                onValueChange = { onToggle() },
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tab.label, modifier = Modifier.weight(1f))
                        // The switch routes through the same set instead of a null callback; COUI
                        // adds its own switch haptic, the row above adds the click one.
                        Switch(
                            checked = checked,
                            onCheckedChange = { next ->
                                if (canToggle) draft = if (next) draft - tab.name else draft + tab.name
                            },
                            enabled = canToggle,
                        )
                    }
                }
            }
            if (visibleCount == 1) {
                Text(
                    "至少保留一个 tab",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = COUITheme.textStyles.footnote1,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = "取消",
                    onClick = rememberHapticClick(onClick = onDismiss),
                )
                TextButton(
                    text = "确定",
                    onClick = rememberHapticClick { onConfirm(draft) },
                )
            }
        }
    }
}
