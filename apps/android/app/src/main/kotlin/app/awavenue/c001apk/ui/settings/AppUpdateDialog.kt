package app.awavenue.c001apk.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.InAppUpdateState
import app.awavenue.c001apk.data.InAppUpdater
import app.awavenue.c001apk.data.UpdateResult
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.openBrowser
import app.awavenue.c001apk.ui.nav.LocalNavigator
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.CheckboxDefaults
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class UpdateDialogAction {
    DISMISS,
    SKIP,
    BROWSER_DOWNLOAD,
}

/**
 * 更新提示。release notes 按 Markdown 渲染，可能有几十行，正文自己滚动、封顶 260dp，别把按钮顶出屏幕。
 * 「跳过本次更新」是个勾选框：勾上后不管点稍后还是点外面关掉，都记成跳过这个版本；点下载更新则照常下载。
 * 支持应用内下载与系统安装器调起，并在界面上实时呈现 COUI 进度条与状态。
 *
 * 面板用 [OverlayDialog] 而不是 `WindowDialog`：只有前者挂在根 Scaffold 上，能读到页面的
 * backdrop，磨砂半透明底与按钮胶囊的材质才和 COUI 单行输入对话框一致；窗口级对话框拿不到
 * backdrop，只能退回不透明底色，浅色主题下按钮胶囊和面板几乎同色，看不出边界。
 */
@Composable
fun UpdateDialog(
    version: String,
    prerelease: Boolean,
    notes: String,
    inAppUpdate: Boolean = false,
    downloadState: InAppUpdateState = InAppUpdateState.Idle,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onStartInAppUpdate: () -> Unit = {},
    onCancelInAppUpdate: () -> Unit = {},
    onInstall: () -> Unit = {},
    onBrowserDownload: () -> Unit = {},
) {
    var show by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<UpdateDialogAction?>(null) }
    var skip by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val haptic = LocalHapticFeedback.current
    val textMargin = DialogDefaults.insideMargin.width
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f

    // 推荐按钮固定 COUI 蓝。动态取色下 primary 可能被壁纸带成中性灰，蓝胶囊会退化成和磨砂面板
    // 同色的白灰块，两个按钮就都看不出边界了。次级胶囊沿用 COUI 原配方（面板提亮 67% 白）。
    val recommendColors = ButtonDefaults.textButtonColorsPrimary(
        color = if (isDark) Color(0xFF1A8CFF) else Color(0xFF0080FF),
    )

    // 未勾选的复选框描边默认取 colorScheme.secondary（浅色 #E5E5E5），那是给不透明白卡用的；
    // 磨砂面板本身已经压到 #EAEAEA，两者只差几个色阶，勾选框等于消失。这里改用正文副色的
    // 45% 不透明度，浅色落到 #818181：比标签文字浅一档，与面板的对比约 3.1:1，过得了非文本
    // 控件的 3:1 门槛。
    val checkboxColors = CheckboxDefaults.checkboxColors(
        uncheckedBackgroundColor = COUITheme.colorScheme.onBackgroundVariant.copy(alpha = 0.45f),
    )
    val notesScroll = rememberScrollState()

    OverlayDialog(
        show = show,
        title = "发现新版本 $version",
        summary = if (prerelease) "预发布版本" else "正式版本",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = {
            if (downloadState !is InAppUpdateState.Downloading) {
                show = false
            }
        },
        onDismissFinished = {
            if (!settled) {
                settled = true
                when (action) {
                    UpdateDialogAction.BROWSER_DOWNLOAD -> onBrowserDownload()
                    UpdateDialogAction.SKIP -> onSkip()
                    UpdateDialogAction.DISMISS, null -> {
                        if (skip) onSkip() else onDismiss()
                    }
                }
            }
        },
    ) {
        Column {
            if (notes.isNotBlank()) {
                // 正文封顶 260dp 后自己滚动，但滚到底之前没有任何提示，最后一行会被面板边缘硬切
                // 成半句话。这里把视口底部的内容淡出：DstOut 打在独立离屏层上，只挖掉正文自己，
                // 露出来的是面板，所以和磨砂底色无关。滚到底后 canScrollForward 变 false，淡出消失。
                MarkdownText(
                    notes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (notesScroll.canScrollForward) {
                                val fade = 32.dp.toPx()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startY = size.height - fade,
                                        endY = size.height,
                                    ),
                                    blendMode = BlendMode.DstOut,
                                )
                            }
                        }
                        .verticalScroll(notesScroll)
                        .padding(horizontal = textMargin),
                )
            }

            when (val state = downloadState) {
                is InAppUpdateState.Idle -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = textMargin, top = 12.dp)
                            .toggleable(
                                value = skip,
                                interactionSource = null,
                                indication = null,
                                role = Role.Checkbox,
                                onValueChange = {
                                    skip = it
                                    haptic.performHapticFeedback(
                                        if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                    )
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(state = ToggleableState(skip), onClick = null, colors = checkboxColors)
                        Text(
                            "跳过本次更新",
                            fontSize = 14.sp,
                            color = COUITheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }

                    DialogButtonBar(
                        negative = DialogButtonBarAction(text = "稍后", onClick = { show = false }),
                        positive = DialogButtonBarAction(
                            text = if (inAppUpdate) "应用内更新" else "下载更新",
                            recommended = true,
                            onClick = {
                                if (inAppUpdate) {
                                    onStartInAppUpdate()
                                } else {
                                    action = UpdateDialogAction.BROWSER_DOWNLOAD
                                    show = false
                                }
                            },
                        ),
                        recommendColors = recommendColors,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                is InAppUpdateState.Downloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textMargin)
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val downloadedStr = InAppUpdater.formatBytes(state.downloadedBytes)
                            val totalStr = if (state.totalBytes > 0) InAppUpdater.formatBytes(state.totalBytes) else "未知大小"
                            Text(
                                "正在下载 $downloadedStr / $totalStr",
                                fontSize = 13.sp,
                                color = COUITheme.colorScheme.onBackgroundVariant,
                            )
                            if (state.progress >= 0f) {
                                Text(
                                    "${(state.progress * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = COUITheme.colorScheme.primary,
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = if (state.progress >= 0f) state.progress else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "取消",
                            onClick = { onCancelInAppUpdate() },
                        ),
                        positive = DialogButtonBarAction(
                            text = if (state.progress >= 0f) "下载中 ${(state.progress * 100).toInt()}%" else "下载中…",
                            enabled = false,
                            onClick = {},
                        ),
                        recommendColors = recommendColors,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                is InAppUpdateState.ReadyToInstall -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textMargin)
                            .padding(top = 12.dp),
                    ) {
                        Text(
                            "安装包已下载完成，正在调起系统安装器…",
                            fontSize = 13.sp,
                            color = COUITheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    DialogButtonBar(
                        negative = DialogButtonBarAction(text = "关闭", onClick = { show = false }),
                        positive = DialogButtonBarAction(
                            text = "立即安装",
                            recommended = true,
                            onClick = { onInstall() },
                        ),
                        recommendColors = recommendColors,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                is InAppUpdateState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textMargin)
                            .padding(top = 12.dp),
                    ) {
                        Text(
                            state.message,
                            fontSize = 13.sp,
                            color = COUITheme.colorScheme.error,
                        )
                    }

                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "浏览器下载",
                            onClick = {
                                action = UpdateDialogAction.BROWSER_DOWNLOAD
                                show = false
                            },
                        ),
                        positive = DialogButtonBarAction(
                            text = "重试",
                            recommended = true,
                            onClick = { onStartInAppUpdate() },
                        ),
                        recommendColors = recommendColors,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** 发现新版本: 稍后 closes, 跳过 silences this version for the automatic check, 更新 triggers in-app or browser update. */
@Composable
fun AppUpdateDialog(update: UpdateResult.Available, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val release = update.release
    val isPrerelease = release.version.preRelease != null

    val abis = remember { Build.SUPPORTED_ABIS.toList() }
    val apkUrl = remember(release) { release.apkDownloadUrl(abis) }
    val inAppUpdateEnabled = app.settings.inAppUpdate && !apkUrl.isNullOrBlank()

    var downloadState by remember { mutableStateOf<InAppUpdateState>(InAppUpdateState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun startDownload() {
        val targetUrl = apkUrl ?: return
        downloadJob?.cancel()
        downloadJob = scope.launch {
            InAppUpdater.downloadApk(
                context = context,
                url = targetUrl,
                version = release.version.toString(),
                onProgress = { state ->
                    downloadState = state
                    if (state is InAppUpdateState.ReadyToInstall) {
                        InAppUpdater.installApk(context, state.apkFile)
                    }
                },
            )
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadState = InAppUpdateState.Idle
    }

    UpdateDialog(
        version = release.version.toString(),
        prerelease = isPrerelease,
        notes = release.notes,
        inAppUpdate = inAppUpdateEnabled,
        downloadState = downloadState,
        onDismiss = {
            cancelDownload()
            onDismiss()
        },
        onSkip = {
            cancelDownload()
            app.settings.skipAppUpdate(release.version.toString())
            onDismiss()
        },
        onStartInAppUpdate = {
            startDownload()
        },
        onCancelInAppUpdate = {
            cancelDownload()
        },
        onInstall = {
            (downloadState as? InAppUpdateState.ReadyToInstall)?.let {
                InAppUpdater.installApk(context, it.apkFile)
            }
        },
        onBrowserDownload = {
            cancelDownload()
            context.openBrowser(release.downloadFor(abis))
            onDismiss()
        },
    )
}

/**
 * 只认 release notes 里用得到的那部分 Markdown：`#` 标题、`-`/`*`/`1.` 列表、`>` 引用、
 * ``` 代码块，行内 `**粗**`、`*斜*`、`` `代码` ``、`[文字](链接)`。其余原样当正文显示。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.onBackgroundVariant,
    headingColor: Color = COUITheme.colorScheme.onBackground,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val linkColor = COUITheme.colorScheme.primary
    val codeBackground = headingColor.copy(alpha = 0.08f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, linkColor, codeBackground),
                    fontSize = if (block.level <= 2) 15.sp else 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = headingColor,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp),
                )
                is MdBlock.Paragraph -> Text(
                    inline(block.text, linkColor, codeBackground),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = color,
                )
                is MdBlock.Item -> Row(Modifier.padding(start = (block.depth * 12).dp)) {
                    Text(
                        block.marker,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = color,
                        modifier = Modifier.widthIn(min = 14.dp),
                    )
                    Text(
                        inline(block.text, linkColor, codeBackground),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                }
                is MdBlock.Quote -> Text(
                    inline(block.text, linkColor, codeBackground),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontStyle = FontStyle.Italic,
                    color = color.copy(alpha = color.alpha * 0.8f),
                    modifier = Modifier.padding(start = 10.dp),
                )
                is MdBlock.Code -> Text(
                    block.text,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Item(val marker: String, val text: String, val depth: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

private val headingRegex = Regex("^(#{1,6})\\s+(.*?)\\s*#*$")
private val bulletRegex = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val orderedRegex = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
private val ruleRegex = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

private fun parseBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    val lines = markdown.replace("\r\n", "\n").lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                blocks += MdBlock.Code(code.toString())
            }
            trimmed.isEmpty() || ruleRegex.matches(line) -> flush()
            headingRegex.matches(trimmed) -> {
                flush()
                val m = headingRegex.find(trimmed)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            bulletRegex.matches(line) -> {
                flush()
                val m = bulletRegex.find(line)!!
                blocks += MdBlock.Item("•", m.groupValues[2], m.groupValues[1].length / 2)
            }
            orderedRegex.matches(line) -> {
                flush()
                val m = orderedRegex.find(line)!!
                blocks += MdBlock.Item("${m.groupValues[2]}.", m.groupValues[3], m.groupValues[1].length / 2)
            }
            trimmed.startsWith(">") -> {
                flush()
                blocks += MdBlock.Quote(trimmed.removePrefix(">").trim())
            }
            // 列表项的续行直接接在上一项后面，不另起段落。
            paragraph.isEmpty() && blocks.lastOrNull() is MdBlock.Item && line.startsWith(" ") -> {
                val last = blocks.removeAt(blocks.lastIndex) as MdBlock.Item
                blocks += last.copy(text = last.text + " " + trimmed)
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flush()
    return blocks
}

/** 行内语法：粗体、斜体、行内代码、链接。没配对的标记原样留着。 */
private fun inline(text: String, linkColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    append(text[i + 1])
                    i += 2
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) { append(c); i++ } else {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end < 0) { append("**"); i += 2 } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(inline(text.substring(i + 2, end), linkColor, codeBackground))
                        }
                        i = end + 2
                    }
                }
                // 下划线不当强调：版本说明里常有 snake_case 之类的标识符。
                c == '*' && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
                    val end = text.indexOf(c, i + 1)
                    if (end < 0) { append(c); i++ } else {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(inline(text.substring(i + 1, end), linkColor, codeBackground))
                        }
                        i = end + 1
                    }
                }
                c == '[' -> {
                    val close = text.indexOf("](", i + 1)
                    val end = if (close < 0) -1 else text.indexOf(')', close + 2)
                    if (end < 0) { append(c); i++ } else {
                        val label = text.substring(i + 1, close)
                        val url = text.substring(close + 2, end).trim()
                        withLink(
                            LinkAnnotation.Url(
                                url,
                                TextLinkStyles(
                                    style = SpanStyle(color = linkColor),
                                    pressedStyle = SpanStyle(color = linkColor.copy(alpha = linkColor.alpha * 0.3f)),
                                ),
                            ),
                        ) { append(inline(label, linkColor, codeBackground)) }
                        i = end + 1
                    }
                }
                else -> {
                    append(c)
                    i++
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
    }

    val pending = update
    if (pending != null && !navigator.hasOverlay && app.settings.autoCheckAppUpdate) {
        AppUpdateDialog(pending, onDismiss = { update = null })
    }
}

