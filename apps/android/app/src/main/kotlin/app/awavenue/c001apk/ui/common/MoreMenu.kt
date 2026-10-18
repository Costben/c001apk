package app.awavenue.c001apk.ui.common

import androidx.compose.runtime.Composable
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup

/**
 * The COUI 17 overflow menu, as the settings page's top-right one: a list popup that drops from the
 * button it is placed beside. Put it in the same `Box` as that button. Picking an entry closes the
 * menu before running it.
 */
@Composable
fun MoreMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<Pair<String, () -> Unit>>,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.BottomEnd,
) {
    OverlayListPopup(
        show = show,
        alignment = alignment,
        onDismissRequest = onDismissRequest,
    ) {
        ListPopupColumn {
            entries.forEachIndexed { index, entry ->
                DropdownImpl(
                    text = entry.first,
                    optionSize = entries.size,
                    isSelected = false,
                    index = index,
                    onSelectedIndexChange = {
                        onDismissRequest()
                        entry.second()
                    },
                )
            }
        }
    }
}
