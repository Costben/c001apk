package app.awavenue.c001apk.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.data.InAppUpdater
import app.awavenue.c001apk.ui.common.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Resolving(val versionCode: Long) : ApkDownloadState
    data class Downloading(
        val versionCode: Long,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : ApkDownloadState

    data class ReadyToInstall(
        val versionCode: Long,
        val file: File,
    ) : ApkDownloadState

    data class Failed(
        val versionCode: Long,
        val message: String,
    ) : ApkDownloadState
}

/**
 * In-app APK downloader and installer for Coolapk apps.
 *
 * Keeps transfers alive across LazyColumn recycling and screen transitions, stores APKs under
 * `cacheDir/updates/` (already exposed by FileProvider), reports throttled progress back on the
 * main dispatcher, and triggers the system package installer once verification passes.
 */
object ApkDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val resolvedUrls = ConcurrentHashMap<String, String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val states = mutableStateMapOf<String, ApkDownloadState>()

    fun stateOf(packageName: String, versionCode: Long): ApkDownloadState {
        val current = states[packageName] ?: return ApkDownloadState.Idle
        return when (current) {
            is ApkDownloadState.Resolving -> if (current.versionCode == versionCode) current else ApkDownloadState.Idle
            is ApkDownloadState.Downloading -> if (current.versionCode == versionCode) current else ApkDownloadState.Idle
            is ApkDownloadState.ReadyToInstall -> {
                if (current.versionCode == versionCode && current.file.exists()) current else ApkDownloadState.Idle
            }
            is ApkDownloadState.Failed -> if (current.versionCode == versionCode) current else ApkDownloadState.Idle
            ApkDownloadState.Idle -> ApkDownloadState.Idle
        }
    }

    fun startOrToggle(
        context: Context,
        repository: CoolapkRepository,
        userAgent: String,
        packageName: String,
        appId: Int,
        versionCode: Long,
    ) {
        val appContext = context.applicationContext
        val current = stateOf(packageName, versionCode)
        when (current) {
            is ApkDownloadState.Resolving,
            is ApkDownloadState.Downloading,
            -> {
                cancel(packageName)
                appContext.toast("已取消下载")
                return
            }

            is ApkDownloadState.ReadyToInstall -> {
                launchInstaller(context, current.file)
                return
            }

            ApkDownloadState.Idle,
            is ApkDownloadState.Failed,
            -> Unit
        }

        jobs.remove(packageName)?.cancel()
        states[packageName] = ApkDownloadState.Resolving(versionCode)

        val job = scope.launch {
            val cacheKey = "$packageName:$versionCode"
            val apkFile = cachedApkFile(appContext, packageName, versionCode)
            val tmpFile = File(apkFile.parentFile, "${apkFile.name}.tmp")
            try {
                // Reuse an already downloaded, verified APK of the exact same package & version.
                val cachedValid = withContext(Dispatchers.IO) {
                    apkFile.exists() && apkFile.length() > 0L && verifyApk(appContext, apkFile, packageName)
                }
                if (cachedValid) {
                    states[packageName] = ApkDownloadState.ReadyToInstall(versionCode, apkFile)
                    launchInstaller(context, apkFile)
                    return@launch
                }

                val targetUrl = resolvedUrls[cacheKey] ?: repository.appDownloadUrl(
                    packageName = packageName,
                    id = appId,
                    versionCode = versionCode.toInt(),
                )?.also { resolvedUrls[cacheKey] = it }

                if (targetUrl.isNullOrBlank()) {
                    states[packageName] = ApkDownloadState.Failed(versionCode, "无法获取下载链接")
                    appContext.toast("无法获取下载链接，请稍后重试")
                    return@launch
                }

                states[packageName] = ApkDownloadState.Downloading(
                    versionCode = versionCode,
                    progress = -1f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                )

                val downloaded = withContext(Dispatchers.IO) {
                    streamDownload(
                        url = targetUrl,
                        userAgent = userAgent,
                        tmpFile = tmpFile,
                        destFile = apkFile,
                        onProgress = { progress, readBytes, totalBytes ->
                            scope.launch {
                                if (states[packageName] is ApkDownloadState.Downloading) {
                                    states[packageName] = ApkDownloadState.Downloading(
                                        versionCode = versionCode,
                                        progress = progress,
                                        downloadedBytes = readBytes,
                                        totalBytes = totalBytes,
                                    )
                                }
                            }
                        },
                    )
                }

                if (!downloaded) {
                    states[packageName] = ApkDownloadState.Failed(versionCode, "下载未完成")
                    appContext.toast("下载未完成，请重试")
                    return@launch
                }

                val valid = withContext(Dispatchers.IO) { verifyApk(appContext, apkFile, packageName) }
                if (!valid) {
                    withContext(Dispatchers.IO) { apkFile.delete() }
                    states[packageName] = ApkDownloadState.Failed(versionCode, "安装包校验失败")
                    appContext.toast("安装包校验失败，请重试")
                    return@launch
                }

                states[packageName] = ApkDownloadState.ReadyToInstall(versionCode, apkFile)
                launchInstaller(context, apkFile)
            } catch (e: CancellationException) {
                withContext(Dispatchers.IO) { tmpFile.delete() }
                if (states[packageName] !is ApkDownloadState.ReadyToInstall) {
                    states.remove(packageName)
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { tmpFile.delete() }
                resolvedUrls.remove(cacheKey)
                val msg = e.message?.takeIf { it.isNotBlank() } ?: "网络异常"
                states[packageName] = ApkDownloadState.Failed(versionCode, msg)
                appContext.toast("下载失败：$msg")
            } finally {
                jobs.remove(packageName)
            }
        }
        jobs[packageName] = job
    }

    fun cancel(packageName: String) {
        jobs.remove(packageName)?.cancel()
        states.remove(packageName)
    }

    /** Clears state and removes the cached APK once [packageName] is installed at >= target version. */
    fun onPackageInstalled(context: Context, packageName: String, installedVersionCode: Long) {
        val current = states[packageName]
        val targetVersion = when (current) {
            is ApkDownloadState.ReadyToInstall -> current.versionCode
            is ApkDownloadState.Failed -> current.versionCode
            is ApkDownloadState.Downloading -> current.versionCode
            is ApkDownloadState.Resolving -> current.versionCode
            else -> null
        }
        if (targetVersion != null && installedVersionCode >= targetVersion) {
            jobs.remove(packageName)?.cancel()
            states.remove(packageName)
            val file = (current as? ApkDownloadState.ReadyToInstall)?.file
                ?: cachedApkFile(context.applicationContext, packageName, targetVersion)
            scope.launch(Dispatchers.IO) { file.delete() }
        }
    }

    private suspend fun streamDownload(
        url: String,
        userAgent: String,
        tmpFile: File,
        destFile: File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Boolean {
        val request = Request.Builder()
            .url(url)
            .apply {
                if (userAgent.isNotBlank()) header("User-Agent", userAgent)
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            destFile.parentFile?.mkdirs()
            tmpFile.delete()
            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                            throw CancellationException()
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 80L || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                            lastEmit = now
                            val progress = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            if (totalBytes > 0 && downloadedBytes < totalBytes) {
                tmpFile.delete()
                return false
            }

            destFile.delete()
            if (!tmpFile.renameTo(destFile)) {
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }
            return true
        }
    }

    private fun cachedApkFile(context: Context, packageName: String, versionCode: Long): File {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safePkg = packageName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(updatesDir, "apk-$safePkg-$versionCode.apk")
    }

    private fun verifyApk(context: Context, file: File, expectedPackage: String): Boolean {
        val archive = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            }
        }.getOrNull() ?: return false
        val archivePackage = archive.packageName.ifEmpty { archive.applicationInfo?.packageName.orEmpty() }
        return archivePackage.isEmpty() || expectedPackage.isEmpty() || archivePackage == expectedPackage
    }

    private fun launchInstaller(context: Context, file: File) {
        runCatching {
            InAppUpdater.installApk(context, file)
        }.onFailure {
            context.applicationContext.toast("无法调起系统安装器：${it.message ?: "请检查安装权限"}")
        }
    }
}

/**
 * Listens for package install/replace broadcasts and lifecycle ON_RESUME so app update screens
 * immediately detect completed installs and hide/refresh the updated app.
 */
@Composable
fun ObservePackageInstallEvents(
    context: Context,
    onPackageChanged: (packageName: String?) -> Unit,
) {
    val currentCallback = rememberUpdatedState(onPackageChanged)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val pkg = intent?.data?.schemeSpecificPart
                currentCallback.value(pkg)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        var sawPause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> sawPause = true
                Lifecycle.Event.ON_RESUME -> if (sawPause) {
                    sawPause = false
                    currentCallback.value(null)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
