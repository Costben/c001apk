package app.awavenue.c001apk.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.carousel.CarouselContent
import app.awavenue.c001apk.ui.common.rememberHapticClick
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TOPIC_LIST_URL = "/v6/page/dataList?url=V11_VERTICAL_TOPIC&title=话题&page=1"

private val RailWidth = 84.dp
private val RailItemHeight = 42.dp
private val RailLabelInset = 22.dp
private val RailIndicatorInset = 10.dp

/** Trailing space the rail needs so its last rows can clear the floating bottom bar. */
private val RailBottomInset = 96.dp

enum class HomeTopicTab { Topic, Product }

/**
 * The home tabs whose body is a category selector above a vertical page of carousels. The selector
 * and the carousels are one unit: the phone only shows the selected category, so there is no pager
 * to fling.
 */
@Composable
fun HomeTopicPage(tabType: HomeTopicTab, scrollBehavior: ScrollBehavior, contentPadding: PaddingValues) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var entries by remember(tabType) { mutableStateOf<List<Entity>?>(null) }
    var error by remember(tabType) { mutableStateOf<String?>(null) }
    var selected by remember(tabType) { mutableIntStateOf(if (tabType == HomeTopicTab.Topic) 1 else 0) }

    suspend fun load() {
        error = null
        try {
            val data = when (tabType) {
                HomeTopicTab.Topic -> app.repository.listFromUrl(TOPIC_LIST_URL).firstOrNull()?.entities
                HomeTopicTab.Product -> app.repository.productCategoryList()
            }.orEmpty()
            entries = data
            selected = selected.coerceAtMost((data.size - 1).coerceAtLeast(0))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = ApiFailure.from(e).message
        }
    }

    LaunchedEffect(tabType) { load() }

    val list = entries
    when {
        error != null -> StateBox(contentPadding) {
            EmptyState(title = "加载失败", subtitle = error, actionText = "重试", onActionClick = { scope.launch { load() } })
        }

        list == null -> StateBox(contentPadding) { InfiniteProgressIndicator() }

        list.isEmpty() -> StateBox(contentPadding) {
            EmptyState(title = "暂无内容", actionText = "刷新", onActionClick = { scope.launch { load() } })
        }

        else -> {
            val selectedIndex = if (selected in list.indices) selected else 0
            val current = list[selectedIndex]
            Row(
                Modifier.fillMaxSize(),
            ) {
                CategoryRail(
                    titles = list.map { it.title },
                    selectedIndex = selectedIndex,
                    onSelected = { selected = it },
                    scrollBehavior = scrollBehavior,
                    topInset = contentPadding.calculateTopPadding(),
                )
                CarouselContent(
                    url = current.url,
                    title = current.title,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(
                        // The list keeps the bar's height as leading space so its first card starts
                        // below the bar, while the list itself runs under the bar and scrolls away
                        // behind it.
                        top = contentPadding.calculateTopPadding() + 4.dp,
                        bottom = contentPadding.calculateBottomPadding() + 12.dp,
                    ),
                    scrollBehavior = scrollBehavior,
                    // The rail already supplies the left margin, so the cards take less inset than
                    // a full-width stream does and read wider beside it.
                    itemPadding = 6.dp,
                )
            }
        }
    }
}

/**
 * The vertical category list on the left. The home bar above already carries the tab row, so this
 * is the page's only navigator: a second row of tabs would just repeat it.
 */
@Composable
private fun CategoryRail(
    titles: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    scrollBehavior: ScrollBehavior,
    topInset: Dp,
) {
    val listState = rememberLazyListState()
    val topInsetPx = with(LocalDensity.current) { topInset.roundToPx() }
    LaunchedEffect(selectedIndex, topInsetPx) {
        if (selectedIndex in titles.indices) {
            // Keep the selected row clear of the bar: a plain scroll-to-item would park it at the
            // very top, behind the material.
            listState.animateScrollToItem(selectedIndex, scrollOffset = -topInsetPx)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .width(RailWidth)
            .fillMaxHeight()
            // The rail scrolls the page header like the list beside it, so a drag that starts on
            // the categories collapses the bar exactly the way the home feed does.
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // The rail runs under the bar and to the bottom edge, so its tail rides behind the
        // floating bar and needs the bar's height as trailing space to stay reachable.
        contentPadding = PaddingValues(top = topInset, bottom = RailBottomInset),
    ) {
        itemsIndexed(titles) { index, title ->
            RailItem(
                title = title,
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
            )
        }
    }
}

@Composable
private fun RailItem(title: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(160),
        label = "railPress",
    )
    val accent = COUITheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(RailItemHeight)
            .clip(RoundedCornerShape(topEnd = 13.dp, bottomEnd = 13.dp))
            // A ripple would paint a square over the row, so the press feedback is the same
            // translucent pill the rest of the app uses.
            .background(COUITheme.colorScheme.onSurface.copy(alpha = 0.08f * pressAlpha))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = rememberHapticClick(onClick = onClick),
            ),
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = RailIndicatorInset)
                    .width(3.dp)
                    .height(18.dp)
                    .background(accent, RoundedCornerShape(2.dp)),
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = RailLabelInset, end = 8.dp),
            style = COUITheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (selected) accent else COUITheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StateBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { content() }
}
