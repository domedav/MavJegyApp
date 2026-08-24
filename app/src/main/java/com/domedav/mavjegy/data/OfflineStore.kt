package com.domedav.mavjegy.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Agresszív offline cache: minden hálózati válasz diszkre kerül.
 * Hálózat nélkül a cache-ből hibátlanul működik az app.
 */
object OfflineStore {

    private fun dir(context: Context, name: String): File =
        File(context.filesDir, name).apply { if (!exists()) mkdirs() }

    private fun write(file: File, content: String) {
        try {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            file.writeText(content)
        } catch (_: Exception) {}
    }

    private fun read(file: File): String? = try {
        if (file.exists()) file.readText() else null
    } catch (_: Exception) {
        null
    }

    // --- Jegykép bájtok (szerver-oldali jegy/bérlet kép) ---
    fun saveTicketImage(context: Context, purchaseId: String, bytes: ByteArray) {
        try {
            val f = File(dir(context, "jegykep_cache"), purchaseId.replace(Regex("[^A-Za-z0-9_-]"), "_"))
            f.parentFile?.let { if (!it.exists()) it.mkdirs() }
            f.writeBytes(bytes)
        } catch (_: Exception) {}
    }

    fun loadTicketImage(context: Context, purchaseId: String): ByteArray? = try {
        val f = File(dir(context, "jegykep_cache"), purchaseId.replace(Regex("[^A-Za-z0-9_-]"), "_"))
        if (f.exists()) f.readBytes() else null
    } catch (_: Exception) {
        null
    }

    // --- Bérlettulajdonos (HPT) adatok JSON-ban ---
    fun savePassOwner(context: Context, key: String, fullName: String?, birthDate: String?, photoBase64: String?, azonosito: String?) {
        val o = JSONObject()
        o.put("fullName", fullName ?: "")
        o.put("birthDate", birthDate ?: "")
        o.put("photo", photoBase64 ?: "")
        o.put("azonosito", azonosito ?: "")
        write(File(dir(context, "pass_owner_cache"), key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json"), o.toString())
    }

    data class CachedPassOwner(
        val fullName: String?,
        val birthDate: String?,
        val photoBase64: String?,
        val azonosito: String?
    )

    fun loadPassOwner(context: Context, key: String): CachedPassOwner? {
        val raw = read(File(dir(context, "pass_owner_cache"), key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")) ?: return null
        return try {
            val o = JSONObject(raw)
            fun s(k: String) = o.optString(k, "").takeIf { it.isNotBlank() }
            CachedPassOwner(s("fullName"), s("birthDate"), s("photo"), s("azonosito"))
        } catch (_: Exception) {
            null
        }
    }

    // --- Utastípus kód -> név térkép (GetAlapadatok) ---
    private fun typeNamesFile(context: Context) = File(context.filesDir, "type_names.json")

    fun saveTypeNames(context: Context, map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        write(typeNamesFile(context), o.toString())
    }

    fun loadTypeNames(context: Context): Map<String, String>? {
        val raw = read(typeNamesFile(context)) ?: return null
        return try {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k, o.getString(k)) } }
        } catch (_: Exception) {
            null
        }
    }
}
