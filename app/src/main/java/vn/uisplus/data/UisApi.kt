package vn.uisplus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class UisException(message: String, val isAuth: Boolean = false) : Exception(message)

data class Tokens(
    val access: String,
    val refresh: String,
    val expiresAt: Long,
    val name: String,
    val userName: String,
)

/** Client cho API UIS PTIT HCM (chi dung cac API DOC). */
class UisApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    companion object {
        const val BASE = "https://uis.ptithcm.edu.vn"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36 UISPlus/1.0"
        private fun paging(limit: Int) =
            """"additional":{"paging":{"limit":$limit,"page":1},"ordering":[{"name":null,"order_type":null}]}"""
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun login(user: String, pass: String): Tokens = auth(
        FormBody.Builder()
            .add("grant_type", "password")
            .add("username", user.trim())
            .add("password", pass)
            .add("uri", "$BASE/#/home")
            .add("vaitro", "")
            .add("mgr", "1")
            .build(),
        fallbackRefresh = null,
    )

    suspend fun refresh(refreshToken: String): Tokens = auth(
        FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build(),
        fallbackRefresh = refreshToken,
    )

    private suspend fun auth(body: FormBody, fallbackRefresh: String?): Tokens = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/api/auth/login")
            .header("User-Agent", UA)
            .header("Referer", "$BASE/")
            .post(body)
            .build()
        val text = try {
            client.newCall(req).execute().use { it.body.string() }
        } catch (e: IOException) {
            throw UisException("Không kết nối được tới UIS: ${e.message ?: "lỗi mạng"}")
        }
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw UisException("UIS trả về dữ liệu lạ khi đăng nhập", isAuth = true)
        val access = o.str("access_token")
        if (access.isBlank()) {
            val msg = listOf("error_description", "message", "error").map { o.str(it) }.firstOrNull { it.isNotBlank() }
            throw UisException(
                if (fallbackRefresh == null) (msg?.let { "Đăng nhập thất bại: $it" } ?: "Sai tài khoản hoặc mật khẩu")
                else "Phiên đăng nhập đã hết hạn",
                isAuth = true,
            )
        }
        val expiresIn = o.int("expires_in").takeIf { it > 0 } ?: 3600
        Tokens(
            access = access,
            refresh = o.str("refresh_token").ifBlank { fallbackRefresh ?: "" },
            expiresAt = System.currentTimeMillis() + (expiresIn - 120).coerceAtLeast(60) * 1000L,
            name = o.str("name"),
            userName = o.str("userName"),
        )
    }

    /** POST JSON, tra ve object goc. Loi auth cua UIS nam trong body (HTTP van 200). */
    suspend fun post(token: String, path: String, body: String): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/api/$path")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", UA)
            .header("Referer", "$BASE/")
            .post(body.toRequestBody(JSON_TYPE))
            .build()
        val (code, text) = try {
            client.newCall(req).execute().use { it.code to it.body.string() }
        } catch (e: IOException) {
            throw UisException("Không kết nối được tới UIS: ${e.message ?: "lỗi mạng"}")
        }
        if (code == 401 || code == 403) throw UisException("Phiên đăng nhập hết hạn", isAuth = true)
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw UisException("UIS lỗi (HTTP $code)")
        val c = o.int("code")
        val result = o.str("result")
        if (c == 401 || c == 403) throw UisException("Phiên đăng nhập hết hạn", isAuth = true)
        if (result == "false" || (c != 0 && c != 200)) {
            throw UisException("UIS báo lỗi: ${o.str("message").ifBlank { "code $c" }}")
        }
        o
    }

    suspend fun semesters(token: String): Parser.SemesterList {
        val o = post(token, "sch/w-locdshockytkbuser",
            """{"filter":{"is_tieng_anh":null},"additional":{"paging":{"limit":100,"page":1},"ordering":[{"name":"hoc_ky","order_type":1}]}}""")
        return Parser.semesters(o.obj("data") ?: throw UisException("Thiếu dữ liệu học kỳ"))
    }

    suspend fun weekTimetable(token: String, hocKy: Int, name: String): Timetable {
        val o = post(token, "sch/w-locdstkbtuanusertheohocky",
            """{"filter":{"hoc_ky":$hocKy,"ten_hoc_ky":""},${paging(100)}}""")
        return Parser.weekTimetable(hocKy, name, o.obj("data") ?: throw UisException("Thiếu dữ liệu TKB"))
    }
}
