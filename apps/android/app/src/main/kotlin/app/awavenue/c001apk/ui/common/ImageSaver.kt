package app.awavenue.c001apk.ui.common

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import app.awavenue.c001apk.C001apkApp
import app.awavenue.c001apk.media.MotionPhotoExporter
import app.awavenue.c001apk.media.MotionPhotoParser
import app.awavenue.c001apk.media.hasJpegHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val ALBUM = "c001apk"

/** Downloads a picture and files it into the gallery album the Flutter client uses. */
suspend fun saveImage(context: Context, url: String): Boolean {
    val bytes = downloadBytes(context, url) ?: return false
    return withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, url, bytes)
        } else {
            saveToPublicPictures(context, url, bytes)
        }
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
 * Rebuilds a live photo as a motion photo: the still JPEG with the MP4 appended and XMP describing
 * it, so the gallery plays it the way the phone that took it would.
 */
suspend fun saveLivePhoto(context: Context, imageUrl: String): Boolean {
    val app = context.applicationContext as? C001apkApp ?: return false
    return withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "livephoto-save")
        dir.deleteRecursively()
        dir.mkdirs()
        val image = File(dir, "source.jpg")
        val video = File(dir, "motion.mp4")
        val output = File(dir, "export.jpg")
        try {
            image.writeBytes(app.apiClient.getImageBytes(imageUrl))
            if (!image.hasJpegHeader()) return@withContext false
            val info = MotionPhotoParser.parse(image, expectMotionPhoto = true)
            val motionVideo = if (info.embeddedVideoLength != null) {
                null
            } else {
                val url = app.repository.livePhotoVideoUrl(imageUrl) ?: return@withContext false
                video.writeBytes(app.apiClient.getImageBytes(url))
                video
            }
            MotionPhotoExporter.export(image, motionVideo, output, info.presentationTimestampUs)
            val bytes = output.readBytes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(context, imageUrl, bytes)
            } else {
                saveToPublicPictures(context, imageUrl, bytes)
            }
        } catch (_: Exception) {
            false
        } finally {
            dir.deleteRecursively()
        }
    }
}

private suspend fun downloadBytes(context: Context, url: String): ByteArray? {
    val app = context.applicationContext as? C001apkApp ?: return null
    return runCatching { app.apiClient.getImageBytes(url) }.getOrNull()
}

private fun fileName(url: String): String =
    url.substringAfterLast('/').substringBefore('?').ifEmpty { "image.jpg" }

private fun mimeType(url: String): String {
    val extension = fileName(url).substringAfterLast('.', "")
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "image/jpeg"
}

private fun saveToMediaStore(context: Context, url: String, bytes: ByteArray): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName(url))
        put(MediaStore.Images.Media.MIME_TYPE, mimeType(url))
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    return runCatching {
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(target)?.use { it.write(bytes) } ?: return false
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        true
    }.getOrDefault(false)
}

/** Below Q the public album needs WRITE_EXTERNAL_STORAGE, which the app does not declare. */
@Suppress("DEPRECATION")
private fun saveToPublicPictures(context: Context, url: String, bytes: ByteArray): Boolean = runCatching {
    val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
    if (!album.exists() && !album.mkdirs()) return false
    val file = File(album, fileName(url))
    file.writeBytes(bytes)
    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    true
}.getOrDefault(false)
