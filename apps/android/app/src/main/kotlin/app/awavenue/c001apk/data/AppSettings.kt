package app.awavenue.c001apk.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.awavenue.c001apk.ui.home.HomeTab

enum class DarkMode(val label: String) { System("跟随系统"), Off("始终关闭"), On("始终开启") }

/** How many rows the 浏览历史 card on 我的 shows: a short preview, or the whole list. */
enum class HistoryCardMode(val label: String) { Compact("缩略展开"), Full("完整展开") }

/**
 * Persistent settings and the API device profile. Values are Compose state so screens
 * recompose when a setting changes; writes go straight to SharedPreferences.
 *
 * The keys mirror the Flutter client's `SettingsBoxKey`, minus the ones whose UI is commented
 * out upstream (image quality, image dimension, square corners).
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var darkMode by mutableStateOf(DarkMode.entries.getOrElse(prefs.getInt(KEY_DARK_MODE, 0)) { DarkMode.System })
        private set

    /** Dynamic colour (Material You). When off, [seedColor] picks the palette. */
    var dynamicColor by mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
        private set

    var seedColor by mutableIntStateOf(prefs.getInt(KEY_SEED_COLOR, 0))
        private set

    var fontScale by mutableFloatStateOf(prefs.getFloat(KEY_FONT_SCALE, 1f))
        private set

    /** The home tab by name, so adding or dropping a tab never lands on the wrong one. */
    var homeTab by mutableStateOf(
        prefs.getString(KEY_HOME_TAB_NAME, null)
            ?: LEGACY_HOME_TABS.getOrNull(prefs.getInt(KEY_HOME_TAB, 2))?.takeIf { it != "Follow" }
            ?: "Feed",
    )
        private set

    /**
     * The home tabs the user hides from the tab row, stored by name as a comma-separated list so
     * enum order changes never remap the set. Unknown names are dropped on read.
     */
    var hiddenHomeTabs by mutableStateOf(
        prefs.getString(KEY_HIDDEN_HOME_TABS, null)
            ?.split(',')
            ?.filter { it in HOME_TAB_NAMES }
            ?.toSet()
            .orEmpty(),
    )
        private set

    /**
     * What the 关注 page shows: 1 user / 2 topic / 3 product / 4 app / 0 all / 5 the local list. An
     * explicit category from the old 关注内容 setting carries over; the old mixed default becomes 用户.
     */
    var followSelection by mutableIntStateOf(
        prefs.getInt(KEY_FOLLOW_SELECTION, prefs.getInt(KEY_FOLLOW_TYPE, 0).takeIf { it in 1..4 } ?: 1),
    )
        private set

    var openInBrowser by mutableStateOf(prefs.getBoolean(KEY_OPEN_IN_BROWSER, false))
        private set

    var recordHistory by mutableStateOf(prefs.getBoolean(KEY_RECORD_HISTORY, true))
        private set

    var historyCardMode by mutableStateOf(
        HistoryCardMode.entries.getOrElse(prefs.getInt(KEY_HISTORY_CARD_MODE, 0)) { HistoryCardMode.Compact },
    )
        private set

    var showEmoji by mutableStateOf(prefs.getBoolean(KEY_SHOW_EMOJI, true))
        private set

    /**
     * The header cards of the 动态 stream. Hiding one only drops it while drawing; the fetched page
     * keeps it, so turning it back on needs no refresh.
     */
    var showHomeCarousel by mutableStateOf(prefs.getBoolean(KEY_SHOW_HOME_CAROUSEL, true))
        private set

    var showHomeChannels by mutableStateOf(prefs.getBoolean(KEY_SHOW_HOME_CHANNELS, true))
        private set

    var showHomeTopics by mutableStateOf(prefs.getBoolean(KEY_SHOW_HOME_TOPICS, true))
        private set

    var hapticFeedback by mutableStateOf(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
        private set

    var customHaptics by mutableStateOf(prefs.getBoolean(KEY_CUSTOM_HAPTICS, false))
        private set

    var reverseHapticEffects by mutableStateOf(prefs.getBoolean(KEY_REVERSE_HAPTIC_EFFECTS, false))
        private set

    var hapticStrength by mutableStateOf(HapticStrength.fromName(prefs.getString(KEY_HAPTIC_STRENGTH, null)))
        private set

    /** Installed-app updates on the 应用 tab, not this client's own. */
    var checkUpdate by mutableStateOf(prefs.getBoolean(KEY_CHECK_UPDATE, true))
        private set

    /** This client's own release check, once per cold start. */
    var autoCheckAppUpdate by mutableStateOf(prefs.getBoolean(KEY_AUTO_CHECK_APP_UPDATE, true))
        private set

    /** Whether update checks may offer pre-release builds. Stable only by default. */
    var acceptPrereleaseUpdates by mutableStateOf(prefs.getBoolean(KEY_ACCEPT_PRERELEASE_UPDATES, false))
        private set

    /** Whether to download APK and trigger installation in-app instead of opening browser. */
    var inAppUpdate by mutableStateOf(prefs.getBoolean(KEY_IN_APP_UPDATE, true))
        private set

    var skippedAppUpdateVersion by mutableStateOf(prefs.getString(KEY_SKIPPED_APP_UPDATE, "").orEmpty())
        private set

    var szlmId by mutableStateOf(prefs.getString(KEY_SZLM_ID, "").orEmpty())
        private set

    /** Whether the first-launch guide has been seen and dismissed. */
    var onboardingDone by mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
        private set

    val installTime: String
    var deviceParams: DeviceParams
        private set

    init {
        val storedInstallTime = prefs.getString(KEY_INSTALL_TIME, null)
        installTime = storedInstallTime ?: System.currentTimeMillis().toString()
        val stored = readDeviceParams()
        deviceParams = stored?.takeIf { it.versionCode == Protocol.VERSION_CODE }
            ?: DeviceParams.generate(szlmId).also(::writeDeviceParams)
        if (storedInstallTime == null) prefs.edit { putString(KEY_INSTALL_TIME, installTime) }
    }

    fun updateDarkMode(value: DarkMode) = put { putInt(KEY_DARK_MODE, value.ordinal) }.also { darkMode = value }

    fun updateDynamicColor(value: Boolean) = put { putBoolean(KEY_DYNAMIC_COLOR, value) }.also { dynamicColor = value }

    fun updateSeedColor(value: Int) = put { putInt(KEY_SEED_COLOR, value) }.also { seedColor = value }

    fun updateFontScale(value: Float) = put { putFloat(KEY_FONT_SCALE, value) }.also { fontScale = value }

    fun updateHomeTab(value: String) = put { putString(KEY_HOME_TAB_NAME, value) }.also { homeTab = value }

    /** Stores [value] by name, dropping anything that is not a current [HomeTab]. */
    fun updateHiddenHomeTabs(value: Set<String>) {
        val normalized = value.filterTo(mutableSetOf()) { it in HOME_TAB_NAMES }
        put { putString(KEY_HIDDEN_HOME_TABS, normalized.joinToString(",")) }
        hiddenHomeTabs = normalized
    }

    fun updateFollowSelection(value: Int) = put { putInt(KEY_FOLLOW_SELECTION, value) }.also { followSelection = value }

    fun updateOpenInBrowser(value: Boolean) = put { putBoolean(KEY_OPEN_IN_BROWSER, value) }.also { openInBrowser = value }

    fun updateRecordHistory(value: Boolean) = put { putBoolean(KEY_RECORD_HISTORY, value) }.also { recordHistory = value }

    fun updateHistoryCardMode(value: HistoryCardMode) =
        put { putInt(KEY_HISTORY_CARD_MODE, value.ordinal) }.also { historyCardMode = value }

    fun updateShowEmoji(value: Boolean) = put { putBoolean(KEY_SHOW_EMOJI, value) }.also { showEmoji = value }

    fun updateShowHomeCarousel(value: Boolean) =
        put { putBoolean(KEY_SHOW_HOME_CAROUSEL, value) }.also { showHomeCarousel = value }

    fun updateShowHomeChannels(value: Boolean) =
        put { putBoolean(KEY_SHOW_HOME_CHANNELS, value) }.also { showHomeChannels = value }

    fun updateShowHomeTopics(value: Boolean) =
        put { putBoolean(KEY_SHOW_HOME_TOPICS, value) }.also { showHomeTopics = value }

    fun updateHapticFeedback(value: Boolean) =
        put { putBoolean(KEY_HAPTIC_FEEDBACK, value) }.also { hapticFeedback = value }

    fun updateCustomHaptics(value: Boolean) =
        put { putBoolean(KEY_CUSTOM_HAPTICS, value) }.also { customHaptics = value }

    fun updateReverseHapticEffects(value: Boolean) =
        put { putBoolean(KEY_REVERSE_HAPTIC_EFFECTS, value) }.also { reverseHapticEffects = value }

    fun updateHapticStrength(value: HapticStrength) =
        put { putString(KEY_HAPTIC_STRENGTH, value.name) }.also { hapticStrength = value }

    fun updateCheckUpdate(value: Boolean) = put { putBoolean(KEY_CHECK_UPDATE, value) }.also { checkUpdate = value }

    fun updateAutoCheckAppUpdate(value: Boolean) =
        put { putBoolean(KEY_AUTO_CHECK_APP_UPDATE, value) }.also { autoCheckAppUpdate = value }

    fun updateAcceptPrereleaseUpdates(value: Boolean) =
        put { putBoolean(KEY_ACCEPT_PRERELEASE_UPDATES, value) }.also { acceptPrereleaseUpdates = value }

    fun updateInAppUpdate(value: Boolean) =
        put { putBoolean(KEY_IN_APP_UPDATE, value) }.also { inAppUpdate = value }

    fun skipAppUpdate(version: String) =
        put { putString(KEY_SKIPPED_APP_UPDATE, version) }.also { skippedAppUpdateVersion = version }

    fun updateSzlmId(value: String) {
        szlmId = value
        prefs.edit { putString(KEY_SZLM_ID, value) }
        deviceParams = deviceParams.reissueXAppDevice(value).also(::writeDeviceParams)
    }

    fun completeOnboarding() {
        if (onboardingDone) return
        put { putBoolean(KEY_ONBOARDING_DONE, true) }
        onboardingDone = true
    }

    /** Shows the first-launch guide again from 设置, without touching the saved device token. */
    fun restartOnboarding() {
        put { putBoolean(KEY_ONBOARDING_DONE, false) }
        onboardingDone = false
    }

    /** Rebuilds the spoofed device profile, and with it the user agent and `X-App-Device`. */
    fun regenerateDeviceParams() {
        deviceParams = DeviceParams.generate(szlmId).also(::writeDeviceParams)
    }

    /** [updateXAppDevice] re-derives the token that encodes manufacturer, brand, model and build. */
    fun updateDeviceParams(
        updateXAppDevice: Boolean = false,
        transform: (DeviceParams) -> DeviceParams,
    ) {
        val next = transform(deviceParams)
        deviceParams = (if (updateXAppDevice) next.reissueXAppDevice(szlmId) else next).also(::writeDeviceParams)
    }

    private fun put(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    private fun readDeviceParams(): DeviceParams? {
        val values = DEVICE_KEYS.map { prefs.getString(it, null) ?: return null }
        return DeviceParams(
            versionName = values[0],
            apiVersion = values[1],
            versionCode = values[2],
            manufacturer = values[3],
            brand = values[4],
            model = values[5],
            buildNumber = values[6],
            sdkInt = values[7],
            androidVersion = values[8],
            xAppDevice = values[9],
        )
    }

    private fun writeDeviceParams(params: DeviceParams) {
        val values = with(params) {
            listOf(versionName, apiVersion, versionCode, manufacturer, brand, model, buildNumber, sdkInt, androidVersion, xAppDevice)
        }
        prefs.edit { DEVICE_KEYS.zip(values).forEach { (key, value) -> putString(key, value) } }
    }

    private inline fun SharedPreferences.edit(block: SharedPreferences.Editor.() -> Unit) {
        edit().apply(block).apply()
    }

    private companion object {
        const val KEY_DARK_MODE = "darkMode"
        const val KEY_DYNAMIC_COLOR = "dynamicColor"
        const val KEY_SEED_COLOR = "staticColor"
        const val KEY_FONT_SCALE = "fontScale"
        const val KEY_HOME_TAB = "homeTab"
        const val KEY_HOME_TAB_NAME = "homeTabName"
        const val KEY_HIDDEN_HOME_TABS = "hiddenHomeTabs"
        /** The tab names [KEY_HIDDEN_HOME_TABS] may contain; anything else is dropped on read. */
        val HOME_TAB_NAMES = HomeTab.entries.mapTo(mutableSetOf()) { it.name }
        /** The tab order the index under [KEY_HOME_TAB] was saved against, 关注 still first. */
        val LEGACY_HOME_TABS = listOf("Follow", "App", "Feed", "Hot", "Topic", "Product", "CoolPic")
        const val KEY_FOLLOW_TYPE = "followType"
        const val KEY_FOLLOW_SELECTION = "followFeedSelectionV1"
        const val KEY_OPEN_IN_BROWSER = "openInBrowser"
        const val KEY_RECORD_HISTORY = "recordHistory"
        const val KEY_HISTORY_CARD_MODE = "historyCardMode"
        const val KEY_SHOW_EMOJI = "showEmoji"
        const val KEY_SHOW_HOME_CAROUSEL = "showHomeCarousel"
        const val KEY_SHOW_HOME_CHANNELS = "showHomeChannels"
        const val KEY_SHOW_HOME_TOPICS = "showHomeTopics"
        const val KEY_HAPTIC_FEEDBACK = "hapticFeedback"
        const val KEY_CUSTOM_HAPTICS = "customHaptics"
        const val KEY_REVERSE_HAPTIC_EFFECTS = "reverseHapticEffects"
        const val KEY_HAPTIC_STRENGTH = "hapticStrength"
        const val KEY_CHECK_UPDATE = "checkUpdate"
        const val KEY_AUTO_CHECK_APP_UPDATE = "autoCheckAppUpdate"
        const val KEY_ACCEPT_PRERELEASE_UPDATES = "acceptPrereleaseUpdates"
        const val KEY_IN_APP_UPDATE = "inAppUpdate"
        const val KEY_SKIPPED_APP_UPDATE = "skippedAppUpdateVersion"
        const val KEY_SZLM_ID = "szlmId"
        const val KEY_ONBOARDING_DONE = "onboardingDone"
        const val KEY_INSTALL_TIME = "installTime"
        val DEVICE_KEYS = listOf(
            "versionName", "apiVersion", "versionCode", "manufacturer", "brand", "model",
            "buildNumber", "sdkInt", "androidVersion", "xAppDevice",
        )
    }
}
