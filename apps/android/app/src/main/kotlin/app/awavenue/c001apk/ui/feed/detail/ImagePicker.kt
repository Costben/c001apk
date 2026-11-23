package app.awavenue.c001apk.ui.feed.detail

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.awavenue.c001apk.media.splitMotionPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

private const val MAX_PICTURES = 9
private const val MAX_LIVE_VIDEO_DURATION_MS = 10_000L

/**
 * The system photo picker behind a plain launch callback. Each picked item is copied into the
 * app cache and described for the upload ticket, the way the Flutter client does when it reads
 * the bytes, decodes the size and hashes the file.
 */
@Composable
fun rememberImagePicker(
    maxCount: Int = MAX_PICTURES,
    onLoading: (String?) -> Unit,
    onPicked: (List<SelectedImage>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxCount.coerceAtLeast(2)),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = mutableListOf<SelectedImage>()
            for ((index, uri) in uris.withIndex()) {
                onLoading("正在加载图片: $index/${uris.size}")
                val item = withContext(Dispatchers.IO) { describe(context, uri) } ?: continue
                picked += item
            }
            onLoading(null)
            if (picked.isNotEmpty()) onPicked(picked)
        }
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

private fun describe(context: Context, uri: Uri): SelectedImage? = runCatching {
    val mimeType = context.contentResolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.')?.lowercase())
        ?: "image/png"
    val suffix = mimeType.substringAfter('/', "png").let { if (it == "jpeg") "jpg" else it }
    val sourceFile = File(context.cacheDir, "${UUID.randomUUID()}.$suffix")
    context.contentResolver.openInputStream(uri)?.use { input ->
        sourceFile.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    if (sourceFile.length() == 0L) {
        sourceFile.delete()
        return null
    }

    val stillFile = File(context.cacheDir, "${UUID.randomUUID()}.$suffix")
    val videoFile = File(context.cacheDir, "${UUID.randomUUID()}.mp4")
    val splitOk = splitMotionPhoto(sourceFile, stillFile, videoFile)
    val imageFile: File
    val validVideoFile: File?
    if (splitOk) {
        sourceFile.delete()
        imageFile = stillFile
        val durationMs = readVideoDurationMs(videoFile)
        if (durationMs != null && durationMs in 1L..MAX_LIVE_VIDEO_DURATION_MS) {
            validVideoFile = videoFile
        } else {
            videoFile.delete()
            validVideoFile = null
        }
    } else {
        stillFile.delete()
        videoFile.delete()
        imageFile = sourceFile
        validVideoFile = null
    }

    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(imageFile.absolutePath, options)
    val width = options.outWidth.coerceAtLeast(1)
    val height = options.outHeight.coerceAtLeast(1)
    val imageMd5 = md5Hex(imageFile)
    val liveVideo = validVideoFile?.let { file ->
        LivePhotoVideo(
            path = file.absolutePath,
            name = "${UUID.randomUUID().toString().replace("-", "")}.mp4",
            md5 = md5Hex(file),
        )
    }
    SelectedImage(
        path = imageFile.absolutePath,
        model = OssUploadModel(
            name = "${UUID.randomUUID().toString().replace("-", "")}.$suffix",
            resolution = "${width}x$height",
            md5 = imageMd5,
            livePhotoVideo = liveVideo?.name.orEmpty(),
        ),
        liveVideo = liveVideo,
        livePhotoEnabled = liveVideo != null,
    )
}.getOrNull()

private fun md5Hex(file: File): String {
    val digest = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(64 * 1024)
    file.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun readVideoDurationMs(file: File): Long? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } finally {
        runCatching { retriever.release() }
    }
}.getOrNull()
