package vn.uisplus.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Trang thai phat cho UI. */
data class PlayState(
    val current: Track? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    /** Player.REPEAT_MODE_OFF / ONE / ALL */
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val error: String? = null,
    /** Hen gio tat nhac (epoch ms), 0 = tat; -1 = het bai hien tai. */
    val sleepAt: Long = 0,
)

/**
 * v1.7: phia UI noi voi MusicService qua MediaController. Lenh goi truoc khi ket noi xong
 * se duoc xep hang roi chay ngay khi controller san sang.
 */
object MusicPlayer {
    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null

    fun connect(ctx: Context) {
        if (future != null) return
        val app = ctx.applicationContext
        val token = SessionToken(app, ComponentName(app, MusicService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            sync()
            val q = ArrayList(pending); pending.clear()
            q.forEach { it(c) }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun with(ctx: Context, block: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) { block(c); return }
        if (c != null && !c.isConnected) { controller = null; future = null }
        pending += block
        connect(ctx)
    }

    /** Vi tri phat hien tai (ms) – UI tu hoi theo nhip. */
    fun position(): Long = controller?.currentPosition ?: 0
    fun buffered(): Long = controller?.bufferedPosition ?: 0

    fun playTracks(ctx: Context, tracks: List<Track>, start: Int = 0, shuffle: Boolean = false, startMs: Long = 0L) {
        if (tracks.isEmpty()) return
        with(ctx) { c ->
            c.shuffleModeEnabled = shuffle
            val first = if (shuffle && start == 0) tracks.indices.random() else start.coerceIn(0, tracks.lastIndex)
            c.setMediaItems(tracks.map { it.toItem() }, first, startMs.coerceAtLeast(0L))
            c.prepare()
            c.play()
        }
        _state.value = _state.value.copy(error = null)
    }

    /** Them vao cuoi hang cho (hoac phat ngay neu chua co gi). */
    fun enqueue(ctx: Context, t: Track, next: Boolean = false) = with(ctx) { c ->
        if (c.mediaItemCount == 0) { c.setMediaItem(t.toItem()); c.prepare(); c.play() }
        else if (next) c.addMediaItem(c.currentMediaItemIndex + 1, t.toItem())
        else c.addMediaItem(t.toItem())
    }

    fun toggle(ctx: Context) = with(ctx) { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
            c.play()
        }
    }
    /** v1.8: chi tam dung neu da ket noi (khong tu khoi dong service khi chua phat gi). */
    fun pause(@Suppress("UNUSED_PARAMETER") ctx: Context) { controller?.takeIf { it.isConnected && it.isPlaying }?.pause() }
    // v1.9: hang cho khoi phuc tu phien truoc chua prepare (STATE_IDLE) -> prepare khi bam
    private fun MediaController.ready() { if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare() }
    fun next(ctx: Context) = with(ctx) { it.ready(); it.seekToNext() }
    fun prev(ctx: Context) = with(ctx) { it.ready(); it.seekToPrevious() }
    fun seek(ctx: Context, ms: Long) = with(ctx) { it.seekTo(ms) }
    fun jump(ctx: Context, index: Int) = with(ctx) { it.seekTo(index, 0); it.ready(); it.play() }
    fun removeAt(ctx: Context, index: Int) = with(ctx) { it.removeMediaItem(index) }
    fun setShuffle(ctx: Context, on: Boolean) = with(ctx) { it.shuffleModeEnabled = on }
    fun cycleRepeat(ctx: Context) = with(ctx) {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun stop(ctx: Context) = with(ctx) { it.stop(); it.clearMediaItems() }

    /** Hen gio tat nhac: [minutes] > 0, 0 = huy, -1 = het bai nay. */
    fun sleep(ctx: Context, minutes: Int) {
        sleepJob?.cancel()
        when {
            minutes == 0 -> _state.value = _state.value.copy(sleepAt = 0)
            minutes < 0 -> _state.value = _state.value.copy(sleepAt = -1)
            else -> {
                val at = System.currentTimeMillis() + minutes * 60_000L
                _state.value = _state.value.copy(sleepAt = at)
                sleepJob = scope.launch {
                    delay(at - System.currentTimeMillis())
                    with(ctx) { it.pause() }
                    _state.value = _state.value.copy(sleepAt = 0)
                }
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { sync() }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Hen gio "het bai nay": chuyen bai tu dong -> dung
            if (_state.value.sleepAt == -1L && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                controller?.pause()
                _state.value = _state.value.copy(sleepAt = 0)
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = friendly(error))
        }
    }

    private fun friendly(e: PlaybackException): String {
        val msg = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
        return when {
            msg.contains("403") -> "YouTube từ chối luồng (403). Thử lại sau ít phút."
            e.errorCode in 2000..2999 -> "Mất kết nối mạng khi tải nhạc."
            msg.contains("age", true) || msg.contains("sign in", true) -> "Bài này cần đăng nhập/giới hạn tuổi, không phát được."
            else -> "Không phát được bài này: " + (e.errorCodeName.removePrefix("ERROR_CODE_"))
        }
    }

    private fun sync() {
        val c = controller ?: return
        val n = c.mediaItemCount
        val q = (0 until n).map { c.getMediaItemAt(it).toTrack() }
        val idx = c.currentMediaItemIndex
        val cur = c.currentMediaItem?.toTrack()
        _state.value = _state.value.copy(
            current = cur,
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING && c.playWhenReady,
            duration = c.duration.coerceAtLeast(0).takeIf { c.duration != androidx.media3.common.C.TIME_UNSET }
                ?: (cur?.dur ?: 0) * 1000,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            queue = q,
            index = idx,
            error = if (c.isPlaying) null else _state.value.error,
        )
    }

    // v1.9: Track.toItem()/MediaItem.toTrack() chuyen sang LastSession.kt (service dung chung)
}
