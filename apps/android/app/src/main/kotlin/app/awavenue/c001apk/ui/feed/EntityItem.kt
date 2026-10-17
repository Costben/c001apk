package app.awavenue.c001apk.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.nav.FFFListType
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.user.UserRow
import app.awavenue.c001apk.ui.user.UserRowTypes
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PageIndicator
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

private val ItemPadding = 12.dp
private val appCardTypes = setOf("apk", "product", "topic")
private const val ICON_GRID_COLUMNS = 5
private const val INFINITE_LOOP_RESET_DELAY = 500L

/** One list entry. Templates follow the Flutter client's `itemCard` dispatch. */
@Composable
fun EntityItem(
    item: Entity,
    onBlock: ((uid: String, id: String?) -> Unit)? = null,
    onDelete: ((id: String, fid: String?) -> Unit)? = null,
    onLike: ((id: String, liked: Boolean) -> Unit)? = null,
) {
    val padded = Modifier.padding(horizontal = ItemPadding)
    when {
        item.entityType == "feed" -> if (!FeedActions.isHidden(item)) FeedCard(item, padded)
        item.entityTemplate == "imageCarouselCard_1" -> CarouselCard(item, padded)
        item.entityTemplate == "iconLinkGridCard" -> IconGridCard(item, padded)
        item.entityTemplate == "iconMiniGridCard" -> MiniGridCard(item, padded)
        item.entityTemplate == "iconMiniScrollCard" -> MiniScrollCard(item)
        item.entityTemplate == "imageSquareScrollCard" -> ImageSquareScrollCard(item)
        item.entityTemplate == "imageTextScrollCard" -> ImageTextScrollCard(item, padded)
        item.entityTemplate == "iconScrollCard" -> IconScrollCard(item, padded)
        item.entityTemplate == "titleCard" -> TitleCard(item, padded)
        item.entityTemplate in setOf("noMoreDataCard", "refreshCard", "messageCard") -> Text(
            item.title.ifEmpty { item.string("description") },
            modifier = padded.fillMaxWidth().padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        item.entityType == "card" -> Text(
            "暂不支持此内容",
            modifier = padded.fillMaxWidth().padding(vertical = 8.dp),
            style = COUITheme.textStyles.body1,
        )
        item.entityType == "feed_reply" && item.likeUserInfo != null -> LikeCard(item, padded)
        item.entityType == "feed_reply" -> {
            val openLink = rememberLinkOpener()
            val navigator = LocalNavigator.current
            ReplyCard(
                reply = item,
                onOpenFeed = { openLink(item.url, "") },
                modifier = padded,
                onBlock = onBlock,
                onDelete = onDelete,
                onLike = onLike,
                onShowReplies = { origin ->
                    navigator.push(Route.Reply2Reply(id = origin.id, originUid = origin.uid))
                },
            )
        }
        item.entityType in UserRowTypes -> {
            val navigator = LocalNavigator.current
            val openLink = rememberLinkOpener()
            UserRow(item) {
                val uid = item.uid.ifEmpty { item.obj("userInfo")?.string("uid").orEmpty() }
                if (item.entityType == "recentHistory" || uid.isEmpty()) {
                    openLink(item.url, item.title)
                } else {
                    navigator.push(Route.User(uid))
                }
            }
        }
        item.entityType == "collection" -> CollectionRow(item, padded)
        item.entityType in appCardTypes -> AppRow(item, padded)
        else -> EntityRowCard(item, padded)
    }
}

/** The app, product and topic rows of `components/cards/app_card.dart`, counters included. */
@Composable
private fun AppRow(item: Entity, modifier: Modifier) {
    val openLink = rememberLinkOpener()
    val stats = when (item.entityType) {
        "apk" -> listOf("${item.commentNum}讨论", "${item.downCount}下载")
        "product" -> listOf("${item.hotNumTxt}热度", "${item.feedCommentNumTxt}讨论")
        else -> listOf("${item.hotNumTxt}热度", "${item.commentNumTxt}讨论")
    }
    Card(
        modifier.fillMaxWidth(),
        insideMargin = PaddingValues(10.dp),
        onClick = { openLink(item.url, item.title) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                item.logo,
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                contentDescription = item.title,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.forEach { StatText(it) }
                }
            }
        }
    }
}

@Composable
private fun StatText(value: String) {
    Text(
        value,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.outline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A section link, ported from `components/cards/title_card.dart`. */
@Composable
private fun TitleCard(item: Entity, modifier: Modifier) {
    val openLink = rememberLinkOpener()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (item.url.isNotEmpty()) Modifier.clickable { openLink(item.url, item.title) } else Modifier,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.title,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        if (item.url.isNotEmpty()) {
            Icon(COUIIcons.ChevronForward, contentDescription = null, tint = COUITheme.colorScheme.outline)
        }
    }
}

/** A saved collection folder, opening its content list. */
@Composable
private fun CollectionRow(item: Entity, modifier: Modifier) {
    val navigator = LocalNavigator.current
    Card(
        modifier.fillMaxWidth(),
        insideMargin = PaddingValues(10.dp),
        onClick = {
            navigator.push(Route.FFFList(FFFListType.CollectionItem, id = item.id, title = item.title))
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                item.string("cover_pic"),
                Modifier.size(width = 53.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)),
                contentDescription = item.title,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        item.string("is_open_title"),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${item.string("follow_num")}人关注",
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.outline,
                        maxLines = 1,
                    )
                    Text(
                        "${item.string("item_num")}个内容",
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CarouselCard(item: Entity, modifier: Modifier) {
    val entries = item.entities.filter { it.pic.isNotEmpty() && !it.url.startsWith("http") }
    if (entries.isEmpty()) return
    val openLink = rememberLinkOpener()
    val pages = if (entries.size == 1) entries else listOf(entries.last()) + entries + listOf(entries.first())
    val pagerState = rememberPagerState(initialPage = if (pages.size == 1) 0 else 1) { pages.size }
    if (pages.size > 1) {
        LaunchedEffect(pagerState.currentPage) {
            when (pagerState.currentPage) {
                0 -> {
                    delay(INFINITE_LOOP_RESET_DELAY)
                    pagerState.scrollToPage(pages.size - 2)
                }
                pages.size - 1 -> {
                    delay(INFINITE_LOOP_RESET_DELAY)
                    pagerState.scrollToPage(1)
                }
            }
        }
    }
    Box(modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(17.dp))) {
        HorizontalPager(state = pagerState) { page ->
            val entry = pages[page]
            Card(cornerRadius = 0.dp, onClick = { openLink(entry.url, entry.title) }) {
                NetworkImage(entry.pic, Modifier.fillMaxWidth().aspectRatio(3f), contentDescription = entry.title)
            }
        }
        if (pages.size > 1) {
            val current = when (pagerState.currentPage) {
                0 -> pages.size - 3
                pages.size - 1 -> 0
                else -> pagerState.currentPage - 1
            }
            PageIndicator(
                count = pages.size - 2,
                currentIndex = current,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun IconGridCard(item: Entity, modifier: Modifier) {
    val entries = item.entities
    val pages = entries.size / ICON_GRID_COLUMNS
    if (pages == 0) return
    val openLink = rememberLinkOpener()
    val pagerState = rememberPagerState { pages }
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
        HorizontalPager(state = pagerState) { page ->
            Row(Modifier.fillMaxWidth()) {
                repeat(ICON_GRID_COLUMNS) { column ->
                    val entry = entries[page * ICON_GRID_COLUMNS + column]
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Card(
                            cornerRadius = 12.dp,
                            onClick = { openLink(entry.url, entry.title) },
                        ) {
                            NetworkImage(entry.pic.ifEmpty { entry.logo }, Modifier.size(40.dp), contentDescription = entry.title)
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(
                            entry.title,
                            style = COUITheme.textStyles.footnote1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (pages != 1) {
            PageIndicator(
                count = pages,
                currentIndex = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ImageSquareScrollCard(item: Entity) {
    val entries = item.entities
    if (entries.isEmpty()) return
    val openLink = rememberLinkOpener()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - ItemPadding * 2 - 40.dp) / 5
        LazyRow(
            contentPadding = PaddingValues(horizontal = ItemPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries) { entry ->
                Box(
                    Modifier
                        .size(cell)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openLink(entry.url, entry.title) },
                ) {
                    NetworkImage(entry.pic, Modifier.fillMaxSize(), contentDescription = entry.title)
                    Box(Modifier.fillMaxSize().background(Color(0x8D000000)))
                    Text(
                        entry.title,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEEEEEE),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageTextScrollCard(item: Entity, modifier: Modifier) {
    val entries = item.entities
    if (entries.isEmpty()) return
    val openLink = rememberLinkOpener()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = maxWidth / 3 * 2
        Column(Modifier.fillMaxWidth()) {
            if (item.title.isNotEmpty()) SmallTitle(item.title)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries) { entry ->
                    Card(
                        Modifier.width(cell),
                        cornerRadius = 12.dp,
                        onClick = { openLink(entry.url, entry.title) },
                    ) {
                        NetworkImage(
                            entry.pic,
                            Modifier.fillMaxWidth().aspectRatio(2.22f),
                            contentDescription = entry.title,
                        )
                        Text(
                            "${entry.title}\n",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconScrollCard(item: Entity, modifier: Modifier) {
    val entries = item.entities
    if (entries.isEmpty()) return
    val openLink = rememberLinkOpener()
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(vertical = 10.dp)) {
        if (item.title.isNotEmpty()) SmallTitle(item.title)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - 55.dp) / 4.5f
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(entries) { entry ->
                    Column(
                        Modifier
                            .width(cell)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { openLink(entry.url, entry.title) }
                            .padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NetworkImage(
                            entry.userAvatar,
                            Modifier.size(cell - 10.dp).clip(CircleShape),
                            contentDescription = entry.username,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(entry.username, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniItem(entry: Entity, modifier: Modifier = Modifier) {
    val openLink = rememberLinkOpener()
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { openLink(entry.url, entry.title) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkImage(entry.logo.ifEmpty { entry.pic }, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(6.dp))
        Text(entry.title, style = COUITheme.textStyles.footnote1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniGridCard(item: Entity, modifier: Modifier) {
    val entries = item.entities
    if (entries.isEmpty()) return
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(vertical = 6.dp)) {
        if (item.title.isNotEmpty()) SmallTitle(item.title)
        entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { MiniItem(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniScrollCard(item: Entity) {
    val entries = item.entities
    if (entries.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = ItemPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries) { entry ->
            Card(cornerRadius = 8.dp) { MiniItem(entry) }
        }
    }
}

/** Users, topics, apps, products and the other single-entity kinds. */
@Composable
private fun EntityRowCard(item: Entity, modifier: Modifier) {
    val image = item.logo.ifEmpty { item.userAvatar }.ifEmpty { item.pic }
    val title = item.title.ifEmpty { item.username }
    if (title.isEmpty()) return
    val openLink = rememberLinkOpener()
    val navigator = LocalNavigator.current
    val isUser = item.entityType == "user"
    Card(
        modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
        onClick = {
            val url = item.url.ifEmpty { if (isUser) "/u/${item.uid}" else "" }
            // A dedicated screen beats the generic dispatcher for the kinds we model directly.
            when {
                isUser -> navigator.push(Route.User(item.uid))
                else -> openLink(url, title)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                image,
                Modifier.size(44.dp).clip(if (isUser) CircleShape else RoundedCornerShape(12.dp)),
                contentDescription = title,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val summary = item.string("description").ifEmpty { item.string("intro") }
                if (summary.isNotEmpty()) {
                    Text(
                        summary,
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
