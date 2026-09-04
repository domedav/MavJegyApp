package com.domedav.mavjegy.util

import android.content.Context
import org.json.JSONObject

/**
 * Mavinform hírek kitűzése (pin), link alapján.
 * Pin timestamp-et tárol, 1 nap után automatikusan lejár.
 */
object NewsPinPrefs {
    private const val PREFS = "news_pin_prefs"
    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** pinelt item-ek: link -> pinTimestamp (csak azok amik még nem jártak le) */
    fun getPinnedLinks(context: Context): Set<String> {
        val all = prefs(context).all
        val now = System.currentTimeMillis()
        return all.mapNotNull { (key, value) ->
            val ts = (value as? Number)?.toLong() ?: return@mapNotNull null
            if (now - ts < ONE_DAY_MS) key else null
        }.toSet()
    }

    /** Van-e pinelve az adott link? */
    fun isPinned(context: Context, link: String): Boolean {
        val ts = prefs(context).getLong(link, 0L)
        return ts > 0 && System.currentTimeMillis() - ts < ONE_DAY_MS
    }

    /** Toggle pin: ha pinelve volt, leveszi; ha nem volt, kitűzi */
    fun togglePin(context: Context, link: String): Boolean {
        val currentlyPinned = isPinned(context, link)
        if (currentlyPinned) {
            prefs(context).edit().remove(link).apply()
            return false
        } else {
            prefs(context).edit().putLong(link, System.currentTimeMillis()).apply()
            return true
        }
    }

    /** Lejárt pin-ek takarítása */
    fun cleanExpired(context: Context) {
        val now = System.currentTimeMillis()
        val editor = prefs(context).edit()
        var changed = false
        prefs(context).all.forEach { (key, value) ->
            val ts = (value as? Number)?.toLong() ?: return@forEach
            if (now - ts >= ONE_DAY_MS) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }
}
