package app.awavenue.c001apk.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.data.SavedAccount
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Hide
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/**
 * 账号管理. Tapping an account switches to it; the switch rebuilds the main screen and closes the
 * pages opened under the previous account, this one included.
 */
@Composable
fun AccountsScreen() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberPageScrollBehavior("accounts")
    val backdrop = rememberGlassBackdrop()
    val session = app.session

    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<SavedAccount?>(null) }

    fun run(action: () -> Unit) {
        error = null
        runCatching(action).onFailure { error = "账号操作未完成，请重试" }
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = "账号管理",
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
            state = rememberPageListState("accounts"),
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
                Text(
                    "点击账号即可切换。登录仅保存在本机；游客模式会保留已保存的账号。",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            error?.let { message ->
                item { Text(message, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = COUITheme.colorScheme.error) }
            }
            item { SmallTitle("已保存的账号") }
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    if (app.accounts.accounts.isEmpty()) {
                        BasicComponent(
                            title = "暂无已保存账号",
                            summary = "添加账号后可在这里快速切换",
                            startAction = { AccountAvatar("") },
                        )
                    }
                    app.accounts.accounts.forEach { account ->
                        val active = session.isLogin && session.uid == account.uid
                        BasicComponent(
                            title = account.displayName.ifEmpty { account.uid },
                            summary = when {
                                account.needsLogin -> "登录已失效 · 点击重新登录"
                                active -> "当前使用"
                                else -> "UID ${account.uid}"
                            },
                            startAction = { AccountAvatar(account.avatar) },
                            endActions = {
                                IconButton(onClick = { removing = account }) {
                                    Icon(COUIIcons.Delete, contentDescription = "移除${account.displayName}")
                                }
                            },
                            onClick = {
                                if (account.needsLogin) {
                                    navigator.push(Route.Login)
                                } else {
                                    run { app.accounts.switchTo(account.uid) }
                                }
                            },
                        )
                    }
                }
            }
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 6.dp)) {
                    BasicComponent(
                        title = "添加账号",
                        summary = "登录新的酷安账号，已有登录会保留",
                        startAction = { RowIcon { Icon(COUIIcons.Add, contentDescription = null) } },
                        onClick = { navigator.push(Route.Login) },
                    )
                    BasicComponent(
                        title = "使用游客模式",
                        summary = if (session.isLogin) "暂时不使用账号浏览，保留所有本机数据" else "当前为游客模式",
                        startAction = { RowIcon { Icon(COUIIcons.Hide, contentDescription = null) } },
                        onClick = { run { app.accounts.useGuest() } },
                        enabled = session.isLogin,
                    )
                }
            }
        }
    }

    removing?.let { account ->
        RemoveDialog(
            account = account,
            isCurrent = session.uid == account.uid,
            onConfirm = {
                removing = null
                run { app.accounts.remove(account.uid) }
            },
            onCancel = { removing = null },
        )
    }
}

@Composable
private fun AccountAvatar(avatar: String) {
    RowIcon {
        if (avatar.isEmpty()) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(COUITheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(COUIIcons.Contacts, contentDescription = null, tint = COUITheme.colorScheme.onSecondaryContainer)
            }
        } else {
            NetworkImage(avatar, Modifier.size(40.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun RowIcon(content: @Composable () -> Unit) {
    Box(Modifier.padding(end = 12.dp)) { content() }
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun RemoveDialog(account: SavedAccount, isCurrent: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    OverlayAlertDialog(
        show = true,
        title = "移除本机登录？",
        summary = "将移除“${account.displayName}”在本机保存的登录。再次使用需要重新登录。" +
            if (isCurrent) "\n当前账号将切换为游客。" else "",
        confirmText = "移除",
        cancelText = "取消",
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}
