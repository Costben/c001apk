package app.awavenue.c001apk.ui.follow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
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
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.RemoveContact
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/** 管理本地关注 / 管理悄悄关注: the device-only list, newest addition first. */
@Composable
fun LocalFollowListScreen() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scrollBehavior = rememberPageScrollBehavior("follow-list")
    val backdrop = rememberGlassBackdrop()
    val label = localFollowLabel()
    val users = app.localFollow.users

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = "管理$label",
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
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            LazyColumn(
                state = rememberPageListState("follow-list"),
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
                        "名单仅保存在本机，登录、退出或关闭悄悄关注开关后仍会保留。不会自动同步到账号。",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (users.isEmpty()) {
                    item {
                        Text(
                            "还没有关注的用户",
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    item {
                        Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                            users.forEach { user ->
                                BasicComponent(
                                    title = user.name.ifEmpty { "用户 ${user.uid}" },
                                    summary = "UID：${user.uid}",
                                    startAction = {
                                        Box(Modifier.padding(end = 12.dp)) {
                                            NetworkImage(user.avatar, Modifier.size(40.dp).clip(CircleShape))
                                        }
                                    },
                                    endActions = {
                                        IconButton(
                                            onClick = {
                                                runCatching { app.localFollow.unfollow(user.uid) }
                                                    .onSuccess { context.toast("已取消$label，仅修改本机名单") }
                                                    .onFailure { context.toast("本地关注保存失败，请重试") }
                                            },
                                        ) {
                                            Icon(COUIIcons.RemoveContact, contentDescription = "取消$label")
                                        }
                                    },
                                    onClick = { navigator.push(Route.User(user.uid)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
