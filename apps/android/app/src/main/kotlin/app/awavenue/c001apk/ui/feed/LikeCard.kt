package app.awavenue.c001apk.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.text.HtmlText
import app.awavenue.c001apk.ui.text.HtmlTextDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * One entry of「我收到的赞」. The API aggregates a post's likers into the post row: `likeUserInfo`
 * is the latest one, `likeTime` when they liked, and `recent_like_list` the other names. Rendering
 * the row as a plain feed card dropped all three, so the list looked like the author's own posts.
 */
@Composable
fun LikeCard(item: Entity, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val openLink = rememberLinkOpener()
    val likeUser = item.likeUserInfo
    val name = likeUser?.string("username").orEmpty()
        .ifEmpty { item.string("likeUsername") }
        .ifEmpty { item.username }
    val avatar = likeUser?.string("userAvatar").orEmpty()
        .ifEmpty { item.string("likeAvatar") }
        .ifEmpty { item.userAvatar }
    val uid = likeUser?.string("uid").orEmpty()
        .ifEmpty { item.string("likeUid") }
    val feedType = item.string("feedTypeName").ifEmpty { item.infoHtml }.ifEmpty { "动态" }
    val others = item.string("recent_like_list")
        .split(',')
        .count { it.isNotBlank() }
        .minus(1)
        .coerceAtLeast(0)
    val action = if (others > 0) "和另外 $others 人赞了你的$feedType" else "赞了你的$feedType"
    val time = item.likeTime?.takeIf { it > 0 } ?: item.dateline
    val previewPic = item.pic.ifEmpty { item.picArr.firstOrNull().orEmpty() }
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(10.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NetworkImage(
                    avatar,
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(enabled = uid.isNotEmpty()) { navigator.push(Route.User(uid)) },
                    contentDescription = name,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    fromToday(time),
                    fontSize = 13.sp,
                    color = COUITheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(10.dp))
            HtmlText(action, style = HtmlTextDefaults.style())
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(rememberSquircleShape(12.dp))
                    .background(COUITheme.colorScheme.surface)
                    .clickable { openLink(item.url, "") }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (previewPic.isNotEmpty()) {
                    NetworkImage(previewPic, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "@${item.username}",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    HtmlText(item.message, style = HtmlTextDefaults.style().copy(fontSize = 13.sp))
                }
            }
        }
    }
}
