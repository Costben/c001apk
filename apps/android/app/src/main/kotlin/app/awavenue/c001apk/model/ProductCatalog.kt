package app.awavenue.c001apk.model

import java.net.URI
import java.net.URLDecoder

/** A destination in the digital catalog: a category, a subcategory or a brand. */
class CatalogEntry(val title: String, val url: String, val logo: String = "")

/** A top-level digital category and the subcategories the server offers under it. */
class CatalogCategory(val entry: CatalogEntry, val children: List<CatalogEntry>)

private val CatalogSortEntityIds = setOf("productCategorySortSelect", "productSeriesSortSelect")

/**
 * The digital sidebar's categories. A child may repeat its parent's id and differ only by
 * `filterOther=true`, so the complete url is what identifies a selection.
 */
fun catalogCategories(rows: List<Entity>): List<CatalogCategory> = rows.mapNotNull { row ->
    val entry = row.catalogEntry() ?: return@mapNotNull null
    CatalogCategory(
        entry,
        row.list("secondCategoryRows").mapNotNull { it.catalogEntry() }.distinctBy { it.url },
    )
}.distinctBy { it.entry.url }

private fun Entity.catalogEntry(): CatalogEntry? {
    val title = title
    val url = url
    if (title.isEmpty() || url.isEmpty()) return null
    return CatalogEntry(title, url, logo)
}

/** The brand row a category page opens with: an `iconGridHorizonCard` whose id is `category-detail`. */
fun Entity.catalogBrands(): List<CatalogEntry>? {
    if (entityTemplate != "iconGridHorizonCard" || entityId != "category-detail") return null
    return entities.mapNotNull { it.catalogEntry() }.distinctBy { it.url }
}

/** The sort row a category page opens with; choosing an option reloads the page it belongs to. */
fun Entity.catalogSorts(): List<CatalogEntry>? {
    if (entityTemplate != "sortSelectCard" || entityId !in CatalogSortEntityIds) return null
    return entities.mapNotNull { it.catalogEntry() }.distinctBy { it.url }
}

/** The option the server marks as current: a title in `extraDataArr`, or a url. */
fun Entity.catalogSelectedSort(): String =
    (obj("extraDataArr") ?: nested("extraDataArr"))?.string("selectedTab").orEmpty()

/** The `sortField` a sort option carries, read through any `/page?url=` wrappers. */
fun String.catalogSortField(): String? = runCatching {
    var current = this
    repeat(4) {
        val query = URI(current).rawQuery.orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                    URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
        query["sortField"]?.let { return@runCatching it }
        current = query["url"] ?: return@runCatching null
    }
    null
}.getOrNull()

/**
 * The url a sort option loads. The server hands options as `/page?url=…` wrappers or as the plain
 * destination; the list endpoint wants the destination itself.
 */
fun String.catalogListUrl(): String {
    val prefix = "/page?url="
    if (!startsWith(prefix)) return this
    val inner = removePrefix(prefix)
    return runCatching { URLDecoder.decode(inner, "UTF-8") }.getOrDefault(inner)
}
