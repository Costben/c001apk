package app.awavenue.c001apk.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** The shared side of every screen that talks to the system: hints, clipboard, share, browser. */

fun Context.toast(message: String) {
    if (message.isEmpty()) return
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.copyText(text: String, hint: String = "已复制") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("c001apk", text))
    toast(hint)
}

fun Context.openBrowser(url: String) {
    if (url.isEmpty()) return
    val target = if (url.startsWith("http")) url else "https://www.coolapk.com$url"
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
        .onFailure { toast("没有可用的浏览器") }
}

fun Context.shareText(text: String, subject: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    runCatching { startActivity(Intent.createChooser(intent, null)) }
}

/** Shares a locally written file; the caller supplies a cache path it can write to. */
fun Context.shareFile(file: File, mimeType: String, subject: String? = null) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(Intent.createChooser(intent, null)) }
}

/** True when the scheme opens outside the app rather than in the in-app browser. */
fun isExternalLink(url: String) = url.startsWith("http") && !url.contains("coolapk.com")
