package app.awavenue.c001apk.ui.common

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import app.awavenue.c001apk.C001apkApp
import app.awavenue.c001apk.media.MotionPhotoExporter
import app.awavenue.c001apk.media.MotionPhotoParser
import app.awavenue.c001apk.media.MotionPhotoRemuxer
import app.awavenue.c001apk.ui.image.cacheImageFile
import app.awavenue.c001apk.ui.image.cacheLivePhotoVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

private const val ALBUM = "c001apk"
private const val LIVE_SAVE_TAG = "LivePhotoSave"
private val saveSequence = AtomicInteger(0)

private fun uniqueSaveName(prefix: String, ext: String): String {
    val seq = saveSequence.incrementAndGet() and 0xfff
    return "${prefix}_${System.currentTimeMillis()}_$seq.$ext"
}

/** Downloads a picture and files it into the gallery album the Flutter client uses. */
suspend fun saveImage(context: Context, url: String): Boolean {
    val bytes = downloadBytes(context, url) ?: return false
    return saveBytes(context, url, bytes)
}

/**
 * Saves the full-resolution file behind a display variant. Coolapk hands the app a `@WxH` address
 * and lists request an `.s.jpg` thumbnail on top of it; both are stripped first. If the original
 * address is not served, the variant that was on screen is saved instead.
 */
suspend fun saveOriginalImage(context: Context, url: String): Boolean {
    val app = context.applicationContext as? C001apkApp
    val target = originalImageUrl(url)
    if (app != null) {
        val cached = cacheImageFile(context, app, target)
            ?: (if (target != url) cacheImageFile(context, app, url) else null)
        if (cached != null) {
            val ext = extensionFor(target)
            return saveFileToGallery(
                context = context,
                file = cached,
                displayName = uniqueSaveName("c001apk", ext),
                mimeType = mimeType(target),
            )
        }
    }
    val bytes = downloadBytes(context, target)
        ?: (if (target != url) downloadBytes(context, url) else null)
        ?: return false
    return saveBytes(context, target, bytes)
}

private suspend fun saveBytes(context: Context, url: String, bytes: ByteArray): Boolean =
    withContext(Dispatchers.IO) {
        writeStreamToGallery(
            context = context,
            displayName = uniqueSaveName("c001apk", extensionFor(url)),
            mimeType = mimeType(url),
        ) { out -> out.write(bytes) }
    }

private suspend fun saveFileToGallery(
    context: Context,
    file: File,
    displayName: String,
    mimeType: String,
): Boolean = withContext(Dispatchers.IO) {
    writeStreamToGallery(context, displayName, mimeType) { out ->
        file.inputStream().use { input -> input.copyTo(out) }
    }
}

/** Saves the picture into the app cache so the share sheet can hand it to another app. */
suspend fun shareImage(context: Context, url: String): Boolean {
    val bytes = downloadBytes(context, url) ?: return false
    return withContext(Dispatchers.IO) {
        runCatching {
            // Only cache/shared/ is exposed through the FileProvider (res/xml/file_paths.xml).
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, fileName(url))
            file.writeBytes(bytes)
            withContext(Dispatchers.Main) { context.shareFile(file, mimeType(url), url) }
            true
        }.getOrDefault(false)
    }
}

/**
 * 将酷安实况图（静图原图 + 附带实况视频）规范化重封装并合成为 Android 通用 Motion Photo JPEG
 * （直接采用 Cobo COUI 的合成管线，兼容 ColorOS / Google Photos 等系统相册识别播放）。
 */
suspend fun saveLivePhoto(context: Context, imageUrl: String): Boolean {
    val app = context.applicationContext as? C001apkApp ?: return false
    return withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "livephoto-export-${System.nanoTime()}")
        workDir.mkdirs()
        try {
            val targetUrl = originalImageUrl(imageUrl)
            val stillFile = cacheImageFile(context, app, targetUrl)
                ?: if (targetUrl != imageUrl) cacheImageFile(context, app, imageUrl) else null
            if (stillFile == null) {
                Log.w(LIVE_SAVE_TAG, "实况静图下载失败: $targetUrl")
                return@withContext false
            }

            val jpegFile = MotionPhotoRemuxer.ensureJpeg(stillFile, File(workDir, "still.jpg"))
            val existingInfo = MotionPhotoParser.parse(jpegFile, expectMotionPhoto = true)

            val mp4File = if (existingInfo.embeddedVideoLength != null) {
                null
            } else {
                val rawVideo = cacheLivePhotoVideo(context, app, imageUrl)
                    ?: if (targetUrl != imageUrl) cacheLivePhotoVideo(context, app, targetUrl) else null
                if (rawVideo == null) {
                    Log.w(LIVE_SAVE_TAG, "实况视频下载失败: $imageUrl")
                    return@withContext false
                }
                MotionPhotoRemuxer.prepareMp4(rawVideo, File(workDir, "motion.mp4"))
            }

            val exported = MotionPhotoExporter.export(
                sourceImage = jpegFile,
                video = mp4File,
                destination = File(workDir, "export.jpg"),
                presentationTimestampUs = existingInfo.presentationTimestampUs,
            )
            saveFileToGallery(
                context = context,
                file = exported,
                displayName = uniqueSaveName("c001apk_live", "jpg"),
                mimeType = "image/jpeg",
            )
        } catch (error: Throwable) {
            Log.w(LIVE_SAVE_TAG, "实况图合成保存失败: $imageUrl", error)
            false
        } finally {
            workDir.deleteRecursively()
        }
    }
}

private suspend fun downloadBytes(context: Context, url: String): ByteArray? {
    val app = context.applicationContext as? C001apkApp ?: return null
    return runCatching { app.apiClient.getImageBytes(url) }.getOrNull()
}

private fun fileName(url: String): String =
    url.substringAfterLast('/').substringBefore('?').ifEmpty { "image.jpg" }

private fun extensionFor(url: String): String {
    val ext = fileName(url).substringAfterLast('.', "")
    return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ext.lowercase() else "jpg"
}

private fun mimeType(url: String): String {
    val extension = extensionFor(url)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
}

private fun writeStreamToGallery(
    context: Context,
    displayName: String,
    mimeType: String,
    write: (OutputStream) -> Unit,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return saveToPublicPictures(context, displayName, write)
    }
    return runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        try {
            val out = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("无法打开相册输出流")
            out.use(write)
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }.onFailure { err ->
        Log.w(LIVE_SAVE_TAG, "写入系统相册失败: $displayName", err)
    }.getOrDefault(false)
}

/** Below Q the public album needs WRITE_EXTERNAL_STORAGE, which the app does not declare. */
@Suppress("DEPRECATION")
private fun saveToPublicPictures(
    context: Context,
    displayName: String,
    write: (OutputStream) -> Unit,
): Boolean = runCatching {
    val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
    if (!album.exists() && !album.mkdirs()) return false
    val file = File(album, displayName)
    file.outputStream().use(write)
    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    true
}.getOrDefault(false)
