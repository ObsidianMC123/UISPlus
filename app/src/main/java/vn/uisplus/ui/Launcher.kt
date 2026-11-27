package vn.uisplus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.uisplus.focus.Focus
import vn.uisplus.music.MusicPlayer
import vn.uisplus.ui.theme.Tok
import vn.uisplus.ui.theme.Ui

/** Chi so man hinh trong HomeScreen (giu so cu cho intent "tab"). */
object Tabs {
    const val TODAY = 0
    const val WEEK = 1
    const val BUS = 2
    const val FOCUS = 3
    const val ME = 4
    const val MUSIC = 5
    const val NOTES = 6
    /** Cac man nam trong popup "4 cua so". */
    val IN_LAUNCHER = setOf(FOCUS, MUSIC, NOTES)
}

/**
 * v1.7: nut giua thanh tab - icon 4 cua so (ve bang path, khong emoji).
 * Mo popup: 4 o xoay 45 do + thu nho, nhuong cho dau X xoay vao.
 */
@Composable
fun CenterButton(open: Boolean, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 520f), label = "cb")
    val bg by animateColorAsState(if (open || active) Ui.Brand else Ui.Ink, tween(220), label = "cbbg")
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(48.dp, 40.dp)
                .graphicsLayer { val s = 1f - 0.04f * p; scaleX = s; scaleY = s }
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .bounceClick(scaleDown = 0.9f, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Ic.Windows, "Tiện ích", tint = Ui.Bg,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    rotationZ = 90f * p; alpha = 1f - p; val s = 1f - 0.45f * p; scaleX = s; scaleY = s
                },
            )
            Icon(
                Ic.Close, "Đóng", tint = Ui.Bg,
                modifier = Modifier.size(22.dp).graphicsLayer {
                    rotationZ = -90f * (1f - p); alpha = p; val s = 0.55f + 0.45f * p; scaleX = s; scaleY = s
                },
            )
        }
    }
}

/**
 * Popup "Tien ich" bat ra tu nut giua: scrim mo dan, the noi phong to tu day (diem goc = nut),
 * cac o vao lech nhip. Ve TREN noi dung nhung DUOI thanh tab de nut giua thanh nut dong.
 */
@Composable
fun LauncherPopup(visible: Boolean, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    if (visible) BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = if (Ui.isDark) 0.55f else 0.32f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = NAV_BAR_HEIGHT + 12.dp),
            enter = fadeIn(tween(140)) +
                scaleIn(spring(dampingRatio = 0.78f, stiffness = 520f), initialScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 1.15f)) +
                slideInVertically(spring(dampingRatio = 0.85f, stiffness = 480f)) { it / 5 },
            exit = fadeOut(tween(140)) +
                scaleOut(tween(180, easing = Tok.EmphasizedAccel), targetScale = 0.7f, transformOrigin = TransformOrigin(0.5f, 1.15f)) +
                slideOutVertically(tween(180)) { it / 6 },
        ) {
            val ctx = LocalContext.current
            val ps by MusicPlayer.state.collectAsStateWithLifecycle()
            val st by Focus.state.collectAsStateWithLifecycle()
            val now = rememberNow(1000)
            val shape = RoundedCornerShape(Tok.RSheet)
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .elev(16.dp, shape)
                    .clip(shape)
                    .background(Ui.Card)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(14.dp),
            ) {
                Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tiện ích", color = Ui.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val musicSub = ps.current?.let { (if (ps.playing) "Đang phát · " else "Tạm dừng · ") + it.title } ?: "YouTube · tìm & danh sách phát"
                    val timerSub = when {
                        st.running -> "Đang chạy · còn " + fmtDur((st.endAt - now).coerceAtLeast(0))
                        st.active -> "Đang tạm dừng"
                        else -> "Pomodoro · tập trung học"
                    }
                    Tile(0, Ic.Music, "Nhạc", musicSub, current == Tabs.MUSIC, live = ps.playing, Modifier.weight(1f)) { onPick(Tabs.MUSIC) }
                    Tile(1, Ic.Timer, "Hẹn giờ", timerSub, current == Tabs.FOCUS, live = st.running, Modifier.weight(1f)) { onPick(Tabs.FOCUS) }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val notes by vn.uisplus.notes.NoteStore.notes.collectAsStateWithLifecycle()
                    Tile(2, Ic.Note, "Ghi bài", if (notes.isEmpty()) "Chụp bảng · soạn ghi chú" else "${notes.size} ghi bài · chụp bảng", current == Tabs.NOTES, live = false, Modifier.weight(1f)) { onPick(Tabs.NOTES) }
                    Tile(3, Ic.Camera, "Chụp bảng", "Mở camera, gắn vào môn đang học", false, live = false, Modifier.weight(1f)) { onPick(-Tabs.NOTES) }
                }
                // dang phat -> dieu khien nhanh ngay trong popup
                val t = ps.current
                if (t != null) {
                    StaggerIn(2) {
                        Row(
                            Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(Tok.RCard)).background(Ui.Soft)
                                .clickable { onDismiss(); MusicUi.full = true }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NetImage(t.art, Modifier.size(40.dp))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(t.title, color = Ui.Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, color = Ui.Sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Ui.Card).clickable { MusicPlayer.toggle(ctx) }, contentAlignment = Alignment.Center) {
                                Icon(if (ps.playing) Ic.Pause else Ic.Play, null, tint = Ui.Ink, modifier = Modifier.size(22.dp))
                            }
                            Box(Modifier.padding(start = 4.dp).size(40.dp).clip(CircleShape).clickable { MusicPlayer.next(ctx) }, contentAlignment = Alignment.Center) {
                                Icon(Ic.Skip, null, tint = Ui.Ink, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Phan tu trong popup vao lech nhip theo [i] (dung transition cua AnimatedVisibility cha). */
@Composable
private fun androidx.compose.animation.AnimatedVisibilityScope.StaggerIn(i: Int, content: @Composable () -> Unit) {
    val p by transition.animateFloat(
        transitionSpec = { tween(320, delayMillis = 50 + i * 45, easing = Tok.EmphasizedDecel) },
        label = "st$i",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    Box(Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 36f }) { content() }
}

@Composable
private fun androidx.compose.animation.AnimatedVisibilityScope.Tile(
    i: Int, icon: ImageVector, title: String, sub: String, selected: Boolean, live: Boolean,
    modifier: Modifier, onClick: () -> Unit,
) {
    val p by transition.animateFloat(
        transitionSpec = { tween(340, delayMillis = 40 + i * 55, easing = Tok.EmphasizedDecel) },
        label = "tile$i",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val shape = RoundedCornerShape(Tok.RCard)
    Column(
        modifier
            .graphicsLayer { alpha = p; translationY = (1f - p) * 48f; val s = 0.92f + 0.08f * p; scaleX = s; scaleY = s }
            .height(124.dp)
            .clip(shape)
            .background(if (selected) Ui.BrandSoft else Ui.Soft)
            .border(1.dp, if (selected) Ui.Brand.copy(alpha = 0.5f) else Color.Transparent, shape)
            .bounceClick(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ui.Card), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ui.Brand, modifier = Modifier.size(22.dp))
            if (live) PulseDot(Ui.Brand, Modifier.align(Alignment.TopEnd).padding(4.dp), dotSize = 6.dp)
        }
        Box(Modifier.weight(1f))
        Text(title, color = Ui.Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(sub, color = Ui.Sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
