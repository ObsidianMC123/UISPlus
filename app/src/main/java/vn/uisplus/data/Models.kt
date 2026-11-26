package vn.uisplus.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

val VN_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

@Serializable
data class Period(val tiet: Int, val start: String, val end: String)

@Serializable
data class ClassSession(
    val key: String,
    val idTkb: String,
    val maMon: String,
    val tenMon: String,
    val epochDay: Long,
    val thu: Int,
    val tietBd: Int,
    val soTiet: Int,
    val startMin: Int,
    val endMin: Int,
    val phong: String,
    val giangVien: String = "",
    val nhom: String = "",
    val toTh: String = "",
    val lop: String = "",
    val coSo: String = "",
    val soTinChi: String = "",
    val nghiDay: Boolean = false,
    val dayBu: Boolean = false,
    val tuan: Int = 0,
    val hocKy: Int = 0,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val startMillis: Long get() = date.atStartOfDay(VN_ZONE).plusMinutes(startMin.toLong()).toInstant().toEpochMilli()
    val endMillis: Long get() = date.atStartOfDay(VN_ZONE).plusMinutes(endMin.toLong()).toInstant().toEpochMilli()
    val timeText: String get() = "${hm(startMin)} – ${hm(endMin)}"
    val tietText: String get() = if (soTiet <= 1) "Tiết $tietBd" else "Tiết $tietBd–${tietBd + soTiet - 1}"
}

@Serializable
data class Week(val tuan: Int, val startEpochDay: Long, val endEpochDay: Long, val label: String)

@Serializable
data class Semester(val hocKy: Int, val name: String, val startEpochDay: Long? = null, val endEpochDay: Long? = null)

@Serializable
data class Timetable(
    val hocKy: Int,
    val hocKyName: String,
    val periods: List<Period>,
    val weeks: List<Week>,
    val sessions: List<ClassSession>,
)

@Serializable
data class ChangeEvent(
    val at: Long,
    val type: String,
    val title: String,
    val detail: String,
    val sessionKey: String? = null,
) {
    companion object {
        const val ADDED = "added"
        const val REMOVED = "removed"
        const val ROOM = "room"
        const val TIME = "time"
        const val CANCEL = "cancel"
        const val MAKEUP = "makeup"
        const val SUMMARY = "summary"
    }
}

@Serializable
data class AppData(
    val userName: String,
    val studentName: String,
    val currentHocKy: Int,
    val semesters: List<Semester>,
    val timetables: List<Timetable>,
    val changes: List<ChangeEvent> = emptyList(),
    val lastSync: Long = 0,
) {
    val sessions: List<ClassSession> by lazy {
        timetables.flatMap { it.sessions }.distinctBy { it.key }.sortedWith(compareBy({ it.epochDay }, { it.startMin }))
    }
    val weeks: List<Week> by lazy {
        timetables.flatMap { it.weeks }.distinctBy { it.startEpochDay }.sortedBy { it.startEpochDay }
    }
    val currentSemesterName: String
        get() = semesters.firstOrNull { it.hocKy == currentHocKy }?.name
            ?: timetables.firstOrNull()?.hocKyName ?: ""

    fun sessionsOn(epochDay: Long): List<ClassSession> = sessions.filter { it.epochDay == epochDay }
}

fun hm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

private val THU_SHORT = arrayOf("", "CN", "T2", "T3", "T4", "T5", "T6", "T7")
private val THU_LONG = arrayOf("", "Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")

/** java DayOfWeek (1=Mon..7=Sun) -> "T2".."CN" */
fun dowShort(date: LocalDate): String = THU_SHORT[if (date.dayOfWeek.value == 7) 1 else date.dayOfWeek.value + 1]
fun dowLong(date: LocalDate): String = THU_LONG[if (date.dayOfWeek.value == 7) 1 else date.dayOfWeek.value + 1]
