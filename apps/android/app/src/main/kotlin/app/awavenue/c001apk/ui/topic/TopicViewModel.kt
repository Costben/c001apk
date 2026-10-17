package app.awavenue.c001apk.ui.topic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The topic/product header. It lives on the back-stack entry, so opening a post on top of this page
 * and coming back keeps the loaded header instead of dropping it and showing a spinner again.
 */
class TopicViewModel(
    private val repository: CoolapkRepository,
    private val tag: String?,
    private val id: String?,
) : ViewModel() {
    var detail by mutableStateOf<Entity?>(null)
        private set

    var failure by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        failure = null
        viewModelScope.launch {
            try {
                detail = if (!tag.isNullOrEmpty()) {
                    repository.topicDetail(tag)
                } else {
                    repository.productDetail(id.orEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = ApiFailure.from(e).message
            }
        }
    }
}
