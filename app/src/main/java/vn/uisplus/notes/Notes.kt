package vn.uisplus.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import android.util.LruCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * v1.10: Ghi bai theo mon/buoi hoc - chup anh bang + soan ghi chu.
 * Luu offline: filesDir/notes/notes.json (AtomicFile) + anh o filesDir/notes/img/.
 */
@Serializable
data class Note(
    val id: String,
    val maMon: String = "",
    val tenMon: String = "",
    val epochDay: Long = 0,
    val sessionKey: String = "",
    val title: String = "",
    val body: String = "",
    val photos: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && photos.isEmpty()
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    /** Tieu de hien thi: tieu de -> dong dau noi dung -> "Ghi bai dd/MM". */
    val heading: String
        get() = title.trim().ifBlank {
            body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(80)
                ?: ("Ghi bài " + date.format(DM))
        }

    companion object {
        val DM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}

object NoteStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = ListSerializer(Note.serializer())
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    @Volatile
    private var loaded = false

    fun dir(ctx: Context): File = File(ctx.filesDir, "notes").apply { mkdirs() }
    fun imgDir(ctx: Context): File = File(dir(ctx), "img").apply { mkdirs() }
    private fun file(ctx: Context) = AtomicFile(File(dir(ctx), "notes.json"))

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val list = runCatching { json.decodeFromString(ser, file(ctx).readFully().decodeToString()) }
            .getOrDefault(emptyList())
        _notes.value = list.sortedByDescending { it.updatedAt }
        loaded = true
    }

    @Synchronized
    private fun persist(ctx: Context, list: List<Note>) {
        val f = file(ctx)
        val out = f.startWrite()
        try {
            out.write(json.encodeToString(ser, list).toByteArray())
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            throw e
        }
    }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    fun blank(maMon: String = "", tenMon: String = "", epochDay: Long = LocalDate.now().toEpochDay(), sessionKey: String = ""): Note {
        val now = System.currentTimeMillis()
        return Note(newId(), maMon, tenMon, epochDay, sessionKey, createdAt = now, updatedAt = now)
    }

    fun get(id: String): Note? = _notes.value.firstOrNull { it.id == id }

    /** Luu (them hoac thay). Ghi chu rong thi xoa khoi danh sach. */
    @Synchronized
    fun upsert(ctx: Context, n: Note) {
        load(ctx)
        if (n.isEmpty) { delete(ctx, n.id); return }
        val list = (_notes.value.filterNot { it.id == n.id } + n).sortedByDescending { it.updatedAt }
        _notes.value = list
        persist(ctx, list)
    }

    @Synchronized
    fun delete(ctx: Context, id: String) {
        load(ctx)
        val n = _notes.value.firstOrNull { it.id == id } ?: return
        n.photos.forEach { photoFile(ctx, it).delete() }
        val list = _notes.value.filterNot { it.id == id }
        _notes.value = list
        persist(ctx, list)
    }

    fun forSession(maMon: String, epochDay: Long): List<Note> =
        _notes.value.filter { it.maMon == maMon && it.epochDay == epochDay }

    fun photoFile(ctx: Context, name: String): File = File(imgDir(ctx), name)
    fun newPhotoName(): String = "p_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"

    /** Chep anh tu thu vien (content://) vao kho rieng cua app. */
    fun importUri(ctx: Context, uri: Uri): String? = runCatching {
        val name = newPhotoName()
        ctx.contentResolver.openInputStream(uri)!!.use { i -> photoFile(ctx, name).outputStream().use { i.copyTo(it) } }
        name
    }.getOrNull()

    fun deletePhoto(ctx: Context, name: String) {
        photoFile(ctx, name).delete()
        Thumbs.evict(name)
    }

    /** Van ban de chia se (Zalo/Messenger/Drive...). */
    fun shareText(n: Note): String = buildString {
        if (n.tenMon.isNotBlank()) append(n.tenMon).append(" · ")
        append(n.date.format(Note.DMY)).append('\n')
        if (n.title.isNotBlank()) append(n.title.trim()).append("\n\n")
        append(n.body.trim())
    }.trim()

    fun timeText(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(vn.uisplus.data.VN_ZONE).format(DateTimeFormatter.ofPattern("HH:mm dd/MM"))
}

/** Giai ma anh co thu nho + xoay theo EXIF (may anh Xiaomi hay ghi xoay vao EXIF). */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun evict(name: String) {
        cache.snapshot().keys.filter { it.startsWith("$name@") }.forEach { cache.remove(it) }
    }

    fun load(f: File, maxPx: Int): Bitmap? {
        val key = "${f.name}@$maxPx"
        cache.get(key)?.let { return it }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
        val bm = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val rot = runCatching {
            when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val out = if (rot == 0f) bm else Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, Matrix().apply { postRotate(rot) }, true)
        cache.put(key, out)
        return out
    }
}
