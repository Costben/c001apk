package app.awavenue.c001apk.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.nav.FFFListType
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Navigator
import app.awavenue.c001apk.ui.nav.Route
import java.net.URI
import java.net.URLDecoder

private const val COOLMARKET_SCHEME = "coolmarket://"

/**
 * Sends a Coolapk link to the screen that serves it, the way the Flutter client's
 * `Utils.onOpenLink` does. Returns false when nothing matched so the caller can fall back.
 */
fun openCoolapkLink(
    context: Context,
    navigator: Navigator,
    url: String,
    title: String = "",
    openInBrowser: Boolean = false,
): Boolean {
    if (url.isEmpty()) return false
    val path = coolapkPath(url)
    return when {
        path.startsWith("/feed/") -> {
            navigator.push(Route.Feed(path.removePrefix("/feed/").substringBefore('?').replace('?', '&')))
            true
        }

        path.startsWith("/u/") -> {
            navigator.push(Route.User(segment(path, "/u/")))
            true
        }

        path.startsWith("/apk/") -> {
            navigator.push(Route.Apk(segment(path, "/apk/")))
            true
        }

        path.startsWith("/game/") -> {
            navigator.push(Route.Apk(segment(path, "/game/")))
            true
        }

        path.startsWith("/t/") -> {
            val tag = segment(path, "/t/").replace(Regex("\\?type=[A-Za-z0-9]+"), "")
            // type=8 is the picture topic, which has its own screen rather than the tag page.
            if (path.contains("type=8")) navigator.push(Route.CoolPic(tag)) else navigator.push(Route.Topic(tag))
            true
        }

        path.startsWith("/product/") -> {
            navigator.push(Route.Product(segment(path, "/product/")))
            true
        }

        path.startsWith("/page?url=") -> {
            navigator.push(Route.Carousel(url = path.removePrefix("/page?url="), title = title, isInit = true))
            true
        }

        path.startsWith("#/") -> {
            navigator.push(Route.Carousel(url = path.removePrefix("#"), title = title, isInit = true))
            true
        }

        path.startsWith("/user/") -> {
            navigator.push(Route.FFFList(FFFListType.UserFollow))
            true
        }

        path.startsWith("/dyh/") -> {
            navigator.push(Route.Dyh(id = segment(path, "/dyh/"), title = title))
            true
        }

        url.startsWith("http") -> {
            if (openInBrowser) context.openBrowser(url) else navigator.push(Route.Web(url))
            true
        }

        else -> {
            context.toast("暂不支持此链接")
            false
        }
    }
}

/** The same dispatcher bound to the current screen, for tap handlers to hold on to. */
@Composable
fun rememberLinkOpener(): (String, String) -> Boolean {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val openInBrowser = LocalApp.current.settings.openInBrowser
    return remember(navigator, context, openInBrowser) {
        { url, title -> openCoolapkLink(context, navigator, url, title, openInBrowser) }
    }
}

/** The in-app path behind a link, whether it is a bare path, a `coolmarket://` URI or a website URL. */
private fun coolapkPath(url: String): String {
    if (url.startsWith(COOLMARKET_SCHEME)) return url.removePrefix("coolmarket://").let { "/$it" }
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    if (uri.host?.contains("coolapk") != true) return url
    val query = uri.rawQuery
    return if (query.isNullOrEmpty()) uri.path.orEmpty() else "${uri.path}?$query"
}

private fun segment(path: String, prefix: String): String {
    val raw = path.removePrefix(prefix).substringBefore('?')
    // Paths arrive percent-encoded; a literal '+' is a plus, not a space, in a path segment.
    return runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
}
