package app.awavenue.c001apk.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.image.imageOrigin
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.theme.isAppInDarkTheme
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.Hide
import io.github.suqi8.coui.kmp.theme.COUITheme

private val HeaderHeight = 169.dp
private val CoverHeight = 125.dp
private val AvatarSize = 84.dp

/** The four counters under the name; [onStatClick] receives the index of the counter tapped. */
object UserStat {
    const val Feed = 0
    const val Like = 1
    const val Follow = 2
    const val Fan = 3
}

@Composable
fun UserInfoCard(
    user: Entity,
    onFollowClick: () -> Unit,
    onLocalFollow: () -> Unit,
    onStatClick: (Int) -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val app = LocalApp.current
    val cover = user.string("cover")
    val avatar = user.userAvatar
    val username = user.username
    val uid = user.uid
    val level = user.string("level")
    val isFollow = user.string("isFollow") == "1"
    val isLogin = app.session.isLogin
    val localFollowed = app.localFollow.contains(uid)
    val secretFollow = isLogin && app.localFollow.secretEnabled
    // The Flutter card dims the cover on a light theme and lifts it on a dark one so the white
    // avatar ring keeps its contrast either way.
    val scrim = if (isAppInDarkTheme(app.settings.darkMode)) Color(0x5DFFFFFF) else Color(0x8D000000)

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(HeaderHeight)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(CoverHeight)
                    .imageOrigin(cover, 0.dp)
                    .clickable(enabled = cover.isNotEmpty()) {
                        navigator.push(Route.ImageView(listOf(cover)))
                    },
            ) {
                NetworkImage(cover, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(scrim))
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 85.dp)
                    .size(AvatarSize)
                    .border(4.dp, COUITheme.colorScheme.surface, CircleShape)
                    .padding(4.dp)
                    .imageOrigin(avatar, Dp.Infinity)
                    .clickable(enabled = avatar.isNotEmpty()) {
                        navigator.push(Route.ImageView(listOf(avatar)))
                    },
            ) {
                NetworkImage(avatar, Modifier.fillMaxSize().clip(CircleShape), contentDescription = username)
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 128.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(
                    onClick = {
                        if (!isLogin) return@IconButton
                        navigator.push(Route.Chat(privateChatKey(uid, app.session.uid), uid, username))
                    },
                ) {
                    Icon(COUIIcons.Email, contentDescription = "私信")
                }
                // A tap always works the account follow, or the local list for a guest; with 悄悄关注
                // on, a long press works the local list for a signed-in account.
                Button(
                    onClick = onFollowClick,
                    modifier = Modifier.longPress(enabled = secretFollow, onLongPress = onLocalFollow),
                ) {
                    if (isLogin && localFollowed) {
                        Icon(COUIIcons.Hide, contentDescription = "已悄悄关注", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        when {
                            isLogin -> if (isFollow) "取消关注" else "关注"
                            localFollowed -> "取消本地关注"
                            else -> "本地关注"
                        },
                    )
                }
            }
        }
        Text(
            username,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { context.copyText(username) },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("uid: $uid", fontSize = 14.sp, modifier = Modifier.clickable { context.copyText(uid) })
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(COUITheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.5.dp),
            ) {
                Text(
                    "Lv.$level",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    color = COUITheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatText("${user.string("feed")}动态", UserStat.Feed, onStatClick)
            StatText("${user.string("be_like_num")}赞", UserStat.Like, onStatClick)
            StatText("${user.string("follow")}关注", UserStat.Follow, onStatClick)
            StatText("${user.string("fans")}粉丝", UserStat.Fan, onStatClick)
        }
        Text(
            "${fromToday(user.long("logintime"))}活跃",
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp),
            fontSize = 14.sp,
        )
    }
}

/**
 * A long press that coexists with a component's own click. It watches the gesture before the
 * component does, and once the press has been held long enough it consumes the release so the
 * click does not fire as well.
 */
private fun Modifier.longPress(enabled: Boolean, onLongPress: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        composed {
            val action by rememberUpdatedState(onLongPress)
            pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        true
                    }
                    if (released == null) {
                        action()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                }
            }
        }
    }

@Composable
private fun StatText(value: String, index: Int, onStatClick: (Int) -> Unit) {
    Text(value, fontSize = 14.sp, modifier = Modifier.clickable { onStatClick(index) })
}

/** The private-chat key is the pair of uids ordered numerically, as the Flutter client builds it. */
private fun privateChatKey(uid: String, selfUid: String): String {
    val first = uid.toLongOrNull()
    val second = selfUid.toLongOrNull()
    return if (first != null && second != null) {
        "${minOf(first, second)}_${maxOf(first, second)}"
    } else {
        listOf(uid, selfUid).sorted().joinToString("_")
    }
}
