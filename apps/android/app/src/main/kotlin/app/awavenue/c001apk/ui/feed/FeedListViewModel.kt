package app.awavenue.c001apk.ui.feed

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awavenue.c001apk.BuildConfig
import app.awavenue.c001apk.data.ContentGate
import app.awavenue.c001apk.model.CatalogEntry
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.model.catalogBrands
import app.awavenue.c001apk.model.catalogSelectedSort
import app.awavenue.c001apk.model.catalogSortField
import app.awavenue.c001apk.model.catalogSorts
import app.awavenue.c001apk.network.ApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed interface ListStatus {
    data object Loading : ListStatus
    data object Content : ListStatus
    data object Empty : ListStatus
    data class Error(val message: String) : ListStatus
}

sealed interface FooterStatus {
    data object Idle : FooterStatus
    data object Loading : FooterStatus
    data object End : FooterStatus
    data class Error(val message: String) : FooterStatus

    /** A bounded batch ended with every row hidden; [count] is how many the rules removed. */
    data class Filtered(val count: Int) : FooterStatus
}

fun interface PageLoader {
    suspend fun load(page: Int, firstItem: String?, lastItem: String?): List<Entity>
}

/**
 * Cursor paging shared by every Coolapk list: `page` advances on success while `firstItem`
 * and `lastItem` carry the ids the server uses to continue the stream.
 *
 * Rows hidden by the block list or a content rule are dropped here rather than in the response
 * parser, so the page that carried them can be topped up from the next one and the list can say
 * how much it hid.
 */
class FeedListViewModel(
    private var loader: PageLoader,
    private val contentGate: () -> ContentGate,
) : ViewModel() {
    var items by mutableStateOf<List<Entity>>(emptyList())
        private set
    var status by mutableStateOf<ListStatus>(ListStatus.Loading)
        private set
    var footer by mutableStateOf<FooterStatus>(FooterStatus.Idle)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    /** The `sortSelectCard` the list opened with; later orders may omit it. */
    var sortCard by mutableStateOf<Entity?>(null)
        private set

    /** The sort option the user picked, or the one the card marked when the list opened. */
    var selectedSort by mutableStateOf<String?>(null)
        private set

    /** The brand row a digital category page carries above its list; empty elsewhere. */
    var catalogBrands by mutableStateOf<List<CatalogEntry>>(emptyList())
        private set

    /** The sort row the same page carries; choosing one reloads the list. */
    var catalogSorts by mutableStateOf<List<CatalogEntry>>(emptyList())
        private set

    /** The catalog sort in effect, by its complete url. */
    var selectedCatalogSort by mutableStateOf<String?>(null)
        private set

    /** Set when a pull-to-refresh fails while older items are still on screen. */
    var refreshError by mutableStateOf<String?>(null)

    /** How many fetched rows the block list and the content rules have hidden since the refresh. */
    var filteredCount by mutableStateOf(0)
        private set

    private var page = 1
    private var firstItem: String? = null
    private var lastItem: String? = null
    private var isEnd = false
    private var isLoading = false
    private var requestId = 0

    init {
        load(isRefresh = true)
    }

    fun refresh() {
        if (isLoading) return
        isRefreshing = items.isNotEmpty()
        load(isRefresh = true)
    }

    /** Reloads the list with another order of the same page, keeping its sort card. */
    fun switchList(loader: PageLoader) {
        this.loader = loader
        items = emptyList()
        status = ListStatus.Loading
        footer = FooterStatus.Idle
        refreshError = null
        isRefreshing = false
        filteredCount = 0
        page = 1
        firstItem = null
        lastItem = null
        isEnd = false
        load(isRefresh = true)
    }

    /** Marks the chosen option on [sortCard]; the caller reloads the list through [switchList]. */
    fun selectSort(title: String) {
        selectedSort = title
    }

    /** Marks the chosen catalog option; the caller reloads the list through [switchList]. */
    fun selectCatalogSort(url: String) {
        selectedCatalogSort = url
    }

    fun loadMore() {
        // A batch that ended all-filtered waits for the reader instead of asking again by itself.
        if (isLoading || isEnd || footer is FooterStatus.Filtered || status != ListStatus.Content) return
        load(isRefresh = false)
    }

    fun retryFooter() {
        isEnd = false
        loadMore()
    }

    /** Continues past a batch that was entirely hidden, from where that batch stopped. */
    fun continueFiltered() {
        if (isLoading || isEnd) return
        if (status != ListStatus.Content) status = ListStatus.Content
        load(isRefresh = false)
    }

    fun removeUserFeeds(uid: String) {
        if (uid.isEmpty()) return
        items = items.filterNot { it.uid == uid }
    }

    fun removeReply(id: String, fid: String?) {
        items = if (fid != null) {
            items.map { if (it.id == fid) it.withReplyRows(it.replyRows.filterNot { row -> row.id == id }) else it }
        } else {
            items.filterNot { it.id == id }
        }
    }

    fun applyLike(id: String, liked: Boolean, count: String) {
        items = items.map { if (it.id == id) it.withLike(count.ifEmpty { it.likeNum }, liked) else it }
    }

    private fun load(isRefresh: Boolean) {
        val id = ++requestId
        isLoading = true
        val startPage = if (isRefresh) 1 else page
        // The list's own first id, which every request of this list carries. A refresh starts over,
        // so it stays null until the first page of the batch arrives.
        val listFirst = if (isRefresh) null else firstItem
        var requestPage = startPage
        var requestFirst = listFirst
        var requestLast = if (isRefresh) null else lastItem
        if (isRefresh && items.isEmpty()) status = ListStatus.Loading
        if (!isRefresh) footer = FooterStatus.Loading
        viewModelScope.launch {
            try {
                val gate = contentGate()
                val collected = mutableListOf<Entity>()
                var hidden = 0
                var requests = 0
                var reachedEnd = false
                var cursorFirst: String? = null
                var cursorLast: String? = null
                while (true) {
                    val result = loader.load(requestPage, requestFirst, requestLast)
                    // A switched list cancels the answers the previous order still has in flight.
                    if (id != requestId) return@launch
                    requests++
                    // A digital category page carries its brand and sort controls with the page itself;
                    // later pages and sorted answers may omit them, so the last seen rows are kept.
                    result.firstNotNullOfOrNull { it.catalogBrands() }?.let { catalogBrands = it }
                    result.firstNotNullOfOrNull { it.catalogSorts() }?.let { sorts ->
                        catalogSorts = sorts
                        if (selectedCatalogSort == null) {
                            val marked = result.firstNotNullOfOrNull {
                                it.catalogSelectedSort().takeIf { value -> value.isNotEmpty() }
                            }
                            val field = marked?.catalogSortField()
                            selectedCatalogSort = sorts.firstOrNull { it.title == marked }?.url
                                ?: sorts.firstOrNull { it.url == marked }?.url
                                ?: sorts.firstOrNull { field != null && it.url.catalogSortField() == field }?.url
                                ?: sorts.firstOrNull()?.url
                        }
                    }
                    // The order card only comes with the first order the server sent. Keep it so the
                    // list can still offer the other orders after switching away from that url.
                    result.firstOrNull { it.entityTemplate == "sortSelectCard" && it.catalogSorts() == null }?.let { card ->
                        sortCard = card
                        if (selectedSort == null) {
                            selectedSort = card.nested("extraData")?.string("selectedTab")
                        }
                    }
                    // Both catalog rows are drawn by the list's header, not as entries of their own.
                    val content = result.filterNot {
                        it.entityTemplate == "sortSelectCard" || it.catalogBrands() != null
                    }
                    // The cursor follows the page the server sent, not the rows left after filtering,
                    // so a hidden tail is never fetched twice.
                    if (content.isNotEmpty()) {
                        if (cursorFirst == null) cursorFirst = content.first().id
                        cursorLast = content.last().id
                    }
                    val visible = content.filter { gate.isVisible(it) }
                    hidden += content.size - visible.size
                    collected += visible
                    if (content.isEmpty()) {
                        reachedEnd = true
                        break
                    }
                    if (visible.isNotEmpty()) break
                    // The whole page was hidden: top it up from the next one, but only for a bounded
                    // number of requests, so a rule that matches everything cannot spin the network.
                    if (requests >= FilteredBatchRequests) break
                    requestPage = startPage + requests
                    // `listFirst` is the list's first id, not the first id of this batch's page.
                    requestFirst = listFirst ?: cursorFirst
                    requestLast = cursorLast
                }
                if (id != requestId) return@launch
                if (isRefresh) {
                    page = 1
                    firstItem = null
                    lastItem = null
                    isEnd = false
                    filteredCount = hidden
                } else {
                    filteredCount += hidden
                }
                if (firstItem == null) firstItem = cursorFirst
                cursorLast?.let { lastItem = it }
                page = startPage + requests
                when {
                    collected.isNotEmpty() -> {
                        val merged = if (isRefresh) collected else items + collected
                        val seen = HashSet<String>()
                        // Contact rows share their own entityId (the signed-in account); the followed
                        // side is what tells them apart.
                        items = merged.filter { item ->
                            val key = if (item.entityType == "contacts") item.contactUid else item.entityId
                            key.isEmpty() || seen.add(key)
                        }
                        status = ListStatus.Content
                        isEnd = reachedEnd
                        footer = if (reachedEnd) FooterStatus.End else FooterStatus.Idle
                    }
                    reachedEnd -> {
                        isEnd = true
                        if (isRefresh) {
                            items = emptyList()
                            status = ListStatus.Empty
                            footer = FooterStatus.Idle
                        } else {
                            footer = FooterStatus.End
                        }
                    }
                    else -> {
                        // The batch stopped with nothing left to show; the reader decides to go on.
                        if (isRefresh) {
                            items = emptyList()
                            status = ListStatus.Content
                        }
                        footer = FooterStatus.Filtered(filteredCount)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id != requestId) return@launch
                if (BuildConfig.DEBUG) Log.w("FeedList", "page $startPage failed", e)
                val message = ApiFailure.from(e).message
                when {
                    isRefresh && items.isEmpty() -> status = ListStatus.Error(message)
                    isRefresh -> refreshError = message
                    else -> {
                        isEnd = true
                        footer = FooterStatus.Error(message)
                    }
                }
            } finally {
                if (id == requestId) {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }
}

private fun Entity.withReplyRows(rows: List<Entity>): Entity =
    Entity(JsonObject(json + ("replyRows" to JsonArray(rows.map { it.json }))))
private fun Entity.withLike(count: String, liked: Boolean): Entity {
    val action = JsonObject(
        (userAction?.json ?: JsonObject(emptyMap())) + ("like" to JsonPrimitive(if (liked) "0" else "1")),
    )
    return Entity(JsonObject(json + ("likenum" to JsonPrimitive(count)) + ("userAction" to action)))
}

/** Requests one load may spend topping up a page whose rows were all hidden. */
private const val FilteredBatchRequests = 3
