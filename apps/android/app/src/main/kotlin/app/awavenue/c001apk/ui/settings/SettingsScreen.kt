package app.awavenue.c001apk.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.awavenue.c001apk.BuildConfig
import app.awavenue.c001apk.platform.InstallerAppOption
import app.awavenue.c001apk.platform.ShizukuInstaller
import app.awavenue.c001apk.data.BlacklistType
import app.awavenue.c001apk.data.DarkMode
import app.awavenue.c001apk.data.HapticStrength
import app.awavenue.c001apk.data.HistoryCardMode
import app.awavenue.c001apk.data.ImageBrowseQuality
import app.awavenue.c001apk.data.SeedColors
import app.awavenue.c001apk.data.UpdateResult
import app.awavenue.c001apk.model.ContentFilterPreferences
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.appDialogButtonColors
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
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
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
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Recent
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Theme
import io.github.suqi8.coui.kmp.icon.extended.Tune
import io.github.suqi8.coui.kmp.icon.extended.Update
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
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
import kotlin.math.roundToInt

/** The discrete scale steps 字体大小 snaps to. */
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

    var cacheSize by remember { mutableStateOf<Long?>(null) }
    var askClearCache by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateResult.Available?>(null) }
    var shizukuAvailable by remember { mutableStateOf(ShizukuInstaller.isShizukuAvailable()) }
    var shizukuGranted by remember { mutableStateOf(ShizukuInstaller.hasShizukuPermission()) }
    var availableInstallers by remember { mutableStateOf(ShizukuInstaller.queryAvailableInstallers(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shizukuAvailable = ShizukuInstaller.isShizukuAvailable()
                shizukuGranted = ShizukuInstaller.hasShizukuPermission()
                availableInstallers = ShizukuInstaller.queryAvailableInstallers(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var menu by remember { mutableStateOf(false) }

    // 字体大小 drags against a local draft and only commits on release: the scale is applied by
    // swapping LocalDensity, so committing mid-drag re-measures the whole page on every step and
    // the drag stalls.
    var fontScaleDraft by remember { mutableFloatStateOf(settings.fontScale) }
    LaunchedEffect(settings.fontScale) { fontScaleDraft = settings.fontScale }

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
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
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
                                alignment = PopupPositionProvider.Align.End,
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
                                            role = Role.Button,
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
            val listState = rememberPageListState("settings")
            BindScrollToTop(listState)
            LazyColumn(
                state = listState,
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
                    SettingsGroupCard {
                        ArrowPreference(
                            title = "使用教程",
                            summary = "从官方酷安导入设备标识，减少人机验证",
                            startAction = { PreferenceStartIcon(COUIIcons.Info) },
                            cardListPosition = CardListPosition.Head,
                            onClick = rememberHapticClick { settings.restartOnboarding() },
                        )
                        ArrowPreference(
                            title = "协议参数",
                            summary = "${settings.deviceParams.brand} ${settings.deviceParams.model}",
                            startAction = { PreferenceStartIcon(COUIIcons.Settings) },
                            cardListPosition = CardListPosition.Tail,
                            onClick = rememberHapticClick { navigator.push(Route.Params) },
                        )
                    }
                }
                item {
                    SmallTitle("主题")
                    SettingsGroupCard {
                        SwitchPreference(
                            title = "动态取色",
                            checked = settings.dynamicColor,
                            onCheckedChange = settings::updateDynamicColor,
                            startAction = { PreferenceStartIcon(COUIIcons.Background) },
                            cardListPosition = CardListPosition.Head,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        )
                        OverlayDropdownPreference(
                            entry = seedColorEntry,
                            title = "主题颜色",
                            startAction = { PreferenceStartIcon(COUIIcons.Photos) },
                            cardListPosition = CardListPosition.Middle,
                            enabled = !settings.dynamicColor,
                            maxHeight = 380.dp,
                        )
                        OverlayDropdownPreference(
                            title = "深色模式",
                            items = darkModes.map { it.label },
                            selectedIndex = darkModes.indexOf(settings.darkMode),
                            onSelectedIndexChange = { settings.updateDarkMode(darkModes[it]) },
                            startAction = { PreferenceStartIcon(COUIIcons.Theme) },
                            cardListPosition = CardListPosition.Tail,
                        )
                    }
                }
                item {
                    SmallTitle("显示")
                    SettingsGroupCard {
                        ArrowPreference(
                            title = "用户黑名单",
                            startAction = { PreferenceStartIcon(COUIIcons.Blocklist) },
                            cardListPosition = CardListPosition.Head,
                            onClick = rememberHapticClick { navigator.push(Route.Blacklist(BlacklistType.User)) },
                        )
                        ArrowPreference(
                            title = "话题黑名单",
                            startAction = { PreferenceStartIcon(COUIIcons.Blocklist) },
                            cardListPosition = CardListPosition.Middle,
                            onClick = rememberHapticClick { navigator.push(Route.Blacklist(BlacklistType.Topic)) },
                        )
                        ArrowPreference(
                            title = "内容过滤",
                            summary = contentFilterSummary(app.contentFilter.preferences),
                            startAction = { PreferenceStartIcon(COUIIcons.Tune) },
                            cardListPosition = CardListPosition.Middle,
                            onClick = rememberHapticClick { navigator.push(Route.ContentFilter) },
                        )
                        SliderPreference(
                            title = "字体大小",
                            value = fontScaleDraft,
                            onValueChange = { fontScaleDraft = it },
                            onValueChangeFinished = {
                                val snapped = (fontScaleDraft * 20f).roundToInt() / 20f
                                if (snapped != settings.fontScale) settings.updateFontScale(snapped)
                            },
                            startAction = { PreferenceStartIcon(COUIIcons.Tune) },
                            cardListPosition = CardListPosition.Middle,
                            valueText = "${scaleText(fontScaleDraft)}x",
                            valueRange = fontScaleSteps.first()..fontScaleSteps.last(),
                            steps = fontScaleSteps.size - 2,
                            hapticEffect = if (settings.hapticFeedback) {
                                SliderDefaults.SliderHapticEffect.Step
                            } else {
                                SliderDefaults.SliderHapticEffect.None
                            },
                            showKeyPoints = true,
                        )
                        SwitchPreference(
                            title = "在浏览器中打开",
                            checked = settings.openInBrowser,
                            onCheckedChange = settings::updateOpenInBrowser,
                            startAction = { PreferenceStartIcon(COUIIcons.Link) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "记录浏览历史",
                            checked = settings.recordHistory,
                            onCheckedChange = settings::updateRecordHistory,
                            startAction = { PreferenceStartIcon(COUIIcons.Recent) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "在“我的”显示草稿箱",
                            summary = "关闭后可隐藏“我的”页面中的草稿箱卡片",
                            checked = settings.showMineDrafts,
                            onCheckedChange = settings::updateShowMineDrafts,
                            startAction = { PreferenceStartIcon(COUIIcons.Hide) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "在“我的”显示我的关注",
                            summary = "关闭后可隐藏“我的”页面中的我的关注卡片",
                            checked = settings.showMineFollow,
                            onCheckedChange = settings::updateShowMineFollow,
                            startAction = { PreferenceStartIcon(COUIIcons.Hide) },
                            cardListPosition = CardListPosition.Middle,
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
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "显示表情",
                            checked = settings.showEmoji,
                            onCheckedChange = settings::updateShowEmoji,
                            startAction = { PreferenceStartIcon(AppIcons.EmojiEmotions) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        OverlayDropdownPreference(
                            title = "浏览图片",
                            items = ImageBrowseQuality.entries.map { it.label },
                            selectedIndex = ImageBrowseQuality.entries.indexOf(settings.imageBrowseQuality),
                            onSelectedIndexChange = {
                                settings.updateImageBrowseQuality(ImageBrowseQuality.entries[it])
                            },
                            startAction = { PreferenceStartIcon(COUIIcons.Photos) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "实况图自动播放",
                            summary = "进入或滑动到实况图时自动播放一次，结束后仅支持长按重播",
                            checked = settings.livePhotoAutoPlay,
                            onCheckedChange = settings::updateLivePhotoAutoPlay,
                            startAction = { PreferenceStartIcon(COUIIcons.Photos) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "实况图播放声音",
                            summary = "播放实况图时同步播放附带的视频声音",
                            checked = settings.livePhotoSound,
                            onCheckedChange = settings::updateLivePhotoSound,
                            startAction = { PreferenceStartIcon(AppIcons.VolumeOn) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "检查已安装应用更新",
                            summary = "在应用页右下角显示已安装应用的更新入口，并在有更新时高亮图标",
                            checked = settings.checkUpdate,
                            onCheckedChange = settings::updateCheckUpdate,
                            startAction = { PreferenceStartIcon(COUIIcons.Update) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "应用更新角标红点",
                            summary = "检测到应用更新时在更新球右上角显示强调色圆点；关闭后仅高亮图标",
                            checked = settings.appUpdateBadge,
                            onCheckedChange = settings::updateAppUpdateBadge,
                            startAction = { PreferenceStartIcon(COUIIcons.Update) },
                            cardListPosition = CardListPosition.Tail,
                            enabled = settings.checkUpdate,
                        )
                    }
                }
                item {
                    SmallTitle("首页内容")
                    SettingsGroupCard {
                        SwitchPreference(
                            title = "显示轮播横幅",
                            summary = "首页动态顶部的大图推荐和活动横幅",
                            checked = settings.showHomeCarousel,
                            onCheckedChange = settings::updateShowHomeCarousel,
                            startAction = { PreferenceStartIcon(COUIIcons.Photos) },
                            cardListPosition = CardListPosition.Head,
                        )
                        SwitchPreference(
                            title = "显示频道快捷入口",
                            summary = "值得看、热闻、活动等入口",
                            checked = settings.showHomeChannels,
                            onCheckedChange = settings::updateShowHomeChannels,
                            startAction = { PreferenceStartIcon(COUIIcons.ListView) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "显示推荐话题与数码入口",
                            summary = "机型、话题等横向或网格入口",
                            checked = settings.showHomeTopics,
                            onCheckedChange = settings::updateShowHomeTopics,
                            startAction = { PreferenceStartIcon(COUIIcons.Recent) },
                            cardListPosition = CardListPosition.Tail,
                        )
                    }
                }
                item {
                    SmallTitle("交互")
                    SettingsGroupCard {
                        SwitchPreference(
                            title = "预测性返回手势",
                            summary = "返回时页面跟随手指并露出下一层；关闭后直接返回",
                            checked = settings.predictiveBack,
                            onCheckedChange = settings::updatePredictiveBack,
                            startAction = { PreferenceStartIcon(COUIIcons.Back) },
                            cardListPosition = CardListPosition.Head,
                        )
                        SwitchPreference(
                            title = "动态卡片返回跟手轨迹",
                            summary = "卡片详情预测性返回时跟随手指偏移，并顺着甩出方向回缩",
                            checked = settings.predictiveBackCardFling,
                            onCheckedChange = settings::updatePredictiveBackCardFling,
                            startAction = { PreferenceStartIcon(COUIIcons.Notes) },
                            cardListPosition = CardListPosition.Middle,
                            enabled = settings.predictiveBack,
                        )
                        SwitchPreference(
                            title = "图片返回跟手轨迹",
                            summary = "看图页预测性返回时跟随手指偏移，并顺着甩出方向缩回原图",
                            checked = settings.predictiveBackImageFling,
                            onCheckedChange = settings::updatePredictiveBackImageFling,
                            startAction = { PreferenceStartIcon(COUIIcons.Photos) },
                            cardListPosition = CardListPosition.Middle,
                            enabled = settings.predictiveBack,
                        )
                        SwitchPreference(
                            title = "点击触感",
                            summary = "点击卡片、按钮时轻微振动",
                            checked = settings.hapticFeedback,
                            onCheckedChange = settings::updateHapticFeedback,
                            startAction = { PreferenceStartIcon(COUIIcons.Tune) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "自定义触感",
                            summary = "用系统振动器代替界面自带的点击反馈",
                            checked = settings.customHaptics,
                            onCheckedChange = settings::updateCustomHaptics,
                            startAction = { PreferenceStartIcon(COUIIcons.Settings) },
                            cardListPosition = CardListPosition.Middle,
                            enabled = settings.hapticFeedback,
                        )
                        SwitchPreference(
                            title = "反转触感强度",
                            summary = "把轻和强的振动效果对调",
                            checked = settings.reverseHapticEffects,
                            onCheckedChange = settings::updateReverseHapticEffects,
                            startAction = { PreferenceStartIcon(COUIIcons.Refresh) },
                            cardListPosition = CardListPosition.Middle,
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
                            cardListPosition = CardListPosition.Tail,
                            enabled = settings.hapticFeedback,
                        )
                    }
                }
                item {
                    val label = if (app.session.isLogin) "悄悄关注" else "本地关注"
                    SmallTitle("关注")
                    SettingsGroupCard {
                        if (app.session.isLogin) {
                            SwitchPreference(
                                title = "悄悄关注",
                                summary = "开启后，长按用户主页的关注按钮添加或取消。仅保存在本机，不会同步到账号",
                                checked = app.localFollow.secretEnabled,
                                onCheckedChange = app.localFollow::updateSecretEnabled,
                                startAction = { PreferenceStartIcon(COUIIcons.Hide) },
                                cardListPosition = CardListPosition.Head,
                            )
                        } else {
                            BasicComponent(
                                title = "本地关注",
                                summary = "游客可在用户主页直接关注，并在底栏“关注”查看内容。名单仅保存在本机",
                                startAction = { PreferenceStartIcon(COUIIcons.Contacts) },
                                cardListPosition = CardListPosition.Head,
                            )
                        }
                        ArrowPreference(
                            title = "管理$label",
                            summary = "查看本机关注名单、进入用户主页或取消关注",
                            startAction = { PreferenceStartIcon(COUIIcons.ContactsBook) },
                            cardListPosition = CardListPosition.Tail,
                            onClick = rememberHapticClick { navigator.push(Route.LocalFollowList) },
                        )
                    }
                }
                item {
                    SmallTitle("更新")
                    SettingsGroupCard {
                        SwitchPreference(
                            title = "自动检查 c001apk 更新",
                            summary = "每次启动时检查一次最新版本",
                            checked = settings.autoCheckAppUpdate,
                            onCheckedChange = settings::updateAutoCheckAppUpdate,
                            startAction = { PreferenceStartIcon(COUIIcons.Refresh) },
                            cardListPosition = CardListPosition.Head,
                        )
                        SwitchPreference(
                            title = "接收预发布更新",
                            summary = "包含预发布版本；关闭时只接收正式版",
                            checked = settings.acceptPrereleaseUpdates,
                            onCheckedChange = settings::updateAcceptPrereleaseUpdates,
                            startAction = { PreferenceStartIcon(COUIIcons.Update) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "应用内更新",
                            summary = "开启后在应用内拉取 APK 并发起安装请求；关闭时通过浏览器下载",
                            checked = settings.inAppUpdate,
                            onCheckedChange = settings::updateInAppUpdate,
                            startAction = { PreferenceStartIcon(COUIIcons.Download) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        val installerOptions = remember(availableInstallers, settings.preferredInstallerPackage) {
                            buildList {
                                add(InstallerAppOption(packageName = "", label = "系统默认（自动匹配）"))
                                addAll(availableInstallers)
                                val saved = settings.preferredInstallerPackage
                                if (saved.isNotEmpty() && none { it.packageName == saved }) {
                                    add(InstallerAppOption(packageName = saved, label = "$saved（未安装）"))
                                }
                            }
                        }
                        val selectedInstallerIndex = installerOptions
                            .indexOfFirst { it.packageName == settings.preferredInstallerPackage }
                            .coerceAtLeast(0)
                        OverlayDropdownPreference(
                            title = "APK 安装器",
                            summary = installerOptions[selectedInstallerIndex].displayTitle,
                            items = installerOptions.map { it.displayTitle },
                            selectedIndex = selectedInstallerIndex,
                            onSelectedIndexChange = { index ->
                                settings.updatePreferredInstallerPackage(installerOptions[index].packageName)
                            },
                            startAction = { PreferenceStartIcon(COUIIcons.Settings) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            title = "通过 Shizuku 静默安装",
                            summary = when {
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> "Shizuku 静默安装需要 Android 7.0 及以上系统"
                                !shizukuAvailable -> "未检测到 Shizuku 服务运行；开启前需先启动 Shizuku"
                                !shizukuGranted -> "Shizuku 已运行，开启时将申请授权"
                                settings.shizukuInstall -> "已授权，安装应用更新与下载的 APK 时优先静默安装"
                                else -> "Shizuku 已授权，开启后可免确认安装 APK"
                            },
                            checked = settings.shizukuInstall,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    settings.updateShizukuInstall(false)
                                    return@SwitchPreference
                                }
                                shizukuAvailable = ShizukuInstaller.isShizukuAvailable()
                                if (!shizukuAvailable) {
                                    context.toast("请先安装并启动 Shizuku 服务")
                                    return@SwitchPreference
                                }
                                if (ShizukuInstaller.hasShizukuPermission()) {
                                    shizukuGranted = true
                                    settings.updateShizukuInstall(true)
                                } else {
                                    ShizukuInstaller.requestShizukuPermission { granted ->
                                        shizukuGranted = granted
                                        if (granted) {
                                            settings.updateShizukuInstall(true)
                                            context.toast("已授予 Shizuku 权限")
                                        } else {
                                            settings.updateShizukuInstall(false)
                                            context.toast("未授予 Shizuku 权限")
                                        }
                                    }
                                }
                            },
                            startAction = { PreferenceStartIcon(COUIIcons.Update) },
                            cardListPosition = CardListPosition.Middle,
                        )
                        ArrowPreference(
                            title = if (checkingUpdate) "正在检查更新…" else "检查更新",
                            summary = if (settings.acceptPrereleaseUpdates) {
                                "查看 c001apk 的最新版本（含预发布）"
                            } else {
                                "查看 c001apk 的最新正式版本"
                            },
                            startAction = { PreferenceStartIcon(COUIIcons.Download) },
                            cardListPosition = CardListPosition.Middle,
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
                        ArrowPreference(
                            title = "版本更新说明",
                            summary = "查看最新版本的更新日志与发布说明",
                            startAction = { PreferenceStartIcon(COUIIcons.Notes) },
                            cardListPosition = CardListPosition.Tail,
                            enabled = !checkingUpdate,
                            onClick = rememberHapticClick {
                                scope.launch {
                                    checkingUpdate = true
                                    when (val result = app.updates.check(forceShow = true)) {
                                        is UpdateResult.Available -> update = result
                                        is UpdateResult.Failed -> context.toast(result.message)
                                        else -> context.toast("获取更新说明失败")
                                    }
                                    checkingUpdate = false
                                }
                            },
                        )
                    }
                }
                item {
                    SmallTitle("其他")
                    SettingsGroupCard {
                        ArrowPreference(
                            title = "账号管理",
                            summary = "已保存 ${app.accounts.accounts.size} 个账号 · 切换、添加或移除",
                            startAction = { PreferenceStartIcon(COUIIcons.ContactsCircle) },
                            cardListPosition = CardListPosition.Head,
                            onClick = rememberHapticClick { navigator.push(Route.Accounts) },
                        )
                        ArrowPreference(
                            title = "关于",
                            summary = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                            startAction = { PreferenceStartIcon(COUIIcons.Info) },
                            cardListPosition = CardListPosition.Middle,
                            onClick = rememberHapticClick { navigator.push(Route.About) },
                        )
                        ArrowPreference(
                            title = "清理缓存",
                            summary = cacheSize?.let(::formatSize),
                            startAction = { PreferenceStartIcon(COUIIcons.Clear) },
                            cardListPosition = CardListPosition.Tail,
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

/** Grouped preference card using COUI 17 group corner radius and weight. */
@Composable
internal fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
        content = content,
    )
}

/** The start icon every preference row on the settings pages shares. */
@Composable
internal fun PreferenceStartIcon(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, tint = COUITheme.colorScheme.onBackground)
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
        buttonColors = appDialogButtonColors(),
        show = true,
        title = "清理缓存",
        summary = "当前缓存大小：${formatSize(cacheSize)}",
        confirmText = "确定",
        cancelText = "取消",
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
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

/** The 内容过滤 row: how many rules are saved, and whether they are on. */
private fun contentFilterSummary(preferences: ContentFilterPreferences): String = when {
    preferences.rules.isEmpty() -> "按关键词、用户或话题隐藏列表内容"
    !preferences.enabled -> "${preferences.rules.size} 条规则（未启用）"
    else -> "${preferences.rules.size} 条规则"
}

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
