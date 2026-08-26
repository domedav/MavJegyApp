package com.domedav.mavjegy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Agresszív offline cache: minden hálózati válasz diszkre kerül.
 * Hálózat nélkül a cache-ből hibátlanul működik az app.
 *
 * Fotók: SHA-256 hash-el deduplikálva (duplikátum csak egyszer tárolódik),
 * kompresszáltan (JPEG, max. 2 MB) mentve.
 */
object OfflineStore {

    private const val MAX_PHOTO_BYTES = 2 * 1024 * 1024 // 2 MB

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

    // --- Bérlet/jegy utasfotó: hash -> deduplikált, kompresszált fájl ---

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Fotó bájt mentése: dekódol → méretez (max 1280 px) → JPEG tömörítés,
     * amíg 2 MB alá nem csökken. Visszaadja a hash-t (deduplikációs kulcs).
     */
    fun saveOwnerPhoto(context: Context, rawBytes: ByteArray): String? {
        return try {
            val hash = sha256(rawBytes)
            val out = dir(context, "owner_photo_cache")
            val target = File(out, "$hash.jpg")
            if (target.exists() && target.length() > 0) return hash // duplikátum: már megvan

            var bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null

            // Méretezés: hosszabb oldal max 1280 px
            val maxDim = 1280
            val largest = maxOf(bmp.width, bmp.height)
            if (largest > maxDim) {
                val scale = maxDim.toFloat() / largest
                bmp = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }

            // Kompresszió: minőség csökkentése amíg < 2 MB
            var quality = 85
            var data: ByteArray
            do {
                val bos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                data = bos.toByteArray()
                quality -= 10
            } while (data.size > MAX_PHOTO_BYTES && quality >= 30)

            target.parentFile?.let { if (!it.exists()) it.mkdirs() }
            target.writeBytes(data)
            hash
        } catch (_: Exception) {
            null
        }
    }

    /** Base64 fotó kényelmi mentő */
    fun saveOwnerPhotoBase64(context: Context, base64: String): String? = try {
        saveOwnerPhoto(context, java.util.Base64.getDecoder().decode(base64))
    } catch (_: Exception) {
        null
    }

    fun loadOwnerPhoto(context: Context, hash: String): ByteArray? = try {
        val f = File(dir(context, "owner_photo_cache"), "$hash.jpg")
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

    // --- Szerver jegykép: egyszer lekérve MENTJÜK (hash-dedup + JPEG kompresszió) ---

    private fun jegykepDir(context: Context) = dir(context, "jegykep_cache")

    private fun jegykepIndexFile(context: Context) = File(context.filesDir, "jegykep_index.json")

    /** Kompresszió: max 1600 px hosszabb oldal, JPEG minőség csökkentve 2 MB-ig */
    private fun compressImage(raw: ByteArray): ByteArray? {
        val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        var scaled = bmp
        val maxDim = 1600
        val largest = maxOf(bmp.width, bmp.height)
        if (largest > maxDim) {
            val scale = maxDim.toFloat() / largest
            scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }
        var quality = 85
        var data: ByteArray
        do {
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            data = bos.toByteArray()
            quality -= 10
        } while (data.size > MAX_PHOTO_BYTES && quality >= 30)
        return data
    }

    fun saveServerJegyKep(context: Context, purchaseId: String, raw: ByteArray): String? {
        return try {
            val compressed = compressImage(raw) ?: return null
            val hash = sha256(compressed)
            val target = File(jegykepDir(context), "$hash.jpg")
            if (!target.exists() || target.length() == 0L) {
                target.parentFile?.let { if (!it.exists()) it.mkdirs() }
                target.writeBytes(compressed)
            }
            // purchaseId -> hash index
            val idx = jegykepIndexFile(context)
            val obj = try {
                JSONObject(idx.takeIf { it.exists() }?.readText() ?: "{}")
            } catch (_: Exception) { JSONObject() }
            obj.put(purchaseId, hash)
            idx.writeText(obj.toString())
            hash
        } catch (_: Exception) {
            null
        }
    }

    fun loadServerJegyKep(context: Context, purchaseId: String): ByteArray? = try {
        val idx = jegykepIndexFile(context)
        val obj = JSONObject(idx.takeIf { it.exists() }?.readText() ?: "{}")
        val hash = obj.optString(purchaseId, "").takeIf { it.isNotBlank() } ?: return null
        val f = File(jegykepDir(context), "$hash.jpg")
        if (f.exists()) f.readBytes() else null
    } catch (_: Exception) {
        null
    }

    // --- Szerver jegyképből dekódolt vonalkód-szöveg (kicsi, gyors, offline) ---
    private fun serverBarcodeFile(context: Context, purchaseId: String) =
        File(dir(context, "server_barcode_cache"), purchaseId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".txt")

    fun saveServerBarcode(context: Context, purchaseId: String, text: String) {
        write(serverBarcodeFile(context, purchaseId), text)
    }

    fun loadServerBarcode(context: Context, purchaseId: String): String? =
        read(serverBarcodeFile(context, purchaseId))?.takeIf { it.isNotBlank() }

    fun deleteServerJegyKep(context: Context, purchaseId: String) {
        try {
            val idx = jegykepIndexFile(context)
            if (idx.exists()) {
                val obj = JSONObject(idx.readText())
                val hash = obj.optString(purchaseId, "")
                obj.remove(purchaseId)
                idx.writeText(obj.toString())
                if (hash.isNotBlank()) {
                    val f = File(jegykepDir(context), "$hash.jpg")
                    if (f.exists()) f.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun deleteServerBarcode(context: Context, purchaseId: String) {
        try {
            serverBarcodeFile(context, purchaseId).delete()
        } catch (_: Exception) {}
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
