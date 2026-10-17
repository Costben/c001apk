package app.awavenue.c001apk.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.ui.image.imageOrigin
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.text.HtmlText
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** One conversation of the private-message list, ported from the Flutter `MessageCard`. */
@Composable
fun MessageRow(item: Entity, showUnread: Boolean, onClick: () -> Unit) {
    val navigator = LocalNavigator.current
    val uid = item.string("messageUid")
    val username = item.string("messageUsername")
    val unread = item.int("unreadNum") ?: 0

    Card(
        Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(10.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                item.string("messageUserAvatar"),
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = uid.isNotEmpty()) { navigator.push(Route.User(uid)) },
                contentDescription = username,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(username, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(10.dp))
                    Text(fromToday(item.dateline), fontSize = 13.sp, color = COUITheme.colorScheme.outline)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HtmlText(item.message, modifier = Modifier.weight(1f), maxLines = 1)
                    if (showUnread && unread != 0) {
                        Spacer(Modifier.width(10.dp))
                        Badge(count = unread)
                    }
                }
            }
        }
    }
}

/**
 * One private message: a bubble on the left or the right of the list, with the picture beside it.
 * The geometry follows the Flutter `ChatCard`.
 */
@Composable
fun ChatCard(item: Entity, onLongPress: () -> Unit, onImageClick: (String) -> Unit) {
    val navigator = LocalNavigator.current
    val isLeft = item.string("fromuid") != LocalApp.current.session.uid
    val message = item.message
    val pic = item.string("message_pic")

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Bubbles stop 142dp short of the row width, as the Flutter client's ChatCard does.
        val bubbleMax = maxWidth - 142.dp
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.Top,
        ) {
            if (isLeft) {
                ChatAvatar(item.string("fromUserAvatar"), item.string("messageUid")) {
                    navigator.push(Route.User(item.string("messageUid")))
                }
                Spacer(Modifier.width(10.dp))
            }
            if (message.isNotEmpty()) {
                val shape = RoundedCornerShape(
                    topStart = if (isLeft) 4.dp else 12.dp,
                    topEnd = if (isLeft) 12.dp else 4.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp,
                )
                Box(
                    Modifier
                        .widthIn(max = bubbleMax)
                        .clip(shape)
                        .background(
                            if (isLeft) COUITheme.colorScheme.surfaceContainer else COUITheme.colorScheme.primaryContainer,
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    HtmlText(message)
                }
            }
            if (pic.isNotEmpty()) {
                ChatImage(pic, bubbleMax / 2, onImageClick)
            }
            if (!isLeft) {
                Spacer(Modifier.width(10.dp))
                ChatAvatar(item.string("fromUserAvatar"), item.string("fromuid")) {
                    navigator.push(Route.User(item.string("fromuid")))
                }
            }
        }
    }
}

@Composable
private fun ChatAvatar(url: String, contentDescription: String, onClick: () -> Unit) {
    NetworkImage(
        url,
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = contentDescription.isNotEmpty(), onClick = onClick),
        contentDescription = contentDescription,
    )
}

@Composable
private fun ChatImage(pic: String, width: androidx.compose.ui.unit.Dp, onImageClick: (String) -> Unit) {
    val (imageWidth, imageHeight) = imageSize(pic)
    val shape = RoundedCornerShape(12.dp)
    val size = Modifier
        .width(width)
        .height(width * (imageHeight / imageWidth))
        .imageOrigin(pic, 12.dp)
        .clip(shape)
        .border(1.dp, COUITheme.colorScheme.outline.copy(alpha = 0.5f), shape)
    // A picture whose URL has not been resolved yet only reserves its place; the page fetches the
    // link and recomposes this row with it.
    if (pic.startsWith("http")) {
        Box(size.clickable { onImageClick(pic) }) {
            NetworkImage(pic, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    } else {
        Box(size.background(COUITheme.colorScheme.surfaceContainer))
    }
}
