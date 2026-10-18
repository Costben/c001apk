package app.awavenue.c001apk.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.awavenue.c001apk.MainActivity
import app.awavenue.c001apk.R
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.nav.NotificationType

/**
 * System notifications for what arrived on the account since the last look: one per kind of
 * unread count `checkCount` reports, posted when that count grows. Opening one goes straight to its
 * list. The counts already notified are kept per account, so a count that merely stays unread is
 * not announced again.
 */
object MessageNotifier {
    private const val CHANNEL_ID = "messages"
    private const val PREFS = "message_notifier"
    private const val KEY_PERMISSION_ASKED = "permission_asked"

    /** In-app link a notification opens: the list of [NotificationType], by name. */
    const val LINK_PREFIX = "c001apk://notification/"

    /** A `checkCount` field, the list it belongs to and how a new count reads. */
    private class Kind(val field: String, val type: NotificationType, val title: String, val text: (Int) -> String)

    private val kinds = listOf(
        Kind("atme", NotificationType.At, "@我的动态") { "$it 条动态提到了你" },
        Kind("atcommentme", NotificationType.Comment, "@我的评论") { "$it 条评论提到了你" },
        Kind("feedlike", NotificationType.Like, "我收到的赞") { "收到 $it 个新的赞" },
        Kind("contacts_follow", NotificationType.Follow, "好友关注") { "$it 位新的关注者" },
        Kind("message", NotificationType.Message, "私信") { "$it 条未读私信" },
        Kind("notification", NotificationType.Message, "酷安小秘书") { "$it 条未读通知" },
    )

    /** Posts a notification for every count in [count] that grew since it was last notified. */
    fun notifyNew(context: Context, uid: String, count: Entity) {
        if (uid.isEmpty() || !canNotify(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, manager)
        val editor = prefs.edit()
        kinds.forEachIndexed { index, kind ->
            val current = count.int(kind.field) ?: return@forEachIndexed
            val key = "$uid.${kind.field}"
            if (current > prefs.getInt(key, 0)) {
                val system = if (kind.field == "notification") current else count.int("notification") ?: 0
                manager.notify(index, build(context, index, kind, current, system))
            } else if (current == 0) {
                manager.cancel(index)
            }
            editor.putInt(key, current)
        }
        editor.apply()
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "消息提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "私信、提到我的、赞与关注"
            },
        )
    }

    private fun build(context: Context, id: Int, kind: Kind, count: Int, system: Int): Notification {
        val link = if (kind.type == NotificationType.Message) {
            "$LINK_PREFIX${kind.type.name}?system=$system"
        } else {
            "$LINK_PREFIX${kind.type.name}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link), context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(kind.title)
            .setContentText(kind.text(count))
            .setNumber(count)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
    }

    /**
     * Asks for the notification permission once an account is signed in, since message reminders
     * are what it is for. Asked once: declining is an answer, and Android stops showing the prompt
     * after the second refusal anyway.
     */
    @Composable
    fun RequestPermission() {
        val app = LocalApp.current
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val isLogin = app.session.isLogin
        LaunchedEffect(isLogin) {
            if (!isLogin || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                return@LaunchedEffect
            }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_PERMISSION_ASKED, false)) return@LaunchedEffect
            prefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
