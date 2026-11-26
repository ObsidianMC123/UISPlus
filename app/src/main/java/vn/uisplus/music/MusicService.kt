package vn.uisplus.music

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
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
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object { const val SCHEME = "ytm" }
}
