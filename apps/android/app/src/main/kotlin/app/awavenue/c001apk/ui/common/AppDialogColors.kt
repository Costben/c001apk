package app.awavenue.c001apk.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.TextButtonColors
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The capsule fill of an alert-dialog button.
 *
 * The library's [ButtonDefaults] recipe hard-codes the 67% white it measured on the *light*
 * ColorOS 17 dialog (229 panel → 246 capsule). Reused over the dark panel — 0.667 × white +
 * 0.333 × (39) ≈ 183 — it turns the capsule into the brightest thing on screen, while the live dark
 * dialog keeps it at roughly 15% white over the panel (≈ 70). Split by theme here rather than in
 * the library so the pinned submodule stays untouched.
 */
@Composable
fun appDialogButtonColors(): TextButtonColors =
    dialogCapsuleColors(textColor = COUITheme.colorScheme.onBackground)

/**
 * The capsule of a button that destroys something.
 *
 * ColorOS 17 does not fill a delete button red: `StyleDialogUtil` and `ScreenLockFeature` keep the
 * ordinary translucent capsule and only call `setTextColor(R.color.color_delete_alert_dialog_button_warning_color)`
 * — see [DialogButtonBarDefaults.DangerTextColor], `#ffea3447`, the colour this reuses. So the
 * destructive button differs from 取消 by its label alone, which is what the system's own delete
 * dialogs look like.
 *
 * A caller reaches this tier by marking the action `recommended = true`: [DialogButtonBar] colours
 * every non-recommended action with one [TextButtonColors], so the red label cannot ride on the
 * shared `colors`.
 */
@Composable
fun appDialogDestructiveColors(): TextButtonColors =
    dialogCapsuleColors(textColor = DialogButtonBarDefaults.DangerTextColor)

@Composable
private fun dialogCapsuleColors(textColor: Color): TextButtonColors {
    val dark = COUITheme.colorScheme.background.luminance() < 0.5f
    return ButtonDefaults.textButtonColorsBorderless(
        color = if (dark) Color(0x26FFFFFF) else Color(0xAAFFFFFF),
        disabledColor = Color(0x14000000),
        textColor = textColor,
        disabledTextColor = COUITheme.colorScheme.disabledOnSurface,
    )
}
