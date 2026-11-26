package vn.uisplus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.uisplus.data.AppData
import vn.uisplus.data.ClassSession
import vn.uisplus.data.VN_ZONE
import vn.uisplus.data.Week
import vn.uisplus.data.dowShort
import vn.uisplus.ui.theme.Ui
import vn.uisplus.ui.theme.subjectColor
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val DM = DateTimeFormatter.ofPattern("dd/MM")

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Tuan cua TKB; neu API khong tra ds_tuan thi tu dung theo thu Hai. */
private fun buildWeeks(d: AppData): List<Week> {
    if (d.weeks.isNotEmpty()) return d.weeks
    val days = d.sessions.map { it.epochDay }
    if (days.isEmpty()) {
        val mon = LocalDate.now(VN_ZONE).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
        return listOf(Week(1, mon.toEpochDay(), mon.toEpochDay() + 6, ""))
    }
    val first = LocalDate.ofEpochDay(days.min()).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.toEpochDay()
    val out = mutableListOf<Week>()
    var s = first
    var i = 1
    while (s <= days.max()) {
        out += Week(i++, s, s + 6, ""); s += 7
    }
    return out
}

@Composable
fun WeekScreen(vm: MainViewModel, d: AppData, onOpen: (ClassSession) -> Unit) {
    val now = rememberNow(30_000)
    val today = Instant.ofEpochMilli(now).atZone(VN_ZONE).toLocalDate().toEpochDay()
    val weeks = remember(d) { buildWeeks(d) }
    val initial = remember(weeks) {
        weeks.indexOfFirst { today in it.startEpochDay..it.endEpochDay }.takeIf { it >= 0 }
            ?: weeks.indexOfFirst { it.startEpochDay > today }.takeIf { it >= 0 }
            ?: weeks.lastIndex.coerceAtLeast(0)
    }
    var wi by rememberSaveable(weeks.size) { mutableIntStateOf(initial) }
    var grid by rememberSaveable { mutableStateOf(false) }
    val week = weeks[wi.coerceIn(0, weeks.lastIndex)]
    val weekSessions = remember(d, week) { d.sessions.filter { it.epochDay in week.startEpochDay..week.endEpochDay } }
    val periods = remember(d) { d.timetables.firstOrNull()?.periods.orEmpty() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lịch học", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp), color = Ui.Ink)
                Text(d.currentSemesterName, style = MaterialTheme.typography.bodySmall, color = Ui.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ViewToggle(grid) { grid = it }
        }
        WeekSwitcher(
            week = week,
            count = weekSessions.count { !it.nghiDay },
            isCurrent = today in week.startEpochDay..week.endEpochDay,
            canPrev = wi > 0,
            canNext = wi < weeks.lastIndex,
            onPrev = { wi-- },
            onNext = { wi++ },
            onToday = { wi = initial },
        )
        AnimatedContent(
            targetState = Pair(wi, grid),
            transitionSpec = {
                val dir = when {
                    targetState.first > initialState.first -> 1
                    targetState.first < initialState.first -> -1
                    else -> 0
                }
                if (dir == 0) fadeIn(tween(300)).togetherWith(fadeOut(tween(200)))
                else (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 300f)) { it * dir } + fadeIn())
                    .togetherWith(slideOutHorizontally(tween(250)) { -it * dir / 2 } + fadeOut(tween(200)))
            },
            label = "week",
            modifier = Modifier.fillMaxSize(),
        ) { (idx, g) ->
            val w = weeks[idx.coerceIn(0, weeks.lastIndex)]
            val list = remember(d, w) { d.sessions.filter { it.epochDay in w.startEpochDay..w.endEpochDay } }
            if (g) WeekGrid(w, list, today, periods.size, onOpen)
            else DayPager(w, list, today, now, onOpen)
        }
    }
}

@Composable
private fun ViewToggle(grid: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (grid) 44.dp else 0.dp, spring(dampingRatio = 0.65f, stiffness = 500f), label = "tog")
    Box(
        Modifier
            .elev(8.dp, RoundedCornerShape(16.dp), spotColor = Ui.Brand.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card)
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = x).size(44.dp, 36.dp).clip(RoundedCornerShape(12.dp)).background(Ui.BrandSoft))
        Row {
            listOf(false to Ic.List, true to Ic.Grid).forEach { (g, icon) ->
                val tint by animateColorAsState(if (grid == g) Ui.Brand else Ui.Sub, label = "tt")
                Box(
                    Modifier.size(44.dp, 36.dp).bounceClick { onChange(g) },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, if (g) "Dạng lưới" else "Dạng danh sách", tint = tint, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun WeekSwitcher(
    week: Week, count: Int, isCurrent: Boolean, canPrev: Boolean, canNext: Boolean,
    onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(Ic.Left, canPrev, onPrev)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(week, label = "wk", transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(150))) }) { w ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tuần ${w.tuan}", style = MaterialTheme.typography.titleMedium, color = Ui.Ink)
                        if (isCurrent) {
                            Spacer(Modifier.width(6.dp))
                            Pill(null, "tuần này", Ui.Brand, Ui.Brand.copy(alpha = 0.1f))
                        }
                    }
                    Text(
                        "${LocalDate.ofEpochDay(w.startEpochDay).format(DM)} – ${LocalDate.ofEpochDay(w.endEpochDay).format(DM)} · $count buổi",
                        style = MaterialTheme.typography.bodySmall, color = Ui.Sub,
                    )
                }
            }
        }
        if (!isCurrent) {
            Text(
                "Hôm nay",
                style = MaterialTheme.typography.labelMedium,
                color = Ui.Brand,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onToday).padding(6.dp),
            )
        }
        RoundIcon(Ic.Right, canNext, onNext)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (enabled) 1f else 0.3f, label = "ri")
    Box(
        Modifier.size(42.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Ui.Soft)
            .bounceClick(scaleDown = 0.85f, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Ui.Ink) }
}

@Composable
private fun DayPager(week: Week, sessions: List<ClassSession>, today: Long, now: Long, onOpen: (ClassSession) -> Unit) {
    val days = remember(week) { (week.startEpochDay..week.endEpochDay).toList() }
    val byDay = remember(sessions) { sessions.groupBy { it.epochDay } }
    val start = remember(week) {
        days.indexOf(today).takeIf { it >= 0 } ?: days.indexOfFirst { byDay[it]?.isNotEmpty() == true }.takeIf { it >= 0 } ?: 0
    }
    val pager = rememberPagerState(initialPage = start) { days.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEachIndexed { i, ed ->
                DayChip(
                    date = LocalDate.ofEpochDay(ed),
                    selected = pager.currentPage == i,
                    isToday = ed == today,
                    count = byDay[ed]?.count { !it.nghiDay } ?: 0,
                    modifier = Modifier.weight(1f),
                ) { scope.launch { pager.animateScrollToPage(i) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val list = byDay[days[page]].orEmpty()
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    val s = lerpF(0.9f, 1f, 1f - off)
                    scaleX = s; scaleY = s
                    alpha = lerpF(0.4f, 1f, 1f - off)
                },
            ) {
                if (list.isEmpty()) {
                    Box(Modifier.padding(20.dp)) {
                        EmptyCard("Không có lịch", "${dowShort(LocalDate.ofEpochDay(days[page]))} ${LocalDate.ofEpochDay(days[page]).format(DM)} trống trơn", "🌿")
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 130.dp),
                    ) {
                        itemsIndexed(list, key = { _, s -> s.key }) { i, s ->
                            Stagger(i, key = page) { TimelineItem(s, now, i == list.lastIndex, onOpen) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(date: LocalDate, selected: Boolean, isToday: Boolean, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Ui.Brand else Ui.Card, tween(250), label = "bg")
    val fg by animateColorAsState(if (selected) Color.White else Ui.Ink, tween(250), label = "fg")
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "lift")
    Column(
        modifier
            .graphicsLayer { translationY = -6f * lift; scaleX = 1f + 0.06f * lift; scaleY = 1f + 0.06f * lift }
            .bounceClick(scaleDown = 0.9f, onClick = onClick)
            .elev((10 * lift).dp, RoundedCornerShape(16.dp), spotColor = Ui.Brand)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (isToday && !selected) Modifier.border(1.5.dp, Ui.Brand.copy(alpha = 0.5f), RoundedCornerShape(16.dp)) else Modifier)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(dowShort(date), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(alpha = 0.85f) else Ui.Faint)
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = fg)
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(count.coerceAtMost(3)) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) Color.White else Ui.Brand))
            }
            if (count == 0) Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun WeekGrid(week: Week, sessions: List<ClassSession>, today: Long, periodCount: Int, onOpen: (ClassSession) -> Unit) {
    val maxEnd = sessions.maxOfOrNull { it.tietBd + it.soTiet - 1 } ?: 0
    val rowFrom = 1
    val rowTo = maxOf(10, maxEnd, minOf(periodCount, 12))
    val rowH = 34.dp
    val labelW = 26.dp
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(labelW))
            for (i in 0..6) {
                val ed = week.startEpochDay + i
                val dt = LocalDate.ofEpochDay(ed)
                val isT = ed == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dowShort(dt), style = MaterialTheme.typography.labelSmall, color = if (isT) Ui.Brand else Ui.Faint)
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (isT) Ui.Brand else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${dt.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = if (isT) Color.White else Ui.Ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Ui.Card),
        ) {
            val colW = (maxWidth - labelW) / 7
            val todayCol = (today - week.startEpochDay).toInt()
            if (todayCol in 0..6) {
                Box(
                    Modifier.offset(x = labelW + colW * todayCol).width(colW).height(rowH * (rowTo - rowFrom + 1))
                        .background(Ui.Brand.copy(alpha = 0.05f)),
                )
            }
            Column {
                for (t in rowFrom..rowTo) {
                    Row(
                        Modifier.fillMaxWidth().height(rowH).drawBehind {
                            if (t > rowFrom) drawLine(Ui.Line, Offset(labelW.toPx(), 0f), Offset(size.width, 0f), 1f)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$t", style = MaterialTheme.typography.labelSmall, color = Ui.Faint, textAlign = TextAlign.Center, modifier = Modifier.width(labelW))
                    }
                }
            }
            sessions.forEachIndexed { i, s ->
                val col = (s.epochDay - week.startEpochDay).toInt()
                if (col in 0..6 && s.tietBd >= rowFrom) {
                    val c = subjectColor(s.maMon)
                    Box(
                        Modifier
                            .offset(x = labelW + colW * col, y = rowH * (s.tietBd - rowFrom))
                            .width(colW)
                            .height(rowH * s.soTiet)
                            .padding(2.dp),
                    ) {
                        Stagger(i, key = week.startEpochDay) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .bounceClick(scaleDown = 0.92f) { onOpen(s) }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (s.nghiDay) Ui.Soft else c.copy(alpha = 0.16f))
                                    .drawBehind { drawRect(c, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                                    .padding(start = 6.dp, end = 3.dp, top = 4.dp, bottom = 3.dp),
                            ) {
                                Text(
                                    s.tenMon,
                                    fontSize = 9.5.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lerp(c, Ui.Contrast, 0.35f),
                                    maxLines = (s.soTiet * 2).coerceAtMost(6),
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    s.phong.substringBefore(" "),
                                    fontSize = 8.5.sp,
                                    color = lerp(c, Ui.Contrast, 0.2f),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(130.dp))
    }
}
