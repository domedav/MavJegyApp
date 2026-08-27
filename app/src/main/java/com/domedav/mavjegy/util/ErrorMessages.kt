package com.domedav.mavjegy.util

/** Fejlesztői hibaüzenetek lefordítása a felhasználó számára érthető, általános szövegre. */
fun friendlyError(raw: String?): String {
    val m = raw?.trim().orEmpty()
    if (m.isEmpty()) return "Valami hiba történt. Próbáld újra."
    val l = m.lowercase()
    return when {
        l.contains("unknownhost") || l.contains("timeout") ||
        l.contains("socket") || l.contains("unable to resolve") ||
        l.contains("no route") || l.contains("connectexception") ||
        l.contains("network") || l.contains("dns") ->
            "Nincs internetkapcsolat."
        l.contains("401") || l.contains("403") || l.contains("unauthorized") ||
        l.contains("bejelentkezes sikertelen") || l.contains("hibás") ||
        l.contains("rossz") || l.contains("invalid") || l.contains("credential") ||
        l.contains("jelszó") || l.contains("felhasználó") ->
            "Hibás felhasználónév vagy jelszó."
        l.contains("blokkolva") || l.contains("waf") || l.contains("getjegykep") ||
        l.contains("http 4") || l.contains("http 5") || l.contains("http 40") ||
        l.contains("http 50") || l.contains("500") || l.contains("503") ->
            "A szerver nem érhető el. Próbáld később."
        l.contains("lejárt") -> "A jegy lejárt."
        else -> "Hiba történt. Próbáld újra."
    }
}
