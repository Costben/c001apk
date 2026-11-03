package app.awavenue.c001apk.model

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * The TG-Twilight `filters/rules.json` snapshot, schema version 1. It is kept apart from the
 * user's own rules: importing a new revision replaces this snapshot but leaves personal rules,
 * scope choices and disabled-rule picks alone.
 *
 * Read and written by hand, like the rest of the stored state: this module does not run the
 * serialization compiler plugin, so `@Serializable` here would compile and then fail at runtime.
 */
data class TwilightRuleSet(
    val schemaVersion: Int,
    val revision: Int,
    val publishedAt: String,
    val rules: List<TwilightRule>,
) {
    fun validate() {
        require(schemaVersion == 1 && revision > 0) { "不支持的远程规则版本" }
        validDate(publishedAt)
        require(rules.size <= 2000 && rules.map { it.id }.distinct().size == rules.size) { "远程规则重复或数量过多" }
        rules.forEach(TwilightRule::validate)
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("schemaVersion", JsonPrimitive(schemaVersion))
        put("revision", JsonPrimitive(revision))
        put("publishedAt", JsonPrimitive(publishedAt))
        put("rules", buildJsonArray { rules.forEach { add(it.toJson()) } })
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024

        fun parse(text: String): TwilightRuleSet {
            require(text.length <= MAX_BYTES) { "远程规则文件过大" }
            val root = Json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException(Malformed)
            return fromJson(root).also { it.validate() }
        }

        internal fun fromJson(root: JsonObject): TwilightRuleSet = TwilightRuleSet(
            schemaVersion = root.number("schemaVersion") ?: throw IllegalArgumentException(Malformed),
            revision = root.number("revision") ?: throw IllegalArgumentException(Malformed),
            publishedAt = root.text("publishedAt") ?: throw IllegalArgumentException(Malformed),
            rules = (root["rules"] as? JsonArray).orEmpty().map { row ->
                TwilightRule.fromJson(row as? JsonObject ?: throw IllegalArgumentException(Malformed))
            },
        )
    }
}

/**
 * One remote rule. A `text` rule lists groups that must all appear within [maxDistance] characters
 * of one sentence; `author` and `topic` compare identifiers directly and are always standard level.
 */
data class TwilightRule(
    val id: String,
    val reason: String,
    val kind: String,
    val level: String = "standard",
    val scopes: List<String> = listOf("feed", "comment", "user"),
    val values: List<String> = emptyList(),
    val groups: List<List<String>> = emptyList(),
    val maxDistance: Int = 80,
    val exceptions: List<String> = emptyList(),
    val reviewedAt: String? = null,
    val sources: List<String> = emptyList(),
) {
    val key: String get() = "twilight:$id"

    fun validate() {
        validString(id, 100)
        require(id.matches(Regex("[a-zA-Z0-9_.:-]+")) && !id.startsWith("personal.")) { "远程规则 ID 无效" }
        validString(reason, 200)
        require(kind in setOf("author", "topic", "text") && level in setOf("standard", "strict"))
        require(
            scopes.isNotEmpty() && scopes.size <= 3 && scopes.distinct().size == scopes.size &&
                scopes.all { it in setOf("feed", "comment", "user") },
        )
        validStrings(values, 200, 256)
        validStrings(exceptions, 16, 128)
        require(groups.size <= 8 && maxDistance in 1..240)
        groups.forEach { validStrings(it, 16, 256) }
        if (kind == "text") {
            require(values.isEmpty() && groups.isNotEmpty() && groups.all { it.isNotEmpty() })
            require(
                groups.flatten().all { term ->
                    val normalized = normalizeFilterText(term)
                    normalized.isNotEmpty() && EXCLUDED_TERMS.none(normalized::contains)
                },
            )
        } else {
            require(values.isNotEmpty() && groups.isEmpty() && exceptions.isEmpty() && level == "standard")
            if (kind == "author") require(values.all { it.matches(Regex("[1-9][0-9]{0,19}")) })
        }
        reviewedAt?.let(::validDate)
        validStrings(sources, 8, 2048)
        sources.forEach { source ->
            val uri = runCatching { URI(source) }.getOrNull() ?: throw IllegalArgumentException("远程规则来源无效")
            require(uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null) { "远程规则来源无效" }
        }
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("reason", JsonPrimitive(reason))
        put("kind", JsonPrimitive(kind))
        put("level", JsonPrimitive(level))
        put("scopes", buildJsonArray { scopes.forEach { add(JsonPrimitive(it)) } })
        put("values", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
        put("groups", buildJsonArray { groups.forEach { group -> add(buildJsonArray { group.forEach { add(JsonPrimitive(it)) } }) } })
        put("maxDistance", JsonPrimitive(maxDistance))
        put("exceptions", buildJsonArray { exceptions.forEach { add(JsonPrimitive(it)) } })
        reviewedAt?.let { put("reviewedAt", JsonPrimitive(it)) }
        put("sources", buildJsonArray { sources.forEach { add(JsonPrimitive(it)) } })
    }

    companion object {
        internal fun fromJson(json: JsonObject): TwilightRule = TwilightRule(
            id = json.text("id") ?: throw IllegalArgumentException(Malformed),
            reason = json.text("reason") ?: throw IllegalArgumentException(Malformed),
            kind = json.text("kind") ?: throw IllegalArgumentException(Malformed),
            level = json.text("level") ?: "standard",
            scopes = json.strings("scopes") ?: listOf("feed", "comment", "user"),
            values = json.strings("values").orEmpty(),
            groups = json.rows("groups").orEmpty(),
            maxDistance = json.number("maxDistance") ?: 80,
            exceptions = json.strings("exceptions").orEmpty(),
            reviewedAt = json.text("reviewedAt"),
            sources = json.strings("sources").orEmpty(),
        )
    }
}

private const val Malformed = "远程规则格式无效"

/** Terms that would make a broad text rule match ordinary conversation; upstream excludes them. */
internal val EXCLUDED_TERMS = listOf("lsp", "手冲", "后入", "性压抑")

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotEmpty)

private fun JsonObject.number(key: String): Int? = (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

private fun JsonObject.strings(key: String): List<String>? =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun JsonObject.rows(key: String): List<List<String>>? =
    (this[key] as? JsonArray)?.map { row ->
        (row as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    }

private fun validString(value: String, max: Int) {
    require(
        value.isNotEmpty() && value.length <= max && value.trim() == value &&
            value.none { it.code < 32 || it.code == 127 },
    ) { "远程规则格式无效" }
}

private fun validStrings(values: List<String>, maxCount: Int, maxLength: Int) {
    require(values.size <= maxCount && values.distinct().size == values.size)
    values.forEach { validString(it, maxLength) }
}

private val DateShape = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?(Z|[+-]\\d{2}:\\d{2})")

/**
 * Checks the ISO-8601 date without `java.time`, which is not available on the oldest supported
 * release (API 23). Month lengths and leap years are checked so `2026-02-31` is rejected.
 */
private fun validDate(value: String) {
    require(value.length <= 40 && DateShape.matches(value)) { "远程规则时间格式无效" }
    val year = value.substring(0, 4).toInt()
    val month = value.substring(5, 7).toInt()
    val day = value.substring(8, 10).toInt()
    val hour = value.substring(11, 13).toInt()
    val minute = value.substring(14, 16).toInt()
    val second = value.substring(17, 19).toInt()
    require(month in 1..12 && hour in 0..23 && minute in 0..59 && second in 0..59) { "远程规则时间格式无效" }
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val days = when (month) {
        2 -> if (leap) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    require(day in 1..days) { "远程规则时间格式无效" }
    val offset = value.substringAfterLast('+', "").ifEmpty { value.substringAfterLast('-', "") }
    if (offset.isNotEmpty() && offset.length == 5 && offset[2] == ':') {
        require(offset.substring(0, 2).toInt() in 0..23 && offset.substring(3, 5).toInt() in 0..59) {
            "远程规则时间格式无效"
        }
    }
}
