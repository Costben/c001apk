package app.awavenue.c001apk.ui.feed.detail

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.awavenue.c001apk.C001apkApp
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.MoreMenu
import app.awavenue.c001apk.ui.common.appDialogButtonColors
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.openBrowser
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalNavigator
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val COOLAPK_HOME = "https://www.coolapk.com/"

private class DownloadRequest(val url: String, val name: String, val size: Long)

/**
 * The in-app browser, ported from the Flutter `WebviewPage`: session cookies are prepared before
 * the first load, reaching the Coolapk home URL finishes a web login, and downloads become a
 * confirmed DownloadManager entry.
 */
@Composable
fun WebScreen(url: String, isLogin: Boolean = false) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val backdrop = rememberGlassBackdrop()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var currentUrl by remember { mutableStateOf(url) }
    var menuOpen by remember { mutableStateOf(false) }
    var cookiesReady by remember { mutableStateOf(false) }
    var cookiesFailed by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf<DownloadRequest?>(null) }
    var externalLink by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        val ok = withContext(Dispatchers.IO) { runCatching { prepareWebSession(app, includeAccount = !isLogin) }.isSuccess }
        if (ok) cookiesReady = true else cookiesFailed = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = title,
                    gradientGlass = true,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        Box {
                            TopBarIconButton(onClick = { menuOpen = true }) {
                                Icon(COUIIcons.More, contentDescription = "更多")
                            }
                            MoreMenu(
                                show = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                entries = listOf(
                                    "刷新" to { webView?.reload() },
                                    "复制" to { context.copyText(currentUrl) },
                                    "在浏览器中打开" to { context.openBrowser(currentUrl) },
                                    "清理缓存" to {
                                        runCatching {
                                            webView?.clearCache(true)
                                            webView?.clearHistory()
                                        }.onSuccess { context.toast("已清理") }
                                            .onFailure { context.toast("缓存清理失败，请稍后重试") }
                                    },
                                    "返回上一页" to {
                                        val view = webView
                                        if (view != null && view.canGoBack()) view.goBack()
                                    },
                                ),
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (progress < 1f) {
                LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    cookiesFailed -> Text(
                        "网页登录状态准备失败，请返回后重试",
                        modifier = Modifier.align(Alignment.Center),
                    )

                    !cookiesReady -> Unit

                    else -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.userAgentString = app.settings.deviceParams.userAgent
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    settings.forceDark = WebSettings.FORCE_DARK_AUTO
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, address: String) {
                                        currentUrl = address
                                        progress = 1f
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView,
                                        handler: SslErrorHandler,
                                        error: SslError,
                                    ) {
                                        handler.cancel()
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val target = request.url.toString()
                                        if (!target.startsWith("http")) {
                                            externalLink = target
                                            return true
                                        }
                                        if (isLogin && target == COOLAPK_HOME) {
                                            val uid = cookieValue(COOLAPK_HOME, "uid")
                                            val username = cookieValue(COOLAPK_HOME, "username")
                                            val token = cookieValue(COOLAPK_HOME, "token")
                                            if (!uid.isNullOrEmpty() && !username.isNullOrEmpty() && !token.isNullOrEmpty()) {
                                                runCatching { app.repository.saveWebLogin(uid, username, token) }
                                                    .onFailure { context.toast("无法保存网页登录状态，请重试") }
                                            }
                                            navigator.pop()
                                            return true
                                        }
                                        return false
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        progress = newProgress / 100f
                                    }

                                    override fun onReceivedTitle(view: WebView, value: String?) {
                                        title = value.orEmpty()
                                    }
                                }
                                setDownloadListener(DownloadListener { link, _, disposition, mimeType, length ->
                                    val guessed = URLUtil.guessFileName(link, disposition, mimeType)
                                    val name = runCatching { Uri.decode(guessed) }.getOrDefault(guessed)
                                    download = DownloadRequest(link, name, length)
                                })
                                loadUrl(url, mapOf("X-Requested-With" to Protocol.APP_ID))
                                webView = this
                            }
                        },
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    download?.let { request ->
        OverlayDialog(
            show = true,
            title = "下载文件 ${request.name}？",
            onDismissRequest = { download = null },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(
                    request.url,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = COUITheme.textStyles.footnote1,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(text = "关闭", onClick = { download = null })
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "下载（${formatSize(request.size.toDouble())}）",
                        onClick = {
                            download = null
                            context.copyText(request.url)
                            enqueueDownload(context, request)
                        },
                    )
                }
            }
        }
    }

    externalLink?.let { target ->
        OverlayAlertDialog(
            buttonColors = appDialogButtonColors(),
            show = true,
            title = "当前网页将要打开外部链接，是否打开",
            confirmText = "打开",
            cancelText = "关闭",
            onConfirm = {
                externalLink = null
                context.openBrowser(target)
            },
            onCancel = { externalLink = null },
        )
    }
}

/** Clears the previous account's Coolapk cookies, then plants this session's, as upstream does. */
/** A web login starts signed out, so it can add a different account than the active one. */
private fun prepareWebSession(app: C001apkApp, includeAccount: Boolean) {
    val manager = CookieManager.getInstance()
    manager.setAcceptCookie(true)
    for (name in listOf("uid", "username", "token", "SESSID", "ddid")) {
        manager.setCookie(COOLAPK_HOME, "$name=; domain=.coolapk.com; path=/; max-age=0")
        for (host in listOf("www.coolapk.com", "account.coolapk.com", "coolapk.com")) {
            manager.setCookie("https://$host/", "$name=; domain=$host; path=/; max-age=0")
        }
    }
    if (!includeAccount || !app.session.isLogin) {
        manager.flush()
        return
    }
    val values = buildList {
        add("DID" to app.settings.szlmId)
        add("forward" to "https://www.coolapk.com")
        add("displayVersion" to "v16")
        add("uid" to app.session.uid)
        add("username" to app.session.username)
        add("token" to app.session.token)
        val sessid = app.session.sessid
        if (sessid.startsWith("SESSID=")) add("SESSID" to sessid.substring("SESSID=".length))
    }
    for ((name, value) in values) {
        if (value.isNotEmpty()) {
            manager.setCookie(COOLAPK_HOME, "$name=$value; domain=.coolapk.com; path=/; secure")
        }
    }
    manager.flush()
}

private fun cookieValue(url: String, name: String): String? =
    CookieManager.getInstance().getCookie(url)
        ?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')

private fun enqueueDownload(context: Context, request: DownloadRequest) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    if (manager == null) {
        context.openBrowser(request.url)
        return
    }
    runCatching {
        val target = DownloadManager.Request(Uri.parse(request.url))
            .setTitle(request.name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            target.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, request.name)
        } else {
            // Public Downloads needs WRITE_EXTERNAL_STORAGE below Q, which the app does not hold.
            target.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, request.name)
        }
        manager.enqueue(target)
    }.onFailure { context.openBrowser(request.url) }
}

/** Byte size the way the Flutter client prints it. */
private fun formatSize(value: Double): String {
    val units = listOf("B", "K", "M", "G")
    var size = value
    var index = 0
    while (size > 1024 && index < units.size - 1) {
        index++
        size /= 1024
    }
    return String.format(Locale.US, "%.2f%s", size, units[index])
}
