package app.awavenue.c001apk.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class DarkMode(val label: String) { System("跟随系统"), Off("始终关闭"), On("始终开启") }

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

    var showEmoji by mutableStateOf(prefs.getBoolean(KEY_SHOW_EMOJI, true))
        private set

    /** Installed-app updates on the 应用 tab, not this client's own. */
    var checkUpdate by mutableStateOf(prefs.getBoolean(KEY_CHECK_UPDATE, true))
        private set

    /** This client's own release check, once per cold start. */
    var autoCheckAppUpdate by mutableStateOf(prefs.getBoolean(KEY_AUTO_CHECK_APP_UPDATE, true))
        private set

    var skippedAppUpdateVersion by mutableStateOf(prefs.getString(KEY_SKIPPED_APP_UPDATE, "").orEmpty())
        private set

    var szlmId by mutableStateOf(prefs.getString(KEY_SZLM_ID, "").orEmpty())
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

    fun updateFollowSelection(value: Int) = put { putInt(KEY_FOLLOW_SELECTION, value) }.also { followSelection = value }

    fun updateOpenInBrowser(value: Boolean) = put { putBoolean(KEY_OPEN_IN_BROWSER, value) }.also { openInBrowser = value }

    fun updateRecordHistory(value: Boolean) = put { putBoolean(KEY_RECORD_HISTORY, value) }.also { recordHistory = value }

    fun updateShowEmoji(value: Boolean) = put { putBoolean(KEY_SHOW_EMOJI, value) }.also { showEmoji = value }

    fun updateCheckUpdate(value: Boolean) = put { putBoolean(KEY_CHECK_UPDATE, value) }.also { checkUpdate = value }

    fun updateAutoCheckAppUpdate(value: Boolean) =
        put { putBoolean(KEY_AUTO_CHECK_APP_UPDATE, value) }.also { autoCheckAppUpdate = value }

    fun skipAppUpdate(version: String) =
        put { putString(KEY_SKIPPED_APP_UPDATE, version) }.also { skippedAppUpdateVersion = version }

    fun updateSzlmId(value: String) {
        szlmId = value
        prefs.edit { putString(KEY_SZLM_ID, value) }
        deviceParams = deviceParams.reissueXAppDevice(value).also(::writeDeviceParams)
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
        /** The tab order the index under [KEY_HOME_TAB] was saved against, 关注 still first. */
        val LEGACY_HOME_TABS = listOf("Follow", "App", "Feed", "Hot", "Topic", "Product", "CoolPic")
        const val KEY_FOLLOW_TYPE = "followType"
        const val KEY_FOLLOW_SELECTION = "followFeedSelectionV1"
        const val KEY_OPEN_IN_BROWSER = "openInBrowser"
        const val KEY_RECORD_HISTORY = "recordHistory"
        const val KEY_SHOW_EMOJI = "showEmoji"
        const val KEY_CHECK_UPDATE = "checkUpdate"
        const val KEY_AUTO_CHECK_APP_UPDATE = "autoCheckAppUpdate"
        const val KEY_SKIPPED_APP_UPDATE = "skippedAppUpdateVersion"
        const val KEY_SZLM_ID = "szlmId"
        const val KEY_INSTALL_TIME = "installTime"
        val DEVICE_KEYS = listOf(
            "versionName", "apiVersion", "versionCode", "manufacturer", "brand", "model",
            "buildNumber", "sdkInt", "androidVersion", "xAppDevice",
        )
    }
}
