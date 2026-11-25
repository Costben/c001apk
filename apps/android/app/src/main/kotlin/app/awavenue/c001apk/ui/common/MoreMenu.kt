package app.awavenue.c001apk.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupListDivider
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
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
) {
    MoreMenu(
        show = show,
        onDismissRequest = onDismissRequest,
        groups = if (entries.isEmpty()) emptyList() else listOf(entries),
        alignment = alignment,
    )
}

/**
 * Grouped COUI 17 overflow menu: adjacent groups are separated by a [PopupListDivider] while rows
 * within one group share a `groupKey` so no intra-group divider is drawn.
 */
@JvmName("MoreMenuGrouped")
@Composable
fun MoreMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<Pair<String, () -> Unit>>>,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
) {
    val nonEmpty = groups.filter { it.isNotEmpty() }
    OverlayListPopup(
        show = show,
        alignment = alignment,
        onDismissRequest = onDismissRequest,
    ) {
        ListPopupColumn {
            nonEmpty.forEachIndexed { groupIdx, group ->
                val isFirstGroup = groupIdx == 0
                val isLastGroup = groupIdx == nonEmpty.lastIndex
                group.forEachIndexed { itemIdx, entry ->
                    DropdownImpl(
                        item = DropdownItem(text = entry.first),
                        optionSize = group.size,
                        isSelected = false,
                        index = itemIdx,
                        isFirst = isFirstGroup && itemIdx == 0,
                        isLast = isLastGroup && itemIdx == group.lastIndex,
                        groupKey = "more-menu-$groupIdx",
                        role = Role.Button,
                        onSelectedIndexChange = {
                            onDismissRequest()
                            entry.second()
                        },
                    )
                }
                if (!isLastGroup) {
                    PopupListDivider()
                }
            }
        }
    }
}
