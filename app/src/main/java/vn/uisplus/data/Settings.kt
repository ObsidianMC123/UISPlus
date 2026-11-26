package vn.uisplus.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** themeMode: 0 = theo he thong, 1 = sang, 2 = toi, 3 = AMOLED den, 4 = toi theo gio (darkFromH..darkToH). */
data class Prefs(
    val remindersOn: Boolean = true,
    val reminderMinutes: Int = 15,
    val changeAlertsOn: Boolean = true,
    val syncHours: Int = 6,
    // Giao dien
    val themeMode: Int = 0,
    val dynamicColor: Boolean = false,
    val darkFromH: Int = 18,
    val darkToH: Int = 6,
    // Pomodoro
    val focusMin: Int = 25,
    val shortBreakMin: Int = 5,
    val longBreakMin: Int = 15,
    val roundsBeforeLong: Int = 4,
    val focusAutoNext: Boolean = true,
    val focusDnd: Boolean = false,
)

class Settings(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Prefs> = _state.asStateFlow()
    val value: Prefs get() = _state.value

    private fun read() = Prefs(
        remindersOn = sp.getBoolean("rem_on", true),
        reminderMinutes = sp.getInt("rem_min", 15),
        changeAlertsOn = sp.getBoolean("chg_on", true),
        syncHours = sp.getInt("sync_h", 6),
        themeMode = sp.getInt("theme_mode", 0),
        dynamicColor = sp.getBoolean("theme_dyn", false),
        darkFromH = sp.getInt("dark_from", 18),
        darkToH = sp.getInt("dark_to", 6),
        focusMin = sp.getInt("f_focus", 25),
        shortBreakMin = sp.getInt("f_short", 5),
        longBreakMin = sp.getInt("f_long", 15),
        roundsBeforeLong = sp.getInt("f_rounds", 4),
        focusAutoNext = sp.getBoolean("f_auto", true),
        focusDnd = sp.getBoolean("f_dnd", false),
    )

    fun update(f: (Prefs) -> Prefs) {
        val n = f(_state.value)
        sp.edit()
            .putBoolean("rem_on", n.remindersOn)
            .putInt("rem_min", n.reminderMinutes)
            .putBoolean("chg_on", n.changeAlertsOn)
            .putInt("sync_h", n.syncHours)
            .putInt("theme_mode", n.themeMode)
            .putBoolean("theme_dyn", n.dynamicColor)
            .putInt("dark_from", n.darkFromH)
            .putInt("dark_to", n.darkToH)
            .putInt("f_focus", n.focusMin)
            .putInt("f_short", n.shortBreakMin)
            .putInt("f_long", n.longBreakMin)
            .putInt("f_rounds", n.roundsBeforeLong)
            .putBoolean("f_auto", n.focusAutoNext)
            .putBoolean("f_dnd", n.focusDnd)
            .apply()
        _state.value = n
    }
}
