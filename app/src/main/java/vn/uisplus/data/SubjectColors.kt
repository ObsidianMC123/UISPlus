package vn.uisplus.data

/** Mau rieng, on dinh cho tung mon (dung chung cho app + widget). */
object SubjectColors {
    private val PALETTE = longArrayOf(
        0xFF6366F1, // indigo
        0xFF0EA5E9, // sky
        0xFF10B981, // emerald
        0xFFF59E0B, // amber
        0xFFEF4444, // red
        0xFF8B5CF6, // violet
        0xFFEC4899, // pink
        0xFF14B8A6, // teal
        0xFFF97316, // orange
        0xFF3B82F6, // blue
        0xFF84CC16, // lime
        0xFFD946EF, // fuchsia
    )

    fun argb(maMon: String): Long {
        var h = 7
        for (c in maMon) h = h * 31 + c.code
        return PALETTE[Math.floorMod(h, PALETTE.size)]
    }
}
