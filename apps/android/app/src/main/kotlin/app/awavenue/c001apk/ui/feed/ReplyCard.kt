package app.awavenue.c001apk.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.text.HtmlText
import app.awavenue.c001apk.ui.text.HtmlTextDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Blocklist
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.ExpandMore
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.icon.extended.Report
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme

/** One row of a sheet menu, shared by the feed menu, the reply panel and the web page menu. */
@Composable
fun ActionSheetItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
) {
    val tint = if (color == Color.Unspecified) COUITheme.colorScheme.onSurface else color
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = tint,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = COUITheme.textStyles.subtitle, color = tint)
    }
}

/**
 * One reply. The geometry follows the Flutter `FeedReplyCard`: top-level replies sit on a
 * rounded container block, nested ones are flat rows inside their parent, and the reply-to-reply
 * page additionally marks the origin reply.
 */
@Composable
fun ReplyCard(
    reply: Entity,
    onOpenFeed: () -> Unit,
    modifier: Modifier = Modifier,
    isReply2Reply: Boolean = false,
    isTopReply: Boolean = false,
    uid: String? = null,
    onBlock: ((uid: String, id: String?) -> Unit)? = null,
    onReply: ((id: String, username: String, fid: String?) -> Unit)? = null,
    onDelete: ((id: String, fid: String?) -> Unit)? = null,
    onLike: ((id: String, liked: Boolean) -> Unit)? = null,
    onShowReplies: ((reply: Entity) -> Unit)? = null,
) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val isFeedReply = reply.string("fetchType") == "feed_reply"
    val isContainer = !isFeedReply || (isReply2Reply && !isTopReply)
    var panelOpen by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(if (isContainer) RoundedCornerShape(12.dp) else RectangleShape)
            .background(if (isContainer) COUITheme.colorScheme.surfaceContainer else Color.Transparent),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isFeedReply && onReply != null) {
                            onReply(reply.id, reply.username, null)
                        } else {
                            onOpenFeed()
                        }
                    },
                    onLongClick = { navigator.push(Route.Copy(reply.message)) },
                )
                .padding(
                    start = if (isContainer) 10.dp else 16.dp,
                    end = if (isContainer) 10.dp else 16.dp,
                    top = if (isContainer) 10.dp else 12.dp,
                    bottom = if (isContainer) 10.dp else 12.dp,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            NetworkImage(
                reply.userAvatar,
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = { navigator.push(Route.User(reply.uid)) }),
                contentDescription = reply.username,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                HtmlText(
                    usernameHtml(reply, isReply2Reply, isTopReply, uid),
                    Modifier.padding(end = 15.dp),
                    style = HtmlTextDefaults.style(),
                    maxLines = 2,
                )
                if (reply.message.isNotEmpty()) {
                    HtmlText(reply.message, style = HtmlTextDefaults.style())
                }
                if (reply.picArr.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    ImageGrid(reply.picArr, onClick = { index ->
                        navigator.push(Route.ImageView(reply.picArr, index))
                    })
                }
                val nestedFeed = reply.obj("feed")
                if (!isFeedReply && nestedFeed != null && !isReply2Reply) {
                    Spacer(Modifier.height(10.dp))
                    NestedFeed(nestedFeed, onClick = {
                        if (nestedFeed.url.isNotEmpty()) navigator.push(Route.Web(nestedFeed.url))
                    })
                }
                Spacer(Modifier.height(10.dp))
                ReplyBottomInfo(reply, onLike)
                val moreCount = reply.int("replyRowsMore") ?: 0
                if (!isReply2Reply && (reply.replyRows.isNotEmpty() || moreCount != 0)) {
                    Spacer(Modifier.height(10.dp))
                    NestedReplies(
                        reply = reply,
                        moreCount = moreCount,
                        onReply = onReply,
                        onBlock = onBlock,
                        onDelete = onDelete,
                        onShowReplies = onShowReplies,
                    )
                }
            }
        }
        IconButton(
            onClick = { panelOpen = true },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                COUIIcons.ExpandMore,
                contentDescription = "更多",
                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }

    OverlayBottomSheet(show = panelOpen, onDismissRequest = { panelOpen = false }) {
        ReplyMenuSheet(
            reply = reply,
            isOwn = reply.uid.isNotEmpty() && reply.uid == app.session.uid,
            onCopy = { navigator.push(Route.Copy(reply.message)) },
            onBlock = { onBlock?.invoke(reply.uid, null) },
            onDelete = { onDelete?.invoke(reply.id, null) },
            onReport = { navigator.push(Route.Web(Protocol.reportLink(reply.id, isReply = true))) },
            onShowReplies = { onShowReplies?.invoke(reply) },
        )
    }
}

@Composable
private fun ReplyMenuSheet(
    reply: Entity,
    isOwn: Boolean,
    onCopy: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onShowReplies: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (reply.message.isNotEmpty()) ActionSheetItem(COUIIcons.Copy, "复制", onCopy)
        ActionSheetItem(COUIIcons.Blocklist, "屏蔽", onBlock)
        if (isOwn) ActionSheetItem(COUIIcons.Delete, "删除", onDelete)
        ActionSheetItem(COUIIcons.Report, "举报", onReport)
        ActionSheetItem(COUIIcons.Messages, "查看回复", onShowReplies)
    }
}

@Composable
private fun NestedReplies(
    reply: Entity,
    moreCount: Int,
    onReply: ((id: String, username: String, fid: String?) -> Unit)?,
    onBlock: ((uid: String, id: String?) -> Unit)?,
    onDelete: ((id: String, fid: String?) -> Unit)?,
    onShowReplies: ((reply: Entity) -> Unit)?,
) {
    val navigator = LocalNavigator.current
    var panelFor by remember { mutableStateOf<Entity?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(COUITheme.colorScheme.surfaceContainer),
    ) {
        reply.replyRows.forEach { nested ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onReply?.invoke(nested.id, nested.username, reply.id) },
                        onLongClick = { panelFor = nested },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                HtmlText(
                    nestedReplyHtml(nested, reply),
                    style = HtmlTextDefaults.style().copy(fontSize = 14.sp),
                    onTextClick = { onShowReplies?.invoke(reply) },
                    onShowTotalReply = { onShowReplies?.invoke(reply) },
                    picArr = nested.picArr,
                )
            }
        }
        if (moreCount > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onShowReplies?.invoke(reply) })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    "查看更多回复(${reply.replyNum})",
                    style = HtmlTextDefaults.style().copy(fontSize = 14.sp),
                    color = COUITheme.colorScheme.primary,
                )
            }
        }
    }

    panelFor?.let { nested ->
        OverlayBottomSheet(show = true, onDismissRequest = { panelFor = null }) {
            ReplyMenuSheet(
                reply = nested,
                isOwn = nested.uid.isNotEmpty() && nested.uid == LocalApp.current.session.uid,
                onCopy = { navigator.push(Route.Copy(nested.message)) },
                onBlock = { onBlock?.invoke(nested.uid, reply.id) },
                onDelete = { onDelete?.invoke(nested.id, reply.id) },
                onReport = { navigator.push(Route.Web(Protocol.reportLink(nested.id, isReply = true))) },
                onShowReplies = { onShowReplies?.invoke(nested) },
            )
        }
    }
}

@Composable
private fun NestedFeed(feed: Entity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(COUITheme.colorScheme.surface)
            .combinedClickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (feed.pic.isNotEmpty()) {
            NetworkImage(feed.pic, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("@${feed.username}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stripTags(feed.message),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ReplyBottomInfo(reply: Entity, onLike: ((id: String, liked: Boolean) -> Unit)?) {
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            fromToday(reply.dateline),
            modifier = Modifier.weight(1f),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (reply.replyNum.isNotEmpty()) {
            Icon(
                AppIcons.ChatBubbleOutlined,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                reply.replyNum,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (reply.likeNum.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            val liked = reply.isLiked
            Row(
                modifier = if (app.session.isLogin && onLike != null) {
                    Modifier.combinedClickable(onClick = { onLike(reply.id, liked) })
                } else {
                    Modifier
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (liked) AppIcons.ThumbUp else AppIcons.ThumbUpOutlined,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (liked) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    reply.likeNum,
                    style = COUITheme.textStyles.footnote1,
                    color = if (liked) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** The author line: the plain variant outside the reply-to-reply page, links inside it. */
private fun usernameHtml(reply: Entity, isReply2Reply: Boolean, isTopReply: Boolean, uid: String?): String {
    val username = reply.username
    if (!isReply2Reply) {
        val authorTag = if (reply.uid.isNotEmpty() && reply.uid == reply.string("feedUid")) " [楼主]" else ""
        val topTag = if (isTopReply) " [置顶]" else ""
        return "$username$authorTag$topTag"
    }
    val replyTag = when {
        reply.uid.isNotEmpty() && reply.uid == reply.string("feedUid") -> " [楼主] "
        reply.uid.isNotEmpty() && reply.uid == uid && !isTopReply -> " [层主] "
        else -> ""
    }
    val self = "<a href=\"/u/${reply.uid}\">$username$replyTag</a>"
    if (isTopReply) return self
    val ruid = reply.string("ruid")
    if (ruid.isEmpty() || ruid == "0") return self
    val rReplyTag = when {
        ruid == reply.string("feedUid") -> " [楼主] "
        ruid == uid -> " [层主] "
        else -> ""
    }
    return "${self}回复<a href=\"/u/$ruid\">${reply.string("rusername")}$rReplyTag</a>"
}

/** One nested reply, with the same tags and picture link the Flutter client builds. */
private fun nestedReplyHtml(nested: Entity, parent: Entity): String {
    val replyTag = when {
        nested.uid.isNotEmpty() && nested.uid == parent.string("feedUid") -> " [楼主] "
        nested.uid.isNotEmpty() && nested.uid == parent.uid -> " [层主] "
        else -> ""
    }
    val ruid = nested.string("ruid")
    val rReplyTag = when {
        ruid == parent.string("feedUid") -> " [楼主] "
        ruid == parent.uid -> " [层主] "
        else -> ""
    }
    val rReplyUser = if (ruid == parent.uid) {
        ""
    } else {
        "<a href=\"/u/$ruid\">${nested.string("rusername")}$rReplyTag</a>"
    }
    val replyPic = if (nested.picArr.isEmpty()) {
        ""
    } else {
        " <a href=\"${nested.pic}\">查看图片(${nested.picArr.size})</a>"
    }
    return "<a href=\"/u/${nested.uid}\">${nested.username}$replyTag</a>回复$rReplyUser: ${nested.message}$replyPic"
}

private val tagPattern = Regex("<[^>]*>")

private fun stripTags(html: String): String =
    tagPattern.replace(html, "").replace("&nbsp;", " ").replace("&amp;", "&").trim()
