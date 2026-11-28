package app.awavenue.c001apk.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import app.awavenue.c001apk.ui.common.rememberCapsuleShape
import app.awavenue.c001apk.ui.image.imageOrigin
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SpotLightDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.Hide
import io.github.suqi8.coui.kmp.theme.COUITheme

private val AvatarSize = 72.dp
private val ActionButtonHeight = 36.dp
private val BottomBridgeHeight = 16.dp

/** The four counters under the name; [onStatClick] receives the index of the counter tapped. */
object UserStat {
    const val Feed = 0
    const val Like = 1
    const val Follow = 2
    const val Fan = 3
}

/** Renders the user's cover photo (or dark slate fallback) with a legibility gradient scrim. */
@Composable
fun UserCoverBackdrop(
    cover: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    Box(modifier.clipToBounds()) {
        Box(
            Modifier.matchParentSize().graphicsLayer {
                if (scale != 1f) {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = scale
                    scaleY = scale
                }
            },
        ) {
            if (cover.isNotEmpty()) {
                NetworkImage(
                    cover,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    placeholderColor = Color(0xFF1F2733),
                )
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF0F172A)),
                            ),
                        ),
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x52000000),
                            0.4f to Color(0x7A000000),
                            1f to Color(0xCC0B0F17),
                        ),
                    ),
            )
        }
    }
}

@Composable
fun UserInfoCard(
    user: Entity,
    onFollowClick: () -> Unit,
    onLocalFollow: () -> Unit,
    onStatClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pullProgress: Float = 0f,
    hideInlineCover: Boolean = false,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val app = LocalApp.current
    val cover = user.string("cover")
    val avatar = user.userAvatar
    val username = user.username
    val uid = user.uid
    val level = user.string("level")
    val bio = user.string("bio").ifEmpty { user.string("verify_title") }
    val isFollow = user.string("isFollow") == "1"
    val isLogin = app.session.isLogin
    val localFollowed = app.localFollow.contains(uid)
    val secretFollow = isLogin && app.localFollow.secretEnabled
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val secondaryText = Color.White.copy(alpha = 0.85f)
    val mutedText = Color.White.copy(alpha = 0.72f)
    val badgeBg = Color.White.copy(alpha = 0.22f)
    val avatarScale = 1f + 0.08f * pullProgress.coerceIn(0f, 1.5f)

    // Full-bleed cover spanning from the status bar down to the rounded surface transition, with a
    // vertical dark gradient so avatar, metadata and counters stay legible over bright photos.
    Box(
        modifier
            .fillMaxWidth()
            .then(
                if (cover.isNotEmpty()) {
                    Modifier
                        .imageOrigin(cover, 0.dp)
                        .clickable { navigator.push(Route.ImageView(listOf(cover))) }
                } else {
                    Modifier
                },
            ),
    ) {
        if (!hideInlineCover) {
            UserCoverBackdrop(cover = cover, modifier = Modifier.matchParentSize())
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = statusTop + 60.dp, bottom = BottomBridgeHeight + 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(AvatarSize)
                        .graphicsLayer {
                            scaleX = avatarScale
                            scaleY = avatarScale
                        }
                        .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                        .padding(2.dp)
                        .imageOrigin(avatar, Dp.Infinity)
                        .clickable(enabled = avatar.isNotEmpty()) {
                            navigator.push(Route.ImageView(listOf(avatar)))
                        },
                ) {
                    NetworkImage(avatar, Modifier.fillMaxSize().clip(CircleShape), contentDescription = username)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // A tap always works the account follow, or the local list for a guest; with 悄悄关注
                    // on, a long press works the local list for a signed-in account.
                    Button(
                        onClick = onFollowClick,
                        modifier = Modifier.longPress(enabled = secretFollow, onLongPress = onLocalFollow),
                        minHeight = ActionButtonHeight,
                        colors = if (isFollow) {
                            ButtonDefaults.buttonColors(
                                color = badgeBg,
                                contentColor = Color.White,
                            )
                        } else {
                            ButtonDefaults.buttonColorsPrimary()
                        },
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    ) {
                        if (isLogin && localFollowed) {
                            Icon(COUIIcons.Hide, contentDescription = "已悄悄关注", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            when {
                                isLogin -> if (isFollow) "已关注" else "关注"
                                localFollowed -> "取消本地关注"
                                else -> "本地关注"
                            },
                            fontSize = 14.sp,
                        )
                    }
                    Button(
                        onClick = {
                            if (!isLogin) return@Button
                            navigator.push(Route.Chat(privateChatKey(uid, app.session.uid), uid, username))
                        },
                        enabled = isLogin,
                        minWidth = ActionButtonHeight,
                        minHeight = ActionButtonHeight,
                        cornerRadius = ActionButtonHeight / 2,
                        colors = ButtonDefaults.buttonColors(
                            color = badgeBg,
                            contentColor = Color.White,
                            disabledColor = badgeBg.copy(alpha = 0.55f),
                            disabledContentColor = Color.White.copy(alpha = 0.45f),
                        ),
                        insideMargin = PaddingValues(0.dp),
                        spotLight = SpotLightDefaults.capsuleParams1(isDark = true),
                    ) {
                        Icon(
                            COUIIcons.Email,
                            contentDescription = "私信",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    username,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { context.copyText(username) },
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (level.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(rememberCapsuleShape())
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "Lv.$level",
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
            if (bio.isNotEmpty()) {
                Text(
                    bio,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 6.dp, end = 20.dp),
                    color = secondaryText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatText("${user.string("be_like_num")} 获赞", UserStat.Like, Color.White, onStatClick)
                StatText("${user.string("follow")} 关注", UserStat.Follow, Color.White, onStatClick)
                StatText("${user.string("fans")} 粉丝", UserStat.Fan, Color.White, onStatClick)
                StatText("${user.string("feed")} 动态", UserStat.Feed, Color.White, onStatClick)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${fromToday(user.long("logintime"))}活跃",
                    color = mutedText,
                    fontSize = 12.sp,
                )
                Text(
                    "uid: $uid",
                    color = mutedText,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { context.copyText(uid) },
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BottomBridgeHeight)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(COUITheme.colorScheme.background),
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
private fun StatText(value: String, index: Int, color: Color, onStatClick: (Int) -> Unit) {
    Text(
        value,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable { onStatClick(index) },
    )
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
