package app.awavenue.c001apk.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.theme.ChromeDensity
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.TopAppBarState
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The search 首页 and 关注 run in place: the bar's search key stretches into the field, the history
 * fades in under it, and a submitted keyword lists its results on the same layer instead of pushing
 * a search page.
 *
 * The state owns the choices (keyword, content type, order), the pager the results scroll in and the
 * two fades the bar reads while searching, so both pages step back through the search the same way.
 */
@Stable
internal class InPlaceSearchState internal constructor(
    private val expandedState: MutableState<Boolean>,
    private val queryState: MutableState<String>,
    private val submittedState: MutableState<String?>,
    private val searchTypeState: MutableState<SearchType>,
    private val sortTypeState: MutableState<SearchSortType>,
    private val addHistory: (String) -> Unit,
    private val clearFieldFocus: () -> Unit,
    private val hideKeyboard: () -> Unit,
    val tabs: List<SearchContentType>,
    val pagerState: PagerState,
    val scrollState: TopAppBarState,
    val scrollBehavior: ScrollBehavior,
) {
    /** The field is stretched over the bar, or the search key is resting in it. */
    var expanded by expandedState

    /** What the field holds. The results show [submitted] rather than this. */
    var query by queryState

    /** The keyword whose results are on screen; null while the history is. */
    var submitted by submittedState

    var searchType by searchTypeState
    var sortType by sortTypeState

    /**
     * 0 → 1 as the search opens. Read from draw lambdas and graphics layers, never during
     * composition, so opening the search never recomposes the page behind it.
     */
    val searchFade = Animatable(if (expandedState.value) 1f else 0f)

    /** 0 → 1 as the results replace the history. */
    val resultsFade = Animatable(if (submittedState.value != null) 1f else 0f)

    val resultsShown by derivedStateOf { resultsFade.value > 0f }
    val historyGone by derivedStateOf { resultsFade.value >= 1f }

    fun close() {
        query = ""
        submitted = null
        expanded = false
    }

    fun onQueryChange(text: String) {
        query = text
        // Clearing the field goes back to the history.
        if (text.isEmpty()) submitted = null
    }

    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        // The results are what the user is reading now: a field that kept the focus would leave the
        // caret blinking over them.
        clearFieldFocus()
        hideKeyboard()
        addHistory(text)
        query = text
        if (text != submitted) scrollState.contentOffset = 0f
        submitted = text
    }
}

/**
 * The state both pages share, kept across configuration changes: an account switch rebuilds the
 * main screen and with it this state, which is what an account-own search needs.
 */
@Composable
internal fun rememberInPlaceSearchState(): InPlaceSearchState {
    val app = LocalApp.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val expanded = rememberSaveable { mutableStateOf(false) }
    val query = rememberSaveable { mutableStateOf("") }
    // The keyword the results are showing; null while the history is.
    val submitted = rememberSaveable { mutableStateOf<String?>(null) }
    val searchType = rememberSaveable { mutableStateOf(SearchType.All) }
    val sortType = rememberSaveable { mutableStateOf(SearchSortType.Dateline) }
    val tabs = SearchContentType.entries
    val pagerState = rememberPagerState { tabs.size }
    // The results never collapse the bar; the state only tracks how far the list has scrolled,
    // which drives the bar's material the way a stream's scroll does.
    val scrollState = rememberTopAppBarState(initialHeightOffsetLimit = 0f)
    val scrollBehavior = COUIScrollBehavior(state = scrollState)
    val state = remember(expanded, query, submitted, searchType, sortType, pagerState, scrollState) {
        InPlaceSearchState(
            expandedState = expanded,
            queryState = query,
            submittedState = submitted,
            searchTypeState = searchType,
            sortTypeState = sortType,
            addHistory = { app.searchHistory.add(it) },
            clearFieldFocus = { focusManager.clearFocus() },
            hideKeyboard = { keyboard?.hide() },
            tabs = tabs,
            pagerState = pagerState,
            scrollState = scrollState,
            scrollBehavior = scrollBehavior,
        )
    }
    LaunchedEffect(state.expanded) {
        state.searchFade.animateTo(if (state.expanded) 1f else 0f, tween(220))
    }
    LaunchedEffect(state.submitted != null) {
        state.resultsFade.animateTo(if (state.submitted != null) 1f else 0f, tween(220))
    }
    return state
}

/**
 * The bar the search runs in: the round key at rest, the field and the 取消 capsule once expanded.
 *
 * The two back steps that sit above the bar's own live here, registered after it so they win: back
 * from the results returns to the history first, and back with a page pushed over the search leaves
 * that page instead of closing the search under it.
 */
@Composable
internal fun InPlaceSearchBar(
    state: InPlaceSearchState,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    extraActions: (@Composable RowScope.() -> Unit)? = null,
) {
    val navigator = LocalNavigator.current
    ChromeDensity {
        TopAppBarSearchBar(
            query = state.query,
            onQueryChange = state::onQueryChange,
            // The field stays open while a page is pushed over it: coming back from a feed must
            // land on the results exactly as they were, not on a bar that collapsed and reopened.
            expanded = state.expanded,
            onExpandedChange = { state.expanded = it },
            modifier = modifier,
            placeholder = "搜索",
            cancelText = "取消",
            onSearch = state::submit,
            onCancel = state::close,
            searchButtonContentDescription = "搜索",
            backdrop = backdrop,
            // Embedded: the bar's own material is drawn behind it, not a capsule of its own.
            gradientColor = Color.Transparent,
            extraActions = extraActions,
        )
        BackHandler(state.expanded && !navigator.hasOverlay && state.submitted != null) {
            state.submitted = null
        }
        BackHandler(state.expanded && navigator.hasOverlay) { navigator.pop() }
    }
}

/**
 * The history and the results under the expanded field, on a surface of the page's own colour: the
 * list behind them is gone while the search is open, and only the results the user reads show.
 *
 * [topChrome] is the page's own row of content types for a bar that has no second row to put them
 * in: it floats over the results with no band of its own, so a scrolled row passes behind the chips
 * the way it does under a title instead of stopping at a painted edge. It is handed the backdrop the
 * results are recorded into, so a chip can frost what scrolls behind it: the row sits above that
 * layer, and a chip sampling a layer it is drawn into would read it while it is being recorded.
 */
@Composable
internal fun InPlaceSearchPanel(
    state: InPlaceSearchState,
    historyTop: Dp,
    resultContentPadding: PaddingValues,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    topChromeHeight: Dp = 0.dp,
    topChrome: (@Composable (Backdrop?) -> Unit)? = null,
) {
    val surface = COUITheme.colorScheme.surface
    val resultsBackdrop = rememberLayerBackdrop()
    AnimatedVisibility(
        visible = state.expanded,
        modifier = modifier,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(surface)
                // Swallow taps and drags so nothing reaches the list underneath.
                .clickable(interactionSource = null, indication = null) {},
        ) {
            if (!state.historyGone) {
                SearchHistorySection(
                    onPick = state::submit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - state.resultsFade.value }
                        // Straight under the search field, whatever rows the bar carries below it.
                        .padding(top = historyTop)
                        .verticalScroll(rememberScrollState()),
                    bottomPadding = bottomPadding,
                )
            }
            val submitted = state.submitted
            if (submitted != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = state.resultsFade.value },
                ) {
                    // The results are recorded as a layer of their own, with the page's colour under
                    // them: a chip samples it to frost whatever scrolls behind its row. The row itself
                    // is drawn above this box, never inside it.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(surface)
                            .layerBackdrop(resultsBackdrop),
                    ) {
                        SearchResultPager(
                            query = submitted,
                            tabs = state.tabs,
                            pagerState = state.pagerState,
                            searchType = state.searchType,
                            sortType = state.sortType,
                            pageType = null,
                            pageParam = null,
                            scrollBehavior = state.scrollBehavior,
                            contentPadding = resultContentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (topChrome != null) {
                        // The bar keeps the page's colour and the chips float under it: the band wears
                        // off across the chips' own row, so a scrolled row slides in under them instead
                        // of being cut flat at the bar's edge.
                        val band = historyTop + topChromeHeight
                        val opaqueStop = historyTop.value / band.value
                        val clearStop = ((historyTop + CHIP_ROW_FADE).value / band.value)
                            .coerceIn(opaqueStop, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(band)
                                .background(
                                    Brush.verticalGradient(
                                        0f to surface,
                                        opaqueStop to surface,
                                        clearStop to surface.copy(alpha = 0f),
                                        1f to surface.copy(alpha = 0f),
                                    ),
                                ),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            topChrome(resultsBackdrop)
                        }
                    }
                }
            }
        }
    }
}

/** How far past the bar's own colour the chips' band wears off onto the results. */
private val CHIP_ROW_FADE = 24.dp
