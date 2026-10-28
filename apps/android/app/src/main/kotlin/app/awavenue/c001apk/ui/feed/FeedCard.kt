package app.awavenue.c001apk.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.CounterButton
import app.awavenue.c001apk.ui.common.MoreMenu
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.openCoolapkLink
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalAppBarBounds
import app.awavenue.c001apk.ui.nav.LocalFeedListState
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.expandOrigin
import app.awavenue.c001apk.ui.nav.rememberExpandOrigin
import app.awavenue.c001apk.ui.text.FeedText
import app.awavenue.c001apk.ui.text.HtmlText
import app.awavenue.c001apk.ui.text.HtmlTextDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val CardPadding = 12.dp

// A card previews the post: a long body is cut at this many lines and the pictures at a nine-grid,
// the rest is on the detail page.
private const val PreviewMaxLines = 7
private const val PreviewMaxPictures = 9

/** A like the user just made, together with the feed it was made on. */
internal class FeedLike(val liked: Boolean, val count: String, val source: Entity)

/**
 * Likes and removals applied locally. A list rebuilds its cards while scrolling, so the outcome
 * is kept per feed id and only trusted as long as the very feed it belongs to is still the one
 * the list hands out.
 */
internal object FeedActions {
    private val likes = mutableStateMapOf<String, FeedLike>()
    private val removedFeeds = mutableStateMapOf<String, Boolean>()
    private val blockedUsers = mutableStateMapOf<String, Boolean>()

    /**
     * Feeds whose body overflowed the preview. A page rebuilt after a detail closes lays its
     * cards out again, and the overflow is only known after that first layout; keeping the last
     * answer stops 查看更多 from dropping out for the frame in between.
     */
    private val truncatedFeeds = mutableSetOf<String>()

    fun wasTruncated(id: String): Boolean = id in truncatedFeeds

    fun rememberTruncated(id: String, truncated: Boolean) {
        if (truncated) truncatedFeeds.add(id) else truncatedFeeds.remove(id)
    }

    fun like(feed: Entity): FeedLike? = likes[feed.id]?.takeIf { it.source === feed }

    fun rememberLike(feed: Entity, liked: Boolean, count: String) {
        likes[feed.id] = FeedLike(liked, count, feed)
    }

    fun removeFeed(id: String) {
        removedFeeds[id] = true
    }

    /** Blocking a user drops their feeds from the list, the way the Flutter controller does. */
    fun blockUser(uid: String) {
        if (uid.isNotEmpty()) blockedUsers[uid] = true
    }

    fun isRemoved(feed: Entity): Boolean = removedFeeds[feed.id] == true || blockedUsers[feed.uid] == true

    /**
     * Whether [EntityItem] draws nothing for [item]. A list built on it drops such rows from its data,
     * otherwise the empty slot keeps the list's item spacing as a gap.
     */
    fun isHidden(item: Entity): Boolean =
        item.entityTemplate == "configCard" || (item.entityType == "feed" && isRemoved(item))
}

@Composable
fun FeedCard(
    feed: Entity,
    modifier: Modifier = Modifier,
    isHistory: Boolean = false,
    onDelete: (() -> Unit)? = null,
) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val origin = rememberExpandOrigin(CardDefaults.CornerRadius, CardDefaults.defaultColors().color)
    val listState = LocalFeedListState.current
    val appBarBounds = LocalAppBarBounds.current
    SideEffect {
        origin.listState = listState
        origin.rowKey = feed.id
    }
    // The page this row lives on is disposed while another one covers it; when it comes back its
    // rows are new. The host works out where the list must start while the page is still covered,
    // so the row jumps there on the first measure instead of scrolling after it is on screen.
    // The page is rebuilt on the way back; the host has already worked out where its list must
    // start. Ask for that position in a SideEffect, which runs before the first layout, so the
    // first frame the user sees is already the final one instead of the old position.
    SideEffect {
        val state = listState ?: return@SideEffect
        val pending = appBarBounds.pendingReturnFor(feed.id) ?: return@SideEffect
        val target = pending.target ?: return@SideEffect
        state.requestScrollToItem(target.index, target.offset)
        appBarBounds.clearReturn(feed.id)
    }
    // A return without a precomputed target still has to clear the bar; that one has to measure
    // the rebuilt row, so it runs after the first frames.
    LaunchedEffect(feed.id, listState) {
        if (listState == null) return@LaunchedEffect
        repeat(4) {
            val pending = appBarBounds.pendingReturnFor(feed.id)
            if (pending != null) {
                appBarBounds.alignReturnedRow(feed.id, origin, listState)
                appBarBounds.clearReturn(feed.id)
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }
    val openFeed = rememberHapticClick { navigator.push(Route.Feed(feed.id, preview = feed), origin) }
    var menuOpen by remember(feed) { mutableStateOf(false) }

    val like = FeedActions.like(feed)
    val liked = like?.liked ?: feed.isLiked
    val likeNum = like?.count ?: feed.likeNum
    val onLike: () -> Unit = rememberHapticClick {
        scope.launch {
            try {
                val result = app.repository.like(feed.id, liked, isFeed = true)
                if (result.message.isNotEmpty()) {
                    context.toast(result.message)
                } else {
                    FeedActions.rememberLike(feed, !liked, result.count.ifEmpty { likeNum })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toast(ApiFailure.from(e).message)
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth().expandOrigin(origin),
        insideMargin = PaddingValues(CardPadding),
        onClick = openFeed,
        onLongPress = { navigator.push(Route.Copy(feed.message)) },
    ) {
        FeedHeader(
            feed,
            onUserClick = rememberHapticClick { navigator.push(Route.User(feed.uid)) },
            onMore = rememberHapticClick { menuOpen = true },
        ) {
            MoreMenu(
                show = menuOpen,
                onDismissRequest = { menuOpen = false },
                entries = feedMenuEntries(
                    showDelete = isHistory || (feed.uid.isNotEmpty() && feed.uid == app.session.uid),
                    onCopy = { context.copyText("${Protocol.URL_COOLAPK}/feed/${feed.id}") },
                    onBlock = {
                        app.blacklist.blockUser(feed.uid)
                        FeedActions.blockUser(feed.uid)
                    },
                    onReport = { navigator.push(Route.Web(Protocol.reportLink(feed.id))) },
                    onDelete = {
                        if (onDelete != null) {
                            onDelete()
                        } else {
                            scope.launch {
                                try {
                                    val result = app.repository.deleteFeed(feed.id)
                                    if (result.message.isNotEmpty()) {
                                        context.toast(result.message)
                                    } else {
                                        FeedActions.removeFeed(feed.id)
                                        if (result.count.isNotEmpty()) context.toast(result.count)
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    context.toast(ApiFailure.from(e).message)
                                }
                            }
                        }
                    },
                ),
            )
        }
        // Title and body sizes follow the Flutter feed card: 16sp, title bold.
        if (feed.messageTitle.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HtmlText(
                feed.messageTitle,
                style = HtmlTextDefaults.style().copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                onTextClick = openFeed,
            )
        }
        if (feed.message.isNotEmpty()) {
            Spacer(Modifier.height(if (feed.messageTitle.isNotEmpty()) 4.dp else 10.dp))
            var truncated by remember(feed) { mutableStateOf(FeedActions.wasTruncated(feed.id)) }
            FeedText(
                feed.message,
                fontSize = 16.sp,
                preview = true,
                previewMaxLines = PreviewMaxLines,
                onTextClick = openFeed,
                onTextLayout = {
                    truncated = it.hasVisualOverflow
                    FeedActions.rememberTruncated(feed.id, it.hasVisualOverflow)
                },
            )
            if (truncated) {
                Text(
                    "查看更多",
                    modifier = Modifier.padding(top = 2.dp).clickable(onClick = openFeed),
                    style = HtmlTextDefaults.style().copy(fontSize = 16.sp),
                    color = COUITheme.colorScheme.primary,
                )
            }
        }
        if (feed.picArr.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ImageGrid(
                feed.picArr,
                onClick = { index -> navigator.push(Route.ImageView(feed.picArr, index)) },
                maxCount = PreviewMaxPictures,
            )
        }
        if (feed.forwardSourceType.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ForwardSource(feed.forwardSourceFeed)
        }
        if (feed.extraUrl.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ExtraLink(feed)
        }
        val hotReply = feed.replyRows.firstOrNull()
        if (hotReply != null) {
            Spacer(Modifier.height(10.dp))
            val hotReplyPics = hotReply.picArr
            val hotReplyPic = if (hotReplyPics.isEmpty()) {
                ""
            } else {
                " <a href=\"${hotReply.pic}\">查看图片(${hotReplyPics.size})</a>"
            }
            InsetBlock(
                onClick = openFeed,
                onLongClick = { navigator.push(Route.Copy(hotReply.message)) },
            ) {
                HtmlText(
                    "<a href=\"/u/${hotReply.uid}\">${hotReply.username}</a>: ${hotReply.message}$hotReplyPic",
                    style = COUITheme.textStyles.footnote1,
                    maxLines = 3,
                    onTextClick = openFeed,
                    onShowTotalReply = openFeed,
                    picArr = hotReplyPics,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FeedFooter(feed, liked, likeNum, onLike = if (app.session.isLogin) onLike else null)
        val relationRows = listOfNotNull(feed.targetRow) + feed.relationRows
        if (relationRows.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            RelationRows(relationRows)
        }
    }

}

private fun feedMenuEntries(
    showDelete: Boolean,
    onCopy: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
): List<Pair<String, () -> Unit>> = buildList {
    add("复制" to onCopy)
    add("屏蔽" to onBlock)
    add("举报" to onReport)
    if (showDelete) add("删除" to onDelete)
}

@Composable
private fun FeedHeader(
    feed: Entity,
    onUserClick: () -> Unit,
    onMore: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NetworkImage(
            feed.userAvatar,
            Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onUserClick),
            contentDescription = feed.username,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(feed.username, style = COUITheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val info = stripHtml(feed.infoHtml)
            val device = stripHtml(feed.deviceTitle)
            if (info.isNotEmpty() || device.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (info.isNotEmpty()) {
                        Text(
                            info,
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    if (device.isNotEmpty()) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.Smartphone,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                device,
                                style = COUITheme.textStyles.footnote1,
                                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (feed.isStickTop) StickTopTag()
        Box {
            IconButton(onClick = onMore) {
                Icon(COUIIcons.More, contentDescription = "更多", tint = COUITheme.colorScheme.outline)
            }
            menu()
        }
    }
}

@Composable
private fun StickTopTag() {
    Text(
        "置顶",
        modifier = Modifier
            .border(1.25.dp, COUITheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .background(COUITheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        fontSize = 12.sp,
        color = COUITheme.colorScheme.primary,
        maxLines = 1,
    )
}

@Composable
private fun ForwardSource(source: Entity?) {
    val navigator = LocalNavigator.current
    InsetBlock(
        onClick = source?.let { { navigator.push(Route.Feed(it.id)) } },
        onLongClick = source?.let { { navigator.push(Route.Copy(it.message)) } },
    ) {
        val sourceFeed = source
        if (sourceFeed == null) {
            Text("动态已被删除", color = COUITheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
            return@InsetBlock
        }
        val author = "<a href=\"/u/${sourceFeed.uid}\">@${sourceFeed.username}</a>: "
        if (sourceFeed.messageTitle.isNotEmpty()) {
            HtmlText(author + sourceFeed.messageTitle, maxLines = 2)
            FeedText(sourceFeed.message, preview = true, previewMaxLines = 6)
        } else {
            FeedText(author + "\n" + sourceFeed.message, preview = true, previewMaxLines = 6)
        }
        val sourcePics = sourceFeed.picArr
        if (sourcePics.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ImageGrid(
                sourcePics,
                onClick = { index -> navigator.push(Route.ImageView(sourcePics, index)) },
                maxCount = PreviewMaxPictures,
            )
        }
    }
}

@Composable
private fun ExtraLink(feed: Entity) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val openInBrowser = LocalApp.current.settings.openInBrowser
    InsetBlock(
        onClick = rememberHapticClick {
            openCoolapkLink(context, navigator, feed.extraUrl, feed.extraTitle, openInBrowser)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(feed.extraPic, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(feed.extraTitle.ifEmpty { feed.extraUrl }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    feed.extraUrl,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The 话题 / 应用 / 数码 chips a feed points at, in the order the Flutter card lays them out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationRows(rows: List<Entity>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        rows.forEach { RelationChip(it) }
    }
}

@Composable
private fun RelationChip(entry: Entity) {
    val openLink = rememberLinkOpener()
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(COUITheme.colorScheme.surface)
            .clickable(onClick = rememberHapticClick { openLink(entry.url, entry.title) })
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkImage(entry.logo, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(4.dp))
        Text(entry.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InsetBlock(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(rememberSquircleShape(12.dp))
            .background(COUITheme.colorScheme.surface)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .padding(10.dp),
    ) { content() }
}

@Composable
private fun FeedFooter(feed: Entity, liked: Boolean, likeNum: String, onLike: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            fromToday(feed.dateline),
            modifier = Modifier.weight(1f),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
        if (feed.replyNum.isNotEmpty()) {
            CounterButton(AppIcons.ChatBubbleOutlined, feed.replyNum, highlighted = false, onClick = null)
        }
        if (likeNum.isNotEmpty()) {
            Spacer(Modifier.width(16.dp))
            CounterButton(if (liked) AppIcons.ThumbUp else AppIcons.ThumbUpOutlined, likeNum, liked, onClick = onLike)
        }
    }
}

private val tagPattern = Regex("<[^>]*>")

private fun stripHtml(html: String): String =
    tagPattern.replace(html, "").replace("&nbsp;", " ").replace("&amp;", "&").trim()
