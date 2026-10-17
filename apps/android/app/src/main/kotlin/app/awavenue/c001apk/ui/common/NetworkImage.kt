package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Remote image with a neutral placeholder; non-http URLs render the placeholder only. */
@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalPlatformContext.current
    val placeholder = Modifier.background(COUITheme.colorScheme.secondaryContainer)
    val request = remember(context, url) {
        url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { ImageRequest.Builder(context).data(it).crossfade(true).build() }
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.then(placeholder),
    )
}

const val SUFFIX_THUMBNAIL = ".s.jpg"

fun thumbnail(url: String): String = if (url.isEmpty() || url.endsWith(SUFFIX_THUMBNAIL)) url else url + SUFFIX_THUMBNAIL

/** Width and height encoded in Coolapk image URLs (`…@1080x2400.jpg`); 1×1 when absent. */
fun imageSize(url: String): Pair<Float, Float> {
    val at = url.lastIndexOf('@')
    val x = url.lastIndexOf('x')
    val dot = url.lastIndexOf('.')
    if (at == -1 || x <= at || dot <= x) return 1f to 1f
    val width = url.substring(at + 1, x).toFloatOrNull()
    val height = url.substring(x + 1, dot).toFloatOrNull()
    return if (width != null && height != null && width > 0 && height > 0) width to height else 1f to 1f
}
