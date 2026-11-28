package app.awavenue.c001apk.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import app.awavenue.c001apk.ui.common.toast

/**
 * Handles commit callbacks from Android's [PackageInstaller] session fallback when no external
 * Activity handles `ACTION_VIEW` / `ACTION_INSTALL_PACKAGE` intents for APK files.
 */
class PackageInstallSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?: return
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmIntent) }
                    .onFailure {
                        context.applicationContext.toast("无法打开系统安装确认界面：${it.message ?: "未知错误"}")
                    }
            }

            PackageInstaller.STATUS_SUCCESS,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            -> Unit

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: "安装失败（状态码 $status）"
                context.applicationContext.toast(message)
            }
        }
    }
}
