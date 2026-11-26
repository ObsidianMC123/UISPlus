package vn.uisplus.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

data class SyncResult(val changes: List<ChangeEvent>, val first: Boolean)

class Repository private constructor(context: Context) {
    private val app = context.applicationContext
    val api = UisApi()
    val store = SecureStore(app)
    val settings = Settings(app)
    private val file = File(app.filesDir, "timetable.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData?> = _data.asStateFlow()

    private val _loggedIn = MutableStateFlow(computeLoggedIn())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private fun computeLoggedIn() = store.userName != null && (store.refreshToken != null || store.hasPassword)

    private fun load(): AppData? = runCatching {
        if (file.exists()) json.decodeFromString(AppData.serializer(), file.readText()) else null
    }.getOrNull()

    /** Doc lai tu file (dung cho receiver/widget chay o process moi). */
    fun reload() {
        _data.value = load()
    }

    private fun save(d: AppData) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), d))
        if (!tmp.renameTo(file)) {
            file.delete(); tmp.renameTo(file)
        }
        _data.value = d
    }

    suspend fun login(user: String, pass: String, remember: Boolean): SyncResult {
        val t = api.login(user, pass)
        store.clear()
        store.userName = t.userName.ifBlank { user.trim().uppercase() }
        store.studentName = t.name
        store.saveTokens(t)
        if (remember) store.password = pass
        if (file.exists()) file.delete()
        _data.value = null
        _loggedIn.value = true
        return sync()
    }

    private suspend fun freshToken(force: Boolean = false): String {
        val at = store.accessToken
        if (!force && at != null && System.currentTimeMillis() < store.accessExpiresAt) return at
        store.refreshToken?.let { rt ->
            try {
                val t = api.refresh(rt)
                store.saveTokens(t)
                return t.access
            } catch (e: UisException) {
                if (!e.isAuth) throw e
            }
        }
        val user = store.userName
        val pass = store.password
        if (user != null && pass != null) {
            val t = api.login(user, pass)
            store.saveTokens(t)
            if (t.name.isNotBlank()) store.studentName = t.name
            return t.access
        }
        throw UisException("Phiên đăng nhập đã hết hạn, hãy đăng nhập lại", isAuth = true)
    }

    private suspend fun <T> authed(block: suspend (String) -> T): T {
        return try {
            block(freshToken())
        } catch (e: UisException) {
            if (!e.isAuth) throw e
            block(freshToken(force = true))
        }
    }

    suspend fun sync(): SyncResult = mutex.withLock {
        val sems = authed { api.semesters(it) }
        val cur = sems.current
        val wanted = buildList {
            add(cur)
            sems.semesters.filter { it.hocKy > cur }.minByOrNull { it.hocKy }?.let { add(it.hocKy) }
        }
        val tts = mutableListOf<Timetable>()
        for (hk in wanted) {
            val name = sems.semesters.firstOrNull { it.hocKy == hk }?.name ?: "Học kỳ $hk"
            try {
                tts += authed { api.weekTimetable(it, hk, name) }
            } catch (e: UisException) {
                if (hk == cur) throw e // HK sau chua co TKB thi bo qua
            }
        }
        val old = _data.value
        val now = System.currentTimeMillis()
        val newData = AppData(
            userName = store.userName ?: "",
            studentName = store.studentName ?: "",
            currentHocKy = cur,
            semesters = sems.semesters,
            timetables = tts,
            lastSync = now,
        )
        val changes = if (old == null) emptyList() else Diff.compute(old.sessions, newData.sessions, now)
        save(newData.copy(changes = (changes + (old?.changes ?: emptyList())).take(80)))
        SyncResult(changes, first = old == null)
    }

    fun clearChanges() {
        _data.value?.let { save(it.copy(changes = emptyList())) }
    }

    fun logout() {
        store.clear()
        file.delete()
        _data.value = null
        _loggedIn.value = false
    }

    companion object {
        @Volatile
        private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
