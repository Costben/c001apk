package app.awavenue.c001apk.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class InstalledApp(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val icon: Drawable?,
)

/**
 * Third-party packages, newest update first — the order the Flutter client's
 * `AppInfo.parseList` produced. Blocking; callers run it off the main thread.
 */
fun installedApps(context: Context): List<InstalledApp> {
    val manager = context.packageManager
    val applications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        manager.getInstalledApplications(0)
    }
    return applications
        .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != ApplicationInfo.FLAG_SYSTEM }
        .mapNotNull { info ->
            val packageInfo = packageInfo(manager, info.packageName) ?: return@mapNotNull null
            InstalledApp(
                appName = manager.getApplicationLabel(info).toString(),
                packageName = info.packageName,
                versionName = packageInfo.versionName.orEmpty(),
                versionCode = versionCode(packageInfo),
                lastUpdateTime = packageInfo.lastUpdateTime,
                icon = runCatching { info.loadIcon(manager) }.getOrNull(),
            )
        }
        .sortedByDescending { it.lastUpdateTime }
}

private fun packageInfo(manager: PackageManager, packageName: String): PackageInfo? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        manager.getPackageInfo(packageName, 0)
    }
}.getOrNull()

private fun versionCode(packageInfo: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
