package app.awavenue.c001apk.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.BuildConfig
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.data.UpdateResult
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.openBrowser
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.toImageBitmap
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.preference.AppInfoPreference
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.launch

private data class AboutRef(
    val title: String,
    val summary: String,
    val status: String,
    val url: String,
)

private val UI_UPSTREAMS = listOf(
    AboutRef(
        title = "COUI 17",
        summary = "自维护 ColorOS 17 Compose 组件库 · Costben/coui17",
        status = "Apache-2.0",
        url = Protocol.URL_COUI,
    ),
    AboutRef(
        title = "COUI Kit",
        summary = "io.github.itoseo:coui · Jetpack Compose 组件与视觉效果库",
        status = "MPL-2.0",
        url = "https://github.com/ItosEO/COUI-Kit",
    ),
    AboutRef(
        title = "COUI",
        summary = "suqi8/coui · ColorOS 16 跨平台 Compose UI 组件库上游",
        status = "Apache-2.0",
        url = "https://github.com/suqi8/coui",
    ),
    AboutRef(
        title = "Miuix",
        summary = "compose-miuix-ui/miuix · Compose Multiplatform 基础组件上游",
        status = "Apache-2.0",
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
    AboutRef(
        title = "Material Icons",
        summary = "组件库未提供的图标路径数据",
        status = "Apache-2.0",
        url = "https://fonts.google.com/icons",
    ),
)

private val PROJECT_REFERENCES = listOf(
    AboutRef(
        title = "c001apk-flutter",
        summary = "bggRGjQaUbCoE/c001apk-flutter · 上游 Flutter 客户端项目",
        status = "GitHub",
        url = Protocol.URL_UPSTREAM,
    ),
    AboutRef(
        title = "c001apk",
        summary = "KiritoXDone/c001apk · 功能逻辑与大屏布局参考",
        status = "GitHub",
        url = "https://github.com/KiritoXDone/c001apk",
    ),
    AboutRef(
        title = "c001apk-compose",
        summary = "bggRGjQaUbCoE/c001apk-compose · 会话管理接口路径参考",
        status = "AGPL-3.0",
        url = "https://github.com/bggRGjQaUbCoE/c001apk-compose",
    ),
    AboutRef(
        title = "coolapk-desktop",
        summary = "daimiaopeng/coolapk-desktop · Token V3 兼容实现与协议查找表参考",
        status = "MIT",
        url = "https://github.com/daimiaopeng/coolapk-desktop",
    ),
    AboutRef(
        title = "PiliPlus",
        summary = "bggRGjQaUbCoE/PiliPlus · 启动器图标取色、设置分类与更新弹窗参考",
        status = "GPL-3.0",
        url = "https://github.com/bggRGjQaUbCoE/PiliPlus",
    ),
)

private val CORE_LIBRARIES = listOf(
    AboutRef(
        title = "AndroidX Media3",
        summary = "ExoPlayer 视频流播放与媒体控制引擎",
        status = "Apache-2.0",
        url = "https://github.com/androidx/media",
    ),
    AboutRef(
        title = "Coil 3",
        summary = "头像、九宫格与动图加载",
        status = "Apache-2.0",
        url = "https://github.com/coil-kt/coil",
    ),
    AboutRef(
        title = "OkHttp",
        summary = "HTTP/2 网络连接池与协议请求",
        status = "Apache-2.0",
        url = "https://github.com/square/okhttp",
    ),
    AboutRef(
        title = "Jetpack Compose",
        summary = "声明式 UI 框架与预测性返回手势转场",
        status = "Apache-2.0",
        url = "https://developer.android.com/compose",
    ),
)

private const val DISCLAIMER =
    "第三方酷安客户端，仅供个人学习与测试。酷安名称、商标及服务内容归相应权利人。" +
        "协议查找表用于非公开协议兼容，其第三方来源与权利不由原项目 MIT 许可担保。" +
        "完整声明见仓库 docs/THIRD_PARTY_NOTICES.md。"

@Composable
fun AboutScreen() {
    val app = LocalApp.current
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberPageScrollBehavior("about")
    val backdrop = rememberGlassBackdrop()
    val appIcon = rememberAppIconPainter(context)
    val versionText = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    var checkingUpdate by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateResult.Available?>(null) }

    fun launchUpdateCheck(forceShow: Boolean) {
        scope.launch {
            checkingUpdate = true
            when (val result = app.updates.check(forceShow = forceShow)) {
                is UpdateResult.Available -> update = result
                UpdateResult.UpToDate -> context.toast(if (forceShow) "获取更新说明失败" else "已是最新版本")
                is UpdateResult.Failed -> context.toast(result.message)
                UpdateResult.Busy -> context.toast("正在检查更新，请稍候")
                UpdateResult.Suppressed -> Unit
            }
            checkingUpdate = false
        }
    }

    Scaffold(
        edgeToEdge = true,
        containerColor = AppBarBlurOverlayDefaults.containerColor(),
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "关于",
                    showDivider = false,
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = rememberHapticClick { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = rememberPageListState("about"),
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = innerPadding.calculateTopPadding() + AppBarBlurOverlayDefaults.ExtraHeight + 10.dp,
                end = 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "app-header") {
                AppInfoPreference(
                    appName = "c001apk",
                    appVersion = versionText,
                    icon = appIcon,
                    onVersionLongClick = {
                        context.copyText("c001apk $versionText", hint = "已复制版本信息")
                    },
                )
            }

            item(key = "project-title") { SectionTitle("项目", topPadding = 8.dp) }

            item(key = "project") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    ArrowPreference(
                        title = "开源仓库",
                        summary = Protocol.URL_SOURCE_CODE,
                        statusText = "GitHub",
                        cardListPosition = CardListPosition.Head,
                        onClick = rememberHapticClick { context.openBrowser(Protocol.URL_SOURCE_CODE) },
                    )
                    ArrowPreference(
                        title = "检查更新",
                        summary = if (checkingUpdate) "正在检查…" else "当前版本 ${BuildConfig.VERSION_NAME}",
                        cardListPosition = CardListPosition.Middle,
                        enabled = !checkingUpdate,
                        onClick = rememberHapticClick(enabled = !checkingUpdate) { launchUpdateCheck(forceShow = false) },
                    )
                    ArrowPreference(
                        title = "版本更新说明",
                        summary = "查看最新版本的更新日志与发布说明",
                        cardListPosition = CardListPosition.Tail,
                        enabled = !checkingUpdate,
                        onClick = rememberHapticClick(enabled = !checkingUpdate) { launchUpdateCheck(forceShow = true) },
                    )
                }
            }

            item(key = "ui-upstreams-title") { SectionTitle("前端界面库") }

            item(key = "ui-upstreams") {
                AboutGroupCard(
                    items = UI_UPSTREAMS,
                    onOpen = context::openBrowser,
                )
            }

            item(key = "references-title") { SectionTitle("协议与项目参考") }

            item(key = "references") {
                AboutGroupCard(
                    items = PROJECT_REFERENCES,
                    onOpen = context::openBrowser,
                )
            }

            item(key = "core-libs-title") { SectionTitle("核心开源依赖") }

            item(key = "core-libs") {
                AboutGroupCard(
                    items = CORE_LIBRARIES,
                    onOpen = context::openBrowser,
                )
            }

            item(key = "disclaimer") {
                Text(
                    text = DISCLAIMER,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }

    update?.let { AppUpdateDialog(it, onDismiss = { update = null }) }
}

/** Category header aligned with grouped preference cards inside a 12dp-padded list. */
@Composable
private fun SectionTitle(
    text: String,
    topPadding: Dp = 16.dp,
) {
    SmallTitle(
        text = text,
        insideMargin = PaddingValues(
            start = 16.dp,
            top = topPadding,
            end = 16.dp,
            bottom = 0.dp,
        ),
    )
}

/** Grouped preference card where adjacent rows use COUI's built-in card-list dividers. */
@Composable
private fun AboutGroupCard(
    items: List<AboutRef>,
    onOpen: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        items.forEachIndexed { index, item ->
            ArrowPreference(
                title = item.title,
                summary = item.summary,
                statusText = item.status,
                cardListPosition = cardListPositionOf(index, items.size),
                onClick = rememberHapticClick { onOpen(item.url) },
            )
        }
    }
}

@Composable
private fun rememberAppIconPainter(context: Context): Painter? = remember(context) {
    runCatching {
        BitmapPainter(context.packageManager.getApplicationIcon(context.packageName).toImageBitmap(size = 192))
    }.getOrNull()
}
