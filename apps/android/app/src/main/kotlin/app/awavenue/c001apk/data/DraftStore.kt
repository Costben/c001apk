package app.awavenue.c001apk.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

private val WHITESPACE = Regex("\\s+")

/** One picture kept with a draft: the stored copy plus the metadata the upload ticket wants. */
data class DraftImage(
    val path: String,
    val name: String,
    val resolution: String,
    val md5: String,
) {
    fun toJson() = buildJsonObject {
        put("path", JsonPrimitive(path))
        put("name", JsonPrimitive(name))
        put("resolution", JsonPrimitive(resolution))
        put("md5", JsonPrimitive(md5))
    }

    companion object {
        fun fromJson(obj: JsonObject) = DraftImage(
            path = obj.text("path"),
            name = obj.text("name"),
            resolution = obj.text("resolution"),
            md5 = obj.text("md5"),
        )
    }
}

/**
 * A composer that was put aside: the text, the pictures and the target it was answering, so
 * reopening it lands on the same page. An empty [replyId] means a new post rather than a reply,
 * and [targetType] / [title] carry the topic the post was written under.
 */
data class Draft(
    val id: String,
    val text: String,
    val images: List<DraftImage>,
    val replyId: String,
    val replyType: String,
    val targetType: String,
    val targetId: String,
    val title: String,
    val feedType: String,
    val onlySelf: Boolean,
    val updatedAt: Long,
) {
    /** What the draft box says about where this draft would be published. */
    val label: String
        get() = when {
            replyId.isNotEmpty() -> if (replyType == "reply") "回复 @$title" else "评论 @$title"
            targetType == "tag" -> "话题 #$title#"
            targetType == "apk" -> "应用评论 · $title"
            targetType == "product_phone" -> "数码 · $title"
            title.isNotEmpty() -> "发布于 · $title"
            else -> "发布动态"
        }

    /** One line for the list: the text, or what is left when the draft is pictures only. */
    val preview: String
        get() = text.trim().replace(WHITESPACE, " ").ifEmpty {
            if (images.isEmpty()) "（空白草稿）" else "（${images.size}张图片）"
        }

    fun toJson() = buildJsonObject {
        put("text", JsonPrimitive(text))
        put("images", buildJsonArray { images.forEach { add(it.toJson()) } })
        put("replyId", JsonPrimitive(replyId))
        put("replyType", JsonPrimitive(replyType))
        put("targetType", JsonPrimitive(targetType))
        put("targetId", JsonPrimitive(targetId))
        put("title", JsonPrimitive(title))
        put("feedType", JsonPrimitive(feedType))
        put("onlySelf", JsonPrimitive(onlySelf))
        put("updatedAt", JsonPrimitive(updatedAt))
    }

    companion object {
        fun fromJson(id: String, obj: JsonObject) = Draft(
            id = id,
            text = obj.text("text"),
            images = (obj["images"] as? JsonArray)
                ?.map { DraftImage.fromJson(it.jsonObject) }
                .orEmpty(),
            replyId = obj.text("replyId"),
            replyType = obj.text("replyType").ifEmpty { "feed" },
            targetType = obj.text("targetType"),
            targetId = obj.text("targetId"),
            title = obj.text("title"),
            feedType = obj.text("feedType").ifEmpty { "feed" },
            onlySelf = (obj["onlySelf"] as? JsonPrimitive)?.booleanOrNull ?: false,
            updatedAt = obj.text("updatedAt").toLongOrNull() ?: 0L,
        )
    }
}

private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

/**
 * The draft box: the text, pictures and target of a composer that was closed half-written.
 *
 * Pictures are copied out of the picker's cache directory into the app's own files, because that
 * cache is evicted and a draft can sit there for weeks. Each draft owns one folder, and a save
 * rewrites it: pictures already inside it stay, everything else is copied in under a fresh name
 * and whatever is left over is deleted, so removing a picture from the sheet really frees it.
 */
class DraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("drafts", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "drafts")

    private var stored by mutableStateOf(read())

    /** Newest first. */
    val drafts: List<Draft>
        get() = stored.values.sortedByDescending { it.updatedAt }

    val count: Int
        get() = stored.size

    fun find(id: String): Draft? = stored[id]

    /**
     * Copies [images] aside and records the draft, replacing the one already under [id]. The file
     * work runs off the caller's thread; the state update lands back on it.
     */
    suspend fun save(
        id: String,
        text: String,
        images: List<DraftImage>,
        replyId: String = "",
        replyType: String = "feed",
        targetType: String = "",
        targetId: String = "",
        title: String = "",
        feedType: String = "feed",
        onlySelf: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): Draft {
        val kept = withContext(Dispatchers.IO) { copyImages(id, images) }
        val draft = Draft(
            id = id,
            text = text,
            images = kept,
            replyId = replyId,
            replyType = replyType,
            targetType = targetType,
            targetId = targetId,
            title = title,
            feedType = feedType,
            onlySelf = onlySelf,
            updatedAt = now,
        )
        stored = stored + (id to draft)
        write()
        return draft
    }

    fun remove(id: String) {
        if (stored.containsKey(id)) {
            stored = stored - id
            write()
        }
        File(root, id).deleteRecursively()
    }

    fun clear() {
        stored = emptyMap()
        write()
        root.deleteRecursively()
    }

    private fun copyImages(id: String, images: List<DraftImage>): List<DraftImage> {
        val dir = File(root, id).apply { mkdirs() }
        val kept = images.mapNotNull { image ->
            val source = File(image.path)
            if (!source.isFile) return@mapNotNull null
            // A picture that already lives in this draft's folder is kept where it is.
            if (source.parentFile?.absolutePath == dir.absolutePath) return@mapNotNull image
            val extension = source.extension.ifEmpty { "jpg" }
            val destination = File(dir, "${UUID.randomUUID()}.$extension")
            runCatching { source.copyTo(destination, overwrite = true) }
                .getOrNull()
                ?.let { image.copy(path = it.absolutePath) }
        }
        val keep = kept.mapTo(HashSet()) { it.path }
        dir.listFiles()?.forEach { file -> if (file.absolutePath !in keep) file.delete() }
        return kept
    }

    private fun write() {
        val json = buildJsonObject { stored.forEach { (id, draft) -> put(id, draft.toJson()) } }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun read(): Map<String, Draft> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject.mapValues { (id, value) ->
                Draft.fromJson(id, value.jsonObject)
            }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val KEY = "draft"
    }
}
