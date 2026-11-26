package vn.uisplus.music

import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Mot bai hat (luu duoc vao danh sach phat). [id] = videoId YouTube. */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String = "",
    val dur: Long = 0,
    val thumb: String = "",
) {
    val art: String get() = thumb.ifBlank { "https://i.ytimg.com/vi/$id/mqdefault.jpg" }
}

/** Danh sach phat da nhap (luu offline metadata, phat van can mang). */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val owner: String = "",
    val url: String = "",
    val thumb: String = "",
    val tracks: List<Track> = emptyList(),
    val at: Long = 0,
    /** v1.8: true = danh sach anh tu tao (sua duoc), false = nhap tu YouTube. */
    val local: Boolean = false,
)

/** v1.8: 1 muc chat luong video. [muxed] = luong co san ca tieng (khong can ghep audio). */
data class VideoQ(val label: String, val height: Int, val url: String, val muxed: Boolean)

/** v1.8: thong tin de xem 1 video (kieu trang xem YouTube). */
data class VideoInfo(
    val track: Track,
    val views: Long,
    val likes: Long,
    val uploaded: String,
    val description: String,
    val channelThumb: String,
    val subscribers: Long,
    /** cao -> thap */
    val qualities: List<VideoQ>,
    val audioUrl: String?,
    val live: Boolean,
    val hls: String?,
    val related: List<Track>,
)

/** Ket qua tim playlist (de nhap nhanh). */
data class PlaylistHit(val url: String, val name: String, val owner: String, val count: Long, val thumb: String)

/** Luong audio da giai ma. */
data class Audio(val url: String, val mime: String?, val bitrate: Int, val at: Long)

/**
 * v1.7: lop mong boc NewPipeExtractor (YouTube). Moi ham deu CHAN (blocking) ->
 * goi tu Dispatchers.IO / thread loader cua ExoPlayer.
 */
object Yt {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var inited = false
    private val yt get() = ServiceList.YouTube

    fun init() {
        if (inited) return
        synchronized(this) {
            if (inited) return
            NewPipe.init(OkDownloader(client), Localization("vi", "VN"), ContentCountry("VN"))
            inited = true
        }
    }

    // ---------------- tim kiem ----------------

    /** [songs]=true: tim tren YouTube Music (bai hat), false: video YouTube thuong. */
    fun search(query: String, songs: Boolean): List<Track> {
        init()
        val filter = if (songs) "music_songs" else "videos"
        val qh = yt.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(yt, qh)
        val out = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toTrack() }
        // YouTube Music doi khi tra rong -> lui ve video thuong
        if (songs && out.isEmpty()) return search(query, false)
        return out.distinctBy { it.id }
    }

    fun searchPlaylists(query: String): List<PlaylistHit> {
        init()
        val qh = yt.searchQHFactory.fromQuery(query, listOf("playlists"), "")
        return SearchInfo.getInfo(yt, qh).relatedItems.filterIsInstance<PlaylistInfoItem>().map {
            PlaylistHit(it.url, it.name, it.uploaderName ?: "", it.streamCount, bestThumb(it.thumbnails))
        }
    }

    // ---------------- link ----------------

    /** Link co phai danh sach phat (list=...) khong. */
    fun isPlaylistUrl(s: String): Boolean {
        val t = s.trim()
        return (t.contains("youtube.com") || t.contains("youtu.be")) && Regex("[?&]list=[\\w-]+").containsMatchIn(t)
    }

    /** Lay videoId tu link (youtu.be/x, watch?v=x, music.youtube.com, shorts/x) hoac null. */
    fun videoId(s: String): String? {
        val t = s.trim()
        Regex("(?:v=|youtu\\.be/|shorts/|embed/|live/)([\\w-]{11})").find(t)?.let { return it.groupValues[1] }
        return null
    }

    /** Chuan hoa link playlist ve dang youtube.com/playlist?list=ID (bo v=, index, si...). */
    fun normalizePlaylistUrl(s: String): String {
        val id = Regex("[?&]list=([\\w-]+)").find(s.trim())?.groupValues?.get(1) ?: return s.trim()
        return "https://www.youtube.com/playlist?list=$id"
    }

    /** Doc toan bo playlist (cong khai / khong cong khai), toi da [max] bai. */
    fun playlist(url: String, max: Int = 1000, onProgress: (Int) -> Unit = {}): Playlist {
        init()
        val u = normalizePlaylistUrl(url)
        val info = PlaylistInfo.getInfo(yt, u)
        val items = ArrayList<StreamInfoItem>()
        items += info.relatedItems
        onProgress(items.size)
        var page: Page? = info.nextPage
        while (Page.isValid(page) && items.size < max) {
            val more = PlaylistInfo.getMoreItems(yt, u, page)
            if (more.items.isEmpty()) break
            items += more.items
            onProgress(items.size)
            page = more.nextPage
        }
        val tracks = items.mapNotNull { it.toTrack() }.distinctBy { it.id }.take(max)
        val listId = Regex("[?&]list=([\\w-]+)").find(u)?.groupValues?.get(1) ?: u
        return Playlist(
            id = listId,
            name = info.name ?: "Danh sách phát",
            owner = info.uploaderName ?: "",
            url = u,
            thumb = bestThumb(info.thumbnails).ifBlank { tracks.firstOrNull()?.art ?: "" },
            tracks = tracks,
            at = System.currentTimeMillis(),
        )
    }

    /** Thong tin 1 video (khi dan link bai le). */
    fun track(id: String): Track {
        init()
        val si = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$id")
        return Track(id, si.name, si.uploaderName ?: "", si.duration, bestThumb(si.thumbnails))
    }

    // ---------------- luong audio ----------------

    private val audioCache = ConcurrentHashMap<String, Audio>()
    private const val AUDIO_TTL = 3L * 60 * 60 * 1000 // link googlevideo song ~6h, lay 3h cho chac

    fun invalidate(id: String) { audioCache.remove(id) }

    /** Link audio tot nhat cho [id] (uu tien track goc, bitrate cao, M4A khi bang nhau). */
    fun audio(id: String): Audio {
        audioCache[id]?.let { if (System.currentTimeMillis() - it.at < AUDIO_TTL) return it }
        init()
        val si = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$id")
        val best = bestAudio(si) ?: error("Không có luồng âm thanh cho bài này")
        val a = Audio(best.content, best.format?.mimeType, best.averageBitrate, System.currentTimeMillis())
        audioCache[id] = a
        return a
    }

    private fun bestAudio(si: StreamInfo, preferM4a: Boolean = false): AudioStream? =
        si.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.sortedWith(
            compareByDescending<AudioStream> { isOriginal(it) }
                .thenByDescending { if (preferM4a) it.format == MediaFormat.M4A else false }
                .thenByDescending { it.averageBitrate }
                .thenByDescending { it.format == MediaFormat.M4A },
        ).firstOrNull()

    // ---------------- video (v1.8) ----------------

    /**
     * Thong tin + cac luong hinh cua 1 video. Hinh "chi video" (DASH tach rieng, toi da 1080p)
     * se ghep voi [VideoInfo.audioUrl] luc phat; luong "muxed" (thuong 360p) co san tieng.
     */
    fun video(id: String): VideoInfo {
        init()
        val si = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$id")
        val audio = bestAudio(si, preferM4a = true)
        // uu tien MP4 (H.264) vi giai ma phan cung tren moi may; WebM (VP9) chi khi khong co MP4
        val vo = si.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height in 1..1080 }
            .groupBy { it.height }
            .mapNotNull { (_, l) ->
                l.sortedWith(
                    compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { it.format == MediaFormat.MPEG_4 }
                        .thenBy { it.fps },
                ).firstOrNull()
            }
            .map { VideoQ(it.height.toString() + "p" + (if (it.fps > 30) it.fps.toString() else ""), it.height, it.content, false) }
        val muxed = si.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.isVideoOnly }
            .map { VideoQ(it.height.toString() + "p", it.height, it.content, true) }
        val qualities = (if (audio != null) vo else emptyList()) + muxed.filter { m -> audio == null || vo.none { it.height == m.height } }
        val live = si.streamType.name.contains("LIVE")
        val t = Track(id, si.name, si.uploaderName ?: "", si.duration, bestThumb(si.thumbnails))
        val related = (si.relatedItems ?: emptyList()).filterIsInstance<StreamInfoItem>().mapNotNull { it.toTrack() }.distinctBy { it.id }
        if (audio != null) audioCache[id] = Audio(audio.content, audio.format?.mimeType, audio.averageBitrate, System.currentTimeMillis())
        return VideoInfo(
            track = t,
            views = si.viewCount,
            likes = si.likeCount,
            uploaded = ago(si.textualUploadDate ?: ""),
            description = runCatching { si.description?.content ?: "" }.getOrDefault(""),
            channelThumb = bestThumb(si.uploaderAvatars),
            subscribers = si.uploaderSubscriberCount,
            qualities = qualities.sortedByDescending { it.height },
            audioUrl = audio?.content,
            live = live,
            hls = si.hlsUrl?.takeIf { it.isNotBlank() },
            related = related,
        )
    }

    private fun isOriginal(s: AudioStream): Boolean = try {
        val t = s.audioTrackType
        t == null || t.name == "ORIGINAL"
    } catch (_: Throwable) { true }

    // ---------------- tien ich ----------------

    /** "2026-11-21T02:08:10-08:00" -> "5 ngày trước" (giu nguyen neu khong phai ISO). */
    fun ago(s: String, now: Long = System.currentTimeMillis()): String {
        val t = runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull() ?: return s
        val d = (now - t) / 86_400_000L
        return when {
            d < 1 -> "Hôm nay"
            d < 7 -> "$d ngày trước"
            d < 30 -> "${d / 7} tuần trước"
            d < 365 -> "${d / 30} tháng trước"
            else -> "${d / 365} năm trước"
        }
    }

    private fun StreamInfoItem.toTrack(): Track? {
        val id = try { yt.streamLHFactory.getId(url) } catch (_: Exception) { videoId(url) } ?: return null
        val artist = (uploaderName ?: "").removeSuffix(" - Topic")
        return Track(id, name ?: return null, artist, duration.coerceAtLeast(0), bestThumb(thumbnails))
    }

    /** Anh vua du (~ <= 480px) de khoi tai anh qua lon. */
    fun bestThumb(list: List<Image>?): String {
        if (list.isNullOrEmpty()) return ""
        val known = list.filter { it.height > 0 }
        val pick = known.filter { it.height <= 480 }.maxByOrNull { it.height }
            ?: known.minByOrNull { it.height }
            ?: list.last()
        // anh YouTube Music (googleusercontent) chi 120px -> xin ban 544px cho man phat
        return pick.url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
    }

    @Suppress("unused")
    private fun InfoItem.debug() = "$infoType $name $url"
}

/** Downloader cho NewPipe dung OkHttp. */
private class OkDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val rb = okhttp3.Request.Builder().url(request.url()).method(method, body).header("User-Agent", Yt.UA)
        for ((k, vs) in request.headers()) {
            if (vs.isEmpty()) continue
            rb.removeHeader(k)
            for (v in vs) rb.addHeader(k, v)
        }
        client.newCall(rb.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            val text = r.body.string()
            return Response(r.code, r.message, r.headers.toMultimap(), text, r.request.url.toString())
        }
    }
}
