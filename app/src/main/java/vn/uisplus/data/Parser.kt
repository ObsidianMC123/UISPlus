package vn.uisplus.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun JsonObject.str(k: String): String {
    val p = this[k] as? JsonPrimitive ?: return ""
    if (p is JsonNull) return ""
    return p.content
}

internal fun JsonObject.int(k: String): Int = str(k).trim().let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() ?: 0 }

internal fun JsonObject.bool(k: String): Boolean {
    val p = this[k] as? JsonPrimitive ?: return false
    if (p is JsonNull) return false
    return p.booleanOrNull ?: (p.content == "1" || p.content.equals("true", true))
}

internal fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
internal fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
internal fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

internal fun parseDmy(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim(), DMY) }.getOrNull()

internal fun parseIsoDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()

private fun hmToMin(s: String): Int? {
    val p = s.trim().split(":")
    if (p.size < 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    return h * 60 + m
}

/** "2A08-2A08" -> "2A08", "2E05-Lý thuyết" -> "2E05 · Lý thuyết" */
fun cleanRoom(raw: String): String {
    val s = raw.trim()
    val i = s.indexOf('-')
    if (i <= 0) return s
    val a = s.substring(0, i).trim()
    val b = s.substring(i + 1).trim()
    return if (b.isEmpty() || b.equals(a, true)) a else "$a · $b"
}

object Parser {

    data class SemesterList(val current: Int, val semesters: List<Semester>)

    fun semesters(data: JsonObject): SemesterList {
        val list = data.arr("ds_hoc_ky").map {
            Semester(
                hocKy = it.int("hoc_ky"),
                name = it.str("ten_hoc_ky"),
                startEpochDay = parseDmy(it.str("ngay_bat_dau_hk"))?.toEpochDay(),
                endEpochDay = parseDmy(it.str("ngay_ket_thuc_hk"))?.toEpochDay(),
            )
        }.filter { it.hocKy > 0 }
        var cur = data.int("hoc_ky_theo_ngay_hien_tai")
        if (cur == 0) cur = list.maxOfOrNull { it.hocKy } ?: 0
        return SemesterList(cur, list)
    }

    fun weekTimetable(hocKy: Int, hocKyName: String, data: JsonObject): Timetable {
        val periods = data.arr("ds_tiet_trong_ngay").map {
            Period(it.int("tiet"), it.str("gio_bat_dau"), it.str("gio_ket_thuc"))
        }.sortedBy { it.tiet }
        val startOf = periods.associate { it.tiet to (hmToMin(it.start) ?: 0) }
        val endOf = periods.associate { it.tiet to (hmToMin(it.end) ?: 0) }

        val weeks = mutableListOf<Week>()
        val sessions = LinkedHashMap<String, ClassSession>()
        for (w in data.arr("ds_tuan_tkb")) {
            val tuan = w.int("tuan_hoc_ky")
            val ws = parseDmy(w.str("ngay_bat_dau"))
            val we = parseDmy(w.str("ngay_ket_thuc"))
            if (ws != null && we != null) {
                weeks += Week(tuan, ws.toEpochDay(), we.toEpochDay(), w.str("thong_tin_tuan"))
            }
            for (t in w.arr("ds_thoi_khoa_bieu")) {
                val date = parseIsoDate(t.str("ngay_hoc")) ?: continue
                val tbd = t.int("tiet_bat_dau").coerceAtLeast(1)
                val st = t.int("so_tiet").coerceAtLeast(1)
                val last = tbd + st - 1
                val sMin = startOf[tbd] ?: (7 * 60 + (tbd - 1) * 50)
                val eMin = endOf[last] ?: (sMin + st * 50)
                val idTkb = t.str("id_tkb")
                var key = "$idTkb|${date.toEpochDay()}|$tbd"
                var n = 2
                while (sessions.containsKey(key)) key = "$idTkb|${date.toEpochDay()}|$tbd#${n++}"
                sessions[key] = ClassSession(
                    key = key,
                    idTkb = idTkb,
                    maMon = t.str("ma_mon"),
                    tenMon = t.str("ten_mon").ifBlank { t.str("ma_mon") },
                    epochDay = date.toEpochDay(),
                    thu = t.int("thu_kieu_so"),
                    tietBd = tbd,
                    soTiet = st,
                    startMin = sMin,
                    endMin = eMin,
                    phong = cleanRoom(t.str("ma_phong")),
                    giangVien = t.str("ten_giang_vien").trim(),
                    nhom = t.str("ma_nhom"),
                    toTh = t.str("ma_to_th"),
                    lop = t.str("ma_lop"),
                    coSo = t.str("ma_co_so"),
                    soTinChi = t.str("so_tin_chi"),
                    nghiDay = t.bool("is_nghi_day"),
                    dayBu = t.bool("is_day_bu"),
                    tuan = tuan,
                    hocKy = hocKy,
                )
            }
        }
        return Timetable(
            hocKy = hocKy,
            hocKyName = hocKyName,
            periods = periods,
            weeks = weeks.sortedBy { it.startEpochDay },
            sessions = sessions.values.sortedWith(compareBy({ it.epochDay }, { it.startMin })),
        )
    }
}
