package app.awavenue.c001apk.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import app.awavenue.c001apk.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class InstallerAppOption(
    val packageName: String,
    val label: String,
) {
    val displayTitle: String
        get() = if (packageName.isEmpty()) label else "$label ($packageName)"
}

/**
 * Discovers compatible APK installer activities and runs silent package installation through
 * Shizuku's UserService when authorized.
 */
object ShizukuInstaller {
    private const val PERMISSION_REQUEST_CODE = 0x5348
    private const val BIND_TIMEOUT_MS = 15_000L
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun queryAvailableInstallers(context: Context): List<InstallerAppOption> {
        val pm = context.packageManager
        val probeIntents = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://${context.packageName}.fileprovider/probe.apk"), APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = Uri.parse("content://${context.packageName}.fileprovider/probe.apk")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        val discovered = LinkedHashMap<String, InstallerAppOption>()
        for (intent in probeIntents) {
            val resolved = runCatching {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrDefault(emptyList())
            for (info in resolved) {
                val pkg = info.activityInfo?.packageName?.takeIf { it.isNotEmpty() } ?: continue
                if (pkg == context.packageName || pkg in discovered) continue
                val label = runCatching { info.loadLabel(pm).toString() }
                    .getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: pkg
                discovered[pkg] = InstallerAppOption(packageName = pkg, label = label)
            }
        }
        return discovered.values.sortedBy { it.label.lowercase() }
    }

    fun isShizukuAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasShizukuPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            runCatching {
                if (!Shizuku.pingBinder()) false
                else if (Shizuku.isPreV11()) false
                else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)

    fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
        if (!isShizukuAvailable()) {
            onResult(false)
            return
        }
        if (hasShizukuPermission()) {
            onResult(true)
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PERMISSION_REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }.onFailure {
            Shizuku.removeRequestPermissionResultListener(listener)
            onResult(false)
        }
    }

    suspend fun installApkSilently(appContext: Context, apkFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable()) {
            return@withContext Result.failure(IllegalStateException("Shizuku 服务未运行"))
        }
        if (!hasShizukuPermission()) {
            return@withContext Result.failure(IllegalStateException("尚未授予 Shizuku 权限"))
        }
        val args = Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, ShizukuInstallUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("shizuku_installer")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        val deferred = CompletableDeferred<IShizukuInstallService>()
        val unbound = AtomicBoolean(false)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val iface = service?.let { IShizukuInstallService.Stub.asInterface(it) }
                if (iface != null) {
                    deferred.complete(iface)
                } else {
                    deferred.completeExceptionally(IllegalStateException("无法连接 Shizuku 安装服务"))
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(IllegalStateException("Shizuku 安装服务已断开"))
                }
            }
        }

        try {
            Shizuku.bindUserService(args, connection)
            val service = withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
            val result = ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                service.installApk(pfd, apkFile.length())
            }
            if (result == "SUCCESS") {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(result.ifEmpty { "未知安装错误" }))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            if (unbound.compareAndSet(false, true)) {
                runCatching { Shizuku.unbindUserService(args, connection, true) }
            }
        }
    }
}
