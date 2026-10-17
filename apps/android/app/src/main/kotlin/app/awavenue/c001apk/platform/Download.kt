package app.awavenue.c001apk.platform

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.awavenue.c001apk.ui.common.openBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

fun downloadApk(context: Context, url: String, fileName: String) {
    val mimeType = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url))
    val request = DownloadManager.Request(Uri.parse(url))
        .setMimeType(mimeType)
        .setTitle(fileName)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        request.allowScanningByMediaScanner()
        request.setVisibleInDownloadsUi(true)
    }
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    if (runCatching { manager.enqueue(request) }.isFailure) {
        context.openBrowser(url)
    }
}

/**
 * The download behind a download button. Android 13 hides a download's progress and completion
 * notification without this permission, so it is asked for before the first transfer starts.
 */
@Composable
fun rememberApkDownload(): (url: String, fileName: String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let { (url, fileName) -> downloadApk(context, url, fileName) }
        pending = null
    }
    return { url, fileName ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = url to fileName
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            downloadApk(context, url, fileName)
        }
    }
}

suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
    OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        response.body.bytes()
    }
}
