package vn.uisplus.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import vn.uisplus.bus.Arrival
import vn.uisplus.bus.BusData
import vn.uisplus.bus.DirectTrip
import vn.uisplus.bus.Itinerary
import vn.uisplus.bus.TripPlan
import vn.uisplus.bus.Place
import vn.uisplus.bus.Places
import vn.uisplus.bus.RouteDetail
import vn.uisplus.bus.RouteInfo
import vn.uisplus.bus.Stop
import vn.uisplus.bus.StopArrivals
import vn.uisplus.bus.VarGeo
import vn.uisplus.bus.Vehicle
import vn.uisplus.ui.theme.Ui
import vn.uisplus.widget.BusWidget

private sealed class BusSel {
    data object None : BusSel()
    data class StopSel(val id: Int, val back: BusSel? = null) : BusSel()
    data class RouteSel(val id: Int, val varIdx: Int = 0, val focusStop: Int? = null) : BusSel()
    /** Tram -> 1 tuyen: danh sach xe dang toi tram (gan truoc xa sau). [alight] = tram xuong (khi di tu tim dia diem). */
    data class ArrSel(
        val stopId: Int, val routeId: Int, val varId: Int, val routeNo: String, val dest: String,
        val alight: Int? = null, val back: BusSel? = null,
    ) : BusSel() { val key get() = TripKey(stopId, routeId, varId) }
    /** 1 xe cu the: cac tram chinh xe se di qua. */
    data class VehSel(val arr: ArrSel, val plate: String) : BusSel()
    data class PlaceSel(val place: Place) : BusSel()
    /** v1.9: lich trinh tung buoc cua 1 cach di toi [back].place, xuat phat tu [origin]. */
    data class TripSel(val plan: TripPlan, val back: PlaceSel, val origin: Pair<Double, Double>, val fromMe: Boolean) : BusSel()
}

internal class Live<T>(val data: T? = null, val loading: Boolean = false, val error: String? = null, val at: Long = 0)

fun etaText(sec: Int): String = when {
    sec < 45 -> "Sắp tới"
    sec < 3600 -> "${(sec + 30) / 60} phút"
    else -> "${sec / 3600}g${(sec % 3600) / 60}p"
}

fun distText(m: Double): String = if (m < 1000) "${m.toInt()} m" else String.format("%.1f km", m / 1000)

private fun hasLoc(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun fetchLocation(ctx: Context, cb: (Location?) -> Unit) {
    if (!hasLoc(ctx)) { cb(null); return }
    val lm = ctx.getSystemService(LocationManager::class.java) ?: run { cb(null); return }
    val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        .maxByOrNull { it.time }
    if (last != null) cb(last)
    val prov = when {
        runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
        runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
        else -> null
    }
    if (prov == null) { if (last == null) cb(null); return }
    runCatching {
        LocationManagerCompat.getCurrentLocation(lm, prov, android.os.CancellationSignal(), ContextCompat.getMainExecutor(ctx)) { loc ->
            if (loc != null) cb(loc) else if (last == null) cb(null)
        }
    }.onFailure { if (last == null) cb(null) }
}

@Composable
fun BusScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var ready by remember { mutableStateOf(BusData.stops.isNotEmpty()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { BusData.init(ctx) }
        ready = true
        withContext(Dispatchers.IO) { BusData.refreshStaticIfOld(ctx) }
    }

    val pinned by BusData.pinned.collectAsStateWithLifecycle()
    var sel by remember { mutableStateOf<BusSel>(BusSel.None) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var homeTab by remember { mutableIntStateOf(0) }
    var me by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locBusy by remember { mutableStateOf(false) }
    var viewTick by remember { mutableIntStateOf(0) }

    // ---------------------------------------------------------------- ban do
    val mapView = remember { newBusMapView(ctx) }
    val ctl = remember { BusMapController(ctx, mapView) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        mapView.onResume()
        val ml = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { viewTick++; return false }
            override fun onZoom(event: ZoomEvent?): Boolean { viewTick++; return false }
        }
        mapView.addMapListener(ml)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.removeMapListener(ml)
            mapView.onPause()
            mapView.onDetach()
        }
    }
    val dark = Ui.isDark
    LaunchedEffect(dark) { ctl.setDark(dark) }

    fun locate(center: Boolean) {
        locBusy = true
        fetchLocation(ctx) { loc ->
            locBusy = false
            if (loc == null) {
                vm.toastMsg("Chưa lấy được vị trí – kiểm tra GPS/quyền vị trí")
                return@fetchLocation
            }
            me = loc.latitude to loc.longitude
            ctl.showMe(loc.latitude, loc.longitude)
            if (center) ctl.goTo(loc.latitude, loc.longitude, 16.5)
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) locate(true) else vm.toastMsg("Không có quyền vị trí – vẫn tìm trạm bằng ô tìm kiếm được")
    }
    fun askLocate() {
        if (hasLoc(ctx)) locate(true)
        else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) { if (hasLoc(ctx)) locate(false) }

    // ---------------------------------------------------------------- du lieu theo lua chon
    var stopLive by remember { mutableStateOf(Live<List<StopArrivals>>()) }
    var detail by remember { mutableStateOf<RouteDetail?>(null) }
    var geo by remember { mutableStateOf<VarGeo?>(null) }
    var vehLive by remember { mutableStateOf(Live<List<Vehicle>>()) }
    val pinnedLive = remember { mutableStateMapOf<Int, List<StopArrivals>>() }
    var pinnedAt by remember { mutableStateOf(0L) }

    val brand = Ui.Brand.toArgb()
    fun openStop(s: Stop, back: BusSel? = null) {
        sel = BusSel.StopSel(s.id, back); query = ""; searching = false; focus.clearFocus()
        ctl.goTo(s.lat, s.lng)
    }
    fun openRoute(id: Int, varIdx: Int = 0, focusStop: Int? = null) {
        sel = BusSel.RouteSel(id, varIdx, focusStop); query = ""; searching = false; focus.clearFocus()
    }
    fun openPlace(p: Place) {
        sel = BusSel.PlaceSel(p); query = ""; searching = false; focus.clearFocus()
        ctl.showPlace(p.lat, p.lng, brand)
        ctl.goTo(p.lat, p.lng, 16.0)
    }

    // ---------------------------------------------------------------- tim dia diem (v1.5)
    var placeRes by remember { mutableStateOf<List<Place>>(emptyList()) }
    var placeBusy by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        placeRes = emptyList()
        // so tuyen (19, 76, D4...) thi khong can tra dia diem
        if (q.length < 3 || Regex("^[A-Za-z]?\\d{1,3}[A-Za-z]?$").matches(q)) { placeBusy = false; return@LaunchedEffect }
        placeBusy = true
        delay(420)
        placeRes = runCatching { Places.search(q, me ?: (PTIT_CENTER.latitude to PTIT_CENTER.longitude)) }.getOrDefault(emptyList())
        placeBusy = false
    }
    // Nhan giu ban do -> chon diem do nhu 1 dia diem
    DisposableEffect(ctl) {
        ctl.onLongPress = { la, ln ->
            val tmp = Place("Điểm đã chọn", "Đang tìm địa chỉ…", la, ln)
            openPlace(tmp)
            scope.launch {
                val p = Places.reverse(la, ln)
                val cur = sel
                if (cur is BusSel.PlaceSel && cur.place === tmp) sel = BusSel.PlaceSel(p ?: tmp.copy(addr = "%.5f, %.5f".format(java.util.Locale.US, la, ln)))
            }
        }
        onDispose { ctl.onLongPress = null }
    }
    // Ghim do tren ban do theo dia diem dang chon
    LaunchedEffect(sel) {
        val s = sel
        when (s) {
            is BusSel.PlaceSel -> ctl.showPlace(s.place.lat, s.place.lng, brand)
            is BusSel.TripSel -> ctl.showPlace(s.back.place.lat, s.back.place.lng, brand)
            else -> ctl.clearPlace()
        }
    }
    // Tuyen di thang toi dia diem
    var plan by remember { mutableStateOf(Live<List<TripPlan>>()) }
    var planFor by remember { mutableStateOf("") }
    val placeSel = sel as? BusSel.PlaceSel
    val planKey = placeSel?.let { p ->
        val o = me ?: (PTIT_CENTER.latitude to PTIT_CENTER.longitude)
        "%.4f,%.4f>%.3f,%.3f".format(java.util.Locale.US, p.place.lat, p.place.lng, o.first, o.second)
    }
    LaunchedEffect(planKey) {
        val p = placeSel?.place ?: return@LaunchedEffect
        if (planKey == planFor && plan.data != null) return@LaunchedEffect
        val o = me ?: (PTIT_CENTER.latitude to PTIT_CENTER.longitude)
        plan = Live(loading = true)
        plan = try {
            // 1) tuyen di thang hien ngay, 2) tim them cach doi 1 lan tuyen (co the lau hon lan dau)
            val direct = BusData.planDirect(ctx, o.first, o.second, p.lat, p.lng)
            plan = Live(direct.map { TripPlan(it) }, loading = true, at = System.currentTimeMillis())
            var all = try {
                BusData.plan(ctx, o.first, o.second, p.lat, p.lng, direct)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                direct.map { TripPlan(it) }
            }
            // v1.9: khong co cach nao trong 800 m -> noi rong ban kinh di bo len 1,5 km
            if (all.isEmpty()) all = try {
                BusData.plan(ctx, o.first, o.second, p.lat, p.lng, null, maxWalk = 1500.0)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            Live(all, at = System.currentTimeMillis())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Live(error = "Không tìm được tuyến (${e.message ?: "lỗi mạng"})")
        }
        planFor = planKey ?: ""
    }

    // ---------------------------------------------------------------- lich trinh kieu Google Maps (v1.9)
    val tripSel = sel as? BusSel.TripSel
    var tripGeos by remember { mutableStateOf<List<VarGeo?>>(emptyList()) }
    var tripLive by remember { mutableStateOf<List<Live<List<Arrival>>>>(emptyList()) }
    val tripKey = tripSel?.let { it.plan.key + "@" + it.origin }
    LaunchedEffect(tripKey) {
        val t = tripSel ?: run { tripGeos = emptyList(); tripLive = emptyList(); ctl.clearTrip(); return@LaunchedEffect }
        val legs = listOfNotNull(t.plan.first, t.plan.second)
        tripLive = legs.map { Live(loading = true) }
        tripGeos = legs.map { null }
        // lo trinh tung chang (co cache) -> ve len ban do
        launch {
            val gs = legs.map { l -> runCatching { BusData.varGeo(ctx, l.routeId, l.varId) }.getOrNull() }
            tripGeos = gs
            val walks = ArrayList<List<Pair<Double, Double>>>()
            val rides = ArrayList<Pair<List<Pair<Double, Double>>, Int>>()
            val stops = ArrayList<Pair<Stop, Int>>()
            walks += listOf(t.origin, t.plan.first.board.lat to t.plan.first.board.lng)
            for ((i, l) in legs.withIndex()) {
                val c = routeColor(l.routeNo)
                val g = gs[i]
                val sp = g?.let { Itinerary.span(it, l) }
                rides += (if (g != null && sp != null) Itinerary.path(g, sp.first, sp.second)
                else listOf(l.board.lat to l.board.lng, l.alight.lat to l.alight.lng)) to c
                stops += l.board to c
                stops += l.alight to c
            }
            t.plan.second?.let { b -> walks += listOf(t.plan.first.alight.lat to t.plan.first.alight.lng, b.board.lat to b.board.lng) }
            val last = legs.last()
            walks += listOf(last.alight.lat to last.alight.lng, t.back.place.lat to t.back.place.lng)
            ctl.showTrip(walks, rides, stops, if (t.fromMe) null else t.origin, dark) { st -> openStop(st, back = t) }
            val pts = ArrayList<Pair<Double, Double>>()
            pts += t.origin
            rides.forEach { pts += it.first }
            pts += t.back.place.lat to t.back.place.lng
            ctl.fitPoints(pts, 17.0)
        }
        // gio xe song tai tram len cua tung chang (20 s/lan)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                tripLive = legs.mapIndexed { i, l ->
                    try {
                        val list = BusData.arrivals(l.board.id).firstOrNull { it.routeId == l.routeId && it.varId == l.varId }?.arrivals.orEmpty()
                        Live(list, at = System.currentTimeMillis())
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val old = tripLive.getOrNull(i)
                        Live(old?.data, error = e.message ?: "lỗi mạng", at = old?.at ?: 0L)
                    }
                }
                delay(20_000)
            }
        }
    }

    // ---------------------------------------------------------------- tram -> tuyen -> xe (v1.5)
    val arrSel: BusSel.ArrSel? = when (val s = sel) {
        is BusSel.ArrSel -> s
        is BusSel.VehSel -> s.arr
        else -> null
    }
    val arrKey = arrSel?.key
    var arrGeo by remember { mutableStateOf<VarGeo?>(null) }
    var arrLive by remember { mutableStateOf(Live<List<Arrival>>()) }
    var arrSeed by remember { mutableStateOf<Live<List<Arrival>>?>(null) }
    var allStops by remember { mutableStateOf(false) }
    /** ban ghi cuoi cung cua xe dang xem (giu lai khi xe roi khoi du doan = da qua tram) */
    val lastSeen = remember { mutableStateMapOf<String, Arrival>() }
    LaunchedEffect(arrKey) {
        val k = arrKey ?: run { arrGeo = null; arrLive = Live(); lastSeen.clear(); return@LaunchedEffect }
        arrLive = arrSeed ?: Live(loading = true)
        arrSeed = null
        arrGeo = arrGeo?.takeIf { it.routeId == k.routeId && it.varId == k.varId }
        launch {
            if (arrGeo == null) arrGeo = runCatching { BusData.varGeo(ctx, k.routeId, k.varId) }.getOrNull()
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                arrLive = try {
                    val list = BusData.arrivals(k.stopId).firstOrNull { it.routeId == k.routeId && it.varId == k.varId }?.arrivals.orEmpty()
                    list.forEach { lastSeen[it.plate] = it }
                    Live(list, at = System.currentTimeMillis())
                } catch (e: Exception) {
                    Live(arrLive.data, error = "Không tải được giờ xe (${e.message ?: "lỗi mạng"})", at = arrLive.at)
                }
                delay(15_000)
            }
        }
    }
    // Ve ban do cho che do xe dang toi / 1 xe
    var lastFit by remember { mutableStateOf("") }
    LaunchedEffect(sel, arrGeo, arrLive, allStops) {
        val a = arrSel ?: return@LaunchedEffect
        val g = arrGeo ?: return@LaunchedEffect
        val plate = (sel as? BusSel.VehSel)?.plate
        val color = routeColor(a.routeNo)
        val elapsed = if (arrLive.at > 0) ((System.currentTimeMillis() - arrLive.at) / 1000).toInt() else 0
        val buses = tripBuses(g, a.stopId, arrLive.data.orEmpty())
        val stop = g.stops.firstOrNull { it.id == a.stopId }
        if (plate == null) {
            // chi tram cua ban (+ tram xuong) + 2 dau tuyen -> ban do gon, khong roi mat
            val ends = setOfNotNull(g.stops.firstOrNull()?.id, g.stops.lastOrNull()?.id)
            ctl.showRoute(g, color, a.stopId, onlyStops = ends, extraStop = a.alight) { st -> openStop(st) }
        } else {
            val me2 = buses.firstOrNull { it.a.plate == plate }?.veh
                ?: lastSeen[plate]?.let { tripBuses(g, a.stopId, listOf(it)).firstOrNull()?.veh }
            val from = me2?.nextStop ?: g.stops.indexOfFirst { it.id == a.stopId }.coerceAtLeast(0)
            val alightIdx = a.alight?.let { id -> g.stops.indexOfFirst { it.id == id } } ?: -1
            val tIdx = g.stops.indexOfFirst { it.id == a.stopId }
            val keys = keyStopIdx(g, from, *listOf(tIdx, alightIdx).filter { it >= 0 }.toIntArray()).map { g.stops[it].id }.toSet()
            ctl.showRoute(g, color, a.stopId, fromM = me2?.pos, onlyStops = if (allStops) null else keys, extraStop = a.alight) { st ->
                ctl.goTo(st.lat, st.lng)
            }
        }
        ctl.showVehicles(buses.mapNotNull { it.veh }, a.routeNo, color, selected = plate,
            label = { v ->
                val b = buses.firstOrNull { it.a.plate == v.plate }
                val eta = b?.let { (it.a.etaSec - elapsed).coerceAtLeast(0) }
                "\uD83D\uDE8C " + (eta?.let { if (it < 45) "tới" else "${(it + 30) / 60}'" } ?: a.routeNo)
            },
        ) { v -> sel = BusSel.VehSel(a, v.plate) }
        // thu phong 1 lan moi khi doi che do / doi xe
        val fitKey = "${a.key}/$plate"
        if (fitKey != lastFit && (buses.isNotEmpty() || arrLive.data != null)) {
            lastFit = fitKey
            val pts = ArrayList<Pair<Double, Double>>()
            stop?.let { pts += it.lat to it.lng }
            if (plate == null) buses.take(3).mapNotNull { it.veh }.forEach { pts += it.lat to it.lng }
            else buses.firstOrNull { it.a.plate == plate }?.veh?.let { pts += it.lat to it.lng }
            ctl.fitPoints(pts, 17.0)
        }
    }

    // Tram tren ban do (khi khong xem tuyen)
    LaunchedEffect(viewTick, sel, pinned, ready) {
        if (!ready) return@LaunchedEffect
        delay(220)
        val s = sel
        if (s is BusSel.RouteSel || s is BusSel.ArrSel || s is BusSel.VehSel || s is BusSel.TripSel) { ctl.showStops(emptyList(), null, emptySet(), brand) {}; return@LaunchedEffect }
        val bb = mapView.boundingBox
        val list = if (mapView.zoomLevelDouble >= 14.5)
            withContext(Dispatchers.Default) { BusData.stopsIn(bb.latSouth, bb.latNorth, bb.lonWest, bb.lonEast, 220) }
        else pinned.mapNotNull { BusData.stop(it) }
        ctl.showStops(list, (s as? BusSel.StopSel)?.id, pinned.toSet(), brand) { st -> openStop(st) }
    }

    // ETA tram dang chon (15s/lan, chi khi app dang mo)
    LaunchedEffect(sel) {
        val s = sel as? BusSel.StopSel ?: return@LaunchedEffect
        stopLive = Live(loading = true)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                stopLive = try {
                    Live(BusData.arrivals(s.id), at = System.currentTimeMillis())
                } catch (e: Exception) {
                    Live(stopLive.data, error = "Không tải được ETA (${e.message ?: "lỗi mạng"})", at = stopLive.at)
                }
                delay(15_000)
            }
        }
    }

    // Tuyen dang chon: thong tin + duong di + xe
    LaunchedEffect(sel) {
        val s = sel as? BusSel.RouteSel
        if (s == null) {
            detail = null; geo = null; vehLive = Live()
            // che do xe-dang-toi tu ve lai lo trinh/xe, khong xoa o day
            if (sel !is BusSel.ArrSel && sel !is BusSel.VehSel) {
                ctl.showRoute(null, 0, null) {}
                ctl.clearVehicles()
            }
            return@LaunchedEffect
        }
        val d = try {
            detail?.takeIf { it.id == s.id } ?: BusData.routeDetail(ctx, s.id)
        } catch (e: Exception) {
            vehLive = Live(error = "Không tải được tuyến (${e.message ?: "lỗi mạng"})"); return@LaunchedEffect
        }
        detail = d
        val v = d.vars.getOrNull(s.varIdx) ?: d.vars.firstOrNull() ?: return@LaunchedEffect
        val oldGeo = geo
        val g = try { BusData.varGeo(ctx, s.id, v.varId) } catch (e: Exception) {
            vehLive = Live(error = "Không tải được lộ trình (${e.message ?: "lỗi mạng"})"); return@LaunchedEffect
        }
        geo = g
        val color = routeColor(d.no)
        ctl.showRoute(g, color, s.focusStop) { st -> sel = BusSel.RouteSel(s.id, s.varIdx, st.id) }
        if (oldGeo !== g) {
            ctl.clearVehicles()
            val fs = s.focusStop?.let { id -> g.stops.firstOrNull { it.id == id } }
            if (fs != null) ctl.goTo(fs.lat, fs.lng) else ctl.fit(g)
        } else {
            s.focusStop?.let { id -> g.stops.firstOrNull { it.id == id } }?.let { ctl.goTo(it.lat, it.lng) }
        }
        vehLive = Live(vehLive.data, loading = true)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vehLive = try {
                    val list = BusData.vehicles(g)
                    ctl.showVehicles(list, d.no, color) { veh ->
                        vm.toastMsg("${d.no} · ${veh.plate} · sắp tới ${g.stops.getOrNull(veh.nextStop)?.name ?: "—"}")
                    }
                    Live(list, at = System.currentTimeMillis())
                } catch (e: Exception) {
                    Live(vehLive.data, error = "Không tải được vị trí xe (${e.message ?: "lỗi mạng"})", at = vehLive.at)
                }
                delay(15_000)
            }
        }
    }

    // ETA cac tram ghim (30s/lan, khi dang o man chinh)
    LaunchedEffect(sel, pinned, homeTab) {
        if (sel != BusSel.None || homeTab != 0) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                for (id in pinned.take(6)) {
                    runCatching { BusData.arrivals(id) }.onSuccess { pinnedLive[id] = it }
                }
                pinnedAt = System.currentTimeMillis()
                delay(30_000)
            }
        }
    }

    BackHandler(enabled = sel != BusSel.None || searching || query.isNotEmpty()) {
        when {
            searching || query.isNotEmpty() -> { query = ""; searching = false; focus.clearFocus() }
            sel is BusSel.RouteSel && (sel as BusSel.RouteSel).focusStop != null -> sel = (sel as BusSel.RouteSel).copy(focusStop = null)
            sel is BusSel.VehSel -> sel = (sel as BusSel.VehSel).arr
            sel is BusSel.TripSel -> sel = (sel as BusSel.TripSel).back
            sel is BusSel.ArrSel -> sel = (sel as BusSel.ArrSel).let { it.back ?: BusSel.StopSel(it.stopId) }
            sel is BusSel.StopSel -> sel = (sel as BusSel.StopSel).back ?: BusSel.None
            else -> sel = BusSel.None
        }
    }

    val now = rememberNow(1000L)

    Box(Modifier.fillMaxSize().background(Ui.Bg)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // ------------------------------------------------------------ thanh tim kiem
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .elev(10.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ui.Card)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Search, null, tint = Ui.Sub, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Tìm tuyến, trạm hoặc địa điểm…", color = Ui.Faint, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; searching = true },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ui.Ink),
                        cursorBrush = SolidColor(Ui.Brand),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(Ic.Close, "Xoá", tint = Ui.Sub, modifier = Modifier.size(22.dp).clip(CircleShape).clickable { query = ""; searching = false; focus.clearFocus() })
                }
            }
            AnimatedVisibility(query.isNotBlank() && ready, enter = fadeIn() + slideInVertically { -it / 8 }, exit = fadeOut()) {
                val res = remember(query) { BusData.search(query, me) }
                SearchResults(res.first, placeRes, placeBusy, res.second, me, onRoute = { openRoute(it.id) }, onPlace = { openPlace(it) }, onStop = { openStop(it) })
            }
        }

        // ------------------------------------------------------------ nut vi tri
        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 14.dp, top = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapFab(Ic.MyLocation, "Vị trí của tôi", busy = locBusy) { askLocate() }
            MapFab(Ic.School, "Học viện") { ctl.goTo(PTIT_CENTER.latitude, PTIT_CENTER.longitude, 16.5) }
        }

        // ------------------------------------------------------------ panel duoi
        BusSheet(sel = sel, searching = searching || query.isNotBlank(), modifier = Modifier.align(Alignment.BottomCenter)) {
            AnimatedContent(
                targetState = sel,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(300, easing = vn.uisplus.ui.theme.Tok.EmphasizedDecel)) { it / 12 })
                        .togetherWith(fadeOut(tween(110)))
                },
                modifier = Modifier.fillMaxSize(),
                label = "busPanel",
            ) { s ->
                Column(
                    Modifier.fillMaxSize(),
                ) {
                    when (s) {
                        is BusSel.None -> HomePanel(
                            tab = homeTab, onTab = { homeTab = it },
                            pinned = pinned, pinnedLive = pinnedLive, pinnedAt = pinnedAt, now = now,
                            me = me, onLocate = { askLocate() },
                            onStop = { openStop(it) },
                        )
                        is BusSel.StopSel -> {
                            val st = BusData.stop(s.id)
                            if (st != null) StopPanel(
                                st, stopLive, now, pinned = s.id in pinned,
                                onPin = { BusData.togglePin(ctx, st.id); scope.launch { BusWidget.refresh(ctx) } },
                                onClose = { sel = BusSel.None },
                                onBack = s.back?.let { b -> { sel = b } },
                                onRoute = { sa ->
                                    // v1.5: bam tuyen -> danh sach xe dang toi tram nay (gan truoc xa sau)
                                    arrSeed = Live(sa.arrivals, at = stopLive.at)
                                    allStops = false
                                    sel = BusSel.ArrSel(st.id, sa.routeId, sa.varId, sa.routeNo, sa.dest, back = s)
                                },
                            )
                        }
                        is BusSel.ArrSel -> ArrPanel(
                            s.routeNo, s.dest, BusData.stop(s.stopId), arrGeo?.takeIf { it.varId == s.varId }, arrLive, now,
                            onBus = { plate -> allStops = false; sel = BusSel.VehSel(s, plate) },
                            onFullRoute = {
                                scope.launch {
                                    val idx = runCatching { BusData.routeDetail(ctx, s.routeId).vars.indexOfFirst { it.varId == s.varId } }.getOrDefault(0)
                                    openRoute(s.routeId, idx.coerceAtLeast(0), s.stopId)
                                }
                            },
                            onBack = { sel = s.back ?: BusSel.StopSel(s.stopId) },
                            onClose = { sel = BusSel.None },
                        )
                        is BusSel.VehSel -> {
                            val a = s.arr
                            val cur = arrLive.data?.firstOrNull { it.plate == s.plate }
                            VehPanel(
                                a.routeNo, a.dest, s.plate, BusData.stop(a.stopId), a.alight, arrGeo?.takeIf { it.varId == a.varId },
                                arrival = cur ?: lastSeen[s.plate], at = if (cur != null) arrLive.at else 0L,
                                gone = cur == null && arrLive.data != null, now = now,
                                allStops = allStops, onToggleAll = { allStops = !allStops },
                                onStopTap = { st -> ctl.goTo(st.lat, st.lng) },
                                onBack = { sel = a },
                                onClose = { sel = BusSel.None },
                            )
                        }
                        is BusSel.PlaceSel -> PlacePanel(
                            s.place, me, plan,
                            onTrip = { tp: TripPlan ->
                                // v1.9: bam 1 cach di -> lich trinh tung buoc kieu Google Maps
                                val o = me ?: (PTIT_CENTER.latitude to PTIT_CENTER.longitude)
                                sel = BusSel.TripSel(tp, s, o, me != null)
                            },
                            onStop = { st -> openStop(st, back = s) },
                            onLocate = { askLocate() },
                            onClose = { sel = BusSel.None },
                        )
                        is BusSel.TripSel -> TripPanel(
                            s.plan, s.back.place, if (s.fromMe) "Vị trí của bạn" else "Học viện (PTIT)",
                            tripGeos, tripLive, now,
                            onLeg = { t: DirectTrip ->
                                arrSeed = null
                                allStops = false
                                sel = BusSel.ArrSel(t.board.id, t.routeId, t.varId, t.routeNo, t.dest, alight = t.alight.id, back = s)
                            },
                            onStopTap = { st -> ctl.goTo(st.lat, st.lng) },
                            onBack = { sel = s.back },
                            onClose = { sel = BusSel.None },
                        )
                        is BusSel.RouteSel -> RoutePanel(
                            s, detail, geo, vehLive, now,
                            onVar = { sel = BusSel.RouteSel(s.id, it, null) },
                            onStop = { st -> sel = s.copy(focusStop = st.id) },
                            onOpenStop = { st -> openStop(st) },
                            onClose = { sel = BusSel.None },
                        )
                    }
                }
            }
        }

        if (!ready) {
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Ui.Card).padding(18.dp)) {
                CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
        if (query.isBlank()) Text(
            MAP_ATTRIBUTION,
            color = if (dark) Color(0xFFB8BEC8) else Color(0xFF555555), fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 18.dp, top = 76.dp)
                .clip(RoundedCornerShape(6.dp)).background(if (dark) Color(0xAA15181D) else Color(0xAAFFFFFF)).padding(horizontal = 4.dp),
        )
    }
}

// ==================================================================== thanh phan

@Composable
private fun MapFab(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, busy: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .elev(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Ui.Card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(icon, desc, tint = Ui.Ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun RouteBadge(no: String, big: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(routeColor(no)))
            .padding(horizontal = if (big) 10.dp else 8.dp, vertical = if (big) 5.dp else 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(no, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (big) 16.sp else 13.sp, maxLines = 1)
    }
}

@Composable
private fun SegTabs(items: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 14.dp).clip(RoundedCornerShape(16.dp)).background(Ui.Soft).padding(3.dp),
    ) {
        items.forEachIndexed { i, t ->
            val on = i == sel
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Ui.Card else Color.Transparent)
                    .clickable { onSel(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(t, color = if (on) Ui.Ink else Ui.Sub, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SearchResults(
    routes: List<RouteInfo>, places: List<Place>, placeBusy: Boolean, stops: List<Stop>, me: Pair<Double, Double>?,
    onRoute: (RouteInfo) -> Unit, onPlace: (Place) -> Unit, onStop: (Stop) -> Unit,
) {
    LazyColumn(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .elev(10.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Card),
    ) {
        if (routes.isEmpty() && stops.isEmpty() && places.isEmpty() && !placeBusy) item {
            Text("Không thấy tuyến/trạm/địa điểm nào", color = Ui.Sub, modifier = Modifier.padding(18.dp))
        }
        if (routes.isNotEmpty()) item { ListHeader("Tuyến") }
        items(routes, key = { "r${it.id}" }) { r ->
            Row(
                Modifier.fillMaxWidth().clickable { onRoute(r) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteBadge(r.no)
                Spacer(Modifier.width(12.dp))
                Text(r.name, color = Ui.Ink, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // 3 tram khop nhat len truoc, roi dia diem, roi cac tram con lai
        if (stops.isNotEmpty()) item { ListHeader("Trạm") }
        items(stops.take(3), key = { "s${it.id}" }) { s ->
            StopRow(s, me?.let { BusData.distM(it.first, it.second, s.lat, s.lng) }, null) { onStop(s) }
        }
        if (places.isNotEmpty() || placeBusy) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListHeader("Địa điểm")
                if (placeBusy) CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.padding(start = 8.dp, top = 8.dp).size(12.dp), strokeWidth = 2.dp)
            }
        }
        items(places, key = { "p${it.lat},${it.lng},${it.name}" }) { p ->
            PlaceRow(p, me?.let { BusData.distM(it.first, it.second, p.lat, p.lng) }) { onPlace(p) }
        }
        if (stops.size > 3) item { ListHeader("Trạm khác") }
        items(stops.drop(3), key = { "s${it.id}" }) { s ->
            StopRow(s, me?.let { BusData.distM(it.first, it.second, s.lat, s.lng) }, null) { onStop(s) }
        }
    }
}

@Composable
internal fun ListHeader(t: String) {
    Text(t.uppercase(), color = Ui.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
internal fun StopRow(s: Stop, dist: Double?, summary: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Place, null, tint = Ui.Brand, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = summary ?: listOfNotNull(s.code.takeIf { it.isNotBlank() }, s.routes.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "Tuyến ")).joinToString(" — ")
            Text(sub, color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (dist != null) Text(distText(dist), color = Ui.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun HomePanel(
    tab: Int, onTab: (Int) -> Unit,
    pinned: List<Int>, pinnedLive: Map<Int, List<StopArrivals>>, pinnedAt: Long, now: Long,
    me: Pair<Double, Double>?, onLocate: () -> Unit,
    onStop: (Stop) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        SegTabs(listOf("Đã ghim", "Gần tôi"), tab, onTab)
        Spacer(Modifier.weight(1f))
        if (tab == 0 && pinnedAt > 0) Text("cập nhật ${relTime(pinnedAt, now)}", color = Ui.Faint, fontSize = 11.sp)
    }
    Spacer(Modifier.size(6.dp))
    if (tab == 0) {
        if (pinned.isEmpty()) {
            Text("Chưa ghim trạm nào. Bấm vào trạm trên bản đồ rồi chọn ☆ để ghim.", color = Ui.Sub, fontSize = 13.sp,
                modifier = Modifier.padding(16.dp))
        } else LazyColumn(Modifier.padding(bottom = 8.dp)) {
            items(pinned, key = { it }) { id ->
                val st = BusData.stop(id) ?: return@items
                val live = pinnedLive[id]
                Row(
                    Modifier.fillMaxWidth().clickable { onStop(st) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(st.name, color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(st.code, st.addr).filter { it.isNotBlank() }.joinToString(" · "), color = Ui.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            live == null -> Text("…", color = Ui.Faint)
                            live.none { it.arrivals.isNotEmpty() } -> Text("Chưa có xe", color = Ui.Faint, fontSize = 12.sp)
                            else -> live.filter { it.arrivals.isNotEmpty() }.take(3).forEach { sa ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RouteBadge(sa.routeNo)
                                    Text(" " + etaText(sa.arrivals.first().etaSec), color = Ui.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        if (me == null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bật vị trí để xem các trạm quanh bạn", color = Ui.Sub, fontSize = 13.sp)
                Spacer(Modifier.size(10.dp))
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Ui.Brand).clickable(onClick = onLocate).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text("Dùng vị trí của tôi", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            val near = remember(me) { BusData.nearest(me.first, me.second, 15, 1500.0) }
            if (near.isEmpty()) Text("Không có trạm nào trong 1,5 km", color = Ui.Sub, modifier = Modifier.padding(16.dp))
            else LazyColumn(Modifier.padding(bottom = 8.dp)) {
                items(near, key = { it.first.id }) { (s, d) -> StopRow(s, d, null) { onStop(s) } }
            }
        }
    }
}

@Composable
internal fun PanelHeader(
    title: String, sub: String, onClose: () -> Unit,
    lead: @Composable (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null, onBack: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 6.dp else 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Icon(Ic.Left, "Quay lại", tint = Ui.Ink, modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack).padding(6.dp))
            Spacer(Modifier.width(2.dp))
        }
        if (lead != null) { lead(); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ui.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = Ui.Sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        Icon(Ic.Close, "Đóng", tint = Ui.Sub, modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose).padding(7.dp))
    }
}

@Composable
private fun StopPanel(
    st: Stop, live: Live<List<StopArrivals>>, now: Long, pinned: Boolean,
    onPin: () -> Unit, onClose: () -> Unit, onRoute: (StopArrivals) -> Unit, onBack: (() -> Unit)? = null,
) {
    PanelHeader(st.name, listOf(st.code, st.addr).filter { it.isNotBlank() }.joinToString(" · "), onClose, onBack = onBack, trailing = {
        Icon(
            if (pinned) Ic.Star else Ic.StarOutline, if (pinned) "Bỏ ghim" else "Ghim",
            tint = if (pinned) Color(0xFFF59E0B) else Ui.Sub,
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onPin).padding(7.dp),
        )
    })
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (live.loading && live.data == null) {
            CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text("  Đang lấy giờ xe tới…", color = Ui.Sub, fontSize = 12.sp)
        } else if (live.at > 0) {
            PulseDot(Color(0xFF10B981), Modifier.size(10.dp), 7.dp)
            Text("  Trực tiếp · cập nhật ${relTime(live.at, now)}", color = Ui.Sub, fontSize = 12.sp)
        }
    }
    live.error?.let { Text(it, color = Color(0xFFDC2626), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
    val list = live.data.orEmpty()
    val noBus = st.routes.filter { r -> list.none { it.routeNo == r } }
    LazyColumn(Modifier.padding(bottom = 8.dp)) {
        items(list, key = { "${it.routeId}/${it.varId}" }) { sa ->
            Row(
                Modifier.fillMaxWidth().clickable { onRoute(sa) }.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteBadge(sa.routeNo, big = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("→ ${sa.dest}", color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val first = sa.arrivals.firstOrNull()
                    val more = sa.arrivals.drop(1).take(2).joinToString(" · ") { etaText(it.etaSec) }
                    Text(
                        listOfNotNull(first?.let { "${it.plate} · còn ${distText(it.distM.toDouble())}" }, more.takeIf { it.isNotEmpty() }?.let { "sau: $it" }).joinToString("  —  "),
                        color = Ui.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val eta = sa.arrivals.firstOrNull()?.etaSec
                Text(
                    eta?.let { etaText(it) } ?: "—",
                    color = if (eta != null && eta < 300) Color(0xFF059669) else Ui.Ink,
                    fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                )
            }
        }
        if (noBus.isNotEmpty() && live.data != null) item {
            Text("Chưa có xe sắp tới: ${noBus.joinToString(", ")}", color = Ui.Faint, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun RoutePanel(
    s: BusSel.RouteSel, d: RouteDetail?, geo: VarGeo?, veh: Live<List<Vehicle>>, now: Long,
    onVar: (Int) -> Unit, onStop: (Stop) -> Unit, onOpenStop: (Stop) -> Unit, onClose: () -> Unit,
) {
    val info = BusData.route(s.id)
    val no = d?.no ?: info?.no ?: "…"
    PanelHeader(d?.name ?: info?.name ?: "Đang tải tuyến…",
        listOfNotNull(d?.opTime?.takeIf { it.isNotBlank() }?.let { "Hoạt động $it" }, d?.headway?.takeIf { it.isNotBlank() }?.let { "$it phút/chuyến" }).joinToString(" · "),
        onClose, lead = { RouteBadge(no, big = true) })
    if (d != null && d.vars.size > 1) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            d.vars.forEachIndexed { i, v ->
                val on = i == s.varIdx
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (on) Color(routeColor(no)) else Ui.Soft)
                        .clickable { onVar(i) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text("→ ${v.short.ifBlank { v.name }}", color = if (on) Color.White else Ui.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
    val vs = veh.data
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            veh.loading && vs == null -> {
                CircularProgressIndicator(color = Ui.Brand, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("  Đang tìm xe trên tuyến…", color = Ui.Sub, fontSize = 12.sp)
            }
            vs != null -> {
                PulseDot(Color(0xFF10B981), Modifier.size(10.dp), 7.dp)
                Text("  ${vs.size} xe chiều này · vị trí ước lượng · ${relTime(veh.at, now)}", color = Ui.Sub, fontSize = 12.sp)
            }
        }
    }
    veh.error?.let { Text(it, color = Color(0xFFDC2626), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
    if (geo == null) return
    val byNext = vs.orEmpty().groupBy { it.nextStop }
    val color = Color(routeColor(no))
    LazyColumn(Modifier.padding(bottom = 8.dp)) {
        items(geo.stops.size, key = { geo.stops[it].id * 1000 + it }) { i ->
            val st = geo.stops[i]
            val focused = st.id == s.focusStop
            val buses = byNext[i].orEmpty()
            Row(
                Modifier.fillMaxWidth()
                    .background(if (focused) color.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { if (focused) onOpenStop(st) else onStop(st) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(22.dp).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(if (i == 0 || i == geo.stops.size - 1) 14.dp else 10.dp).clip(CircleShape)
                            .background(if (i == 0 || i == geo.stops.size - 1) color else Ui.Card)
                            .border(2.dp, color, CircleShape),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(st.name, color = Ui.Ink, fontSize = 14.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (focused) Text("Bấm lần nữa để xem giờ xe tới trạm này", color = Ui.Sub, fontSize = 11.sp)
                }
                if (buses.isNotEmpty()) {
                    Text("\uD83D\uDE8C" + (if (buses.size > 1) "×${buses.size}" else ""), fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        if (d != null && (d.orgs.isNotBlank() || d.tickets.isNotBlank())) item {
            Text(listOf(d.orgs, d.tickets).filter { it.isNotBlank() }.joinToString("\n"), color = Ui.Faint, fontSize = 11.sp,
                modifier = Modifier.padding(16.dp))
        }
    }
}

// ==================================================================== bottom sheet 3 nac (v1.4)

/**
 * Sheet keo 3 nac: 0 thu gon (chi phan dau) · 1 nua man · 2 gan full (duoi thanh tim kiem).
 * Keo o tay cam/phan dau; danh sach ben trong van cuon binh thuong. Bam tay cam = len 1 nac.
 * Khong chan cham len ban do: lop ngoai khong co pointer input.
 */
@Composable
private fun BusSheet(sel: BusSel, searching: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxSize().navigationBarsPadding().padding(bottom = NAV_BAR_HEIGHT),
    ) {
        val topGap = androidx.compose.foundation.layout.WindowInsets.statusBars
            .let { with(density) { it.getTop(density).toDp() } } + 76.dp
        val snaps = with(density) {
            val full = (maxHeight - topGap).toPx()
            listOf(132.dp.toPx(), minOf(360.dp.toPx(), full * 0.6f), full.coerceAtLeast(400.dp.toPx()))
        }
        var detent by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(1) }
        val h = remember { androidx.compose.animation.core.Animatable(snaps[1]) }
        val spec = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.86f, stiffness = 420f)
        fun settle(to: Int, velocity: Float = 0f) {
            detent = to.coerceIn(0, 2)
            scope.launch { h.animateTo(snaps[detent], spec, initialVelocity = velocity) }
        }
        // Doi man hinh / xoay may: bam lai nac hien tai
        LaunchedEffect(snaps[2], snaps[1]) { h.snapTo(snaps[detent]) }
        // Chon tram/tuyen moi ma sheet dang thu gon -> mo nua man
        val selKey = when (sel) {
            is BusSel.StopSel -> "s" + sel.id
            is BusSel.RouteSel -> "r" + sel.id
            is BusSel.ArrSel -> "a" + sel.key
            is BusSel.VehSel -> "v" + sel.plate
            is BusSel.PlaceSel -> "p" + sel.place.lat + "," + sel.place.lng
            is BusSel.TripSel -> "t" + sel.plan.key
            else -> "none"
        }
        LaunchedEffect(selKey) { if (selKey != "none" && detent == 0) settle(1) }
        // Dang go tim kiem -> thu gon de nhuong cho ket qua
        LaunchedEffect(searching) { if (searching && detent != 0) settle(0) }

        val drag = androidx.compose.foundation.gestures.rememberDraggableState { d ->
            scope.launch { h.snapTo((h.value - d).coerceIn(snaps[0] * 0.85f, snaps[2])) }
        }
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { h.value.toDp() })
                .elev(16.dp, shape)
                .clip(shape)
                .background(Ui.Card)
                .draggable(
                    drag,
                    androidx.compose.foundation.gestures.Orientation.Vertical,
                    onDragStopped = { v ->
                        val cur = h.value
                        val target = when {
                            v < -1200f -> snaps.indexOfFirst { it > cur + 1f }.let { if (it < 0) 2 else it }
                            v > 1200f -> snaps.indexOfLast { it < cur - 1f }.coerceAtLeast(0)
                            else -> snaps.indices.minBy { kotlin.math.abs(snaps[it] - cur) }
                        }
                        settle(target, -v)
                    },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                        settle(if (detent >= 2) 0 else detent + 1)
                    }
                    .padding(top = 8.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Ui.Line))
            }
            Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        }
    }
}
