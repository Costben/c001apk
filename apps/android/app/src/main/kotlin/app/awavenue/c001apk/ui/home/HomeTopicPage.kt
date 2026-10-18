package app.awavenue.c001apk.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.carousel.CarouselContent
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TOPIC_LIST_URL = "/v6/page/dataList?url=V11_VERTICAL_TOPIC&title=话题&page=1"

enum class HomeTopicTab { Topic, Product }

/**
 * The home tabs whose body is a category rail next to a vertical page of carousels. The rail and the
 * carousels are one unit: the phone only shows the selected category, so there is no pager to fling.
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
                Modifier
                    .fillMaxSize()
                    // Only the top inset is padding here: the bottom one rides inside the two
                    // scroll areas, so the panel reaches the screen edge behind the floating bar
                    // instead of being cut off above it.
                    .padding(top = contentPadding.calculateTopPadding()),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.22f)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                ) {
                    itemsIndexed(list) { index, entry ->
                        CategoryRow(
                            title = entry.title,
                            selected = index == selectedIndex,
                            onClick = { selected = index },
                        )
                    }
                }
                Box(Modifier.fillMaxHeight().weight(0.78f).background(COUITheme.colorScheme.surfaceContainerHighest)) {
                    CarouselContent(
                        url = current.url,
                        title = current.title,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 12.dp,
                            bottom = contentPadding.calculateBottomPadding() + 12.dp,
                        ),
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(if (selected) colors.primary else Color.Transparent),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (selected) colors.surfaceContainerHighest else Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = if (selected) colors.primary else colors.onSurface,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StateBox(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { content() }
}
