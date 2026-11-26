package vn.uisplus.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.ReusableBitmapDrawable
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import vn.uisplus.BuildConfig
import vn.uisplus.bus.Stop
import vn.uisplus.bus.VarGeo
import vn.uisplus.bus.Vehicle
import java.io.File
import java.io.InputStream

/** Mau co dinh cho tung so tuyen (moi tuyen 1 mau, khong doi theo chieu). */
private val ROUTE_COLORS = intArrayOf(
    0xFF2563EB.toInt(), 0xFFE11D48.toInt(), 0xFF059669.toInt(), 0xFFD97706.toInt(),
    0xFF7C3AED.toInt(), 0xFF0891B2.toInt(), 0xFFDB2777.toInt(), 0xFF65A30D.toInt(),
)

fun routeColor(no: String): Int {
    var h = 0
    for (c in no.uppercase()) h = h * 31 + c.code
    return ROUTE_COLORS[Math.floorMod(h, ROUTE_COLORS.size)]
}

val PTIT_CENTER = GeoPoint(10.848164, 106.787147)

// ==================================================================== nguon tile
//
// 2 che do:
//  * Co CARTO key (local.properties: carto.key=...) -> tile CARTO @2x 512px: Voyager (sang) /
//    Dark Matter (toi). Tile 512px ve o cung kich thuoc vat ly voi tile 256 nen NET GAP DOI
//    (osmdroid: size = tile * density*256/tile).
//  * Khong co key -> tile OSM 256px; che do toi = "dark remap" tung pixel (KHONG dung
//    INVERT_COLORS nua: invert lam duong trang thanh den, nuoc xanh thanh cam, khu truong vang
//    thanh tim -> rat kho nhin).

val CARTO_KEY: String = BuildConfig.CARTO_KEY.trim()
val MAP_USES_CARTO: Boolean get() = CARTO_KEY.isNotEmpty()
val MAP_ATTRIBUTION: String
    get() = if (MAP_USES_CARTO) "© OpenStreetMap · © CARTO · dữ liệu EBMS TP.HCM" else "© OpenStreetMap · dữ liệu EBMS TP.HCM"

private fun cartoSource(style: String): XYTileSource = XYTileSource(
    "CARTO-$style-2x", 0, 20, 512, "@2x.png?key=$CARTO_KEY",
    arrayOf("https://basemaps.cartocdn.com/rastertiles/$style/"),
    "© OpenStreetMap contributors, © CARTO",
)

/** OSM Mapnik (giong TileSourceFactory.MAPNIK, cung ten -> dung lai cache cu) + dark remap. */
private class OsmTileSource : XYTileSource(
    "Mapnik", 0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.org/"),
    "© OpenStreetMap contributors",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
    ),
) {
    @Volatile var dark = false

    override fun getDrawable(aFileInputStream: InputStream?): Drawable? = remap(super.getDrawable(aFileInputStream))
    override fun getDrawable(aFilePath: String?): Drawable? = remap(super.getDrawable(aFilePath))

    private fun remap(d: Drawable?): Drawable? {
        if (!dark || d !is BitmapDrawable) return d
        val b = d.bitmap ?: return d
        return if (b.isMutable && b.config == Bitmap.Config.ARGB_8888) {
            DarkTiles.apply(b); d
        } else {
            val c = b.copy(Bitmap.Config.ARGB_8888, true) ?: return d
            DarkTiles.apply(c)
            ReusableBitmapDrawable(c)
        }
    }
}

/**
 * Bien tile OSM sang sang tong toi, giu sac do:
 *  - chu/vien den (L thap) -> sang (L0)          => nhan duong/phuong van doc ro
 *  - nen dat be OSM (L~0.93) -> xam xanh rat toi (L1)
 *  - duong trang (L=1) -> SANG HON nen (L2)      => duong noi len thay vi chim nhu khi invert
 *  - mau pastel sang (khu truong, nen) bi giam bao hoa de khong ua vang; nuoc/cong vien giu mau.
 * Tham so da thu tren tile that quanh PTIT (tools/maptest/dark.js cung thuat toan).
 */
internal object DarkTiles {
    private const val K = 0.90f
    private const val L0 = 0.86f
    private const val L1 = 0.115f
    private const val L2 = 0.25f
    private const val SAT = 0.70f
    private const val PL = 0.72f
    private const val PS = 0.20f
    private const val TB = 7f

    private fun hue2(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 0.5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    fun remap(rgb: Int): Int {
        val r = ((rgb shr 16) and 255) / 255f
        val g = ((rgb shr 8) and 255) / 255f
        val b = (rgb and 255) / 255f
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val l = (mx + mn) / 2f
        val d = mx - mn
        var h = 0f
        var s = 0f
        if (d > 1e-6f) {
            s = d / (1f - Math.abs(2f * l - 1f))
            h = when (mx) {
                r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
                g -> ((b - r) / d + 2f) / 6f
                else -> ((r - g) / d + 4f) / 6f
            }
        }
        val l2 = if (l <= K) L0 - (L0 - L1) * (l / K) else L1 + (l - K) / (1f - K) * (L2 - L1)
        val pastel = if (l > PL) minOf(1f, (l - PL) / (1f - PL)) else 0f
        val s2 = minOf(1f, s * SAT * (1f - pastel * (1f - PS)))
        val rr: Float
        val gg: Float
        val bb: Float
        if (s2 < 1e-6f) {
            rr = l2; gg = l2; bb = l2
        } else {
            val q = if (l2 < 0.5f) l2 * (1f + s2) else l2 + s2 - l2 * s2
            val p = 2f * l2 - q
            rr = hue2(p, q, h + 1f / 3f); gg = hue2(p, q, h); bb = hue2(p, q, h - 1f / 3f)
        }
        fun cl(v: Float) = Math.round(v).coerceIn(0, 255)
        return (cl(rr * 255f - TB * 0.5f) shl 16) or (cl(gg * 255f) shl 8) or cl(bb * 255f + TB)
    }

    fun apply(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        // tile OSM chi co vai tram mau -> cache theo mau
        val cache = HashMap<Int, Int>(512)
        for (i in px.indices) {
            val c = px[i]
            val rgb = c and 0xFFFFFF
            val out = cache.getOrPut(rgb) { remap(rgb) }
            px[i] = (c and -0x1000000) or out
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }
}

fun newBusMapView(ctx: Context): MapView {
    Configuration.getInstance().apply {
        userAgentValue = "UISPlus/1.3 (vn.uisplus.app)"
        val base = File(ctx.cacheDir, "osmdroid")
        osmdroidBasePath = base
        osmdroidTileCache = File(base, "tiles")
        tileFileSystemCacheMaxBytes = 120L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 90L * 1024 * 1024
    }
    return MapView(ctx).apply {
        setTileSource(if (MAP_USES_CARTO) cartoSource("voyager") else OsmTileSource())
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        // tha tay sau khi pinch -> lam tron ve zoom nguyen: tile ve dung ti le, khong bi
        // phong them toi 1.4x giua 2 muc zoom (nguyen nhan chinh lam ban do "mo/lo")
        setZoomRounding(true)
        minZoomLevel = 10.0
        maxZoomLevel = 19.0
        controller.setZoom(16.0)
        controller.setCenter(PTIT_CENTER)
        setScrollableAreaLimitLatitude(11.3, 10.2, 0)
        setScrollableAreaLimitLongitude(106.2, 107.2, 0)
    }
}

class BusMapController(private val ctx: Context, val map: MapView) {
    private val dens = ctx.resources.displayMetrics.density
    private fun px(dp: Float) = dp * dens

    private val stopLayer = FolderOverlay()
    private val routeStopLayer = FolderOverlay()
    private val vehLayer = FolderOverlay()
    private var routeLine: Polyline? = null
    private var meMarker: Marker? = null
    private val vehMarkers = HashMap<String, Marker>()
    private val icons = HashMap<String, BitmapDrawable>()
    private var dark: Boolean? = null

    init {
        map.overlays.add(stopLayer)
        map.overlays.add(routeStopLayer)
        map.overlays.add(vehLayer)
    }

    private var osm: OsmTileSource? = map.tileProvider.tileSource as? OsmTileSource

    fun setDark(d: Boolean) {
        if (dark == d) return
        dark = d
        val to = map.overlayManager.tilesOverlay
        to.setColorFilter(null)
        // o luoi cho tile dang tai: theo tong nen, khong con o xam sang nhap nhay tren nen toi
        to.setLoadingBackgroundColor(if (d) 0xFF1A1D23.toInt() else 0xFFEDEBE6.toInt())
        to.setLoadingLineColor(if (d) 0xFF23272E.toInt() else 0xFFE2DFD8.toInt())
        if (MAP_USES_CARTO) {
            val src: ITileSource = cartoSource(if (d) "dark_all" else "voyager")
            if (map.tileProvider.tileSource.name() != src.name()) map.setTileSource(src)
        } else {
            val o = osm ?: OsmTileSource().also { osm = it; map.setTileSource(it) }
            o.dark = d
            // tile da giai ma trong RAM la ban cu -> xoa de tai lai (tu cache dia, khong ton mang)
            map.tileProvider.clearTileCache()
        }
        map.invalidate()
    }

    // ------------------------------------------------------------ icon
    private fun dot(color: Int, ring: Int, sizeDp: Float): BitmapDrawable = icons.getOrPut("dot$color/$ring/$sizeDp") {
        val s = px(sizeDp).toInt().coerceAtLeast(8)
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = ring
        c.drawCircle(s / 2f, s / 2f, s / 2f, p)
        p.color = color
        c.drawCircle(s / 2f, s / 2f, s / 2f - px(2.5f), p)
        BitmapDrawable(ctx.resources, b)
    }

    private fun badge(text: String, color: Int, big: Boolean = false): BitmapDrawable = icons.getOrPut("b$text/$color/$big") {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = px(if (big) 13f else 11f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tw = tp.measureText(text)
        val h = px(if (big) 26f else 22f)
        val w = (tw + px(14f)).coerceAtLeast(h)
        val stroke = px(2f)
        val b = Bitmap.createBitmap((w + stroke * 2).toInt(), (h + stroke * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(RectF(0f, 0f, w + stroke * 2, h + stroke * 2), h, h, p)
        p.color = color
        c.drawRoundRect(RectF(stroke, stroke, w + stroke, h + stroke), h, h, p)
        val fm = tp.fontMetrics
        c.drawText(text, stroke + (w - tw) / 2, stroke + h / 2 - (fm.ascent + fm.descent) / 2, tp)
        BitmapDrawable(ctx.resources, b)
    }

    private fun marker(lat: Double, lng: Double, icon: BitmapDrawable, title: String, onClick: (() -> Unit)?): Marker =
        Marker(map).apply {
            position = GeoPoint(lat, lng)
            this.icon = icon
            this.title = title
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> onClick?.invoke(); true }
        }

    // ------------------------------------------------------------ tram
    fun showStops(stops: List<Stop>, selected: Int?, pinned: Set<Int>, brand: Int, onClick: (Stop) -> Unit) {
        stopLayer.items.clear()
        for (s in stops) {
            val ic = when {
                s.id == selected -> dot(brand, 0xFFFFFFFF.toInt(), 22f)
                s.id in pinned -> dot(0xFFF59E0B.toInt(), 0xFFFFFFFF.toInt(), 16f)
                else -> dot(0xFF2563EB.toInt(), 0xFFFFFFFF.toInt(), 13f)
            }
            stopLayer.add(marker(s.lat, s.lng, ic, s.name) { onClick(s) })
        }
        map.invalidate()
    }

    // ------------------------------------------------------------ tuyen
    private var routeGhost: Polyline? = null

    private fun line(pts: List<GeoPoint>, color: Int, widthDp: Float) = Polyline(map).apply {
        setPoints(pts)
        outlinePaint.color = color
        outlinePaint.strokeWidth = px(widthDp)
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.isAntiAlias = true
        infoWindow = null
    }

    /**
     * Ve lo trinh. [fromM] != null: chi to dam doan con lai tu vi tri do (m tu dau tuyen), doan da qua ve mo.
     * [onlyStops] != null: chi ve cac tram co id trong tap nay (vd tram chinh xe sap di qua).
     */
    fun showRoute(
        geo: VarGeo?, color: Int, selectedStop: Int?,
        fromM: Double? = null, onlyStops: Set<Int>? = null, extraStop: Int? = null,
        onStop: (Stop) -> Unit,
    ) {
        routeLine?.let { map.overlays.remove(it) }
        routeGhost?.let { map.overlays.remove(it) }
        routeLine = null
        routeGhost = null
        routeStopLayer.items.clear()
        if (geo == null || geo.lats.size < 2) { map.invalidate(); return }
        val all = ArrayList<GeoPoint>(geo.lats.size)
        for (i in geo.lats.indices) all += GeoPoint(geo.lats[i], geo.lngs[i])
        if (fromM == null) {
            routeLine = line(all, color, 5f)
            map.overlays.add(0, routeLine)
        } else {
            val ghostColor = (color and 0x00FFFFFF) or (0x55 shl 24)
            routeGhost = line(all, ghostColor, 4f)
            val (sLat, sLng) = geo.pointAt(fromM)
            val rest = ArrayList<GeoPoint>()
            rest += GeoPoint(sLat, sLng)
            for (i in geo.lats.indices) if (geo.cum[i] > fromM) rest += all[i]
            if (rest.size < 2) rest += all.last()
            routeLine = line(rest, color, 6f)
            // ghost duoi cung, roi duong con lai, roi cac lop tram/xe
            map.overlays.add(0, routeLine)
            map.overlays.add(0, routeGhost)
        }
        for ((i, s) in geo.stops.withIndex()) {
            if (onlyStops != null && s.id !in onlyStops && s.id != selectedStop && s.id != extraStop) continue
            val end = i == 0 || i == geo.stops.size - 1
            val ic = when {
                s.id == selectedStop -> dot(color, 0xFFFFFFFF.toInt(), 22f)
                s.id == extraStop -> dot(0xFF111827.toInt(), 0xFFFFFFFF.toInt(), 20f)
                end -> dot(color, 0xFFFFFFFF.toInt(), 17f)
                else -> dot(0xFFFFFFFF.toInt(), color, 13f)
            }
            routeStopLayer.add(marker(s.lat, s.lng, ic, s.name) { onStop(s) })
        }
        map.invalidate()
    }

    /** Thu phong de thay het cac diem (chua cho panel phia duoi). */
    fun fitPoints(points: List<Pair<Double, Double>>, maxZoom: Double = 17.0) {
        if (points.isEmpty()) return
        if (points.size == 1) { goTo(points[0].first, points[0].second, 16.0); return }
        val bb = BoundingBox.fromGeoPointsSafe(points.map { GeoPoint(it.first, it.second) })
        map.post {
            runCatching {
                val latSpan = maxOf(bb.latitudeSpan, 0.004)
                val lngSpan = maxOf(bb.longitudeSpanWithDateLine, 0.004)
                val padded = BoundingBox(
                    bb.latNorth + latSpan * 0.15, bb.lonEast + lngSpan * 0.12,
                    bb.latSouth - latSpan * 1.6, bb.lonWest - lngSpan * 0.12,
                )
                map.zoomToBoundingBox(padded, true, px(36f).toInt(), maxZoom, 650L)
            }
        }
    }

    // ------------------------------------------------------------ dia diem (ghim do)
    private var placeMarker: Marker? = null

    private fun pin(color: Int): BitmapDrawable = icons.getOrPut("pin$color") {
        val w = px(30f)
        val h = px(40f)
        val b = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = w / 2f
        val path = android.graphics.Path().apply {
            moveTo(r, h)
            lineTo(r - r * 0.62f, r + r * 0.78f)
            lineTo(r + r * 0.62f, r + r * 0.78f)
            close()
        }
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(r, r, r, p)
        c.drawPath(path, p)
        p.color = color
        c.drawCircle(r, r, r - px(2.5f), p)
        val inner = android.graphics.Path().apply {
            moveTo(r, h - px(3f))
            lineTo(r - r * 0.5f, r + r * 0.7f)
            lineTo(r + r * 0.5f, r + r * 0.7f)
            close()
        }
        c.drawPath(inner, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(r, r, r * 0.36f, p)
        BitmapDrawable(ctx.resources, b)
    }

    fun showPlace(lat: Double, lng: Double, color: Int) {
        val m = placeMarker ?: Marker(map).also {
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            it.setOnMarkerClickListener { _, _ -> true }
            placeMarker = it
        }
        m.icon = pin(color)
        m.position = GeoPoint(lat, lng)
        if (m !in map.overlays) map.overlays.add(m)
        map.invalidate()
    }

    fun clearPlace() {
        placeMarker?.let { map.overlays.remove(it) }
        map.invalidate()
    }

    /** Nhan giu ban do -> (lat, lng). Gan null de tat. */
    var onLongPress: ((Double, Double) -> Unit)? = null

    init {
        map.overlays.add(0, org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                val cb = onLongPress ?: return false
                if (p == null) return false
                cb(p.latitude, p.longitude)
                return true
            }
        }))
    }

    fun fit(geo: VarGeo) {
        if (geo.lats.size < 2) return
        val pts = geo.lats.indices.map { GeoPoint(geo.lats[it], geo.lngs[it]) }
        val bb = BoundingBox.fromGeoPointsSafe(pts)
        map.post {
            runCatching {
                // chua cho panel phia duoi: noi rong bounding box xuong duoi
                val latSpan = bb.latitudeSpan
                val padded = BoundingBox(bb.latNorth + latSpan * 0.08, bb.lonEast, bb.latSouth - latSpan * 0.9, bb.lonWest)
                map.zoomToBoundingBox(padded, true, px(36f).toInt())
            }
        }
    }

    // ------------------------------------------------------------ xe
    fun showVehicles(
        list: List<Vehicle>, routeNo: String, color: Int,
        selected: String? = null, label: ((Vehicle) -> String)? = null,
        onClick: (Vehicle) -> Unit,
    ) {
        val keep = list.map { it.plate }.toSet()
        val gone = vehMarkers.keys.filter { it !in keep }
        for (k in gone) vehMarkers.remove(k)?.let { vehLayer.remove(it) }
        for (v in list) {
            val m = vehMarkers[v.plate]
            val icon = badge(label?.invoke(v) ?: "\uD83D\uDE8C $routeNo", color, true)
            val alpha = if (selected == null || selected == v.plate) 1f else 0.4f
            if (m == null) {
                val nm = marker(v.lat, v.lng, icon, v.plate) { onClick(v) }
                nm.alpha = alpha
                vehMarkers[v.plate] = nm
                vehLayer.add(nm)
            } else {
                m.icon = icon
                m.alpha = alpha
                m.setOnMarkerClickListener { _, _ -> onClick(v); true }
                val from = m.position
                val to = GeoPoint(v.lat, v.lng)
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1400
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        val f = it.animatedValue as Float
                        m.position = GeoPoint(from.latitude + (to.latitude - from.latitude) * f, from.longitude + (to.longitude - from.longitude) * f)
                        map.invalidate()
                    }
                    start()
                }
            }
        }
        map.invalidate()
    }

    fun clearVehicles() {
        vehLayer.items.clear()
        vehMarkers.clear()
        map.invalidate()
    }

    // ------------------------------------------------------------ vi tri cua toi
    fun showMe(lat: Double, lng: Double) {
        val ic = dot(0xFF0EA5E9.toInt(), 0xFFFFFFFF.toInt(), 18f)
        val m = meMarker ?: Marker(map).also {
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.icon = ic
            it.setOnMarkerClickListener { _, _ -> true }
            meMarker = it
            map.overlays.add(it)
        }
        m.position = GeoPoint(lat, lng)
        map.invalidate()
    }

    fun goTo(lat: Double, lng: Double, zoom: Double? = null) {
        // zoom nguyen -> tile khong bi phong le (net hon)
        val z = Math.round(zoom ?: maxOf(map.zoomLevelDouble, 17.0)).toDouble()
        map.controller.animateTo(GeoPoint(lat, lng), z, 600L)
    }
}
