package vn.uisplus.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import kotlinx.coroutines.withTimeoutOrNull
import vn.uisplus.MainActivity
import vn.uisplus.R
import vn.uisplus.bus.BusData
import vn.uisplus.bus.Stop
import vn.uisplus.bus.StopArrivals
import vn.uisplus.ui.etaText
import vn.uisplus.ui.routeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fixed(c: Color) = ColorProvider(day = c, night = c)
private val B_INK = ColorProvider(day = Color(0xFF111827), night = Color(0xFFF3F4F6))
private val B_SUB = ColorProvider(day = Color(0xFF6B7280), night = Color(0xFFA1A7B3))
private val B_GREEN = ColorProvider(day = Color(0xFF059669), night = Color(0xFF34D399))

/** Widget xe buyt: ETA cac tram da ghim trong tab Xe buyt (tuyen nao cung duoc). */
class BusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        BusData.init(context)
        val pins = BusData.pinned.value.take(3)
        val rows = pins.mapNotNull { pid ->
            val st = BusData.stop(pid) ?: return@mapNotNull null
            st to withTimeoutOrNull(12_000) { runCatching { BusData.arrivals(pid) }.getOrNull() }
        }
        val at = System.currentTimeMillis()
        provideContent { BusWidgetBody(rows, at) }
    }

    companion object {
        suspend fun refresh(ctx: Context) {
            runCatching { BusWidget().updateAll(ctx) }
        }
    }
}

class BusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BusWidget()
}

class BusWidgetRefresh : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BusWidget().update(context, glanceId)
    }
}

@Composable
internal fun BusWidgetBody(rows: List<Pair<Stop, List<StopArrivals>?>>, at: Long) {
    val size = LocalSize.current
    val ctxIntent = Intent().setClassName("vn.uisplus.app", MainActivity::class.java.name)
        .putExtra("tab", "bus")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    // ~ moi tram: 20dp ten + 22dp/tuyen
    val avail = (size.height.value - 44f).coerceAtLeast(40f)
    val perStop = if (rows.isEmpty()) 0 else ((avail / rows.size - 20f) / 22f).toInt().coerceIn(1, 4)
    val stopsShown = if (perStop <= 1 && rows.size > 1) rows.take(((avail) / 42f).toInt().coerceAtLeast(1)) else rows
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .cornerRadius(22.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(ctxIntent)),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83D\uDE8C Xe buýt", style = TextStyle(color = B_INK, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at)) + "  ⟳",
                style = TextStyle(color = B_SUB, fontSize = 12.sp),
                modifier = GlanceModifier.padding(start = 6.dp).clickable(actionRunCallback<BusWidgetRefresh>()),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        if (rows.isEmpty()) {
            Text("Mở UIS+ → tab Xe buýt → ghim trạm ☆ để hiện ở đây", style = TextStyle(color = B_SUB, fontSize = 12.sp))
        }
        for ((st, list) in stopsShown) {
            Text(st.name, maxLines = 1, style = TextStyle(color = B_INK, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            when {
                list == null -> Text("Không tải được – bấm ⟳", style = TextStyle(color = B_SUB, fontSize = 11.sp))
                list.none { it.arrivals.isNotEmpty() } -> Text("Chưa có xe sắp tới", style = TextStyle(color = B_SUB, fontSize = 11.sp))
                else -> for (sa in list.filter { it.arrivals.isNotEmpty() }.take(perStop)) {
                    val eta = sa.arrivals.first().etaSec
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            GlanceModifier.background(fixed(Color(routeColor(sa.routeNo)))).cornerRadius(7.dp).padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(sa.routeNo, style = TextStyle(color = fixed(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        Text("→ ${sa.dest}", maxLines = 1, style = TextStyle(color = B_SUB, fontSize = 11.sp), modifier = GlanceModifier.defaultWeight())
                        Text(etaText(eta), style = TextStyle(color = if (eta < 300) B_GREEN else B_INK, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(GlanceModifier.height(4.dp))
        }
    }
}
