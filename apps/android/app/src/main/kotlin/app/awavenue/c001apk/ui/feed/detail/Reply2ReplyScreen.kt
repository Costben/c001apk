package app.awavenue.c001apk.ui.feed.detail

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.BuildConfig
import app.awavenue.c001apk.data.BlacklistStore
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.ReplyCard
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The answers to one reply. [id] is the reply being listed and [replyId] the feed it belongs to.
 *
 * The Flutter page injects the origin reply as its first row and reads the title from that
 * entity's reply count. A caller that holds the [origin] entity does the same here; one that does
 * not lists the answers alone and the title counts what has been loaded so far.
 */
@Composable
fun Reply2ReplyScreen(id: String, replyId: String, originUid: String = "", origin: Entity? = null) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = viewModel(key = "reply2reply-$id") {
        Reply2ReplyViewModel(id, origin, app.repository, app.blacklist)
    }
    val listState = rememberLazyListState()
    val backdrop = rememberGlassBackdrop()
    var replyTarget by remember { mutableStateOf<ReplyTarget?>(null) }

    LaunchedEffect(viewModel.notice) {
        viewModel.notice?.let {
            context.toast(it)
            viewModel.notice = null
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && last >= layout.totalItemsCount - 3
        }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "共 ${origin?.replyNum ?: viewModel.replies.size} 回复",
                    gradientGlass = true,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize().glassSource(backdrop).padding(innerPadding)) {
                when (val status = viewModel.status) {
                    ListStatus.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        InfiniteProgressIndicator()
                    }

                    ListStatus.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = "暂无内容",
                            actionText = "刷新",
                            onActionClick = { viewModel.load() },
                        )
                    }

                    is ListStatus.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = status.message,
                            actionText = "重试",
                            onActionClick = { viewModel.load() },
                        )
                    }

                    ListStatus.Content -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        itemsIndexed(
                            viewModel.replies,
                            key = { index, reply -> reply.entityId.ifEmpty { "reply2reply-$index" } },
                            contentType = { _, _ -> "reply" },
                        ) { index, reply ->
                            Column(Modifier.fillMaxWidth()) {
                                ReplyCard(
                                    reply = reply,
                                    onOpenFeed = {
                                        navigator.push(
                                            Route.Web(reply.url.ifEmpty { "${Protocol.URL_COOLAPK}/feed/$replyId" }),
                                        )
                                    },
                                    isReply2Reply = true,
                                    isTopReply = origin != null && index == 0,
                                    uid = originUid.ifEmpty { null },
                                    onBlock = viewModel::onBlock,
                                    onDelete = viewModel::onDelete,
                                    onLike = viewModel::onLike,
                                    onReply = { rid, username, _ ->
                                        replyTarget = ReplyTarget(
                                            id = rid,
                                            type = "reply",
                                            title = username,
                                            feedType = "feed",
                                        )
                                    },
                                )
                                HorizontalDivider(color = COUITheme.colorScheme.dividerLine)
                            }
                        }
                        item(contentType = "footer") {
                            // A short list never leaves "near the end", so the scroll trigger alone
                            // would leave this footer spinning; reaching it asks for the next page.
                            LaunchedEffect(viewModel.replies) { viewModel.loadMore() }
                            Reply2ReplyFooter(viewModel.footer, viewModel::retryFooter)
                        }
                    }
                }
            }
        }
    }

    replyTarget?.let { target ->
        ReplyComposerSheet(
            target = target,
            onDismiss = { replyTarget = null },
            onPosted = {
                replyTarget = null
                viewModel.load()
            },
        )
    }
}

@Composable
private fun Reply2ReplyFooter(status: FooterStatus, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (status) {
            FooterStatus.Idle, FooterStatus.Loading -> InfiniteProgressIndicator()
            FooterStatus.End -> Unit
            is FooterStatus.Error -> TextButton(text = "${status.message}，点击重试", onClick = onRetry)
        }
    }
}

/** Collects one reply's answers; paging follows every other list in the client. */
private class Reply2ReplyViewModel(
    private val id: String,
    private val origin: Entity?,
    private val repository: CoolapkRepository,
    private val blacklist: BlacklistStore,
) : ViewModel() {

    var replies by mutableStateOf<List<Entity>>(emptyList())
        private set
    var status by mutableStateOf<ListStatus>(ListStatus.Loading)
        private set
    var footer by mutableStateOf<FooterStatus>(FooterStatus.Idle)
        private set
    var notice by mutableStateOf<String?>(null)

    private var page = 1
    private var firstItem: String? = null
    private var lastItem: String? = null
    private var isEnd = false
    private var isLoading = false

    init {
        load()
    }

    fun load() {
        if (isLoading) return
        page = 1
        firstItem = null
        lastItem = null
        isEnd = false
        status = ListStatus.Loading
        footer = FooterStatus.Idle
        fetch(refresh = true)
    }

    fun loadMore() {
        if (isLoading || isEnd || status != ListStatus.Content) return
        fetch(refresh = false)
    }

    fun retryFooter() {
        isEnd = false
        loadMore()
    }

    private fun fetch(refresh: Boolean) {
        if (isLoading) return
        isLoading = true
        val requestPage = if (refresh) 1 else page
        if (!refresh) footer = FooterStatus.Loading
        viewModelScope.launch {
            try {
                val rows = repository.replyToReply(
                    id = id,
                    page = requestPage,
                    firstItem = if (refresh) null else firstItem,
                    lastItem = if (refresh) null else lastItem,
                )
                if (rows.isEmpty()) {
                    isEnd = true
                    when {
                        // Upstream still shows the origin reply when it has no answers yet.
                        refresh && origin != null -> {
                            replies = listOf(origin)
                            status = ListStatus.Content
                            footer = FooterStatus.End
                        }
                        refresh -> {
                            replies = emptyList()
                            status = ListStatus.Empty
                        }
                        else -> footer = FooterStatus.End
                    }
                } else {
                    if (refresh) {
                        page = 1
                        firstItem = null
                        lastItem = null
                    }
                    firstItem = firstItem ?: rows.first().id
                    lastItem = rows.last().id
                    val merged = if (refresh) listOfNotNull(origin) + rows else replies + rows
                    val seen = HashSet<String>()
                    replies = merged.filter { it.entityId.isEmpty() || seen.add(it.entityId) }
                    status = ListStatus.Content
                    footer = FooterStatus.Idle
                    page = requestPage + 1
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("Reply2Reply", "page $requestPage failed", e)
                val message = ApiFailure.from(e).message
                when {
                    refresh -> status = ListStatus.Error(message)
                    else -> {
                        isEnd = true
                        footer = FooterStatus.Error(message)
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun onBlock(uid: String, id: String?) {
        if (uid.isEmpty()) return
        blacklist.blockUser(uid)
        replies = replies.filterNot { it.uid == uid }
    }

    fun onDelete(id: String, fid: String?) {
        viewModelScope.launch {
            try {
                val result = repository.deleteReply(id)
                if (result.message.isNotEmpty()) {
                    notice = result.message
                    return@launch
                }
                replies = replies.filterNot { it.id == id }
                if (result.count.isNotEmpty()) notice = result.count
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = ApiFailure.from(e).message
            }
        }
    }

    fun onLike(id: String, liked: Boolean) {
        viewModelScope.launch {
            try {
                val result = repository.like(id, liked, isFeed = false)
                if (result.message.isNotEmpty()) {
                    notice = result.message
                    return@launch
                }
                replies = replies.map {
                    if (it.id == id) it.withLike(result.count, liked) else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = ApiFailure.from(e).message
            }
        }
    }
}

private fun Entity.withLike(count: String, currentlyLiked: Boolean): Entity {
    val action = JsonObject(
        (userAction?.json ?: JsonObject(emptyMap())) + ("like" to JsonPrimitive(if (currentlyLiked) "0" else "1")),
    )
    return Entity(JsonObject(json + ("likenum" to JsonPrimitive(count)) + ("userAction" to action)))
}
