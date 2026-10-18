package app.awavenue.c001apk.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.awavenue.c001apk.C001apkApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Checks the signed-in account's unread counts in the background and hands them to
 * [MessageNotifier]. A failed check is not retried: the next period asks again.
 */
class MessageCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as C001apkApp
        if (!app.session.isLogin || !MessageNotifier.canNotify(applicationContext)) return Result.success()
        try {
            // Nobody is there to solve a captcha in the background.
            val count = app.repository.checkCount(skipCaptcha = true)
            MessageNotifier.notifyNew(applicationContext, app.session.uid, count) { type ->
                try {
                    app.repository.notificationList(type.endpoint, 1, null, null).firstOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline, signed out elsewhere or rate-limited: the next run tries again.
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "message-check"

        /** WorkManager's shortest period; it also keeps the work alive across restarts. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MessageCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
