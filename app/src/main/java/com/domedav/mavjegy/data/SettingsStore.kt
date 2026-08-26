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
    private const val KEY_DETAIL_PREFER_SERVER_IMAGE = "detailPreferServerImage"
    private const val KEY_HAS_SWIPED_BACK = "hasSwipedBack"
    private const val KEY_WEBVIEW_LOGIN_HINT_SEEN = "webviewLoginHintSeen"

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

    /** Ticket detail defaults to the real server image instead of the Aztec code. */
    fun getDetailPreferServerImage(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DETAIL_PREFER_SERVER_IMAGE, false)

    fun setDetailPreferServerImage(context: Context, prefer: Boolean) {
        prefs(context).edit().putBoolean(KEY_DETAIL_PREFER_SERVER_IMAGE, prefer).apply()
    }

    /** User has already performed the swipe-to-close gesture at least once. */
    fun getHasSwipedBack(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_SWIPED_BACK, false)

    fun setHasSwipedBack(context: Context, swiped: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAS_SWIPED_BACK, swiped).apply()
    }

    /** The WebView "you need to log in" hint has been shown once. */
    fun getWebviewLoginHintSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WEBVIEW_LOGIN_HINT_SEEN, false)

    fun setWebviewLoginHintSeen(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_WEBVIEW_LOGIN_HINT_SEEN, seen).apply()
    }
}
