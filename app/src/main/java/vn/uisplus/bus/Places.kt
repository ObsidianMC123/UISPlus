package vn.uisplus.bus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Mot dia diem (POI / dia chi) tim duoc, hoac diem nguoi dung nhan giu tren ban do. */
data class Place(
    val name: String,
    val addr: String,
    val lat: Double,
    val lng: Double,
    /** nhan loai dia diem (Truong hoc, Cho, ...) - co the rong */
    val kind: String = "",
)

/**
 * Tim dia diem kieu Google Maps / MultiGo, KHONG can key:
 *  - go tim (search-as-you-type): Photon (photon.komoot.io, du lieu OSM) - ho tro go khong dau,
 *    uu tien gan vi tri, gioi han khung TP.HCM.
 *  - nhan giu ban do -> dia chi: Nominatim reverse (1 lan / thao tac, dung chinh sach).
 * Da thu that 26/11/2026: "cho ben thanh", "vincom thu duc", "dai hoc bach khoa", "san bay tan son nhat" deu ra dung.
 */
object Places {
    private const val UA = "UISPlus/1.5 (Android; vn.uisplus.app)"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cache = object : LinkedHashMap<String, List<Place>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>?) = size > 40
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .header("Accept-Language", "vi")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
    }

    /** Luon dung dau cham thap phan (locale vi dung dau phay -> sai tham so URL). */
    private fun f5(v: Double) = String.format(java.util.Locale.US, "%.5f", v)

    private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()

    private fun kindOf(key: String, value: String): String = when (value) {
        "university", "college" -> "Đại học"
        "school", "kindergarten" -> "Trường học"
        "marketplace" -> "Chợ"
        "mall", "department_store" -> "Trung tâm thương mại"
        "supermarket", "convenience" -> "Siêu thị"
        "hospital", "clinic" -> "Bệnh viện"
        "aerodrome", "terminal" -> "Sân bay"
        "station" -> "Nhà ga"
        "bus_station" -> "Bến xe"
        "park" -> "Công viên"
        "cinema", "theatre" -> "Giải trí"
        "restaurant", "fast_food", "food_court" -> "Ăn uống"
        "cafe" -> "Cà phê"
        "place_of_worship" -> "Tôn giáo"
        "museum", "attraction", "viewpoint" -> "Tham quan"
        "hotel", "hostel", "guest_house" -> "Lưu trú"
        "apartments", "residential" -> if (key == "building" || key == "landuse") "Khu dân cư" else "Đường"
        else -> when (key) {
            "highway" -> "Đường"
            "place", "boundary" -> "Khu vực"
            "building" -> "Toà nhà"
            "shop" -> "Cửa hàng"
            "office" -> "Văn phòng"
            else -> ""
        }
    }

    /** Tim dia diem trong TP.HCM; [near] = (lat, lng) de uu tien ket qua gan. */
    suspend fun search(q: String, near: Pair<Double, Double>?): List<Place> {
        val query = q.trim()
        if (query.length < 2) return emptyList()
        val ck = BusData.fold(query) + (near?.let { "@%.2f,%.2f".format(it.first, it.second) } ?: "")
        synchronized(cache) { cache[ck] }?.let { return it }
        val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "12")
            .addQueryParameter("bbox", "106.3,10.3,107.1,11.2")
            .apply { near?.let { addQueryParameter("lat", f5(it.first)); addQueryParameter("lon", f5(it.second)) } }
            .build().toString()
        val root = json.parseToJsonElement(get(url)).jsonObject
        val seen = HashSet<String>()
        val out = (root["features"] as? JsonArray).orEmpty().mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val p = o["properties"] as? JsonObject ?: return@mapNotNull null
            val c = (o["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray ?: return@mapNotNull null
            val lng = (c.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lat = (c.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val key = p.s("osm_key")
            val value = p.s("osm_value")
            // tram xe buyt da co san trong danh sach tram cua app
            if (value == "bus_stop" || value == "platform" || value == "stop_position") return@mapNotNull null
            val street = listOf(p.s("housenumber"), p.s("street")).filter { it.isNotBlank() }.joinToString(" ")
            val name = p.s("name").ifBlank { street }.ifBlank { return@mapNotNull null }
            val addr = listOf(street.takeIf { it != name }.orEmpty(), p.s("district"), p.s("city").ifBlank { p.s("county") })
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
            val dk = BusData.fold(name) + "@" + "%.3f,%.3f".format(lat, lng)
            if (!seen.add(dk)) return@mapNotNull null
            Place(name, addr, lat, lng, kindOf(key, value))
        }.take(7)
        synchronized(cache) { cache[ck] = out }
        return out
    }

    /** Dia chi cua 1 diem (nhan giu ban do). Loi mang -> null. */
    suspend fun reverse(lat: Double, lng: Double): Place? = runCatching {
        val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("zoom", "18")
            .addQueryParameter("accept-language", "vi")
            .addQueryParameter("lat", f5(lat))
            .addQueryParameter("lon", f5(lng))
            .build().toString()
        val o = json.parseToJsonElement(get(url)).jsonObject
        val parts = o.s("display_name").split(",").map { it.trim() }
            .filter { it.isNotBlank() && it != "Việt Nam" && !it.all(Char::isDigit) }
        if (parts.isEmpty()) return@runCatching null
        val name = o.s("name").ifBlank { parts.take(2).joinToString(" ") }
        val addr = parts.filter { it != name }.take(4).joinToString(", ")
        Place(name, addr, lat, lng)
    }.getOrNull()
}
