package app.awavenue.c001apk.ui.search

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.appDialogButtonColors
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.LocalRouteTransition
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageScrollState
import io.github.suqi8.coui.kmp.basic.FlowLayout
import io.github.suqi8.coui.kmp.basic.SearchHistoryChip
import io.github.suqi8.coui.kmp.basic.SearchHistoryViewDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Clear
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog

/**
 * The search page in COUISearchBar's edit state: one field capsule across the header.
 *
 * The bar carries neither a back key nor the "取消" capsule: leaving is the system back gesture and
 * submitting is the keyboard's search action, so the field takes the whole width the bar has
 * between its 16dp margins.
 *
 * The caustic light under the capsule is `coui_caustic_color_1`, clipped to the outside of the
 * outline so it never brightens the translucent fill. The device only lights the bar while it is
 * being edited, so the light ramps in with the page instead of snapping on.
 */
@OptIn(ExperimentalCOUIApi::class)
@Composable
fun SearchScreen(title: String, pageType: String?, pageParam: String?) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    // True when this page was composed only to be revealed under a back gesture: a preview of the
    // page the pop will land on, not the page the user is on, so it must not open the keyboard.
    // Constant for this instance: the field clears its text when it collapses, so this cannot be
    // something that changes while the page lives.
    val preview = LocalRouteTransition.current?.preview == true
    var keyword by remember { mutableStateOf("") }

    // The device only lights the bar while it is being edited, so the caustic light ramps in with
    // the page instead of snapping on.
    val glowProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { glowProgress.animateTo(1f, tween(300)) }
    val causticColor = SearchBarDefaults.causticGlowColor()
    val glowColor = causticColor.copy(alpha = causticColor.alpha * glowProgress.value)

    fun submit(text: String) {
        if (text.isEmpty()) return
        app.searchHistory.add(text)
        navigator.push(
            Route.SearchResult(
                keyword = text,
                title = title,
                pageType = pageType,
                pageParam = pageParam,
            ),
        )
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InputField(
                        query = keyword,
                        onQueryChange = { keyword = it },
                        onSearch = { submit(keyword.trim()) },
                        expanded = !preview,
                        onExpandedChange = { },
                        label = if (title.isNotEmpty()) "在 $title 中搜索" else "搜索",
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = glowColor,
                    )
                }
            }
        },
    ) { innerPadding ->
        SearchHistorySection(
            onPick = { text ->
                keyword = text
                submit(text)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberPageScrollState("search")),
            bottomPadding = innerPadding.calculateBottomPadding(),
        )
    }
}

/**
 * The 搜索历史 chips with their 清除全部 key, shared by the search page and the home bar's in-place
 * search. Nothing is drawn while the history is empty.
 *
 * Long-pressing a chip springs a ✕ out of its right side; tapping the ✕ deletes that keyword, and
 * tapping the chip again (or long-pressing another one) puts the ✕ away.
 */
@Composable
fun SearchHistorySection(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val app = LocalApp.current
    val history = app.searchHistory.keywords
    var showClearDialog by remember { mutableStateOf(false) }
    // The chip whose ✕ is out. Only one at a time, so a long press elsewhere moves it.
    var deleting by remember { mutableStateOf<String?>(null) }

    if (history.isNotEmpty()) {
        Column(modifier) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "搜索历史",
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(COUIIcons.Clear, contentDescription = "清除全部")
                }
            }
            FlowLayout(
                modifier = Modifier.fillMaxWidth().padding(
                    start = 10.dp,
                    end = 10.dp,
                    bottom = 10.dp + bottomPadding,
                ),
                itemSpacing = SearchHistoryViewDefaults.FlowItemSpacing,
                lineSpacing = SearchHistoryViewDefaults.FlowLineSpacing,
            ) {
                history.forEach { text ->
                    key(text) {
                        // COUI's search-history chip: a long press springs its ✕ out while the
                        // chip widens; only one is out at a time.
                        SearchHistoryChip(
                            text = text,
                            onClick = { onPick(text) },
                            onDelete = {
                                deleting = null
                                app.searchHistory.remove(text)
                            },
                            deleteMode = deleting == text,
                            onDeleteModeChange = { on -> deleting = if (on) text else null },
                            deleteContentDescription = "删除 $text",
                        )
                    }
                }
            }
        }
    }

    OverlayAlertDialog(
        buttonColors = appDialogButtonColors(),
        show = showClearDialog,
        title = "确定清除全部搜索历史？",
        confirmText = "确定",
        cancelText = "取消",
        onCancel = { showClearDialog = false },
        onConfirm = {
            showClearDialog = false
            deleting = null
            app.searchHistory.clear()
        },
    )
}
