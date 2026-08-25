package com.domedav.mavjegy.util

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

data class DecodedTicket(
    val barcodeContent: String?,
    val jegySorszam: String?,
    val rawJson: JsonObject?,
    /** Bérletigazolvány azonosító (HPTNevesitesAzonosito / NevesitesUzletiAzonosito) */
    val nevesitesAzonosito: String? = null,
    /** Emberi olvasatú utastípus megnevezés (pl. "Diákigazolvány (nappali,esti)") */
    val utasTipusMegnevezes: String? = null
)

data class TicketOwner(
    val name: String?,
    val birthDate: String?,
    val passengerType: String?,
    val photoBase64: String?,
    val azonosito: String? = null
)

object TicketDecoder {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun decodeSerialized(b64: String): DecodedTicket? {
        return try {
            val compressed = Base64.getDecoder().decode(b64)
            val inflater = Inflater(true)
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && inflater.needsInput()) break
                if (n > 0) out.write(buf, 0, n)
            }
            inflater.end()
            val text = out.toString("UTF-8")
            val root = json.parseToJsonElement(text) as? JsonObject ?: return DecodedTicket(null, null, null)

            var jegySorszam: String? = null
            var nevesites: String? = null
            var utasTipusNev: String? = null
            var ervKezdet: String? = null
            var ervVege: String? = null

            fun scan(obj: JsonObject) {
                obj.forEach { (key, v) ->
                    if (v is JsonPrimitive && !v.isStringNullBlank()) {
                        when (key) {
                            "JegySorszam" -> if (jegySorszam == null) jegySorszam = v.content
                            "HPTNevesitesAzonosito", "NevesitesUzletiAzonosito" ->
                                if (nevesites == null) nevesites = v.content
                            "UtasTipusMegnevezesNev" -> if (utasTipusNev == null) utasTipusNev = v.content
                            "ErvenyessegKezdete" -> if (ervKezdet == null) ervKezdet = v.content
                            "ErvenyessegVege" -> if (ervVege == null) ervVege = v.content
                        }
                    }
                    when (v) {
                        is JsonObject -> scan(v)
                        is kotlinx.serialization.json.JsonArray -> v.forEach { if (it is JsonObject) scan(it) }
                        else -> {}
                    }
                }
            }
            scan(root)

            // Vonalkód-tartalom: MINDIG valós jegy-adat (JegySorszám + Nevesítési
            // azonosító + érvényesség), amit a szerver ad – ez az elsődleges.
            // Ha mégse állna össze, marad a Kod-mező.
            val realPayload = buildRealDataPayload(
                jegySorszam, nevesites, utasTipusNev, ervKezdet, ervVege
            )
            val barcodeContent = realPayload ?: findKod(root)
            DecodedTicket(barcodeContent, jegySorszam, root, nevesites, utasTipusNev)
        } catch (_: Exception) {
            null
        }
    }

    /** Valós jegy/bérlet adatokból épített, scannelhető payload. */
    private fun buildRealDataPayload(
        jegySorszam: String?,
        nevesitesAzonosito: String?,
        utasTipusNev: String?,
        ervKezdet: String?,
        ervVege: String?
    ): String? {
        if (jegySorszam.isNullOrBlank() && nevesitesAzonosito.isNullOrBlank()) return null
        val parts = mutableListOf("MAV1")
        parts += jegySorszam ?: "-"
        parts += nevesitesAzonosito ?: "-"
        parts += ervKezdet?.toCompactDate() ?: "-"
        parts += ervVege?.toCompactDate() ?: "-"
        if (!utasTipusNev.isNullOrBlank()) parts += utasTipusNev
        return parts.joinToString("|")
    }

    /** ISO dátum-idő -> yyyyMMdd */
    private fun String.toCompactDate(): String? = try {
        val datePart = substringBefore('T').take(10)
        val p = datePart.split("-")
        if (p.size == 3) "${p[0]}${p[1]}${p[2]}" else null
    } catch (_: Exception) {
        null
    }

    fun extractOwner(rawJson: JsonObject?): TicketOwner? {
        if (rawJson == null) return null
        var name: String? = null
        var birthDate: String? = null
        var passengerType: String? = null
        var passengerTypeRaw: String? = null
        var photoBase64: String? = null
        var azonosito: String? = null

        // Egy JSON-objektumon belül párosítjuk a mezőket – így a névhez a ROSSZ,
        // máshol található dátum/azonosító nem társul (korábbi hiba oka)
        fun fromObject(obj: JsonObject) {
            if (name == null) {
                name = (obj["UtazoNeve"] as? JsonPrimitive)?.takeIf { !it.isStringNullBlank() }?.content
            }
            if (birthDate == null || birthDate!!.startsWith("0001-01-01")) {
                val bd = (obj["SzuletesiDatum"] as? JsonPrimitive)?.takeIf { !it.isStringNullBlank() }?.content
                if (bd != null && !bd.startsWith("0001-01-01")) birthDate = bd
            }
            if (azonosito == null) {
                azonosito = ((obj["NevesitesAzonosito"] as? JsonPrimitive)?.takeIf { !it.isStringNullBlank() }?.content)
                    ?: ((obj["HPTNevesitesAzonosito"] as? JsonPrimitive)?.takeIf { !it.isStringNullBlank() }?.content)
                    ?: ((obj["NevesitesUzletiAzonosito"] as? JsonPrimitive)?.takeIf { !it.isStringNullBlank() }?.content)
                    ?: ((obj["berletIgazolvanyazonosito"] as? JsonPrimitive)?.takeIf { !it.isStringNullBlank() }?.content)
            }
            if (photoBase64 == null) {
                val bin = obj["BinarisAllomany"] as? JsonObject
                photoBase64 = (bin?.get("\$value") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            }
            if (passengerTypeRaw == null) {
                passengerTypeRaw = (obj["UtasTipusMegnevezesNev"] as? JsonPrimitive)
                    ?.takeIf { !it.isStringNullBlank() }?.content
            }
        }

        fun walk(obj: JsonObject) {
            fromObject(obj)
            obj.forEach { (_, v) ->
                if (name != null && birthDate != null && azonosito != null && photoBase64 != null) return
                when (v) {
                    is JsonObject -> walk(v)
                    is kotlinx.serialization.json.JsonArray ->
                        v.forEach { if (it is JsonObject) walk(it) }
                    else -> {}
                }
            }
        }

        walk(rawJson)

        // Utastípus: emberi olvasatú megnevezés előnyben; csak ha nincs, jöhet a HU_ kód
        fun findKodTyped(obj: JsonObject): String? {
            obj.forEach { (key, v) ->
                if (key == "Kod" && v is JsonPrimitive && !v.isStringNullBlank() && v.content.startsWith("HU_")) {
                    return v.content
                }
                when (v) {
                    is JsonObject -> findKodTyped(v)?.let { return it }
                    is kotlinx.serialization.json.JsonArray ->
                        v.forEach { if (it is JsonObject) findKodTyped(it)?.let { k -> return k } }
                    else -> {}
                }
            }
            return null
        }
        passengerType = findKodTyped(rawJson)

        return if (name == null && birthDate == null && passengerType == null && photoBase64 == null && azonosito == null) null
        else TicketOwner(name, birthDate, passengerType, photoBase64, azonosito)
    }

    private fun findKod(obj: JsonObject): String? {
        obj.forEach { (key, v) ->
            if (key == "Kod" && v is JsonPrimitive && !v.isStringNullBlank()) return v.content
            when (v) {
                is JsonObject -> findKod(v)?.let { return it }
                is kotlinx.serialization.json.JsonArray ->
                    v.forEach { e ->
                        when (e) {
                            is JsonObject -> findKod(e)?.let { return it }
                            else -> {}
                        }
                    }
                else -> {}
            }
        }
        return null
    }

    private fun JsonPrimitive.isStringNullBlank(): Boolean =
        !isString || content.isBlank() || content == "null"

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        content.takeIf { it.isNotBlank() }
}
