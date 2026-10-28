package app.awavenue.c001apk.ui.feed.detail

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awavenue.c001apk.BuildConfig
import app.awavenue.c001apk.data.AppSettings
import app.awavenue.c001apk.data.BlacklistStore
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.data.FavHistoryItem
import app.awavenue.c001apk.data.HistoryStore
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.network.ApiFailureKind
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.text.MarkdownContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The four reply orderings of the pinned header, matching the Flutter `ReplySortType`. */
enum class ReplySortType(val label: String) {
    Def("默认"),
    Dateline("最新"),
    Hot("热门"),
    Author("楼主"),
}

/**
 * The feed body and its reply stream. State, request parameters and paging follow the Flutter
 * `FeedController`; the reply list itself is page-local because it is mutated in place by the
 * reply actions instead of only being appended to.
 */
class FeedDetailViewModel(
    val id: String,
    private val repository: CoolapkRepository,
    private val settings: AppSettings,
    private val history: HistoryStore,
    private val blacklist: BlacklistStore,
    private val targetReplyId: String = "",
) : ViewModel() {

    // ---- feed body -----------------------------------------------------------------------------

    var feed by mutableStateOf<Entity?>(null)
        private set
    var feedStatus by mutableStateOf<ListStatus>(ListStatus.Loading)
        private set
    var isPublic by mutableStateOf(false)
        private set

    /** Set when the body could not be loaded, so the retry can offer the right next step. */
    var feedFailureKind by mutableStateOf<ApiFailureKind?>(null)
        private set

    /** Why the account-scoped view fell back to the public one, or the retry outcome. */
    var accountDetailNotice by mutableStateOf("")
        private set
    var isLoadingDetail by mutableStateOf(false)
        private set
    var articles by mutableStateOf<List<FeedArticle>>(emptyList())
        private set
    var articleImages by mutableStateOf<List<String>>(emptyList())
        private set

    /** Whether the body can be read as Markdown, so 查看原文 has something to switch to. */
    var hasMarkdown by mutableStateOf(false)
        private set
    var isFav by mutableStateOf(false)
        private set
    var isBlocked by mutableStateOf(false)
        private set

    /** One-shot text for a toast; the screen clears it after showing it. */
    var notice by mutableStateOf<String?>(null)

    val feedUid: String get() = feed?.uid.orEmpty()
    val feedUsername: String get() = feed?.username.orEmpty()
    val feedTypeName: String? get() = feed?.string("feedTypeName")?.ifEmpty { null }
    val replyCount: String get() = feed?.replyNum.orEmpty()
    val shareUrl: String get() = Protocol.shareLink("feed", id)
    val reportUrl: String get() = Protocol.reportLink(id)

    // ---- replies -------------------------------------------------------------------------------

    var replies by mutableStateOf<List<Entity>>(emptyList())
        private set
    var replyStatus by mutableStateOf<ListStatus>(ListStatus.Loading)
        private set
    var replyFooter by mutableStateOf<FooterStatus>(FooterStatus.Idle)
        private set
    var replySort by mutableStateOf(ReplySortType.Def)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var refreshError by mutableStateOf<String?>(null)

    private var topReply by mutableStateOf<Entity?>(null)
    private var replyMe: Entity? = null
    private var listType = "lastupdate_desc"
    private var fromFeedAuthor = 0
    private var page = 1
    private var firstItem: String? = null
    private var lastItem: String? = null
    private var isEnd = false
    private var isLoading = false
    private var replyJob: Job? = null
    private var replyGeneration = 0

    /** Guards the automatic challenge retry, so a cancelled check cannot loop. */
    private var askedToVerify = false

    /**
     * The reply a notification pointed at, or the top-level reply holding it with the target as its
     * only preview row. It leads the default ordering so the reader does not page through to find it.
     */
    private var anchor: Entity? = null
    private var anchorRequested = false
    private var anchorJumped = false

    /** Id of the anchor while it still wants marking; the screen clears it once it has. */
    var highlightReplyId by mutableStateOf<String?>(null)
        private set

    /** Set once the anchor is in the list, so the screen scrolls to it a single time. */
    var scrollToAnchor by mutableStateOf(false)
        private set

    fun onAnchorScrolled() {
        scrollToAnchor = false
    }

    fun onAnchorHighlighted() {
        highlightReplyId = null
    }

    /** The first row is the pinned reply only while the default ordering is active. */
    val showsPinnedReply: Boolean get() = topReply != null && replySort == ReplySortType.Def

    /** Id of the pinned reply while it is on display, so its menu can offer to unpin it. */
    val pinnedReplyId: String? get() = if (showsPinnedReply) topReply?.id else null

    init {
        loadFeed(verify = false)
    }

    // ---- feed ----------------------------------------------------------------------------------

    /** Retries the body; [verify] re-runs it through the account-scoped call. */
    fun loadFeed(verify: Boolean = false) {
        if (isLoadingDetail) return
        val previousFeed = feed
        val previousPublic = isPublic
        val previousStatus = feedStatus
        var verifyNext = false
        isLoadingDetail = true
        if (!verify) feedStatus = ListStatus.Loading
        viewModelScope.launch {
            try {
                val detail = repository.feedDetail(id, verify)
                applyFeed(detail.feed)
                isPublic = detail.isPublic
                feedStatus = ListStatus.Content
                accountDetailNotice = if (detail.isPublic) PUBLIC_NOTICE else ""
                if (targetReplyId.isNotEmpty() && !anchorRequested) {
                    anchorRequested = true
                    anchor = loadAnchor()
                }
                refreshReplies()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("FeedDetail", "feed $id failed", e)
                val failure = ApiFailure.from(e)
                when {
                    verify -> {
                        accountDetailNotice = failure.message.ifEmpty { UNAVAILABLE_NOTICE }
                        if (previousFeed != null) {
                            feed = previousFeed
                            isPublic = previousPublic
                            feedStatus = previousStatus
                        } else {
                            feedFailureKind = failure.kind
                            feedStatus = ListStatus.Error(failure.message)
                        }
                    }

                    // Without this the challenge would dead-end on the error screen, because the
                    // public substitute is not reachable from every network.
                    failure.kind in CHALLENGE_KINDS && !askedToVerify -> {
                        askedToVerify = true
                        verifyNext = true
                    }

                    else -> {
                        feedFailureKind = failure.kind
                        feedStatus = ListStatus.Error(failure.message)
                    }
                }
            } finally {
                isLoadingDetail = false
            }
            if (verifyNext) loadFeed(verify = true)
        }
    }

    /** The anchor for [targetReplyId]; a missing or deleted reply only costs the jump, not the page. */
    private suspend fun loadAnchor(): Entity? = try {
        val target = repository.replyDetail(targetReplyId).withPicArr()
        when {
            target.string("fid") != id -> null
            target.parentReplyId() == null -> withNestedPreview(target, null)
            else -> withNestedPreview(topLevelReply(target), target)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w("FeedDetail", "reply $targetReplyId failed", e)
        notice = ApiFailure.from(e).message.ifEmpty { null }
        null
    }

    /**
     * The comment [reply] sits under in the list. A nested reply's `rid` is the row it answered, which
     * is itself nested when the thread runs back and forth, so it climbs until a row has no parent.
     */
    private suspend fun topLevelReply(reply: Entity): Entity {
        var current = reply
        repeat(MAX_THREAD_DEPTH) {
            val parentId = current.string("rrid").takeIf { it.isNotEmpty() && it != "0" && it != current.id }
                ?: current.parentReplyId()
                ?: return current
            current = repository.replyDetail(parentId).withPicArr()
        }
        return current
    }

    /**
     * `replyDetail` carries no nested rows, so [reply] would list as a bare comment with only a count.
     * Its first page of answers fills the same preview the reply list draws, [target] leading it.
     */
    private suspend fun withNestedPreview(reply: Entity, target: Entity?): Entity {
        val total = reply.int("replynum") ?: 0
        val answers = if (total == 0) {
            emptyList()
        } else {
            try {
                repository.replyToReply(reply.id, page = 1, firstItem = null, lastItem = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The comment still lists with its count; 查看更多回复 opens the full thread.
                emptyList()
            }
        }
        val rows = (listOfNotNull(target) + answers.filterNot { it.id == target?.id })
            .filterNot { blacklist.isUserBlocked(it.uid) }
            .take(NESTED_PREVIEW)
        val more = (total - rows.size).coerceAtLeast(0)
        return Entity(JsonObject(reply.json + ("replyRowsMore" to JsonPrimitive(more)))).withReplyRows(rows)
    }

    private fun applyFeed(data: Entity) {
        feed = data
        val blocks = FeedArticle.parse(data.string("message_raw_output")).toMutableList()
        if (blocks.isNotEmpty()) {
            if (data.title.isNotEmpty()) blocks.add(0, FeedArticle(type = "title", title = data.title))
            val cover = data.string("message_cover")
            if (cover.isNotEmpty()) blocks.add(0, FeedArticle(type = "image", url = cover))
        }
        articles = blocks
        articleImages = blocks.filter { it.type == "image" }.map { it.url }
        hasMarkdown = MarkdownContent.parse(data.message).isMarkdown ||
            blocks.any { it.type == "text" && MarkdownContent.parse(it.message).isMarkdown }
        topReply = data.list("topReplyRows").firstOrNull()
        replyMe = data.list("replyMeRows").firstOrNull()
        isFav = history.isFav(data.id)
        isBlocked = blacklist.isUserBlocked(data.uid)
        if (settings.recordHistory && !history.historyFeeds.containsKey(data.id)) history.record(memoryOf(data))
    }

    private fun memoryOf(data: Entity) = FavHistoryItem(
        id = data.id,
        uid = data.uid,
        username = data.username,
        userAvatar = data.userAvatar,
        message = data.message.take(150),
        device = data.deviceTitle,
        dateline = data.dateline ?: 0L,
        time = System.currentTimeMillis(),
    )

    fun toggleFav() {
        val data = feed ?: return
        history.toggleFav(memoryOf(data))
        isFav = history.isFav(data.id)
    }

    fun toggleBlock() {
        val uid = feed?.uid.orEmpty()
        if (uid.isEmpty()) return
        if (isBlocked) blacklist.unblockUser(uid) else blacklist.blockUser(uid)
        isBlocked = !isBlocked
    }

    fun onLike(id: String, liked: Boolean, isFeed: Boolean) {
        viewModelScope.launch {
            try {
                val result = repository.like(id, liked, isFeed)
                if (result.message.isNotEmpty()) {
                    notice = result.message
                    return@launch
                }
                val count = result.count
                if (isFeed) {
                    feed = feed?.withLike(count, liked)
                } else {
                    replies = replies.map { if (it.id == id) it.withLike(count, liked) else it }
                    anchor = anchor?.let { if (it.id == id) it.withLike(count, liked) else it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = ApiFailure.from(e).message
            }
        }
    }

    // ---- replies -------------------------------------------------------------------------------

    fun selectReplySort(sort: ReplySortType) {
        if (sort == replySort) return
        replySort = sort
        when (sort) {
            ReplySortType.Def -> {
                listType = "lastupdate_desc"
                fromFeedAuthor = 0
            }
            ReplySortType.Dateline -> {
                listType = "dateline_desc"
                fromFeedAuthor = 0
            }
            ReplySortType.Hot -> {
                listType = "popular"
                fromFeedAuthor = 0
            }
            ReplySortType.Author -> {
                listType = ""
                fromFeedAuthor = 1
            }
        }
        page = 1
        firstItem = null
        lastItem = null
        isEnd = false
        replyFooter = FooterStatus.Loading
        // A page still loading was asked for with the old order; drop it so the new one goes out.
        replyJob?.cancel()
        isLoading = false
        loadReplies(refresh = true)
    }

    fun refreshReplies() {
        if (isLoading) return
        isRefreshing = replies.isNotEmpty()
        loadReplies(refresh = true)
    }

    fun loadMoreReplies() {
        if (isLoading || isEnd || replyStatus != ListStatus.Content) return
        loadReplies(refresh = false)
    }

    fun retryReplyFooter() {
        isEnd = false
        loadMoreReplies()
    }

    private fun loadReplies(refresh: Boolean) {
        if (isLoading) return
        isLoading = true
        val requestPage = if (refresh) 1 else page
        val requestFirst = if (refresh) null else firstItem
        val requestLast = if (refresh) null else lastItem
        if (refresh && replies.isEmpty()) replyStatus = ListStatus.Loading
        if (!refresh) replyFooter = FooterStatus.Loading

        val generation = ++replyGeneration
        replyJob = viewModelScope.launch {
            try {
                val rows = repository.feedReply(
                    id = id,
                    listType = listType,
                    page = requestPage,
                    firstItem = requestFirst,
                    lastItem = requestLast,
                    discussMode = 1,
                    feedType = "feed",
                    blockStatus = 0,
                    fromFeedAuthor = fromFeedAuthor,
                )
                if (refresh) {
                    page = 1
                    firstItem = null
                    lastItem = null
                    isEnd = false
                }
                if (rows.isEmpty()) {
                    isEnd = true
                    if (refresh) {
                        replies = emptyList()
                        replyStatus = ListStatus.Empty
                    } else {
                        replyFooter = FooterStatus.End
                    }
                } else {
                    firstItem = firstItem ?: rows.first().id
                    lastItem = rows.last().id
                    val decorated = decorate(rows, requestPage)
                    val merged = if (refresh) decorated else replies + decorated
                    val seen = HashSet<String>()
                    replies = merged.filter { it.entityId.isEmpty() || seen.add(it.entityId) }
                    replyStatus = ListStatus.Content
                    replyFooter = FooterStatus.Idle
                    page = requestPage + 1
                    // Only the first arrival jumps; a later refresh keeps the anchor listed but
                    // leaves the reader where they are.
                    if (refresh && anchor != null && !anchorJumped) {
                        anchorJumped = true
                        highlightReplyId = anchor?.id
                        scrollToAnchor = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("FeedDetail", "reply page $requestPage failed", e)
                val message = ApiFailure.from(e).message
                when {
                    refresh && replies.isEmpty() -> replyStatus = ListStatus.Error(message)
                    refresh -> refreshError = message
                    else -> {
                        isEnd = true
                        replyFooter = FooterStatus.Error(message)
                    }
                }
            } finally {
                if (generation == replyGeneration) {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    private fun decorate(rows: List<Entity>, requestPage: Int): List<Entity> {
        val injected = if (requestPage == 1 && listType == "lastupdate_desc") {
            listOfNotNull(anchor, topReply, replyMe)
        } else {
            emptyList()
        }
        return (injected + rows).map { reply ->
            val nested = reply.replyRows
            if (nested.isEmpty()) reply else reply.withReplyRows(nested.filterNot { blacklist.isUserBlocked(it.uid) })
        }
    }

    /** Removes a blocked author from the list, or from one reply's nested preview. */
    fun onBlockReply(uid: String, id: String?) {
        replies = if (id != null) {
            replies.map { if (it.id == id) it.withReplyRows(it.replyRows.filterNot { r -> r.uid == uid }) else it }
        } else {
            replies.filterNot { it.uid == uid }
        }
    }

    fun onDeleteReply(id: String, fid: String?) {
        viewModelScope.launch {
            try {
                val result = repository.deleteReply(id)
                if (result.message.isNotEmpty()) {
                    notice = result.message
                    return@launch
                }
                // A refresh would otherwise list the deleted reply again from the stale anchor.
                if (anchor?.id == id || anchor?.id == fid) anchor = null
                if (fid != null) {
                    replies = replies.map {
                        if (it.id == fid) it.withReplyRows(it.replyRows.filterNot { r -> r.id == id }) else it
                    }
                } else {
                    replies = replies.filterNot { it.id == id }
                }
                if (result.count.isNotEmpty()) notice = result.count
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = ApiFailure.from(e).message
            }
        }
    }

    /** Pins [replyId] as the feed's single top reply, or clears the current pin. */
    fun onTogglePinReply(replyId: String, pinned: Boolean) {
        viewModelScope.launch {
            try {
                val result = repository.pinReply(id, replyId, pinned)
                if (result.message.isNotEmpty()) {
                    notice = result.message
                    return@launch
                }
                topReply = if (pinned) replies.firstOrNull { it.id == replyId } else null
                refreshReplies()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = ApiFailure.from(e).message
            }
        }
    }

    private companion object {
        const val PUBLIC_NOTICE = "正文可正常阅读，当前账号的点赞状态暂不可用。"
        const val UNAVAILABLE_NOTICE = "账号详情暂不可用，请稍后重试"
        val CHALLENGE_KINDS = setOf(ApiFailureKind.Captcha, ApiFailureKind.Authentication)

        /** Nested rows the reply list previews under a comment before 查看更多回复. */
        const val NESTED_PREVIEW = 3

        /** Bounds the climb to the top-level comment should the server ever send a cycle. */
        const val MAX_THREAD_DEPTH = 32
    }
}

private fun Entity.withReplyRows(rows: List<Entity>): Entity =
    Entity(JsonObject(json + ("replyRows" to JsonArray(rows.map { it.json }))))

/** The reply this one answered, or `null` for a top-level comment. */
private fun Entity.parentReplyId(): String? = string("rid").takeIf { it.isNotEmpty() && it != "0" }

/** `replyDetail` sends a reply's pictures only as the comma-joined `pic`; the card reads `picArr`. */
private fun Entity.withPicArr(): Entity {
    if (json["picArr"] is JsonArray) return this
    val pics = pic.split(',').map(String::trim).filter(String::isNotEmpty)
    return Entity(JsonObject(json + ("picArr" to JsonArray(pics.map { JsonPrimitive(it) }))))
}

private fun Entity.withLike(count: String, liked: Boolean): Entity {
    val action = JsonObject(
        (userAction?.json ?: JsonObject(emptyMap())) + ("like" to JsonPrimitive(if (liked) "0" else "1")),
    )
    return Entity(JsonObject(json + ("likenum" to JsonPrimitive(count)) + ("userAction" to action)))
}
