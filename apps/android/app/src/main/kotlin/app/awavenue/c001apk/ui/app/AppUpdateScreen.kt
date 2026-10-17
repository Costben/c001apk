package app.awavenue.c001apk.ui.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.data.TokenUtils
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.platform.InstalledApp
import app.awavenue.c001apk.platform.installedApps
import app.awavenue.c001apk.platform.rememberApkDownload
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun AppUpdateScreen() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val backdrop = rememberGlassBackdrop()
    val download = rememberApkDownload()

    var installed by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var updates by remember { mutableStateOf<List<Entity>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(attempts) {
        refreshing = updates != null
        try {
            val deviceApps = withContext(Dispatchers.IO) { installedApps(context) }
            installed = deviceApps
            val request = JsonObject(
                deviceApps.associate { it.packageName to JsonPrimitive("0,${it.versionCode},0") },
            )
            val response = app.repository.appsUpdate(TokenUtils.base64NoPad(Json.encodeToString(request)))
            val serverMessage = (response["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (serverMessage.isNotEmpty()) {
                message = serverMessage
                updates = null
                total = null
            } else {
                val data = (response["data"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonObject)?.let(::Entity) }
                message = null
                updates = data
                total = data.size.takeIf { it > 0 }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = ApiFailure.from(e).message
            updates = null
            total = null
        }
        refreshing = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "更新${total?.let { "：$it" } ?: ""}",
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().glassSource(backdrop)) {
            val error = message
            val list = updates
            val versions = remember(installed) { installed.associateBy { it.packageName } }
            when {
                error != null -> CenterBox(innerPadding) {
                    EmptyState(title = error, actionText = "重试", onActionClick = { attempts++ })
                }
                list == null -> CenterBox(innerPadding) { InfiniteProgressIndicator() }
                list.isEmpty() -> CenterBox(innerPadding) {
                    EmptyState(title = "暂无内容", actionText = "刷新", onActionClick = { attempts++ })
                }
                else -> PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = { attempts++ },
                    pullToRefreshState = rememberPullToRefreshState(),
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().overScrollVertical(),
                        contentPadding = PaddingValues(
                            start = 10.dp,
                            top = innerPadding.calculateTopPadding() + 10.dp,
                            end = 10.dp,
                            bottom = innerPadding.calculateBottomPadding() + 10.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            list,
                            key = { it.string("packageName").ifEmpty { it.id } },
                        ) { item ->
                            AppUpdateCard(
                                app = item,
                                installed = versions[item.string("packageName")],
                                download = download,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateCard(
    app: Entity,
    installed: InstalledApp?,
    download: (url: String, fileName: String) -> Unit,
) {
    val repository = LocalApp.current.repository
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }

    val packageName = app.string("packageName")
    val versionName = app.string("apkversionname")
    val versionCode = app.string("apkversioncode")
    val bitType = when (app.int("pkgBitType")) {
        1 -> "32位"
        2, 3 -> "64位"
        else -> ""
    }

    Card(
        Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(10.dp),
        onClick = { navigator.push(Route.Apk(packageName)) },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            NetworkImage(
                app.logo,
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                contentDescription = app.title,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).animateContentSize()) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                app.title,
                                style = COUITheme.textStyles.body1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                bitType,
                                style = COUITheme.textStyles.body2,
                                color = COUITheme.colorScheme.outline,
                            )
                        }
                        val current = installed?.let { "${it.versionName}(${it.versionCode})" } ?: "未知"
                        Text(
                            "$current > $versionName($versionCode)",
                            style = COUITheme.textStyles.body2,
                            color = COUITheme.colorScheme.outline,
                        )
                    }
                    TextButton(
                        text = "下载",
                        onClick = {
                            scope.launch {
                                val target = url ?: repository.appDownloadUrl(
                                    packageName = packageName,
                                    id = app.id.toIntOrNull() ?: 0,
                                    versionCode = versionCode.toIntOrNull() ?: 0,
                                )
                                if (target.isNullOrEmpty()) {
                                    context.toast("无法获取下载链接，请稍后重试")
                                    return@launch
                                }
                                url = target
                                download(target, "${app.title}-$versionName-$versionCode.apk")
                            }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fromToday(app.long("lastupdate")),
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        app.string("apksize"),
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.outline,
                    )
                }
                Text(
                    app.string("changelog").ifEmpty { "暂无更新说明" },
                    modifier = Modifier.clickable { expanded = !expanded },
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.outline,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CenterBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { content() }
}
