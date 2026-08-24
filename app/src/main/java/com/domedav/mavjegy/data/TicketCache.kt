package com.domedav.mavjegy.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CachedTicketDetails(
    val serializedTicketData: String? = null,
    val jegySorszam: String? = null,
    val ajanlatNev: String? = null,
    val ervenyessegKezdete: String? = null,
    val ervenyessegVege: String? = null,
    val fetchedAt: Long = 0L
)

object TicketCache {
    private val json = Json { ignoreUnknownKeys = true }
    private const val DIR = "ticket_cache"

    private fun file(context: Context, purchaseId: String) =
        java.io.File(java.io.File(context.filesDir, DIR),
            purchaseId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    fun save(context: Context, purchaseId: String, details: TicketDetails) {
        try {
            val f = file(context, purchaseId)
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(
                CachedTicketDetails.serializer(),
                CachedTicketDetails(
                    serializedTicketData = details.ticketData?.serializedTicketData,
                    jegySorszam = details.ticketData?.jegySorszam,
                    ajanlatNev = details.ajanlatNev,
                    ervenyessegKezdete = details.ervenyessegKezdete,
                    ervenyessegVege = details.ervenyessegVege,
                    fetchedAt = System.currentTimeMillis()
                )
            ))
        } catch (_: Exception) {}
    }

    fun load(context: Context, purchaseId: String): TicketDetails? {
        return try {
            val f = file(context, purchaseId)
            if (!f.exists()) return null
            val c = json.decodeFromString(CachedTicketDetails.serializer(), f.readText())
            TicketDetails(
                ticketData = TicketData(c.serializedTicketData, c.jegySorszam),
                ajanlatNev = c.ajanlatNev,
                ervenyessegKezdete = c.ervenyessegKezdete,
                ervenyessegVege = c.ervenyessegVege
            )
        } catch (_: Exception) {
            null
        }
    }
}
