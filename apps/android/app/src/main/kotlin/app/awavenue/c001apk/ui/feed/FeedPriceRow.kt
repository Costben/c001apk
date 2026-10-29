package app.awavenue.c001apk.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.model.FeedPrice
import app.awavenue.c001apk.model.TradeLink
import app.awavenue.c001apk.model.purchasePriceSummary
import app.awavenue.c001apk.model.purchasePriceSummaryFeedId
import app.awavenue.c001apk.model.purchasePriceSummaryUpdateTime
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Link
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The price line above a feed card's text: the amount on the left, the configuration beside it and
 * the listing's link on the right. Nothing is drawn when the feed carries neither.
 */
@Composable
fun FeedPriceRow(
    price: FeedPrice?,
    link: TradeLink?,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (price == null && link == null) return
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (price != null) {
            Text(
                price.label,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                price.amount,
                modifier = Modifier.widthIn(max = 148.dp),
                style = COUITheme.textStyles.main.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                color = COUITheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val configuration = price.configuration
            if (configuration != null) {
                Text(
                    configuration,
                    modifier = Modifier.weight(1f),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (link != null) {
            IconButton(
                onClick = { onOpenLink(link.url) },
                minWidth = 40.dp,
                minHeight = 40.dp,
            ) {
                Icon(
                    COUIIcons.Link,
                    contentDescription = link.label,
                    modifier = Modifier.size(22.dp),
                    tint = COUITheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * The `subTabFeedCard6` row: a reference price read from the sub-tab, not an average and not a
 * promise. It names the feed it was taken from and opens it, so the price can be checked.
 */
@Composable
fun PurchasePriceSummaryCard(item: Entity, modifier: Modifier = Modifier) {
    val summary = remember(item) { item.purchasePriceSummary() } ?: return
    val feedId = remember(item) { item.purchasePriceSummaryFeedId() }
    val updated = remember(item) { item.purchasePriceSummaryUpdateTime() }
    val navigator = LocalNavigator.current

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = if (feedId.isEmpty()) null else ({ navigator.push(Route.Feed(feedId)) }),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            FeedPriceRow(summary, null, onOpenLink = {})
            if (updated.isNotEmpty()) {
                Text(
                    "更新于 $updated",
                    modifier = Modifier.padding(top = 2.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (feedId.isNotEmpty()) {
                Text(
                    "查看价格来源",
                    modifier = Modifier.padding(top = 4.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.primary,
                )
            }
        }
    }
}
