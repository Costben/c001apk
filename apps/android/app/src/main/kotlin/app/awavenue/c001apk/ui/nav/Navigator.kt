package app.awavenue.c001apk.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * One place in the back stack. It owns the ViewModels of the screen it shows, so they live as long
 * as the entry is stacked — across a route pushed on top of it — and are cleared once it is popped.
 * Entries compare by identity, so the same route pushed twice gets two independent screens.
 */
class BackStackEntry internal constructor(
    val route: Route,
    internal val origin: ExpandOrigin? = null,
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    /** Set once the screen has entered, so returning to it from a page above does not grow it again. */
    internal var shown = false

    /** Set when back takes the entry off the stack, as opposed to a replace swapping it out. */
    internal var popped = false

    /** Set when a back gesture had already pulled the screen away before it was popped. */
    internal var previewed = false

    /**
     * What the screen looked like when another one was stacked over it, kept while it is covered. A
     * back gesture reveals the screen under the one it pulls away from this picture: composing the
     * screen again would show every list at the top, and it would jump to where the user left it
     * once the gesture is let go.
     */
    internal var snapshot: ImageBitmap? = null

    /**
     * What the screen's `rememberSaveable` state (list positions, selected tabs) held when it left
     * composition under a route pushed on top, so it comes back where the user left it.
     */
    internal var savedState: Map<String, List<Any?>>? = null

    private var hosts = 0
    private var removed = false

    /** Called while the entry's screen is composed, including during its exit animation. */
    internal fun attach() {
        hosts++
    }

    internal fun detach() {
        hosts--
        clearIfGone()
    }

    internal fun remove() {
        removed = true
        clearIfGone()
    }

    // A popped screen is still composed while it animates out; clearing then would let it
    // recreate its ViewModels in the emptied store.
    private fun clearIfGone() {
        if (removed && hosts == 0) viewModelStore.clear()
    }
}

/**
 * A plain back stack. The main screen stays composed at the bottom of the stack so its tabs keep
 * their lists and scroll positions, which is what the Flutter client's navigator does too.
 */
class Navigator {
    val stack: SnapshotStateList<BackStackEntry> = mutableStateListOf(BackStackEntry(Route.Main))

    /** True while a route is stacked above the main screen. */
    val hasOverlay: Boolean get() = stack.size > 1

    /**
     * What back does on the main screen. Returns false when the app should go to the background
     * instead — the Flutter client sends the app home rather than exiting.
     */
    var onMainBack: (() -> Boolean)? = null

    /** [origin] is the element the page grows out of, and shrinks back into on pop. */
    fun push(route: Route, origin: ExpandOrigin? = null) {
        stack.add(BackStackEntry(route, origin))
    }

    /** Replaces the top route, so a search can turn into a result without stacking twice. */
    fun replace(route: Route) {
        if (stack.size > 1) {
            val old = stack[stack.lastIndex]
            stack[stack.lastIndex] = BackStackEntry(route)
            old.remove()
        } else {
            push(route)
        }
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack[stack.lastIndex].popped = true
        stack.removeAt(stack.lastIndex).remove()
        return true
    }

    fun popToMain() {
        while (stack.size > 1) {
            stack[stack.lastIndex].popped = true
            stack.removeAt(stack.lastIndex).remove()
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

@Composable
fun rememberNavigator(): Navigator = androidx.compose.runtime.remember { Navigator() }
