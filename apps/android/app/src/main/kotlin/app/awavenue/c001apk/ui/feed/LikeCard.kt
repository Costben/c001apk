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
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.text.HtmlText
import app.awavenue.c001apk.ui.text.HtmlTextDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * One entry of「我收到的赞」, ported from `components/cards/like_card.dart`: who liked, what was
 * liked, and a preview of the liked content.
 */
@Composable
fun LikeCard(item: Entity, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val openLink = rememberLinkOpener()
    val likeUser = item.likeUserInfo
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(10.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NetworkImage(
                    likeUser?.string("userAvatar").orEmpty(),
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { navigator.push(Route.User(likeUser?.string("uid").orEmpty())) },
                    contentDescription = likeUser?.string("username").orEmpty(),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    likeUser?.string("username").orEmpty(),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    fromToday(item.likeTime),
                    fontSize = 13.sp,
                    color = COUITheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(10.dp))
            HtmlText("赞了你的${item.infoHtml}", style = HtmlTextDefaults.style())
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(COUITheme.colorScheme.surface)
                    .clickable { openLink(item.url, "") }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.pic.isNotEmpty()) {
                    NetworkImage(item.pic, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
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
