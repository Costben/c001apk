package app.awavenue.c001apk.model

/** A price line on a feed card: what it is, the amount, and the configuration it belongs to. */
class FeedPrice(val label: String, val amount: String, val configuration: String?)

/** An outside listing behind a second-hand feed. */
class TradeLink(val label: String, val url: String, val isXianyu: Boolean)

private val AmountPattern = Regex("""[0-9]{1,12}(\.[0-9]{1,2})?""")

/**
 * Missing, zero and malformed amounts are hidden rather than read as free or as a negotiated
 * price: the fields arrive as numbers, strings and empty strings in the same list.
 */
private fun String?.asPrice(): String? {
    val value = this?.trim().orEmpty()
    if (!AmountPattern.matches(value)) return null
    val amount = value.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
    return "¥" + amount.stripTrailingZeros().toPlainString()
}

/** `tsubdata`, `ershou_info` and `extraData` arrive as objects or as JSON held in a string. */
private fun Entity.embedded(key: String): Entity? = obj(key) ?: nested(key)

/**
 * The price a feed card shows. `tsubid` 6 marks a feed read through the purchase-price tab, whose
 * price sits in `tsubdata`; second-hand and trade feeds carry theirs in `ershou_info`.
 */
fun Entity.priceDisplay(): FeedPrice? {
    if (string("tsubid") == "6") {
        val purchase = embedded("tsubdata")
        val amount = purchase?.string("final_price").asPrice()
        if (purchase != null && amount != null) {
            return FeedPrice("到手价", amount, purchase.string("config_name").ifEmpty { null })
        }
    }
    if (string("feedType") in TradeFeedTypes) {
        val info = embedded("ershou_info") ?: return null
        info.string("product_price").asPrice()?.let { amount ->
            val config = info.embedded("product_config_data")
            val name = config?.string("configTitle").orEmpty().ifEmpty {
                listOf(config?.string("ram").orEmpty(), config?.string("rom").orEmpty())
                    .filter { it.isNotEmpty() }
                    .joinToString("+")
            }
            return FeedPrice("交易价", amount, name.ifEmpty { null })
        }
    }
    return null
}

/** The listing a second-hand feed points at, when it carries a usable link. */
fun Entity.tradeLink(): TradeLink? {
    if (string("feedType") !in TradeFeedTypes) return null
    val info = embedded("ershou_info") ?: return null
    val url = info.string("link_url").trim()
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    return if (info.string("link_source") == "闲鱼") {
        TradeLink("查看闲鱼", url, isXianyu = true)
    } else {
        TradeLink("查看交易链接", url, isXianyu = false)
    }
}

/**
 * The `subTabFeedCard6` row: a reference price for the sub-tab, not an average and not a
 * guarantee. It names the feed it was taken from so the card can open it.
 */
fun Entity.purchasePriceSummary(): FeedPrice? {
    if (entityTemplate != "subTabFeedCard6") return null
    val extra = embedded("extraData") ?: return null
    return extra.string("final_price").asPrice()?.let { FeedPrice("到手价参考", it, null) }
}

/** The feed the summary price was taken from, empty when the row does not name one. */
fun Entity.purchasePriceSummaryFeedId(): String =
    (embedded("extraData")?.string("feed_id")).orEmpty().takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }.orEmpty()

/** When the summary price was last read from its source. */
fun Entity.purchasePriceSummaryUpdateTime(): String =
    embedded("extraData")?.string("update_time").orEmpty()

private val TradeFeedTypes = setOf("ershou", "trade")
