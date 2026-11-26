package vn.uisplus.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.uisplus.music.MusicPlayer
import vn.uisplus.music.MusicStore
import vn.uisplus.music.PlayState
import vn.uisplus.music.Playlist
import vn.uisplus.music.PlaylistHit
import vn.uisplus.music.Track
import vn.uisplus.music.Yt
import vn.uisplus.ui.theme.Tok
import vn.uisplus.ui.theme.Ui

/** Trang thai UI nhac dung chung (mo man phat toan man hinh tu bat ky dau). */
object MusicUi {
    var full by mutableStateOf(false)
}

// ============================================================ anh mang

private object Thumbs {
    val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
}

private fun loadBitmap(url: String, maxPx: Int): ImageBitmap? = runCatching {
    Yt.client.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", Yt.UA).build()).execute().use { r ->
        if (!r.isSuccessful) return null
        val bytes = r.body.bytes()
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
    }
}.getOrNull()

/** Anh tu mang co cache + mo dan; [maxPx] gioi han do phan giai giai ma. */
@Composable
fun NetImage(url: String, modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp), maxPx: Int = 240) {
    val key = "$url@$maxPx"
    val img by produceState(Thumbs.cache.get(key), key) {
        if (value == null && url.isNotBlank()) {
            value = withContext(Dispatchers.IO) { loadBitmap(url, maxPx) }?.also { Thumbs.cache.put(key, it) }
        }
    }
    Box(modifier.clip(shape).background(Ui.Soft)) {
        Crossfade(img, Modifier.fillMaxSize(), animationSpec = tween(260), label = "img") { b ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Ic.Music, null, tint = Ui.Faint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

fun fmtDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val ss = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
}

/** Vi tri phat cap nhat theo nhip (chi chay khi dang phat). */
@Composable
private fun rememberPosition(ps: PlayState, periodMs: Long = 500): Long {
    val v by produceState(MusicPlayer.position(), ps.playing, ps.current?.id) {
        value = MusicPlayer.position()
        while (ps.playing) { delay(periodMs); value = MusicPlayer.position() }
    }
    return v
}

// ============================================================ man Nhac

private val MODES = listOf("Bài hát", "Video", "Danh sách phát")

@Composable
fun MusicScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { MusicStore.load(ctx) }
        MusicPlayer.connect(ctx)
    }
    val lists by MusicStore.lists.collectAsStateWithLifecycle()
    val recent by MusicStore.recent.collectAsStateWithLifecycle()
    val ps by MusicPlayer.state.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<Track>?>(null) }
    var hits by remember { mutableStateOf<List<PlaylistHit>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf<Int?>(null) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = lists.firstOrNull { it.id == openId }
    // v1.8
    var resMode by remember { mutableStateOf(0) }
    var createDlg by remember { mutableStateOf(false) }
    var importDlg by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<Track?>(null) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(msg) { if (msg != null) { delay(3500); msg = null } }

    fun importUrl(url: String) {
        if (importing != null) return
        focus.clearFocus()
        importing = 0
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { Yt.playlist(url) { n -> importing = n } }
            }
            importing = null
            r.onSuccess { p ->
                if (p.tracks.isEmpty()) { msg = "Danh sách trống hoặc riêng tư"; return@onSuccess }
                MusicStore.save(ctx, p)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                query = ""; results = null; hits = null
                openId = p.id
            }.onFailure { msg = "Không nhập được: " + (it.message ?: it.javaClass.simpleName).take(80) }
        }
    }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        if (Yt.isPlaylistUrl(q)) { importUrl(q); return }
        val vid = Yt.videoId(q)
        loading = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        vid != null && (q.startsWith("http") || q.contains("youtu")) -> listOf(Yt.track(vid)) to null
                        mode == 2 -> null to Yt.searchPlaylists(q)
                        else -> Yt.search(q, songs = mode == 0) to null
                    }
                }
            }
            loading = false
            r.onSuccess { (t, h) ->
                results = t; hits = h; resMode = mode
                if (vid == null) MusicStore.addRecent(ctx, q)
                if (vid != null && t?.size == 1) MusicPlayer.playTracks(ctx, t)
            }.onFailure { msg = "Lỗi tìm kiếm: " + (it.message ?: it.javaClass.simpleName).take(80) }
        }
    }

    BackHandler(enabled = openId != null || results != null || hits != null) {
        when {
            openId != null -> openId = null
            else -> { results = null; hits = null; query = "" }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = open,
            contentKey = { it?.id },
            transitionSpec = {
                val fwd = targetState != null
                (slideInHorizontally(tween(Tok.DurMedium, easing = Tok.EmphasizedDecel)) { if (fwd) it / 8 else -it / 8 } +
                    fadeIn(tween(220, delayMillis = 60)))
                    .togetherWith(slideOutHorizontally(tween(Tok.DurShort, easing = Tok.EmphasizedAccel)) { if (fwd) -it / 8 else it / 8 } + fadeOut(tween(100)))
            },
            label = "music",
        ) { pl ->
            if (pl != null) {
                PlaylistPane(
                    pl, ps, top, onBack = { openId = null }, onRefresh = { importUrl(pl.url) }, refreshing = importing != null,
                    onDelete = { MusicStore.delete(ctx, pl.id); openId = null; msg = "Đã xóa \"${pl.name}\"" },
                    onAddTo = { addTo = it },
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 14.dp, bottom = if (ps.current != null) 210.dp else 130.dp),
                ) {
                    item {
                        Column(Modifier.padding(bottom = 14.dp)) {
                            Text("Nhạc", style = MaterialTheme.typography.headlineMedium, color = Ui.Ink)
                            Text("Tìm trên YouTube hoặc dán link danh sách phát", style = MaterialTheme.typography.bodyMedium, color = Ui.Sub)
                        }
                    }
                    item {
                        SearchField(query, loading, onChange = { query = it }, onSubmit = { submit() }, onClear = { query = ""; results = null; hits = null })
                    }
                    item {
                        Segmented(MODES, mode, Modifier.padding(top = 10.dp)) {
                            mode = it
                            if (query.isNotBlank() && (results != null || hits != null)) submit()
                        }
                    }
                    // link dan vao -> hanh dong ro rang
                    item {
                        val q = query.trim()
                        AnimatedVisibility(
                            Yt.isPlaylistUrl(q) && importing == null,
                            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(),
                        ) {
                            ActionRow(Ic.PlaylistAdd, "Nhập danh sách phát từ link", "Công khai hoặc không công khai", Modifier.padding(top = 12.dp)) { importUrl(q) }
                        }
                    }
                    item {
                        AnimatedVisibility(importing != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column(
                                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(Tok.RCard)).background(Ui.Card)
                                    .border(1.dp, Ui.Line, RoundedCornerShape(Tok.RCard)).padding(14.dp),
                            ) {
                                Text("Đang nhập danh sách phát… ${importing ?: 0} bài", color = Ui.Ink, style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(Modifier.padding(top = 10.dp).fillMaxWidth().clip(CircleShape), color = Ui.Brand, trackColor = Ui.Soft)
                            }
                        }
                    }
                    item {
                        AnimatedVisibility(msg != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Text(msg ?: "", color = Ui.Brand, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                        }
                    }

                    val r = results
                    val h = hits
                    when {
                        r != null -> {
                            item { SectionTitle(if (r.isEmpty()) "Không có kết quả" else "Kết quả") }
                            val video = resMode == 1
                            itemsIndexed(r, key = { _, t -> "r" + t.id }) { i, t ->
                                Stagger(i.coerceAtMost(8), key = r) {
                                    TrackRow(
                                        t, playing = ps.current?.id == t.id, isPlaying = ps.playing, video = video,
                                        onClick = { if (video) VideoUi.open(t) else MusicPlayer.playTracks(ctx, r, i) },
                                        menu = trackMenu(ctx, t, video, onMsg = { msg = it }, onAddTo = { addTo = t }),
                                    )
                                }
                            }
                        }
                        h != null -> {
                            item { SectionTitle(if (h.isEmpty()) "Không có kết quả" else "Danh sách phát") }
                            itemsIndexed(h, key = { _, p -> "h" + p.url }) { i, p ->
                                Stagger(i.coerceAtMost(8), key = h) { HitRow(p) { importUrl(p.url) } }
                            }
                        }
                        else -> {
                            if (recent.isNotEmpty()) {
                                item { SectionTitle("Tìm gần đây", "Xóa") { MusicStore.clearRecent(ctx) } }
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(recent) { q ->
                                            Text(
                                                q, color = Ui.Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                                                modifier = Modifier.clip(RoundedCornerShape(Tok.RChip)).background(Ui.Soft)
                                                    .clickable { query = q; submit() }.padding(horizontal = 12.dp, vertical = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            item { SectionTitle("Danh sách phát của bạn", if (lists.isEmpty()) null else "${lists.size}") }
                            item {
                                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PillButton(Ic.Add, "Tạo mới", filled = false, modifier = Modifier.weight(1f)) { createDlg = true }
                                    PillButton(Ic.Link, "Nhập từ YouTube", filled = false, modifier = Modifier.weight(1.3f)) { importDlg = true }
                                }
                            }
                            if (lists.isEmpty()) {
                                item { EmptyCard("Chưa có danh sách phát", "Bấm \"Tạo mới\" để tự gom bài, hoặc \"Nhập từ YouTube\" rồi dán link danh sách phát.", icon = Ic.Queue) }
                            }
                            itemsIndexed(lists, key = { _, p -> "l" + p.id }) { i, p ->
                                Stagger(i.coerceAtMost(8)) {
                                    PlaylistRow(p, playingHere = ps.current != null && p.tracks.any { it.id == ps.current?.id }) { openId = p.id }
                                }
                            }
                        }
                    }
                }
            }
        }
        MiniPlayer(
            ps,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = NAV_BAR_HEIGHT + 10.dp),
        )
    }

    if (createDlg) {
        NameDialog("Tạo danh sách phát", "", "Tạo", onDismiss = { createDlg = false }) { name ->
            createDlg = false
            val p = MusicStore.create(ctx, name)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            query = ""; results = null; hits = null
            openId = p.id
        }
    }
    if (importDlg) {
        ImportDialog(onDismiss = { importDlg = false }) { url ->
            importDlg = false
            importUrl(url)
        }
    }
    addTo?.let { t -> AddToPlaylistDialog(t, onDone = { m -> if (m != null) msg = m }) { addTo = null } }
}

// ============================================================ v1.8: menu bai + hop thoai danh sach

/** 1 dong trong menu "..." cua bai hat. */
class MenuAct(val icon: ImageVector, val label: String, val danger: Boolean = false, val run: () -> Unit)

private fun trackMenu(
    ctx: android.content.Context, t: Track, video: Boolean,
    onMsg: (String) -> Unit, onAddTo: () -> Unit, extra: List<MenuAct> = emptyList(),
): List<MenuAct> = listOf(
    if (video) MenuAct(Ic.Headphones, "Nghe dạng nhạc") { MusicPlayer.playTracks(ctx, listOf(t)) }
    else MenuAct(Ic.Video, "Xem video") { VideoUi.open(t) },
    MenuAct(Ic.PlayNext, "Phát tiếp theo") { MusicPlayer.enqueue(ctx, t, next = true); onMsg("Sẽ phát tiếp theo") },
    MenuAct(Ic.Queue, "Thêm vào hàng chờ") { MusicPlayer.enqueue(ctx, t); onMsg("Đã thêm vào hàng chờ") },
    MenuAct(Ic.PlaylistAdd, "Thêm vào danh sách phát…", run = onAddTo),
) + extra

@Composable
private fun dlgColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ui.Ink, unfocusedTextColor = Ui.Ink,
    focusedBorderColor = Ui.Brand, unfocusedBorderColor = Ui.Line,
    cursorColor = Ui.Brand, focusedLabelColor = Ui.Brand, unfocusedLabelColor = Ui.Sub,
)

/** Hop thoai nhap ten (tao moi / doi ten). */
@Composable
fun NameDialog(title: String, initial: String, ok: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ui.Card,
        title = { Text(title, color = Ui.Ink) },
        text = {
            OutlinedTextField(
                name, { name = it.take(80) }, singleLine = true, label = { Text("Tên danh sách") },
                colors = dlgColors(), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onOk(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onOk(name) }, enabled = name.isNotBlank()) { Text(ok, color = if (name.isNotBlank()) Ui.Brand else Ui.Faint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy", color = Ui.Sub) } },
    )
}

/** Hop thoai dan link danh sach phat YouTube (tu dien san neu clipboard dang co link). */
@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val ctx = LocalContext.current
    fun clip(): String = runCatching {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim() ?: ""
    }.getOrDefault("")
    var url by remember { mutableStateOf(clip().takeIf { Yt.isPlaylistUrl(it) } ?: "") }
    val ok = Yt.isPlaylistUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ui.Card,
        title = { Text("Nhập từ YouTube", color = Ui.Ink) },
        text = {
            Column {
                Text(
                    "Trên YouTube / YouTube Music: mở danh sách phát → Chia sẻ → Sao chép đường liên kết, rồi dán vào đây. Danh sách phải ở chế độ Công khai hoặc Không công khai.",
                    color = Ui.Sub, style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    url, { url = it.trim() }, singleLine = true, label = { Text("Link danh sách phát") },
                    placeholder = { Text("youtube.com/playlist?list=…", color = Ui.Faint) },
                    colors = dlgColors(), modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    trailingIcon = {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { url = clip() }, contentAlignment = Alignment.Center) {
                            Icon(Ic.Paste, "Dán", tint = Ui.Brand, modifier = Modifier.size(20.dp))
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (ok) onImport(url) }),
                )
                if (url.isNotBlank() && !ok) {
                    Text("Link này không phải danh sách phát (thiếu \"list=\").", color = Ui.Brand, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onImport(url) }, enabled = ok) { Text("Nhập", color = if (ok) Ui.Brand else Ui.Faint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy", color = Ui.Sub) } },
    )
}

/** Chon danh sach tu tao de them bai [t] (hoac tao danh sach moi ngay tai day). */
@Composable
fun AddToPlaylistDialog(t: Track, onDone: (String?) -> Unit = {}, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { MusicStore.load(ctx) } }
    val lists by MusicStore.lists.collectAsStateWithLifecycle()
    val mine = lists.filter { it.local }
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("Danh sách mới", "", "Tạo và thêm", onDismiss = { creating = false }) { name ->
            val p = MusicStore.create(ctx, name, first = t)
            onDone("Đã thêm vào \"${p.name}\""); onDismiss()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ui.Card,
        title = { Text("Thêm vào danh sách phát", color = Ui.Ink) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(if (mine.size > 4) 320.dp else ((mine.size + 1) * 64).dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Tok.RButton)).clickable { creating = true }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Ui.BrandSoft), contentAlignment = Alignment.Center) {
                            Icon(Ic.Add, null, tint = Ui.Brand, modifier = Modifier.size(24.dp))
                        }
                        Text("Danh sách mới", color = Ui.Brand, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                items(mine, key = { it.id }) { p ->
                    val has = p.tracks.any { it.id == t.id }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Tok.RButton)).clickable {
                            if (has) onDone("Bài này đã có trong \"${p.name}\"")
                            else { MusicStore.addTrack(ctx, p.id, t); onDone("Đã thêm vào \"${p.name}\"") }
                            onDismiss()
                        }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NetImage(p.thumb.ifBlank { p.tracks.firstOrNull()?.art ?: "" }, Modifier.size(44.dp), RoundedCornerShape(10.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, color = Ui.Ink, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.tracks.size} bài", color = Ui.Sub, style = MaterialTheme.typography.bodySmall)
                        }
                        if (has) Icon(Ic.Check, "Đã có", tint = Ui.Brand, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng", color = Ui.Sub) } },
    )
}

// ============================================================ thanh phan

@Composable
private fun SearchField(value: String, loading: Boolean, onChange: (String) -> Unit, onSubmit: () -> Unit, onClear: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Ui.Brand else Ui.Line, tween(180), label = "sb")
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(Tok.RButton)).background(Ui.Card)
            .border(1.dp, border, RoundedCornerShape(Tok.RButton)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Search, null, tint = Ui.Sub, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (value.isEmpty()) Text("Bài hát, nghệ sĩ hoặc link YouTube", color = Ui.Faint, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ui.Ink),
                cursorBrush = SolidColor(Ui.Brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().onFocusChangedCompat { focused = it },
            )
        }
        Crossfade(if (loading) 1 else if (value.isNotEmpty()) 2 else 0, label = "sbt") { s ->
            when (s) {
                1 -> CircularProgressIndicator(Modifier.size(18.dp), color = Ui.Brand, strokeWidth = 2.dp)
                2 -> Icon(Ic.Close, "Xóa", tint = Ui.Sub, modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClear))
                else -> Spacer(Modifier.size(20.dp))
            }
        }
    }
}

private fun Modifier.onFocusChangedCompat(cb: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { cb(it.isFocused) }

/** Thanh chon 3 che do, vien chon truot muot. */
@Composable
private fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Tok.RButton)).background(Ui.Soft).padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) Ui.Card else Color.Transparent, tween(200), label = "seg$i")
            val fg by animateColorAsState(if (sel) Ui.Ink else Ui.Sub, tween(200), label = "segt$i")
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(Tok.RButton - 3.dp)).background(bg)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Tok.RCard)).background(Ui.BrandSoft).bounceClick(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Ui.Brand, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = Ui.Ink, style = MaterialTheme.typography.titleSmall)
            Text(sub, color = Ui.Sub, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Ic.Right, null, tint = Ui.Brand, modifier = Modifier.size(20.dp))
    }
}

/** 3 cot nhay nhe bao bai dang phat. */
@Composable
fun EqBars(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "c")
    Row(modifier.size(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(a, b, c).forEach { h ->
            Box(Modifier.weight(1f).fillMaxHeight(if (active) h else 0.3f).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

@Composable
private fun TrackRow(
    t: Track, playing: Boolean, isPlaying: Boolean, index: Int? = null, video: Boolean = false,
    onClick: () -> Unit, menu: List<MenuAct> = emptyList(),
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Tok.RButton)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                if (playing) EqBars(isPlaying, Ui.Brand)
                else Text("${index + 1}", color = Ui.Faint, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
            }
        }
        Box {
            if (video) {
                // che do Video: anh 16:9 + thoi luong nhu YouTube
                NetImage(t.art, Modifier.size(112.dp, 63.dp), maxPx = 320)
                if (t.dur > 0) Text(
                    fmtDur(t.dur * 1000), color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 4.dp),
                )
            } else NetImage(t.art, Modifier.size(48.dp))
            if (playing && index == null && !video) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    EqBars(isPlaying, Color.White)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                t.title, color = if (playing) Ui.Brand else Ui.Ink, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, maxLines = if (video) 2 else 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(t.artist.ifBlank { null }, if (t.dur > 0 && !video) fmtDur(t.dur * 1000) else null).joinToString(" · "),
                color = Ui.Sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (menu.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { open = true }, contentAlignment = Alignment.Center) {
                    Icon(Ic.More, "Tùy chọn", tint = Ui.Sub, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Ui.Card) {
                    menu.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label, color = if (m.danger) Ui.Brand else Ui.Ink) },
                            leadingIcon = { Icon(m.icon, null, tint = if (m.danger) Ui.Brand else Ui.Sub, modifier = Modifier.size(20.dp)) },
                            onClick = { open = false; m.run() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(p: PlaylistHit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Tok.RButton)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(p.thumb, Modifier.size(72.dp, 48.dp), maxPx = 200)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(p.name, color = Ui.Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(p.owner.ifBlank { null }, if (p.count > 0) "${p.count} bài" else null).joinToString(" · "),
                color = Ui.Sub, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            )
        }
        Icon(Ic.PlaylistAdd, "Nhập", tint = Ui.Brand, modifier = Modifier.padding(start = 8.dp).size(22.dp))
    }
}

@Composable
private fun PlaylistRow(p: Playlist, playingHere: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.padding(bottom = 10.dp).fillMaxWidth().elev(2.dp, RoundedCornerShape(Tok.RCard)).clip(RoundedCornerShape(Tok.RCard))
            .background(Ui.Card).bounceClick(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(p.thumb.ifBlank { p.tracks.firstOrNull()?.art ?: "" }, Modifier.size(56.dp), RoundedCornerShape(10.dp), maxPx = 200)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(p.name, color = Ui.Ink, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(if (p.local) "Của bạn" else p.owner.ifBlank { null }, "${p.tracks.size} bài").joinToString(" · "),
                color = Ui.Sub, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            )
        }
        if (playingHere) EqBars(true, Ui.Brand, Modifier.padding(end = 6.dp))
        Icon(Ic.Right, null, tint = Ui.Faint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PlaylistPane(
    p: Playlist, ps: PlayState, top: Dp,
    onBack: () -> Unit, onRefresh: () -> Unit, refreshing: Boolean, onDelete: () -> Unit,
    onAddTo: (Track) -> Unit,
) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note) { if (note != null) { delay(2500); note = null } }
    val totalMs = p.tracks.sumOf { it.dur } * 1000
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 6.dp, bottom = if (ps.current != null) 210.dp else 130.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Ic.Left, "Quay lại", onBack)
                Spacer(Modifier.weight(1f))
                if (refreshing) CircularProgressIndicator(Modifier.padding(12.dp).size(18.dp), color = Ui.Brand, strokeWidth = 2.dp)
                else if (p.url.isNotBlank()) RoundIcon(Ic.Refresh, "Cập nhật", onRefresh)
                if (p.local) RoundIcon(Ic.Edit, "Đổi tên") { renaming = true }
                RoundIcon(Ic.Delete, "Xóa") { confirm = true }
            }
        }
        item {
            Row(Modifier.padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(p.thumb.ifBlank { p.tracks.firstOrNull()?.art ?: "" }, Modifier.size(112.dp), RoundedCornerShape(Tok.RCard), maxPx = 360)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(p.name, color = Ui.Ink, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (p.local) Text("Danh sách của bạn", color = Ui.Sub, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    else if (p.owner.isNotBlank()) Text(p.owner, color = Ui.Sub, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text("${p.tracks.size} bài · ${fmtDur(totalMs)}", color = Ui.Faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (p.tracks.isNotEmpty()) item {
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(Ic.Play, "Phát", filled = true, modifier = Modifier.weight(1f)) { MusicPlayer.playTracks(ctx, p.tracks, 0) }
                PillButton(Ic.Shuffle, "Trộn bài", filled = false, modifier = Modifier.weight(1f)) { MusicPlayer.playTracks(ctx, p.tracks, 0, shuffle = true) }
            }
        }
        item {
            AnimatedVisibility(note != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(note ?: "", color = Ui.Brand, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        if (p.tracks.isEmpty()) item {
            EmptyCard("Danh sách đang trống", "Tìm bài hát hoặc video, bấm nút ⋮ bên phải rồi chọn \"Thêm vào danh sách phát…\".", icon = Ic.PlaylistAdd)
        }
        itemsIndexed(p.tracks, key = { i, t -> "$i" + t.id }) { i, t ->
            val extra = if (!p.local) emptyList() else listOfNotNull(
                if (i > 0) MenuAct(Ic.Up, "Chuyển lên") { MusicStore.moveTrack(ctx, p.id, i, i - 1) } else null,
                if (i < p.tracks.lastIndex) MenuAct(Ic.Down, "Chuyển xuống") { MusicStore.moveTrack(ctx, p.id, i, i + 1) } else null,
                MenuAct(Ic.Delete, "Xóa khỏi danh sách", danger = true) { MusicStore.removeTrack(ctx, p.id, i); note = "Đã bỏ \"${t.title}\"" },
            )
            TrackRow(
                t, playing = ps.current?.id == t.id, isPlaying = ps.playing, index = i,
                onClick = { MusicPlayer.playTracks(ctx, p.tracks, i) },
                menu = trackMenu(ctx, t, video = false, onMsg = { note = it }, onAddTo = { onAddTo(t) }, extra = extra),
            )
        }
    }
    if (renaming) {
        NameDialog("Đổi tên danh sách", p.name, "Lưu", onDismiss = { renaming = false }) { n ->
            renaming = false; MusicStore.rename(ctx, p.id, n)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Ui.Card,
            title = { Text("Xóa danh sách phát?", color = Ui.Ink) },
            text = {
                Text(
                    if (p.local) "\"${p.name}\" (${p.tracks.size} bài) sẽ bị xóa vĩnh viễn."
                    else "\"${p.name}\" sẽ bị xóa khỏi app. Danh sách trên YouTube không bị ảnh hưởng.",
                    color = Ui.Sub,
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Xóa", color = Ui.Brand) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Hủy", color = Ui.Sub) } },
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Ui.Ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PillButton(icon: ImageVector, text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Tok.RButton)
    Row(
        modifier.height(46.dp).clip(shape)
            .then(if (filled) Modifier.background(Ui.Brand) else Modifier.border(1.dp, Ui.Line, shape).background(Ui.Card))
            .bounceClick(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) Color.White else Ui.Ink, modifier = Modifier.size(20.dp))
        Text(text, color = if (filled) Color.White else Ui.Ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

// ============================================================ mini player

@Composable
fun MiniPlayer(ps: PlayState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    AnimatedVisibility(
        ps.current != null && !MusicUi.full, modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) { it } + fadeIn(),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
    ) {
        val t = ps.current ?: return@AnimatedVisibility
        val pos = rememberPosition(ps)
        val shape = RoundedCornerShape(Tok.RCard)
        Column(
            Modifier.fillMaxWidth().elev(16.dp, shape).clip(shape).background(Ui.Card)
                .clickable(remember { MutableInteractionSource() }, indication = null) { MusicUi.full = true },
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(t.art, Modifier.size(44.dp), RoundedCornerShape(10.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    AnimatedContent(t.id, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut()) }, label = "mt") { _ ->
                        Column {
                            Text(t.title, color = Ui.Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, color = Ui.Sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                PlayPauseSmall(ps) { MusicPlayer.toggle(ctx) }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { MusicPlayer.next(ctx) }, contentAlignment = Alignment.Center) {
                    Icon(Ic.Skip, "Bài sau", tint = Ui.Ink, modifier = Modifier.size(22.dp))
                }
            }
            val frac = if (ps.duration > 0) (pos.toFloat() / ps.duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(2.dp).background(Ui.Soft)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Ui.Brand))
            }
        }
    }
}

@Composable
private fun PlayPauseSmall(ps: PlayState, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (ps.buffering) CircularProgressIndicator(Modifier.size(22.dp), color = Ui.Brand, strokeWidth = 2.dp)
        else Crossfade(ps.playing, label = "pp") { p ->
            Icon(if (p) Ic.Pause else Ic.Play, if (p) "Tạm dừng" else "Phát", tint = Ui.Ink, modifier = Modifier.size(26.dp))
        }
    }
}

// ============================================================ man phat toan man hinh

@Composable
fun MusicFullPlayer() {
    val ctx = LocalContext.current
    val ps by MusicPlayer.state.collectAsStateWithLifecycle()
    LaunchedEffect(ps.current) { if (ps.current == null) MusicUi.full = false }
    AnimatedVisibility(
        MusicUi.full && ps.current != null,
        enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(260, easing = Tok.EmphasizedAccel)) { it } + fadeOut(tween(220)),
    ) {
        BackHandler { MusicUi.full = false }
        val t = ps.current ?: return@AnimatedVisibility
        var showQueue by rememberSaveable { mutableStateOf(false) }
        var sleepMenu by remember { mutableStateOf(false) }
        var addTo by remember { mutableStateOf<Track?>(null) }
        var note by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(note) { if (note != null) { delay(2500); note = null } }
        addTo?.let { tr -> AddToPlaylistDialog(tr, onDone = { note = it }) { addTo = null } }
        var drag by remember { mutableStateOf<Float?>(null) }
        val pos = rememberPosition(ps, 250)
        val dur = ps.duration
        Column(
            Modifier.fillMaxSize().background(Ui.Bg).clickable(remember { MutableInteractionSource() }, indication = null) {}
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Ic.Down, "Thu nhỏ") { MusicUi.full = false }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ĐANG PHÁT", color = Ui.Sub, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp))
                    Text("Bài ${ps.index + 1}/${ps.queue.size}", color = Ui.Ink, style = MaterialTheme.typography.labelLarge)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (showQueue) Ui.BrandSoft else Color.Transparent).clickable { showQueue = !showQueue },
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.Queue, "Hàng chờ", tint = if (showQueue) Ui.Brand else Ui.Ink, modifier = Modifier.size(22.dp)) }
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(showQueue, transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut(tween(120))) }, label = "fq") { q ->
                    if (q) QueueList(ps) else {
                        val scale by animateFloatAsState(if (ps.playing) 1f else 0.86f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "art")
                        AnimatedContent(t.id, transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut(tween(200))) }, label = "art2") { _ ->
                            NetImage(
                                t.art,
                                Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                                    .elev(16.dp, RoundedCornerShape(20.dp)),
                                RoundedCornerShape(20.dp), maxPx = 720,
                            )
                        }
                    }
                }
            }

            AnimatedContent(t.id, transitionSpec = { (slideInHorizontally { it / 6 } + fadeIn()).togetherWith(slideOutHorizontally { -it / 6 } + fadeOut()) }, label = "ti") { _ ->
                Column {
                    Text(t.title, color = Ui.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(t.artist, color = Ui.Sub, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            val frac = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Slider(
                value = frac,
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { MusicPlayer.seek(ctx, (it * dur).toLong()) }; drag = null },
                enabled = dur > 0,
                colors = SliderDefaults.colors(thumbColor = Ui.Brand, activeTrackColor = Ui.Brand, inactiveTrackColor = Ui.Soft),
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmtDur((frac * dur).toLong()), color = Ui.Sub, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(if (dur > 0) fmtDur(dur) else "--:--", color = Ui.Sub, style = MaterialTheme.typography.labelMedium)
            }

            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                ToggleIcon(Ic.Shuffle, "Trộn bài", ps.shuffle) { MusicPlayer.setShuffle(ctx, !ps.shuffle) }
                Box(Modifier.size(56.dp).clip(CircleShape).clickable { MusicPlayer.prev(ctx) }, contentAlignment = Alignment.Center) {
                    Icon(Ic.SkipPrev, "Bài trước", tint = Ui.Ink, modifier = Modifier.size(34.dp))
                }
                val press by animateFloatAsState(if (ps.playing) 1f else 0.94f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "pb")
                Box(
                    Modifier.size(76.dp).graphicsLayer { scaleX = press; scaleY = press }.clip(CircleShape).background(Ui.Brand).bounceClick { MusicPlayer.toggle(ctx) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (ps.buffering) CircularProgressIndicator(Modifier.size(30.dp), color = Color.White, strokeWidth = 2.5.dp)
                    else AnimatedContent(ps.playing, transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()).togetherWith(scaleOut(targetScale = 0.6f) + fadeOut()) }, label = "bp") { p ->
                        Icon(if (p) Ic.Pause else Ic.Play, if (p) "Tạm dừng" else "Phát", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }
                Box(Modifier.size(56.dp).clip(CircleShape).clickable { MusicPlayer.next(ctx) }, contentAlignment = Alignment.Center) {
                    Icon(Ic.Skip, "Bài sau", tint = Ui.Ink, modifier = Modifier.size(34.dp))
                }
                ToggleIcon(if (ps.repeat == Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat, "Lặp lại", ps.repeat != Player.REPEAT_MODE_OFF) { MusicPlayer.cycleRepeat(ctx) }
            }

            AnimatedVisibility(ps.error != null || note != null) {
                Text(note ?: ps.error ?: "", color = Ui.Brand, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }

            // hen gio tat nhac + (v1.8) luu vao danh sach + xem video
            val now = rememberNow(1000)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Row(
                    Modifier.clip(CircleShape).background(Ui.Soft).clickable { addTo = t }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.PlaylistAdd, null, tint = Ui.Sub, modifier = Modifier.size(16.dp))
                    Text("Lưu", color = Ui.Ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
                Row(
                    Modifier.clip(CircleShape).background(Ui.Soft).clickable {
                        val pos = MusicPlayer.position()
                        MusicPlayer.pause(ctx)
                        MusicUi.full = false
                        VideoUi.open(t, pos)
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.Video, null, tint = Ui.Sub, modifier = Modifier.size(16.dp))
                    Text("Video", color = Ui.Ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
                Box {
                    val on = ps.sleepAt != 0L
                    val label = when {
                        ps.sleepAt == -1L -> "Tắt khi hết bài này"
                        ps.sleepAt > 0 -> "Tắt sau " + fmtDur(ps.sleepAt - now)
                        else -> "Hẹn giờ tắt"
                    }
                    Row(
                        Modifier.clip(CircleShape).background(if (on) Ui.BrandSoft else Ui.Soft).clickable { sleepMenu = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ic.Moon, null, tint = if (on) Ui.Brand else Ui.Sub, modifier = Modifier.size(16.dp))
                        Text(label, color = if (on) Ui.Brand else Ui.Ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                    }
                    DropdownMenu(sleepMenu, onDismissRequest = { sleepMenu = false }, containerColor = Ui.Card) {
                        listOf(15, 30, 45, 60, 90).forEach { m ->
                            DropdownMenuItem(text = { Text("$m phút", color = Ui.Ink) }, onClick = { sleepMenu = false; MusicPlayer.sleep(ctx, m) })
                        }
                        DropdownMenuItem(text = { Text("Hết bài này", color = Ui.Ink) }, onClick = { sleepMenu = false; MusicPlayer.sleep(ctx, -1) })
                        if (ps.sleepAt != 0L) DropdownMenuItem(text = { Text("Tắt hẹn giờ", color = Ui.Brand) }, onClick = { sleepMenu = false; MusicPlayer.sleep(ctx, 0) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (on) Ui.Brand else Ui.Sub, tween(180), label = "ti")
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(24.dp))
        if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(CircleShape).background(Ui.Brand))
    }
}

@Composable
private fun QueueList(ps: PlayState) {
    val ctx = LocalContext.current
    val st = rememberLazyListState(initialFirstVisibleItemIndex = (ps.index - 1).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxSize(), state = st) {
        itemsIndexed(ps.queue, key = { i, t -> "$i" + t.id }) { i, t ->
            val cur = i == ps.index
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Tok.RButton)).background(if (cur) Ui.BrandSoft else Color.Transparent)
                    .clickable { MusicPlayer.jump(ctx, i) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetImage(t.art, Modifier.size(40.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(t.title, color = if (cur) Ui.Brand else Ui.Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = Ui.Sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (cur) EqBars(ps.playing, Ui.Brand)
                else Box(Modifier.size(36.dp).clip(CircleShape).clickable { MusicPlayer.removeAt(ctx, i) }, contentAlignment = Alignment.Center) {
                    Icon(Ic.Close, "Bỏ khỏi hàng chờ", tint = Ui.Faint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
