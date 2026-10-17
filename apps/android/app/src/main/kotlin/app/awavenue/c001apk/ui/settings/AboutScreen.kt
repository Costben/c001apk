package app.awavenue.c001apk.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.BuildConfig
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toImageBitmap
import app.awavenue.c001apk.ui.nav.LocalNavigator
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.Link
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/** A third-party component the app ships or derives code from, with its own licence. */
private class Notice(val title: String, val licence: String, val summary: String, val url: String)

private val notices = listOf(
    Notice(
        title = "coui17",
        licence = "Apache-2.0",
        summary = "界面组件库，上游为 suqi8/coui 与 compose-miuix-ui/miuix",
        url = Protocol.URL_COUI,
    ),
    Notice(
        title = "coolapk-desktop",
        licence = "MIT",
        summary = "Token V3 兼容实现与协议查找表参考",
        url = "https://github.com/daimiaopeng/coolapk-desktop",
    ),
    Notice(
        title = "PiliPlus",
        licence = "GPL-3.0",
        summary = "启动器图标的系统取色配色参考",
        url = "https://github.com/bggRGjQaUbCoE/PiliPlus",
    ),
    Notice(
        title = "Material Icons",
        licence = "Apache-2.0",
        summary = "组件库未提供的图标路径数据",
        url = "https://fonts.google.com/icons",
    ),
)

private const val ACKNOWLEDGEMENTS =
    "酷安名称、商标及服务内容归相应权利人。协议查找表用于非公开协议兼容，其第三方来源与权利" +
        "不由原项目 MIT 许可担保。完整声明见仓库 docs/THIRD_PARTY_NOTICES.md。"

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = COUIScrollBehavior()
    val backdrop = rememberGlassBackdrop()
    val version = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = "关于",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val context = LocalContext.current
                    val icon = remember(context) {
                        runCatching {
                            context.packageManager.getApplicationIcon(context.packageName).toImageBitmap()
                        }.getOrNull()
                    }
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = "c001apk 标志",
                            modifier = Modifier
                                .width(96.dp)
                                .height(96.dp)
                                .clip(RoundedCornerShape(24.dp)),
                        )
                    }
                    Text("c001apk", fontSize = COUITheme.textStyles.title1.fontSize)
                    Text(version, color = COUITheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            item {
                SmallTitle("应用")
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    ArrowPreference(
                        title = "c001apk",
                        summary = "仅供测试",
                        startAction = { PreferenceStartIcon(COUIIcons.Info) },
                        onClick = null,
                    )
                    ArrowPreference(
                        title = "版本",
                        summary = version,
                        startAction = { PreferenceStartIcon(COUIIcons.Info) },
                        onClick = null,
                    )
                    ArrowPreference(
                        title = "项目地址",
                        summary = Protocol.URL_SOURCE_CODE,
                        startAction = { PreferenceStartIcon(COUIIcons.Link) },
                        onClick = { uriHandler.openUri(Protocol.URL_SOURCE_CODE) },
                    )
                    ArrowPreference(
                        title = "致谢 c001apk-flutter",
                        summary = Protocol.URL_UPSTREAM,
                        startAction = { PreferenceStartIcon(COUIIcons.Link) },
                        onClick = { uriHandler.openUri(Protocol.URL_UPSTREAM) },
                    )
                    ArrowPreference(
                        title = "界面组件 coui17",
                        summary = "ColorOS 风格 Compose 组件库",
                        startAction = { PreferenceStartIcon(COUIIcons.Link) },
                        onClick = { uriHandler.openUri(Protocol.URL_COUI) },
                    )
                }
            }
            item {
                SmallTitle("开源许可证")
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    notices.forEach { notice ->
                        ArrowPreference(
                            title = notice.title,
                            summary = "${notice.licence} · ${notice.summary}",
                            startAction = { PreferenceStartIcon(COUIIcons.Link) },
                            onClick = { uriHandler.openUri(notice.url) },
                        )
                    }
                }
            }
            item {
                Text(
                    ACKNOWLEDGEMENTS,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                    textAlign = TextAlign.Start,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
