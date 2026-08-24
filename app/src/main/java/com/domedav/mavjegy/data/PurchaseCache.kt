package com.domedav.mavjegy.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class PurchaseDto(
    val id: String,
    val validFrom: String? = null,
    val validTo: String? = null,
    val startStation: String? = null,
    val endStation: String? = null,
    val status: String,
    val takenOver: Boolean = false,
    val amount: Double = 0.0,
    val currency: String = ""
)

object PurchaseCache {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    internal fun toDto(p: Purchase) = PurchaseDto(
        id = p.id, validFrom = p.validFrom, validTo = p.validTo,
        startStation = p.startStation, endStation = p.endStation,
        status = p.status, takenOver = p.takenOver,
        amount = p.amount, currency = p.currency
    )

    internal fun fromDto(d: PurchaseDto) = Purchase(
        id = d.id, validFrom = d.validFrom, validTo = d.validTo,
        startStation = d.startStation, endStation = d.endStation,
        status = d.status, takenOver = d.takenOver,
        amount = d.amount, currency = d.currency
    )

    fun save(context: Context, list: List<Purchase>) {
        try {
            file(context).writeText(
                json.encodeToString(ListSerializer(PurchaseDto.serializer()), list.map { toDto(it) })
            )
        } catch (_: Exception) {
        }
    }

    fun load(context: Context): List<Purchase>? {
        return try {
            val f = file(context)
            if (!f.exists()) return null
            json.decodeFromString(
                ListSerializer(PurchaseDto.serializer()),
                f.readText()
            ).map { fromDto(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context): File = File(context.filesDir, "purchases_cache.json")
}
