package vn.uisplus.music

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Track <-> MediaItem (uri "ytm://<id>", link that giai luoi trong MusicService). */
internal fun Track.toItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(Uri.parse("${MusicService.SCHEME}://$id"))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(Uri.parse(art))
            .setExtras(Bundle().apply { putLong("dur", dur); putString("thumb", thumb) })
            .build(),
    )
    .build()

internal fun MediaItem.toTrack(): Track {
    val m = mediaMetadata
    return Track(
        id = mediaId,
        title = m.title?.toString() ?: "",
        artist = m.artist?.toString() ?: "",
        dur = m.extras?.getLong("dur") ?: 0,
        thumb = m.extras?.getString("thumb") ?: (m.artworkUri?.toString() ?: ""),
    )
}

/**
 * v1.9: nho hang cho + bai dang nghe + vi tri de tat app / tat may mo lai nghe tiep.
 *  - hang cho (co the toi 1000 bai) ghi vao filesDir/music/last_queue.json CHI khi hang cho doi;
 *  - bai/vi tri ghi vao SharedPreferences "music_last" (re, ghi moi 5 s khi dang phat).
 */
object LastSession {
    @Serializable
    private data class Queue(val tracks: List<Track> = emptyList())

    data class Saved(val tracks: List<Track>, val index: Int, val posMs: Long, val shuffle: Boolean, val repeat: Int, val at: Long)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private const val SP = "music_last"

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "music/last_queue.json")
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    /** Co phien cu de nghe tiep khong (doc nhanh, khong parse hang cho). */
    fun has(ctx: Context): Boolean = sp(ctx).getString("id", null) != null && file(ctx).length() > 2

    /** Ghi lai hang cho (goi khi timeline doi). Rong -> xoa phien. */
    @Synchronized
    fun saveQueue(ctx: Context, tracks: List<Track>) {
        val f = file(ctx)
        if (tracks.isEmpty()) {
            f.delete()
            sp(ctx).edit().clear().apply()
            return
        }
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "last_queue.tmp")
        tmp.writeText(json.encodeToString(Queue.serializer(), Queue(tracks)))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /** Ghi bai dang nghe + vi tri (re, goi thuong xuyen). */
    fun savePos(ctx: Context, index: Int, id: String, posMs: Long, shuffle: Boolean, repeat: Int) {
        sp(ctx).edit()
            .putInt("index", index).putString("id", id).putLong("pos", posMs.coerceAtLeast(0))
            .putBoolean("shuffle", shuffle).putInt("repeat", repeat).putLong("at", System.currentTimeMillis())
            .apply()
    }

    fun savePlayer(ctx: Context, p: Player, queueChanged: Boolean) {
        val n = p.mediaItemCount
        if (queueChanged) saveQueue(ctx, (0 until n).map { p.getMediaItemAt(it).toTrack() })
        if (n == 0) return
        val id = p.currentMediaItem?.mediaId ?: return
        // het ca hang cho -> lan sau bat dau lai tu dau bai cuoi
        val pos = if (p.playbackState == Player.STATE_ENDED) 0L else p.currentPosition
        savePos(ctx, p.currentMediaItemIndex, id, pos, p.shuffleModeEnabled, p.repeatMode)
    }

    @Synchronized
    fun load(ctx: Context): Saved? {
        val s = sp(ctx)
        val id = s.getString("id", null) ?: return null
        val f = file(ctx)
        if (!f.exists()) return null
        val tracks = runCatching { json.decodeFromString(Queue.serializer(), f.readText()).tracks }.getOrNull()
        if (tracks.isNullOrEmpty()) return null
        // uu tien khop theo id (phong khi index lech), roi moi toi index da luu
        val saved = s.getInt("index", 0)
        val idx = when {
            tracks.getOrNull(saved)?.id == id -> saved
            else -> tracks.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: saved.coerceIn(0, tracks.lastIndex)
        }
        return Saved(
            tracks, idx, s.getLong("pos", 0L), s.getBoolean("shuffle", false),
            s.getInt("repeat", Player.REPEAT_MODE_OFF), s.getLong("at", 0L),
        )
    }
}
