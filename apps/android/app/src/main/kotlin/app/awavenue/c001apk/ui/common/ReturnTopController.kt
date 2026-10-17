package app.awavenue.c001apk.ui.common

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Lets a tapped tab ask the list on screen to jump back to its top. Mirrors the Flutter client's
 * `ReturnTopController`.
 */
@Stable
class ReturnTopController {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: Flow<Unit> get() = requests

    fun requestReturn() {
        requests.tryEmit(Unit)
    }
}
