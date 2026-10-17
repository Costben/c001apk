package app.awavenue.c001apk.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.BuildConfig
import app.awavenue.c001apk.data.BlacklistType
import app.awavenue.c001apk.data.DarkMode
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.data.SeedColors
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.openBrowser
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import coil3.SingletonImageLoader
import io.github.suqi8.coui.kmp.basic.BasicComponentColors
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Background
import io.github.suqi8.coui.kmp.icon.extended.Blocklist
import io.github.suqi8.coui.kmp.icon.extended.Clear
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.Link
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.icon.extended.Recent
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Theme
import io.github.suqi8.coui.kmp.icon.extended.Tune
import io.github.suqi8.coui.kmp.icon.extended.Update
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.preference.WindowDropdownPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** The scale steps the Flutter slider dialog snaps to. */
private val fontScaleSteps = listOf(0.8f, 0.85f, 0.9f, 0.95f, 1f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f)

/** The 关注内容 options, labelled the way the settings page prints them. */
private val followTypes = listOf("全部", "用户", "话题", "数码", "应用")

/** The 深色模式 options in the order the Flutter dropdown lists them. */
private val darkModes = listOf(DarkMode.Off, DarkMode.On, DarkMode.System)

@Composable
fun SettingsScreen(outerPadding: PaddingValues) {
    val app = LocalApp.current
    val settings = app.settings
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = COUIScrollBehavior()
    val scope = rememberCoroutineScope()

    val backdrop = rememberGlassBackdrop()

    var showFontScale by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf<Long?>(null) }
    var askClearCache by remember { mutableStateOf(false) }
    var askLogout by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    // Upstream reads the cache size when the page opens, so the row shows it before any tap.
    LaunchedEffect(Unit) {
        cacheSize = runCatching { cacheBytes(context) }.getOrNull()
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = "设置",
                    scrollBehavior = scrollBehavior,
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(COUIIcons.More, contentDescription = "菜单")
                            }
                            OverlayListPopup(
                                show = menu,
                                alignment = PopupPositionProvider.Align.BottomEnd,
                                onDismissRequest = { menu = false },
                            ) {
                                ListPopupColumn {
                                    val entries = listOf(
                                        "意见反馈" to { context.openBrowser(Protocol.URL_SOURCE_CODE) },
                                        "关于" to { navigator.push(Route.About) },
                                    )
                                    entries.forEachIndexed { index, entry ->
                                        DropdownImpl(
                                            text = entry.first,
                                            optionSize = entries.size,
                                            isSelected = false,
                                            index = index,
                                            onSelectedIndexChange = {
                                                menu = false
                                                entry.second()
                                            },
                                        )
                                    }
                                }
                            }
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
                bottom = outerPadding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            item {
                SmallTitle("c001apk")
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    ArrowPreference(
                        title = "协议参数",
                        summary = "${settings.deviceParams.brand} ${settings.deviceParams.model}",
                        startAction = { PreferenceStartIcon(COUIIcons.Settings) },
                        onClick = { navigator.push(Route.Params) },
                    )
                }
            }
            item {
                SmallTitle("主题")
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    SwitchPreference(
                        title = "动态取色",
                        checked = settings.dynamicColor,
                        onCheckedChange = settings::updateDynamicColor,
                        startAction = { PreferenceStartIcon(COUIIcons.Background) },
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                    WindowDropdownPreference(
                        title = "主题颜色",
                        items = SeedColors.labels,
                        selectedIndex = settings.seedColor,
                        onSelectedIndexChange = settings::updateSeedColor,
                        startAction = { PreferenceStartIcon(COUIIcons.Photos) },
                        enabled = !settings.dynamicColor,
                        maxHeight = 380.dp,
                    )
                    WindowDropdownPreference(
                        title = "深色模式",
                        items = darkModes.map { it.label },
                        selectedIndex = darkModes.indexOf(settings.darkMode),
                        onSelectedIndexChange = { settings.updateDarkMode(darkModes[it]) },
                        startAction = { PreferenceStartIcon(COUIIcons.Theme) },
                    )
                }
            }
            item {
                SmallTitle("显示")
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    ArrowPreference(
                        title = "用户黑名单",
                        startAction = { PreferenceStartIcon(COUIIcons.Blocklist) },
                        onClick = { navigator.push(Route.Blacklist(BlacklistType.User)) },
                    )
                    ArrowPreference(
                        title = "话题黑名单",
                        startAction = { PreferenceStartIcon(COUIIcons.Blocklist) },
                        onClick = { navigator.push(Route.Blacklist(BlacklistType.Topic)) },
                    )
                    ArrowPreference(
                        title = "字体大小",
                        summary = "${scaleText(settings.fontScale)}x",
                        startAction = { PreferenceStartIcon(COUIIcons.Tune) },
                        onClick = { showFontScale = true },
                    )
                    WindowDropdownPreference(
                        title = "关注内容",
                        items = followTypes,
                        selectedIndex = settings.followType,
                        onSelectedIndexChange = settings::updateFollowType,
                        startAction = { PreferenceStartIcon(COUIIcons.Recent) },
                    )
                    SwitchPreference(
                        title = "在浏览器中打开",
                        checked = settings.openInBrowser,
                        onCheckedChange = settings::updateOpenInBrowser,
                        startAction = { PreferenceStartIcon(COUIIcons.Link) },
                    )
                    SwitchPreference(
                        title = "记录浏览历史",
                        checked = settings.recordHistory,
                        onCheckedChange = settings::updateRecordHistory,
                        startAction = { PreferenceStartIcon(COUIIcons.Recent) },
                    )
                    SwitchPreference(
                        title = "显示表情",
                        checked = settings.showEmoji,
                        onCheckedChange = settings::updateShowEmoji,
                        startAction = { PreferenceStartIcon(AppIcons.EmojiEmotions) },
                    )
                    SwitchPreference(
                        title = "检查更新",
                        checked = settings.checkUpdate,
                        onCheckedChange = settings::updateCheckUpdate,
                        startAction = { PreferenceStartIcon(COUIIcons.Update) },
                    )
                }
            }
            item {
                SmallTitle("其他")
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    ArrowPreference(
                        title = "关于",
                        summary = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                        startAction = { PreferenceStartIcon(COUIIcons.Info) },
                        onClick = { navigator.push(Route.About) },
                    )
                    ArrowPreference(
                        title = "清理缓存",
                        summary = cacheSize?.let(::formatSize),
                        startAction = { PreferenceStartIcon(COUIIcons.Clear) },
                        onClick = {
                            scope.launch {
                                cacheSize = runCatching { cacheBytes(context) }.getOrNull()
                                askClearCache = true
                            }
                        },
                    )
                    if (app.session.isLogin) {
                        ArrowPreference(
                            title = "退出登录",
                            summary = app.session.username,
                            titleColor = BasicComponentColors(
                                COUITheme.colorScheme.error,
                                COUITheme.colorScheme.error,
                            ),
                            startAction = { PreferenceStartIcon(COUIIcons.Lock) },
                            onClick = { askLogout = true },
                        )
                    }
                }
            }
        }
    }

    if (showFontScale) {
        FontScaleDialog(
            current = settings.fontScale,
            onChange = settings::updateFontScale,
            onClose = { showFontScale = false },
        )
    }

    if (askClearCache) {
        ClearCacheDialog(
            cacheSize = cacheSize ?: 0L,
            onConfirm = {
                askClearCache = false
                scope.launch {
                    runCatching { clearCache(context) }
                        .onSuccess { context.toast("已清理缓存，释放 ${formatSize(it)}") }
                        .onFailure { context.toast("缓存清理失败，请稍后重试") }
                    cacheSize = runCatching { cacheBytes(context) }.getOrNull()
                }
            },
            onCancel = { askClearCache = false },
        )
    }

    if (askLogout) {
        LogoutDialog(
            onConfirm = {
                askLogout = false
                app.session.logout()
            },
            onCancel = { askLogout = false },
        )
    }
}

/** The start icon every preference row on the settings pages shares. */
@Composable
internal fun PreferenceStartIcon(icon: ImageVector) {
    Box(Modifier.padding(end = 8.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = COUITheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun ClearCacheDialog(cacheSize: Long, onConfirm: () -> Unit, onCancel: () -> Unit) {
    OverlayAlertDialog(
        show = true,
        title = "清理缓存",
        summary = "当前缓存大小：${formatSize(cacheSize)}",
        confirmText = "确定",
        cancelText = "取消",
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    OverlayAlertDialog(
        show = true,
        title = "确定退出登录？",
        confirmText = "确定",
        cancelText = "取消",
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

@Composable
private fun FontScaleDialog(current: Float, onChange: (Float) -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableFloatStateOf(current) }

    OverlayDialog(show = true, title = "字体大小", onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            SliderPreference(
                value = draft,
                onValueChange = { draft = it },
                valueRange = fontScaleSteps.first()..fontScaleSteps.last(),
                keyPoints = fontScaleSteps,
                showKeyPoints = true,
                valueText = scaleText(draft),
            )
            Text(
                "字体大小：${scaleText(draft)}",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                fontSize = (15 * draft).sp,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = "重置",
                    onClick = {
                        onChange(1f)
                        onClose()
                    },
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        onChange(draft)
                        onClose()
                    },
                )
            }
        }
    }
}

private suspend fun cacheBytes(context: Context): Long = withContext(Dispatchers.IO) {
    val loader = SingletonImageLoader.get(context)
    (loader.diskCache?.size ?: 0L) + (loader.memoryCache?.size ?: 0L)
}

private suspend fun clearCache(context: Context): Long {
    val before = cacheBytes(context)
    withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        loader.diskCache?.clear()
        loader.memoryCache?.clear()
    }
    return (before - cacheBytes(context)).coerceAtLeast(0L)
}

private fun scaleText(value: Float): String = String.format(Locale.US, "%.2f", value)

/** Byte size the way the Flutter client prints it. */
private fun formatSize(value: Long): String {
    val units = listOf("B", "K", "M", "G")
    var size = value.toDouble()
    var index = 0
    while (size > 1024 && index < units.size - 1) {
        index++
        size /= 1024
    }
    return String.format(Locale.US, "%.2f%s", size, units[index])
}
