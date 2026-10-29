package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

/** Remote image with a neutral placeholder; non-http URLs render the placeholder only. */
@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true,
) {
    val context = LocalPlatformContext.current
    val placeholder = Modifier.background(COUITheme.colorScheme.secondaryContainer)
    // A dropped connection used to leave the slot blank until the card was composed again.
    // One quiet retry covers those transient failures; after that the address is tried without its
    // size suffix, because a thumbnail that has expired often still resolves as the original.
    val original = remember(url) { originalImageUrl(url) }
    val lastAttempt = if (original == url) 1 else 2
    var attempt by remember(url) { mutableIntStateOf(0) }
    var retry by remember(url) { mutableStateOf(false) }
    val address = if (attempt >= 2) original else url
    val request = remember(context, address, crossfade, attempt) {
        address.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { ImageRequest.Builder(context).data(it).crossfade(crossfade).build() }
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.then(placeholder),
        onError = { if (attempt < lastAttempt) retry = true },
    )
    LaunchedEffect(retry) {
        if (!retry) return@LaunchedEffect
        delay(600)
        retry = false
        attempt += 1
    }
}

const val SUFFIX_THUMBNAIL = ".s.jpg"

/** Inserts the thumbnail suffix before any query string, so `…jpg?t=1` stays a valid URL. */
fun thumbnail(url: String): String {
    if (url.isEmpty()) return url
    val query = url.indexOf('?')
    val base = if (query >= 0) url.substring(0, query) else url
    val suffix = if (query >= 0) url.substring(query) else ""
    return if (base.endsWith(SUFFIX_THUMBNAIL)) url else base + SUFFIX_THUMBNAIL + suffix
}

/**
 * The address of the original file behind a Coolapk picture: the app is handed a display variant
 * with a `@WxH` size suffix, and lists ask for the smaller `.s.jpg` thumbnail on top of it.
 * Anything that is not a Coolapk address is left untouched.
 */
fun originalImageUrl(url: String): String {
    if (url.isEmpty() || !url.contains("coolapk.com")) return url
    val query = url.indexOf('?')
    var base = if (query >= 0) url.substring(0, query) else url
    val suffix = if (query >= 0) url.substring(query) else ""
    if (base.endsWith(SUFFIX_THUMBNAIL)) base = base.removeSuffix(SUFFIX_THUMBNAIL)
    base = base.replace(Regex("""@\d+x\d+(?=\.[A-Za-z0-9]+$)"""), "")
    return base + suffix
}

/** Width and height encoded in Coolapk image URLs (`…@1080x2400.jpg`); 1×1 when absent. */
fun imageSize(url: String): Pair<Float, Float> {
    val path = url.substringBefore('?')
    val at = path.lastIndexOf('@')
    val x = path.lastIndexOf('x')
    val dot = path.lastIndexOf('.')
    if (at == -1 || x <= at || dot <= x) return 1f to 1f
    val width = path.substring(at + 1, x).toFloatOrNull()
    val height = path.substring(x + 1, dot).toFloatOrNull()
    return if (width != null && height != null && width > 0 && height > 0) width to height else 1f to 1f
}
