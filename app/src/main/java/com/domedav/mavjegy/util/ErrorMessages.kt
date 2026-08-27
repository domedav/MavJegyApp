package com.domedav.mavjegy.util

import com.domedav.mavjegy.R

/** Fejlesztői hibaüzenetek lefordítása a felhasználó számára érthető, általános szövegre. */
fun friendlyError(raw: String?): Int {
    val m = raw?.trim().orEmpty()
    if (m.isEmpty()) return R.string.err_generic_fallback
    val l = m.lowercase()
    return when {
        l.contains("unknownhost") || l.contains("timeout") ||
        l.contains("socket") || l.contains("unable to resolve") ||
        l.contains("no route") || l.contains("connectexception") ||
        l.contains("network") || l.contains("dns") ->
            R.string.err_no_internet
        l.contains("401") || l.contains("403") || l.contains("unauthorized") ||
        l.contains("bejelentkezes sikertelen") || l.contains("hibás") ||
        l.contains("rossz") || l.contains("invalid") || l.contains("credential") ||
        l.contains("jelszó") || l.contains("felhasználó") ->
            R.string.err_bad_credentials
        l.contains("blokkolva") || l.contains("waf") || l.contains("getjegykep") ||
        l.contains("http 4") || l.contains("http 5") || l.contains("http 40") ||
        l.contains("http 50") || l.contains("500") || l.contains("503") ->
            R.string.err_server
        l.contains("lejárt") -> R.string.err_ticket_expired
        else -> R.string.err_generic
    }
}
