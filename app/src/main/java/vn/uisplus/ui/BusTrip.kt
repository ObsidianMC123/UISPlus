package vn.uisplus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import vn.uisplus.bus.Arrival
import vn.uisplus.bus.BusData
import vn.uisplus.bus.Itinerary
import vn.uisplus.bus.DirectTrip
import vn.uisplus.bus.TripPlan
import vn.uisplus.bus.Place
import vn.uisplus.bus.Stop
import vn.uisplus.bus.VarGeo
import vn.uisplus.bus.Vehicle
import vn.uisplus.ui.theme.Ui
import java.time.Instant
import java.time.ZoneId

// ==================================================================== du lieu chuyen xe (v1.5)

internal data class TripKey(val stopId: Int, val routeId: Int, val varId: Int)

/** 1 xe dang toi tram: ban ghi ETA + vi tri uoc luong tren lo trinh (neu da co lo trinh). */
internal class TripBus(val a: Arrival, val veh: Vehicle?, val stopsAway: Int?)

/** Xe dang toi [stopId], GAN TRUOC XA SAU (theo ETA). Vi tri = vi tri tram - d (m doc tuyen). */
internal fun tripBuses(geo: VarGeo?, stopId: Int, arrs: List<Arrival>): List<TripBus> {
    val idx = geo?.stops?.indexOfFirst { it.id == stopId } ?: -1
    return arrs.sortedWith(compareBy({ it.etaSec }, { it.distM })).map { a ->
        if (geo == null || idx < 0) TripBus(a, null, null) else {
            val pos = (geo.stopPos[idx] - a.distM).coerceAtLeast(0.0)
            val (la, ln) = geo.pointAt(pos)
            val next = geo.stopPos.indexOfFirst { it >= pos - 5 }.let { if (it < 0) geo.stops.size - 1 else it }.coerceAtMost(idx)
            TripBus(a, Vehicle(a.plate, la, ln, pos, 0.0, a.gpsAt, next), (idx - next).coerceAtLeast(0))
        }
    }
}

/** Toc do uoc luong (m/s) suy tu ETA cua chinh xe do; thieu du lieu -> 5 m/s (~18 km/h). */
internal fun tripSpeed(a: Arrival): Double =
    if (a.etaSec >= 60 && a.distM >= 200) (a.distM.toDouble() / a.etaSec).coerceIn(2.5, 12.0) else 5.0

/**
 * Tram "chinh" xe se di qua tu [from]: tram ke tiep, tram cua ban, tram xuong, ben cuoi
 * + cac tram dau moi (nhieu tuyen di qua nhat), khoang 1/5 so tram con lai (toi thieu 5).
 */
internal fun keyStopIdx(g: VarGeo, from: Int, vararg must: Int): List<Int> {
    val n = g.stops.size
    if (n == 0) return emptyList()
    val start = from.coerceIn(0, n - 1)
    val range = (start until n).toList()
    val quota = maxOf(5, range.size / 5)
    val hubs = range.sortedByDescending { g.stops[it].routes.size }.take(quota)
    return (hubs + start + (n - 1) + must.toList()).filter { it in start until n }.distinct().sorted()
}

internal fun clockText(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}

@Composable
private fun EtaBig(text: String, color: Color, size: Int = 17) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(260, easing = vn.uisplus.ui.theme.Tok.EmphasizedDecel)) { it / 2 } + fadeIn(tween(200)))
                .togetherWith(slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
        },
        label = "eta",
    ) { t -> Text(t, color = color, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, maxLines = 1) }
}

@Composable
private fun LiveLine(loading: Boolean, hasData: Boolean, text: String, at: Long, now: Long) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (loading && !hasData) {
            CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text("  Đang lấy giờ xe tới…", color = Ui.Sub, fontSize = 12.sp)
        } else if (at > 0) {
            PulseDot(Color(0xFF10B981), Modifier.size(10.dp), 7.dp)
            Text("  $text · cập nhật ${relTime(at, now)}", color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ==================================================================== tram -> tuyen: cac xe dang toi

@Composable
internal fun ArrPanel(
    routeNo: String, dest: String, stop: Stop?, geo: VarGeo?, live: Live<List<Arrival>>, now: Long,
    onBus: (String) -> Unit, onFullRoute: () -> Unit, onBack: () -> Unit, onClose: () -> Unit,
) {
    val color = Color(routeColor(routeNo))
    PanelHeader("→ $dest", stop?.let { "Xe tới trạm ${it.name}" } ?: "", onClose, onBack = onBack, lead = { RouteBadge(routeNo, big = true) })
    val buses = remember(geo, live.data, stop?.id) { tripBuses(geo, stop?.id ?: -1, live.data.orEmpty()) }
    val elapsed = if (live.at > 0) ((now - live.at) / 1000).toInt().coerceAtLeast(0) else 0
    LiveLine(live.loading, live.data != null, "${buses.size} xe đang tới · gần trước, xa sau", live.at, now)
    live.error?.let { Text(it, color = Color(0xFFDC2626), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
    LazyColumn(Modifier.padding(bottom = 8.dp)) {
        if (live.data != null && buses.isEmpty()) item {
            Text("Hiện chưa có xe $routeNo nào sắp tới trạm này. EBMS chỉ báo xe trong khoảng 1 giờ tới.",
                color = Ui.Sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        itemsIndexed(buses, key = { _, b -> b.a.plate }) { i, b ->
            val eta = (b.a.etaSec - elapsed).coerceAtLeast(0)
            val first = i == 0
            Row(
                Modifier.fillMaxWidth().clickable { onBus(b.a.plate) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(if (first) color else color.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = if (first) Color.White else color, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.a.plate, color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                    val gpsMin = if (b.a.gpsAt > 0) ((now - b.a.gpsAt) / 60_000).toInt() else 0
                    val sub = listOfNotNull(
                        "còn ${distText(b.a.distM.toDouble())}",
                        b.stopsAway?.let { if (it <= 0) "trạm kế tiếp" else "cách $it trạm" },
                        if (gpsMin >= 2) "GPS ${gpsMin}' trước" else null,
                    ).joinToString(" · ")
                    Text(sub, color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    EtaBig(etaText(eta), if (eta < 300) Color(0xFF059669) else Ui.Ink)
                    Text("~" + clockText(now + eta * 1000L), color = Ui.Faint, fontSize = 11.sp)
                }
                Icon(Ic.Right, null, tint = Ui.Faint, modifier = Modifier.padding(start = 4.dp).size(18.dp))
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).border(1.dp, Ui.Line, RoundedCornerShape(12.dp))
                    .clickable(onClick = onFullRoute).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Bus, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Xem toàn tuyến $routeNo", color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Ic.Right, null, tint = Ui.Faint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ==================================================================== xe -> cac tram xe se di qua

@Composable
internal fun VehPanel(
    routeNo: String, dest: String, plate: String, stop: Stop?, alightId: Int?, geo: VarGeo?,
    arrival: Arrival?, at: Long, gone: Boolean, now: Long,
    allStops: Boolean, onToggleAll: () -> Unit, onStopTap: (Stop) -> Unit, onBack: () -> Unit, onClose: () -> Unit,
) {
    val color = Color(routeColor(routeNo))
    PanelHeader("Xe $plate", "Tuyến $routeNo → $dest", onClose, onBack = onBack, lead = { RouteBadge(routeNo, big = true) })
    val elapsed = if (at > 0) ((now - at) / 1000).toInt().coerceAtLeast(0) else 0
    val tb = remember(geo, arrival, stop?.id) { arrival?.let { tripBuses(geo, stop?.id ?: -1, listOf(it)).firstOrNull() } }
    // ---- the tom tat
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (stop != null) "Tới trạm ${stop.name}" else "Tới trạm của bạn", color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                arrival == null -> Text(if (gone) "Xe đã qua trạm / mất tín hiệu" else "Đang lấy vị trí xe…", color = Ui.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                else -> {
                    val eta = (arrival.etaSec - elapsed).coerceAtLeast(0)
                    EtaBig(etaText(eta), if (eta < 300) Color(0xFF059669) else Ui.Ink, 24)
                    Text(
                        listOfNotNull(
                            "còn ${distText(arrival.distM.toDouble())}",
                            tb?.stopsAway?.let { if (it <= 0) "trạm kế tiếp" else "$it trạm nữa" },
                            "dự kiến ${clockText(now + eta * 1000L)}",
                        ).joinToString(" · "),
                        color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (gone) Text("đã qua", color = Ui.Faint, fontSize = 12.sp)
        else PulseDot(Color(0xFF10B981), Modifier.size(12.dp), 8.dp)
    }
    if (geo == null) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text("  Đang tải lộ trình…", color = Ui.Sub, fontSize = 12.sp)
        }
        return
    }
    val n = geo.stops.size
    val target = stop?.let { s -> geo.stops.indexOfFirst { it.id == s.id } } ?: -1
    val alight = alightId?.let { a -> geo.stops.indexOfFirst { it.id == a } } ?: -1
    val v = tb?.veh
    val from = v?.nextStop ?: target.coerceAtLeast(0)
    val shown = remember(geo, from, allStops, target, alight) {
        if (allStops) (from.coerceIn(0, n - 1) until n).toList()
        else keyStopIdx(geo, from, *listOf(target, alight).filter { it >= 0 }.toIntArray())
    }
    val speed = arrival?.let { tripSpeed(it) } ?: 5.0
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("CÁC TRẠM XE SẼ ĐI QUA", color = Ui.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Ui.Soft).clickable(onClick = onToggleAll)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(if (allStops) "Chỉ trạm chính" else "Tất cả ${n - from.coerceIn(0, n - 1)} trạm", color = Ui.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    LazyColumn(Modifier.padding(bottom = 8.dp)) {
        if (from > 0) item {
            Text("Đã qua $from trạm", color = Ui.Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 46.dp, top = 6.dp, bottom = 2.dp))
        }
        itemsIndexed(shown, key = { _, k -> k }) { pos, k ->
            val st = geo.stops[k]
            val prev = if (pos == 0) from - 1 else shown[pos - 1]
            val skipped = (k - prev - 1).coerceAtLeast(0)
            val isTarget = k == target
            val isAlight = k == alight
            val isLast = k == n - 1
            val secs = when {
                v == null || arrival == null -> null
                isTarget -> (arrival.etaSec - elapsed).toDouble()
                else -> (geo.stopPos[k] - v.pos) / speed - elapsed
            }?.coerceAtLeast(0.0)
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    .background(if (isTarget || isAlight) color.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable { onStopTap(st) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.width(3.dp).fillMaxHeight()
                            .padding(top = if (pos == 0) 20.dp else 0.dp, bottom = if (pos == shown.size - 1) 20.dp else 0.dp)
                            .background(color.copy(alpha = 0.55f)),
                    )
                    val big = isTarget || isAlight || isLast
                    Box(
                        Modifier.size(if (big) 14.dp else 10.dp).clip(CircleShape)
                            .background(if (big) color else Ui.Card)
                            .border(2.dp, color, CircleShape),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
                    Text(st.name, color = Ui.Ink, fontSize = 14.sp, fontWeight = if (isTarget || isAlight) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val tags = listOfNotNull(
                        if (isTarget) "Trạm của bạn" else null,
                        if (isAlight) "Xuống ở đây" else null,
                        if (isLast) "Bến cuối" else null,
                        if (!allStops && skipped > 0) "qua $skipped trạm nhỏ" else null,
                        st.routes.size.takeIf { it >= 4 && !isTarget }?.let { "$it tuyến" },
                    )
                    if (tags.isNotEmpty()) Text(tags.joinToString(" · "), color = if (isTarget || isAlight) color else Ui.Sub, fontSize = 11.sp,
                        fontWeight = if (isTarget || isAlight) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (secs != null) Column(horizontalAlignment = Alignment.End) {
                    Text(clockText(now + (secs * 1000).toLong()), color = Ui.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(etaText(secs.toInt()), color = Ui.Faint, fontSize = 11.sp)
                }
            }
        }
        item {
            Text("Giờ ở các trạm là ước lượng theo tốc độ hiện tại của xe.", color = Ui.Faint, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

// ==================================================================== dia diem (tim kiem / nhan giu)

@Composable
internal fun PlacePanel(
    p: Place, me: Pair<Double, Double>?, plan: Live<List<TripPlan>>,
    onTrip: (TripPlan) -> Unit, onStop: (Stop) -> Unit, onLocate: () -> Unit, onClose: () -> Unit,
) {
    PanelHeader(p.name, listOf(p.kind, p.addr).filter { it.isNotBlank() }.joinToString(" · "), onClose, lead = {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Ui.BrandSoft), contentAlignment = Alignment.Center) {
            Icon(Ic.Place, null, tint = Ui.Brand, modifier = Modifier.size(20.dp))
        }
    })
    val near = remember(p.lat, p.lng) { BusData.nearest(p.lat, p.lng, 8, 800.0) }
    val dist = me?.let { BusData.distM(it.first, it.second, p.lat, p.lng) }
    LazyColumn(Modifier.padding(bottom = 8.dp)) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Directions, null, tint = Ui.Brand, modifier = Modifier.size(16.dp))
                Text(
                    if (dist != null) "  Từ vị trí của bạn · cách ${distText(dist)}" else "  Từ Học viện (chưa có vị trí của bạn)",
                    color = Ui.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false),
                )
                if (me == null) Text("  Dùng vị trí", color = Ui.Brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLocate).padding(4.dp))
            }
        }
        // v1.9: tram gan diem xuat phat nhat (vi tri cua ban / Hoc vien)
        if (dist == null || dist >= 450) item {
            val o = me ?: (PTIT_CENTER.latitude to PTIT_CENTER.longitude)
            val ns = remember(o) { BusData.nearest(o.first, o.second, 1, 2000.0).firstOrNull() }
            if (ns != null) NearestStopCard(ns.first, ns.second, fromMe = me != null) { onStop(ns.first) }
        }
        item { ListHeader("Đi tới đây bằng xe buýt") }
        val trips = plan.data
        when {
            dist != null && dist < 450 -> item {
                Text("Điểm này rất gần, đi bộ khoảng ${maxOf(1L, Math.round(dist / 70.0))} phút.", color = Ui.Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            plan.loading && trips == null -> item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("  Đang tìm tuyến…", color = Ui.Sub, fontSize = 12.sp)
                }
            }
            plan.error != null && trips == null -> item {
                Text(plan.error, color = Color(0xFFDC2626), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            trips != null && trips.isEmpty() && plan.loading -> item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("  Không có tuyến đi thẳng, đang tìm cách đổi tuyến…", color = Ui.Sub, fontSize = 12.sp)
                }
            }
            trips != null && trips.isEmpty() -> item {
                Text("Không tìm được cách đi (tối đa 1 lần đổi tuyến, đi bộ tối đa 1,5 km mỗi đầu). Xem các trạm gần điểm này ở dưới.",
                    color = Ui.Sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            trips != null -> {
                items(trips.take(6), key = { it.key }) { tp ->
                    if (tp.second == null) DirectRow(tp, onTrip) else TransferRow(tp, onTrip)
                }
                if (plan.loading) item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text("  Đang tìm thêm cách đổi tuyến…", color = Ui.Faint, fontSize = 11.sp)
                    }
                }
            }
        }
        item { ListHeader("Trạm gần điểm này") }
        if (near.isEmpty()) item {
            Text("Không có trạm nào trong 800 m", color = Ui.Sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        items(near, key = { "n${it.first.id}" }) { (s, d) -> StopRow(s, d, null) { onStop(s) } }
    }
}

/** 1 dong cach di doi tuyen: 2 huy hieu tuyen, 3 dong chu (len / doi / xuong). Bam tung chang de xem xe. */
@Composable
private fun TransferRow(tp: TripPlan, onTrip: (TripPlan) -> Unit) {
    val a = tp.first
    val b = tp.second ?: return
    Column(Modifier.fillMaxWidth().clickable { onTrip(tp) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteBadge(a.routeNo)
            Icon(Ic.Right, null, tint = Ui.Faint, modifier = Modifier.padding(horizontal = 2.dp).size(14.dp))
            RouteBadge(b.routeNo)
            Text("  1 lần đổi", color = Ui.Faint, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("${tp.minutes}'", color = Ui.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Walk, null, tint = Ui.Sub, modifier = Modifier.size(13.dp))
            Text(" ${distText(a.walkTo)} · lên ${a.routeNo} ở ${a.board.name}", color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Bus, null, tint = Ui.Brand, modifier = Modifier.size(13.dp))
            val walk = if (tp.xferWalk >= 20) " (đi bộ ${distText(tp.xferWalk)})" else ""
            Text(
                " ${a.stops} trạm · đổi ${b.routeNo}$walk ở ${a.alight.name}",
                color = Ui.Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Bus, null, tint = Ui.Sub, modifier = Modifier.size(13.dp))
            Text(" ${b.stops} trạm · xuống ${b.alight.name} · đi bộ ${distText(b.walkFrom)}", color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Đã cộng ~5' chờ đổi xe, chưa tính chờ xe đầu", color = Ui.Faint, fontSize = 10.sp)
    }
}

@Composable
private fun DirectRow(tp: TripPlan, onTrip: (TripPlan) -> Unit) {
                val t = tp.first
                Row(
                    Modifier.fillMaxWidth().clickable { onTrip(tp) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(54.dp), contentAlignment = Alignment.CenterStart) { RouteBadge(t.routeNo, big = true) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("→ ${t.dest}", color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.Walk, null, tint = Ui.Sub, modifier = Modifier.size(13.dp))
                            Text(" ${distText(t.walkTo)} · lên ${t.board.name}", color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.Bus, null, tint = Ui.Sub, modifier = Modifier.size(13.dp))
                            Text(" ${t.stops} trạm · xuống ${t.alight.name} · đi bộ ${distText(t.walkFrom)}", color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                        Text("${t.minutes}'", color = Ui.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("chưa tính chờ", color = Ui.Faint, fontSize = 10.sp)
                    }
                }
}

@Composable
internal fun PlaceRow(p: Place, dist: Double?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Ui.BrandSoft), contentAlignment = Alignment.Center) {
            Icon(Ic.Place, null, tint = Ui.Brand, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(p.kind, p.addr).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (dist != null) Text(distText(dist), color = Ui.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

// ==================================================================== v1.9: tram gan nhat + lich trinh kieu Google Maps

@Composable
private fun NearestStopCard(s: Stop, d: Double, fromMe: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).border(1.dp, Ui.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Ui.Soft), contentAlignment = Alignment.Center) {
            Icon(Ic.Walk, null, tint = Ui.Ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (fromMe) "Trạm gần bạn nhất" else "Trạm gần Học viện nhất", color = Ui.Sub, fontSize = 11.sp)
            Text(s.name, color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${distText(d)} · đi bộ ~${walkMin(d)} phút" + (if (s.routes.isNotEmpty()) " · ${s.routes.take(5).joinToString(", ")}" else ""),
                color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Ic.Right, null, tint = Ui.Faint, modifier = Modifier.size(18.dp))
    }
}

internal fun walkMin(m: Double): Int = maxOf(1, Math.round(Itinerary.walkSec(m) / 60.0).toInt())
private fun minText(sec: Double): String = "${maxOf(1, Math.round(sec / 60.0).toInt())} phút"

private enum class Rail { NONE, WALK, RIDE }

/** Cot trai cua dong lich trinh: thanh noi tren/duoi (cham = di bo, dam = di xe) + nut o giua. */
@Composable
private fun TimeRow(
    time: String?, top: Rail, bottom: Rail, topColor: Color, bottomColor: Color,
    node: (@Composable () -> Unit)?, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val walkColor = Ui.Faint
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) {
            if (time != null) Text(time, color = Ui.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Box(Modifier.width(34.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                fun rail(r: Rail, c: Color, y0: Float, y1: Float) {
                    when (r) {
                        Rail.RIDE -> drawLine(c, Offset(x, y0), Offset(x, y1), strokeWidth = 6.dp.toPx())
                        Rail.WALK -> drawLine(
                            walkColor, Offset(x, y0 + 3.dp.toPx()), Offset(x, y1), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 8.dp.toPx())),
                        )
                        Rail.NONE -> {}
                    }
                }
                rail(top, topColor, 0f, size.height / 2)
                rail(bottom, bottomColor, size.height / 2, size.height)
            }
            node?.invoke()
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = if (node != null) 8.dp else 10.dp), content = content)
    }
}

@Composable
private fun StopNode(color: Color, big: Boolean = true) {
    Box(
        Modifier.size(if (big) 16.dp else 9.dp).clip(CircleShape).background(Ui.Card).border(if (big) 3.dp else 2.dp, color, CircleShape),
    )
}

/**
 * Lich trinh tung buoc cua 1 cach di: di bo toi tram -> len xe (gio xe song EBMS) -> cac tram
 * di qua -> xuong / doi xe -> di bo toi noi. Gio o cot trai la gio du kien theo dong ho may.
 */
@Composable
internal fun TripPanel(
    tp: TripPlan, place: Place, originName: String,
    geos: List<VarGeo?>, live: List<Live<List<Arrival>>>, now: Long,
    onLeg: (DirectTrip) -> Unit, onStopTap: (Stop) -> Unit, onBack: () -> Unit, onClose: () -> Unit,
) {
    // ETA song da tru thoi gian tu luc lay -> tinh tu "bay gio"
    val adj = live.map { l ->
        val el = if (l.at > 0) ((now - l.at) / 1000).toInt().coerceAtLeast(0) else 0
        l.data?.map { it.copy(etaSec = (it.etaSec - el).coerceAtLeast(0)) }
    }
    val iti = Itinerary.build(tp, adj)
    val legs = iti.legs
    fun clock(sec: Double) = clockText(now + (sec * 1000).toLong())
    PanelHeader(
        "${minText(iti.total)} · đến ${clock(iti.total)}",
        "Từ $originName tới ${place.name}",
        onClose, onBack = onBack,
        lead = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                legs.forEachIndexed { i, l ->
                    if (i > 0) Icon(Ic.Right, null, tint = Ui.Faint, modifier = Modifier.size(14.dp))
                    RouteBadge(l.leg.routeNo)
                }
            }
        },
    )
    val walkTotal = tp.first.walkTo + (if (tp.second != null) tp.xferWalk else 0.0) + legs.last().leg.walkFrom
    Text(
        listOfNotNull(
            if (tp.second == null) "Đi thẳng, không đổi xe" else "1 lần đổi xe",
            "đi bộ tổng ${distText(walkTotal)}",
            if (iti.guessed) "chưa có giờ xe sống cho mọi chặng" else "đã tính giờ chờ xe",
        ).joinToString(" · "),
        color = Ui.Sub, fontSize = 12.sp, maxLines = 2,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    var open by androidx.compose.runtime.remember(tp.key) { mutableStateOf(setOf<Int>()) }
    LazyColumn(Modifier.padding(bottom = 8.dp)) {
        // ---- xuat phat
        item(key = "o") {
            TimeRow(clock(0.0), Rail.NONE, Rail.WALK, Color.Transparent, Color.Transparent, { StopNode(Ui.Ink) }) {
                Text(originName, color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        item(key = "w0") {
            TimeRow(null, Rail.WALK, Rail.WALK, Color.Transparent, Color.Transparent, null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Walk, null, tint = Ui.Sub, modifier = Modifier.size(15.dp))
                    Text(
                        if (tp.first.walkTo < 25) " Trạm ở ngay đây" else " Đi bộ ${distText(tp.first.walkTo)} · ~${minText(iti.walkStart)}",
                        color = Ui.Sub, fontSize = 13.sp,
                    )
                }
            }
        }
        legs.forEachIndexed { i, l ->
            val leg = l.leg
            val color = Color(routeColor(leg.routeNo))
            val merged = i == 1 && tp.xferWalk < 20  // doi xe ngay tai tram vua xuong
            // ---- tram len
            if (!merged) item(key = "b$i") {
                TimeRow(
                    clock(l.reach), Rail.WALK, Rail.RIDE, Color.Transparent, color, { StopNode(color) },
                    onClick = { onStopTap(leg.board) },
                ) {
                    Text(leg.board.name, color = Ui.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (i == 0) "Lên xe ${leg.routeNo} tại đây" else "Đổi sang xe ${leg.routeNo} tại đây", color = Ui.Sub, fontSize = 12.sp)
                }
            }
            // ---- chang xe
            item(key = "r$i") {
                TimeRow(null, Rail.RIDE, Rail.RIDE, color, color, null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RouteBadge(leg.routeNo)
                        Text("  → ${leg.dest}", color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(4.dp))
                    val lv = live.getOrNull(i)
                    val bus = l.bus
                    when {
                        bus != null -> {
                            val wait = l.waitSec
                            Text(
                                "Xe ${bus.plate} tới trạm sau ${etaText(bus.etaSec)} (lúc ${clock(bus.etaSec.toDouble())})" +
                                    if (wait >= 60) " · chờ ~${minText(wait)}" else " · vừa kịp",
                                color = Color(0xFF059669), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        lv == null || (lv.loading && lv.data == null) -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = color, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Text("  Đang lấy giờ xe tới…", color = Ui.Sub, fontSize = 12.sp)
                        }
                        lv.error != null && lv.data == null -> Text("Không lấy được giờ xe (lỗi mạng)", color = Color(0xFFDC2626), fontSize = 12.sp)
                        else -> Text(
                            if (i == 0) "Chưa thấy xe nào kịp đón (EBMS chỉ báo xe trong ~1 giờ tới) – chưa tính giờ chờ"
                            else "Chưa có giờ xe – tạm tính chờ 5 phút",
                            color = Ui.Sub, fontSize = 12.sp,
                        )
                    }
                    Text(
                        "Đi ${leg.stops} trạm · ${distText(leg.rideM)} · ~${minText(l.ride)}",
                        color = Ui.Sub, fontSize = 12.sp,
                    )
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(if (i in open) "Ẩn các trạm" else "Xem ${leg.stops} trạm", Ui.Ink, Ui.Soft) {
                            open = if (i in open) open - i else open + i
                        }
                        Chip("Xe đang tới", Color.White, color) { onLeg(leg) }
                    }
                }
            }
            // ---- cac tram giua (mo rong)
            val g = geos.getOrNull(i)
            val sp = g?.let { Itinerary.span(it, leg) }
            if (i in open) {
                if (g == null || sp == null) item(key = "m$i-load") {
                    TimeRow(null, Rail.RIDE, Rail.RIDE, color, color, null) {
                        Text(if (g == null) "Đang tải lộ trình…" else "Không khớp lộ trình", color = Ui.Faint, fontSize = 12.sp)
                    }
                } else {
                    val (a, b) = sp
                    val span = (g.stopPos[b] - g.stopPos[a]).coerceAtLeast(1.0)
                    items((a + 1 until b).toList(), key = { k -> "m$i-$k" }) { k ->
                        val frac = (g.stopPos[k] - g.stopPos[a]) / span
                        TimeRow(
                            clock(l.depart + l.ride * frac), Rail.RIDE, Rail.RIDE, color, color, { StopNode(color, big = false) },
                            onClick = { onStopTap(g.stops[k]) },
                        ) {
                            Text(g.stops[k].name, color = Ui.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            // ---- tram xuong
            val lastLeg = i == legs.lastIndex
            val nextMerged = !lastLeg && tp.xferWalk < 20
            val nextColor = if (!lastLeg) Color(routeColor(legs[i + 1].leg.routeNo)) else Color.Transparent
            item(key = "a$i") {
                TimeRow(
                    clock(l.alight), Rail.RIDE, if (nextMerged) Rail.RIDE else Rail.WALK, color, nextColor, { StopNode(color) },
                    onClick = { onStopTap(leg.alight) },
                ) {
                    Text(leg.alight.name, color = Ui.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            lastLeg -> "Xuống xe"
                            nextMerged -> "Xuống xe, đổi sang xe ${legs[i + 1].leg.routeNo} tại trạm này"
                            else -> "Xuống xe, đi bộ sang trạm khác để đổi xe"
                        },
                        color = Ui.Sub, fontSize = 12.sp,
                    )
                }
            }
            if (!lastLeg && !nextMerged) item(key = "x$i") {
                TimeRow(null, Rail.WALK, Rail.WALK, Color.Transparent, Color.Transparent, null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.Walk, null, tint = Ui.Sub, modifier = Modifier.size(15.dp))
                        Text(" Đi bộ ${distText(tp.xferWalk)} · ~${minText(iti.walkXfer)} sang ${legs[i + 1].leg.board.name}",
                            color = Ui.Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // ---- di bo toi noi
        val wEnd = legs.last().leg.walkFrom
        item(key = "we") {
            TimeRow(null, Rail.WALK, Rail.WALK, Color.Transparent, Color.Transparent, null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Walk, null, tint = Ui.Sub, modifier = Modifier.size(15.dp))
                    Text(if (wEnd < 25) " Nơi đến ở ngay trạm" else " Đi bộ ${distText(wEnd)} · ~${minText(iti.walkEnd)}", color = Ui.Sub, fontSize = 13.sp)
                }
            }
        }
        item(key = "d") {
            TimeRow(clock(iti.total), Rail.WALK, Rail.NONE, Color.Transparent, Color.Transparent, {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Ui.Brand), contentAlignment = Alignment.Center) {
                    Icon(Ic.Place, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }) {
                Text(place.name, color = Ui.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (place.addr.isNotBlank()) Text(place.addr, color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item(key = "note") {
            Text(
                "Giờ xe tới lấy trực tiếp từ EBMS, tự cập nhật 20 giây/lần. Thời gian đi xe ước lượng ~16 km/h, đi bộ ~4 km/h theo đường thẳng nên thực tế có thể lâu hơn chút.",
                color = Ui.Faint, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Chip(text: String, fg: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}
