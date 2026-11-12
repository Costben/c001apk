package app.awavenue.c001apk.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /** Set once the list under a popped entry has been scrolled clear of the app bar. */
    internal var returnScrolled = false

    /** Set once the row that opened this entry has been moved clear of the bar on the way in. */
    internal var alignScheduled = false

    /** Set when a back gesture had already pulled the screen away before it was popped. */
    internal var previewed = false

    /**
     * What the screen looked like when another one was stacked over it, kept while it is covered. A
     * back gesture reveals the screen under the one it pulls away from this picture: composing the
     * screen again would show every list at the top, and it would jump to where the user left it
     * once the gesture is let go.
     *
     * The picture is taken once the press feedback of the tap that opened the page above has sprung
     * out, so a reveal does not bring back an overlay that was already gone.
     */
    internal var snapshot: ImageBitmap? = null

    /**
     * What the screen's `rememberSaveable` state (list positions, selected tabs) held when it left
     * composition under a route pushed on top, so it comes back where the user left it.
     */
    internal var savedState: Map<String, List<Any?>>? = null

    /**
     * The list positions and bar offsets the screen left behind, kept here because the saved state
     * above only restores in the composition slot it was filed from, and the page under a back
     * gesture is composed somewhere else.
     */
    internal val memory = PageMemory()

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

    /** Timestamp of the most recent pop, used to debounce rapid repeated back gestures. */
    var lastPopTime: Long = 0L
        private set

    /** True while a route is stacked above the main screen. */
    val hasOverlay: Boolean get() = stack.size > 1

    /**
     * What back does on the main screen. Returns false when there is nothing left for the main
     * screen to go back to.
     */
    var onMainBack: (() -> Boolean)? = null

    /**
     * Whether back on the main screen still has something to do there — returning to the 主页 tab.
     *
     * When it is false the host stops consuming back at the root, so the system runs its own
     * predictive "return to home": the window animates out to the launcher. Intercepting that
     * gesture was what made back look like the app vanishing with no motion at all.
     */
    var mainBackAvailable: Boolean by mutableStateOf(false)

    /** [origin] is the element the page grows out of, and shrinks back into on pop. */
    fun push(route: Route, origin: ExpandOrigin? = null) {
        if (route is Route.Feed) trimReadingStack()
        stack.add(BackStackEntry(route, origin))
    }

    /**
     * Reading a feed can lead to another one, and the stack would otherwise grow for as long as the
     * reader keeps tapping. The oldest reading page drops off; the main screen at the bottom and
     * everything opened from a feed stay.
     */
    private fun trimReadingStack() {
        val reading = stack.filter { it.route is Route.Feed }
        if (reading.size < MaxReadingStack) return
        val oldest = reading.first()
        stack.remove(oldest)
        oldest.remove()
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
        lastPopTime = System.currentTimeMillis()
        stack[stack.lastIndex].popped = true
        stack.removeAt(stack.lastIndex).remove()
        return true
    }

    fun popToMain() {
        if (stack.size > 1) {
            lastPopTime = System.currentTimeMillis()
        }
        while (stack.size > 1) {
            stack[stack.lastIndex].popped = true
            stack.removeAt(stack.lastIndex).remove()
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/**
 * The main tabs' pager. A tab reads it to tell whether it is the one on screen — 关注 keeps its
 * in-place search only while it is, so a search left open behind a tab switch cannot take the back
 * press meant for the tab the user actually went to.
 */
val LocalMainPager = staticCompositionLocalOf<PagerState?> { null }

/** How many reading pages the back stack keeps; older ones drop off as new ones open. */
private const val MaxReadingStack = 8

@Composable
fun rememberNavigator(): Navigator = androidx.compose.runtime.remember { Navigator() }
