package app.awavenue.c001apk.ui.feed.detail

import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.network.ApiFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/** Companion MP4 metadata for a live photo to be uploaded alongside the still image. */
data class LivePhotoVideo(
    val path: String,
    val name: String,
    val md5: String,
)

/** One picked picture, described the way the prepare endpoint wants it. */
class OssUploadModel(
    val name: String,
    val resolution: String,
    val md5: String,
    val livePhotoVideo: String = "",
    val hdr: Int = 0,
) {
    fun toJson(): JsonObject = JsonObject(
        buildMap {
            put("name", JsonPrimitive(name))
            put("resolution", JsonPrimitive(resolution))
            put("md5", JsonPrimitive(md5))
            put("hdr", JsonPrimitive(hdr))
            if (livePhotoVideo.isNotEmpty()) {
                put("livePhotoVideo", JsonPrimitive(livePhotoVideo))
                put("livePhoto", JsonPrimitive(1))
            }
        },
    )
}

/** A local file plus the metadata (and optional live-photo video) the server must be told about. */
data class SelectedImage(
    val path: String,
    val model: OssUploadModel,
    val liveVideo: LivePhotoVideo? = null,
    val livePhotoEnabled: Boolean = liveVideo != null,
) {
    fun activeLiveVideo(enableLivePhoto: Boolean = true): LivePhotoVideo? =
        liveVideo?.takeIf { enableLivePhoto && livePhotoEnabled }
}

/**
 * Uploads pictures to Aliyun OSS through the ticket the prepare endpoint hands out. The signing
 * rules — canonical headers, canonical resource and the HMAC-SHA1 over both — mirror the
 * Flutter client's `AliyunOssClient`.
 */
object OssUpload {

    private const val UPLOAD_BUCKET = "image"
    private const val UPLOAD_DIR = "feed"
    private const val X_OSS_CALLBACK_VAR = "eyJ4OnZhcjEiOiJmYWxzZSJ9"
    private const val X_OSS_CALLBACK =
        "eyJjYWxsYmFja0JvZHlUeXBlIjoiYXBwbGljYXRpb25cL2pzb24iLCJjYWxsYmFja0hvc3QiOiJhcGkuY29vbGFway5jb20iLCJjYWxsYmFja1VybCI6Imh0dHBzOlwvXC9hcGkuY29vbGFway5jb21cL3Y2XC9jYWxsYmFja1wvbW9iaWxlT3NzVXBsb2FkU3VjY2Vzc0NhbGxiYWNrP2NoZWNrQXJ0aWNsZUNvdmVyUmVzb2x1dGlvbj0wJnZlcnNpb25Db2RlPTIxMDIwMzEiLCJjYWxsYmFja0JvZHkiOiJ7XCJidWNrZXRcIjoke2J1Y2tldH0sXCJvYmplY3RcIjoke29iamVjdH0sXCJoYXNQcm9jZXNzXCI6JHt4OnZhcjF9fSJ9"

    private class UploadTask(
        val name: String,
        val file: File,
        val contentType: String,
        val isImage: Boolean,
        val displayIndex: Int,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    internal fun buildUploadFileList(
        images: List<SelectedImage>,
        enableLivePhoto: Boolean = true,
    ): List<JsonObject> = buildList {
        for (image in images) {
            val activeVideo = image.activeLiveVideo(enableLivePhoto)
            add(
                OssUploadModel(
                    name = image.model.name,
                    resolution = image.model.resolution,
                    md5 = image.model.md5,
                    livePhotoVideo = activeVideo?.name.orEmpty(),
                    hdr = image.model.hdr,
                ).toJson(),
            )
            if (activeVideo != null) {
                add(
                    JsonObject(
                        mapOf(
                            "name" to JsonPrimitive(activeVideo.name),
                            "md5" to JsonPrimitive(activeVideo.md5),
                        ),
                    ),
                )
            }
        }
    }

    internal fun resolveFileInfoByName(fileInfo: JsonArray): Map<String, JsonObject> =
        fileInfo.mapNotNull { it as? JsonObject }
            .filter { it.text("name").isNotEmpty() }
            .associateBy { it.text("name") }

    /**
     * Prepares and uploads every picture (and companion live-photo MP4 when enabled), returning
     * the still-image `pic` entries the write endpoints want. [onProgress] is called with the
     * zero-based index and the total, reproducing the "正在上传图片 $i/$total" progress message.
     * Private messages ("message"/"message") disable live photos via [enableLivePhoto].
     */
    suspend fun upload(
        repository: CoolapkRepository,
        images: List<SelectedImage>,
        toUid: String? = null,
        uploadBucket: String = UPLOAD_BUCKET,
        uploadDir: String = UPLOAD_DIR,
        feedType: String? = null,
        enableLivePhoto: Boolean = true,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): List<String> {
        if (images.isEmpty()) return emptyList()
        val prepareList = buildUploadFileList(images, enableLivePhoto)
        val usesLivePhoto = images.any { it.activeLiveVideo(enableLivePhoto) != null }
        val fields = buildList {
            add("uploadBucket" to uploadBucket)
            add("uploadDir" to uploadDir)
            add("is_anonymous" to "0")
            add("uploadFileList" to JsonArray(prepareList).toString())
            if (toUid != null) add("toUid" to toUid)
            if (!feedType.isNullOrEmpty()) add("feed_type" to feedType)
        }
        val ticket = repository.ossUploadPrepare(fields)
        val payload = ticket["data"] as? JsonObject ?: JsonObject(emptyMap())
        val fileInfo = payload["fileInfo"] as? JsonArray ?: JsonArray(emptyList())
        val infoByName = resolveFileInfoByName(fileInfo)
        val prepare = payload["uploadPrepareInfo"] as? JsonObject ?: JsonObject(emptyMap())
        val accessKeyId = prepare.text("accessKeyId")
        val accessKeySecret = prepare.text("accessKeySecret")
        val securityToken = prepare.text("securityToken")
        val endpoint = prepare.text("endPoint").removePrefix("https://").removePrefix("http://")
        val bucket = prepare.text("bucket")
        val prefix = prepare.text("uploadImagePrefix")

        val tasks = buildList {
            for ((index, image) in images.withIndex()) {
                val imageFile = File(image.path)
                add(
                    UploadTask(
                        name = image.model.name,
                        file = imageFile,
                        contentType = mimeOf(imageFile),
                        isImage = true,
                        displayIndex = index,
                    ),
                )
                val activeVideo = image.activeLiveVideo(enableLivePhoto)
                if (activeVideo != null) {
                    add(
                        UploadTask(
                            name = activeVideo.name,
                            file = File(activeVideo.path),
                            contentType = "video/mp4",
                            isImage = false,
                            displayIndex = index,
                        ),
                    )
                }
            }
        }

        val result = mutableListOf<String>()
        for ((taskIndex, task) in tasks.withIndex()) {
            onProgress?.invoke(taskIndex, tasks.size)
            if (!task.file.exists() || task.file.length() == 0L) {
                throw ApiFailure(ApiFailureKind.Transport, "第 ${task.displayIndex} 张图片上传失败，请稍后重试")
            }
            val info = (
                infoByName[task.name]
                    ?: if (!usesLivePhoto && fileInfo.size == tasks.size) {
                        fileInfo.getOrNull(taskIndex) as? JsonObject
                    } else {
                        null
                    }
                ) ?: throw ApiFailure(ApiFailureKind.Transport, "第 ${task.displayIndex} 张图片上传失败，请稍后重试")

            // If the server already returned an instant-upload URL for this MD5, skip PUT.
            val instantUrl = info.text("url")
            if (instantUrl.isNotEmpty()) {
                if (task.isImage) result += instantUrl
                continue
            }

            val uploadFileName = info.text("uploadFileName")
            if (uploadFileName.isEmpty()) {
                throw ApiFailure(ApiFailureKind.Transport, "第 ${task.displayIndex} 张图片上传失败，请稍后重试")
            }
            val cleanName = uploadFileName.trimStart('/')
            val objectPath = if ('/' in cleanName) cleanName else "$uploadDir/$cleanName"
            // The read and the blocking PUT stay off the caller's (main) dispatcher.
            val uploaded = withContext(Dispatchers.IO) {
                runCatching {
                    put(
                        url = "https://$bucket.$endpoint/$objectPath",
                        bytes = task.file.readBytes(),
                        contentType = task.contentType,
                        accessKeyId = accessKeyId,
                        accessKeySecret = accessKeySecret,
                        securityToken = securityToken,
                        bucket = bucket,
                        objectPath = objectPath,
                    )
                }.getOrElse { false }
            }
            if (!uploaded) {
                throw ApiFailure(ApiFailureKind.Transport, "第 ${task.displayIndex} 张图片上传失败，请稍后重试")
            }
            if (task.isImage) {
                result += "${prefix.trimEnd('/')}/${uploadFileName.trimStart('/')}"
            }
        }
        return result
    }

    private fun put(
        url: String,
        bytes: ByteArray,
        contentType: String,
        accessKeyId: String,
        accessKeySecret: String,
        securityToken: String,
        bucket: String,
        objectPath: String,
    ): Boolean {
        val contentMd5 = Base64.encodeToString(md5(bytes), Base64.NO_WRAP)
        val date = httpDate()
        val canonicalHeaders = listOf(
            "x-oss-callback" to X_OSS_CALLBACK,
            "x-oss-callback-var" to X_OSS_CALLBACK_VAR,
            "x-oss-security-token" to securityToken,
        ).sortedBy { it.first }.joinToString("\n") { "${it.first}:${it.second}" }
        val canonicalResource = "/$bucket/$objectPath"
        val canonicalString = listOf(
            "PUT",
            contentMd5,
            contentType,
            date,
            canonicalHeaders,
            canonicalResource,
        ).joinToString("\n")
        val signature = Base64.encodeToString(hmacSha1(accessKeySecret, canonicalString), Base64.NO_WRAP)

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", contentType)
            .header("Content-MD5", contentMd5)
            .header("Date", date)
            .header("Host", url.substringAfter("https://").substringBefore('/'))
            .header("x-oss-security-token", securityToken)
            .header("x-oss-callback-var", X_OSS_CALLBACK_VAR)
            .header("x-oss-callback", X_OSS_CALLBACK)
            .header("Authorization", "OSS $accessKeyId:$signature")
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        client.newCall(request).execute().use { return it.isSuccessful }
    }

    private fun md5(bytes: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("MD5").digest(bytes)

    private fun hmacSha1(secret: String, value: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
    }

    private fun httpDate(): String {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("GMT")
        return "${format.format(Date())} GMT"
    }

    private fun mimeOf(file: File): String =
        android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}
