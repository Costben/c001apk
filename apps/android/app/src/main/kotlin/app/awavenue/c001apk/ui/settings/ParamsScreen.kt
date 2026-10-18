package app.awavenue.c001apk.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.nav.LocalNavigator
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.EditTextPreference
import io.github.suqi8.coui.kmp.utils.overScrollVertical

@Composable
fun ParamsScreen() {
    val settings = LocalApp.current.settings
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = COUIScrollBehavior()
    val backdrop = rememberGlassBackdrop()
    val params = settings.deviceParams

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = "协议参数",
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
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
                SmallTitle("客户端")
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    EditTextPreference(
                        title = "客户端版本名称",
                        value = params.versionName,
                        onValueChange = { value -> settings.updateDeviceParams { it.copy(versionName = value) } },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                    )
                    EditTextPreference(
                        title = "API 版本",
                        value = params.apiVersion,
                        onValueChange = { value -> settings.updateDeviceParams { it.copy(apiVersion = value) } },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                    )
                    EditTextPreference(
                        title = "客户端版本号",
                        value = params.versionCode,
                        onValueChange = { value -> settings.updateDeviceParams { it.copy(versionCode = value) } },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                    )
                }
            }
            item {
                SmallTitle("设备")
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    EditTextPreference(
                        title = "设备标识（SZLM ID）",
                        value = settings.szlmId,
                        onValueChange = settings::updateSzlmId,
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                        summary = "留空时随机生成",
                    )
                    EditTextPreference(
                        title = "制造商",
                        value = params.manufacturer,
                        onValueChange = { value ->
                            settings.updateDeviceParams(updateXAppDevice = true) { it.copy(manufacturer = value) }
                        },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                    )
                    EditTextPreference(
                        title = "品牌",
                        value = params.brand,
                        onValueChange = { value ->
                            settings.updateDeviceParams(updateXAppDevice = true) { it.copy(brand = value) }
                        },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                    )
                    EditTextPreference(
                        title = "型号",
                        value = params.model,
                        onValueChange = { value ->
                            settings.updateDeviceParams(updateXAppDevice = true) { it.copy(model = value) }
                        },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                    )
                    EditTextPreference(
                        title = "系统构建号",
                        value = params.buildNumber,
                        onValueChange = { value ->
                            settings.updateDeviceParams(updateXAppDevice = true) { it.copy(buildNumber = value) }
                        },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                    )
                    EditTextPreference(
                        title = "SDK 版本",
                        value = params.sdkInt,
                        onValueChange = { value -> settings.updateDeviceParams { it.copy(sdkInt = value) } },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                    )
                    EditTextPreference(
                        title = "Android 版本",
                        value = params.androidVersion,
                        onValueChange = { value -> settings.updateDeviceParams { it.copy(androidVersion = value) } },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                    )
                }
            }
            item {
                SmallTitle("请求头")
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    ArrowPreference(
                        title = "用户代理（User-Agent）",
                        summary = params.userAgent,
                        startAction = { PreferenceStartIcon(COUIIcons.Copy) },
                        onClick = { context.copyText(params.userAgent) },
                    )
                    EditTextPreference(
                        title = "设备编码（X-App-Device）",
                        value = params.xAppDevice,
                        onValueChange = { value -> settings.updateDeviceParams { it.copy(xAppDevice = value) } },
                        confirmButtonText = "确定",
                        cancelButtonText = "取消",
                        bottomAction = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    text = "复制",
                                    onClick = { context.copyText(settings.deviceParams.xAppDevice) },
                                )
                            }
                        },
                    )
                }
            }
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    ArrowPreference(
                        title = "重新生成参数",
                        startAction = { PreferenceStartIcon(COUIIcons.Refresh) },
                        onClick = settings::regenerateDeviceParams,
                    )
                }
            }
        }
    }
}
