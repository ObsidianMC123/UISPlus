package vn.uisplus.bus

/**
 * v1.9: lich trinh tung buoc kieu Google Maps cho 1 cach di (TripPlan):
 *   di bo -> cho xe -> di xe [-> di bo doi tram -> cho xe -> di xe] -> di bo -> toi noi.
 * Gio cho lay tu ETA song EBMS tai tram len cua tung chang: chon xe SOM NHAT toi tram
 * SAU khi minh di bo toi (tre <= 30 s van kip vi xe dung don khach).
 */
data class LegTiming(
    val leg: DirectTrip,
    /** giay (tinh tu bay gio) luc toi tram len */
    val reach: Double,
    /** xe se bat duoc (ETA da tinh tu bay gio); null = chua co du lieu song */
    val bus: Arrival?,
    /** giay luc len xe */
    val depart: Double,
    /** giay ngoi tren xe */
    val ride: Double,
    /** giay luc xuong xe */
    val alight: Double,
) {
    val waitSec: Double get() = (depart - reach).coerceAtLeast(0.0)
}

data class Itin(
    val legs: List<LegTiming>,
    val walkStart: Double,
    val walkXfer: Double,
    val walkEnd: Double,
    /** giay tu bay gio toi khi den noi */
    val total: Double,
    /** true = co chang chua co gio xe song (gio cho la uoc doan/chua tinh) */
    val guessed: Boolean,
)

object Itinerary {
    const val CATCH_SLACK = 30
    /** chua co gio xe song cho chang 2 -> tam tinh cho 5 phut (giong planTransfer) */
    const val GUESS_WAIT = 300.0
    /** EBMS chi bao xe trong khoang ~1 gio toi; xe xa hon coi nhu khong co */
    private const val MAX_ETA = 3 * 3600

    fun walkSec(m: Double): Double = m / BusData.WALK_MPS

    /** Xe som nhat toi tram sau luc minh toi tram [reachSec] (ETA tinh tu bay gio). */
    fun catchable(arrs: List<Arrival>?, reachSec: Double): Arrival? =
        arrs?.filter { it.etaSec >= reachSec - CATCH_SLACK && it.etaSec <= MAX_ETA }?.minByOrNull { it.etaSec }

    /** [live] = ETA song tai tram len cua chang 0, 1 (da tru thoi gian troi qua), null = chua co. */
    fun build(tp: TripPlan, live: List<List<Arrival>?>): Itin {
        val legs = listOfNotNull(tp.first, tp.second)
        val out = ArrayList<LegTiming>(2)
        val walkStart = walkSec(tp.first.walkTo)
        val walkXfer = if (tp.second != null) walkSec(tp.xferWalk) else 0.0
        var t = walkStart
        var guessed = false
        for ((i, leg) in legs.withIndex()) {
            if (i == 1) t += walkXfer
            val reach = t
            val bus = catchable(live.getOrNull(i), reach)
            val depart = when {
                bus != null -> maxOf(reach, bus.etaSec.toDouble())
                i == 0 -> { guessed = true; reach }          // chang dau: chua tinh cho
                else -> { guessed = true; reach + GUESS_WAIT }
            }
            val ride = leg.rideM / BusData.BUS_MPS
            t = depart + ride
            out += LegTiming(leg, reach, bus, depart, ride, t)
        }
        val walkEnd = walkSec(legs.last().walkFrom)
        return Itin(out, walkStart, walkXfer, walkEnd, t + walkEnd, guessed)
    }

    /** Index tram len / xuong cua 1 chang tren lo trinh (tram xuong phai SAU tram len). */
    fun span(g: VarGeo, leg: DirectTrip): Pair<Int, Int>? {
        val i = g.stops.indexOfFirst { it.id == leg.board.id }
        if (i < 0) return null
        val k = (i + 1 until g.stops.size).firstOrNull { g.stops[it].id == leg.alight.id } ?: return null
        return i to k
    }

    /** Doan duong xe chay that (theo path cua tuyen) giua tram [i] va tram [k]. */
    fun path(g: VarGeo, i: Int, k: Int): List<Pair<Double, Double>> {
        if (g.lats.size < 2) return listOf(g.stops[i].lat to g.stops[i].lng, g.stops[k].lat to g.stops[k].lng)
        val a = g.stopPos[i]
        val b = g.stopPos[k]
        val out = ArrayList<Pair<Double, Double>>()
        out += g.pointAt(a)
        for (j in g.cum.indices) if (g.cum[j] > a && g.cum[j] < b) out += g.lats[j] to g.lngs[j]
        out += g.pointAt(b)
        return out
    }
}
