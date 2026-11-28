package app.awavenue.c001apk.data

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.FileProvider
import app.awavenue.c001apk.C001apkApp
import app.awavenue.c001apk.platform.PackageInstallSessionReceiver
import app.awavenue.c001apk.platform.ShizukuInstaller
import app.awavenue.c001apk.ui.common.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
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

    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Triggers package installation for [apkFile]: tries Shizuku silent installation first when
     * enabled and permitted, then any user-selected installer package, and finally falls back across
     * `ACTION_VIEW`, `ACTION_INSTALL_PACKAGE`, and Android's native [PackageInstaller] session API.
     */
    fun installApk(context: Context, apkFile: File) {
        val appContext = context.applicationContext
        val settings = (appContext as? C001apkApp)?.settings
        val preferredPackage = settings?.preferredInstallerPackage.orEmpty()
        if (settings?.shizukuInstall == true &&
            ShizukuInstaller.isShizukuAvailable() &&
            ShizukuInstaller.hasShizukuPermission()
        ) {
            installScope.launch {
                appContext.toast("正在通过 Shizuku 静默安装…")
                ShizukuInstaller.installApkSilently(appContext, apkFile)
                    .onSuccess {
                        appContext.toast("Shizuku 静默安装完成")
                    }
                    .onFailure { err ->
                        appContext.toast("Shizuku 安装失败（${err.message ?: "未知错误"}），正在尝试备用安装器")
                        launchExternalOrSessionInstaller(context, apkFile, preferredPackage)
                    }
            }
            return
        }
        launchExternalOrSessionInstaller(context, apkFile, preferredPackage)
    }

    private fun launchExternalOrSessionInstaller(
        context: Context,
        apkFile: File,
        preferredPackage: String,
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        fun viewIntent(targetPackage: String? = null) = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!targetPackage.isNullOrEmpty()) setPackage(targetPackage)
        }
        @Suppress("DEPRECATION")
        fun installIntent(targetPackage: String? = null) = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!targetPackage.isNullOrEmpty()) setPackage(targetPackage)
        }

        val candidates = buildList {
            if (preferredPackage.isNotEmpty()) {
                add(viewIntent(preferredPackage))
                add(installIntent(preferredPackage))
            }
            add(viewIntent())
            add(installIntent())
        }
        val pm = context.packageManager
        for (intent in candidates) {
            if (intent.resolveActivity(pm) == null) continue
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next installer mechanism.
            } catch (_: SecurityException) {
                // Try the next installer mechanism if targeted installer denies access.
            }
        }
        installViaPackageInstallerSession(context.applicationContext, apkFile)
    }

    private fun installViaPackageInstallerSession(appContext: Context, apkFile: File) {
        installScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val installer = appContext.packageManager.packageInstaller
                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    val sessionId = installer.createSession(params)
                    var committed = false
                    val session = installer.openSession(sessionId)
                    try {
                        session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                            FileInputStream(apkFile).use { input ->
                                input.copyTo(out)
                            }
                            session.fsync(out)
                        }
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                        val callbackIntent = Intent(appContext, PackageInstallSessionReceiver::class.java)
                        val sender = PendingIntent.getBroadcast(appContext, sessionId, callbackIntent, flags).intentSender
                        session.commit(sender)
                        committed = true
                    } finally {
                        if (!committed) runCatching { session.abandon() }
                        runCatching { session.close() }
                    }
                }
            }.onFailure {
                appContext.toast("无法调起系统安装器：${it.message ?: "请检查安装权限"}")
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
