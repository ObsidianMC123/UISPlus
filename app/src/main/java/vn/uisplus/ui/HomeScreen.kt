package vn.uisplus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.uisplus.data.ClassSession
import vn.uisplus.ui.theme.Ui
import vn.uisplus.ui.theme.Tok
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(vm: MainViewModel) {
    val dataState by vm.data.collectAsStateWithLifecycle()
    val d = dataState ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<ClassSession?>(null) }
    var launcher by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        vm.toast.collect {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(it)
        }
    }
    LaunchedEffect(Unit) {
        vn.uisplus.MainActivity.pendingTab.collect { t ->
            if (t != null) { tab = t.coerceIn(0, Tabs.NOTES); launcher = false; vn.uisplus.MainActivity.pendingTab.value = null }
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize().background(Ui.Bg)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                // v1.7: man mo tu popup "4 cua so" -> phong to nhe tu duoi (giong popup), khong truot ngang
                if (targetState in Tabs.IN_LAUNCHER || initialState in Tabs.IN_LAUNCHER) {
                    return@AnimatedContent (fadeIn(tween(260, delayMillis = 40, easing = Tok.EmphasizedDecel)) +
                        scaleIn(tween(320, easing = Tok.EmphasizedDecel), initialScale = 0.94f,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)))
                        .togetherWith(fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.98f))
                }
                // v1.4: "shared axis X" cua Material — truot 8% + mo dan, ra nhanh vao cham
                val dir = if (slot(targetState) > slot(initialState)) 1 else -1
                (slideInHorizontally(tween(Tok.DurMedium, easing = Tok.EmphasizedDecel)) { it / 12 * dir } +
                    fadeIn(tween(210, delayMillis = 70, easing = Tok.EmphasizedDecel)))
                    .togetherWith(
                        slideOutHorizontally(tween(Tok.DurShort, easing = Tok.EmphasizedAccel)) { -it / 12 * dir } +
                            fadeOut(tween(90, easing = Tok.EmphasizedAccel)),
                    )
            },
            label = "tabs",
        ) { t ->
            when (t) {
                0 -> TodayScreen(vm, d, onOpen = { detail = it }, onSeeWeek = { tab = 1 })
                1 -> WeekScreen(vm, d, onOpen = { detail = it })
                2 -> BusScreen(vm)
                3 -> FocusScreen(vm, d)
                Tabs.MUSIC -> MusicScreen()
                Tabs.NOTES -> NotesScreen(d)
                else -> SettingsScreen(vm, d)
            }
        }
        LaunchedEffect(launcher, tab) {
            if (launcher || tab == Tabs.MUSIC) vn.uisplus.music.MusicPlayer.connect(ctx)
        }
        // v1.9: co phien nghe cu -> ket noi som de thanh mini hien bai dang nghe do, bam la nghe tiep
        LaunchedEffect(Unit) {
            val has = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vn.uisplus.music.LastSession.has(ctx) }
            if (has) vn.uisplus.music.MusicPlayer.connect(ctx)
        }
        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = NAV_BAR_HEIGHT + 8.dp),
        ) {
            Snackbar(it, containerColor = Ui.Ink, contentColor = Ui.Bg, shape = RoundedCornerShape(16.dp))
        }
        LauncherPopup(
            visible = launcher, current = tab,
            onPick = {
                launcher = false
                if (it == -Tabs.NOTES) { tab = Tabs.NOTES; NotesUi.quickShot(d) } else tab = it
            },
            onDismiss = { launcher = false },
        )
        BottomBar(
            tab, launcher,
            onSelect = { launcher = false; tab = it },
            onCenter = { launcher = !launcher },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        MusicFullPlayer()
        VideoHost() // v1.8: xem video kieu YouTube, de len tren cung
        NotesEditorHost(d) // v1.10: ghi bai (chup bang + soan ghi chu)
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vn.uisplus.notes.NoteStore.load(ctx) }
    }
    detail?.let { s ->
        SessionSheet(
            s,
            onNote = { cam -> detail = null; NotesUi.openForSession(ctx, s, cam) },
        ) { detail = null }
    }
}

/**
 * v1.4: thanh tab phang kieu Material 3 — 5 tab luon co icon + chu, chi bao dang chon
 * la vien pill nhat mau nhan, no ra tu giua khi chon (khong gradient, khong bong mau).
 */
@Composable
internal fun BottomBar(selected: Int, launcherOpen: Boolean, onSelect: (Int) -> Unit, onCenter: () -> Unit, modifier: Modifier) {
    // v1.7: [Hom nay, Lich, (4 cua so), Xe buyt, Ca nhan]; slot giua = -1, Nhac + Hen gio nam trong popup
    val items = listOf(
        Triple(Tabs.TODAY, Ic.Home, "Hôm nay"), Triple(Tabs.WEEK, Ic.Calendar, "Lịch"), Triple(-1, Ic.Windows, ""),
        Triple(Tabs.BUS, Ic.Bus, "Xe buýt"), Triple(Tabs.ME, Ic.Person, "Cá nhân"),
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().background(Ui.Card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ui.Line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(NAV_BAR_HEIGHT)
                .padding(horizontal = 4.dp),
        ) {
            items.forEach { (i, icon, label) ->
                if (i < 0) {
                    CenterButton(
                        open = launcherOpen, active = selected in Tabs.IN_LAUNCHER,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onCenter()
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    return@forEach
                }
                val sel = i == selected && !launcherOpen
                val pill by animateFloatAsState(
                    if (sel) 1f else 0f,
                    spring(dampingRatio = 0.78f, stiffness = 520f),
                    label = "pill$i",
                )
                val tint by animateColorAsState(if (sel) Ui.Brand else Ui.Sub, tween(200), label = "tint$i")
                val txt by animateColorAsState(if (sel) Ui.Ink else Ui.Sub, tween(200), label = "txt$i")
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (!sel) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(56.dp, 30.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = 0.35f + 0.65f * pill; alpha = pill }
                                .clip(RoundedCornerShape(50))
                                .background(Ui.BrandSoft),
                        )
                        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        label, color = txt, maxLines = 1,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                        fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Vi tri tren thanh tab (de chon huong truot). Man trong popup coi nhu o giua. */
internal fun slot(tab: Int): Int = when (tab) {
    Tabs.TODAY -> 0; Tabs.WEEK -> 1; Tabs.BUS -> 3; Tabs.ME -> 4; else -> 2
}

/** Chieu cao thanh tab (chua tinh thanh dieu huong he thong). */
val NAV_BAR_HEIGHT = 68.dp
