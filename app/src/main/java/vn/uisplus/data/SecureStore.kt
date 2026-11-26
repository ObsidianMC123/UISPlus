package vn.uisplus.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Luu tai khoan/token/mat khau. Gia tri nhay cam duoc ma hoa AES-256-GCM
 * bang khoa nam trong Android Keystore (khoa khong rut ra khoi may duoc).
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun enc(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun dec(s: String): String? = runCatching {
        val (iv, ct) = s.split(":", limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    private fun getSecret(k: String): String? = prefs.getString(k, null)?.let { dec(it) }
    private fun putSecret(k: String, v: String?) {
        prefs.edit().apply { if (v.isNullOrEmpty()) remove(k) else putString(k, enc(v)) }.apply()
    }

    var userName: String?
        get() = prefs.getString("user", null)
        set(v) = prefs.edit().putString("user", v).apply()

    var studentName: String?
        get() = prefs.getString("name", null)
        set(v) = prefs.edit().putString("name", v).apply()

    var password: String?
        get() = getSecret("pass")
        set(v) = putSecret("pass", v)

    var refreshToken: String?
        get() = getSecret("rt")
        set(v) = putSecret("rt", v)

    var accessToken: String?
        get() = getSecret("at")
        set(v) = putSecret("at", v)

    var accessExpiresAt: Long
        get() = prefs.getLong("at_exp", 0)
        set(v) = prefs.edit().putLong("at_exp", v).apply()

    val hasPassword: Boolean get() = prefs.contains("pass")

    fun saveTokens(t: Tokens) {
        accessToken = t.access
        accessExpiresAt = t.expiresAt
        if (t.refresh.isNotBlank()) refreshToken = t.refresh
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val ALIAS = "uisplus_store_v1"
    }
}
