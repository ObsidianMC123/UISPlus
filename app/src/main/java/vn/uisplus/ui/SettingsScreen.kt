package vn.uisplus.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.uisplus.data.AppData
import vn.uisplus.data.Ics
import vn.uisplus.ui.theme.Ui
import vn.uisplus.ui.theme.Palette
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel, d: AppData) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirmLogout by remember { mutableStateOf(false) }
    val now = rememberNow(30_000)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 14.dp, bottom = 130.dp),
    ) {
        item { Stagger(0) { ProfileCard(d) } }

        item { SectionTitle("Giao diện") }
        item {
            Stagger(1) {
                Card {
                    Spacer(Modifier.height(12.dp))
                    ThemePicker(prefs.themeMode) { m -> vm.updatePrefs { it.copy(themeMode = m) } }
                    AnimatedVisibility(prefs.themeMode == 4, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.padding(top = 14.dp)) {
                            Text("Tối từ", style = MaterialTheme.typography.titleSmall, color = Ui.Ink)
                            Spacer(Modifier.height(8.dp))
                            Segmented(listOf(17, 18, 19, 20, 21), prefs.darkFromH, { "${it}h" }) { h -> vm.updatePrefs { it.copy(darkFromH = h) } }
                            Spacer(Modifier.height(12.dp))
                            Text("Sáng lại lúc", style = MaterialTheme.typography.titleSmall, color = Ui.Ink)
                            Spacer(Modifier.height(8.dp))
                            Segmented(listOf(5, 6, 7, 8), prefs.darkToH, { "${it}h" }) { h -> vm.updatePrefs { it.copy(darkToH = h) } }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        HorizontalDivider(color = Ui.Line)
                        SwitchRow(Ic.Palette, "Màu theo hình nền", "Material You (Android 12+)", prefs.dynamicColor) { on ->
                            vm.updatePrefs { it.copy(dynamicColor = on) }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Nhắc lịch") }
        item {
            Stagger(1) {
                Card {
                    SwitchRow(Ic.Bell, "Nhắc trước giờ học", "Thông báo trước mỗi buổi học", prefs.remindersOn) { on ->
                        vm.updatePrefs { it.copy(remindersOn = on) }
                    }
                    AnimatedVisibility(prefs.remindersOn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        var temp by remember(prefs.reminderMinutes) { mutableFloatStateOf(prefs.reminderMinutes.toFloat()) }
                        Column(Modifier.padding(start = 52.dp, end = 4.dp, bottom = 6.dp)) {
                            Text(
                                "Báo trước ${temp.roundToInt()} phút",
                                style = MaterialTheme.typography.labelLarge, color = Ui.Brand,
                            )
                            Slider(
                                value = temp,
                                onValueChange = { temp = it },
                                valueRange = 5f..60f,
                                steps = 10,
                                onValueChangeFinished = { vm.updatePrefs { it.copy(reminderMinutes = temp.roundToInt()) } },
                                colors = SliderDefaults.colors(thumbColor = Ui.Brand, activeTrackColor = Ui.Brand, inactiveTrackColor = Ui.Line),
                            )
                        }
                    }
                    HorizontalDivider(color = Ui.Line)
                    SwitchRow(Ic.Swap, "Báo khi lịch thay đổi", "Đổi phòng, nghỉ dạy, dạy bù, buổi mới", prefs.changeAlertsOn) { on ->
                        vm.updatePrefs { it.copy(changeAlertsOn = on) }
                    }
                    HorizontalDivider(color = Ui.Line)
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text("Tự đồng bộ nền mỗi", style = MaterialTheme.typography.titleSmall, color = Ui.Ink)
                        Spacer(Modifier.height(10.dp))
                        Segmented(listOf(3, 6, 12), prefs.syncHours, { "$it giờ" }) { h -> vm.updatePrefs { it.copy(syncHours = h) } }
                    }
                }
            }
        }

        item { SectionTitle("Dữ liệu") }
        item {
            Stagger(2) {
                Card {
                    ActionRow(Ic.Refresh, "Đồng bộ ngay", "Lần cuối: ${relTime(d.lastSync, now)}", spinning = syncing) { vm.refresh() }
                    HorizontalDivider(color = Ui.Line)
                    ActionRow(Ic.Share, "Xuất lịch (.ics)", "Import vào Google Calendar, Outlook, Apple Calendar") { exportIcs(ctx, d) }
                }
            }
        }

        item {
            SectionTitle(
                "Thay đổi gần đây",
                if (d.changes.isNotEmpty()) "Xóa" else null,
                if (d.changes.isNotEmpty()) ({ vm.clearChanges() }) else null,
            )
        }
        if (d.changes.isEmpty()) {
            item { EmptyCard("Chưa có thay đổi nào", "App sẽ báo khi đổi phòng, nghỉ dạy hay dạy bù", "🔔") }
        } else {
            itemsIndexed(d.changes.take(30), key = { i, e -> "ch$i${e.at}" }) { i, e -> Stagger(i) { ChangeItem(e) } }
        }

        item {
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .bounceClick { confirmLogout = true }
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, Ui.Brand.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .background(Ui.Brand.copy(alpha = 0.05f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Logout, null, tint = Ui.Brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Đăng xuất", style = MaterialTheme.typography.titleSmall, color = Ui.Brand)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "UIS+ 1.1.0 · App không chính thức, chỉ đọc dữ liệu từ uis.ptithcm.edu.vn.\nMật khẩu (nếu lưu) được mã hóa bằng Android Keystore và chỉ nằm trên máy này.",
                style = MaterialTheme.typography.bodySmall, color = Ui.Faint,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = Ui.Card,
            title = { Text("Đăng xuất?") },
            text = { Text("Xóa tài khoản, mật khẩu đã lưu và lịch đã tải khỏi máy này.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Đăng xuất", color = Ui.Brand) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Hủy", color = Ui.Sub) } },
        )
    }
}

@Composable
private fun ProfileCard(d: AppData) {
    val initials = d.studentName.trim().split(" ").filter { it.isNotBlank() }.let { p ->
        ((p.dropLast(1).lastOrNull()?.take(1) ?: "") + (p.lastOrNull()?.take(1) ?: "")).uppercase()
    }.ifBlank { "SV" }
    // v1.4: the ho so phang, avatar chu cai tren nen nhat mau nhan
    Row(
        Modifier
            .fillMaxWidth()
            .elev(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Ui.BrandSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = MaterialTheme.typography.titleLarge, color = Ui.Brand)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(d.studentName.ifBlank { d.userName }, style = MaterialTheme.typography.titleLarge, color = Ui.Ink, maxLines = 1)
            Text(d.userName, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Ui.Sub)
            Spacer(Modifier.height(8.dp))
            Pill(Ic.Calendar, d.currentSemesterName.ifBlank { "Học kỳ ${d.currentHocKy}" }, Ui.Sub, Ui.Soft)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .elev(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun IconBubble(icon: ImageVector, color: Color = Ui.Brand, rotate: Float = 0f) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(19.dp).graphicsLayer { rotationZ = rotate }) }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon)
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

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String, spinning: Boolean = false, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "ar")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "rot")
    Row(
        Modifier.fillMaxWidth().bounceClick(scaleDown = 0.98f, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, Ui.Violet, if (spinning) rot else 0f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ui.Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ui.Sub)
        }
        Icon(Ic.Right, null, tint = Ui.Faint)
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val idx = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(Ui.Soft).padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * idx, spring(dampingRatio = 0.7f, stiffness = 450f), label = "seg")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .elev(6.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Ui.Card),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                val c by animateColorAsState(if (i == idx) Ui.Brand else Ui.Sub, label = "segc")
                Box(Modifier.weight(1f).fillMaxHeight().bounceClick(scaleDown = 0.94f) { onSelect(o) }, contentAlignment = Alignment.Center) {
                    Text(label(o), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = c)
                }
            }
        }
    }
}

private data class ThemeOpt(val mode: Int, val label: String, val left: Palette, val right: Palette, val icon: ImageVector?)

@Composable
private fun ThemePicker(selected: Int, onSelect: (Int) -> Unit) {
    val opts = listOf(
        ThemeOpt(0, "Máy", Palette.Light, Palette.Dark, null),
        ThemeOpt(1, "Sáng", Palette.Light, Palette.Light, null),
        ThemeOpt(2, "Tối", Palette.Dark, Palette.Dark, null),
        ThemeOpt(3, "AMOLED", Palette.Amoled, Palette.Amoled, null),
        ThemeOpt(4, "Hẹn giờ", Palette.Light, Palette.Dark, Ic.Moon),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { o ->
            val sel = o.mode == selected
            val scale by animateFloatAsState(if (sel) 1f else 0.94f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "ts${o.mode}")
            val border by animateColorAsState(if (sel) Ui.Brand else Ui.Line, tween(250), label = "tb${o.mode}")
            Column(
                Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }.bounceClick(scaleDown = 0.9f) { onSelect(o.mode) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(70.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(if (sel) 2.dp else 1.dp, border, RoundedCornerShape(16.dp)),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        fun mini(p: Palette, x0: Float, x1: Float) {
                            drawRect(p.bg, Offset(x0, 0f), Size(x1 - x0, h))
                        }
                        mini(o.left, 0f, w / 2)
                        mini(o.right, w / 2, w)
                        // the "card" + vach brand
                        val cw = w * 0.7f
                        val cx = (w - cw) / 2
                        drawRoundRect(o.left.card, Offset(cx, h * 0.22f), Size(cw / 2, h * 0.56f), CornerRadius(10f, 10f))
                        drawRoundRect(o.right.card, Offset(cx + cw / 2, h * 0.22f), Size(cw / 2, h * 0.56f), CornerRadius(10f, 10f))
                        drawRoundRect(o.left.brand, Offset(cx + cw * 0.12f, h * 0.34f), Size(cw * 0.5f, h * 0.1f), CornerRadius(8f, 8f))
                        drawRoundRect(o.right.sub.copy(alpha = 0.6f), Offset(cx + cw * 0.12f, h * 0.52f), Size(cw * 0.7f, h * 0.07f), CornerRadius(8f, 8f))
                    }
                    if (o.icon != null) {
                        Icon(o.icon, null, tint = Color(0xFFFBBF24), modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(14.dp))
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        sel, modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                        enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 600f)) + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Ui.Brand), contentAlignment = Alignment.Center) {
                            Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    o.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = if (sel) Ui.Brand else Ui.Sub, maxLines = 1,
                )
            }
        }
    }
}

private fun exportIcs(ctx: Context, d: AppData) {
    try {
        val dir = File(ctx.cacheDir, "ics").apply { mkdirs() }
        val f = File(dir, "tkb_ptit_${d.currentHocKy}.ics")
        f.writeText(Ics.build(d.sessions))
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/calendar")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Thời khóa biểu PTIT")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Xuất thời khóa biểu (${d.sessions.size} buổi)"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Không xuất được: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
