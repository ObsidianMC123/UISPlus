package vn.uisplus.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Xuat TKB ra iCalendar (RFC 5545) de import vao Google Calendar / Outlook. */
object Ics {
    private val UTC_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    /** Gap dong > 75 octet theo RFC 5545. */
    private fun fold(line: String): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) return line
        val sb = StringBuilder()
        var count = 0
        for (ch in line) {
            val len = ch.toString().toByteArray(Charsets.UTF_8).size
            if (count + len > 74) {
                sb.append("\r\n ")
                count = 1
            }
            sb.append(ch)
            count += len
        }
        return sb.toString()
    }

    fun build(sessions: List<ClassSession>, now: Long = System.currentTimeMillis()): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//UISPlus//TKB//VI",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:Thời khóa biểu PTIT",
            "X-WR-TIMEZONE:Asia/Ho_Chi_Minh",
        )
        val stamp = UTC_FMT.format(Instant.ofEpochMilli(now))
        for (s in sessions) {
            if (s.nghiDay) continue
            val desc = buildList {
                add("${s.maMon} · ${s.tietText}")
                if (s.giangVien.isNotBlank()) add("GV: ${s.giangVien}")
                if (s.nhom.isNotBlank()) add("Nhóm ${s.nhom}${if (s.toTh.isNotBlank()) " · Tổ ${s.toTh}" else ""}")
                if (s.dayBu) add("Dạy bù")
            }.joinToString("\n")
            lines += "BEGIN:VEVENT"
            lines += "UID:${s.key.replace("|", "-").replace("#", "-")}@uisplus"
            lines += "DTSTAMP:$stamp"
            lines += "DTSTART:${UTC_FMT.format(Instant.ofEpochMilli(s.startMillis))}"
            lines += "DTEND:${UTC_FMT.format(Instant.ofEpochMilli(s.endMillis))}"
            lines += "SUMMARY:${esc(s.tenMon)}"
            lines += "LOCATION:${esc(s.phong)}"
            lines += "DESCRIPTION:${esc(desc)}"
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }
}
