package app.awavenue.c001apk.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/** One announcement, metadata only. The Markdown body is fetched the first time it is opened. */
data class Announcement(
    val id: String,
    val title: String,
    val summary: String,
    val publishedAt: Long,
    val contentRevision: Int,
    val reminderRevision: Int,
    val important: Boolean,
    val path: String,
    val sha256: String,
    val withdrawn: Boolean,
    val expiresAt: Long?,
) {
    val cacheKey: String get() = "$id:$contentRevision:$sha256"

    fun activeAt(now: Long): Boolean = !withdrawn && (expiresAt == null || now < expiresAt)
}

class AnnouncementIndex(val revision: Int, val entries: List<Announcement>) {
    /** A stable form of the table, so the same revision with different content is a conflict. */
    fun fingerprint(): String = buildJsonObject {
        put("schemaVersion", 1)
        put("revision", revision)
        putJsonArray("announcements") { entries.forEach { add(it.toJson()) } }
    }.toString()

    fun toJson(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("revision", revision)
        putJsonArray("announcements") { entries.forEach { add(it.toJson()) } }
    }
}

internal fun Announcement.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("title", title)
    put("summary", summary)
    put("publishedAt", Instant.ofEpochMilli(publishedAt).toString())
    put("contentRevision", contentRevision)
    put("reminderRevision", reminderRevision)
    put("important", important)
    put("path", path)
    put("sha256", sha256)
    put("withdrawn", withdrawn)
    expiresAt?.let { put("expiresAt", Instant.ofEpochMilli(it).toString()) }
}

class AnnouncementFailure(message: String) : Exception(message)

/** Validation of the public announcement table, kept apart so it can be tested without Android. */
internal object AnnouncementJson {
    private val ID = Regex("""^[a-zA-Z0-9][a-zA-Z0-9_-]*$""")
    private val PATH = Regex("""^[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*\.md$""")
    private val DIGEST = Regex("""^[a-f0-9]{64}$""")
    private val ZONED = Regex("""T.*(?:Z|[+-]\d{2}:\d{2})$""")
    private val json = Json { ignoreUnknownKeys = true }

    fun parseIndex(text: String): AnnouncementIndex {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw IllegalArgumentException("公告表格式异常")
        if (root["schemaVersion"]?.int() != 1) throw IllegalArgumentException("公告表格式异常")
        val revision = root["revision"]?.int() ?: throw IllegalArgumentException("公告表格式异常")
        if (revision < 1) throw IllegalArgumentException("公告表格式异常")
        val list = root["announcements"] as? JsonArray ?: throw IllegalArgumentException("公告表格式异常")
        if (list.size > 2000) throw IllegalArgumentException("公告表过大")
        val entries = list.map { parseItem(it) }
        if (entries.map { it.id }.toSet().size != entries.size) {
            throw IllegalArgumentException("公告 ID 重复")
        }
        return AnnouncementIndex(revision, entries.sortedWith(compareByDescending<Announcement> { it.publishedAt }.thenByDescending { it.id }))
    }

    fun parseItem(value: JsonElement): Announcement {
        val obj = value as? JsonObject ?: throw IllegalArgumentException("公告项格式异常")
        fun string(key: String, max: Int, empty: Boolean = false): String {
            val text = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("公告字段格式异常")
            if (text.length > max || (!empty && text.isBlank())) throw IllegalArgumentException("公告字段格式异常")
            return text
        }

        val id = string("id", 100)
        val path = string("path", 200)
        val digest = string("sha256", 64)
        if (!ID.matches(id) || !PATH.matches(path) || !DIGEST.matches(digest)) {
            throw IllegalArgumentException("公告路径或标识格式异常")
        }
        val contentRevision = obj["contentRevision"]?.int() ?: throw IllegalArgumentException("公告修订格式异常")
        val reminderRevision = obj["reminderRevision"]?.int() ?: throw IllegalArgumentException("公告修订格式异常")
        val important = (obj["important"] as? JsonPrimitive)?.booleanOrNull
            ?: throw IllegalArgumentException("公告修订格式异常")
        val withdrawn = (obj["withdrawn"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (contentRevision < 1 || reminderRevision < 1) throw IllegalArgumentException("公告修订格式异常")
        val publishedAt = date(obj["publishedAt"])
        val expiresAt = obj["expiresAt"]?.takeIf { it !is JsonNull }?.let(::date)
        return Announcement(
            id = id,
            title = string("title", 200),
            summary = string("summary", 1000, empty = true),
            publishedAt = publishedAt,
            contentRevision = contentRevision,
            reminderRevision = reminderRevision,
            important = important,
            path = path,
            sha256 = digest,
            withdrawn = withdrawn,
            expiresAt = expiresAt,
        )
    }

    private fun date(value: JsonElement?): Long {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("公告日期缺少时区")
        if (!ZONED.containsMatchIn(text)) throw IllegalArgumentException("公告日期缺少时区")
        return runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            .getOrElse { throw IllegalArgumentException("公告日期格式异常") }
    }

    private fun JsonElement.int(): Int? = (this as? JsonPrimitive)?.intOrNull
}

enum class AnnouncementSync { ColdStart, Resume, Inbox, Manual }

/**
 * The public announcement feed and its local state. The index and the Markdown bodies come from
 * the repository's `announcements` branch, never through the Coolapk client: no account, cookie,
 * token or device header is attached.
 *
 * Receipts are per installation, not per Coolapk account, so switching accounts does not replay
 * notices the user has already read.
 */
class AnnouncementService(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val client: OkHttpClient = announcementClient,
    private val baseUrl: String = ANNOUNCEMENT_BASE,
) {
    private val prefs = context.getSharedPreferences("announcements", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val saveMutex = Mutex()

    private var index: AnnouncementIndex? = null
    private val read = mutableMapOf<String, Int>()
    private val prompted = mutableMapOf<String, Int>()
    private val bodies = LinkedHashMap<String, String>()
    private val bodyLoads = mutableMapOf<String, Deferred<String>>()
    private var syncing: CompletableDeferred<String?>? = null
    private var lastSuccess: Long? = null
    private var lastAttempt: Long? = null
    private var coldChecked = false
    private var expiryJob: Job? = null

    /** Bumped whenever the table, a receipt or a body changes, so Compose can read it. */
    var revision by mutableIntStateOf(0)
        private set

    var automaticEnabled by mutableStateOf(true)
        private set

    var isSyncing by mutableStateOf(false)
        private set

    init {
        restore()
    }

    val items: List<Announcement>
        get() = synchronized(lock) { itemsLocked() }

    val unreadCount: Int
        get() = items.count { !isRead(it) }

    /** The newest important notice that still deserves an automatic prompt, if any. */
    val pendingImportant: Announcement?
        get() = items.firstOrNull { it.important }?.takeIf { item ->
            !isRead(item) && (synchronized(lock) { prompted[item.id] } ?: 0) < item.reminderRevision
        }

    fun isRead(item: Announcement): Boolean =
        (synchronized(lock) { read[item.id] } ?: 0) >= item.reminderRevision

    /** A table may withdraw or replace an item while its body is on screen. */
    fun isCurrent(item: Announcement): Boolean = items.any {
        it.id == item.id && it.cacheKey == item.cacheKey && it.reminderRevision == item.reminderRevision
    }

    suspend fun setAutomaticEnabled(enabled: Boolean) {
        automaticEnabled = enabled
        save()
        bump()
    }

    suspend fun markRead(item: Announcement) {
        if (!isCurrent(item)) return
        synchronized(lock) { read[item.id] = item.reminderRevision }
        save()
        bump()
    }

    suspend fun markPrompted(item: Announcement) {
        if (!isCurrent(item)) return
        synchronized(lock) { prompted[item.id] = item.reminderRevision }
        save()
        bump()
    }

    suspend fun clearBodyCache() {
        synchronized(lock) { bodies.clear() }
        save()
        bump()
    }

    /**
     * The one request in flight is shared by every caller. Automatic reasons honour the
     * 6-hour resume / 15-minute inbox intervals and back off a minute after a failure; a manual
     * refresh always goes out.
     */
    suspend fun sync(reason: AnnouncementSync): String? {
        if ((reason == AnnouncementSync.ColdStart || reason == AnnouncementSync.Resume) && !automaticEnabled) {
            return null
        }
        var existing: CompletableDeferred<String?>? = null
        var started: CompletableDeferred<String?>? = null
        synchronized(lock) {
            val now = clock()
            if (reason == AnnouncementSync.ColdStart && coldChecked) return null
            if (reason == AnnouncementSync.ColdStart) coldChecked = true
            val interval = when (reason) {
                AnnouncementSync.Resume -> RESUME_INTERVAL
                AnnouncementSync.Inbox -> INBOX_INTERVAL
                else -> null
            }
            if (interval != null && lastSuccess != null && now - lastSuccess!! < interval) return null
            if (reason != AnnouncementSync.Manual && lastAttempt != null && now - lastAttempt!! < RETRY_INTERVAL) {
                return null
            }
            lastAttempt = now
            existing = syncing
            if (existing == null) {
                started = CompletableDeferred()
                syncing = started
            }
        }
        existing?.let { return it.await() }
        val pending = started!!
        isSyncing = true
        scope.launch { runSync(pending) }
        return pending.await()
    }

    private suspend fun runSync(done: CompletableDeferred<String?>) {
        var error: String? = null
        try {
            val text = get("index.json", "application/json", MAX_INDEX_BYTES).toString(Charsets.UTF_8)
            val next = AnnouncementJson.parseIndex(text)
            synchronized(lock) {
                val previous = index
                if (previous != null) {
                    if (next.revision < previous.revision ||
                        (next.revision == previous.revision && next.fingerprint() != previous.fingerprint())
                    ) {
                        throw IllegalArgumentException("公告表版本冲突")
                    }
                    val old = previous.entries.associateBy { it.id }
                    next.entries.forEach { entry ->
                        val before = old[entry.id] ?: return@forEach
                        if (entry.contentRevision < before.contentRevision ||
                            entry.reminderRevision < before.reminderRevision ||
                            (entry.contentRevision == before.contentRevision &&
                                (entry.sha256 != before.sha256 || entry.path != before.path))
                        ) {
                            throw IllegalArgumentException("公告修订冲突")
                        }
                    }
                }
                index = next
                val activeIds = itemsLocked().map { it.id }.toSet()
                bodies.keys.removeAll { it.substringBefore(':') !in activeIds }
                // Older important notices stay unread, but never become surprise popups once a
                // newer one exists.
                itemsLocked().filter { it.important }.drop(1).forEach {
                    prompted[it.id] = it.reminderRevision
                }
                lastSuccess = clock()
                scheduleExpiryLocked()
            }
            save()
            bump()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "公告同步失败，请稍后重试；已缓存的公告仍可查看"
        } finally {
            synchronized(lock) { syncing = null }
            isSyncing = false
            done.complete(error)
            bump()
        }
    }

    /** Downloads and verifies one body. Cached bodies are returned without touching the network. */
    suspend fun loadBody(item: Announcement): String {
        if (!isCurrent(item)) throw AnnouncementFailure("公告已更新或撤回，请刷新列表")
        synchronized(lock) {
            bodies.remove(item.cacheKey)?.let {
                bodies[item.cacheKey] = it
                return it
            }
        }
        val pending = synchronized(lock) {
            bodyLoads[item.cacheKey] ?: scope.async { fetchBody(item) }.also { bodyLoads[item.cacheKey] = it }
        }
        try {
            return pending.await()
        } finally {
            synchronized(lock) { bodyLoads.remove(item.cacheKey, pending) }
        }
    }

    private suspend fun fetchBody(item: Announcement): String {
        val bytes = try {
            get(item.path, "text/plain", MAX_BODY_BYTES)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw AnnouncementFailure("公告正文加载失败，请检查网络后重试")
        }
        if (bytes.isEmpty() || bytes.size > MAX_BODY_BYTES || sha256(bytes) != item.sha256) {
            throw AnnouncementFailure("公告正文尚未同步或校验失败，请稍后重试")
        }
        val body = bytes.toString(Charsets.UTF_8)
        if (!isCurrent(item)) throw AnnouncementFailure("公告已更新或撤回，请刷新列表")
        synchronized(lock) {
            bodies[item.cacheKey] = body
            trimBodiesLocked()
        }
        save()
        if (!isCurrent(item)) throw AnnouncementFailure("公告已更新或撤回，请刷新列表")
        return body
    }

    private suspend fun get(path: String, accept: String, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", accept)
            .header("User-Agent", "c001apk-announcements")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code != 200) throw IOException("HTTP ${response.code}")
            val declared = response.body.contentLength()
            if (declared > limit) throw IOException("公告响应过大")
            val bytes = response.body.bytes()
            if (bytes.size > limit) throw IOException("公告响应过大")
            bytes
        }
    }

    private fun itemsLocked(): List<Announcement> {
        val now = clock()
        return index?.entries?.filter { it.activeAt(now) }.orEmpty()
    }

    private fun trimBodiesLocked() {
        var size = bodies.values.sumOf { it.toByteArray(Charsets.UTF_8).size }
        val iterator = bodies.entries.iterator()
        while ((bodies.size > MAX_BODIES || size > MAX_BODY_TOTAL) && iterator.hasNext()) {
            val entry = iterator.next()
            size -= entry.value.toByteArray(Charsets.UTF_8).size
            iterator.remove()
        }
    }

    private fun scheduleExpiryLocked() {
        expiryJob?.cancel()
        val now = clock()
        val next = index?.entries
            ?.filter { !it.withdrawn && it.expiresAt != null && it.expiresAt > now }
            ?.minOfOrNull { it.expiresAt!! }
            ?: return
        expiryJob = scope.launch {
            delay((next - now).coerceAtLeast(1_000))
            synchronized(lock) {
                expiryJob = null
                val active = itemsLocked().map { it.id }.toSet()
                bodies.keys.removeAll { it.substringBefore(':') !in active }
                bump()
                scheduleExpiryLocked()
            }
            save()
        }
    }

    private suspend fun save() {
        saveMutex.withLock {
            withContext(Dispatchers.IO) {
                val state = synchronized(lock) { stateJson() }
                prefs.edit().putString(KEY_STATE, state).apply()
            }
        }
    }

    private fun stateJson(): String = buildJsonObject {
        index?.let { put("index", it.toJson()) }
        putJsonObject("read") { read.forEach { (key, value) -> put(key, value) } }
        putJsonObject("prompted") { prompted.forEach { (key, value) -> put(key, value) } }
        putJsonObject("bodies") { bodies.forEach { (key, value) -> put(key, value) } }
        lastSuccess?.let { put("lastSuccess", it) }
        put("automaticEnabled", automaticEnabled)
    }.toString()

    private fun restore() {
        val raw = prefs.getString(KEY_STATE, null) ?: return
        try {
            val root = Json.parseToJsonElement(raw).jsonObject
            (root["index"] as? JsonObject)?.let { index = AnnouncementJson.parseIndex(it.toString()) }
            (root["read"] as? JsonObject)?.forEach { (key, value) ->
                (value as? JsonPrimitive)?.intOrNull?.let { read[key] = it }
            }
            (root["prompted"] as? JsonObject)?.forEach { (key, value) ->
                (value as? JsonPrimitive)?.intOrNull?.let { prompted[key] = it }
            }
            (root["bodies"] as? JsonObject)?.forEach { (key, value) ->
                (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { bodies[key] = it }
            }
            automaticEnabled = (root["automaticEnabled"] as? JsonPrimitive)?.booleanOrNull != false
            lastSuccess = (root["lastSuccess"] as? JsonPrimitive)?.longOrNull
            synchronized(lock) { trimBodiesLocked() }
        } catch (_: Exception) {
            // A corrupt cache must not break launch or surface stored content as current.
            index = null
            bodies.clear()
        }
    }

    private fun bump() {
        revision++
    }

    private companion object {
        const val KEY_STATE = "state"
        const val MAX_INDEX_BYTES = 1024 * 1024
        const val MAX_BODY_BYTES = 256 * 1024
        const val MAX_BODIES = 20
        const val MAX_BODY_TOTAL = 2 * 1024 * 1024
        const val RESUME_INTERVAL = 6 * 60 * 60 * 1000L
        const val INBOX_INTERVAL = 15 * 60 * 1000L
        const val RETRY_INTERVAL = 60 * 1000L

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** Upstream publishes the feed on its `announcements` branch; it is public and account-free. */
private const val ANNOUNCEMENT_BASE = "https://raw.githubusercontent.com/TG-Twilight/c001apk/announcements/announcements/"

private val announcementClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .followRedirects(false)
    .build()
