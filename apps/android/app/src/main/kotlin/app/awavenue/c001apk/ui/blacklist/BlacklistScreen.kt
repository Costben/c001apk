package app.awavenue.c001apk.ui.blacklist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import app.awavenue.c001apk.data.BlacklistType
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.appDialogButtonColors
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import app.awavenue.c001apk.ui.nav.rememberPageScrollState
import io.github.suqi8.coui.kmp.basic.Chip
import io.github.suqi8.coui.kmp.basic.FlowLayout
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.FileDownloads
import io.github.suqi8.coui.kmp.icon.extended.UploadCloud
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BlacklistScreen(type: BlacklistType) {
    val app = LocalApp.current
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberPageScrollBehavior("blacklist")
    val backdrop = rememberGlassBackdrop()

    var text by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val entries = if (type == BlacklistType.User) app.blacklist.userBlacklist else app.blacklist.topicBlacklist

    fun submit() {
        val value = text
        if (value.isEmpty()) return
        if (value in entries) {
            context.toast("已存在")
            return
        }
        if (type == BlacklistType.User) app.blacklist.blockUser(value) else app.blacklist.blockTopic(value)
        text = ""
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val payload = buildJsonObject {
            put("userBlackList", buildJsonArray { app.blacklist.userBlacklist.forEach { add(JsonPrimitive(it)) } })
            put("topicBlackList", buildJsonArray { app.blacklist.topicBlacklist.forEach { add(JsonPrimitive(it)) } })
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toString().toByteArray()) }
        }.onSuccess { context.toast("导出成功") }.onFailure { context.toast("导出失败") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val values = readBlacklist(Json.parseToJsonElement(raw), type)
            val current = if (type == BlacklistType.User) app.blacklist.userBlacklist else app.blacklist.topicBlacklist
            app.blacklist.replace(type, (values + current).distinct())
        }.onSuccess { context.toast("导入成功") }.onFailure { context.toast("导入失败") }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = "",
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (entries.isNotEmpty()) {
                            TopBarIconButton(onClick = { confirmClear = true }) {
                                Icon(COUIIcons.Delete, contentDescription = "清除全部")
                            }
                        }
                        TopBarIconButton(
                            onClick = {
                                if (entries.isEmpty()) return@TopBarIconButton
                                val stamp = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.CHINA).format(Date())
                                exportLauncher.launch("${type.name.lowercase()}-blacklist_$stamp.json")
                            },
                        ) {
                            Icon(COUIIcons.UploadCloud, contentDescription = "导出")
                        }
                        TopBarIconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                            Icon(COUIIcons.FileDownloads, contentDescription = "导入")
                        }
                    },
                    bottomContent = {
                        TextField(
                            value = text,
                            onValueChange = { input ->
                                text = if (type == BlacklistType.User) input.filter { it.isDigit() } else input
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).focusRequester(focusRequester),
                            label = if (type == BlacklistType.User) "用户 UID" else "话题名称",
                            singleLine = true,
                            showClearButton = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (type == BlacklistType.User) KeyboardType.Number else KeyboardType.Text,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
                    .glassSource(backdrop),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无内容", color = COUITheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .glassSource(backdrop)
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberPageScrollState("blacklist")),
            ) {
                FlowLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 10.dp,
                            top = innerPadding.calculateTopPadding() + 10.dp,
                            end = 10.dp,
                            bottom = innerPadding.calculateBottomPadding() + 10.dp,
                        ),
                    itemSpacing = 8.dp,
                    lineSpacing = 8.dp,
                ) {
                    entries.forEach { entry ->
                        Chip(
                            selected = false,
                            onClick = {
                                when (type) {
                                    BlacklistType.User -> navigator.push(Route.User(entry))
                                    BlacklistType.Topic -> navigator.push(Route.Topic(entry))
                                }
                            },
                            label = entry,
                            minWidth = 0.dp,
                            onDismiss = {
                                when (type) {
                                    BlacklistType.User -> app.blacklist.unblockUser(entry)
                                    BlacklistType.Topic -> app.blacklist.unblockTopic(entry)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ClearDialog(
            type = type,
            onConfirm = {
                confirmClear = false
                app.blacklist.clear(type)
            },
            onCancel = { confirmClear = false },
        )
    }
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun ClearDialog(type: BlacklistType, onConfirm: () -> Unit, onCancel: () -> Unit) {
    OverlayAlertDialog(
        buttonColors = appDialogButtonColors(),
        show = true,
        title = "确定清除全部${if (type == BlacklistType.User) "用户" else "话题"}黑名单？",
        confirmText = "确定",
        cancelText = "取消",
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

/** Reads a saved list: the object form the client writes, or a bare array from the Flutter client. */
private fun readBlacklist(element: JsonElement, type: BlacklistType): List<String> = when (element) {
    is JsonObject -> {
        val key = if (type == BlacklistType.User) "userBlackList" else "topicBlackList"
        (element[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.filter { it.isNotEmpty() }
    }
    is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content }.filter { it.isNotEmpty() }
    else -> emptyList()
}
