package com.domedav.mavjegy.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Egyszerű, app-szintű UI beállítások:
 *  - lastTab: az utoljára nyitott fül (0 = Jegyek, 1 = Vásárlás)
 *  - pillSide: a navigációs pill oldala (0 = jobb oldal, 1 = bal oldal)
 */
object SettingsStore {
    private const val PREFS = "mavjegy_settings"
    private const val KEY_LAST_TAB = "lastTab"
    private const val KEY_PILL_SIDE = "pillSide"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getLastTab(context: Context): Int = prefs(context).getInt(KEY_LAST_TAB, 0)

    fun setLastTab(context: Context, tab: Int) {
        prefs(context).edit().putInt(KEY_LAST_TAB, tab).apply()
    }

    // 0 = jobb oldal (eredeti), 1 = bal oldal
    fun getPillSide(context: Context): Int = prefs(context).getInt(KEY_PILL_SIDE, 0)

    fun setPillSide(context: Context, side: Int) {
        prefs(context).edit().putInt(KEY_PILL_SIDE, side).apply()
    }
}
