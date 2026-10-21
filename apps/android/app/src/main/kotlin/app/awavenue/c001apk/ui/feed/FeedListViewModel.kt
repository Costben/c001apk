package app.awavenue.c001apk.ui.feed

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awavenue.c001apk.BuildConfig
import app.awavenue.c001apk.model.Entity
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
}

fun interface PageLoader {
    suspend fun load(page: Int, firstItem: String?, lastItem: String?): List<Entity>
}

/**
 * Cursor paging shared by every Coolapk list: `page` advances on success while `firstItem`
 * and `lastItem` carry the ids the server uses to continue the stream.
 */
class FeedListViewModel(private var loader: PageLoader) : ViewModel() {
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

    /** Set when a pull-to-refresh fails while older items are still on screen. */
    var refreshError by mutableStateOf<String?>(null)

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

    fun loadMore() {
        if (isLoading || isEnd || status != ListStatus.Content) return
        load(isRefresh = false)
    }

    fun retryFooter() {
        isEnd = false
        loadMore()
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
        val requestPage = if (isRefresh) 1 else page
        val requestFirst = if (isRefresh) null else firstItem
        val requestLast = if (isRefresh) null else lastItem
        if (isRefresh && items.isEmpty()) status = ListStatus.Loading
        if (!isRefresh) footer = FooterStatus.Loading
        viewModelScope.launch {
            try {
                val result = loader.load(requestPage, requestFirst, requestLast)
                // A switched list cancels the answers the previous order still has in flight.
                if (id != requestId) return@launch
                if (isRefresh) {
                    page = 1
                    firstItem = null
                    lastItem = null
                    isEnd = false
                }
                // The order card only comes with the first order the server sent. Keep it so the
                // list can still offer the other orders after switching away from that url.
                result.firstOrNull { it.entityTemplate == "sortSelectCard" }?.let { card ->
                    sortCard = card
                    if (selectedSort == null) {
                        selectedSort = card.nested("extraData")?.string("selectedTab")
                    }
                }
                val content = result.filterNot { it.entityTemplate == "sortSelectCard" }
                if (content.isEmpty()) {
                    isEnd = true
                    if (isRefresh) {
                        items = emptyList()
                        status = ListStatus.Empty
                    } else {
                        footer = FooterStatus.End
                    }
                } else {
                    firstItem = firstItem ?: content.first().id
                    lastItem = content.last().id
                    val merged = if (isRefresh) content else items + content
                    val seen = HashSet<String>()
                    // Contact rows share their own entityId (the signed-in account); the followed
                    // side is what tells them apart.
                    items = merged.filter { item ->
                        val key = if (item.entityType == "contacts") item.contactUid else item.entityId
                        key.isEmpty() || seen.add(key)
                    }
                    status = ListStatus.Content
                    footer = FooterStatus.Idle
                    page = requestPage + 1
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id != requestId) return@launch
                if (BuildConfig.DEBUG) Log.w("FeedList", "page $requestPage failed", e)
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
