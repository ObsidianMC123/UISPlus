package vn.uisplus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import vn.uisplus.data.Prefs
import vn.uisplus.data.Repository
import vn.uisplus.data.UisException
import vn.uisplus.widget.TimetableWidget
import vn.uisplus.work.Reminders
import vn.uisplus.work.SyncRunner
import vn.uisplus.work.SyncWorker

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository.get(app)
    val data = repo.data
    val loggedIn = repo.loggedIn
    val prefs = repo.settings.state

    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()

    private val _toast = Channel<String>(Channel.BUFFERED)
    val toast = _toast.receiveAsFlow()
    fun toastMsg(msg: String) { _toast.trySend(msg) }

    init {
        if (repo.loggedIn.value) {
            SyncWorker.schedule(app)
            Reminders.reschedule(app)
            // Mo app ma du lieu cu hon 30 phut thi tu dong bo
            val last = repo.data.value?.lastSync ?: 0
            if (System.currentTimeMillis() - last > 30 * 60_000L) refresh(silent = true)
        }
    }

    fun login(user: String, pass: String, remember: Boolean) {
        if (_loginBusy.value) return
        if (user.isBlank() || pass.isBlank()) {
            _loginError.value = "Nhập mã sinh viên và mật khẩu nhé"
            return
        }
        _loginBusy.value = true
        _loginError.value = null
        viewModelScope.launch {
            try {
                repo.login(user, pass, remember)
                val ctx = getApplication<Application>()
                SyncWorker.schedule(ctx, replace = true)
                Reminders.reschedule(ctx)
                TimetableWidget.refreshAll(ctx)
            } catch (e: UisException) {
                _loginError.value = e.message
                if (repo.loggedIn.value && repo.data.value == null) repo.logout()
            } catch (e: Exception) {
                _loginError.value = "Lỗi không mong muốn: ${e.message}"
                if (repo.loggedIn.value && repo.data.value == null) repo.logout()
            } finally {
                _loginBusy.value = false
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (_syncing.value) return
        _syncing.value = true
        viewModelScope.launch {
            try {
                val r = SyncRunner.run(getApplication(), background = false)
                if (!silent) {
                    _toast.send(if (r.changes.isEmpty()) "Lịch đã mới nhất" else "Có ${r.changes.size} thay đổi trong lịch")
                } else if (r.changes.isNotEmpty()) {
                    _toast.send("Có ${r.changes.size} thay đổi trong lịch")
                }
            } catch (e: UisException) {
                _toast.send(e.message ?: "Không đồng bộ được")
            } catch (e: Exception) {
                _toast.send("Không đồng bộ được: ${e.message}")
            } finally {
                _syncing.value = false
            }
        }
    }

    fun updatePrefs(f: (Prefs) -> Prefs) {
        val before = repo.settings.value
        repo.settings.update(f)
        val after = repo.settings.value
        val ctx = getApplication<Application>()
        if (before.syncHours != after.syncHours) SyncWorker.schedule(ctx, replace = true)
        if (before.remindersOn != after.remindersOn || before.reminderMinutes != after.reminderMinutes) Reminders.reschedule(ctx)
        if (before.focusMin != after.focusMin || before.shortBreakMin != after.shortBreakMin || before.longBreakMin != after.longBreakMin) {
            vn.uisplus.focus.Focus.onPrefsChanged(ctx)
        }
        if (before.themeMode != after.themeMode) viewModelScope.launch { runCatching { TimetableWidget.refreshAll(ctx) } }
    }

    fun clearChanges() = repo.clearChanges()

    fun logout() {
        val ctx = getApplication<Application>()
        repo.logout()
        SyncWorker.cancel(ctx)
        Reminders.reschedule(ctx)
        viewModelScope.launch { TimetableWidget.refreshAll(ctx) }
    }
}
