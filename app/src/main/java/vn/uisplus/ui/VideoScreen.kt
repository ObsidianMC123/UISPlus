package vn.uisplus.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import vn.uisplus.music.MusicPlayer
import vn.uisplus.music.Track
import vn.uisplus.music.VideoInfo
import vn.uisplus.music.VideoQ
import vn.uisplus.music.Yt
import vn.uisplus.ui.theme.Tok
import vn.uisplus.ui.theme.Ui

/**
 * v1.8: xem video kieu YouTube. Mo tu bat ky dau bang [VideoUi.open]; ve de len tren cung
 * trong HomeScreen ([VideoHost]). Dung ExoPlayer rieng (khong qua MusicService) de co hinh;
 * khi mo video thi nhac nen tu dung (mat audio focus).
 */
object VideoUi {
    var current by mutableStateOf<Track?>(null)
    /** Vi tri bat dau (ms) - dung khi chuyen tu nghe nhac sang xem video. */
    var startMs by mutableStateOf(0L)
    var full by mutableStateOf(false)

    fun open(t: Track, startAt: Long = 0L) {
        startMs = startAt
        current = t
    }

    fun close() {
        full = false
        current = null
    }
}

/** Gioi han chat luong mac dinh (tiet kiem 4G): 720p. */
private const val DEFAULT_MAX_H = 720

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun fmtCount(n: Long): String = when {
    n < 0 -> ""
    n >= 1_000_000_000 -> "%.1f tỷ".format(n / 1e9).replace(".0 ", " ")
    n >= 1_000_000 -> "%.1f Tr".format(n / 1e6).replace(".0 ", " ")
    n >= 1_000 -> "%.0f N".format(n / 1e3)
    else -> n.toString()
}

@Composable
fun VideoHost() {
    AnimatedVisibility(
        VideoUi.current != null,
        enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(260, easing = Tok.EmphasizedAccel)) { it } + fadeOut(tween(220)),
    ) {
        val t = VideoUi.current
        if (t != null) VideoScreen(t)
    }
}

@Composable
private fun VideoScreen(track: Track) {
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.findActivity() }
    val full = VideoUi.full

    val player = remember {
        ExoPlayer.Builder(ctx)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    val http = remember { OkHttpDataSource.Factory(Yt.client).setUserAgent(Yt.UA) }

    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableStateOf<VideoQ?>(null) }
    var qMenu by remember { mutableStateOf(false) }
    var descOpen by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<Track?>(null) }
    var reloadAt by remember { mutableStateOf<Long?>(null) }

    fun sourceFor(v: VideoInfo, q: VideoQ): MediaSource {
        val f = ProgressiveMediaSource.Factory(http)
        val video = f.createMediaSource(MediaItem.fromUri(q.url))
        val a = v.audioUrl
        return if (q.muxed || a == null) video
        else MergingMediaSource(true, true, video, f.createMediaSource(MediaItem.fromUri(a)))
    }

    fun play(v: VideoInfo, q: VideoQ, pos: Long, autoPlay: Boolean = true) {
        quality = q
        player.setMediaSource(sourceFor(v, q), pos)
        player.prepare()
        player.playWhenReady = autoPlay
    }

    // nhac nen dang phat -> dung (audio focus cung lam viec nay, day la cho chac)
    LaunchedEffect(Unit) { MusicPlayer.pause(ctx) }

    // tai thong tin + phat khi doi video
    LaunchedEffect(track.id) {
        info = null; err = null; quality = null; ended = false; descOpen = false
        player.stop()
        val r = withContext(Dispatchers.IO) { runCatching { Yt.video(track.id) } }
        r.onSuccess { v ->
            info = v
            when {
                v.live -> err = "Video đang phát trực tiếp – bản này chưa hỗ trợ xem live."
                v.qualities.isEmpty() -> err = "Không lấy được luồng video này."
                else -> {
                    val q = v.qualities.firstOrNull { it.height <= DEFAULT_MAX_H } ?: v.qualities.last()
                    play(v, q, VideoUi.startMs)
                    VideoUi.startMs = 0
                }
            }
        }.onFailure { err = "Không mở được video: " + (it.message ?: it.javaClass.simpleName).take(90) }
    }

    // loi luong (403 / link het han) -> lay lai link 1 lan
    DisposableEffect(player) {
        var retried = false
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val v = info; val q = quality
                if (!retried && v != null && q != null) {
                    retried = true
                    val pos = player.currentPosition
                    Yt.invalidate(v.track.id)
                    err = null
                    // lay lai bang coroutine o LaunchedEffect duoi (dat co)
                    reloadAt = pos
                } else err = "Lỗi phát video: " + error.errorCodeName.removePrefix("ERROR_CODE_")
            }
            override fun onPlaybackStateChanged(state: Int) {
                ended = state == Player.STATE_ENDED
                if (state == Player.STATE_READY) retried = false
            }
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            player.release()
        }
    }
    LaunchedEffect(reloadAt) {
        val pos = reloadAt ?: return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { runCatching { Yt.video(track.id) } }
        reloadAt = null
        r.onSuccess { v ->
            info = v
            val old = quality
            val q = v.qualities.firstOrNull { it.height == old?.height } ?: v.qualities.firstOrNull { it.height <= DEFAULT_MAX_H } ?: v.qualities.lastOrNull()
            if (q != null) play(v, q, pos) else err = "Không lấy lại được luồng video."
        }.onFailure { err = "Mất kết nối khi tải video." }
    }

    // het video -> tu phat video lien quan dau tien (giong YouTube)
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        val next = info?.related?.firstOrNull() ?: return@LaunchedEffect
        delay(1500)
        if (ended && VideoUi.current?.id == track.id) VideoUi.open(next)
    }

    // ra nen -> tam dung (muon nghe nen thi bam "Nghe nen")
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // toan man hinh: xoay ngang + an thanh he thong
    LaunchedEffect(full) {
        val a = act ?: return@LaunchedEffect
        val ic = WindowCompat.getInsetsController(a.window, a.window.decorView)
        if (full) {
            a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ic.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ic.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            VideoUi.full = false
            act?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { if (VideoUi.full) VideoUi.full = false else VideoUi.close() }

    Column(
        Modifier.fillMaxSize().background(if (full) Color.Black else Ui.Bg)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .then(if (full) Modifier else Modifier.statusBarsPadding()),
    ) {
        // ---------- khung video (cung 1 AndroidView cho ca 2 che do -> khong tao lai) ----------
        Box(
            (if (full) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        this.player = player
                        useController = true
                        keepScreenOn = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setFullscreenButtonClickListener { VideoUi.full = it }
                    }
                },
                update = { it.setFullscreenButtonState(full) },
                modifier = Modifier.fillMaxSize(),
            )
            if (info == null && err == null) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
            if (!full) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp).size(38.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)).clickable { VideoUi.close() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.Down, "Đóng video", tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
        }

        if (!full) {
            val v = info
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                        Text(
                            v?.track?.title ?: track.title, color = Ui.Ink, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(
                            v?.views?.takeIf { it >= 0 }?.let { fmtCount(it) + " lượt xem" },
                            v?.uploaded?.ifBlank { null },
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, color = Ui.Sub, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        err?.let { Text(it, color = Ui.Brand, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetImage(v?.channelThumb ?: "", Modifier.size(38.dp), CircleShape, maxPx = 120)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(v?.track?.artist ?: track.artist, color = Ui.Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if ((v?.subscribers ?: -1) >= 0) Text(fmtCount(v!!.subscribers) + " người đăng ký", color = Ui.Sub, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Chip(Ic.Headphones, "Nghe nền") {
                            val tr = v?.track ?: track
                            val pos = player.currentPosition
                            MusicPlayer.playTracks(ctx, listOf(tr), 0, startMs = pos)
                            VideoUi.close()
                        }
                        Chip(Ic.PlaylistAdd, "Lưu") { addTo = v?.track ?: track }
                        Box {
                            Chip(Ic.Video, quality?.label ?: "Chất lượng") { if (v != null && v.qualities.isNotEmpty()) qMenu = true }
                            DropdownMenu(qMenu, onDismissRequest = { qMenu = false }, containerColor = Ui.Card) {
                                v?.qualities?.forEach { q ->
                                    DropdownMenuItem(
                                        text = { Text(q.label, color = if (q == quality) Ui.Brand else Ui.Ink, fontWeight = if (q == quality) FontWeight.SemiBold else FontWeight.Normal) },
                                        onClick = {
                                            qMenu = false
                                            if (q != quality) play(v, q, player.currentPosition, player.playWhenReady)
                                        },
                                    )
                                }
                            }
                        }
                        Chip(Ic.Share, "Chia sẻ") {
                            val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/" + track.id)
                            ctx.startActivity(Intent.createChooser(i, "Chia sẻ video"))
                        }
                        Chip(Ic.Link, "Mở YouTube") {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + track.id))) }
                        }
                    }
                }
                if (v != null && v.description.isNotBlank()) {
                    item {
                        Column(
                            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(Tok.RCard)).background(Ui.Soft)
                                .clickable { descOpen = !descOpen }.padding(12.dp).animateContentSize(),
                        ) {
                            Text(
                                htmlToText(v.description), color = Ui.Ink, style = MaterialTheme.typography.bodySmall,
                                maxLines = if (descOpen) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                            )
                            Text(if (descOpen) "Ẩn bớt" else "Xem thêm", color = Ui.Sub, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                if (v != null && v.related.isNotEmpty()) {
                    item {
                        Text("Tiếp theo", color = Ui.Ink, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp))
                    }
                    items(v.related, key = { "rv" + it.id }) { r -> RelatedRow(r) { VideoUi.open(r) } }
                }
            }
        }
    }

    addTo?.let { t ->
        AddToPlaylistDialog(t, onDone = { m -> if (m != null) android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_SHORT).show() }) { addTo = null }
    }
}

/** Mo ta YouTube tra ve HTML (<br>, <a>) -> chu thuong. */
private fun htmlToText(s: String): String =
    if (!s.contains('<')) s
    else androidx.core.text.HtmlCompat.fromHtml(s, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

@Composable
private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Ui.Soft).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Ui.Ink, modifier = Modifier.size(18.dp))
        Text(label, color = Ui.Ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun RelatedRow(t: Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(168.dp).aspectRatio(16f / 9f)) {
            NetImage(t.art, Modifier.fillMaxSize(), RoundedCornerShape(10.dp), maxPx = 320)
            if (t.dur > 0) {
                Text(
                    fmtDur(t.dur * 1000), color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(t.title, color = Ui.Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = Ui.Sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
