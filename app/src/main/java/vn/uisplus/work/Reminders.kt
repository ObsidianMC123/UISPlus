package vn.uisplus.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.uisplus.data.Repository
import vn.uisplus.widget.TimetableWidget

/** Hen gio nhac buoi hoc ke tiep bang AlarmManager (tu hen lai sau moi lan bao). */
object Reminders {
    private const val REQ = 4242
    private const val EXTRA_AT = "start_at"

    private fun pending(ctx: Context, startAt: Long?, flags: Int): PendingIntent? {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction("vn.uisplus.REMIND")
        if (startAt != null) i.putExtra(EXTRA_AT, startAt)
        return PendingIntent.getBroadcast(ctx, REQ, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun reschedule(ctx: Context) {
        runCatching { vn.uisplus.bus.BusAlarm.schedule(ctx) }
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        pending(ctx, null, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
        val repo = Repository.get(ctx)
        val prefs = repo.settings.value
        if (!prefs.remindersOn || !repo.loggedIn.value) return
        val lead = prefs.reminderMinutes * 60_000L
        val now = System.currentTimeMillis()
        val next = repo.data.value?.sessions
            ?.filter { !it.nghiDay && it.startMillis - lead > now + 5_000 }
            ?.minByOrNull { it.startMillis } ?: return
        val triggerAt = next.startMillis - lead
        val pi = pending(ctx, next.startMillis, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    internal fun onFire(ctx: Context, startAt: Long) {
        val repo = Repository.get(ctx)
        repo.reload()
        val list = repo.data.value?.sessions?.filter { !it.nghiDay && it.startMillis == startAt }.orEmpty()
        val minutes = ((startAt - System.currentTimeMillis() + 30_000) / 60_000).toInt().coerceAtLeast(0)
        // Bo qua neu bao qua tre (vd may tat luc do)
        if (list.isNotEmpty() && System.currentTimeMillis() < list.first().endMillis) {
            Notifier.remind(ctx, list, minutes)
        }
        reschedule(ctx)
    }

    internal const val KEY_AT = EXTRA_AT
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val at = intent.getLongExtra(Reminders.KEY_AT, 0L)
        val pr = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (at > 0) Reminders.onFire(context, at) else Reminders.reschedule(context)
                TimetableWidget.refreshAll(context)
            } finally {
                pr.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                Reminders.reschedule(context)
                SyncWorker.schedule(context)
                TimetableWidget.refreshAll(context)
            } finally {
                pr.finish()
            }
        }
    }
}
