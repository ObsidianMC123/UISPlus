package vn.uisplus.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.uisplus.data.AppData
import vn.uisplus.data.VN_ZONE
import vn.uisplus.data.dowShort
import vn.uisplus.data.hm
import vn.uisplus.focus.Dnd
import vn.uisplus.focus.Focus
import vn.uisplus.focus.FocusState
import vn.uisplus.focus.FocusStats
import vn.uisplus.focus.Phase
import vn.uisplus.ui.theme.Ui
import vn.uisplus.ui.theme.subjectColor
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun phaseColors(p: Phase): Pair<Color, Color> = when (p) {
    Phase.FOCUS -> Ui.Brand to Ui.Brand2
    Phase.SHORT -> Ui.Green to Ui.Sky
    Phase.LONG -> Ui.Violet to Ui.Sky
}

@Composable
fun FocusScreen(vm: MainViewModel, d: AppData) {
    val ctx = LocalContext.current
    val st by Focus.state.collectAsStateWithLifecycle()
    val log by Focus.log.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val stats = remember(log) { Focus.stats(log) }
    val subjects = remember(d) {
        d.sessions.filter { it.hocKy == d.currentHocKy }.ifEmpty { d.sessions }
            .distinctBy { it.maMon }.map { it.maMon to it.tenMon }.sortedBy { it.second }
    }

    // Dong ho theo tung khung hinh khi dang chay (vong tien do muot)
    val now by produceState(System.currentTimeMillis(), st.running) {
        value = System.currentTimeMillis()
        if (st.running) while (true) {
            withFrameMillis { }
            value = System.currentTimeMillis()
        }
    }
    // Du phong: neu bao thuc bi tre, app tu chot phien khi dang mo
    LaunchedEffect(st.running, st.endAt) {
        if (st.running) {
            delay((st.endAt - System.currentTimeMillis() + 1500).coerceAtLeast(0))
            Focus.onAlarm(ctx)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 14.dp, bottom = 130.dp),
    ) {
        item { Stagger(0) { FocusHeader(st, prefs.roundsBeforeLong, stats.streak) } }
        item {
            Stagger(1) {
                PhaseTabs(st.phase, enabled = !st.active) { Focus.setPhase(ctx, it) }
            }
        }
        item { Stagger(2) { TimerDial(st, now) } }
        item { Stagger(3) { Controls(st) } }

        item { SectionTitle("Học môn gì?") }
        item {
            Stagger(4) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SubjectChip("Tự do", Ui.Sub, st.subject.isBlank()) { Focus.setSubject(ctx, "") } }
                    items(subjects, key = { it.first }) { (ma, ten) ->
                        SubjectChip(ten, subjectColor(ma), st.subject == ten) { Focus.setSubject(ctx, ten) }
                    }
                }
            }
        }

        item { SectionTitle("Khung giờ trống hôm nay") }
        item { Stagger(5) { FreeSlots(d, prefs.focusMin, prefs.shortBreakMin) } }

        item { SectionTitle("Thống kê 7 ngày", if (stats.totalSessions > 0) "${stats.totalSessions} phiên" else null) }
        item { Stagger(6) { StatsCard(stats) } }

        item { SectionTitle("Tùy chỉnh") }
        item {
            Stagger(7) {
                FCard {
                    FSlider("Tập trung", prefs.focusMin, 10f..60f, 9, "phút") { v -> vm.updatePrefs { it.copy(focusMin = v) } }
                    HorizontalDivider(color = Ui.Line)
                    FSlider("Nghỉ ngắn", prefs.shortBreakMin, 3f..15f, 11, "phút") { v -> vm.updatePrefs { it.copy(shortBreakMin = v) } }
                    HorizontalDivider(color = Ui.Line)
                    FSlider("Nghỉ dài", prefs.longBreakMin, 10f..30f, 3, "phút") { v -> vm.updatePrefs { it.copy(longBreakMin = v) } }
                    HorizontalDivider(color = Ui.Line)
                    FSlider("Nghỉ dài sau", prefs.roundsBeforeLong, 2f..6f, 3, "phiên") { v -> vm.updatePrefs { it.copy(roundsBeforeLong = v) } }
                    HorizontalDivider(color = Ui.Line)
                    FSwitch(Ic.Play, "Tự chạy phiên kế tiếp", "Hết giờ học tự sang giờ nghỉ và ngược lại", prefs.focusAutoNext) { on ->
                        vm.updatePrefs { it.copy(focusAutoNext = on) }
                    }
                    HorizontalDivider(color = Ui.Line)
                    FSwitch(Ic.Dnd, "Không làm phiền khi học", if (Dnd.granted(ctx)) "Tự bật khi đang tập trung, tắt khi nghỉ" else "Cần cấp quyền \"Không làm phiền\" cho app", prefs.focusDnd) { on ->
                        vm.updatePrefs { it.copy(focusDnd = on) }
                        if (on && !Dnd.granted(ctx)) {
                            runCatching {
                                ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusHeader(st: FocusState, rounds: Int, streak: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Tập trung", style = MaterialTheme.typography.headlineMedium, color = Ui.Ink)
            val r = (st.round % rounds.coerceAtLeast(1)) + 1
            Text("Pomodoro · vòng $r/$rounds", style = MaterialTheme.typography.bodyMedium, color = Ui.Sub)
        }
        val t = rememberInfiniteTransition(label = "flame")
        val fl by t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fl")
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Ui.Amber.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.Flame, null, tint = Ui.Amber, modifier = Modifier.size(18.dp).graphicsLayer {
                val s = if (streak > 0) fl else 1f
                scaleX = s; scaleY = s
            })
            Spacer(Modifier.width(4.dp))
            Text("$streak ngày", style = MaterialTheme.typography.labelLarge, color = Ui.Amber)
        }
    }
}

@Composable
private fun PhaseTabs(selected: Phase, enabled: Boolean, onSelect: (Phase) -> Unit) {
    val opts = Phase.entries
    val idx = opts.indexOf(selected)
    val alpha by animateFloatAsState(if (enabled) 1f else 0.6f, label = "pta")
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(46.dp).graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(16.dp)).background(Ui.Soft).padding(4.dp),
    ) {
        val w = maxWidth / opts.size
        val x by animateDpAsState(w * idx, spring(dampingRatio = 0.7f, stiffness = 420f), label = "ptx")
        val (c1, c2) = phaseColors(selected)
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .elev(8.dp, RoundedCornerShape(12.dp), spotColor = c1)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(c1, c2))),
        )
        Row(Modifier.fillMaxSize()) {
            opts.forEachIndexed { i, p ->
                val c by animateColorAsState(if (i == idx) Color.White else Ui.Sub, label = "ptc$i")
                Box(
                    Modifier.weight(1f).fillMaxHeight().bounceClick(scaleDown = 0.94f, enabled = enabled) { onSelect(p) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${p.emoji} ${p.label}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = c, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TimerDial(st: FocusState, now: Long) {
    val (c1, c2) = phaseColors(st.phase)
    val ac1 by animateColorAsState(c1, tween(700), label = "dc1")
    val ac2 by animateColorAsState(c2, tween(700), label = "dc2")
    val rem = st.remaining(now)
    val progress = if (st.totalMs > 0) (1f - rem.toFloat() / st.totalMs).coerceIn(0f, 1f) else 0f
    val p by animateFloatAsState(
        progress,
        spring(dampingRatio = 1f, stiffness = if (st.running) 4000f else 90f),
        label = "dp",
    )
    val inf = rememberInfiniteTransition(label = "dial")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "sp")
    val run by animateFloatAsState(if (st.running) 1f else 0f, tween(600), label = "run")
    val track = Ui.Soft
    val tick = Ui.Sub

    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(310.dp)) {
            val r = size.minDimension / 2
            val c = center
            // quang sang tho nhe khi dang chay
            val glow = r * (0.82f + 0.12f * breathe * run)
            drawCircle(
                Brush.radialGradient(listOf(ac1.copy(alpha = 0.10f + 0.22f * run), Color.Transparent), c, glow),
                glow, c,
            )
            // vach phut quay cham
            rotate(spin * run, c) {
                for (i in 0 until 60) {
                    val big = i % 5 == 0
                    val a = Math.toRadians(i * 6.0)
                    val inner = r * (if (big) 0.85f else 0.88f)
                    val outer = r * 0.91f
                    drawLine(
                        tick.copy(alpha = if (big) 0.45f else 0.2f),
                        Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                        Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                        strokeWidth = if (big) 4f else 2.5f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            val sw = 18.dp.toPx()
            val ar = r * 0.72f
            val tl = Offset(c.x - ar, c.y - ar)
            val sz = Size(ar * 2, ar * 2)
            drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            val sweep = 360f * p
            if (sweep > 0.5f) {
                rotate(-90f, c) {
                    drawArc(
                        Brush.sweepGradient(listOf(ac1, ac2, ac1), c),
                        0f, sweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                val ha = Math.toRadians((sweep - 90f).toDouble())
                val h = Offset(c.x + ar * cos(ha).toFloat(), c.y + ar * sin(ha).toFloat())
                drawCircle(ac2.copy(alpha = 0.30f), sw * (0.85f + 0.35f * breathe * run), h)
                drawCircle(Color.White, sw * 0.30f, h)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                st.phase.label.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                color = ac1,
            )
            OdometerText(
                Focus.mmss(rem),
                MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 58.sp, fontFeatureSettings = "tnum"),
                Ui.Ink,
            )
            Text(
                st.subject.ifBlank { "Học tự do" },
                style = MaterialTheme.typography.bodyMedium, color = Ui.Sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 190.dp),
            )
            AnimatedVisibility(st.active && !st.running, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text("Đang tạm dừng", style = MaterialTheme.typography.labelMedium, color = Ui.Amber, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Controls(st: FocusState) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val (c1, c2) = phaseColors(st.phase)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundBtn(Ic.Replay, enabled = st.active || st.round > 0 || st.phase != Phase.FOCUS) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Focus.stop(ctx)
        }
        Spacer(Modifier.width(30.dp))
        val t = rememberInfiniteTransition(label = "pb")
        val halo by t.animateFloat(1f, 1.35f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "halo")
        val haloA by t.animateFloat(0.35f, 0f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "haloA")
        Box(contentAlignment = Alignment.Center) {
            if (!st.running) {
                Box(
                    Modifier.size(84.dp).graphicsLayer { scaleX = halo; scaleY = halo; alpha = haloA }
                        .clip(CircleShape).background(c1),
                )
            }
            Box(
                Modifier.size(84.dp)
                    .elev(22.dp, CircleShape, spotColor = c1)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(c1, c2)))
                    .shimmer(alpha = 0.18f)
                    .bounceClick(scaleDown = 0.9f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Focus.toggle(ctx)
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = st.running,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.4f) + fadeIn())
                            .togetherWith(scaleOut(targetScale = 0.4f) + fadeOut())
                    },
                    label = "pp",
                ) { running ->
                    Icon(if (running) Ic.Pause else Ic.Play, if (running) "Tạm dừng" else "Bắt đầu", tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }
        Spacer(Modifier.width(30.dp))
        RoundBtn(Ic.Skip, enabled = true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Focus.skip(ctx)
        }
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (enabled) 1f else 0.4f, label = "rba")
    Box(
        Modifier.size(54.dp).graphicsLayer { alpha = a }
            .clip(CircleShape).background(Ui.Card)
            .border(1.dp, Ui.Line, CircleShape)
            .bounceClick(scaleDown = 0.88f, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Ui.Ink, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun SubjectChip(text: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) color else Ui.Card, tween(250), label = "scb")
    val fg by animateColorAsState(if (selected) Color.White else Ui.Ink, tween(250), label = "scf")
    Row(
        Modifier.bounceClick(scaleDown = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else Ui.Line, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) Color.White else color))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp))
    }
}

private fun freeSlots(d: AppData, today: Long, nowMin: Int): List<Pair<Int, Int>> {
    val busy = d.sessionsOn(today).filter { !it.nghiDay }.map { it.startMin to it.endMin }.sortedBy { it.first }
    var cur = ((maxOf(nowMin, 7 * 60) + 4) / 5) * 5
    val end = 22 * 60
    val out = mutableListOf<Pair<Int, Int>>()
    for ((s, e) in busy) {
        if (s - cur >= 30) out += cur to s
        cur = maxOf(cur, e)
    }
    if (end - cur >= 30) out += cur to end
    return out
}

@Composable
private fun FreeSlots(d: AppData, focusMin: Int, shortMin: Int) {
    val now = rememberNow(60_000)
    val today = LocalDate.now(VN_ZONE).toEpochDay()
    val t = LocalTime.now(VN_ZONE)
    val slots = remember(d, today, now / 60_000) { freeSlots(d, today, t.hour * 60 + t.minute) }
    if (slots.isEmpty()) {
        EmptyCard("Hôm nay hết khung trống rồi", "Nghỉ ngơi cho khỏe nhé, mai học tiếp", "")
        return
    }
    FCard {
        slots.take(4).forEachIndexed { i, (s, e) ->
            if (i > 0) HorizontalDivider(color = Ui.Line)
            val len = e - s
            val sessions = (len / (focusMin + shortMin).coerceAtLeast(1)).coerceAtLeast(if (len >= focusMin) 1 else 0)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ui.Green.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.Clock, null, tint = Ui.Green, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${hm(s)} – ${hm(e)}", style = MaterialTheme.typography.titleSmall, color = Ui.Ink)
                    val hh = len / 60
                    val mm = len % 60
                    Text(
                        (if (hh > 0) "${hh} giờ " else "") + (if (mm > 0) "$mm phút" else "") + " trống",
                        style = MaterialTheme.typography.bodySmall, color = Ui.Sub,
                    )
                }
                if (sessions > 0) Pill(null, "~$sessions phiên", Ui.Brand, Ui.Brand.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
private fun StatsCard(s: FocusStats) {
    FCard {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox(Ic.Timer, "Hôm nay", "${s.todayMin}", "phút", Ui.Brand, Modifier.weight(1f))
            StatBox(Ic.Flame, "Chuỗi", "${s.streak}", "ngày", Ui.Amber, Modifier.weight(1f))
            val h = s.weekMin / 60f
            StatBox(Ic.Calendar, "7 ngày", if (h >= 10) "${h.roundToInt()}" else "%.1f".format(h), "giờ", Ui.Violet, Modifier.weight(1f))
        }
        WeekBars(s.last7)
        if (s.bySubject.isNotEmpty()) {
            HorizontalDivider(color = Ui.Line, modifier = Modifier.padding(top = 6.dp))
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val max = s.bySubject.first().second.coerceAtLeast(1)
                s.bySubject.take(5).forEachIndexed { i, (name, min) ->
                    val f = remember(name, min) { Animatable(0f) }
                    LaunchedEffect(name, min) {
                        delay(120L + i * 70L)
                        f.animateTo(min.toFloat() / max, spring(dampingRatio = 0.8f, stiffness = 120f))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.labelLarge, color = Ui.Ink, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("$min phút", style = MaterialTheme.typography.labelMedium, color = Ui.Sub)
                        }
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Ui.Soft)) {
                            Box(
                                Modifier.fillMaxWidth(f.value.coerceIn(0.02f, 1f)).fillMaxHeight()
                                    .clip(RoundedCornerShape(50)).background(Ui.BrandGradient),
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                "Chưa có phiên nào. Bấm ▶ để bắt đầu phiên đầu tiên!",
                style = MaterialTheme.typography.bodySmall, color = Ui.Sub,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun StatBox(icon: ImageVector, label: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f)).padding(12.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Ui.Ink)
            Spacer(Modifier.width(3.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Ui.Sub, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ui.Sub)
    }
}

@Composable
private fun WeekBars(days: List<Pair<LocalDate, Int>>) {
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(25)
    Row(
        Modifier.fillMaxWidth().height(130.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { i, (date, min) ->
            val isToday = i == days.lastIndex
            val f = remember(date, min) { Animatable(0f) }
            LaunchedEffect(date, min) {
                delay(80L + i * 60L)
                f.animateTo(min.toFloat() / max, spring(dampingRatio = 0.55f, stiffness = 140f))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (min > 0) Text("$min", style = MaterialTheme.typography.labelSmall, color = if (isToday) Ui.Brand else Ui.Sub)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Ui.Soft))
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(f.value.coerceIn(0f, 1.2f).coerceAtMost(1f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isToday) Brush.verticalGradient(listOf(Ui.Brand2, Ui.Brand))
                                else Brush.verticalGradient(listOf(Ui.Brand.copy(alpha = 0.45f), Ui.Brand.copy(alpha = 0.25f))),
                            ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(dowShort(date), style = MaterialTheme.typography.labelSmall, color = if (isToday) Ui.Brand else Ui.Faint)
            }
        }
    }
}

@Composable
private fun FCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ui.Card).padding(horizontal = 16.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun FSlider(label: String, value: Int, range: ClosedFloatingPointRange<Float>, steps: Int, unit: String, onDone: (Int) -> Unit) {
    var temp by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = Ui.Ink, modifier = Modifier.weight(1f))
            Text("${temp.roundToInt()} $unit", style = MaterialTheme.typography.labelLarge, color = Ui.Brand)
        }
        Slider(
            value = temp,
            onValueChange = { temp = it },
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { onDone(temp.roundToInt()) },
            colors = SliderDefaults.colors(thumbColor = Ui.Brand, activeTrackColor = Ui.Brand, inactiveTrackColor = Ui.Line),
        )
    }
}

@Composable
private fun FSwitch(icon: ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ui.Brand.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Ui.Brand, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ui.Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ui.Sub)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Ui.Brand, uncheckedBorderColor = Ui.Line, uncheckedTrackColor = Ui.Soft),
        )
    }
}
