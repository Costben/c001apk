package app.awavenue.c001apk.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A semantic version. [preRelease] is null for stable versions; a suffix such as `pre`, `beta.2`
 * or `rc.1` sorts below the stable version of the same number. `-release` counts as stable.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val base = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (base != 0) return base
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    override fun toString() = buildString {
        append("$major.$minor.$patch")
        preRelease?.let { append('-').append(it) }
    }

    companion object {
        private val VERSION =
            Regex("""^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?(?:\+\d+)?$""")
        private val PRE_RELEASE_NAMES = listOf("dev", "pre", "alpha", "beta", "rc")

        /** A published tag, stable or pre-release. */
        fun parseRelease(value: String): AppVersion? = VERSION.matchEntire(value)?.let {
            val suffix = it.groupValues[4]
            AppVersion(
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt(),
                preRelease = suffix.takeIf { name -> name.isNotEmpty() && name != "release" },
            )
        }

        /** The running build's `versionName`. */
        fun parseBuild(value: String): AppVersion? = parseRelease(value)

        /** `dev < pre < alpha < beta < rc`, with numeric parts compared as numbers. */
        private fun comparePreRelease(a: String, b: String): Int {
            val left = a.split('.')
            val right = b.split('.')
            for (index in 0 until maxOf(left.size, right.size)) {
                val x = left.getOrNull(index) ?: return -1
                val y = right.getOrNull(index) ?: return 1
                val xn = x.toIntOrNull()
                val yn = y.toIntOrNull()
                val result = when {
                    xn != null && yn != null -> xn.compareTo(yn)
                    xn != null -> -1
                    yn != null -> 1
                    else -> {
                        val xr = PRE_RELEASE_NAMES.indexOf(x).let { if (it >= 0) it else PRE_RELEASE_NAMES.size }
                        val yr = PRE_RELEASE_NAMES.indexOf(y).let { if (it >= 0) it else PRE_RELEASE_NAMES.size }
                        if (xr != yr) xr.compareTo(yr) else x.compareTo(y)
                    }
                }
                if (result != 0) return result
            }
            return 0
        }
    }
}

class AppRelease private constructor(
    val version: AppVersion,
    val page: String,
    val notes: String,
    /** Seconds since the epoch, or null when the date is missing. */
    val publishedAt: Long?,
    private val assets: Map<String, String>,
) {
    /**
     * The APK for this device, preferring its ABIs in order, then a universal APK, then the release
     * page.
     */
    fun downloadFor(abis: List<String>): String = apkDownloadUrl(abis) ?: page

    /**
     * Finds the direct download URL for an APK asset in this release, if one exists.
     * Prefers device ABI, then universal APK matching either `c001apk-coui-*` or `c001apk-*`,
     * and falls back to any APK asset attached to this release.
     */
    fun apkDownloadUrl(abis: List<String>): String? {
        val version = Regex.escape(version.toString())
        val patterns = abis.filter { it in SUPPORTED_ABIS }.map { "-android-${Regex.escape(it)}" } + ""
        for (suffix in patterns) {
            val pattern = Regex("^(c001apk-coui|c001apk)-$version(?:\\+\\d+)?$suffix\\.apk$")
            assets.entries.firstOrNull { pattern.matches(it.key) }?.let { return it.value }
        }
        return assets.entries.firstOrNull { it.key.endsWith(".apk", ignoreCase = true) }?.value
    }

    companion object {
        private const val REPOSITORY = "/${Protocol.REPOSITORY}/releases/"
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        private val json = Json { ignoreUnknownKeys = true }

        private fun trusted(url: HttpUrl, prefix: String): Boolean =
            url.scheme == "https" &&
                url.host == "github.com" &&
                url.username.isEmpty() &&
                url.password.isEmpty() &&
                url.port == 443 &&
                url.encodedPath.startsWith("$REPOSITORY$prefix")

        /** Throws [IllegalArgumentException] for anything that is not a published, trusted release. */
        fun fromJson(body: String): AppRelease {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: throw IllegalArgumentException("更新信息格式异常")
            fun text(obj: JsonObject, key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val draft = (root["draft"] as? JsonPrimitive)?.booleanOrNull
            val prerelease = (root["prerelease"] as? JsonPrimitive)?.booleanOrNull
            val tag = text(root, "tag_name")
            val htmlUrl = text(root, "html_url")
            val assets = root["assets"] as? JsonArray
            if (draft != false || prerelease == null || tag == null || htmlUrl == null || assets == null) {
                throw IllegalArgumentException("更新信息格式异常")
            }
            val version = AppVersion.parseRelease(tag) ?: throw IllegalArgumentException("无法识别版本号")
            // The tag suffix and the GitHub flag must agree, so a stable tag is never listed as a
            // pre-release or the other way round.
            if ((version.preRelease != null) != prerelease) {
                throw IllegalArgumentException("更新信息格式异常")
            }
            val page = htmlUrl.toHttpUrlOrNull()
            if (page == null || !trusted(page, "tag/") || page.pathSegments.lastOrNull() != tag) {
                throw IllegalArgumentException("更新来源异常")
            }
            val downloads = buildMap {
                for (asset in assets) {
                    val obj = asset as? JsonObject ?: continue
                    val name = text(obj, "name") ?: continue
                    val link = text(obj, "browser_download_url") ?: continue
                    val size = (obj["size"] as? JsonPrimitive)?.longOrNull ?: 0L
                    if (text(obj, "state") != "uploaded" || size <= 0) continue
                    val url = link.toHttpUrlOrNull() ?: continue
                    // /<owner>/<repo>/releases/download/<tag>/<name>
                    if (trusted(url, "download/") &&
                        url.pathSegments.size == 6 &&
                        url.pathSegments[4] == tag &&
                        url.pathSegments.last() == name
                    ) {
                        put(name, link)
                    }
                }
            }
            val notes = text(root, "body")?.takeIf { it.isNotBlank() } ?: "此版本未提供更新说明，请查看发布页面。"
            val published = text(root, "published_at")?.let {
                runCatching { java.time.Instant.parse(it).epochSecond }.getOrNull()
            }
            return AppRelease(version, page.toString(), notes, published, downloads)
        }

        /** Parses a `/releases` array, skipping entries that fail validation (drafts, foreign links). */
        fun fromJsonList(body: String): List<AppRelease> {
            val root = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull()
                ?: throw IllegalArgumentException("更新信息格式异常")
            return root.mapNotNull { runCatching { fromJson(it.toString()) }.getOrNull() }
        }
    }
}

sealed interface UpdateResult {
    data class Available(val release: AppRelease, val current: String) : UpdateResult
    data object UpToDate : UpdateResult
    /** The automatic check is switched off, already ran, or the user skipped this version. */
    data object Suppressed : UpdateResult
    data object Busy : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/**
 * Checks this repository's latest GitHub release, or the highest release including pre-releases
 * when the setting asks for them. The automatic check runs once per process, so every cold start
 * sees releases published since the last one.
 */
class UpdateService(
    private val settings: AppSettings,
    private val currentVersion: String,
    baseClient: OkHttpClient,
) {
    private val http = baseClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val checking = AtomicBoolean(false)
    private val automaticChecked = AtomicBoolean(false)

    suspend fun check(automatic: Boolean = false, forceShow: Boolean = false): UpdateResult {
        if (automatic && (!settings.autoCheckAppUpdate || automaticChecked.get())) return UpdateResult.Suppressed
        if (!checking.compareAndSet(false, true)) return UpdateResult.Busy
        if (automatic) automaticChecked.set(true)
        try {
            val current = AppVersion.parseBuild(currentVersion)
                ?: return UpdateResult.Failed("无法识别当前版本，请查看发布页面")
            val includePrerelease = settings.acceptPrereleaseUpdates
            val (code, body) = withContext(Dispatchers.IO) {
                // `/releases/latest` only returns stable releases; the list is needed to see pre-releases.
                val endpoint = if (includePrerelease) {
                    "https://api.github.com/repos/${Protocol.REPOSITORY}/releases?per_page=30"
                } else {
                    "https://api.github.com/repos/${Protocol.REPOSITORY}/releases/latest"
                }
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "c001apk-update")
                    .build()
                http.newCall(request).execute().use { it.code to it.body.string() }
            }
            when (code) {
                403, 429 -> return UpdateResult.Failed("检查更新请求过于频繁，请稍后重试或查看发布页面")
                404 -> return UpdateResult.Failed(
                    if (includePrerelease) "暂未找到可用版本，请稍后重试或查看发布页面"
                    else "暂未找到正式版本，请稍后重试或查看发布页面",
                )
                !in 200..299 -> return UpdateResult.Failed("检查更新失败，请检查网络后重试")
            }
            val release = try {
                if (includePrerelease) {
                    AppRelease.fromJsonList(body).maxByOrNull { it.version }
                } else {
                    AppRelease.fromJson(body)
                }
            } catch (_: IllegalArgumentException) {
                return UpdateResult.Failed("更新信息格式异常，请稍后重试或查看发布页面")
            } ?: return UpdateResult.Failed("暂未找到可用版本，请稍后重试或查看发布页面")
            if (automatic && (!settings.autoCheckAppUpdate || settings.skippedAppUpdateVersion == release.version.toString())) {
                return UpdateResult.Suppressed
            }
            return if (forceShow || release.version > current) UpdateResult.Available(release, currentVersion) else UpdateResult.UpToDate
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return UpdateResult.Failed("检查更新失败，请检查网络后重试")
        } catch (_: Exception) {
            return UpdateResult.Failed("暂时无法检查更新，请稍后重试")
        } finally {
            checking.set(false)
        }
    }
}
