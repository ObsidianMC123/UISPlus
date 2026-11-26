package vn.uisplus.focus

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vn.uisplus.MainActivity
import vn.uisplus.R
import vn.uisplus.data.Prefs
import vn.uisplus.data.Repository
import vn.uisplus.data.VN_ZONE
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Phase(val label: String, val emoji: String) {
    FOCUS("Tập trung", "🍅"),
    SHORT("Nghỉ ngắn", "☕"),
    LONG("Nghỉ dài", "🌿"),
}

@Serializable
data class FocusState(
    val phase: Phase = Phase.FOCUS,
    val active: Boolean = false,      // da bam bat dau (dang chay hoac tam dung)
    val running: Boolean = false,
    val endAt: Long = 0L,             // khi running
    val remainingMs: Long = 0L,       // khi tam dung
    val totalMs: Long = 25 * 60_000L,
    val round: Int = 0,               // so phien tap trung da xong trong chu ky
    val subject: String = "",
) {
    fun remaining(now: Long): Long = when {
        running -> (endAt - now).coerceAtLeast(0)
        active -> remainingMs
        else -> totalMs
    }
}

@Serializable
data class FocusEntry(val at: Long, val minutes: Int, val subject: String = "")

data class FocusStats(
    val todayMin: Int,
    val weekMin: Int,
    val streak: Int,
    val last7: List<Pair<LocalDate, Int>>,
    val bySubject: List<Pair<String, Int>>,
    val totalSessions: Int,
)

object Focus {
    const val CH_RUN = "focus_run"
    const val CH_DONE = "focus_done"
    private const val NOTI_RUN = 4001
    private const val NOTI_DONE = 4002
    private const val BRAND = 0xFFE11D48.toInt()

    const val ACT_ALARM = "vn.uisplus.focus.ALARM"
    const val ACT_TOGGLE = "vn.uisplus.focus.TOGGLE"
    const val ACT_SKIP = "vn.uisplus.focus.SKIP"
    const val ACT_STOP = "vn.uisplus.focus.STOP"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var sp: SharedPreferences? = null
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()
    private val _log = MutableStateFlow<List<FocusEntry>>(emptyList())
    val log: StateFlow<List<FocusEntry>> = _log.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("focus", Context.MODE_PRIVATE)
        sp = p
        _state.value = runCatching { json.decodeFromString(FocusState.serializer(), p.getString("state", null)!!) }.getOrDefault(FocusState())
        _log.value = runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(FocusEntry.serializer()), p.getString("log", null)!!) }.getOrDefault(emptyList())
        createChannels(ctx)
        val s = _state.value
        if (!s.active) _state.value = s.copy(totalMs = durationMs(s.phase, prefs(ctx)))
        // Sau khi khoi dong lai may / app bi kill: hoan tat hoac hen lai bao thuc
        if (s.running) {
            if (s.endAt <= System.currentTimeMillis()) complete(ctx, alert = true)
            else { schedule(ctx, s.endAt); notifyOngoing(ctx) }
        }
    }

    private fun prefs(ctx: Context): Prefs = Repository.get(ctx).settings.value

    fun durationMs(phase: Phase, p: Prefs): Long = 60_000L * when (phase) {
        Phase.FOCUS -> p.focusMin
        Phase.SHORT -> p.shortBreakMin
        Phase.LONG -> p.longBreakMin
    }.coerceAtLeast(1)

    private fun save(n: FocusState) {
        _state.value = n
        sp?.edit()?.putString("state", json.encodeToString(FocusState.serializer(), n))?.apply()
    }

    // ---------- dieu khien ----------

    fun start(ctx: Context) {
        init(ctx)
        val s = _state.value
        val now = System.currentTimeMillis()
        val total = if (s.active) s.totalMs else durationMs(s.phase, prefs(ctx))
        val rem = if (s.active) s.remainingMs.coerceAtLeast(1000) else total
        val n = s.copy(active = true, running = true, totalMs = total, endAt = now + rem, remainingMs = rem)
        save(n)
        schedule(ctx, n.endAt)
        notifyOngoing(ctx)
        NotificationManagerCompat.from(ctx).cancel(NOTI_DONE)
        if (n.phase == Phase.FOCUS) Dnd.enter(ctx, prefs(ctx).focusDnd) else Dnd.exit(ctx)
    }

    fun pause(ctx: Context) {
        init(ctx)
        val s = _state.value
        if (!s.running) return
        save(s.copy(running = false, remainingMs = (s.endAt - System.currentTimeMillis()).coerceAtLeast(1000)))
        cancelAlarm(ctx)
        notifyOngoing(ctx)
        Dnd.exit(ctx)
    }

    fun toggle(ctx: Context) = if (_state.value.running) pause(ctx) else start(ctx)

    /** Bo qua phien hien tai (khong tinh vao thong ke). */
    fun skip(ctx: Context) {
        init(ctx)
        advance(ctx, completedFocus = false, alert = false)
    }

    fun stop(ctx: Context) {
        init(ctx)
        val s = _state.value
        cancelAlarm(ctx)
        NotificationManagerCompat.from(ctx).cancel(NOTI_RUN)
        Dnd.exit(ctx)
        save(FocusState(phase = Phase.FOCUS, subject = s.subject, round = 0, totalMs = durationMs(Phase.FOCUS, prefs(ctx))))
    }

    fun setPhase(ctx: Context, p: Phase) {
        init(ctx)
        val s = _state.value
        if (s.active) return
        save(s.copy(phase = p, totalMs = durationMs(p, prefs(ctx))))
    }

    fun setSubject(ctx: Context, subject: String) {
        init(ctx)
        save(_state.value.copy(subject = subject))
        if (_state.value.active) notifyOngoing(ctx)
    }

    /** Goi khi doi thoi luong trong cai dat. */
    fun onPrefsChanged(ctx: Context) {
        init(ctx)
        val s = _state.value
        if (!s.active) save(s.copy(totalMs = durationMs(s.phase, prefs(ctx))))
    }

    internal fun onAlarm(ctx: Context) {
        init(ctx)
        val s = _state.value
        if (!s.running) return
        if (s.endAt - System.currentTimeMillis() > 2_000) { schedule(ctx, s.endAt); return }
        complete(ctx, alert = true)
    }

    private fun complete(ctx: Context, alert: Boolean) = advance(ctx, completedFocus = _state.value.phase == Phase.FOCUS, alert = alert)

    private fun advance(ctx: Context, completedFocus: Boolean, alert: Boolean) {
        val s = _state.value
        val p = prefs(ctx)
        var round = s.round
        if (completedFocus) {
            round += 1
            addLog(FocusEntry(System.currentTimeMillis(), (s.totalMs / 60_000L).toInt(), s.subject))
        }
        val next = when (s.phase) {
            Phase.FOCUS -> if (completedFocus && round % p.roundsBeforeLong.coerceAtLeast(1) == 0) Phase.LONG else Phase.SHORT
            else -> Phase.FOCUS
        }
        if (s.phase == Phase.LONG) round = 0
        cancelAlarm(ctx)
        save(FocusState(phase = next, subject = s.subject, round = round, totalMs = durationMs(next, p)))
        if (alert) notifyDone(ctx, s.phase, next, s.subject)
        if (s.active && p.focusAutoNext) start(ctx) else {
            NotificationManagerCompat.from(ctx).cancel(NOTI_RUN)
            Dnd.exit(ctx)
        }
    }

    private fun addLog(e: FocusEntry) {
        val n = (_log.value + e).takeLast(3000)
        _log.value = n
        sp?.edit()?.putString("log", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(FocusEntry.serializer()), n))?.apply()
    }

    // ---------- thong ke ----------

    fun stats(log: List<FocusEntry>, today: LocalDate = LocalDate.now(VN_ZONE)): FocusStats {
        fun day(e: FocusEntry) = Instant.ofEpochMilli(e.at).atZone(VN_ZONE).toLocalDate()
        val byDay = log.groupBy { day(it) }.mapValues { (_, v) -> v.sumOf { it.minutes } }
        val last7 = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (byDay[it] ?: 0) }
        var streak = 0
        var d = if ((byDay[today] ?: 0) > 0) today else today.minusDays(1)
        while ((byDay[d] ?: 0) > 0) { streak++; d = d.minusDays(1) }
        val weekStart = today.minusDays(6)
        val bySubject = log.filter { !day(it).isBefore(weekStart) }
            .groupBy { it.subject.ifBlank { "Tự do" } }
            .map { (k, v) -> k to v.sumOf { it.minutes } }
            .sortedByDescending { it.second }
        return FocusStats(
            todayMin = byDay[today] ?: 0,
            weekMin = last7.sumOf { it.second },
            streak = streak,
            last7 = last7,
            bySubject = bySubject,
            totalSessions = log.size,
        )
    }

    // ---------- bao thuc ----------

    private fun alarmPi(ctx: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        ctx, 4100, Intent(ctx, FocusReceiver::class.java).setAction(ACT_ALARM), flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmPi(ctx, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        alarmPi(ctx, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    // ---------- thong bao ----------

    private fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_RUN, "Hẹn giờ học (đang chạy)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Đồng hồ đếm ngược Pomodoro"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Hẹn giờ học (hết giờ)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Báo khi hết phiên tập trung / giờ nghỉ"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }
        )
    }

    private fun canPost(ctx: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 4200,
        Intent(ctx, MainActivity::class.java).putExtra("tab", "focus").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(ctx: Context, act: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, FocusReceiver::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    fun mmss(ms: Long): String {
        val t = (ms + 999) / 1000
        return "%02d:%02d".format(t / 60, t % 60)
    }

    @Suppress("MissingPermission")
    private fun notifyOngoing(ctx: Context) {
        if (!canPost(ctx)) return
        val s = _state.value
        if (!s.active) { NotificationManagerCompat.from(ctx).cancel(NOTI_RUN); return }
        val p = prefs(ctx)
        val sub = if (s.subject.isNotBlank()) " · ${s.subject}" else ""
        val roundText = "Vòng ${(s.round % p.roundsBeforeLong.coerceAtLeast(1)) + 1}/${p.roundsBeforeLong}"
        val b = NotificationCompat.Builder(ctx, CH_RUN)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(BRAND)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(ctx))
        if (s.running) {
            val endTxt = Instant.ofEpochMilli(s.endAt).atZone(VN_ZONE).toLocalTime().format(HM)
            b.setContentTitle("${s.phase.emoji} ${s.phase.label}$sub")
                .setContentText("$roundText · xong lúc $endTxt")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(s.endAt)
                .setShowWhen(true)
                .addAction(0, "Tạm dừng", action(ctx, ACT_TOGGLE, 4301))
        } else {
            b.setContentTitle("⏸ Đã tạm dừng · còn ${mmss(s.remainingMs)}")
                .setContentText("${s.phase.label}$sub · $roundText")
                .setShowWhen(false)
                .addAction(0, "Tiếp tục", action(ctx, ACT_TOGGLE, 4301))
        }
        b.addAction(0, "Bỏ qua", action(ctx, ACT_SKIP, 4302))
            .addAction(0, "Dừng", action(ctx, ACT_STOP, 4303))
        NotificationManagerCompat.from(ctx).notify(NOTI_RUN, b.build())
    }

    @Suppress("MissingPermission")
    private fun notifyDone(ctx: Context, done: Phase, next: Phase, subject: String) {
        if (!canPost(ctx)) return
        val title = if (done == Phase.FOCUS) "🎉 Xong 1 phiên tập trung!" else "⏰ Hết giờ nghỉ rồi"
        val text = when (next) {
            Phase.FOCUS -> "Quay lại học thôi" + if (subject.isNotBlank()) ": $subject" else ""
            Phase.SHORT -> "Nghỉ ngắn ${prefs(ctx).shortBreakMin} phút, đứng dậy vươn vai nhé"
            Phase.LONG -> "Nghỉ dài ${prefs(ctx).longBreakMin} phút, bạn xứng đáng 🌿"
        }
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(BRAND)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTI_DONE, n)
    }
}

/** Bat/tat Khong lam phien khi dang tap trung (chi khi anh da cap quyen). */
object Dnd {
    fun granted(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun enter(ctx: Context, enabled: Boolean) {
        if (!enabled || !granted(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        runCatching {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            ctx.getSharedPreferences("focus", Context.MODE_PRIVATE).edit().putBoolean("dnd_by_us", true).apply()
        }
    }

    fun exit(ctx: Context) {
        val sp = ctx.getSharedPreferences("focus", Context.MODE_PRIVATE)
        if (!sp.getBoolean("dnd_by_us", false)) return
        sp.edit().putBoolean("dnd_by_us", false).apply()
        if (!granted(ctx)) return
        runCatching {
            ctx.getSystemService(NotificationManager::class.java)?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Focus.init(context)
        when (intent.action) {
            Focus.ACT_ALARM -> Focus.onAlarm(context)
            Focus.ACT_TOGGLE -> Focus.toggle(context)
            Focus.ACT_SKIP -> Focus.skip(context)
            Focus.ACT_STOP -> Focus.stop(context)
        }
    }
}
