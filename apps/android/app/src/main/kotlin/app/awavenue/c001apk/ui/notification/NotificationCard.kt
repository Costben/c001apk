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
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.ActionSheetItem
import app.awavenue.c001apk.ui.feed.ImageGrid
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Navigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.text.HtmlText
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Blocklist
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.ExpandMore
import io.github.suqi8.coui.kmp.icon.extended.Report
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val linkPattern = Regex("<a[^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

/**
 * What a notification points at: the item's own url for a contact follow, otherwise the first link
 * inside the note HTML.
 */
fun notificationLink(item: Entity): String? =
    if (item.string("type") == "contacts_follow") {
        item.url.ifEmpty { null }
    } else {
        linkPattern.find(item.string("note"))?.groupValues?.getOrNull(1)
    }

/** Coolapk links open in the in-app browser, which is where unported detail pages land. */
private fun openNotificationLink(navigator: Navigator, url: String) {
    val target = when {
        url.startsWith("http") -> url
        url.startsWith("/") -> Protocol.URL_COOLAPK + url
        else -> return
    }
    navigator.push(Route.Web(target))
}

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

    NotificationCard(
        item = item,
        onOpen = { notificationLink(item)?.let { openNotificationLink(navigator, it) } },
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
    val note = item.string("note")
    val fromUid = item.string("fromuid").ifEmpty { item.uid }
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
                    item.string("fromUserAvatar"),
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = fromUid.isNotEmpty(),
                            onClick = rememberHapticClick { navigator.push(Route.User(fromUid)) },
                        ),
                    contentDescription = item.string("fromusername"),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    HtmlText(item.string("fromusername"))
                    HtmlText(
                        note,
                        onViewFan = if (isFollow) {
                            { navigator.push(Route.User(fromUid)) }
                        } else {
                            null
                        },
                    )
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
            IconButton(
                onClick = rememberHapticClick { menuOpen = true },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    COUIIcons.ExpandMore,
                    contentDescription = "更多",
                    tint = COUITheme.colorScheme.outline,
                )
            }
        }
    }

    OverlayBottomSheet(show = menuOpen, onDismissRequest = { menuOpen = false }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            ActionSheetItem(COUIIcons.Delete, "删除", onDelete)
            ActionSheetItem(COUIIcons.Blocklist, "屏蔽", onBlock)
            ActionSheetItem(COUIIcons.Report, "举报", onClick = {
                navigator.push(Route.Web(Protocol.reportLink(item.string("fromuid"), isUser = true)))
            })
        }
    }
}
