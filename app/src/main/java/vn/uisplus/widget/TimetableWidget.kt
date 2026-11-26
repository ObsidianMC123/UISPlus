package vn.uisplus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import vn.uisplus.MainActivity
import vn.uisplus.R
import vn.uisplus.data.AppData
import vn.uisplus.data.ClassSession
import vn.uisplus.data.Repository
import vn.uisplus.data.SubjectColors
import vn.uisplus.data.VN_ZONE
import vn.uisplus.data.dowShort
import vn.uisplus.data.hm
import vn.uisplus.work.SyncWorker
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun cp(c: Color) = ColorProvider(day = c, night = c)
private val INK = ColorProvider(day = Color(0xFF111827), night = Color(0xFFF3F4F6))
private val SUB = ColorProvider(day = Color(0xFF6B7280), night = Color(0xFFA1A7B3))
private val BRAND = ColorProvider(day = Color(0xFFE11D48), night = Color(0xFFF43F5E))
private val WHITE = cp(Color.White)
private val WHITE_DIM = cp(Color(0xDDFFFFFF))
private val DM = DateTimeFormatter.ofPattern("dd/MM")

class TimetableWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = Repository.get(context)
        repo.reload()
        val data = repo.data.value
        val logged = repo.loggedIn.value
        provideContent { WidgetBody(data, logged) }
    }

    companion object {
        val SMALL = DpSize(110.dp, 60.dp)
        val MEDIUM = DpSize(180.dp, 110.dp)
        val LARGE = DpSize(250.dp, 220.dp)

        suspend fun refreshAll(ctx: Context) {
            runCatching { TimetableWidget().updateAll(ctx) }
        }
    }
}

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncWorker.runOnce(context)
    }
}

private data class Pick(
    val label: String,
    val list: List<ClassSession>,
    val isToday: Boolean,
    val laterLabel: String = "",
    val later: List<ClassSession> = emptyList(),
)

private fun pick(data: AppData, now: Long): Pick {
    val today = LocalDate.now(VN_ZONE)
    val remaining = data.sessionsOn(today.toEpochDay()).filter { it.endMillis > now }
    val next = data.sessions.firstOrNull { it.epochDay > today.toEpochDay() }
    val nextLabel = next?.date?.let { d ->
        if (d.toEpochDay() == today.toEpochDay() + 1) "Ngày mai · ${dowShort(d)} ${d.format(DM)}" else "${dowShort(d)} ${d.format(DM)}"
    } ?: ""
    val nextList = next?.let { data.sessionsOn(it.epochDay) } ?: emptyList()
    if (remaining.isNotEmpty()) {
        return Pick("Hôm nay · ${dowShort(today)} ${today.format(DM)}", remaining, true, nextLabel, nextList)
    }
    if (next == null) return Pick("Hôm nay · ${dowShort(today)} ${today.format(DM)}", emptyList(), true)
    return Pick(nextLabel, nextList, false)
}

@Composable
internal fun WidgetBody(data: AppData?, logged: Boolean) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (!logged || data == null) {
            Column {
                Text("UIS+", style = TextStyle(color = BRAND, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(4.dp))
                Text("Mở app để đăng nhập và tải thời khóa biểu", style = TextStyle(color = SUB, fontSize = 12.sp))
            }
        } else {
            val now = System.currentTimeMillis()
            val p = pick(data, now)
            if (size.width < 170.dp || size.height < 100.dp) Compact(p, now) else Full(p, now)
        }
    }
}

@Composable
private fun Header(p: Pick) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            p.label,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = INK, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            "↻",
            modifier = GlanceModifier.padding(horizontal = 6.dp).clickable(actionRunCallback<RefreshAction>()),
            style = TextStyle(color = BRAND, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun Compact(p: Pick, now: Long) {
    val s = p.list.firstOrNull()
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (s == null) {
            Text("Hết lịch 🎉", style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text("Không còn buổi học nào", style = TextStyle(color = SUB, fontSize = 11.sp))
        } else {
            val live = p.isToday && now >= s.startMillis
            Text(
                if (live) "ĐANG HỌC" else if (p.isToday) "TIẾP THEO · ${hm(s.startMin)}" else p.label.uppercase(),
                style = TextStyle(color = BRAND, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(s.tenMon, style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Text("${s.timeText} · ${s.phong}", style = TextStyle(color = SUB, fontSize = 11.sp), maxLines = 1)
        }
    }
}

@Composable
private fun Full(p: Pick, now: Long) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(p)
        Spacer(GlanceModifier.height(8.dp))
        if (p.list.isEmpty()) {
            Text("Không còn buổi học nào 🎉", style = TextStyle(color = SUB, fontSize = 12.sp))
            return@Column
        }
        val first = p.list.first()
        Hero(first, p.isToday && now >= first.startMillis, p.isToday)
        Spacer(GlanceModifier.height(6.dp))
        val rest = p.list.drop(1).take(4)
        rest.forEach { Item(it) }
        val room = 3 - rest.size
        if (room > 0 && p.later.isNotEmpty()) {
            Text(
                p.laterLabel,
                modifier = GlanceModifier.padding(top = 2.dp, bottom = 4.dp),
                style = TextStyle(color = SUB, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            p.later.take(room).forEach { Item(it) }
        }
    }
}

@Composable
private fun Hero(s: ClassSession, live: Boolean, isToday: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_hero))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            when {
                s.nghiDay -> "NGHỈ DẠY"
                live -> "ĐANG HỌC · đến ${hm(s.endMin)}"
                isToday -> "TIẾP THEO · ${hm(s.startMin)}"
                else -> "BUỔI ĐẦU · ${hm(s.startMin)}"
            },
            style = TextStyle(color = WHITE_DIM, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(s.tenMon, style = TextStyle(color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text("${s.timeText} · ${s.phong}", style = TextStyle(color = WHITE_DIM, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun Item(s: ClassSession) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 5.dp)) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.widget_item))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .height(30.dp)
                    .cornerRadius(2.dp)
                    .background(Color(SubjectColors.argb(s.maMon))),
            ) {}
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    s.tenMon + if (s.nghiDay) " (nghỉ)" else "",
                    style = TextStyle(color = INK, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text("${s.timeText} · ${s.phong}", style = TextStyle(color = SUB, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}
