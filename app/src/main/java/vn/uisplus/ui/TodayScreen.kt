package vn.uisplus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import vn.uisplus.ui.theme.Tok
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.uisplus.data.AppData
import vn.uisplus.data.ClassSession
import vn.uisplus.data.VN_ZONE
import vn.uisplus.data.dowLong
import vn.uisplus.ui.theme.Ui
import vn.uisplus.ui.theme.subjectColor
import java.time.Instant
import java.time.format.DateTimeFormatter

private val DM = DateTimeFormatter.ofPattern("dd/MM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: MainViewModel, d: AppData, onOpen: (ClassSession) -> Unit, onSeeWeek: () -> Unit) {
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val now = rememberNow(1000)
    val minuteKey = now / 60_000
    val today = remember(minuteKey) { Instant.ofEpochMilli(now).atZone(VN_ZONE).toLocalDate() }
    val todayList = remember(d, today) { d.sessionsOn(today.toEpochDay()) }
    val nextDay = remember(d, today) { d.sessions.firstOrNull { it.epochDay > today.toEpochDay() }?.date }
    val nextList = remember(d, nextDay) { nextDay?.let { d.sessionsOn(it.toEpochDay()) } ?: emptyList() }
    val live = d.sessions.firstOrNull { !it.nghiDay && now >= it.startMillis && now < it.endMillis }
    val upcoming = d.sessions.firstOrNull { !it.nghiDay && it.startMillis > now }
    val week = remember(d, today) { d.weeks.firstOrNull { today.toEpochDay() in it.startEpochDay..it.endEpochDay } }
    val weekCount = remember(d, week) { week?.let { w -> d.sessions.count { it.epochDay in w.startEpochDay..w.endEpochDay && !it.nghiDay } } ?: 0 }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    PullToRefreshBox(isRefreshing = syncing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 14.dp, bottom = 130.dp),
        ) {
            item(key = "header") { Stagger(0) { Header(d, today, syncing, now) { vm.refresh() } } }
            item(key = "hero") { Stagger(1) { HeroCard(live, upcoming, now, onOpen) } }
            item(key = "bus") { Stagger(2) { BusCard(d, now) } }
            item(key = "t_title") { SectionTitle("Hôm nay", "${dowLong(today)} ${today.format(DM)}") }
            if (todayList.isEmpty()) {
                item(key = "t_empty") { Stagger(3) { EmptyCard("Hôm nay không có lịch", "Nghỉ ngơi hoặc ôn bài thôi", "🛋️") } }
            } else {
                itemsIndexed(todayList, key = { _, s -> "t" + s.key }) { i, s ->
                    Stagger(3 + i) { TimelineItem(s, now, i == todayList.lastIndex, onOpen) }
                }
            }
            if (nextDay != null && nextList.isNotEmpty()) {
                val label = if (nextDay.toEpochDay() == today.toEpochDay() + 1) "Ngày mai" else dowLong(nextDay)
                item(key = "n_title") { SectionTitle(label, nextDay.format(DM)) }
                itemsIndexed(nextList, key = { _, s -> "n" + s.key }) { i, s ->
                    Stagger(5 + i) { TimelineItem(s, now, i == nextList.lastIndex, onOpen) }
                }
            }
            val recent = d.changes.filter { now - it.at < 7L * 24 * 3600_000 }.take(5)
            if (recent.isNotEmpty()) {
                item(key = "c_title") { SectionTitle("Lịch vừa thay đổi", "Xóa") { vm.clearChanges() } }
                itemsIndexed(recent, key = { i, e -> "c$i${e.at}${e.title}" }) { i, e -> Stagger(i) { ChangeItem(e) } }
            }
        }
    }
}

@Composable
private fun Header(d: AppData, today: java.time.LocalDate, syncing: Boolean, now: Long, onRefresh: () -> Unit) {
    val hour = Instant.ofEpochMilli(now).atZone(VN_ZONE).hour
    val greet = when (hour) {
        in 4..10 -> "Chào buổi sáng"
        in 11..12 -> "Chào buổi trưa"
        in 13..17 -> "Chào buổi chiều"
        else -> "Chào buổi tối"
    }
    val first = d.studentName.trim().split(" ").lastOrNull().orEmpty().ifBlank { d.userName }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$greet,", style = MaterialTheme.typography.bodyLarge, color = Ui.Sub)
            Text(
                "$first",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, letterSpacing = (-0.5).sp),
                color = Ui.Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Cập nhật ${relTime(d.lastSync, now)} · ${d.currentSemesterName}",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.Faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val t = rememberInfiniteTransition(label = "spin")
        val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "rot")
        Box(
            Modifier
                .size(44.dp)
                .bounceClick(onClick = onRefresh)
                .elev(1.dp, CircleShape)
                .clip(CircleShape)
                .background(Ui.Card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Ic.Refresh, "Đồng bộ", tint = if (syncing) Ui.Brand else Ui.Ink,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = if (syncing) rot else 0f },
            )
        }
    }
}

private fun countdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val d = total / 86400
    val h = (total % 86400) / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (d > 0) "%d ngày %02d:%02d".format(d, h, m) else "%02d:%02d:%02d".format(h, m, s)
}

/** v1.4: the "Dang hoc / Tiep theo" phang — thanh mau mon ben trai, so dem nguoc to, thanh tien do mong. */
@Composable
private fun HeroCard(live: ClassSession?, upcoming: ClassSession?, now: Long, onOpen: (ClassSession) -> Unit) {
    val s = live ?: upcoming
    val base = s?.let { subjectColor(it.maMon) } ?: Ui.Brand
    val c1 by animateColorAsState(base, tween(600), label = "c1")
    Box(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.98f) { s?.let(onOpen) }
            .elev(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card)
            .drawBehind { drawRect(c1, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 400f))
            .padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
    ) {
        AnimatedContent(
            targetState = Pair(s?.key, live != null),
            transitionSpec = {
                (fadeIn(tween(Tok.DurMedium, 80, Tok.EmphasizedDecel)) +
                    slideInVertically(tween(Tok.DurMedium, easing = Tok.EmphasizedDecel)) { it / 8 })
                    .togetherWith(fadeOut(tween(Tok.DurShort)))
            },
            label = "heroContent",
        ) { (_, isLive) ->
            if (s == null) {
                Column {
                    Text("Xong lịch", style = MaterialTheme.typography.labelLarge, color = Ui.Sub)
                    Spacer(Modifier.height(4.dp))
                    Text("Không còn buổi học nào sắp tới", style = MaterialTheme.typography.titleLarge, color = Ui.Ink)
                    Text("Kéo xuống để đồng bộ lại nếu trường vừa cập nhật lịch.", style = MaterialTheme.typography.bodySmall, color = Ui.Sub)
                }
            } else {
                HeroContent(s, isLive, now, c1)
            }
        }
    }
}

@Composable
private fun HeroContent(s: ClassSession, isLive: Boolean, now: Long, c: Color) {
    val numStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 34.sp, fontWeight = FontWeight.Bold,
        fontFeatureSettings = Tok.TNUM, letterSpacing = (-0.5).sp,
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLive) {
                PulseDot(c, Modifier.size(10.dp), dotSize = 8.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isLive) "Đang học" else "Buổi tiếp theo",
                style = MaterialTheme.typography.labelLarge,
                color = if (isLive) c else Ui.Sub,
            )
            Spacer(Modifier.weight(1f))
            Text("${dowLong(s.date)}, ${s.date.format(DM)}", style = MaterialTheme.typography.labelMedium, color = Ui.Sub)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            s.tenMon,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 27.sp),
            color = Ui.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            listOf(s.phong.ifBlank { "Chưa có phòng" }, s.timeText).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = Tok.TNUM),
            color = Ui.Sub,
        )
        Spacer(Modifier.height(14.dp))
        Text(if (isLive) "Còn lại" else "Bắt đầu sau", style = MaterialTheme.typography.bodySmall, color = Ui.Sub)
        OdometerText(countdown(if (isLive) s.endMillis - now else s.startMillis - now), numStyle, Ui.Ink)
        if (isLive) {
            val total = (s.endMillis - s.startMillis).coerceAtLeast(1)
            val target = ((now - s.startMillis).toFloat() / total).coerceIn(0f, 1f)
            val p by androidx.compose.animation.core.animateFloatAsState(target, tween(900, easing = Tok.Emphasized), label = "hp")
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ui.Soft)) {
                Box(Modifier.fillMaxWidth(p).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
            }
        }
    }
}
