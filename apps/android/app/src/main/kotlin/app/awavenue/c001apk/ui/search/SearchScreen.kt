package app.awavenue.c001apk.ui.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.appDialogButtonColors
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageScrollState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.FlowLayout
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.SearchBarActionButtonDefaults
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Clear
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

/** COUIMoveEaseInterpolator: PathInterpolator(0.3f, 0f, 0.1f, 1f). */
private val MoveEase = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/**
 * The search page in COUISearchBar's edit state: the field capsule on the start side, the "取消"
 * capsule on the end side. There is no back key in this bar — the cancel capsule is the way out,
 * which is what ColorOS does once the search bar owns the whole header.
 *
 * Geometry is the expanded search bar's: 16dp margins, the 40dp field, and the 12dp gap before the
 * cancel capsule. The field is the flexible child, so the cancel capsule growing in on entry
 * shrinks the field exactly like the device's `doWidthChangedAnimate` (450ms); the capsule itself
 * scales up from 0.5 with COUI's fun-button spring and fades in over 350ms.
 *
 * The caustic light under both capsules is `coui_caustic_color_1`, clipped to the outside of each
 * outline so it never brightens the translucent fill. The device only lights the bar while it is
 * being edited, so the light ramps in with the page instead of snapping on.
 */
@OptIn(ExperimentalCOUIApi::class)
@Composable
fun SearchScreen(title: String, pageType: String?, pageParam: String?) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    var keyword by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    val history = app.searchHistory.keywords

    // The page is composed already in the edit state, so the entrance has to be driven by hand: the
    // slot widens over 450ms (the device's `doWidthChangedAnimate`) while the field beside it gives
    // up exactly that width, and the capsule rides in from the end edge on the same curve.
    val slotProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { slotProgress.animateTo(1f, tween(450, easing = MoveEase)) }
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
                        expanded = true,
                        onExpandedChange = { },
                        label = if (title.isNotEmpty()) "在 $title 中搜索" else "搜索",
                        modifier = Modifier.weight(1f),
                        glowColor = glowColor,
                    )
                    // The gap rides inside the slot, so the field keeps the whole width while the
                    // capsule is still off the end edge.
                    CancelSlot(progress = slotProgress.value) {
                        SearchBarActionButton(
                            text = "取消",
                            onClick = { navigator.pop() },
                            glowColor = glowColor,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        if (history.isEmpty()) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberPageScrollState("search")),
        ) {
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
                    bottom = 10.dp + innerPadding.calculateBottomPadding(),
                ),
                itemSpacing = 8.dp,
                lineSpacing = 8.dp,
            ) {
                history.forEach { text ->
                    Card(
                        cornerRadius = 20.dp,
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        onClick = {
                            keyword = text
                            submit(text)
                        },
                        onLongPress = { app.searchHistory.remove(text) },
                    ) {
                        Text(text, maxLines = 1, style = COUITheme.textStyles.footnote1)
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
            app.searchHistory.clear()
        },
    )
}

/**
 * The slot the cancel capsule rides in through.
 *
 * An `expandHorizontally` transition crops its content while the width animates, and a cropped
 * caustic light reads as a grey rectangle on the frames before the capsule is at full width. This
 * slot instead reports a width that grows with [progress] — the flexible field beside it gives up
 * exactly that width, like the device's `doWidthChangedAnimate` — while the capsule itself keeps its
 * natural size and is placed so that its end edge trails the bar's end edge by the remaining
 * `1 - progress`. Nothing is clipped and no layer is created, so the light stays welded to the
 * capsule at every frame.
 */
@Composable
private fun CancelSlot(
    progress: Float,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        measurePolicy = { measurables, _ ->
            val capsule = measurables.first().measure(Constraints())
            val gap = (SearchBarActionButtonDefaults.StartGap.toPx() * progress).roundToInt()
            val slot = (capsule.width * progress).roundToInt()
            layout(width = gap + slot, height = capsule.height) {
                capsule.place(gap, 0)
            }
        },
    )
}
