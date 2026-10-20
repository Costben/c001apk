package app.awavenue.c001apk.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.data.OfficialDeviceIdentity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.toast
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.LoadingButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.theme.COUITheme

/** The package and page the official client exposes its device identifiers on. */
private const val OFFICIAL_COOLAPK = "com.coolapk.market"
private const val OFFICIAL_DEVICE_ID_PAGE = "coolmarket://www.coolapk.com/native/testSettings"

/**
 * The first-launch guide for importing a device identifier from the official Coolapk client.
 * Shown as an overlay above the navigation host until 完成 or 跳过 is pressed, and reopened from
 * 设置. Importing is optional: the app keeps working with a generated identifier, it just runs
 * into the captcha more often.
 */
@Composable
fun OnboardingScreen(onClose: () -> Unit) {
    val settings = LocalApp.current.settings
    val context = LocalContext.current

    var draft by rememberSaveable { mutableStateOf("") }
    var recognized by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun recognize(text: String) {
        runCatching { OfficialDeviceIdentity.parse(text) }
            .onSuccess {
                recognized = it
                draft = ""
                saved = false
                error = null
            }
            .onFailure {
                recognized = null
                saved = false
                error = it.message ?: "无法识别粘贴内容"
            }
    }

    fun pasteAndRecognize() {
        val text = context.readClipboard()
        if (text.isNullOrBlank()) {
            recognized = null
            error = "剪贴板没有文本，请先在官方酷安完成复制"
            return
        }
        recognize(text)
    }

    fun save() {
        val id = recognized ?: return
        settings.updateSzlmId(id)
        saved = true
        error = null
        context.toast("已保存设备标识")
    }

    fun complete() {
        if (!saved) recognized?.let(settings::updateSzlmId)
        onClose()
    }

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize()) {
        // A tap that lands on no control must not reach the page underneath the guide.
        Box(
            Modifier
                .fillMaxSize()
                .background(COUITheme.colorScheme.background)
                .pointerInput(Unit) { detectTapGestures { } },
        )
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = "使用教程",
                    gradientGlass = true,
                    actions = {
                        TextButton(text = "跳过", onClick = onClose)
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("欢迎使用 c001apk", style = COUITheme.textStyles.title1)
                Text(
                    "花一分钟导入官方酷安的设备标识，能减少浏览动态和评论时反复出现的人机验证。" +
                        "不导入也能正常浏览，只是验证会更频繁。",
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )

                StepCard(
                    number = 1,
                    title = "打开官方酷安",
                    body = "在官方酷安中进入「我 → 设置 → 检测日志与测试」。下面的按钮会直接打开这一页，" +
                        "不会读取酷安账号或密码。",
                ) {
                    TextButton(
                        text = "打开检测日志与测试页",
                        onClick = {
                            when (context.openOfficialDeviceIdPage()) {
                                OfficialPageResult.Opened -> Unit
                                OfficialPageResult.NotInstalled ->
                                    context.toast("尚未安装官方酷安，请先安装后再试")
                                OfficialPageResult.Failed ->
                                    context.toast("无法打开官方酷安，请确认应用已启用并更新")
                            }
                        },
                    )
                }

                StepCard(
                    number = 2,
                    title = "复制完整内容",
                    body = "向下滚动，找到「复制 OAID」。在同一个页面里慢慢点 5 次：前几次只有 OAID 或提示" +
                        "没有 OAID，第 5 次才会复制包含 ShuzlmID 的完整文本。\n\n" +
                        "按钮虽然叫「复制 OAID」，这里实际要用的是其中的 ShuzlmID（数盟 ID）。",
                )

                StepCard(
                    number = 3,
                    title = "粘贴并识别",
                    body = "回到这里，粘贴刚复制的整段文本。只会保存其中的 ShuzlmID，" +
                        "其他字段和本机机型参数都不会改动。",
                ) {
                    TextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            recognized = null
                            saved = false
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = "粘贴完整文本",
                        minLines = 3,
                        maxLines = 6,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(text = "粘贴并识别", onClick = ::pasteAndRecognize)
                        TextButton(
                            text = "保存并应用",
                            onClick = ::save,
                            enabled = recognized != null && !saved,
                        )
                    }
                    recognized?.let { id ->
                        Text(
                            if (saved) {
                                "已保存，本机请求会使用这份设备标识。返回内容页刷新即可。"
                            } else {
                                "已识别 ShuzlmID（${id.length} 位），点击「保存并应用」后生效。"
                            },
                            style = COUITheme.textStyles.body2,
                            color = COUITheme.colorScheme.primary,
                        )
                    }
                    error?.let {
                        Text(
                            it,
                            style = COUITheme.textStyles.body2,
                            color = COUITheme.colorScheme.error,
                        )
                    }
                }

                Text(
                    "以后可以在「我的 → 设置 → 协议参数」查看或修改设备标识，也可以在设置里重看本教程。",
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )

                LoadingButton(
                    text = "完成",
                    onClick = ::complete,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(COUITheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$number",
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onPrimary,
                    )
                }
                Text(title, style = COUITheme.textStyles.title4)
            }
            Text(
                body,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            content()
        }
    }
}

/** The official client's test page, or a reason it did not open. */
private enum class OfficialPageResult { Opened, NotInstalled, Failed }

private fun Context.openOfficialDeviceIdPage(): OfficialPageResult = try {
    packageManager.getApplicationInfo(OFFICIAL_COOLAPK, 0)
    startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_DEVICE_ID_PAGE))
            .setPackage(OFFICIAL_COOLAPK),
    )
    OfficialPageResult.Opened
} catch (e: PackageManager.NameNotFoundException) {
    OfficialPageResult.NotInstalled
} catch (e: Exception) {
    OfficialPageResult.Failed
}

private fun Context.readClipboard(): String? {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(this)?.toString()
}
