package app.awavenue.c001apk.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * One item of a Coolapk response. The API mixes value types freely (numbers arrive as
 * strings and the other way round), so fields are read leniently from the raw object instead
 * of through a strict schema.
 *
 * Named accessors cover the fields used on most screens; everything else is reachable through
 * [string], [obj], [list] and the other raw readers.
 */
@Immutable
class Entity(val json: JsonObject) {
    fun string(key: String): String = json[key].asText()
    fun long(key: String): Long? = json[key].asText().toLongOrNull()
    fun int(key: String): Int? = json[key].asText().toIntOrNull()
    fun double(key: String): Double? = (json[key] as? JsonPrimitive)?.doubleOrNull
    fun bool(key: String): Boolean =
        (json[key] as? JsonPrimitive)?.booleanOrNull ?: (json[key].asText() in setOf("1", "true"))

    fun obj(key: String): Entity? = (json[key] as? JsonObject)?.let(::Entity)
    fun list(key: String): List<Entity> =
        (json[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::Entity) }

    fun strings(key: String): List<String> =
        (json[key] as? JsonArray).orEmpty().map { it.asText() }.filter { it.isNotEmpty() }

    /** Keys whose value is a JSON string holding JSON, e.g. `messageExtra`. */
    fun nested(key: String): Entity? {
        val raw = string(key)
        if (raw.isEmpty()) return null
        return runCatching {
            (kotlinx.serialization.json.Json.parseToJsonElement(raw) as? JsonObject)?.let(::Entity)
        }.getOrNull()
    }

    val entityType get() = string("entityType")
    val entityTemplate get() = string("entityTemplate")
    val entityId get() = string("entityId")
    val id get() = string("id")
    val uid get() = string("uid")
    val title get() = string("title")
    val url get() = string("url")
    val pic get() = string("pic")
    val logo get() = string("logo")
    val message get() = string("message")
    val messageTitle get() = string("message_title")
    val infoHtml get() = string("infoHtml")
    val deviceTitle get() = string("device_title")
    val dateline get() = long("dateline")
    val replyNum get() = string("replynum").ifEmpty { string("replyNum") }
    val likeNum get() = string("likenum").ifEmpty { string("likeNum") }
    val commentNum get() = string("commentnum")
    val commentNumTxt get() = string("commentnum_txt")
    val downCount get() = string("downCount")
    val hotNumTxt get() = string("hot_num_txt")
    val feedCommentNumTxt get() = string("feed_comment_num_txt")
    val picArr get() = strings("picArr")
    val userAvatar get() = string("userAvatar")
    val username get() = obj("userInfo")?.string("username")?.ifEmpty { null } ?: string("username")
    val isLiked get() = obj("userAction")?.string("like") == "1"
    val userAction get() = obj("userAction")
    val likeUserInfo get() = obj("likeUserInfo")
    val likeTime get() = long("likeTime")
    val forwardSourceType get() = string("forwardSourceType")
    val forwardSourceFeed get() = obj("forwardSourceFeed")
    val extraUrl get() = string("extra_url")
    val extraTitle get() = string("extra_title")
    val extraPic get() = string("extra_pic")
    val replyRows get() = list("replyRows")
    val entities get() = list("entities")
    val tags get() = string("tags")
    val ttitle get() = string("ttitle")
    val relationRows get() = list("relationRows")
    val targetRow get() = obj("targetRow")
    val description get() = string("description")
    val intro get() = string("intro")
    val followNum get() = string("follownum").ifEmpty { string("followNum") }
    val fansNum get() = string("fansnum").ifEmpty { string("fansNum") }
    val isTop get() = string("isTop") == "1"
    val isStickTop get() = string("isStickTop") == "1"
    val isFeedAuthor get() = string("isFeedAuthor") == "1"
    val replyUid get() = string("rUid").ifEmpty { string("replyUid") }
    val replyUsername get() = string("rUsername").ifEmpty { string("replyUsername") }
    val replyUserAvatar get() = string("rUserAvatar")
    val replyId get() = string("rid").ifEmpty { string("replyId") }
    val dyhId get() = string("dyhId")
    val collectionInfo get() = obj("collectionInfo")
    val status get() = string("status")

    override fun equals(other: Any?) = other is Entity && other.json == json
    override fun hashCode() = json.hashCode()
    override fun toString() = json.toString()
}

private fun JsonElement?.asText(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> contentOrNull.orEmpty()
    else -> ""
}
