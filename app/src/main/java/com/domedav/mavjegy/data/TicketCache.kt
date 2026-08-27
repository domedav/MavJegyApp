package com.domedav.mavjegy.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import androidx.compose.runtime.mutableStateMapOf
import java.time.format.DateTimeFormatter

@Serializable
data class CachedTicketDetails(
    val serializedTicketData: String? = null,
    val jegySorszam: String? = null,
    val bizonylatTechnikaiAzonosito: String? = null,
    val ajanlatNev: String? = null,
    val ervenyessegKezdete: String? = null,
    val ervenyessegVege: String? = null,
    val fetchedAt: Long = 0L
)

object TicketCache {
    private val json = Json { ignoreUnknownKeys = true }
    private const val DIR = "ticket_cache"

    private val memoryNameCache = mutableStateMapOf<String, String>()

    fun getNameMem(id: String): String? = memoryNameCache[id]

    fun putNameMem(id: String, name: String?) {
        if (!name.isNullOrBlank() && name != "null") memoryNameCache[id] = name
        else memoryNameCache.remove(id)
    }

    // Thread-safe, immutable DateTimeFormatter instances – created once
    private val isoFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    )

    private fun expirationMillis(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        for (fmt in isoFormatters) {
            try {
                val parsed = fmt.parse(s)
                return try {
                    OffsetDateTime.from(parsed).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    try {
                        LocalDateTime.from(parsed)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        LocalDate.from(parsed)
                            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

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
                    bizonylatTechnikaiAzonosito = details.ticketData?.bizonylatTechnikaiAzonosito,
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
            // Cache érvényessége a jegy lejáratáig: érvényesség vége után cache-hiba
            val exp = expirationMillis(c.ervenyessegVege)
            if (exp != null && System.currentTimeMillis() > exp) {
                f.delete() // lejárt -> kuka a cache-ből
                return null
            }
            TicketDetails(
                ticketData = TicketData(c.serializedTicketData, c.jegySorszam, c.bizonylatTechnikaiAzonosito),
                ajanlatNev = c.ajanlatNev,
                ervenyessegKezdete = c.ervenyessegKezdete,
                ervenyessegVege = c.ervenyessegVege
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Csak a jegy neve, ha a jegy még nem járt le – lejárt jegyek cache-e automatikusan törlődik (párosításhoz szükséges adat) */
    fun loadName(context: Context, purchaseId: String): String? =
        load(context, purchaseId)?.ajanlatNev?.takeIf { it.isNotBlank() }

    /** Lejárt / eltávolított jegyek cache-ének törlése – nem létezőként kezeljük */
    fun delete(context: Context, purchaseId: String) {
        try {
            file(context, purchaseId).delete()
        } catch (_: Exception) {}
    }
}
