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

/** One picked picture, described the way the prepare endpoint wants it. */
class OssUploadModel(val name: String, val resolution: String, val md5: String) {
    fun toJson(): JsonObject = JsonObject(
        mapOf(
            "name" to JsonPrimitive(name),
            "resolution" to JsonPrimitive(resolution),
            "md5" to JsonPrimitive(md5),
        ),
    )
}

/** A local file plus the metadata the server must be told about before the upload. */
class SelectedImage(val path: String, val model: OssUploadModel)

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

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    /**
     * Prepares and uploads every picture, returning the `pic` entries the write endpoints want.
     * [onProgress] is called with the zero-based index and the total, reproducing the
     * "正在上传图片 $i/$total" the Flutter client shows. The bucket and directory the ticket is asked
     * for differ per endpoint: feeds use "image"/"feed", private messages "message"/"message".
     */
    suspend fun upload(
        repository: CoolapkRepository,
        images: List<SelectedImage>,
        toUid: String? = null,
        uploadBucket: String = UPLOAD_BUCKET,
        uploadDir: String = UPLOAD_DIR,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): List<String> {
        if (images.isEmpty()) return emptyList()
        val fields = buildList {
            add("uploadBucket" to uploadBucket)
            add("uploadDir" to uploadDir)
            add("is_anonymous" to "0")
            add("uploadFileList" to images.joinToString(", ", "[", "]") { it.model.toJson().toString() })
            if (toUid != null) add("toUid" to toUid)
        }
        val ticket = repository.ossUploadPrepare(fields)
        val payload = ticket["data"] as? JsonObject ?: JsonObject(emptyMap())
        val fileInfo = (payload["fileInfo"] as? JsonArray).orEmpty()
        val prepare = payload["uploadPrepareInfo"] as? JsonObject ?: JsonObject(emptyMap())
        val accessKeyId = prepare.text("accessKeyId")
        val accessKeySecret = prepare.text("accessKeySecret")
        val securityToken = prepare.text("securityToken")
        val endpoint = prepare.text("endPoint").removePrefix("https://").removePrefix("http://")
        val bucket = prepare.text("bucket")
        val prefix = prepare.text("uploadImagePrefix")

        val result = mutableListOf<String>()
        for ((index, info) in fileInfo.withIndex()) {
            val uploadFileName = (info as? JsonObject)?.text("uploadFileName").orEmpty()
            val file = images.getOrNull(index)?.path?.let(::File)
            onProgress?.invoke(index, fileInfo.size)
            if (file == null || !file.exists() || file.length() == 0L) {
                throw ApiFailure(ApiFailureKind.Transport, "第 $index 张图片上传失败，请稍后重试")
            }
            val objectPath = uploadDir + uploadFileName.replaceFirst(uploadDir, "")
            // The read and the blocking PUT stay off the caller's (main) dispatcher.
            val uploaded = withContext(Dispatchers.IO) {
                runCatching {
                    put(
                        url = "https://$bucket.$endpoint/$objectPath",
                        bytes = file.readBytes(),
                        contentType = mimeOf(file),
                        accessKeyId = accessKeyId,
                        accessKeySecret = accessKeySecret,
                        securityToken = securityToken,
                        bucket = bucket,
                        objectPath = objectPath,
                    )
                }.getOrElse { false }
            }
            if (!uploaded) {
                throw ApiFailure(ApiFailureKind.Transport, "第 $index 张图片上传失败，请稍后重试")
            }
            result += "$prefix/$uploadFileName"
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
