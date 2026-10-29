package app.awavenue.c001apk.ui.carousel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.model.CatalogEntry
import app.awavenue.c001apk.ui.common.NetworkImage
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Chip
import io.github.suqi8.coui.kmp.basic.FlowLayout
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme

private val BrandColumnWidth = 64.dp

/**
 * The controls a digital category page opens with: the brands filed under it and the orders it can
 * be listed in. Both are drawn above the list and scroll with it, and neither is an entry of its
 * own, so a sorted answer that omits them keeps them on screen.
 */
@Composable
fun CatalogHeader(
    brands: List<CatalogEntry>,
    sorts: List<CatalogEntry>,
    selectedSort: String?,
    onBrand: (CatalogEntry) -> Unit,
    onSort: (CatalogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (brands.isEmpty() && sorts.isEmpty()) return
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        if (brands.isNotEmpty()) {
            BrandGrid(brands, onBrand)
        }
        if (brands.isNotEmpty() && sorts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
        }
        if (sorts.isNotEmpty()) {
            FlowLayout(itemSpacing = 8.dp, lineSpacing = 8.dp) {
                sorts.forEach { option ->
                    Chip(
                        selected = option.url == selectedSort,
                        onClick = { if (option.url != selectedSort) onSort(option) },
                        label = option.title,
                        minWidth = 0.dp,
                    )
                }
            }
        }
    }
}

/**
 * Brands in a grid as wide as the card allows: two rows until the user asks for the rest, because a
 * category can list dozens and they would otherwise push the list off screen.
 */
@Composable
private fun BrandGrid(brands: List<CatalogEntry>, onBrand: (CatalogEntry) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / BrandColumnWidth).toInt().coerceAtLeast(1)
        val collapsed = columns * 2
        val visible = if (expanded) brands else brands.take(collapsed)
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "品牌",
                    modifier = Modifier.weight(1f),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
                if (brands.size > collapsed) {
                    TextButton(
                        text = if (expanded) "收起" else "全部",
                        onClick = { expanded = !expanded },
                    )
                }
            }
            visible.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { brand ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(role = Role.Button) { onBrand(brand) }
                                .padding(horizontal = 2.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            NetworkImage(
                                brand.logo,
                                Modifier.size(width = 44.dp, height = 26.dp),
                                contentDescription = brand.title,
                                contentScale = ContentScale.Fit,
                            )
                            Text(
                                brand.title,
                                modifier = Modifier.padding(top = 4.dp),
                                style = COUITheme.textStyles.footnote2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
