package vn.uisplus.bus

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import vn.uisplus.MainActivity
import vn.uisplus.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BusNotify {
    const val CH_LIVE = "bus_live"
    const val CH_ALERT = "bus_alert"
    const val ID_LIVE = 4310
    private const val ID_TAP = 4311
    private const val COLOR = 0xFF0EA5E9.toInt()

    fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Xe buýt đang theo dõi", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Thông báo thường trực: còn mấy phút xe tới trạm"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Xe buýt sắp tới / vừa qua", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Báo khi xe sắp tới trạm hoặc vừa chạy qua"
                enableVibration(true)
            }
        )
    }

    private fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 4312,
        Intent(ctx, MainActivity::class.java).putExtra("tab", "today")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(ctx: Context): PendingIntent = PendingIntent.getService(
        ctx, 4313, Intent(ctx, BusService::class.java).setAction(BusService.ACT_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun etaText(sec: Int): String = if (sec <= 60) "đang tới" else "${(sec + 30) / 60} phút"

    fun distText(m: Int): String = if (m >= 1000) String.format(Locale.US, "%.1f km", m / 1000.0) else "$m m"

    fun live(ctx: Context): Notification {
        val st = BusTracker.state.value
        val lines = st.lines.filter { it.arrivals.isNotEmpty() }
        val best = lines.flatMap { l -> l.arrivals.map { l to it } }.minByOrNull { it.second.etaSec }
        val title = when {
            st.error != null && st.lines.isEmpty() -> "Đang thử lại… (mạng hoặc EBMS lỗi)"
            best != null -> "${best.first.routeNo} → ${best.first.dest}: ${etaText(best.second.etaSec)}"
            st.updatedAt == 0L -> "Đang lấy vị trí xe…"
            else -> "Chưa thấy xe ${BusTracker.config.value.routes.joinToString(", ")} chạy tới"
        }
        val style = NotificationCompat.InboxStyle()
        lines.take(5).forEach { l ->
            val a = l.arrivals.first()
            val more = l.arrivals.getOrNull(1)?.let { " · sau ${etaText(it.etaSec)}" } ?: ""
            style.addLine("${l.routeNo} → ${l.dest}: ${etaText(a.etaSec)} (${distText(a.distM)})$more")
        }
        st.passed.take(2).forEach { style.addLine("Đã qua: ${it.routeNo} → ${it.dest} lúc ${hhmm(it.at)}") }
        if (st.until > 0) style.setSummaryText("Trạm HV Bưu chính · tới ${hhmm(st.until)}")
        return NotificationCompat.Builder(ctx, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(COLOR)
            .setContentTitle(title)
            .setContentText(if (lines.size > 1) "${lines.size} hướng có xe đang tới · kéo xuống xem" else "Trạm HV Bưu chính (97 Man Thiện)")
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Dừng theo dõi", stopIntent(ctx))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @Suppress("MissingPermission")
    fun event(ctx: Context, e: BusEvent) {
        if (!canPost(ctx)) return
        val id: Int
        val title: String
        val text: String
        when (e) {
            is BusEvent.Near -> {
                id = ("n" + e.routeNo + e.plate).hashCode()
                title = if (e.etaSec <= 60) "${e.routeNo} đang tới trạm!" else "${e.routeNo} còn ${etaText(e.etaSec)}, ra trạm thôi"
                text = "→ ${e.dest} · cách ${distText(e.distM)} · xe ${e.plate}"
            }
            is BusEvent.Passed -> {
                id = ("p" + e.routeNo + e.dest).hashCode()
                title = "${e.routeNo} vừa qua trạm rồi"
                text = "→ ${e.dest} · " + (e.nextEtaSec?.let { "chuyến sau còn ${etaText(it)}, khỏi chạy" } ?: "chưa thấy chuyến sau trên GPS")
            }
        }
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    @Suppress("MissingPermission")
    fun tapToStart(ctx: Context, until: Long, home: Long) {
        channels(ctx)
        if (!canPost(ctx)) return
        val i = Intent(ctx, BusService::class.java).putExtra(BusAlarm.EXTRA_UNTIL, until)
        val pi = PendingIntent.getForegroundService(ctx, 4314, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(COLOR)
            .setContentTitle("Sắp tan học lúc ${hhmm(home)}")
            .setContentText("Bấm để xem xe ${BusTracker.config.value.routes.joinToString(", ")} còn bao lâu tới trạm")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_TAP, n)
    }

    fun cancelTap(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_TAP)
}
