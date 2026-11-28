package app.awavenue.c001apk.ui.app

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.platform.ApkDownloadState
import io.github.suqi8.coui.kmp.basic.InstallLoadProgress
import io.github.suqi8.coui.kmp.basic.InstallLoadStyle
import io.github.suqi8.coui.kmp.basic.LoadProgressState
import kotlin.math.roundToInt

/**
 * Compact ColorOS flat capsule action button (`64dp × 28dp`) that renders download progress
 * directly inside the capsule with two-pass accent/white text contrast.
 */
@Composable
internal fun ApkDownloadPill(
    state: ApkDownloadState,
    idleText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadState: LoadProgressState
    val progress: Int
    val label: String

    when (state) {
        ApkDownloadState.Idle -> {
            loadState = LoadProgressState.Default
            progress = 0
            label = idleText
        }

        is ApkDownloadState.Resolving -> {
            loadState = LoadProgressState.Wait
            progress = 0
            label = "等待"
        }

        is ApkDownloadState.Downloading -> {
            loadState = LoadProgressState.Loading
            if (state.progress >= 0f) {
                progress = (state.progress * 100f).roundToInt().coerceIn(0, 100)
                label = "$progress%"
            } else {
                progress = 20
                label = "下载中"
            }
        }

        is ApkDownloadState.ReadyToInstall -> {
            loadState = LoadProgressState.Default
            progress = 100
            label = "安装"
        }

        is ApkDownloadState.Failed -> {
            loadState = LoadProgressState.Fail
            progress = 0
            label = "重试"
        }
    }

    InstallLoadProgress(
        onClick = onClick,
        modifier = modifier
            .width(64.dp)
            .height(28.dp),
        state = loadState,
        progress = progress,
        max = 100,
        text = label,
        style = InstallLoadStyle.Default,
        hapticFeedback = true,
        downloadingContentDescription = "下载中 $label，点击取消",
    )
}
