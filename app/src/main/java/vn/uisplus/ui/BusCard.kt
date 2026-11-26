package vn.uisplus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.uisplus.bus.Arrival
import vn.uisplus.bus.BusAlarm
import vn.uisplus.bus.BusConfig
import vn.uisplus.bus.BusLine
import vn.uisplus.bus.BusNotify
import vn.uisplus.bus.BusService
import vn.uisplus.bus.BusTracker
import vn.uisplus.data.AppData
import vn.uisplus.ui.theme.Ui

private const val BUS = "\uD83D\uDE8C"

/** The "Xe buyt ve" o man Hom nay: dem nguoc xe 74/162... toi tram HV Buu chinh, bao xe vua qua. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusCard(d: AppData, now: Long) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { BusTracker.init(ctx); BusAlarm.schedule(ctx); BusTracker.ensureServed() }
    val st by BusTracker.state.collectAsStateWithLifecycle()
    val cfg by BusTracker.config.collectAsStateWithLifecycle()
    var showCfg by rememberSaveable { mutableStateOf(false) }
    val home = remember(d, cfg, now / 60_000) { BusAlarm.nextHome(d, now, cfg) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun startNow() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        runCatching { BusAlarm.startNow(ctx) }.onFailure {
            Toast.makeText(ctx, "Không bật được theo dõi: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
    fun save(c: BusConfig) { BusTracker.saveConfig(ctx, c); BusAlarm.schedule(ctx) }

    val accent by animateColorAsState(if (st.active) Ui.Brand else Ui.Line, tween(500), label = "busAccent")
    Column(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .elev(if (st.active) 16.dp else 5.dp, RoundedCornerShape(16.dp), spotColor = accent.copy(alpha = 0.45f), ambientColor = accent.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(Ui.Soft),
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(Ic.Bus, null, tint = if (st.active) Ui.Brand else Ui.Ink, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Xe buýt về", style = MaterialTheme.typography.titleMedium, color = Ui.Ink, fontWeight = FontWeight.Bold)
                val sub = when {
                    st.active -> "Đang theo dõi · " + (if (st.updatedAt > 0) "cập nhật ${((now - st.updatedAt) / 1000).coerceAtLeast(0)}s trước" else "đang lấy GPS…")
                    !cfg.auto -> "Tự theo dõi đang tắt"
                    home != null -> "Tự bật lúc ${BusNotify.hhmm(home.first)} · tan học ${BusNotify.hhmm(home.second)}"
                    else -> "Chưa có lịch về sắp tới"
                }
                Text(sub, style = MaterialTheme.typography.labelMedium, color = Ui.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (st.active) LiveDot()
            Spacer(Modifier.width(6.dp))
            Text(
                if (showCfg) "Xong" else "Tùy chỉnh",
                style = MaterialTheme.typography.labelLarge,
                color = Ui.Brand,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).bounceClick { showCfg = !showCfg }.padding(6.dp),
            )
        }

        Text(
            "Trạm HV Bưu chính (97 Man Thiện) · tuyến ${cfg.routes.joinToString(", ").ifBlank { "tất cả" }}",
            style = MaterialTheme.typography.labelMedium, color = Ui.Faint,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (st.error != null && st.active) {
            Text(st.error!!, style = MaterialTheme.typography.labelMedium, color = Ui.Brand, modifier = Modifier.padding(top = 6.dp))
        }

        if (st.active || st.lines.isNotEmpty()) {
            val withBus = st.lines.filter { it.arrivals.isNotEmpty() }
            if (withBus.isEmpty() && st.updatedAt > 0) {
                Text(
                    "Chưa thấy xe ${cfg.routes.joinToString(", ")} nào đang chạy tới trạm.",
                    style = MaterialTheme.typography.bodyMedium, color = Ui.Sub, modifier = Modifier.padding(top = 10.dp),
                )
            }
            withBus.forEach { l -> LineRow(l, now, st.updatedAt, cfg.nearMin) }
        }

        if (st.passed.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                st.passed.take(4).forEach { p ->
                    Pill(null, "${p.routeNo} → ${p.dest} đã qua ${BusNotify.hhmm(p.at)}", Ui.Sub, Ui.Soft)
                }
            }
        }

        if (st.notServed.isNotEmpty()) {
            val others = st.servedRoutes.filter { it !in cfg.routes }
            Text(
                "Tuyến ${st.notServed.joinToString(", ")} không đi qua trạm này (theo dữ liệu EBMS)." +
                    if (others.isNotEmpty()) " Trạm này còn có: ${others.joinToString(", ")}." else "",
                style = MaterialTheme.typography.labelMedium, color = Ui.Amber, modifier = Modifier.padding(top = 10.dp),
            )
        }

        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (st.active) {
                BigButton("Dừng theo dõi", Ui.Soft, Ui.Ink, Modifier.weight(1f)) { BusService.stop(ctx) }
            } else {
                BigButton("Theo dõi ngay", Ui.BrandSoft, Ui.Brand, Modifier.weight(1f)) { startNow() }
            }
        }

        AnimatedVisibility(showCfg, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 14.dp)) {
                CfgLabel("Tuyến muốn theo dõi")
                val options = (cfg.routes + st.servedRoutes + BusTracker.DEFAULT_ROUTES).distinct()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { r ->
                        val on = r in cfg.routes
                        Chip(r, on) { save(cfg.copy(routes = if (on) cfg.routes - r else cfg.routes + r)) }
                    }
                }
                CfgLabel("Chiều xe")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Cả 2 chiều", cfg.dir == 0) { save(cfg.copy(dir = 0)) }
                    Chip("→ Bình Thái / Miền Đông", cfg.dir == 1) { save(cfg.copy(dir = 1)) }
                    Chip("→ Thủ Đức / Long Phước", cfg.dir == 2) { save(cfg.copy(dir = 2)) }
                }
                CfgLabel("Bắt đầu trước giờ tan học")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 15, 20, 30).forEach { m -> Chip("$m phút", cfg.leadMin == m) { save(cfg.copy(leadMin = m)) } }
                }
                CfgLabel("Theo dõi thêm sau giờ tan học")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20, 40, 60).forEach { m -> Chip("$m phút", cfg.afterMin == m) { save(cfg.copy(afterMin = m)) } }
                }
                CfgLabel("Báo khi xe còn")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 7, 10).forEach { m -> Chip("$m phút", cfg.nearMin == m) { save(cfg.copy(nearMin = m)) } }
                }
                CfgLabel("Tự bật theo thời khóa biểu")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Bật", cfg.auto) { save(cfg.copy(auto = true)) }
                    Chip("Tắt", !cfg.auto) { save(cfg.copy(auto = false)) }
                }
            }
        }
    }
}

@Composable
private fun LineRow(l: BusLine, now: Long, updatedAt: Long, nearMin: Int) {
    val elapsed = if (updatedAt > 0) ((now - updatedAt) / 1000).toInt().coerceAtLeast(0) else 0
    val first: Arrival = l.arrivals.first()
    val eta = (first.etaSec - elapsed).coerceAtLeast(0)
    val color = when {
        eta <= 90 -> Ui.Green
        eta <= nearMin * 60 -> Ui.Amber
        else -> Ui.Sky
    }
    val c by animateColorAsState(color, tween(400), label = "eta")
    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.widthIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) { Text(l.routeNo, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("→ ${l.dest}", style = MaterialTheme.typography.bodyMedium, color = Ui.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val gpsAge = if (first.gpsAt > 0) (now - first.gpsAt) / 60_000 else 0
            val extra = buildString {
                append(BusNotify.distText(first.distM)); append(" · "); append(first.plate)
                if (gpsAge >= 3) append(" · GPS cũ ${gpsAge}'")
                l.arrivals.getOrNull(1)?.let { append(" · sau: ${BusNotify.etaText((it.etaSec - elapsed).coerceAtLeast(0))}") }
            }
            Text(extra, style = MaterialTheme.typography.labelMedium, color = Ui.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (eta <= 60) {
                val t = rememberInfiniteTransition(label = "go")
                val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "goA")
                Text("Đang tới", color = c, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.graphicsLayer { alpha = a })
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(eta + 30) / 60}", color = c, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    Text(" phút", color = c, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveDot() {
    val t = rememberInfiniteTransition(label = "live")
    val s by t.animateFloat(0.6f, 1.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "liveS")
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(16.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = 1.3f - s }.clip(CircleShape).background(Ui.Green.copy(alpha = 0.5f)))
        Box(Modifier.size(8.dp).clip(CircleShape).background(Ui.Green))
    }
}

@Composable
private fun CfgLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Ui.Sub, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Ui.Sky else Ui.Soft, tween(250), label = "chipBg")
    val fg by animateColorAsState(if (on) Color.White else Ui.Ink, tween(250), label = "chipFg")
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).bounceClick(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun BigButton(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(16.dp)).background(bg).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
}
