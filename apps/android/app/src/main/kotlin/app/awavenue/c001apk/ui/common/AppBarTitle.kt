package app.awavenue.c001apk.ui.common

import androidx.compose.ui.unit.dp

/**
 * The title size of a bar that carries buttons on both sides.
 *
 * COUI's second-page toolbar paints its title with
 * `TextAppearance.COUI.Toolbar.SecondTitle`, 18dp — the value `SmallTopAppBar` uses for every
 * page of this shape (动态详情, 网页, 复制). A bar that hand-builds its own row has to read the
 * same number, or its title ends up a third larger than the page the user just came from.
 *
 * The library keeps the token private (`CollapsedTitleWithSubtitleTextSize`), so it is spelled
 * out here; convert it with the local density the way `SmallTopAppBar` does.
 */
val AppBarTitleSize = 18.dp
