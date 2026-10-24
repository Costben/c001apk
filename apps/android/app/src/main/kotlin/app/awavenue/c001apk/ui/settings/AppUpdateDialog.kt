package app.awavenue.c001apk.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.UpdatePromptState
import app.awavenue.c001apk.data.UpdateResult
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.openBrowser
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.text.MarkdownBody
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.Instant
import java.time.ZoneId

/** 发现新版本: 稍后 closes, 跳过 silences this version for the automatic check, 更新 opens the APK. */
@Composable
fun AppUpdateDialog(update: UpdateResult.Available, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val release = update.release

    OverlayDialog(show = true, title = "发现新版本", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("${update.current} → ${release.version}", style = COUITheme.textStyles.headline1)
                release.publishedAt?.let {
                    val date = Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text("发布于 $date", style = COUITheme.textStyles.footnote1)
                }
                MarkdownBody(
                    release.notes,
                    modifier = Modifier.padding(top = 12.dp),
                    fontSize = 14.sp,
                    explicit = true,
                    coolapk = false,
                    showEmoji = false,
                )
                TextButton(text = "查看发布页面", onClick = { context.openBrowser(release.page) })
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "稍后", onClick = onDismiss)
                TextButton(
                    text = "跳过",
                    onClick = {
                        app.settings.skipAppUpdate(release.version.toString())
                        onDismiss()
                    },
                )
                TextButton(
                    text = "更新",
                    onClick = {
                        context.openBrowser(release.downloadFor(Build.SUPPORTED_ABIS.toList()))
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * The once-per-cold-start check. It only prompts on the main screen, so it never interrupts a
 * login, a verification or another page.
 */
@Composable
fun AutomaticUpdateCheck() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    var update by remember { mutableStateOf<UpdateResult.Available?>(null) }

    LaunchedEffect(Unit) {
        update = app.updates.check(automatic = true) as? UpdateResult.Available
        // Even a suppressed or failed check settles: the announcement monitor is waiting for it.
        UpdatePromptState.settleAutomatic()
    }

    val pending = update
    if (pending != null && !navigator.hasOverlay && app.settings.autoCheckAppUpdate) {
        LaunchedEffect(pending) { UpdatePromptState.markPromptShown() }
        AppUpdateDialog(pending, onDismiss = { update = null })
    }
}
