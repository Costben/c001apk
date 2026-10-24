package app.awavenue.c001apk.ui.feed.detail

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.toast
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.FileDownloads
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 保存内容: writes the post and up to three replies through the system file picker.
 *
 * The document is generated before the picker opens, so a slow image download cannot leave an
 * empty file behind and a failed preparation never asks for a destination.
 */
@Composable
fun ContentExportButton(
    feed: Entity?,
    articles: List<FeedArticle>,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var choosing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf<ExportFormat?>(null) }
    var pending by remember { mutableStateOf<PreparedExport?>(null) }
    var warnings by remember { mutableStateOf<List<String>?>(null) }

    fun finish(uri: Uri?, prepared: PreparedExport?) {
        if (uri == null || prepared == null) return
        pending = null
        scope.launch {
            val saved = withContext(Dispatchers.IO) { writeDocument(context, uri, prepared.bytes) }
            context.toast(if (saved) "内容已保存" else "保存失败，请检查目录权限和剩余空间后重试")
        }
    }

    val mdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        finish(it, pending)
    }
    val htmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) {
        finish(it, pending)
    }

    fun save(prepared: PreparedExport, target: ExportFormat) {
        pending = prepared
        when (target) {
            ExportFormat.Markdown -> mdLauncher.launch(prepared.fileName)
            ExportFormat.Html -> htmlLauncher.launch(prepared.fileName)
        }
    }

    fun start(target: ExportFormat) {
        val data = feed ?: return
        format = target
        scope.launch {
            preparing = true
            try {
                val service = ContentExportService(
                    loadPopular = { id -> app.repository.feedReply(id, "popular", 1, null, null) },
                    blockedUids = { app.blacklist.userBlacklist.toSet() },
                )
                val prepared = withContext(Dispatchers.Default) { service.prepare(data, articles, target) }
                pending = prepared
                if (prepared.warnings.isEmpty()) {
                    save(prepared, target)
                } else {
                    warnings = prepared.warnings
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                context.toast("保存失败，请检查目录权限和剩余空间后重试")
            } finally {
                preparing = false
            }
        }
    }

    TopBarIconButton(
        onClick = { choosing = true },
        enabled = feed != null && !preparing,
    ) {
        if (preparing) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(COUIIcons.FileDownloads, contentDescription = "保存内容")
        }
    }

    OverlayDialog(
        show = choosing,
        title = "保存内容",
        onDismissRequest = { choosing = false },
    ) {
        Column(Modifier.fillMaxWidth()) {
            ExportChoice(
                title = ExportFormat.Markdown.label,
                summary = ExportFormat.Markdown.detail,
                onClick = {
                    choosing = false
                    start(ExportFormat.Markdown)
                },
            )
            ExportChoice(
                title = ExportFormat.Html.label,
                summary = ExportFormat.Html.detail,
                onClick = {
                    choosing = false
                    start(ExportFormat.Html)
                },
            )
            Text(
                "正文与最多三条评论；置顶优先，其余按热门排序。",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }

    val warningList = warnings
    if (warningList != null) {
        OverlayDialog(
            show = true,
            title = "部分内容未能保存",
            summary = warningList.joinToString("\n\n") + "\n\n继续保存已取得的内容？",
            onDismissRequest = {
                warnings = null
                pending = null
            },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        warnings = null
                        pending = null
                    },
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = "继续保存",
                    onClick = {
                        warnings = null
                        val prepared = pending
                        val target = format
                        if (prepared != null && target != null) save(prepared, target)
                    },
                )
            }
        }
    }
}

private fun writeDocument(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no output stream")
}.isSuccess

@Composable
private fun ExportChoice(title: String, summary: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(title, style = COUITheme.textStyles.body1)
        Text(
            summary,
            modifier = Modifier.padding(top = 2.dp),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
