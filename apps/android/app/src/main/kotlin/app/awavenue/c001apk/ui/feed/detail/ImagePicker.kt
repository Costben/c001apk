package app.awavenue.c001apk.ui.feed.detail

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

private const val MAX_PICTURES = 9

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
    val suffix = mimeType.substringAfter('/', "png")
    val file = File(context.cacheDir, "${UUID.randomUUID()}.$suffix")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    val bytes = file.readBytes()
    if (bytes.isEmpty()) {
        file.delete()
        return null
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val width = options.outWidth.coerceAtLeast(1)
    val height = options.outHeight.coerceAtLeast(1)
    val digest = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
    SelectedImage(
        path = file.absolutePath,
        model = OssUploadModel(
            name = "${UUID.randomUUID().toString().replace("-", "")}.$suffix",
            resolution = "${width}x$height",
            md5 = digest,
        ),
    )
}.getOrNull()
