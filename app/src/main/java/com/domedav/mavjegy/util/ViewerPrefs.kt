package com.domedav.mavjegy.util

import android.content.Context

object ViewerPrefs {
    private const val PREFS = "viewer_prefs"
    private const val KEY_SCALE = "scale"
    private const val KEY_OFFSET_Y_RATIO = "offset_y_ratio"

    fun load(context: Context): Pair<Float, Float>? {
        return try {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!sp.contains(KEY_SCALE) && !sp.contains(KEY_OFFSET_Y_RATIO)) null
            else Pair(sp.getFloat(KEY_SCALE, 1f), sp.getFloat(KEY_OFFSET_Y_RATIO, 0f))
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, scale: Float, offsetYRatio: Float) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_SCALE, scale)
                .putFloat(KEY_OFFSET_Y_RATIO, offsetYRatio)
                .apply()
        } catch (_: Exception) {}
    }
}
