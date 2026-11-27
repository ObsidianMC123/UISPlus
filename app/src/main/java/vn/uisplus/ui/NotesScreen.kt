package vn.uisplus.ui

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.uisplus.data.AppData
import vn.uisplus.data.ClassSession
import vn.uisplus.data.VN_ZONE
import vn.uisplus.notes.Note
import vn.uisplus.notes.NoteStore
import vn.uisplus.notes.Thumbs
import vn.uisplus.ui.theme.Tok
import vn.uisplus.ui.theme.Ui
import vn.uisplus.ui.theme.subjectColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Trang thai editor ghi bai - ve tren cung HomeScreen (giong VideoHost). */
object NotesUi {
    var editing by mutableStateOf<Note?>(null)
    var autoCamera by mutableStateOf(false)

    fun open(n: Note, camera: Boolean = false) { autoCamera = camera; editing = n }

    /** Nut "Chup bang" o popup: tao ghi bai cho mon dang hoc (neu co) roi mo camera ngay. */
    fun quickShot(d: AppData) {
        val s = currentSession(d)
        open(if (s != null) NoteStore.blank(s.maMon, s.tenMon, s.epochDay, s.key) else NoteStore.blank(), camera = true)
    }

    /** Tu the buoi hoc: co ghi bai cua buoi nay thi mo ban moi nhat, chua co thi tao moi. */
    fun openForSession(ctx: Context, s: ClassSession, camera: Boolean) {
        NoteStore.load(ctx)
        val old = NoteStore.forSession(s.maMon, s.epochDay).firstOrNull()
        open(old ?: NoteStore.blank(s.maMon, s.tenMon, s.epochDay, s.key), camera)
    }
}

private data class Subj(val ma: String, val ten: String)

private fun subjectsOf(d: AppData, notes: List<Note>): List<Subj> {
    val cur = d.sessions.filter { it.hocKy == d.currentHocKy }.ifEmpty { d.sessions }
    val base = cur.distinctBy { it.maMon }.map { Subj(it.maMon, it.tenMon) }
    val extra = notes.filter { it.maMon.isNotBlank() && base.none { b -> b.ma == it.maMon } }
        .distinctBy { it.maMon }.map { Subj(it.maMon, it.tenMon) }
    return base + extra
}

/** Buoi dang hoc / vua hoc trong hom nay (de "Chup bang" tu gan mon). */
private fun currentSession(d: AppData): ClassSession? {
    val today = LocalDate.now(VN_ZONE)
    val nowMin = LocalTime.now(VN_ZONE).let { it.hour * 60 + it.minute }
    return d.sessionsOn(today.toEpochDay()).filter { !it.nghiDay && it.startMin - 15 <= nowMin }.lastOrNull()
}

// ------------------------------------------------------------------ man danh sach

@Composable
fun NotesScreen(d: AppData) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { NoteStore.load(ctx) } }
    val notes by NoteStore.notes.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val subjects = remember(d, notes) { subjectsOf(d, notes) }
    val cur = remember(d) { currentSession(d) }
    val shown = if (filter == null) notes else notes.filter { it.maMon == filter }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun newNote(camera: Boolean) {
        val f = filter
        val n = when {
            f != null -> NoteStore.blank(f, subjects.firstOrNull { it.ma == f }?.ten ?: "")
            cur != null -> NoteStore.blank(cur.maMon, cur.tenMon, cur.epochDay, cur.key)
            else -> NoteStore.blank()
        }
        NotesUi.open(n, camera)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Ui.Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + NAV_BAR_HEIGHT + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp, start = 4.dp)) {
                Text("Ghi bài", color = Ui.Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (cur != null) "Đang học: ${cur.tenMon}" else "Chụp bảng và soạn ghi chú theo từng môn",
                    color = Ui.Sub, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard(Ic.Camera, "Chụp bảng", "Mở camera ngay", true, Modifier.weight(1f)) { newNote(true) }
                ActionCard(Ic.Note, "Soạn ghi chú", "Chữ + ảnh", false, Modifier.weight(1f)) { newNote(false) }
            }
        }
        if (subjects.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                item { Chip("Tất cả · ${notes.size}", filter == null, Ui.Brand) { filter = null } }
                items(subjects, key = { it.ma }) { s ->
                    val c = notes.count { it.maMon == s.ma }
                    Chip(s.ten + if (c > 0) " · $c" else "", filter == s.ma, subjectColor(s.ma)) { filter = if (filter == s.ma) null else s.ma }
                }
            }
        }
        if (shown.isEmpty()) item {
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Ic.Note, null, tint = Ui.Faint, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text("Chưa có ghi bài nào", color = Ui.Sub, style = MaterialTheme.typography.bodyLarge)
                Text("Bấm \"Chụp bảng\" khi đang học để lưu ảnh bảng", color = Ui.Faint, style = MaterialTheme.typography.bodySmall)
            }
        }
        items(shown, key = { it.id }) { n -> NoteCard(n) { NotesUi.open(n) } }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, sub: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Tok.RCard)
    Row(
        modifier.clip(shape).background(if (primary) Ui.Brand else Ui.Card)
            .border(1.dp, if (primary) Color.Transparent else Ui.Line, shape)
            .bounceClick(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) Color.White else Ui.Brand, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = if (primary) Color.White else Ui.Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(sub, color = if (primary) Color.White.copy(alpha = 0.8f) else Ui.Sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        modifier = Modifier.clip(shape).background(if (selected) color.copy(alpha = 0.16f) else Ui.Card)
            .border(1.dp, if (selected) color.copy(alpha = 0.6f) else Ui.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Ui.Ink else Ui.Sub,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
}

@Composable
private fun NoteCard(n: Note, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Tok.RCard)
    val c = if (n.maMon.isBlank()) Ui.Faint else subjectColor(n.maMon)
    Row(
        Modifier.fillMaxWidth().elev(2.dp, shape).clip(shape).background(Ui.Card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(52.dp).clip(RoundedCornerShape(2.dp)).background(c))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                listOf(n.tenMon.ifBlank { "Không gắn môn" }, n.date.format(Note.DM)).joinToString(" · "),
                color = Ui.Sub, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(n.heading, color = Ui.Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val preview = n.body.trim().let { if (n.title.isBlank()) it.lineSequence().drop(1).joinToString(" ") else it }.replace('\n', ' ').trim()
            Text(
                preview.ifBlank { if (n.photos.isNotEmpty()) "${n.photos.size} ảnh" else "" },
                color = Ui.Faint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (n.photos.isNotEmpty()) {
            Box {
                LocalPhoto(n.photos.first(), Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), 240)
                if (n.photos.size > 1) Text(
                    "+${n.photos.size - 1}",
                    Modifier.align(Alignment.BottomEnd).padding(3.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 5.dp, vertical = 1.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Anh trong kho ghi bai, giai ma nen o IO. */
@Composable
fun LocalPhoto(name: String, modifier: Modifier, maxPx: Int, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val bm by produceState<android.graphics.Bitmap?>(null, name, maxPx) {
        value = withContext(Dispatchers.IO) { runCatching { Thumbs.load(NoteStore.photoFile(ctx, name), maxPx) }.getOrNull() }
    }
    Box(modifier.background(Ui.Soft), contentAlignment = Alignment.Center) {
        val b = bm
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = contentScale)
    }
}

// ------------------------------------------------------------------ editor

/** Ve tren cung HomeScreen: editor ghi bai toan man hinh. */
@Composable
fun NotesEditorHost(d: AppData) {
    val n = NotesUi.editing
    AnimatedVisibility(
        visible = n != null,
        enter = fadeIn(tween(180)) + slideInVertically(tween(280, easing = Tok.EmphasizedDecel)) { it / 8 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 10 },
    ) {
        // giu note cuoi de anim ra khong bi trong
        val last = remember { mutableStateOf<Note?>(null) }
        if (n != null) last.value = n
        last.value?.let { note ->
            androidx.compose.runtime.key(note.id) { NoteEditor(d, note, NotesUi.autoCamera) { NotesUi.editing = null; NotesUi.autoCamera = false } }
        }
    }
}

@Composable
private fun NoteEditor(d: AppData, start: Note, autoCamera: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by NoteStore.notes.collectAsStateWithLifecycle()
    val subjects = remember(d) { subjectsOf(d, all) }
    var n by remember { mutableStateOf(start) }
    var saved by remember { mutableStateOf(start) }
    var title by remember { mutableStateOf(TextFieldValue(start.title)) }
    var body by remember { mutableStateOf(TextFieldValue(start.body)) }
    var pendingShot by rememberSaveable { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<Int?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    fun current() = n.copy(title = title.text, body = body.text)
    fun save(sync: Boolean = false) {
        val c = current()
        if (c == saved) return
        val out = c.copy(updatedAt = System.currentTimeMillis())
        saved = c
        if (sync) NoteStore.upsert(ctx, out) else scope.launch(Dispatchers.IO) { NoteStore.upsert(ctx, out) }
    }
    fun close() { save(sync = true); onClose() }

    // tu luu sau 0,8 s khong go
    LaunchedEffect(n, title.text, body.text) { delay(800); save() }

    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingShot
        pendingShot = null
        if (name != null) {
            val f = NoteStore.photoFile(ctx, name)
            if (ok && f.length() > 0) { n = n.copy(photos = n.photos + name); save() } else NoteStore.deletePhoto(ctx, name)
        }
    }
    fun shoot() {
        val name = NoteStore.newPhotoName()
        val f = NoteStore.photoFile(ctx, name)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        pendingShot = name
        try { takePic.launch(uri) } catch (_: ActivityNotFoundException) {
            pendingShot = null
            Toast.makeText(ctx, "Máy không có app camera", Toast.LENGTH_SHORT).show()
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val names = withContext(Dispatchers.IO) { uris.mapNotNull { NoteStore.importUri(ctx, it) } }
            importing = false
            n = n.copy(photos = n.photos + names)
            save()
        }
    }
    LaunchedEffect(Unit) { if (autoCamera) shoot() }

    BackHandler(viewer == null) { close() }

    val c = if (n.maMon.isBlank()) Ui.Brand else subjectColor(n.maMon)
    Box(Modifier.fillMaxSize().background(Ui.Bg).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // thanh tren
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Ic.Left, "Quay lại") { close() }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(if (start.isEmpty && start.createdAt == start.updatedAt) "Ghi bài mới" else "Ghi bài", color = Ui.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (current() == saved) "Đã lưu trên máy" else "Đang lưu…",
                        color = Ui.Faint, style = MaterialTheme.typography.labelSmall,
                    )
                }
                RoundIcon(Ic.Share, "Chia sẻ") { save(sync = true); shareNote(ctx, current()) }
                RoundIcon(Ic.Delete, "Xóa") { askDelete = true }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // mon + ngay
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    item {
                        Chip("Ngày " + n.date.format(Note.DMY), false, Ui.Brand) {
                            val dt = n.date
                            DatePickerDialog(ctx, { _, y, m, day -> n = n.copy(epochDay = LocalDate.of(y, m + 1, day).toEpochDay(), sessionKey = "") }, dt.year, dt.monthValue - 1, dt.dayOfMonth).show()
                        }
                    }
                    items(subjects, key = { it.ma }) { s ->
                        Chip(s.ten, n.maMon == s.ma, subjectColor(s.ma)) {
                            n = if (n.maMon == s.ma) n.copy(maMon = "", tenMon = "", sessionKey = "") else n.copy(maMon = s.ma, tenMon = s.ten, sessionKey = "")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    title, { title = it },
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = Ui.Ink, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(c),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box { if (title.text.isEmpty()) Text("Tiêu đề (vd: Chương 2 – Giới hạn)", color = Ui.Faint, style = MaterialTheme.typography.headlineSmall); inner() }
                    },
                )
                // anh
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(Ic.Camera, "Chụp bảng", Modifier.weight(1f)) { shoot() }
                    SmallAction(Ic.Photo, if (importing) "Đang thêm…" else "Từ thư viện", Modifier.weight(1f)) {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                if (n.photos.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(n.photos.size) { i ->
                            LocalPhoto(
                                n.photos[i],
                                Modifier.size(width = 132.dp, height = 100.dp).clip(RoundedCornerShape(12.dp)).clickable { viewer = i },
                                360,
                            )
                        }
                    }
                }
                // cong cu soan nhanh
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fun insert(s: String) {
                        val t = body.text
                        val at = body.selection.start.coerceIn(0, t.length)
                        val lineStart = t.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (at == 0) 0 else it + 1 }
                        val pre = if (s.endsWith(" ") && at != lineStart) "\n" else ""
                        val ins = pre + s
                        body = TextFieldValue(t.substring(0, at) + ins + t.substring(at), TextRange(at + ins.length))
                    }
                    Chip("• Ý", false, c) { insert("• ") }
                    Chip("☐ Việc", false, c) { insert("☐ ") }
                    Chip("★ Quan trọng", false, c) { insert("★ ") }
                    Chip("Giờ", false, c) { insert("[" + LocalTime.now(VN_ZONE).format(DateTimeFormatter.ofPattern("HH:mm")) + "] ") }
                }
                BasicTextField(
                    body, { body = it },
                    Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(top = 10.dp, bottom = 24.dp),
                    textStyle = TextStyle(color = Ui.Ink, fontSize = 16.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(c),
                    decorationBox = { inner ->
                        Box {
                            if (body.text.isEmpty()) Text(
                                "Soạn ghi chú bài học…\nDùng • ☐ ★ ở trên để gạch đầu dòng, việc cần làm, ý quan trọng.",
                                color = Ui.Faint, fontSize = 16.sp, lineHeight = 24.sp,
                            )
                            inner()
                        }
                    },
                )
            }
        }
    }

    viewer?.let { i0 ->
        PhotoViewer(
            n.photos, i0,
            onDelete = { name ->
                n = n.copy(photos = n.photos - name)
                NoteStore.deletePhoto(ctx, name)
                save()
                if (n.photos.isEmpty()) viewer = null else viewer = (viewer ?: 0).coerceAtMost(n.photos.size - 1)
            },
            onShare = { name -> sharePhotos(ctx, listOf(name), "") },
            onIndex = { viewer = it },
            onDismiss = { viewer = null },
        )
    }

    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        containerColor = Ui.Card,
        title = { Text("Xóa ghi bài này?", color = Ui.Ink) },
        text = { Text("Xóa cả ${n.photos.size} ảnh đã chụp. Không hoàn tác được.", color = Ui.Sub) },
        confirmButton = {
            TextButton({
                askDelete = false
                val id = n.id
                scope.launch(Dispatchers.IO) { NoteStore.delete(ctx, id) }
                n.photos.forEach { Thumbs.evict(it) }
                saved = current() // khong luu lai nua
                onClose()
            }) { Text("Xóa", color = Ui.Brand) }
        },
        dismissButton = { TextButton({ askDelete = false }) { Text("Hủy", color = Ui.Sub) } },
    )
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Ui.Ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SmallAction(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.clip(shape).background(Ui.Card).border(1.dp, Ui.Line, shape).bounceClick(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Ui.Brand, modifier = Modifier.size(20.dp))
        Text(text, Modifier.padding(start = 8.dp), color = Ui.Ink, style = MaterialTheme.typography.labelLarge)
    }
}

// ------------------------------------------------------------------ xem anh (phong to de doc bang)

@Composable
private fun PhotoViewer(
    photos: List<String>, index: Int,
    onDelete: (String) -> Unit, onShare: (String) -> Unit, onIndex: (Int) -> Unit, onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) return
    val i = index.coerceIn(0, photos.size - 1)
    val name = photos[i]
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember(name) { mutableStateOf(1f) }
        var ox by remember(name) { mutableStateOf(0f) }
        var oy by remember(name) { mutableStateOf(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            LocalPhoto(
                name,
                Modifier.fillMaxSize()
                    .pointerInput(name) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1.05f) { scale = 1f; ox = 0f; oy = 0f } else scale = 2.5f
                        })
                    }
                    .pointerInput(name, 1) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            if (scale <= 1.01f) { ox = 0f; oy = 0f } else { ox += pan.x; oy += pan.y }
                        }
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = ox; translationY = oy },
                2560,
                ContentScale.Fit,
            )
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerBtn(Ic.Close) { onDismiss() }
                Text("${i + 1}/${photos.size}", Modifier.weight(1f).padding(start = 8.dp), color = Color.White, style = MaterialTheme.typography.titleSmall)
                ViewerBtn(Ic.Share) { onShare(name) }
                ViewerBtn(Ic.Delete) { onDelete(name) }
            }
            if (photos.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ViewerBtn(Ic.Left, enabled = i > 0) { onIndex(i - 1) }
                    ViewerBtn(Ic.Right, enabled = i < photos.size - 1) { onIndex(i + 1) }
                }
            }
        }
    }
}

@Composable
private fun ViewerBtn(icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
    }
}

// ------------------------------------------------------------------ chia se

private fun uriOf(ctx: Context, name: String): Uri =
    FileProvider.getUriForFile(ctx, ctx.packageName + ".files", NoteStore.photoFile(ctx, name))

fun shareNote(ctx: Context, n: Note) {
    sharePhotos(ctx, n.photos, NoteStore.shareText(n))
}

private fun sharePhotos(ctx: Context, names: List<String>, text: String) {
    val i = when {
        names.isEmpty() -> Intent(Intent.ACTION_SEND).setType("text/plain")
        names.size == 1 -> Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uriOf(ctx, names[0]))
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(names.map { uriOf(ctx, it) }))
    }
    if (text.isNotBlank()) i.putExtra(Intent.EXTRA_TEXT, text)
    if (names.isNotEmpty()) {
        val uris = names.map { uriOf(ctx, it) }
        val clip = ClipData.newRawUri("", uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        i.clipData = clip
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(Intent.createChooser(i, "Chia sẻ ghi bài").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "Không có app để chia sẻ", Toast.LENGTH_SHORT).show()
    }
}
