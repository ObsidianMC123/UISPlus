package vn.uisplus.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import vn.uisplus.data.ChangeEvent
import vn.uisplus.data.ClassSession
import vn.uisplus.data.dowLong
import vn.uisplus.data.hm
import vn.uisplus.ui.theme.Ui
import vn.uisplus.ui.theme.subjectColor
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    val v by produceState(System.currentTimeMillis(), periodMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(periodMs - (value % periodMs))
        }
    }
    return v
}

fun relTime(at: Long, now: Long = System.currentTimeMillis()): String {
    if (at <= 0) return "chưa có"
    val m = (now - at) / 60_000
    return when {
        m < 1 -> "vừa xong"
        m < 60 -> "$m phút trước"
        m < 24 * 60 -> "${m / 60} giờ trước"
        else -> SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(at))
    }
}

@Composable
fun SectionTitle(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), color = Ui.Ink, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelLarge,
                color = if (onTrailing != null) Ui.Brand else Ui.Sub,
                modifier = if (onTrailing != null) Modifier.clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onTrailing).padding(6.dp) else Modifier,
            )
        }
    }
}

@Composable
fun Pill(icon: ImageVector?, text: String, color: Color = Ui.Sub, bg: Color = Ui.Soft) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SessionCard(
    s: ClassSession,
    modifier: Modifier = Modifier,
    faded: Boolean = false,
    live: Boolean = false,
    onClick: () -> Unit,
) {
    val c = subjectColor(s.maMon)
    Row(
        modifier
            .alpha(if (faded) 0.55f else 1f)
            .bounceClick(onClick = onClick)
            .elev(if (live) 8.dp else 1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card)
            .drawBehind {
                if (live) drawRect(c.copy(alpha = if (Ui.isDark) 0.10f else 0.06f))
                drawRect(c, size = Size(4.dp.toPx(), size.height))
            }
            .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    s.tenMon,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (s.nghiDay) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = Ui.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (s.nghiDay) {
                    Spacer(Modifier.width(6.dp)); Pill(null, "Nghỉ", Ui.Brand, Ui.Brand.copy(alpha = 0.1f))
                }
                if (s.dayBu) {
                    Spacer(Modifier.width(6.dp)); Pill(null, "Dạy bù", Ui.Green, Ui.Green.copy(alpha = 0.12f))
                }
                if (live) {
                    Spacer(Modifier.width(6.dp)); Pill(null, "Đang học", c, c.copy(alpha = 0.12f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(Ic.Place, s.phong.ifBlank { "Chưa có phòng" }, c, c.copy(alpha = 0.1f))
                Pill(Ic.Clock, s.tietText)
            }
            if (s.giangVien.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("GV ${s.giangVien}", style = MaterialTheme.typography.bodySmall, color = Ui.Sub, maxLines = 1)
            }
        }
    }
}

/** Dong timeline: gio | duong ke + cham | the buoi hoc. */
@Composable
fun TimelineItem(s: ClassSession, now: Long, isLast: Boolean, onOpen: (ClassSession) -> Unit) {
    val live = now in s.startMillis until s.endMillis && !s.nghiDay
    val past = now >= s.endMillis
    val c = subjectColor(s.maMon)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(50.dp).padding(top = 12.dp)) {
            Text(hm(s.startMin), style = MaterialTheme.typography.titleSmall, color = if (past) Ui.Faint else Ui.Ink)
            Text(hm(s.endMin), style = MaterialTheme.typography.bodySmall, color = Ui.Faint)
        }
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.fillMaxHeight().width(2.dp)) {
                if (!isLast) drawRoundRect(Ui.Line, topLeft = Offset(0f, 26.dp.toPx()), size = Size(size.width, size.height), cornerRadius = CornerRadius(2f))
            }
            if (live) {
                PulseDot(c, Modifier.padding(top = 16.dp).size(12.dp), dotSize = 12.dp)
            } else {
                Canvas(Modifier.padding(top = 16.dp).size(12.dp)) {
                    if (past) drawCircle(Ui.Faint.copy(alpha = 0.6f))
                    else {
                        drawCircle(Ui.Card)
                        drawCircle(c, style = Stroke(3.dp.toPx()))
                    }
                }
            }
        }
        SessionCard(s, Modifier.weight(1f).padding(bottom = 12.dp), faded = past, live = live) { onOpen(s) }
    }
}

@Composable
fun EmptyCard(title: String, sub: String, @Suppress("UNUSED_PARAMETER") emoji: String = "", icon: ImageVector = Ic.Calendar) {
    Column(
        Modifier
            .fillMaxWidth()
            .elev(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card)
            .padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Ui.Soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ui.Sub, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ui.Ink)
        Spacer(Modifier.height(2.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Ui.Sub, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

fun changeStyle(type: String): Pair<ImageVector, Color> = when (type) {
    ChangeEvent.ROOM -> Ic.Place to Ui.Sky
    ChangeEvent.TIME -> Ic.Clock to Ui.Amber
    ChangeEvent.CANCEL -> Ic.Cancel to Ui.Brand
    ChangeEvent.REMOVED -> Ic.Cancel to Ui.Brand
    ChangeEvent.MAKEUP -> Ic.Add to Ui.Green
    ChangeEvent.ADDED -> Ic.Add to Ui.Green
    else -> Ic.Sparkle to Ui.Violet
}

@Composable
fun ChangeItem(e: ChangeEvent) {
    val (icon, color) = changeStyle(e.type)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .elev(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall, color = Ui.Ink, maxLines = 2)
            Text(e.detail, style = MaterialTheme.typography.bodySmall, color = Ui.Sub)
            Text(relTime(e.at), style = MaterialTheme.typography.labelSmall, color = Ui.Faint)
        }
    }
}

private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSheet(s: ClassSession, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = subjectColor(s.maMon)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ui.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Ui.Line) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            // v1.4: dau sheet phang — thanh mau mon + chu dam, khong gradient
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(s.maMon, style = MaterialTheme.typography.labelLarge, color = Ui.Sub)
                    Text(s.tenMon, style = MaterialTheme.typography.headlineSmall, color = Ui.Ink)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(Ic.Clock, s.timeText, Ui.Ink, Ui.Soft)
                        Pill(Ic.Place, s.phong.ifBlank { "?" }, c, c.copy(alpha = 0.12f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            InfoRow(Ic.Calendar, "Ngày học", "${dowLong(s.date)}, ${s.date.format(DMY)} · tuần ${s.tuan}")
            InfoRow(Ic.Clock, "Thời gian", "${s.timeText} · ${s.tietText} (${s.soTiet} tiết)")
            InfoRow(Ic.Place, "Phòng", s.phong.ifBlank { "Chưa xếp" } + if (s.coSo.isNotBlank()) " · cơ sở ${s.coSo}" else "")
            if (s.giangVien.isNotBlank()) InfoRow(Ic.Teacher, "Giảng viên", s.giangVien)
            InfoRow(
                Ic.Group, "Nhóm · Lớp",
                listOfNotNull(
                    s.nhom.takeIf { it.isNotBlank() }?.let { "Nhóm $it" },
                    s.toTh.takeIf { it.isNotBlank() }?.let { "Tổ $it" },
                    s.lop.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "—" },
            )
            if (s.soTinChi.isNotBlank()) InfoRow(Ic.Sparkle, "Tín chỉ", s.soTinChi)
            if (s.nghiDay || s.dayBu) {
                InfoRow(Ic.Bell, "Trạng thái", if (s.nghiDay) "Buổi này NGHỈ DẠY" else "Buổi DẠY BÙ")
            }
            Spacer(Modifier.height(14.dp))
            GradientButton("Thêm vào Google Calendar") { addToCalendar(ctx, s) }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ui.Soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ui.Sub, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ui.Faint)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ui.Ink)
        }
    }
}

fun addToCalendar(ctx: Context, s: ClassSession) {
    val i = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, s.startMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, s.endMillis)
        .putExtra(CalendarContract.Events.TITLE, s.tenMon)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, s.phong)
        .putExtra(CalendarContract.Events.DESCRIPTION, "${s.maMon} · ${s.tietText}" + if (s.giangVien.isNotBlank()) " · GV ${s.giangVien}" else "")
    try {
        ctx.startActivity(i)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "Máy chưa có app lịch", Toast.LENGTH_SHORT).show()
    }
}
