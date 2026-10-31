package app.awavenue.c001apk.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface InAppUpdateState {
    data object Idle : InAppUpdateState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : InAppUpdateState
    data class ReadyToInstall(val apkFile: File) : InAppUpdateState
    data class Error(val message: String) : InAppUpdateState
}

object InAppUpdater {
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Downloads an update APK into the updates cache directory with progress reporting.
     * Reuses an already fully downloaded valid APK of the same version if present.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        version: String,
        onProgress: (InAppUpdateState) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "c001apk-$version.apk")
        val tmpFile = File(updatesDir, "c001apk-$version.apk.tmp")

        // Reuse if already complete and valid
        if (apkFile.exists() && apkFile.length() > 1024 * 1024) {
            if (isThisApp(context, apkFile)) {
                onProgress(InAppUpdateState.ReadyToInstall(apkFile))
                return@withContext apkFile
            } else {
                apkFile.delete()
            }
        }

        try {
            // The UI already switched to the progress layout when the button was pressed; this
            // second one covers the retry path, which starts from the error card.
            onProgress(InAppUpdateState.Downloading(progress = -1f, downloadedBytes = 0, totalBytes = 0))

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "c001apk-updater")
                .build()

            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                onProgress(InAppUpdateState.Error("下载失败：HTTP ${response.code}"))
                return@withContext null
            }

            val body = response.body
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            tmpFile.delete()
            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100 || downloadedBytes == totalBytes) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            onProgress(InAppUpdateState.Downloading(progress, downloadedBytes, totalBytes))
                        }
                    }
                    output.flush()
                }
            }

            if (totalBytes > 0 && downloadedBytes < totalBytes) {
                tmpFile.delete()
                onProgress(InAppUpdateState.Error("下载失败：文件传输未完成"))
                return@withContext null
            }

            apkFile.delete()
            if (!tmpFile.renameTo(apkFile)) {
                tmpFile.copyTo(apkFile, overwrite = true)
                tmpFile.delete()
            }

            val archive = runCatching {
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }.getOrNull()
            if (archive == null) {
                apkFile.delete()
                onProgress(InAppUpdateState.Error("下载的安装包校验失败，请重试"))
                return@withContext null
            }
            // The release publishes its APK under a name that used to belong to the legacy Flutter
            // build, so the file is checked to be this very app before it is offered to the installer.
            val archivePackage = archive.packageName.ifEmpty {
                archive.applicationInfo?.packageName.orEmpty()
            }
            if (archivePackage.isNotEmpty() && archivePackage != context.packageName) {
                apkFile.delete()
                onProgress(InAppUpdateState.Error("下载到的不是这个应用的安装包，已停止"))
                return@withContext null
            }

            onProgress(InAppUpdateState.ReadyToInstall(apkFile))
            apkFile
        } catch (e: CancellationException) {
            tmpFile.delete()
            throw e
        } catch (e: Exception) {
            tmpFile.delete()
            onProgress(InAppUpdateState.Error("下载出错：${e.message ?: "网络异常"}"))
            null
        }
    }

    /**
     * True when [file] parses as an APK of this very app.
     *
     * The release publishes its APK under `c001apk-<version>.apk`, a name the legacy Flutter build
     * also used, so a file is only offered to the installer once it says it is this package. A
     * missing package name is let through: that means the parser could not read it, and the install
     * itself will refuse.
     */
    private fun isThisApp(context: Context, file: File): Boolean {
        val archive = runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }.getOrNull() ?: return false
        val packageName = archive.packageName.ifEmpty { archive.applicationInfo?.packageName.orEmpty() }
        return packageName.isEmpty() || packageName == context.packageName
    }

    /**
     * Triggers the Android system package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
