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
    val rawJson: JsonObject?
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
            DecodedTicket(
                barcodeContent = findKod(root),
                jegySorszam = (root["JegySorszam"] as? JsonPrimitive)?.contentOrNullSafe(),
                rawJson = root
            )
        } catch (_: Exception) {
            null
        }
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
