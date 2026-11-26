package vn.uisplus.work

import android.Manifest
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
import vn.uisplus.data.ChangeEvent
import vn.uisplus.data.ClassSession

object Notifier {
    const val CH_REMIND = "remind"
    const val CH_CHANGE = "change"
    const val CH_ACCOUNT = "account"
    private const val BRAND = 0xFFE11D48.toInt()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Nhắc giờ học", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Báo trước khi buổi học bắt đầu"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CHANGE, "Thay đổi lịch học", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Đổi phòng, nghỉ dạy, dạy bù, buổi học mới"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ACCOUNT, "Tài khoản", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Suppress("MissingPermission")
    fun remind(ctx: Context, sessions: List<ClassSession>, minutes: Int) {
        if (!canPost(ctx) || sessions.isEmpty()) return
        val s = sessions.first()
        val title = if (minutes > 0) "Còn $minutes phút: ${s.tenMon}" else "Đến giờ học: ${s.tenMon}"
        val text = "${s.timeText} · Phòng ${s.phong}" + if (sessions.size > 1) " (+${sessions.size - 1} môn)" else ""
        val n = NotificationCompat.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(BRAND)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                sessions.joinToString("\n") { "${it.tenMon} · ${it.timeText} · ${it.phong}" +
                    if (it.giangVien.isNotBlank()) " · ${it.giangVien}" else "" }))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(s.key.hashCode(), n)
    }

    @Suppress("MissingPermission")
    fun changes(ctx: Context, events: List<ChangeEvent>) {
        if (!canPost(ctx) || events.isEmpty()) return
        val title = if (events.size == 1) events[0].title else "Lịch học có ${events.size} thay đổi"
        val style = NotificationCompat.InboxStyle()
        events.take(7).forEach { style.addLine("${it.title} — ${it.detail}") }
        if (events.size > 7) style.setSummaryText("+${events.size - 7} thay đổi khác")
        val n = NotificationCompat.Builder(ctx, CH_CHANGE)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(BRAND)
            .setContentTitle(title)
            .setContentText(events[0].detail)
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(2001, n)
    }

    @Suppress("MissingPermission")
    fun needLogin(ctx: Context) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ACCOUNT)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(BRAND)
            .setContentTitle("Cần đăng nhập lại UIS+")
            .setContentText("Phiên đăng nhập hết hạn nên chưa cập nhật được lịch.")
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(3001, n)
    }
}
