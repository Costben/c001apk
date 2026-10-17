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
class FeedListViewModel(private val loader: PageLoader) : ViewModel() {
    var items by mutableStateOf<List<Entity>>(emptyList())
        private set
    var status by mutableStateOf<ListStatus>(ListStatus.Loading)
        private set
    var footer by mutableStateOf<FooterStatus>(FooterStatus.Idle)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    /** Set when a pull-to-refresh fails while older items are still on screen. */
    var refreshError by mutableStateOf<String?>(null)

    private var page = 1
    private var firstItem: String? = null
    private var lastItem: String? = null
    private var isEnd = false
    private var isLoading = false

    init {
        load(isRefresh = true)
    }

    fun refresh() {
        if (isLoading) return
        isRefreshing = items.isNotEmpty()
        load(isRefresh = true)
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
        isLoading = true
        val requestPage = if (isRefresh) 1 else page
        val requestFirst = if (isRefresh) null else firstItem
        val requestLast = if (isRefresh) null else lastItem
        if (isRefresh && items.isEmpty()) status = ListStatus.Loading
        if (!isRefresh) footer = FooterStatus.Loading
        viewModelScope.launch {
            try {
                val result = loader.load(requestPage, requestFirst, requestLast)
                if (isRefresh) {
                    page = 1
                    firstItem = null
                    lastItem = null
                    isEnd = false
                }
                if (result.isEmpty()) {
                    isEnd = true
                    if (isRefresh) {
                        items = emptyList()
                        status = ListStatus.Empty
                    } else {
                        footer = FooterStatus.End
                    }
                } else {
                    firstItem = firstItem ?: result.first().id
                    lastItem = result.last().id
                    val merged = if (isRefresh) result else items + result
                    val seen = HashSet<String>()
                    items = merged.filter { it.entityId.isEmpty() || seen.add(it.entityId) }
                    status = ListStatus.Content
                    footer = FooterStatus.Idle
                    page = requestPage + 1
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
                isLoading = false
                isRefreshing = false
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
