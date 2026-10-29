package app.awavenue.c001apk.model

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** What a rule looks at: the rendered text, the author's uid, or the topic names. */
enum class FilterKind(val label: String) { Text("关键词"), Author("用户"), Topic("话题") }

/** Where a rule applies. Comments only exist inside a feed's detail page. */
enum class FilterScope(val label: String) { Feed("动态"), Comment("评论"), User("用户卡片") }

/**
 * One user rule. Text rules match a term inside one sentence of the rendered body; a sentence
 * that holds any exception is left alone, which keeps a broad word from hiding an exemption.
 */
data class ContentRule(
    val id: String,
    val title: String,
    val kind: FilterKind = FilterKind.Text,
    val terms: List<String>,
    val exceptions: List<String> = emptyList(),
    val scopes: Set<FilterScope> = FilterScope.entries.toSet(),
    val enabled: Boolean = true,
) {
    /** A message for the editor, or null when the rule can be saved. */
    fun validate(): String? {
        if (title.isBlank()) return "请填写规则名称"
        if (title.length > 80) return "规则名称最多 80 字"
        if (terms.isEmpty()) return "请填写至少一个匹配词"
        if (terms.size > MaxTerms || exceptions.size > MaxTerms) return "匹配词与例外各最多 $MaxTerms 项"
        val invalid = (terms + exceptions).firstOrNull { normalizeFilterText(it).isBlank() || it.length > 128 }
        if (invalid != null) return "每项需为 1 至 128 个可见字符"
        if (scopes.isEmpty()) return "请至少选择一个生效范围"
        if (kind == FilterKind.Author && terms.any { !UidPattern.matches(it) }) return "用户规则请填写数字 UID"
        return null
    }

    private companion object {
        const val MaxTerms = 50
        val UidPattern = Regex("[1-9][0-9]{0,19}")
    }
}

/** Everything the filter remembers: the master switch, the scopes it may act in, and the rules. */
data class ContentFilterPreferences(
    val enabled: Boolean = false,
    val scopes: Set<FilterScope> = FilterScope.entries.toSet(),
    val rules: List<ContentRule> = emptyList(),
) {
    companion object {
        const val MaxRules = 200
    }
}

/** One candidate for the engine: the visible text plus what the rule kinds compare against. */
data class FilterInput(
    val scope: FilterScope,
    val author: String,
    val text: String,
    val topics: List<String> = emptyList(),
)

data class ContentFilterMatch(val ruleId: String, val reason: String)

/**
 * The user's rules, compiled once per list load. Matching runs on the text the reader actually
 * sees: markup, image urls and article metadata never trigger a keyword.
 */
class ContentFilterEngine(val preferences: ContentFilterPreferences) {
    private class CompiledRule(
        val rule: ContentRule,
        val terms: List<String>,
        val exceptions: List<String>,
    )

    val isEnabled = preferences.enabled && preferences.scopes.isNotEmpty() &&
        preferences.rules.any { it.enabled && it.scopes.any(preferences.scopes::contains) }

    private val rules: List<CompiledRule> = if (!isEnabled) {
        emptyList()
    } else {
        preferences.rules
            .filter { it.enabled && it.terms.isNotEmpty() && it.scopes.any(preferences.scopes::contains) }
            .map { rule ->
                CompiledRule(rule, rule.terms.map(::normalizeFilterText), rule.exceptions.map(::normalizeFilterText))
            }
    }

    fun match(input: FilterInput): ContentFilterMatch? {
        if (rules.isEmpty() || input.scope !in preferences.scopes) return null
        val sentences by lazy { visibleFilterText(input.text).split(SentenceSplit) }
        for (compiled in rules) {
            if (input.scope !in compiled.rule.scopes) continue
            val matched = when (compiled.rule.kind) {
                FilterKind.Author -> input.author.isNotEmpty() && input.author in compiled.terms
                FilterKind.Topic -> input.topics.any { normalizeFilterText(it) in compiled.terms }
                FilterKind.Text -> sentences.any { sentence ->
                    compiled.exceptions.none(sentence::contains) && compiled.terms.any(sentence::contains)
                }
            }
            if (matched) return ContentFilterMatch(compiled.rule.id, compiled.rule.title)
        }
        return null
    }

    fun match(item: Entity, scope: FilterScope? = null): ContentFilterMatch? =
        if (rules.isEmpty()) null else item.filterInput(scope)?.let(::match)
}

/** The rule candidate one list row offers, or null for cards that are never content. */
fun Entity.filterInput(scope: FilterScope? = null): FilterInput? {
    val resolved = scope ?: when (entityType) {
        "feed" -> FilterScope.Feed
        "feed_reply" -> FilterScope.Comment
        "user" -> FilterScope.User
        // Messages, notices, control cards and contact rows are not posts.
        else -> return null
    }
    val body = if (resolved == FilterScope.User) {
        listOfNotNull(username, description, intro)
    } else {
        listOf(messageTitle, title, message, articleFilterText(string("message_raw_output")))
    }.joinToString("\n")
    return FilterInput(
        scope = resolved,
        author = uid.ifEmpty { if (resolved == FilterScope.User) id else "" },
        text = body,
        topics = tags.split(',', '#').map(String::trim).filter(String::isNotBlank) +
            listOfNotNull(ttitle) +
            relationRows.mapNotNull { it.title.takeIf(String::isNotBlank) },
    )
}

/** Only rendered fields count; the raw article payload also carries image urls and metadata. */
internal fun articleFilterText(raw: String): String? = runCatching {
    if (raw.isBlank()) return null
    val rows = Json.parseToJsonElement(raw) as? JsonArray ?: return null
    rows.mapNotNull { it as? JsonObject }.joinToString("\n") { row ->
        fun text(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull
        when (text("type")) {
            "text" -> text("message").orEmpty()
            "image" -> text("description").orEmpty()
            "shareUrl" -> listOfNotNull(text("title"), text("subTitle")).joinToString("\n")
            else -> ""
        }
    }
}.getOrNull()

/** Case, width and zero-width marks are folded so a term matches the way a reader sees it. */
fun normalizeFilterText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(ZeroWidth, "")
        .replace(Spaces, " ")
        .trim()

/** The text a reader sees: markup and block boundaries become line breaks, tags are dropped. */
fun visibleFilterText(value: String): String {
    if (value.isEmpty()) return ""
    val withBreaks = BlockBoundary.replace(value, "\n")
    val stripped = AnyTag.replace(withBreaks, " ")
    return normalizeFilterText(decodeEntities(stripped))
}

private val SentenceSplit = Regex("[。！？!?；;\\r\\n]+")
private val ZeroWidth = Regex("[\\u200b-\\u200f\\u202a-\\u202e\\u2060\\ufeff]")
private val Spaces = Regex("[\\t \\x0B\\f]+")
private val BlockBoundary = Regex("(?i)<\\s*(?:br|/p|/div|/li|/blockquote|/h[1-6]|/tr)\\s*/?\\s*>")
private val AnyTag = Regex("(?s)<[^>]{0,512}>")
private val HtmlEntity = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,8});")
private val NamedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "ensp" to " ", "emsp" to " ", "hellip" to "…", "mdash" to "—",
)

private fun decodeEntities(value: String): String {
    if ('&' !in value) return value
    return HtmlEntity.replace(value) { match ->
        val name = match.groupValues[1]
        val code = when {
            name.startsWith("#x", true) -> name.drop(2).toIntOrNull(16)
            name.startsWith("#") -> name.drop(1).toIntOrNull()
            else -> null
        }
        when {
            code != null && code in 1..0xFFFF -> code.toChar().toString()
            code != null -> match.value
            else -> NamedEntities[name.lowercase(Locale.ROOT)] ?: match.value
        }
    }
}

// ---- storage ---------------------------------------------------------------------------------

/** The whole rule set as one JSON object, kept in SharedPreferences like the block lists. */
fun ContentFilterPreferences.toJson(): JsonObject = buildJsonObject {
    put("enabled", JsonPrimitive(enabled))
    put("scopes", buildJsonArray { scopes.sortedBy { it.ordinal }.forEach { add(JsonPrimitive(it.name)) } })
    put(
        "rules",
        buildJsonArray {
            rules.forEach { rule ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(rule.id))
                        put("title", JsonPrimitive(rule.title))
                        put("kind", JsonPrimitive(rule.kind.name))
                        put("terms", buildJsonArray { rule.terms.forEach { add(JsonPrimitive(it)) } })
                        put("exceptions", buildJsonArray { rule.exceptions.forEach { add(JsonPrimitive(it)) } })
                        put(
                            "scopes",
                            buildJsonArray { rule.scopes.sortedBy { it.ordinal }.forEach { add(JsonPrimitive(it.name)) } },
                        )
                        put("enabled", JsonPrimitive(rule.enabled))
                    },
                )
            }
        },
    )
}

/** Reads a saved rule set; anything unreadable falls back to the default (off, no rules). */
fun decodeContentFilter(raw: String): ContentFilterPreferences {
    if (raw.isBlank() || raw.length > MaxStoredLength) return ContentFilterPreferences()
    return runCatching {
        val root = Json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching ContentFilterPreferences()
        val scopes = root.strings("scopes").mapNotNull { name -> FilterScope.entries.firstOrNull { it.name == name } }
        val rules = (root["rules"] as? JsonArray).orEmpty().mapNotNull { element ->
            val rule = element as? JsonObject ?: return@mapNotNull null
            val terms = rule.strings("terms").filter(String::isNotBlank)
            if (terms.isEmpty()) return@mapNotNull null
            ContentRule(
                id = (rule["id"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                title = (rule["title"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "未命名规则" },
                kind = FilterKind.entries.firstOrNull { it.name == rule.string("kind") } ?: FilterKind.Text,
                terms = terms,
                exceptions = rule.strings("exceptions").filter(String::isNotBlank),
                scopes = rule.strings("scopes")
                    .mapNotNull { name -> FilterScope.entries.firstOrNull { it.name == name } }
                    .toSet()
                    .ifEmpty { FilterScope.entries.toSet() },
                enabled = rule.bool("enabled"),
            )
        }
        ContentFilterPreferences(
            enabled = root.bool("enabled"),
            scopes = scopes.toSet().ifEmpty { FilterScope.entries.toSet() },
            rules = rules.take(ContentFilterPreferences.MaxRules),
        )
    }.getOrDefault(ContentFilterPreferences())
}

private const val MaxStoredLength = 2 * 1024 * 1024

private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.bool(key: String): Boolean {
    val value = (this[key] as? JsonPrimitive)?.contentOrNull
    return value == "true" || value == "1"
}

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
