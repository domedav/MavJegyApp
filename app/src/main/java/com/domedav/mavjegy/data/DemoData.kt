package com.domedav.mavjegy.data

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

/**
 * Demó mód adatai – Demo / Demo belépéssel elérhető, teljesen offline.
 * A serialized JSON pontosan olyan struktúrájú, mint az éles API válasz,
 * így a TicketDecoder / vonalkód-generátor / tulajdonos-kinyerés valódi útvonalon fut.
 */
internal object DemoData {

    const val DEMO_EMAIL = "Demo"
    const val DEMO_PASSWORD = "Demo"

    private val dayMs = 24L * 60 * 60 * 1000

    private fun iso(offsetDays: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis += offsetDays * dayMs }
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
        return fmt.format(cal.time)
    }

    /** raw-deflate + base64 – a TicketDecoder.decodeSerialized pontosan ezt várja */
    private fun deflateB64(json: String): String = try {
        val input = json.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(input)
        deflater.finish()
        val buf = ByteArray(8192)
        val out = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        deflater.end()
        Base64.getEncoder().encodeToString(out.toByteArray())
    } catch (_: Exception) {
        ""
    }

    private fun ticketJson(
        kod: String,
        jegySorszam: String,
        nev: String,
        szuletesiDatum: String,
        nevesitesAzonosito: String
    ): String = """
        {
          "JegySorszam": "$jegySorszam",
          "UtazoNeve": "$nev",
          "SzuletesiDatum": "$szuletesiDatum",
          "NevesitesAzonosito": "$nevesitesAzonosito",
          "Kod": "$kod",
          "Ar": {"Osszeg": 0}
        }
    """.trimIndent().replace("\n", "").replace("  ", "")

    fun purchases(): List<Purchase> = listOf(
        // Bérletek (startStation = null)
        Purchase(
            id = "DEMO_BERLET_1",
            validFrom = iso(-30),
            validTo = iso(335),
            startStation = null,
            endStation = null,
            status = "Ervenyes",
            takenOver = true,
            amount = 18900.0,
            currency = "HUF",
            name = "Diákbérlet"
        ),
        Purchase(
            id = "DEMO_BERLET_2",
            validFrom = iso(-10),
            validTo = iso(355),
            startStation = null,
            endStation = null,
            status = "Ervenyes",
            takenOver = true,
            amount = 24500.0,
            currency = "HUF",
            name = "Budapest–Szeged bérlet"
        ),
        // Jegyek
        Purchase(
            id = "DEMO_JEGY_1",
            validFrom = iso(1),
            validTo = iso(2),
            startStation = "Budapest-Keleti",
            endStation = "Szeged",
            status = "Ervenyes",
            takenOver = false,
            amount = 3450.0,
            currency = "HUF",
            name = "Menetjegy 2. osztály"
        ),
        Purchase(
            id = "DEMO_JEGY_2",
            validFrom = iso(5),
            validTo = iso(6),
            startStation = "Szeged",
            endStation = "Budapest-Nyugati",
            status = "Ervenyes",
            takenOver = false,
            amount = 3450.0,
            currency = "HUF",
            name = "Menetjegy 2. osztály"
        )
    )

    fun ticketDetails(id: String): TicketDetails {
        val p = purchases().firstOrNull { it.id == id } ?: return TicketDetails(null)
        val isPass = p.startStation == null
        val serialized = if (isPass) {
            deflateB64(
                ticketJson(
                    kod = "HU_DEMOBERLET${p.id.takeLast(1)}9876543210",
                    jegySorszam = if (p.id.endsWith("1")) "1000777777" else "1000888888",
                    nev = if (p.id.endsWith("1")) "Kovács Anna" else "Nagy Péter",
                    szuletesiDatum = if (p.id.endsWith("1")) "2003-04-12" else "1995-11-30",
                    nevesitesAzonosito = if (p.id.endsWith("1")) "1000123456" else "1000987654"
                )
            )
        } else {
            deflateB64(
                ticketJson(
                    kod = "HU_DEMOJEGY${p.id.takeLast(1)}1234567890",
                    jegySorszam = if (p.id.endsWith("1")) "1001122334" else "1001556677",
                    nev = "Kovács Anna",
                    szuletesiDatum = "2003-04-12",
                    nevesitesAzonosito = "1000123456"
                )
            )
        }
        return TicketDetails(
            ticketData = TicketData(
                serializedTicketData = serialized,
                jegySorszam = if (isPass) {
                    if (p.id.endsWith("1")) "1000777777" else "1000888888"
                } else {
                    if (p.id.endsWith("1")) "1001122334" else "1001556677"
                }
            ),
            ajanlatNev = p.name,
            ervenyessegKezdete = p.validFrom,
            ervenyessegVege = p.validTo
        )
    }

    fun passOwner(): PassOwnerData = PassOwnerData(
        fullName = "Kovács Anna",
        birthDate = "2003-04-12",
        photoBase64 = null,
        azonosito = "1000123456"
    )

    fun typeNames(): Map<String, String> = mapOf(
        "HU_DIak" to "Diák",
        "HU_Felnott" to "Felnőtt",
        "HU_Nyugdijas" to "Nyugdíjas",
        "HU_DIAKBERLET" to "Diákbérlet"
    )

    fun matches(email: String, password: String): Boolean =
        email.trim() == DEMO_EMAIL && password == DEMO_PASSWORD
}
