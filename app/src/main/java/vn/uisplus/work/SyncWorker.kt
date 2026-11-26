package vn.uisplus.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import vn.uisplus.data.Repository
import vn.uisplus.data.SyncResult
import vn.uisplus.data.UisException
import vn.uisplus.widget.TimetableWidget
import java.util.concurrent.TimeUnit

/** Dong bo + cac hieu ung sau dong bo (thong bao thay doi, hen lai nhac, cap nhat widget). */
object SyncRunner {
    suspend fun run(ctx: Context, background: Boolean): SyncResult {
        val repo = Repository.get(ctx)
        val r = repo.sync()
        if (background && repo.settings.value.changeAlertsOn && !r.first) {
            Notifier.changes(ctx, r.changes)
        }
        Reminders.reschedule(ctx)
        TimetableWidget.refreshAll(ctx)
        return r
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        if (!repo.loggedIn.value) return Result.success()
        return try {
            SyncRunner.run(applicationContext, background = true)
            Result.success()
        } catch (e: UisException) {
            if (e.isAuth) {
                Notifier.needLogin(applicationContext)
                Result.failure()
            } else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "uis_sync"
        private const val ONCE = "uis_sync_once"
        private val NET = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context, replace: Boolean = false) {
            val repo = Repository.get(ctx)
            val wm = WorkManager.getInstance(ctx)
            if (!repo.loggedIn.value) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val h = repo.settings.value.syncHours.toLong().coerceIn(1, 24)
            val req = PeriodicWorkRequestBuilder<SyncWorker>(h, TimeUnit.HOURS)
                .setConstraints(NET)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }

        fun runOnce(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(NET).build(),
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(ctx).cancelUniqueWork(ONCE)
        }
    }
}
