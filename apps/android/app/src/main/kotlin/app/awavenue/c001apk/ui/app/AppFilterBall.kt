package app.awavenue.c001apk.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Filter
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

private val SearchPanelWidth = 248.dp

/**
 * The 筛选 ball in the bar's leading slot. It owns the tab's three-dot menu, anchored above
 * itself: 筛选 is a leaf that opens the name/package field, 排序 is a cascading row whose second
 * level lists each order with 正序 and 倒序.
 */
@Composable
fun AppFilterBall(filter: AppFilterState, modifier: Modifier = Modifier) {
    val entries = remember(filter.query, filter.sort, filter.ascending) {
        listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = "筛选",
                        summary = filter.query.ifBlank { null },
                        onClick = {
                            filter.menuOpen = false
                            filter.searchOpen = true
                        },
                    ),
                ),
            ),
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = "排序",
                        summary = "${filter.sort.label} · ${if (filter.ascending) "正序" else "倒序"}",
                        children = AppSort.entries.flatMap { sort ->
                            listOf(true, false).map { ascending ->
                                DropdownItem(
                                    text = sort.label,
                                    summary = if (ascending) "正序" else "倒序",
                                    selected = filter.sort == sort && filter.ascending == ascending,
                                    onClick = {
                                        filter.sort = sort
                                        filter.ascending = ascending
                                    },
                                )
                            }
                        },
                    ),
                ),
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
            ) { filter.menuOpen = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            COUIIcons.Filter,
            contentDescription = "筛选应用",
            tint = if (filter.active) {
                COUITheme.colorScheme.primary
            } else {
                COUITheme.colorScheme.onSurface
            },
        )
        OverlayCascadingListPopup(
            show = filter.menuOpen,
            entries = entries,
            alignment = PopupPositionProvider.Align.BottomStart,
            onDismissRequest = { filter.menuOpen = false },
        )
    }
}

/**
 * The name/package field the menu's 筛选 row opens. It is an in-window overlay rather than a popup
 * window so it rides above the keyboard: a popup keeps its position when the IME comes up and is
 * covered completely.
 *
 * @param bottomPadding The floating bar's height, so the field clears it while the keyboard is down.
 */
@Composable
fun AppFilterSearchPanel(
    filter: AppFilterState,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val field = rememberTextFieldState(filter.query)
    LaunchedEffect(field.text) { filter.query = field.text.toString() }
    val density = LocalDensity.current
    val keyboardUp = WindowInsets.ime.getBottom(density) > 0
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val focusRequester = remember { FocusRequester() }

    BackHandler(enabled = filter.searchOpen) { filter.searchOpen = false }

    // The row is tapped to type, so the field takes focus as soon as the panel has entered; a
    // request on a zero-size node would leave the keyboard closed.
    LaunchedEffect(filter.searchOpen) {
        if (filter.searchOpen) {
            delay(160)
            focusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = filter.searchOpen,
        modifier = modifier,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(100)),
    ) {
        Box(Modifier.fillMaxSize()) {
            val panelShape = rememberSquircleShape(14.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
                    .clickable(interactionSource = null, indication = null) { filter.searchOpen = false },
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    // With the keyboard up the bar is behind it anyway, so only the small gap.
                    .imePadding()
                    .padding(bottom = if (keyboardUp) 8.dp else bottomPadding + 8.dp)
                    .width(SearchPanelWidth)
                    .dropShadow(
                        shape = panelShape,
                        shadow = Shadow(
                            radius = 16.dp,
                            color = Color.Black.copy(alpha = 0.18f),
                            offset = DpOffset(0.dp, 6.dp),
                        ),
                    )
                    .clip(panelShape)
                    .background(if (isDark) Color(0xFF333333) else Color.White)
                    .padding(12.dp),
            ) {
                TextField(
                    state = field,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    backgroundMode = TextFieldMode.None,
                    label = "筛选应用名或包名",
                    showClearButton = true,
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        }
    }
}
