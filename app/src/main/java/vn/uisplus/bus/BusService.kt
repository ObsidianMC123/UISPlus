package vn.uisplus.bus

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.uisplus.MainActivity
import vn.uisplus.R
import vn.uisplus.data.AppData
import vn.uisplus.data.Repository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun hhmm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

/** Hen gio tu dong: <leadMin> phut truoc gio tan buoi hoc cuoi trong ngay. */
object BusAlarm {
    private const val REQ = 4300
    const val EXTRA_UNTIL = "until"
    const val EXTRA_HOME = "home"

    /** Gio ve (ket thuc buoi cuoi) cua tung ngay co hoc, tang dan. */
    fun homeTimes(d: AppData?): List<Long> =
        d?.sessions.orEmpty().filter { !it.nghiDay }
            .groupBy { it.epochDay }.values.map { l -> l.maxOf { it.endMillis } }.sorted()

    /** (startAt, homeAt) cua lan ve ke tiep chua qua. */
    fun nextHome(d: AppData?, now: Long, c: BusConfig, skip: Long = 0L): Pair<Long, Long>? =
        homeTimes(d).firstOrNull { it + c.afterMin * 60_000L > now && it != skip }
            ?.let { (it - c.leadMin * 60_000L) to it }

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("bus", Context.MODE_PRIVATE)

    private fun pending(ctx: Context, until: Long?, home: Long?, flags: Int): PendingIntent? {
        val i = Intent(ctx, BusReceiver::class.java).setAction("vn.uisplus.BUS_AUTO")
        if (until != null) i.putExtra(EXTRA_UNTIL, until)
        if (home != null) i.putExtra(EXTRA_HOME, home)
        return PendingIntent.getBroadcast(ctx, REQ, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(ctx: Context) {
        BusTracker.init(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        pending(ctx, null, null, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
        val c = BusTracker.config.value
        val repo = Repository.get(ctx)
        if (!c.auto || !repo.loggedIn.value) { BusTracker.setSchedule(0, 0); return }
        val now = System.currentTimeMillis()
        val handled = sp(ctx).getLong("handled_home", 0L)
        val (start, home) = nextHome(repo.data.value, now, c, skip = handled) ?: run { BusTracker.setSchedule(0, 0); return }
        BusTracker.setSchedule(start, home)
        val until = home + c.afterMin * 60_000L
        if (start <= now) {
            // Dang trong khung gio ve (vd vua mo app) -> bat luon.
            if (!BusTracker.state.value.active) autoStart(ctx, until, home)
            return
        }
        val pi = pending(ctx, until, home, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start, pi)
        }
    }

    internal fun autoStart(ctx: Context, until: Long, home: Long) {
        sp(ctx).edit().putLong("handled_home", home).apply()
        if (System.currentTimeMillis() >= until) { schedule(ctx); return }
        try {
            BusService.start(ctx, until)
        } catch (e: Exception) {
            // Android chan mo foreground service tu nen -> bao de anh bam mo.
            BusNotify.tapToStart(ctx, until, home)
        }
    }

    /** Anh bam Dung trong khung gio ve -> khong tu bat lai cho lan ve nay. */
    internal fun markCurrentHandled(ctx: Context) {
        val c = BusTracker.config.value
        val now = System.currentTimeMillis()
        val h = nextHome(Repository.get(ctx).data.value, now, c) ?: return
        if (h.first <= now) sp(ctx).edit().putLong("handled_home", h.second).apply()
    }

    fun startNow(ctx: Context, minutes: Int = 30) {
        BusTracker.init(ctx)
        val now = System.currentTimeMillis()
        val c = BusTracker.config.value
        val home = nextHome(Repository.get(ctx).data.value, now, c)
        var until = now + minutes * 60_000L
        if (home != null && home.first <= now) until = maxOf(until, home.second + c.afterMin * 60_000L)
        BusService.start(ctx, until)
    }
}

class BusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val until = intent.getLongExtra(BusAlarm.EXTRA_UNTIL, 0L)
        val home = intent.getLongExtra(BusAlarm.EXTRA_HOME, 0L)
        BusTracker.init(context)
        if (until > 0) BusAlarm.autoStart(context, until, home) else BusAlarm.schedule(context)
    }
}

/** Foreground service: goi EBMS moi 12-20s, cap nhat thong bao thuong truc + bao xe sap toi / vua qua. */
class BusService : Service() {
    companion object {
        const val ACT_STOP = "vn.uisplus.BUS_STOP"

        fun start(ctx: Context, until: Long) {
            val i = Intent(ctx, BusService::class.java).putExtra(BusAlarm.EXTRA_UNTIL, until)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            BusAlarm.markCurrentHandled(ctx)
            ctx.startService(Intent(ctx, BusService::class.java).setAction(ACT_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BusTracker.init(this)
        BusNotify.channels(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, BusNotify.ID_LIVE, BusNotify.live(this), type)
        if (intent?.action == ACT_STOP) {
            BusAlarm.markCurrentHandled(this)
            finish()
            return START_NOT_STICKY
        }
        BusNotify.cancelTap(this)
        val until = intent?.getLongExtra(BusAlarm.EXTRA_UNTIL, 0L)?.takeIf { it > 0 }
            ?: (System.currentTimeMillis() + 30 * 60_000L)
        BusTracker.begin(until)
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_NOT_STICKY
    }

    private var lastWidget = 0L

    private suspend fun loop() {
        val nm = NotificationManagerCompat.from(this)
        while (scope.isActive && BusTracker.state.value.active && System.currentTimeMillis() < BusTracker.state.value.until) {
            val events = runCatching { BusTracker.poll() }.getOrElse { emptyList() }
            events.forEach { runCatching { BusNotify.event(this, it) } }
            if (!BusTracker.state.value.active) break
            runCatching { nm.notify(BusNotify.ID_LIVE, BusNotify.live(this)) }
            // 1.3: dang theo doi gio ve thi cap nhat luon widget Xe buyt (toi da 1 lan/phut)
            val t = System.currentTimeMillis()
            if (t - lastWidget > 60_000) { lastWidget = t; runCatching { vn.uisplus.widget.BusWidget.refresh(this) } }
            val soonest = BusTracker.state.value.lines.flatMap { it.arrivals }.minOfOrNull { it.etaSec } ?: Int.MAX_VALUE
            delay(if (soonest <= 180) 12_000L else 20_000L)
        }
        finish()
    }

    private fun finish() {
        BusTracker.end()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        job?.cancel()
    }

    override fun onDestroy() {
        BusTracker.end()
        scope.cancel()
        runCatching { BusAlarm.schedule(applicationContext) }
        super.onDestroy()
    }
}
