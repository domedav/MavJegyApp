package com.domedav.mavjegy.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            createSecure(context)
        } catch (_: Throwable) {
            // sérült keystore / prefs: töröljük és újrapróbáljuk
            context.getSharedPreferences("mavjegy_secure_prefs", Context.MODE_PRIVATE)
                .edit().clear().commit()
            val filesDir = context.filesDir
            java.io.File(filesDir.parent, "shared_prefs/mavjegy_secure_prefs.xml").delete()
            runCatching { createSecure(context) }.getOrElse {
                context.getSharedPreferences("mavjegy_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun createSecure(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            "mavjegy_secure_prefs",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun setToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun setCredentials(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun setUserId(id: String) {
        prefs.edit().putString(KEY_USER_ID, id).apply()
    }

    fun getUaid(): String = prefs.getString(KEY_UAID, null) ?: ""

    fun hasUaid(): Boolean = !getUaid().isNullOrBlank()

    fun setUaid(id: String) {
        prefs.edit().putString(KEY_UAID, id).apply()
    }

    fun hasToken(): Boolean = !getToken().isNullOrBlank()

    fun hasCredentials(): Boolean =
        !getEmail().isNullOrBlank() && !getPassword().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "userTokenXml"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
        const val KEY_USER_ID = "felhasznaloAzonosito"
        const val KEY_UAID = "uaid"
    }
}
