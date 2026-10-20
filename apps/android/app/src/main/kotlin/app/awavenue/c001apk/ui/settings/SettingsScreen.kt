package app.awavenue.c001apk.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.BuildConfig
import app.awavenue.c001apk.data.BlacklistType
import app.awavenue.c001apk.data.DarkMode
import app.awavenue.c001apk.data.HapticStrength
import app.awavenue.c001apk.data.HistoryCardMode
import app.awavenue.c001apk.data.SeedColors
import app.awavenue.c001apk.data.UpdateResult
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import coil3.SingletonImageLoader
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
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
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Clear
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.ContactsBook
import io.github.suqi8.coui.kmp.icon.extended.ContactsCircle
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Hide
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.Link
import io.github.suqi8.coui.kmp.icon.extended.ListView
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Recent
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Theme
import io.github.suqi8.coui.kmp.icon.extended.Tune
import io.github.suqi8.coui.kmp.icon.extended.Update
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** The scale steps the Flutter slider dialog snaps to. */
private val fontScaleSteps = listOf(0.8f, 0.85f, 0.9f, 0.95f, 1f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f)

/** The 深色模式 options in the order the Flutter dropdown lists them. */
private val darkModes = listOf(DarkMode.Off, DarkMode.On, DarkMode.System)

/** 默认 keeps COUI's own blue instead of the first seed, so its dot shows that blue. */
private val defaultAccent = Color(0xFF0080FF)

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val settings = app.settings
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberPageScrollBehavior("settings")
    val scope = rememberCoroutineScope()

    val backdrop = rememberGlassBackdrop()

    var showFontScale by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf<Long?>(null) }
    var askClearCache by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateResult.Available?>(null) }
    var menu by remember { mutableStateOf(false) }

    // Each 主题颜色 option carries a dot in the seed it applies, so the list reads as swatches.
    val seedColorEntry = remember(settings.seedColor) {
        DropdownEntry(
            items = SeedColors.labels.mapIndexed { index, label ->
                DropdownItem(
                    text = label,
                    selected = index == settings.seedColor,
                    onClick = { settings.updateSeedColor(index) },
                    icon = { iconModifier ->
                        val color = if (index == 0) defaultAccent else SeedColors.colorAt(index)
                        SeedColorSwatch(color, iconModifier)
                    },
                )
            },
        )
    }

    // Upstream reads the cache size when the page opens, so the row shows it before any tap.
    LaunchedEffect(Unit) {
        cacheSize = runCatching { cacheBytes(context) }.getOrNull()
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = "设置",
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = rememberHapticClick { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        Box {
                            TopBarIconButton(onClick = rememberHapticClick { menu = true }) {
                                Icon(COUIIcons.More, contentDescription = "菜单")
                            }
                            OverlayListPopup(
                                show = menu,
                                alignment = PopupPositionProvider.Align.BottomEnd,
                                onDismissRequest = { menu = false },
                            ) {
                                ListPopupColumn {
                                    val entries = listOf(
                                        "关于" to { navigator.push(Route.About) },
                                    )
                                    entries.forEachIndexed { index, entry ->
                                        DropdownImpl(
                                            item = DropdownItem(text = entry.first),
                                            optionSize = entries.size,
                                            isSelected = false,
                                            index = index,
                                            groupKey = "settings-menu",
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
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            LazyColumn(
                state = rememberPageListState("settings"),
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
                    SmallTitle("c001apk")
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        ArrowPreference(
                            title = "协议参数",
                            summary = "${settings.deviceParams.brand} ${settings.deviceParams.model}",
                            startAction = { PreferenceStartIcon(COUIIcons.Settings) },
                            onClick = rememberHapticClick { navigator.push(Route.Params) },
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
                        OverlayDropdownPreference(
                            entry = seedColorEntry,
                            title = "主题颜色",
                            startAction = { PreferenceStartIcon(COUIIcons.Photos) },
                            enabled = !settings.dynamicColor,
                            maxHeight = 380.dp,
                        )
                        OverlayDropdownPreference(
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
                            onClick = rememberHapticClick { navigator.push(Route.Blacklist(BlacklistType.User)) },
                        )
                        ArrowPreference(
                            title = "话题黑名单",
                            startAction = { PreferenceStartIcon(COUIIcons.Blocklist) },
                            onClick = rememberHapticClick { navigator.push(Route.Blacklist(BlacklistType.Topic)) },
                        )
                        ArrowPreference(
                            title = "字体大小",
                            summary = "${scaleText(settings.fontScale)}x",
                            startAction = { PreferenceStartIcon(COUIIcons.Tune) },
                            onClick = rememberHapticClick { showFontScale = true },
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
                        OverlayDropdownPreference(
                            title = "浏览历史卡片",
                            summary = "缩略展开只显示最近 5 条",
                            items = HistoryCardMode.entries.map { it.label },
                            selectedIndex = HistoryCardMode.entries.indexOf(settings.historyCardMode),
                            onSelectedIndexChange = {
                                settings.updateHistoryCardMode(HistoryCardMode.entries[it])
                            },
                            startAction = { PreferenceStartIcon(COUIIcons.Recent) },
                        )
                        SwitchPreference(
                            title = "显示表情",
                            checked = settings.showEmoji,
                            onCheckedChange = settings::updateShowEmoji,
                            startAction = { PreferenceStartIcon(AppIcons.EmojiEmotions) },
                        )
                        SwitchPreference(
                            title = "检查已安装应用更新",
                            summary = "在应用列表中显示已安装应用的更新入口",
                            checked = settings.checkUpdate,
                            onCheckedChange = settings::updateCheckUpdate,
                            startAction = { PreferenceStartIcon(COUIIcons.Update) },
                        )
                    }
                }
                item {
                    SmallTitle("交互")
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        SwitchPreference(
                            title = "点击触感",
                            summary = "点击卡片、按钮时轻微振动",
                            checked = settings.hapticFeedback,
                            onCheckedChange = settings::updateHapticFeedback,
                            startAction = { PreferenceStartIcon(COUIIcons.Tune) },
                        )
                        SwitchPreference(
                            title = "自定义触感",
                            summary = "用系统振动器代替界面自带的点击反馈",
                            checked = settings.customHaptics,
                            onCheckedChange = settings::updateCustomHaptics,
                            startAction = { PreferenceStartIcon(COUIIcons.Settings) },
                            enabled = settings.hapticFeedback,
                        )
                        SwitchPreference(
                            title = "反转触感强度",
                            summary = "把轻和强的振动效果对调",
                            checked = settings.reverseHapticEffects,
                            onCheckedChange = settings::updateReverseHapticEffects,
                            startAction = { PreferenceStartIcon(COUIIcons.Refresh) },
                            enabled = settings.hapticFeedback && settings.customHaptics,
                        )
                        OverlayDropdownPreference(
                            title = "触感强度",
                            items = HapticStrength.entries.map { it.label },
                            selectedIndex = HapticStrength.entries.indexOf(settings.hapticStrength),
                            onSelectedIndexChange = {
                                settings.updateHapticStrength(HapticStrength.entries[it])
                            },
                            startAction = { PreferenceStartIcon(COUIIcons.ListView) },
                            enabled = settings.hapticFeedback,
                        )
                    }
                }
                item {
                    val label = if (app.session.isLogin) "悄悄关注" else "本地关注"
                    SmallTitle("关注")
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        if (app.session.isLogin) {
                            SwitchPreference(
                                title = "悄悄关注",
                                summary = "开启后，长按用户主页的关注按钮添加或取消。仅保存在本机，不会同步到账号",
                                checked = app.localFollow.secretEnabled,
                                onCheckedChange = app.localFollow::updateSecretEnabled,
                                startAction = { PreferenceStartIcon(COUIIcons.Hide) },
                            )
                        } else {
                            BasicComponent(
                                title = "本地关注",
                                summary = "游客可在用户主页直接关注，并在底栏“关注”查看内容。名单仅保存在本机",
                                startAction = { PreferenceStartIcon(COUIIcons.Contacts) },
                            )
                        }
                        ArrowPreference(
                            title = "管理$label",
                            summary = "查看本机关注名单、进入用户主页或取消关注",
                            startAction = { PreferenceStartIcon(COUIIcons.ContactsBook) },
                            onClick = rememberHapticClick { navigator.push(Route.LocalFollowList) },
                        )
                    }
                }
                item {
                    SmallTitle("更新")
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        SwitchPreference(
                            title = "自动检查 c001apk 更新",
                            summary = "每次启动时检查一次最新正式版本",
                            checked = settings.autoCheckAppUpdate,
                            onCheckedChange = settings::updateAutoCheckAppUpdate,
                            startAction = { PreferenceStartIcon(COUIIcons.Refresh) },
                        )
                        ArrowPreference(
                            title = if (checkingUpdate) "正在检查更新…" else "检查更新",
                            summary = "查看 c001apk 的最新正式版本",
                            startAction = { PreferenceStartIcon(COUIIcons.Download) },
                            enabled = !checkingUpdate,
                            onClick = rememberHapticClick {
                                scope.launch {
                                    checkingUpdate = true
                                    when (val result = app.updates.check()) {
                                        is UpdateResult.Available -> update = result
                                        UpdateResult.UpToDate -> context.toast("已是最新版本")
                                        is UpdateResult.Failed -> context.toast(result.message)
                                        UpdateResult.Busy -> context.toast("正在检查更新，请稍候")
                                        UpdateResult.Suppressed -> Unit
                                    }
                                    checkingUpdate = false
                                }
                            },
                        )
                    }
                }
                item {
                    SmallTitle("其他")
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        ArrowPreference(
                            title = "账号管理",
                            summary = "已保存 ${app.accounts.accounts.size} 个账号 · 切换、添加或移除",
                            startAction = { PreferenceStartIcon(COUIIcons.ContactsCircle) },
                            onClick = rememberHapticClick { navigator.push(Route.Accounts) },
                        )
                        ArrowPreference(
                            title = "关于",
                            summary = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                            startAction = { PreferenceStartIcon(COUIIcons.Info) },
                            onClick = rememberHapticClick { navigator.push(Route.About) },
                        )
                        ArrowPreference(
                            title = "清理缓存",
                            summary = cacheSize?.let(::formatSize),
                            startAction = { PreferenceStartIcon(COUIIcons.Clear) },
                            onClick = rememberHapticClick {
                                scope.launch {
                                    cacheSize = runCatching { cacheBytes(context) }.getOrNull()
                                    askClearCache = true
                                }
                            },
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

    update?.let { AppUpdateDialog(it, onDismiss = { update = null }) }

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

}

/** The start icon every preference row on the settings pages shares. */
@Composable
internal fun PreferenceStartIcon(icon: ImageVector) {
    Box(Modifier.padding(end = 8.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = COUITheme.colorScheme.onBackground)
    }
}

/** A filled dot in [color], used as the leading slot of a 主题颜色 option. */
@Composable
private fun SeedColorSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color),
        )
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
