package app.awavenue.c001apk.ui.feed.detail

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.awavenue.c001apk.network.CaptchaChallenge
import app.awavenue.c001apk.ui.LocalApp
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val CAPTCHA_BASE_URL = "https://captcha.c001apk.invalid/"

/** The NEC widget page. A native WebView never fires the platform-ready event, so it starts itself. */
private fun captchaHtml(captchaId: String): String = """<!doctype html><html lang="zh-CN"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font:16px sans-serif;margin:24px;color:#263238}#captcha{margin-top:24px}p{line-height:1.6}</style>
</head><body><p id="status">正在加载验证组件…</p><div id="captcha"></div>
<script>
function statusText(text){document.getElementById('status').textContent=text;}
function startCaptcha(){
  var timeout=setTimeout(function(){statusText('验证组件加载超时，请点击右上角重新加载');},15000);
  var script=document.createElement('script');
  script.src='https://cstaticdun.126.net/load.min.js';
  script.onerror=function(){clearTimeout(timeout);statusText('验证组件加载失败，请检查网络后重新加载');};
  script.onload=function(){
    if(typeof initNECaptcha!=='function'){clearTimeout(timeout);statusText('验证组件不可用，请重新加载');return;}
    initNECaptcha({captchaId:'$captchaId',element:'#captcha',
      mode:'embed',apiVersion:2,width:Math.min(320,window.innerWidth-48)+'px',lang:'zh-CN',
      onVerify:function(err,data){
        if(!err && data && data.validate){window.flutter_inappwebview.callHandler('captchaVerified',data.validate);}
        else{statusText('验证未通过，请按提示重试');}
      }
    },function(){clearTimeout(timeout);statusText('请按下方提示完成人机验证');},
      function(){clearTimeout(timeout);statusText('无法初始化验证，请检查网络后重新加载');});
  };
  document.head.appendChild(script);
}
startCaptcha();
</script></body></html>"""

/** The NEC challenge page. [onResult] receives the proof, or null when the user backs out. */
@Composable
fun CaptchaScreen(challenge: CaptchaChallenge, onResult: (String?) -> Unit) {
    val app = LocalApp.current
    var reloads by remember { mutableStateOf(0) }
    val html = remember(challenge.captchaId) { captchaHtml(challenge.captchaId) }

    // The page draws over the navigator rather than being stacked in it, so without this back would
    // pop the screen underneath while the challenge stayed up. Upstream pushes it as a route.
    BackHandler { onResult(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "人机验证",
                navigationIcon = {
                    IconButton(onClick = { onResult(null) }) {
                        Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { reloads++ }) {
                        Icon(COUIIcons.Refresh, contentDescription = "重新加载")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CaptchaWebView(
                html = remember(html) { html },
                userAgent = app.settings.deviceParams.userAgent,
                reloadKey = reloads,
                onVerified = { validate ->
                    challenge.proof(validate)?.let(onResult)
                },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaWebView(
    html: String,
    userAgent: String,
    reloadKey: Int,
    onVerified: (String) -> Unit,
) {
    val loadedKey = remember { intArrayOf(0) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = userAgent
                settings.javaScriptCanOpenWindowsAutomatically = false
                addJavascriptInterface(Bridge(this, onVerified), "flutter_inappwebview")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // The challenge never navigates the main frame; sub-frames stay allowed.
                        return request.isForMainFrame
                    }
                }
                loadDataWithBaseURL(CAPTCHA_BASE_URL, html, "text/html", "UTF-8", null)
            }
        },
        update = { view ->
            if (loadedKey[0] != reloadKey) {
                loadedKey[0] = reloadKey
                view.loadDataWithBaseURL(CAPTCHA_BASE_URL, html, "text/html", "UTF-8", null)
            }
        },
    )
}

/** Bridges the page's `window.flutter_inappwebview.callHandler` onto the main thread. */
private class Bridge(private val view: WebView, private val onVerified: (String) -> Unit) {
    @JavascriptInterface
    fun callHandler(name: String, value: String) {
        if (name == "captchaVerified" && value.isNotEmpty()) {
            view.post { onVerified(value) }
        }
    }
}

/**
 * Mounts the in-app verification once for the whole app and answers `ApiClient.captchaVerifier`.
 * It has to sit above every screen that can trigger a challenge.
 */
@Composable
fun CaptchaVerifierHost() {
    val app = LocalApp.current
    var challenge by remember { mutableStateOf<CaptchaChallenge?>(null) }
    var pending by remember { mutableStateOf<CancellableContinuation<String?>?>(null) }

    DisposableEffect(app) {
        app.apiClient.captchaVerifier = { requested ->
            suspendCancellableCoroutine { continuation ->
                pending?.cancel()
                pending = continuation
                challenge = requested
                continuation.invokeOnCancellation {
                    if (pending === continuation) {
                        pending = null
                        challenge = null
                    }
                }
            }
        }
        onDispose { app.apiClient.captchaVerifier = null }
    }

    challenge?.let { requested ->
        CaptchaScreen(requested) { proof ->
            val continuation = pending
            pending = null
            challenge = null
            if (continuation?.isActive == true) continuation.resume(proof)
        }
    }
}
