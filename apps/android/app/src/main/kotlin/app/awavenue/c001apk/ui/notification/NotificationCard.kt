package app.awavenue.c001apk.ui.notification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.MoreMenu
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.ImageGrid
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.text.HtmlText
import app.awavenue.c001apk.ui.text.HtmlTextDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A notification with the side effects the list owns: deleting it drops it from the server, blocking
 * its sender hides the user. [onChanged] lets the list reload.
 */
@Composable
fun NotificationItem(item: Entity, onChanged: () -> Unit) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The same dispatcher the links inside the note use: a Coolapk path stays in the app, only an
    // external site falls back to the browser. Building a web URL for every tap sent a like or a
    // reply to the web page even though the post screen exists here.
    val openLink = rememberLinkOpener()

    NotificationCard(
        item = item,
        onOpen = { notificationDisplay(item).link?.let { openLink(it, "") } },
        onDelete = {
            scope.launch {
                try {
                    val result = app.repository.deleteNotification(item.id)
                    val message = result.message.ifEmpty { result.count }
                    if (message.isNotEmpty()) context.toast(message)
                    onChanged()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    context.toast(ApiFailure.from(e).message)
                }
            }
        },
        onBlock = {
            app.blacklist.blockUser(item.string("fromuid"))
            onChanged()
        },
    )
}

/** One notification, ported from the Flutter `NotificationCard`. */
@Composable
fun NotificationCard(item: Entity, onOpen: () -> Unit, onDelete: () -> Unit, onBlock: () -> Unit) {
    val navigator = LocalNavigator.current
    val display = remember(item) { notificationDisplay(item) }
    val note = item.string("note")
    val fromUid = display.actorUid.ifEmpty { item.uid }
    val isFollow = item.string("type") == "contacts_follow"
    val pictures = item.picArr
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        onClick = rememberHapticClick(onClick = onOpen),
        onLongPress = { navigator.push(Route.Copy(note)) },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                NetworkImage(
                    display.actorAvatar,
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = fromUid.isNotEmpty(),
                            onClick = rememberHapticClick { navigator.push(Route.User(fromUid)) },
                        ),
                    contentDescription = display.actorName,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (display.actorName.isNotEmpty()) HtmlText(display.actorName)
                    if (display.action.isNotEmpty()) {
                        HtmlText(
                            display.action,
                            onViewFan = if (isFollow) {
                                { navigator.push(Route.User(fromUid)) }
                            } else {
                                null
                            },
                        )
                    }
                    // The reply itself. At-comment and like rows keep it in `message`, not in the
                    // note, so rendering the note alone dropped the very content being notified.
                    if (display.content.isNotEmpty()) {
                        HtmlText(
                            display.content,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (display.target.isNotEmpty()) {
                        HtmlText(
                            display.target,
                            modifier = Modifier.padding(top = 2.dp),
                            style = HtmlTextDefaults.style().copy(fontSize = 13.sp),
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 3,
                        )
                    }
                    if (pictures.isNotEmpty()) {
                        ImageGrid(
                            pictures = pictures,
                            onClick = { index -> navigator.push(Route.ImageView(pictures, index)) },
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        fromToday(item.dateline),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = rememberHapticClick { menuOpen = true }) {
                    Icon(
                        COUIIcons.More,
                        contentDescription = "更多",
                        tint = COUITheme.colorScheme.outline,
                    )
                }
                MoreMenu(
                    show = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    entries = listOf(
                        "删除" to onDelete,
                        "屏蔽" to onBlock,
                        "举报" to {
                            navigator.push(Route.Web(Protocol.reportLink(item.string("fromuid"), isUser = true)))
                        },
                    ),
                )
            }
        }
    }
}
