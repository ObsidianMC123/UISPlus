package vn.uisplus.music

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import vn.uisplus.MainActivity

/**
 * v1.7: dich vu phat nhac nen (Media3). MediaItem co uri "ytm://<videoId>";
 * link googlevideo that duoc giai LUOI khi ExoPlayer can tai (ResolvingDataSource),
 * nen hang cho 500 bai van khong ton thoi gian giai ma truoc.
 */
@UnstableApi
class MusicService : MediaSessionService() {
    private var session: MediaSession? = null
    private var retries = 0

    override fun onCreate() {
        super.onCreate()
        val http = OkHttpDataSource.Factory(Yt.client).setUserAgent(Yt.UA)
        val resolving = ResolvingDataSource.Factory(http) { spec ->
            val uri = spec.uri
            if (uri.scheme == SCHEME) {
                val id = uri.host ?: uri.schemeSpecificPart.trimStart('/')
                spec.withUri(Uri.parse(Yt.audio(id).url))
            } else spec
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { retries = 0 }
            override fun onPlayerError(error: PlaybackException) {
                // Link het han / 403 -> giai lai link 1 lan roi phat tiep tu cho cu
                val id = player.currentMediaItem?.mediaId ?: return
                val http403 = generateSequence(error.cause) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                if (retries < 2 && (http403 != null || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                ) {
                    retries++
                    Yt.invalidate(id)
                    val pos = player.currentPosition
                    player.seekTo(player.currentMediaItemIndex, pos)
                    player.prepare()
                    player.play()
                }
            }
        })
        val open = PendingIntent.getActivity(
            this, 7001,
            Intent(this, MainActivity::class.java).putExtra("tab", "music")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // v1.9: mo lai app/may -> nap hang cho + bai + vi tri cua lan nghe truoc (KHONG tu phat;
        // chua prepare nen khong ton mang, bam phat moi giai link)
        runCatching { LastSession.load(this) }.getOrNull()?.let { s ->
            player.setMediaItems(s.tracks.map { it.toItem() }, s.index, s.posMs)
            player.shuffleModeEnabled = s.shuffle
            player.repeatMode = s.repeat
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                val queue = events.contains(Player.EVENT_TIMELINE_CHANGED)
                if (queue || events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                    )
                ) save(queue)
            }
        })
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // Android 13+: the "tiep tuc nghe" tren khung media / tai nghe bluetooth khi app da tat
                override fun onPlaybackResumption(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val s = LastSession.load(this@MusicService)
                        ?: return Futures.immediateFailedFuture(UnsupportedOperationException("chua co phien"))
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(s.tracks.map { it.toItem() }, s.index, s.posMs),
                    )
                }
            })
            .build()
        ticker.postDelayed(tick, 5_000)
    }

    // ---------------------------------------------------------------- v1.9: luu phien nghe
    private val io = Executors.newSingleThreadExecutor()
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (session?.player?.isPlaying == true) save(false)
            ticker.postDelayed(this, 5_000)
        }
    }

    /** Doc trang thai tren main thread, ghi file o luong phu. */
    private fun save(queueChanged: Boolean) {
        val p = session?.player ?: return
        val n = p.mediaItemCount
        val tracks = if (queueChanged) (0 until n).map { p.getMediaItemAt(it).toTrack() } else null
        val id = p.currentMediaItem?.mediaId
        val idx = p.currentMediaItemIndex
        val pos = if (p.playbackState == Player.STATE_ENDED) 0L else p.currentPosition
        val sh = p.shuffleModeEnabled
        val rp = p.repeatMode
        val app = applicationContext
        io.execute {
            runCatching {
                if (tracks != null) LastSession.saveQueue(app, tracks)
                if (n > 0 && id != null) LastSession.savePos(app, idx, id, pos, sh, rp)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        save(false)
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) stopSelf()
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tick)
        save(false)
        io.shutdown()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object { const val SCHEME = "ytm" }
}
