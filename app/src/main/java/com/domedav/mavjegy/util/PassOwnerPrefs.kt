package com.domedav.mavjegy.util

import android.content.Context
import org.json.JSONObject

/**
 * Jegy/bérlet tulajdonosi adatainak felhasználói szerkesztése, jegyenként (id alapján).
 * Minden mező felülírható: név, születési dátum, azonosító, fotó (hash a foto-cache-ből).
 */
object PassOwnerPrefs {
    data class Edit(
        val name: String?,
        val birthDate: String?,
        val azonosito: String?,
        val photoHash: String? = null
    )

    private const val PREFS = "pass_owner_prefs"
    private const val KEY_NAME = "name"
    private const val KEY_BIRTH = "birthDate"
    private const val KEY_AZON = "azonosito"
    private const val KEY_PHOTO = "photoHash"

    fun load(context: Context, passId: String): Edit? = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(passId, null)
        if (raw.isNullOrBlank()) null
        else {
            val o = JSONObject(raw)
            fun s(k: String) = o.optString(k, "").takeIf { it.isNotBlank() }
            Edit(s(KEY_NAME), s(KEY_BIRTH), s(KEY_AZON), s(KEY_PHOTO))
        }
    } catch (_: Exception) {
        null
    }

    fun save(context: Context, passId: String, edit: Edit) {
        try {
            val o = JSONObject()
            o.put(KEY_NAME, edit.name ?: "")
            o.put(KEY_BIRTH, edit.birthDate ?: "")
            o.put(KEY_AZON, edit.azonosito ?: "")
            o.put(KEY_PHOTO, edit.photoHash ?: "")
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(passId, o.toString()).apply()
        } catch (_: Exception) {}
    }

    fun clear(context: Context, passId: String) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(passId).apply()
        } catch (_: Exception) {}
    }
}
