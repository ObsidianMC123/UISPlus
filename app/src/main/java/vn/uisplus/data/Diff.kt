package vn.uisplus.data

import java.time.Instant
import java.time.format.DateTimeFormatter

/** So sanh TKB cu/moi -> danh sach thay doi (chi xet cac buoi tu hom nay tro di). */
object Diff {
    private val DM = DateTimeFormatter.ofPattern("dd/MM")

    private fun whenText(s: ClassSession) = "${dowShort(s.date)} ${s.date.format(DM)}, ${s.timeText}"

    fun compute(old: List<ClassSession>, new: List<ClassSession>, now: Long = System.currentTimeMillis()): List<ChangeEvent> {
        val today = Instant.ofEpochMilli(now).atZone(VN_ZONE).toLocalDate().toEpochDay()
        val oldF = old.filter { it.epochDay >= today }
        val newF = new.filter { it.epochDay >= today }
        val oldByKey = oldF.associateBy { it.key }.toMutableMap()
        val oldByDay = oldF.groupBy { "${it.idTkb}|${it.epochDay}" }.mapValues { it.value.toMutableList() }.toMutableMap()

        val events = mutableListOf<ChangeEvent>()
        val added = mutableListOf<ClassSession>()

        fun take(o: ClassSession) {
            oldByKey.remove(o.key)
            oldByDay["${o.idTkb}|${o.epochDay}"]?.remove(o)
        }

        for (n in newF) {
            val o = oldByKey[n.key] ?: oldByDay["${n.idTkb}|${n.epochDay}"]?.firstOrNull()
            if (o == null) {
                added += n
                continue
            }
            take(o)
            if (!o.nghiDay && n.nghiDay) {
                events += ChangeEvent(now, ChangeEvent.CANCEL, "Nghỉ dạy: ${n.tenMon}", whenText(n), n.key)
                continue
            }
            if (o.nghiDay && !n.nghiDay) {
                events += ChangeEvent(now, ChangeEvent.ADDED, "Học lại bình thường: ${n.tenMon}", whenText(n), n.key)
            }
            if (o.phong != n.phong && n.phong.isNotBlank()) {
                events += ChangeEvent(now, ChangeEvent.ROOM, "Đổi phòng: ${n.tenMon}",
                    "${whenText(n)} · ${o.phong.ifBlank { "?" }} → ${n.phong}", n.key)
            }
            if (o.startMin != n.startMin || o.endMin != n.endMin || o.epochDay != n.epochDay) {
                events += ChangeEvent(now, ChangeEvent.TIME, "Đổi giờ: ${n.tenMon}",
                    "${whenText(o)} → ${whenText(n)}", n.key)
            }
            if (!o.dayBu && n.dayBu) {
                events += ChangeEvent(now, ChangeEvent.MAKEUP, "Dạy bù: ${n.tenMon}", "${whenText(n)} · ${n.phong}", n.key)
            }
        }

        if (added.size > 6) {
            val subjects = added.map { it.tenMon }.distinct()
            events += ChangeEvent(now, ChangeEvent.SUMMARY, "Có ${added.size} buổi học mới",
                subjects.take(4).joinToString(", ") + if (subjects.size > 4) "…" else "")
        } else {
            for (n in added) {
                events += if (n.dayBu) ChangeEvent(now, ChangeEvent.MAKEUP, "Dạy bù: ${n.tenMon}", "${whenText(n)} · ${n.phong}", n.key)
                else ChangeEvent(now, ChangeEvent.ADDED, "Buổi học mới: ${n.tenMon}", "${whenText(n)} · ${n.phong}", n.key)
            }
        }

        val removed = oldByKey.values.toList()
        if (removed.size > 6) {
            events += ChangeEvent(now, ChangeEvent.REMOVED, "${removed.size} buổi học bị gỡ khỏi lịch",
                removed.map { it.tenMon }.distinct().take(4).joinToString(", "))
        } else {
            for (o in removed) {
                events += ChangeEvent(now, ChangeEvent.REMOVED, "Bỏ buổi: ${o.tenMon}", whenText(o), o.key)
            }
        }
        return events
    }
}
