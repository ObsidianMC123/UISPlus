package vn.uisplus.bus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Theo doi xe buyt ve tai tram Hoc vien Buu chinh (97 Man Thien) qua API EBMS
 * (https://apicms.ebms.vn, khong can key). Da goi thu 26/11/2026:
 *  - stop 3713 (Q9 146): 162 -> Ga Binh Thai, 76 -> BX Mien Dong moi
 *  - stop 3746 (Q9 143): 162 -> Ga Thu Duc,  76 -> Long Phuoc
 *  - tuyen 74 (BX An Suong - BX Cu Chi) KHONG di qua 2 tram nay.
 */
data class BusStop(val id: Int, val code: String, val name: String, val dir: String)

data class BusConfig(
    val auto: Boolean = true,
    val routes: List<String> = listOf("76", "162"),
    /** 0 = ca 2 chieu, 1 = chi tram 3713, 2 = chi tram 3746 */
    val dir: Int = 0,
    val leadMin: Int = 20,
    val afterMin: Int = 40,
    val nearMin: Int = 5,
)

data class Arrival(val plate: String, val etaSec: Int, val distM: Int, val gpsAt: Long)

data class BusLine(
    val routeNo: String,
    val dest: String,
    val stopId: Int,
    val dir: String,
    val arrivals: List<Arrival>,
)

data class PassedBus(val routeNo: String, val dest: String, val plate: String, val at: Long, val stopId: Int)

data class BusState(
    val active: Boolean = false,
    val until: Long = 0,
    val updatedAt: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val lines: List<BusLine> = emptyList(),
    val passed: List<PassedBus> = emptyList(),
    /** tuyen anh chon nhung khong di qua tram da chon */
    val notServed: List<String> = emptyList(),
    /** cac tuyen di qua tram (theo EBMS) */
    val servedRoutes: List<String> = emptyList(),
    val nextStartAt: Long = 0,
    val nextHomeAt: Long = 0,
)

sealed class BusEvent {
    data class Near(val routeNo: String, val dest: String, val plate: String, val etaSec: Int, val distM: Int) : BusEvent()
    data class Passed(val routeNo: String, val dest: String, val plate: String, val nextEtaSec: Int?) : BusEvent()
}

@Serializable
private data class PredDto(
    val r: Int = 0,
    val rNo: String? = null,
    val rN: String? = null,
    val s: Int = 0,
    val v: Int = 0,
    val vN: String? = null,
    val arrs: List<ArrDto>? = null,
)

@Serializable
private data class ArrDto(
    val v: String? = null,
    val t: Int? = null,
    val d: Double? = null,
    val dts: String? = null,
)

@Serializable
private data class RouteThroughDto(
    @SerialName("RouteNo") val no: String = "",
)

object BusTracker {
    val STOPS = listOf(
        BusStop(3713, "Q9 146", "Học Viện Bưu chính", "Hướng Ga Bình Thái · BX Miền Đông mới"),
        BusStop(3746, "Q9 143", "Học Viện Bưu chính", "Hướng Ga Thủ Đức · Long Phước"),
    )
    val DEFAULT_ROUTES = listOf("76", "162", "162D")

    private const val BASE = "https://apicms.ebms.vn/"
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val _state = MutableStateFlow(BusState())
    val state: StateFlow<BusState> = _state.asStateFlow()
    private val _config = MutableStateFlow(BusConfig())
    val config: StateFlow<BusConfig> = _config.asStateFlow()
    private var loaded = false

    private data class Seen(val routeNo: String, val dest: String, val plate: String, val eta: Int, val dist: Int, val at: Long, val stopId: Int, val key: String)

    private val seen = HashMap<String, Seen>()
    private val nearNotified = HashSet<String>()
    private val served = HashMap<Int, Set<String>>()

    fun norm(r: String) = r.trim().uppercase()

    // ---------------- config ----------------
    @Synchronized
    fun init(ctx: Context) {
        if (loaded) return
        val sp = ctx.applicationContext.getSharedPreferences("bus", Context.MODE_PRIVATE)
        _config.value = BusConfig(
            auto = sp.getBoolean("auto", true),
            // 1.2.1: 74 khong qua tram HV Buu chinh -> ban cu da luu 74 thi tu doi sang 76
            routes = (sp.getString("routes", null) ?: "76,162").split(',').map { norm(it) }
                .map { if (it == "74") "76" else it }.filter { it.isNotEmpty() }.distinct(),
            dir = sp.getInt("dir", 0),
            leadMin = sp.getInt("lead", 20),
            afterMin = sp.getInt("after", 40),
            nearMin = sp.getInt("near", 5),
        )
        loaded = true
    }

    fun saveConfig(ctx: Context, c: BusConfig) {
        ctx.applicationContext.getSharedPreferences("bus", Context.MODE_PRIVATE).edit()
            .putBoolean("auto", c.auto)
            .putString("routes", c.routes.joinToString(","))
            .putInt("dir", c.dir)
            .putInt("lead", c.leadMin)
            .putInt("after", c.afterMin)
            .putInt("near", c.nearMin)
            .apply()
        _config.value = c
        _state.value = _state.value.copy(notServed = notServed(c))
    }

    fun stopsFor(c: BusConfig): List<BusStop> = when (c.dir) {
        1 -> listOf(STOPS[0])
        2 -> listOf(STOPS[1])
        else -> STOPS
    }

    private fun notServed(c: BusConfig): List<String> {
        val stops = stopsFor(c)
        if (stops.any { served[it.id] == null }) return emptyList()
        val all = stops.flatMap { served[it.id].orEmpty() }.toSet()
        return c.routes.filter { it !in all }
    }

    internal fun setSchedule(startAt: Long, homeAt: Long) {
        _state.value = _state.value.copy(nextStartAt = startAt, nextHomeAt = homeAt)
    }

    // ---------------- vong doi phien theo doi ----------------
    @Synchronized
    internal fun begin(until: Long) {
        if (!_state.value.active) {
            seen.clear(); nearNotified.clear()
            _state.value = _state.value.copy(active = true, until = until, error = null, lines = emptyList(), loading = true)
        } else {
            _state.value = _state.value.copy(until = maxOf(until, _state.value.until))
        }
    }

    @Synchronized
    internal fun end() {
        _state.value = _state.value.copy(active = false, loading = false)
    }

    // ---------------- mang ----------------
    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BASE + path)
            .header("User-Agent", "UISPlus/1.2 (Android; vn.uisplus.app)")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
    }

    suspend fun ensureServed() {
        for (st in STOPS) {
            if (served[st.id] != null) continue
            runCatching {
                val list = json.decodeFromString<List<RouteThroughDto>>(get("businfo/getroutesthroughstop/${st.id}"))
                served[st.id] = list.map { norm(it.no) }.filter { it.isNotEmpty() }.toSet()
            }
        }
        val all = STOPS.flatMap { served[it.id].orEmpty() }.distinct().sortedWith(compareBy({ it.filter(Char::isDigit).toIntOrNull() ?: 999 }, { it }))
        _state.value = _state.value.copy(servedRoutes = all, notServed = notServed(_config.value))
    }

    private fun parseTs(s: String?): Long =
        s?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L

    /** Goi API 1 lan, cap nhat state, tra ve cac su kien can bao (xe sap toi / xe vua qua). */
    suspend fun poll(): List<BusEvent> {
        val cfg = _config.value
        val wanted = cfg.routes.map { norm(it) }.toSet()
        val stops = stopsFor(cfg)
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(loading = true)
        ensureServed()

        val lines = mutableListOf<BusLine>()
        val cur = HashMap<String, Seen>()
        val okStops = HashSet<Int>()
        var err: String? = null
        for (st in stops) {
            val preds = try {
                json.decodeFromString<List<PredDto>>(get("prediction/predictbystopid/${st.id}"))
            } catch (e: Exception) {
                err = "Không lấy được dữ liệu xe: ${e.message ?: e.javaClass.simpleName}"
                continue
            }
            okStops += st.id
            for (p in preds) {
                val no = norm(p.rNo ?: continue)
                if (wanted.isNotEmpty() && no !in wanted) continue
                val arrs = p.arrs.orEmpty().mapNotNull { a ->
                    val plate = a.v ?: return@mapNotNull null
                    val t = a.t ?: return@mapNotNull null
                    Arrival(plate, t.coerceAtLeast(0), (a.d ?: 0.0).toInt(), parseTs(a.dts))
                }.sortedBy { it.etaSec }
                lines += BusLine(no, p.vN.orEmpty(), st.id, st.dir, arrs)
                for (a in arrs) {
                    val k = "${st.id}|${p.r}|${p.v}|${a.plate}"
                    cur[k] = Seen(no, p.vN.orEmpty(), a.plate, a.etaSec, a.distM, now, st.id, k)
                }
            }
        }

        val events = mutableListOf<BusEvent>()
        val newPassed = mutableListOf<PassedBus>()
        synchronized(this) {
            // Xe dang toi gan ma bien mat khoi danh sach du doan => da qua tram.
            for ((k, old) in seen) {
                if (old.stopId !in okStops || k in cur) continue
                if (now - old.at > 4 * 60_000) continue
                if (old.eta <= 240 || old.dist <= 800) {
                    val next = lines.firstOrNull { it.routeNo == old.routeNo && it.stopId == old.stopId && it.dest == old.dest }
                        ?.arrivals?.firstOrNull()?.etaSec
                    newPassed += PassedBus(old.routeNo, old.dest, old.plate, now, old.stopId)
                    events += BusEvent.Passed(old.routeNo, old.dest, old.plate, next)
                }
            }
            seen.entries.removeIf { it.value.stopId in okStops }
            seen.putAll(cur)

            // Xe sap toi: bao 1 lan moi xe, bo qua GPS cu > 3 phut.
            for (l in lines) for (a in l.arrivals) {
                val fresh = a.gpsAt == 0L || now - a.gpsAt < 180_000
                val k = "${l.stopId}|${l.routeNo}|${a.plate}"
                if (fresh && a.etaSec <= cfg.nearMin * 60 && k !in nearNotified) {
                    nearNotified += k
                    events += BusEvent.Near(l.routeNo, l.dest, a.plate, a.etaSec, a.distM)
                }
            }
        }

        val order = cfg.routes.map { norm(it) }
        val sorted = lines.sortedWith(compareBy({ order.indexOf(it.routeNo).let { i -> if (i < 0) 99 else i } }, { it.stopId }))
        val passed = (newPassed + _state.value.passed).filter { now - it.at < 30 * 60_000 }.take(8)
        _state.value = _state.value.copy(
            updatedAt = now,
            loading = false,
            error = if (okStops.isEmpty()) err else null,
            lines = if (okStops.isEmpty()) _state.value.lines else sorted,
            passed = passed,
            notServed = notServed(cfg),
        )
        return events
    }
}
