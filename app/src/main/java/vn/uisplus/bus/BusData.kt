package vn.uisplus.bus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Du lieu xe buyt toan TP.HCM tu EBMS (apicms.ebms.vn, khong can key).
 *  - Tram + tuyen goi kem APK (assets/bus, tao bang tools/busref/make_assets.js), tu cap nhat 7 ngay/lan.
 *  - Lo trinh/duong di cua tuyen tai khi can va luu file cache.
 *  - ETA lay song qua prediction/predictbystopid.
 * EBMS KHONG tra toa do xe: vi tri xe = uoc luong tu khoang cach `d` (met, doc theo tuyen) toi tram.
 */
data class Stop(
    val id: Int,
    val code: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val addr: String,
    val routes: List<String>,
) {
    internal val key: String = BusData.fold("$name $code $addr")
}

data class RouteInfo(val id: Int, val no: String, val name: String) {
    internal val key: String = BusData.fold("$no $name")
}

data class RouteVar(
    val routeId: Int,
    val varId: Int,
    val name: String,
    val short: String,
    val outbound: Boolean,
    val distance: Double,
)

data class RouteDetail(
    val id: Int,
    val no: String,
    val name: String,
    val color: String,
    val headway: String,
    val opTime: String,
    val orgs: String,
    val tickets: String,
    val vars: List<RouteVar>,
)

class VarGeo(
    val routeId: Int,
    val varId: Int,
    val stops: List<Stop>,
    val lats: DoubleArray,
    val lngs: DoubleArray,
    /** quang duong cong don (m) tai tung diem cua path */
    val cum: DoubleArray,
    /** vi tri (m tu dau tuyen) cua tung tram tren path */
    val stopPos: DoubleArray,
) {
    val length: Double get() = if (cum.isEmpty()) 0.0 else cum.last()

    /** Diem tren path o quang duong [m] tinh tu dau tuyen. */
    fun pointAt(m: Double): Pair<Double, Double> {
        if (lats.isEmpty()) return 0.0 to 0.0
        val x = m.coerceIn(0.0, length)
        var lo = 0
        var hi = cum.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (cum[mid] <= x) lo = mid else hi = mid
        }
        val seg = cum[hi] - cum[lo]
        val f = if (seg <= 0.0) 0.0 else (x - cum[lo]) / seg
        return (lats[lo] + (lats[hi] - lats[lo]) * f) to (lngs[lo] + (lngs[hi] - lngs[lo]) * f)
    }
}

data class StopArrivals(
    val routeId: Int,
    val routeNo: String,
    val varId: Int,
    val dest: String,
    val arrivals: List<Arrival>,
)

data class Vehicle(
    val plate: String,
    val lat: Double,
    val lng: Double,
    /** vi tri tren tuyen (m tu dau tuyen) */
    val pos: Double,
    val speed: Double,
    val gpsAt: Long,
    /** tram ke tiep (index trong VarGeo.stops) */
    val nextStop: Int,
)

object BusData {
    private const val BASE = "https://apicms.ebms.vn/"
    private const val REFRESH_MS = 7L * 24 * 3600_000
    val PTIT_STOPS = listOf(3713, 3746)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile var stops: List<Stop> = emptyList(); private set
    @Volatile var routes: List<RouteInfo> = emptyList(); private set
    @Volatile private var stopById: Map<Int, Stop> = emptyMap()
    @Volatile private var routeByNo: Map<String, RouteInfo> = emptyMap()
    private var loaded = false

    private val details = HashMap<Int, RouteDetail>()
    private val geos = HashMap<String, VarGeo>()

    private val _pinned = MutableStateFlow<List<Int>>(emptyList())
    val pinned: StateFlow<List<Int>> = _pinned.asStateFlow()

    // ---------------------------------------------------------------- tien ich
    fun fold(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(n.length)
        for (c in n) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(if (c == 'đ') 'd' else c)
        }
        return sb.toString()
    }

    fun distM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val k = 111_320.0
        val dx = (lng2 - lng1) * k * cos(Math.toRadians((lat1 + lat2) / 2))
        val dy = (lat2 - lat1) * k
        return sqrt(dx * dx + dy * dy)
    }

    fun stop(id: Int): Stop? = stopById[id]
    fun route(no: String): RouteInfo? = routeByNo[no.trim().uppercase()]
    fun route(id: Int): RouteInfo? = routes.firstOrNull { it.id == id }

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "bus").apply { mkdirs() }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BASE + path)
            .header("User-Agent", "UISPlus/1.3 (Android; vn.uisplus.app)")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
    }

    private fun JsonElement?.str(): String = (this as? JsonPrimitive)?.takeIf { it.isString || it.toString() != "null" }?.content ?: ""
    private fun JsonObject.s(k: String) = this[k].str().let { if (it == "null") "" else it }
    private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.intOrNull ?: 0

    private fun splitRoutes(s: String) = s.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }

    // ---------------------------------------------------------------- du lieu tinh
    @Synchronized
    fun init(ctx: Context) {
        if (loaded) return
        val app = ctx.applicationContext
        val sp = app.getSharedPreferences("bus", Context.MODE_PRIVATE)
        val pin = sp.getString("pinned", null)
        _pinned.value = pin?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: PTIT_STOPS
        val d = dir(app)
        val stopsTxt = File(d, "stops.json").takeIf { it.length() > 1000 }?.readText()
            ?: app.assets.open("bus/stops.json").bufferedReader().use { it.readText() }
        val routesTxt = File(d, "routes.json").takeIf { it.length() > 100 }?.readText()
            ?: app.assets.open("bus/routes.json").bufferedReader().use { it.readText() }
        applyStatic(stopsTxt, routesTxt)
        loaded = true
    }

    private fun applyStatic(stopsTxt: String, routesTxt: String) {
        val st = json.parseToJsonElement(stopsTxt).jsonArray.mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            Stop(
                id = a[0].jsonPrimitive.intOrNull ?: return@mapNotNull null,
                code = a[1].str(),
                name = a[2].str(),
                lat = a[3].jsonPrimitive.doubleOrNull ?: return@mapNotNull null,
                lng = a[4].jsonPrimitive.doubleOrNull ?: return@mapNotNull null,
                addr = a[5].str(),
                routes = splitRoutes(a[6].str()),
            )
        }
        val rt = json.parseToJsonElement(routesTxt).jsonArray.mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            RouteInfo(a[0].jsonPrimitive.intOrNull ?: return@mapNotNull null, a[1].str().uppercase(), a[2].str())
        }.sortedWith(compareBy({ it.no.takeWhile(Char::isDigit).toIntOrNull() ?: 9999 }, { it.no }))
        stops = st
        stopById = st.associateBy { it.id }
        routes = rt
        routeByNo = rt.associateBy { it.no }
    }

    /** Tai lai danh sach tram/tuyen neu ban luu da cu hon 7 ngay. Loi mang thi bo qua. */
    suspend fun refreshStaticIfOld(ctx: Context) {
        init(ctx)
        val sp = ctx.applicationContext.getSharedPreferences("bus", Context.MODE_PRIVATE)
        val last = sp.getLong("static_at", 0L)
        if (System.currentTimeMillis() - last < REFRESH_MS) return
        runCatching {
            val rawStops = json.parseToJsonElement(get("businfo/getstopsinbounds/106.3/10.3/107.1/11.2")).jsonArray
            val seen = HashSet<Int>()
            val compact = JsonArray(rawStops.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = o.i("StopId")
                if (id == 0 || !seen.add(id)) return@mapNotNull null
                val addr = listOf(o.s("AddressNo"), o.s("Street")).filter { it.isNotBlank() }.joinToString(", ")
                JsonArray(listOf(JsonPrimitive(id), JsonPrimitive(o.s("Code")), JsonPrimitive(o.s("Name")),
                    JsonPrimitive(o.d("Lat")), JsonPrimitive(o.d("Lng")), JsonPrimitive(addr), JsonPrimitive(o.s("Routes"))))
            })
            val rawRoutes = json.parseToJsonElement(get("businfo/getroutesforcombo")).jsonArray
            val rx = Regex("^\\[([^\\]]+)]\\s*(.*)$")
            val compactR = JsonArray(rawRoutes.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = o.s("value").toIntOrNull() ?: return@mapNotNull null
                val m = rx.find(o.s("lable"))
                JsonArray(listOf(JsonPrimitive(id), JsonPrimitive(m?.groupValues?.get(1) ?: ""), JsonPrimitive(m?.groupValues?.get(2) ?: o.s("lable"))))
            })
            if (compact.size < 1000 || compactR.size < 50) throw IOException("du lieu thieu")
            val sTxt = compact.toString()
            val rTxt = compactR.toString()
            withContext(Dispatchers.IO) {
                val d = dir(ctx)
                File(d, "stops.json").writeText(sTxt)
                File(d, "routes.json").writeText(rTxt)
            }
            applyStatic(sTxt, rTxt)
            sp.edit().putLong("static_at", System.currentTimeMillis()).apply()
        }
    }

    // ---------------------------------------------------------------- tim kiem
    fun search(q: String, near: Pair<Double, Double>? = null): Pair<List<RouteInfo>, List<Stop>> {
        val f = fold(q).trim()
        if (f.isEmpty()) return emptyList<RouteInfo>() to emptyList()
        val words = f.split(Regex("\\s+"))
        val up = q.trim().uppercase()
        val rs = routes.filter { r -> r.no == up || words.all { r.key.contains(it) } }
            .sortedWith(compareBy({ if (it.no == up) 0 else if (it.no.startsWith(up)) 1 else 2 }, { it.no.length }))
            .take(12)
        var ss = stops.filter { s -> words.all { s.key.contains(it) } }
        ss = if (near != null) ss.sortedBy { distM(near.first, near.second, it.lat, it.lng) } else ss.sortedBy { it.name.length }
        return rs to ss.take(30)
    }

    fun stopsIn(latMin: Double, latMax: Double, lngMin: Double, lngMax: Double, limit: Int = 250): List<Stop> {
        val out = ArrayList<Stop>()
        for (s in stops) {
            if (s.lat in latMin..latMax && s.lng in lngMin..lngMax) {
                out += s
                if (out.size >= limit) break
            }
        }
        return out
    }

    fun nearest(lat: Double, lng: Double, n: Int = 12, maxM: Double = 1500.0): List<Pair<Stop, Double>> =
        stops.asSequence().map { it to distM(lat, lng, it.lat, it.lng) }
            .filter { it.second <= maxM }.sortedBy { it.second }.take(n).toList()

    // ---------------------------------------------------------------- tram ghim
    fun togglePin(ctx: Context, id: Int) {
        val cur = _pinned.value
        val next = if (id in cur) cur - id else cur + id
        savePins(ctx, next)
    }

    fun movePinUp(ctx: Context, id: Int) {
        val cur = _pinned.value.toMutableList()
        val i = cur.indexOf(id)
        if (i > 0) { cur.removeAt(i); cur.add(i - 1, id); savePins(ctx, cur) }
    }

    private fun savePins(ctx: Context, list: List<Int>) {
        _pinned.value = list
        ctx.applicationContext.getSharedPreferences("bus", Context.MODE_PRIVATE).edit()
            .putString("pinned", list.joinToString(",")).apply()
    }

    // ---------------------------------------------------------------- tuyen
    suspend fun routeDetail(ctx: Context, id: Int): RouteDetail {
        synchronized(details) { details[id] }?.let { return it }
        val f = File(dir(ctx), "route_$id.json")
        val cached = f.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < REFRESH_MS }
        val txt = cached?.let { withContext(Dispatchers.IO) { it.readText() } } ?: run {
            val info = get("businfo/getroutebyid/$id")
            val vars = get("businfo/getvarsbyroute/$id")
            val t = "{\"info\":$info,\"vars\":$vars}"
            withContext(Dispatchers.IO) { f.writeText(t) }
            t
        }
        val root = json.parseToJsonElement(txt).jsonObject
        val o = root["info"]!!.jsonObject
        val vars = root["vars"]!!.jsonArray.mapNotNull { e ->
            val v = e as? JsonObject ?: return@mapNotNull null
            RouteVar(id, v.i("RouteVarId"), v.s("RouteVarName"), v.s("RouteVarShortName"),
                (v["Outbound"] as? JsonPrimitive)?.booleanOrNull ?: true, v.d("Distance"))
        }
        val d = RouteDetail(
            id = id,
            no = o.s("RouteNo"),
            name = o.s("RouteName"),
            color = o.s("Color"),
            headway = o.s("Headway"),
            opTime = o.s("OperationTime"),
            orgs = o.s("Orgs").replace("<br/>", " ").replace("<br>", " ").trim(),
            tickets = o.s("Tickets").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim(),
            vars = vars,
        )
        synchronized(details) { details[id] = d }
        return d
    }

    suspend fun varGeo(ctx: Context, routeId: Int, varId: Int): VarGeo {
        val k = "$routeId/$varId"
        synchronized(geos) { geos[k] }?.let { return it }
        val f = File(dir(ctx), "var_${routeId}_$varId.json")
        val cached = f.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < REFRESH_MS }
        val txt = cached?.let { withContext(Dispatchers.IO) { it.readText() } } ?: run {
            val st = get("businfo/getstopsbyvar/$routeId/$varId")
            val pa = get("businfo/getpathsbyvar/$routeId/$varId")
            val t = "{\"stops\":$st,\"path\":$pa}"
            withContext(Dispatchers.IO) { f.writeText(t) }
            t
        }
        val g = withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(txt).jsonObject
            val vs = root["stops"]!!.jsonArray.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = o.i("StopId")
                stopById[id] ?: Stop(id, o.s("Code"), o.s("Name"), o.d("Lat"), o.d("Lng"),
                    listOf(o.s("AddressNo"), o.s("Street")).filter { it.isNotBlank() }.joinToString(", "), splitRoutes(o.s("Routes")))
            }
            val p = root["path"]!!.jsonObject
            var lats = p["lat"]!!.jsonArray.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }.toDoubleArray()
            var lngs = p["lng"]!!.jsonArray.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }.toDoubleArray()
            if (lats.size < 2 && vs.size >= 2) {
                lats = vs.map { it.lat }.toDoubleArray(); lngs = vs.map { it.lng }.toDoubleArray()
            }
            val n = minOf(lats.size, lngs.size)
            val cum = DoubleArray(n)
            for (i in 1 until n) cum[i] = cum[i - 1] + distM(lats[i - 1], lngs[i - 1], lats[i], lngs[i])
            // chieu tung tram len path, giu thu tu tang dan
            val pos = DoubleArray(vs.size)
            var from = 0
            for ((si, s) in vs.withIndex()) {
                var best = from
                var bd = Double.MAX_VALUE
                val to = n
                for (j in from until to) {
                    val dd = distM(s.lat, s.lng, lats[j], lngs[j])
                    if (dd < bd) { bd = dd; best = j }
                    if (j > from + 400 && bd < 60) break
                }
                pos[si] = if (n == 0) 0.0 else cum[best]
                from = best
            }
            VarGeo(routeId, varId, vs, lats.copyOf(n), lngs.copyOf(n), cum, pos)
        }
        synchronized(geos) { geos[k] = g }
        return g
    }

    // ---------------------------------------------------------------- song
    private fun parseTs(s: String?): Long =
        s?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L

    suspend fun arrivals(stopId: Int): List<StopArrivals> {
        val arr = json.parseToJsonElement(get("prediction/predictbystopid/$stopId")).jsonArray
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val a = (o["arrs"] as? JsonArray).orEmpty().mapNotNull { x ->
                val ao = x as? JsonObject ?: return@mapNotNull null
                val plate = ao.s("v").ifEmpty { return@mapNotNull null }
                val t = (ao["t"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                Arrival(plate, t.coerceAtLeast(0), ao.d("d").toInt(), parseTs(ao.s("dts")))
            }.sortedBy { it.etaSec }
            StopArrivals(o.i("r"), o.s("rNo").uppercase(), o.i("v"), o.s("vN"), a)
        }.sortedWith(compareBy({ it.arrivals.firstOrNull()?.etaSec ?: Int.MAX_VALUE }, { it.routeNo }))
    }

    /**
     * Uoc luong vi tri cac xe dang chay tren 1 chieu cua tuyen: hoi ETA tai tram cuoi + 2 tram giua,
     * vi tri = vi tri tram - d (met doc tuyen). Cung bien so thi lay ban ghi gan tram nhat.
     */
    suspend fun vehicles(geo: VarGeo): List<Vehicle> {
        val n = geo.stops.size
        if (n < 2) return emptyList()
        val probes = listOf(n - 1, (n * 2) / 3, n / 3).filter { it in 1 until n }.distinct()
        val best = HashMap<String, Pair<Double, Arrival>>()
        val bestD = HashMap<String, Int>()
        var anyOk = false
        var lastErr: Exception? = null
        for (pi in probes) {
            val st = geo.stops[pi]
            val list = try { arrivals(st.id) } catch (e: Exception) { lastErr = e; continue }
            anyOk = true
            for (sa in list) {
                if (sa.routeId != geo.routeId || sa.varId != geo.varId) continue
                for (a in sa.arrivals) {
                    val p = geo.stopPos[pi] - a.distM
                    if (p < -50) continue
                    val old = bestD[a.plate]
                    if (old == null || a.distM < old) {
                        bestD[a.plate] = a.distM
                        best[a.plate] = p.coerceAtLeast(0.0) to a
                    }
                }
            }
        }
        if (!anyOk && lastErr != null) throw lastErr!!
        return best.map { (plate, v) ->
            val (pos, a) = v
            val (la, ln) = geo.pointAt(pos)
            val next = geo.stopPos.indexOfFirst { it >= pos - 5 }.let { if (it < 0) n - 1 else it }
            Vehicle(plate, la, ln, pos, 0.0, a.gpsAt, next)
        }.sortedBy { it.pos }
    }

    // ---------------------------------------------------------------- tim duong (1 tuyen, di thang)
    internal const val WALK_MPS = 1.15   // di bo ~4 km/h (da tinh duong vong)
    internal const val BUS_MPS = 4.5     // xe buyt noi do ~16 km/h ke ca dung tram

    /**
     * Cac tuyen di THANG tu [fromLat,fromLng] toi [toLat,toLng]: len o tram trong ban kinh [maxWalk]
     * quanh diem di, xuong o tram quanh diem den, dung chieu (tram len dung TRUOC tram xuong tren lo trinh).
     * Moi tuyen chi giu cach di tot nhat; sap xep theo tong thoi gian uoc luong.
     */
    suspend fun planDirect(
        ctx: Context, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, maxWalk: Double = 800.0,
    ): List<DirectTrip> = kotlinx.coroutines.coroutineScope {
        val o = nearest(fromLat, fromLng, 25, maxWalk)
        val d = nearest(toLat, toLng, 25, maxWalk)
        if (o.isEmpty() || d.isEmpty()) return@coroutineScope emptyList()
        val oW = o.associate { it.first.id to it.second }
        val dW = d.associate { it.first.id to it.second }
        val oR = o.flatMap { it.first.routes }.toSet()
        val dR = d.flatMap { it.first.routes }.toSet()
        val cands = (oR intersect dR).mapNotNull { route(it) }
            .sortedBy { r -> o.filter { r.no in it.first.routes }.minOf { it.second } + d.filter { r.no in it.first.routes }.minOf { it.second } }
            .take(12)
        val sem = kotlinx.coroutines.sync.Semaphore(4)
        cands.map { r ->
            async(Dispatchers.IO) { sem.acquire(); try { runCatching { bestOnRoute(ctx, r, oW, dW) }.getOrNull() } finally { sem.release() } }
        }.awaitAll().filterNotNull().sortedBy { it.minutes }
    }

    private suspend fun bestOnRoute(ctx: Context, r: RouteInfo, oW: Map<Int, Double>, dW: Map<Int, Double>): DirectTrip? {
        val det = routeDetail(ctx, r.id)
        var best: DirectTrip? = null
        for ((vi, v) in det.vars.withIndex()) {
            val g = varGeo(ctx, r.id, v.varId)
            // score = wO/W - posI/B  +  posK/B + wD/W ; giu min(wO/W - posI/B) voi i < k
            var bestI = -1
            var bestA = Double.MAX_VALUE
            for (k in g.stops.indices) {
                val id = g.stops[k].id
                val wd = dW[id]
                if (wd != null && bestI >= 0 && g.stopPos[k] > g.stopPos[bestI]) {
                    val ride = g.stopPos[k] - g.stopPos[bestI]
                    val wo = oW[g.stops[bestI].id] ?: 0.0
                    val sec = wo / WALK_MPS + ride / BUS_MPS + wd / WALK_MPS
                    if (best == null || sec < best.seconds) {
                        best = DirectTrip(
                            routeId = r.id, routeNo = det.no.ifBlank { r.no }, varId = v.varId, varIdx = vi,
                            dest = v.short.ifBlank { v.name }.ifBlank { g.stops.last().name },
                            board = g.stops[bestI], alight = g.stops[k], walkTo = wo, walkFrom = wd,
                            rideM = ride, stops = k - bestI, seconds = sec,
                        )
                    }
                }
                val wo = oW[id]
                if (wo != null) {
                    val a = wo / WALK_MPS - g.stopPos[k] / BUS_MPS
                    if (a < bestA) { bestA = a; bestI = k }
                }
            }
        }
        return best
    }

    // ---------------------------------------------------------------- tim duong (doi 1 lan tuyen)
    private const val XFER_WALK = 300.0     // di bo toi da giua 2 tram doi tuyen
    private const val XFER_PEN = 300.0      // phat 5 phut cho moi lan doi xe (cho xe thu 2)

    /** Cho 1 chieu tuyen: tu tram [i] (index) toi tram [k] mat [sec] giay (da gom di bo dau). */
    private class Reach(val r: RouteInfo, val det: RouteDetail, val vi: Int, val g: VarGeo, val i: Int, val k: Int, val walk: Double, val sec: Double)

    private suspend fun loadVars(ctx: Context, rs: List<RouteInfo>): List<Triple<RouteInfo, RouteDetail, Pair<Int, VarGeo>>> =
        kotlinx.coroutines.coroutineScope {
            val sem = kotlinx.coroutines.sync.Semaphore(5)
            rs.map { r ->
                async(Dispatchers.IO) {
                    sem.acquire()
                    try {
                        val det = runCatching { routeDetail(ctx, r.id) }.getOrNull() ?: return@async emptyList<Triple<RouteInfo, RouteDetail, Pair<Int, VarGeo>>>()
                        det.vars.withIndex().mapNotNull { (vi, v) ->
                            runCatching { varGeo(ctx, r.id, v.varId) }.getOrNull()?.let { Triple(r, det, vi to it) }
                        }
                    } finally { sem.release() }
                }
            }.awaitAll().flatten()
        }

    /**
     * Ket qua tim duong: cac tuyen di thang (neu co) + cach di doi 1 lan tuyen (neu it tuyen thang
     * hoac tuyen thang qua lau). Sap xep theo tong thoi gian (doi tuyen da cong 5 phut cho xe).
     */
    suspend fun plan(
        ctx: Context, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double,
        direct: List<DirectTrip>? = null, maxWalk: Double = 800.0,
    ): List<TripPlan> {
        val dir = (direct ?: planDirect(ctx, fromLat, fromLng, toLat, toLng, maxWalk)).map { TripPlan(it) }
        val bestDir = dir.minOfOrNull { it.seconds }
        if (dir.size >= 3 && (bestDir ?: 0.0) <= 40 * 60.0) return dir
        val xs = planTransfer(ctx, fromLat, fromLng, toLat, toLng, maxWalk)
            .filter { bestDir == null || dir.size < 2 || it.seconds < bestDir }
        return (dir + xs).sortedBy { it.seconds }
    }

    /** Cac cach di doi 1 lan tuyen: tuyen A (gan diem di) -> di bo <= 300 m -> tuyen B (gan diem den). */
    suspend fun planTransfer(
        ctx: Context, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, maxWalk: Double = 800.0,
        maxRoutes: Int = 12,
    ): List<TripPlan> = withContext(Dispatchers.Default) {
        val o = nearest(fromLat, fromLng, 25, maxWalk)
        val d = nearest(toLat, toLng, 25, maxWalk)
        if (o.isEmpty() || d.isEmpty()) return@withContext emptyList()
        val oW = o.associate { it.first.id to it.second }
        val dW = d.associate { it.first.id to it.second }
        fun ranked(near: List<Pair<Stop, Double>>): List<RouteInfo> {
            val m = HashMap<String, Double>()
            for ((s, w) in near) for (r in s.routes) if (w < (m[r] ?: Double.MAX_VALUE)) m[r] = w
            return m.entries.sortedBy { it.value }.mapNotNull { route(it.key) }.take(maxRoutes)
        }
        val rA = ranked(o)
        val rB = ranked(d)
        if (rA.isEmpty() || rB.isEmpty()) return@withContext emptyList()
        val all = loadVars(ctx, (rA + rB).distinctBy { it.id })
        val aIds = rA.map { it.id }.toSet()
        val bIds = rB.map { it.id }.toSet()

        // Chieu di: moi tram sau tram len -> thoi gian toi do (nho nhat)
        val reachA = ArrayList<Reach>()
        for ((r, det, p) in all) {
            if (r.id !in aIds) continue
            val (vi, g) = p
            var bestI = -1
            var bestA = Double.MAX_VALUE
            for (k in g.stops.indices) {
                if (bestI >= 0 && g.stopPos[k] > g.stopPos[bestI]) {
                    reachA += Reach(r, det, vi, g, bestI, k, oW[g.stops[bestI].id] ?: 0.0, bestA + g.stopPos[k] / BUS_MPS)
                }
                val wo = oW[g.stops[k].id]
                if (wo != null) {
                    val a = wo / WALK_MPS - g.stopPos[k] / BUS_MPS
                    if (a < bestA) { bestA = a; bestI = k }
                }
            }
        }
        // Chieu ve: moi tram truoc tram xuong -> thoi gian tu do toi dich (nho nhat)
        val reachB = ArrayList<Reach>()
        for ((r, det, p) in all) {
            if (r.id !in bIds) continue
            val (vi, g) = p
            var bestJ = -1
            var bestC = Double.MAX_VALUE
            for (k in g.stops.indices.reversed()) {
                if (bestJ >= 0 && g.stopPos[bestJ] > g.stopPos[k]) {
                    reachB += Reach(r, det, vi, g, k, bestJ, dW[g.stops[bestJ].id] ?: 0.0, bestC - g.stopPos[k] / BUS_MPS)
                }
                val wd = dW[g.stops[k].id]
                if (wd != null) {
                    val c = g.stopPos[k] / BUS_MPS + wd / WALK_MPS
                    if (c < bestC) { bestC = c; bestJ = k }
                }
            }
        }
        if (reachA.isEmpty() || reachB.isEmpty()) return@withContext emptyList()

        // luoi o ~330 m de tim tram doi tuyen gan nhau
        val cell = 0.003
        fun key(lat: Double, lng: Double) = (lat / cell).toLong() * 1_000_003L + (lng / cell).toLong()
        val grid = HashMap<Long, MutableList<Reach>>()
        for (b in reachB) {
            val s = b.g.stops[b.i]
            grid.getOrPut(key(s.lat, s.lng)) { ArrayList() } += b
        }
        // giu cach tot nhat cho moi cap (tuyen A, tuyen B)
        val best = HashMap<Long, TripPlan>()
        for (a in reachA) {
            val s = a.g.stops[a.k]
            val ci = (s.lat / cell).toLong()
            val cj = (s.lng / cell).toLong()
            for (di in -1L..1L) for (dj in -1L..1L) {
                val lst = grid[(ci + di) * 1_000_003L + (cj + dj)] ?: continue
                for (b in lst) {
                    if (b.r.id == a.r.id) continue
                    val t = b.g.stops[b.i]
                    val w = if (t.id == s.id) 0.0 else distM(s.lat, s.lng, t.lat, t.lng)
                    if (w > XFER_WALK) continue
                    val total = a.sec + w / WALK_MPS + XFER_PEN + b.sec
                    val pk = a.r.id.toLong() * 100_000L + b.r.id
                    val cur = best[pk]
                    if (cur != null && cur.seconds <= total) continue
                    val legA = DirectTrip(
                        routeId = a.r.id, routeNo = a.det.no.ifBlank { a.r.no }, varId = a.g.varId, varIdx = a.vi,
                        dest = a.det.vars[a.vi].let { v -> v.short.ifBlank { v.name } }.ifBlank { a.g.stops.last().name },
                        board = a.g.stops[a.i], alight = s, walkTo = a.walk, walkFrom = w,
                        rideM = a.g.stopPos[a.k] - a.g.stopPos[a.i], stops = a.k - a.i,
                        seconds = a.sec + w / WALK_MPS,
                    )
                    val rideB = b.g.stopPos[b.k] - b.g.stopPos[b.i]
                    val legB = DirectTrip(
                        routeId = b.r.id, routeNo = b.det.no.ifBlank { b.r.no }, varId = b.g.varId, varIdx = b.vi,
                        dest = b.det.vars[b.vi].let { v -> v.short.ifBlank { v.name } }.ifBlank { b.g.stops.last().name },
                        board = t, alight = b.g.stops[b.k], walkTo = 0.0, walkFrom = b.walk,
                        rideM = rideB, stops = b.k - b.i, seconds = b.sec,
                    )
                    best[pk] = TripPlan(legA, legB, w, total)
                }
            }
        }
        // toi da 2 cach cho moi tuyen dau, toi da 8 cach
        val perFirst = HashMap<Int, Int>()
        // tuyen tang cuong (vd 08_TC) trung lo trinh voi tuyen goc -> chi giu 1
        fun base(no: String) = no.substringBefore('_')
        best.values.sortedBy { it.seconds }
            .distinctBy { base(it.first.routeNo) + ">" + base(it.second!!.routeNo) }
            .filter { p ->
            val n = perFirst[p.first.routeId] ?: 0
            if (n >= 2) false else { perFirst[p.first.routeId] = n + 1; true }
        }.take(8)
    }
}

/**
 * 1 cach di: [first] (di thang) hoac [first] -> di bo [xferWalk] m -> [second].
 * [seconds] = tong thoi gian uoc luong (doi tuyen da cong 5 phut cho xe thu 2; chua tinh cho xe dau).
 */
data class TripPlan(
    val first: DirectTrip,
    val second: DirectTrip? = null,
    val xferWalk: Double = 0.0,
    val seconds: Double = first.seconds,
) {
    val minutes: Int get() = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
    val key: String get() = "t${first.routeId}/${first.varId}" + (second?.let { "-${it.routeId}/${it.varId}" } ?: "")
}

/** 1 cach di thang bang 1 tuyen: di bo -> len xe o [board] -> xuong o [alight] -> di bo. */
data class DirectTrip(
    val routeId: Int,
    val routeNo: String,
    val varId: Int,
    val varIdx: Int,
    val dest: String,
    val board: Stop,
    val alight: Stop,
    val walkTo: Double,
    val walkFrom: Double,
    val rideM: Double,
    val stops: Int,
    /** thoi gian uoc luong (giay), chua tinh thoi gian cho xe */
    val seconds: Double,
) {
    val minutes: Int get() = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
}
