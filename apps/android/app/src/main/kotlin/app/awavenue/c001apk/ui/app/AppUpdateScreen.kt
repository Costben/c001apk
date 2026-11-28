package app.awavenue.c001apk.ui.app

import android.content.Context
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.data.TokenUtils
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.platform.ApkDownloadManager
import app.awavenue.c001apk.platform.InstalledApp
import app.awavenue.c001apk.platform.ObservePackageInstallEvents
import app.awavenue.c001apk.platform.installedApps
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppPullToRefresh
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
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
    val scope = rememberCoroutineScope()
    val backdrop = rememberGlassBackdrop()

    var installed by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var updates by remember { mutableStateOf<List<Entity>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf<Int?>(null) }

    // Automatically re-check installed versions when any package finishes installing or when
    // returning from the system installer, immediately hiding apps that are now up to date.
    ObservePackageInstallEvents(context) { changedPkg ->
        scope.launch {
            val deviceApps = withContext(Dispatchers.IO) { installedApps(context) }
            installed = deviceApps
            val byPkg = deviceApps.associateBy { it.packageName }
            if (changedPkg != null) {
                byPkg[changedPkg]?.let {
                    ApkDownloadManager.onPackageInstalled(context, changedPkg, it.versionCode)
                }
            } else {
                deviceApps.forEach {
                    ApkDownloadManager.onPackageInstalled(context, it.packageName, it.versionCode)
                }
            }
            val current = updates ?: return@launch
            val remaining = pruneUpdatedApps(current, byPkg)
            if (remaining.size != current.size) {
                updates = remaining
                total = remaining.size.takeIf { it > 0 }
                InstalledAppUpdateState.update(remaining.size)
            }
        }
    }

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
                val data = pruneUpdatedApps(
                    updates = (response["data"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(::Entity) },
                    installedByPackage = deviceApps.associateBy { it.packageName },
                )
                message = null
                updates = data
                total = data.size.takeIf { it > 0 }
                InstalledAppUpdateState.update(data.size)
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
        edgeToEdge = true,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "更新${total?.let { "：$it" } ?: ""}",
                    gradientGlass = true,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
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
                    EmptyState(title = "全部应用已是最新版本", actionText = "刷新", onActionClick = { attempts++ })
                }
                else -> AppPullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = { attempts++ },
                    contentPadding = innerPadding,
                ) {
                    val listState = rememberLazyListState()
                    BindScrollToTop(listState)
                    LazyColumn(
                        state = listState,
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
                            key = { it.updatePackageName().ifEmpty { it.id } },
                        ) { item ->
                            AppUpdateCard(
                                app = item,
                                installed = versions[item.updatePackageName()],
                                modifier = Modifier.animateItem(),
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
    modifier: Modifier = Modifier,
) {
    val container = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val packageName = app.updatePackageName()
    val versionName = app.string("apkversionname")
    val versionCode = app.string("apkversioncode")
    val versionCodeLong = app.long("apkversioncode") ?: 0L
    val bitType = app.updateBitTypeLabel()
    val downloadState = ApkDownloadManager.stateOf(packageName, versionCodeLong)

    Card(
        modifier.fillMaxWidth(),
        insideMargin = PaddingValues(10.dp),
        onClick = { navigator.push(Route.Apk(packageName)) },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            NetworkImage(
                app.logo,
                Modifier.size(40.dp).clip(rememberSquircleShape(8.dp)),
                contentDescription = app.title,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                app.title,
                                modifier = Modifier.weight(1f, fill = false),
                                style = COUITheme.textStyles.body1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (bitType.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    bitType,
                                    style = COUITheme.textStyles.body2,
                                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                )
                            }
                        }
                        val current = installed?.let { "${it.versionName}(${it.versionCode})" } ?: "未知"
                        Text(
                            "$current > $versionName($versionCode)",
                            style = COUITheme.textStyles.body2,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ApkDownloadPill(
                        state = downloadState,
                        idleText = "更新",
                        onClick = {
                            ApkDownloadManager.startOrToggle(
                                context = context,
                                repository = container.repository,
                                userAgent = container.settings.deviceParams.userAgent,
                                packageName = packageName,
                                appId = app.id.toIntOrNull() ?: 0,
                                versionCode = versionCodeLong,
                            )
                        },
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fromToday(app.long("lastupdate")),
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        app.string("apksize"),
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    app.string("changelog").ifEmpty { "暂无更新说明" },
                    modifier = Modifier.clickable { expanded = !expanded },
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Shared availability state for installed-app updates on Coolapk, so the bottom-right floating
 * update ball on the 应用 tab can highlight its icon and optional badge dot immediately.
 */
internal object InstalledAppUpdateState {
    private const val CHECK_COOLDOWN_MILLIS = 5 * 60 * 1000L

    var availableCount by mutableIntStateOf(0)
        private set

    private var lastCheckedAt = 0L
    private var checking = false

    fun update(count: Int) {
        availableCount = count.coerceAtLeast(0)
        lastCheckedAt = System.currentTimeMillis()
    }

    fun clear() {
        availableCount = 0
        lastCheckedAt = 0L
    }

    suspend fun refreshIfNeeded(
        context: Context,
        repository: CoolapkRepository,
        force: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        if (checking) return
        if (!force && lastCheckedAt > 0L && now - lastCheckedAt < CHECK_COOLDOWN_MILLIS) return
        checking = true
        try {
            val deviceApps = withContext(Dispatchers.IO) { installedApps(context) }
            if (deviceApps.isEmpty()) {
                update(0)
                return
            }
            val request = JsonObject(
                deviceApps.associate { it.packageName to JsonPrimitive("0,${it.versionCode},0") },
            )
            val response = repository.appsUpdate(TokenUtils.base64NoPad(Json.encodeToString(request)))
            val serverMessage = (response["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (serverMessage.isEmpty()) {
                val count = (response["data"] as? JsonArray).orEmpty().count { it is JsonObject }
                update(count)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Silent background check: leave the previous state intact on transient network failure.
        } finally {
            checking = false
        }
    }
}

internal fun Entity.updatePackageName(): String =
    string("packageName").ifEmpty { string("apkname") }

internal fun Entity.updateBitTypeLabel(): String = when (int("pkg_bit_type") ?: int("pkgBitType")) {
    1 -> "32位"
    2, 3 -> "64位"
    else -> ""
}

/**
 * Drops apps that have already been updated to (or past) the offered Coolapk version, or are no
 * longer installed on the device.
 */
internal fun pruneUpdatedApps(
    updates: List<Entity>,
    installedByPackage: Map<String, InstalledApp>,
): List<Entity> = updates.filter { item ->
    val pkg = item.updatePackageName()
    if (pkg.isEmpty()) return@filter true
    val local = installedByPackage[pkg] ?: return@filter false
    val remoteVersionCode = item.long("apkversioncode") ?: return@filter true
    remoteVersionCode > local.versionCode
}

@Composable
private fun CenterBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { content() }
}
