package app.awavenue.c001apk.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.awavenue.c001apk.data.ProductComparisonSelection
import app.awavenue.c001apk.data.ProductComparisonStore
import app.awavenue.c001apk.data.ProductConfigInfo
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.VerticalDivider
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed interface ProductSheetState {
    data object Hidden : ProductSheetState
    data class Configs(
        val productTitle: String,
        val configs: List<ProductConfigInfo>,
    ) : ProductSheetState

    data class Loading(val title: String) : ProductSheetState
    data class Details(
        val title: String,
        val sections: List<ProductSpecSection>,
    ) : ProductSheetState

    data class Comparison(val columns: List<ProductComparisonColumn>) : ProductSheetState
    data class Error(val title: String, val message: String) : ProductSheetState
}

data class ProductSpecSection(
    val title: String,
    val items: List<ProductSpecItem>,
)

data class ProductSpecItem(
    val name: String,
    val value: String,
    val group: String? = null,
)

data class ProductComparisonColumn(
    val selection: ProductComparisonSelection,
    val sections: List<ProductSpecSection>,
)

// ---- parsing ---------------------------------------------------------------------------------

/**
 * Turns a `product/configInfo` body into aligned sections. The endpoint answers `dataRows`
 * directly, but an envelope-wrapped answer is accepted too.
 */
fun productSpecSections(json: JsonObject): List<ProductSpecSection> {
    val root = (json["data"] as? JsonObject) ?: json
    val dataRows = root["dataRows"] as? JsonObject ?: return emptyList()
    return dataRows.mapNotNull { (title, rowsElement) ->
        val rows = rowsElement as? JsonArray ?: return@mapNotNull null
        val groupPath = mutableListOf<String>()
        val items = buildList {
            rows.forEach { element ->
                val row = element as? JsonObject ?: return@forEach
                when (row.text("type")) {
                    "groupData" -> {
                        val groupName = row.text("keyName").trim()
                        if (groupName.isNotEmpty()) {
                            val depth = (row.text("depth").toIntOrNull() ?: 1).coerceAtLeast(1)
                            while (groupPath.size >= depth) groupPath.removeAt(groupPath.lastIndex)
                            while (groupPath.size < depth - 1) groupPath += ""
                            groupPath += groupName
                        }
                    }

                    "itemData" -> (row["data"] as? JsonObject)
                        ?.toSpecItem(groupPath.asGroup())
                        ?.let(::add)
                }
            }
        }
        items.takeIf { it.isNotEmpty() }?.let {
            ProductSpecSection(title.ifBlank { "参数" }, it)
        }
    }
}

private fun JsonObject.toSpecItem(group: String?): ProductSpecItem? {
    val name = text("title").trim()
    if (name.isEmpty()) return null
    val shown = this["show_value"].displayValue()?.takeIf { it != "-" } ?: return null
    val unit = text("unit").trim()
    val mainValue = if (unit.isEmpty()) shown else shown + unit
    val addition = this["addition_value"].displayValue()?.takeIf { it != "-" && it != mainValue }
    return ProductSpecItem(
        name = name,
        value = listOfNotNull(mainValue, addition).joinToString("\n"),
        group = group,
    )
}

private fun JsonElement?.displayValue(): String? {
    if (this == null || this is JsonNull) return null
    val text = when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> mapNotNull { it.displayValue() }.joinToString(" / ")
        is JsonObject -> entries.mapNotNull { (name, value) ->
            value.displayValue()?.let { "$name: $it" }
        }.joinToString(" / ")
    }
    return text.trim().takeIf { it.isNotEmpty() }
}

private fun JsonObject.text(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun List<String>.asGroup(): String? =
    filter { it.isNotEmpty() }.joinToString(" / ").ifBlank { null }

// ---- sheet -----------------------------------------------------------------------------------

@Composable
fun ProductConfigSheet(
    state: ProductSheetState,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
    onViewConfig: (ProductConfigInfo) -> Unit,
    onToggleCompare: (ProductConfigInfo) -> Unit,
    onOpenComparison: () -> Unit,
    onRetry: () -> Unit,
    onRemoveComparison: (String) -> Unit,
) {
    when (state) {
        ProductSheetState.Hidden -> Unit
        is ProductSheetState.Loading -> LoadingDialog(state.title, onDismiss)
        is ProductSheetState.Error -> ErrorDialog(state.title, state.message, onDismiss, onRetry)
        is ProductSheetState.Configs -> ConfigListDialog(
            state = state,
            selectedIds = selectedIds,
            onDismiss = onDismiss,
            onViewConfig = onViewConfig,
            onToggleCompare = onToggleCompare,
            onOpenComparison = onOpenComparison,
        )

        is ProductSheetState.Details -> DetailsDialog(state, onDismiss)
        is ProductSheetState.Comparison -> ComparisonDialog(state, onDismiss, onRemoveComparison)
    }
}

@Composable
private fun LoadingDialog(title: String, onDismiss: () -> Unit) {
    OverlayDialog(show = true, title = title.ifBlank { "产品参数" }, onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            InfiniteProgressIndicator()
        }
    }
}

@Composable
private fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = title.ifBlank { "加载失败" },
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(text = "重试", onClick = onRetry)
            TextButton(text = "关闭", onClick = onDismiss)
        }
    }
}

@Composable
private fun ConfigListDialog(
    state: ProductSheetState.Configs,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
    onViewConfig: (ProductConfigInfo) -> Unit,
    onToggleCompare: (ProductConfigInfo) -> Unit,
    onOpenComparison: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = state.productTitle.ifBlank { "配置参数" },
        summary = "${state.configs.size} 个配置 · 已选 ${selectedIds.size}/${ProductComparisonStore.MAX_SELECTIONS}",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
            ) {
                items(state.configs, key = { it.id }) { config ->
                    ConfigRow(
                        config = config,
                        selected = config.id in selectedIds,
                        onViewConfig = onViewConfig,
                        onToggleCompare = onToggleCompare,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "关闭", onClick = onDismiss)
                TextButton(
                    text = "对比已选 (${selectedIds.size})",
                    onClick = onOpenComparison,
                )
            }
        }
    }
}

@Composable
private fun ConfigRow(
    config: ProductConfigInfo,
    selected: Boolean,
    onViewConfig: (ProductConfigInfo) -> Unit,
    onToggleCompare: (ProductConfigInfo) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clickable { onViewConfig(config) }) {
            Text(
                text = config.title,
                style = COUITheme.textStyles.body1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = priceLabel(config.price),
                style = COUITheme.textStyles.footnote1,
                color = if (config.price.isPositivePrice()) {
                    COUITheme.colorScheme.error
                } else {
                    COUITheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        TextButton(text = "参数", onClick = { onViewConfig(config) })
        TextButton(
            text = if (selected) "已选" else "对比",
            onClick = { onToggleCompare(config) },
        )
    }
}

@Composable
private fun DetailsDialog(state: ProductSheetState.Details, onDismiss: () -> Unit) {
    OverlayDialog(
        show = true,
        title = state.title.ifBlank { "产品参数" },
        onDismissRequest = onDismiss,
    ) {
        if (state.sections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无参数", color = COUITheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 540.dp)) {
                state.sections.forEach { section ->
                    item(key = "section_${section.title}") {
                        Text(
                            text = section.title,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            style = COUITheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = COUITheme.colorScheme.primary,
                        )
                    }
                    var previousGroup: String? = null
                    section.items.forEachIndexed { index, spec ->
                        if (!spec.group.isNullOrBlank() && spec.group != previousGroup) {
                            item(key = "group_${section.title}_${spec.group}_$index") {
                                Text(
                                    text = spec.group.orEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = 28.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
                                    style = COUITheme.textStyles.footnote1,
                                    fontWeight = FontWeight.SemiBold,
                                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        item(key = "item_${section.title}_${spec.group}_${spec.name}_$index") {
                            SpecRow(spec.name, spec.value)
                        }
                        previousGroup = spec.group
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(name: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(0.38f),
            style = COUITheme.textStyles.body2,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = COUITheme.textStyles.body2,
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun ComparisonDialog(
    state: ProductSheetState.Comparison,
    onDismiss: () -> Unit,
    onRemoveComparison: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(COUITheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(COUIIcons.Close, contentDescription = "关闭")
                    }
                    Text(
                        text = "配置对比",
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${state.columns.size} 款配置",
                        modifier = Modifier.padding(end = 16.dp),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                HorizontalDivider()

                if (state.columns.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无可对比的配置", color = COUITheme.colorScheme.onSurfaceVariantSummary)
                    }
                } else {
                    ComparisonBody(state.columns, onRemoveComparison)
                }
            }
        }
    }
}

@Composable
private fun ComparisonBody(
    columns: List<ProductComparisonColumn>,
    onRemoveComparison: (String) -> Unit,
) {
    val horizontalState = rememberScrollState()
    var hideSame by rememberSaveable { mutableStateOf(false) }
    val sections = remember(columns, hideSame) {
        mergeSections(columns)
            .map { section ->
                if (!hideSame) {
                    section
                } else {
                    section.copy(items = section.items.filter { it.values.hasDifference() })
                }
            }
            .filter { it.items.isNotEmpty() }
    }

    Column(Modifier.fillMaxSize()) {
        ComparisonHeader(
            columns = columns,
            hideSame = hideSame,
            onHideSameChange = { hideSame = it },
            horizontalState = horizontalState,
            onRemoveComparison = onRemoveComparison,
        )
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            sections.forEach { section ->
                item(key = "compare_section_${section.title}") {
                    Text(
                        text = section.title,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        style = COUITheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = COUITheme.colorScheme.primary,
                    )
                }
                var previousGroup: String? = null
                section.items.forEachIndexed { index, item ->
                    if (!item.group.isNullOrBlank() && item.group != previousGroup) {
                        item(key = "compare_group_${section.title}_${item.group}_$index") {
                            ComparisonGroupHeader(item.group.orEmpty(), columns.size, horizontalState)
                        }
                    }
                    item(key = "compare_item_${section.title}_${item.group}_${item.name}_$index") {
                        ComparisonSpecRow(item, horizontalState)
                    }
                    previousGroup = item.group
                }
            }
            item(key = "compare_footer") {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ComparisonHeader(
    columns: List<ProductComparisonColumn>,
    hideSame: Boolean,
    onHideSameChange: (Boolean) -> Unit,
    horizontalState: androidx.compose.foundation.ScrollState,
    onRemoveComparison: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(COMPARISON_HEADER_HEIGHT)) {
        Column(
            modifier = Modifier.width(PARAMETER_COLUMN_WIDTH).fillMaxHeight().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Switch(checked = hideSame, onCheckedChange = onHideSameChange)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "隐藏相同项",
                style = COUITheme.textStyles.footnote2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${columns.size} 款配置",
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
        VerticalDivider(Modifier.fillMaxHeight())
        Row(Modifier.horizontalScroll(horizontalState)) {
            columns.forEach { column ->
                ComparisonHeaderColumn(column, onRemoveComparison)
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun ComparisonHeaderColumn(
    column: ProductComparisonColumn,
    onRemoveComparison: (String) -> Unit,
) {
    val config = column.selection.config
    Box(
        modifier = Modifier.width(VALUE_COLUMN_WIDTH).fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = config.title,
                style = COUITheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = column.selection.productTitle,
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = priceLabel(config.price),
                style = COUITheme.textStyles.footnote1,
                color = if (config.price.isPositivePrice()) {
                    COUITheme.colorScheme.error
                } else {
                    COUITheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        IconButton(
            onClick = { onRemoveComparison(config.id) },
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
        ) {
            Icon(
                COUIIcons.Close,
                contentDescription = "移除",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ComparisonGroupHeader(
    title: String,
    columnCount: Int,
    horizontalState: androidx.compose.foundation.ScrollState,
) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.width(PARAMETER_COLUMN_WIDTH).padding(horizontal = 16.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                style = COUITheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(Modifier.horizontalScroll(horizontalState)) {
            Spacer(Modifier.width(VALUE_COLUMN_WIDTH * columnCount.toFloat()))
        }
    }
    HorizontalDivider()
}

@Composable
private fun ComparisonSpecRow(
    item: ComparisonItem,
    horizontalState: androidx.compose.foundation.ScrollState,
) {
    val differs = item.values.hasDifference()
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(PARAMETER_COLUMN_WIDTH)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = item.name,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Row(Modifier.horizontalScroll(horizontalState)) {
            item.values.forEach { value ->
                val missing = value == MISSING_VALUE
                Box(
                    modifier = Modifier
                        .width(VALUE_COLUMN_WIDTH)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = value,
                        style = COUITheme.textStyles.body2,
                        fontWeight = if (differs && !missing) FontWeight.SemiBold else null,
                        color = when {
                            missing -> COUITheme.colorScheme.onSurfaceVariantSummary
                            differs -> COUITheme.colorScheme.primary
                            else -> COUITheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

// ---- comparison merging ----------------------------------------------------------------------

private data class ComparisonSection(
    val title: String,
    val items: List<ComparisonItem>,
)

private data class ComparisonItem(
    val name: String,
    val values: List<String>,
    val group: String?,
)

private fun mergeSections(columns: List<ProductComparisonColumn>): List<ComparisonSection> {
    val sectionKeys = linkedSetOf<String>()
    columns.forEach { column ->
        column.sections.forEach { sectionKeys += it.title.normalizedSpecKey() }
    }
    val sectionMaps = columns.map { column ->
        column.sections.associateBy { it.title.normalizedSpecKey() }
    }

    return sectionKeys.mapNotNull { sectionKey ->
        val sections = sectionMaps.map { it[sectionKey] }
        val itemKeys = linkedSetOf<String>()
        sections.forEach { section ->
            section?.items?.forEach { itemKeys += it.comparisonKey() }
        }
        val itemMaps = sections.map { section ->
            section?.items.orEmpty().associateBy { it.comparisonKey() }
        }
        val items = itemKeys.map { itemKey ->
            val source = itemMaps.firstNotNullOfOrNull { it[itemKey] }
            ComparisonItem(
                name = source?.name.orEmpty(),
                values = itemMaps.map { it[itemKey]?.value ?: MISSING_VALUE },
                group = source?.group,
            )
        }
        items.takeIf { it.isNotEmpty() }?.let {
            ComparisonSection(
                title = sections.firstNotNullOfOrNull { it?.title }.orEmpty(),
                items = it,
            )
        }
    }
}

private fun ProductSpecItem.comparisonKey(): String =
    "${group.orEmpty().normalizedSpecKey()}\u0000${name.normalizedSpecKey()}"

private fun List<String>.hasDifference(): Boolean =
    size > 1 && map { it.normalizedSpecKey() }.distinct().size > 1

private fun String.normalizedSpecKey(): String =
    trim().replace(Regex("\\s+"), "").lowercase()

private fun String?.isPositivePrice(): Boolean =
    !isNullOrBlank() && (toBigDecimalOrNull()?.signum() ?: 0) > 0

private fun priceLabel(price: String?): String =
    price?.trim()?.takeIf { it.isPositivePrice() }?.let { "¥$it" } ?: "暂无价格"

private const val MISSING_VALUE = "—"
private val PARAMETER_COLUMN_WIDTH = 104.dp
private val VALUE_COLUMN_WIDTH = 148.dp
private val COMPARISON_HEADER_HEIGHT = 190.dp
