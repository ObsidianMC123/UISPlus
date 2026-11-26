package vn.uisplus.music

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class Library(val lists: List<Playlist> = emptyList(), val recent: List<String> = emptyList())

/** v1.7: thu vien nhac luu trong filesDir/music/library.json. */
object MusicStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _lists = MutableStateFlow<List<Playlist>>(emptyList())
    val lists: StateFlow<List<Playlist>> = _lists
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    /** Tu khoa tim gan day (toi da 8). */
    val recent: StateFlow<List<String>> = _recent
    @Volatile private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "music/library.json")

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (!f.exists()) return
        runCatching { json.decodeFromString<Library>(f.readText()) }.onSuccess {
            _lists.value = it.lists
            _recent.value = it.recent
        }
    }

    @Synchronized
    private fun persist(ctx: Context) {
        val f = file(ctx)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "library.tmp")
        tmp.writeText(json.encodeToString(Library.serializer(), Library(_lists.value, _recent.value)))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /** Them/cap nhat playlist (trung id thi thay the, giu vi tri). */
    fun save(ctx: Context, p: Playlist) {
        load(ctx)
        val cur = _lists.value
        _lists.value = if (cur.any { it.id == p.id }) cur.map { if (it.id == p.id) p else it } else listOf(p) + cur
        persist(ctx)
    }

    fun delete(ctx: Context, id: String) {
        load(ctx)
        _lists.value = _lists.value.filterNot { it.id == id }
        persist(ctx)
    }

    // ---------------- v1.8: danh sach tu tao ----------------

    fun create(ctx: Context, name: String, first: Track? = null): Playlist {
        val p = Playlist(
            id = "my_" + System.currentTimeMillis(),
            name = name.trim().ifBlank { "Danh sách mới" },
            tracks = listOfNotNull(first),
            at = System.currentTimeMillis(),
            local = true,
        )
        save(ctx, p)
        return p
    }

    fun rename(ctx: Context, id: String, name: String) {
        val p = get(ctx, id) ?: return
        if (name.isBlank()) return
        save(ctx, p.copy(name = name.trim()))
    }

    /** Them bai vao cuoi danh sach; false neu da co. */
    fun addTrack(ctx: Context, id: String, t: Track): Boolean {
        val p = get(ctx, id) ?: return false
        if (p.tracks.any { it.id == t.id }) return false
        save(ctx, p.copy(tracks = p.tracks + t))
        return true
    }

    fun removeTrack(ctx: Context, id: String, index: Int) {
        val p = get(ctx, id) ?: return
        if (index !in p.tracks.indices) return
        save(ctx, p.copy(tracks = p.tracks.filterIndexed { i, _ -> i != index }))
    }

    /** Doi cho bai [from] sang vi tri [to] (len/xuong). */
    fun moveTrack(ctx: Context, id: String, from: Int, to: Int) {
        val p = get(ctx, id) ?: return
        if (from !in p.tracks.indices || to !in p.tracks.indices || from == to) return
        val l = p.tracks.toMutableList()
        l.add(to, l.removeAt(from))
        save(ctx, p.copy(tracks = l))
    }

    /** Ban sao sua duoc cua 1 danh sach nhap tu YouTube. */
    fun copyAsLocal(ctx: Context, src: Playlist): Playlist {
        val p = src.copy(id = "my_" + System.currentTimeMillis(), url = "", local = true, at = System.currentTimeMillis(), owner = "")
        save(ctx, p)
        return p
    }

    fun get(ctx: Context, id: String): Playlist? {
        load(ctx)
        return _lists.value.firstOrNull { it.id == id }
    }

    fun addRecent(ctx: Context, q: String) {
        val t = q.trim()
        if (t.isBlank()) return
        load(ctx)
        _recent.value = (listOf(t) + _recent.value.filterNot { it.equals(t, true) }).take(8)
        persist(ctx)
    }

    fun clearRecent(ctx: Context) {
        load(ctx)
        _recent.value = emptyList()
        persist(ctx)
    }
}
