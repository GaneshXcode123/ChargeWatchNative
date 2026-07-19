package com.chargewatch.app

import android.content.Context

/**
 * Small wrapper around SharedPreferences.
 * Change defaults here if you want the app to start with a different
 * target percentage or alert mode out of the box.
 */
object Prefs {
    private const val FILE = "charge_watch_prefs"
    private const val KEY_TARGET = "target"
    private const val KEY_MODE = "mode"           // "both" | "message" | "alarm"
    private const val KEY_MONITORING = "monitoring"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getTarget(ctx: Context): Int = prefs(ctx).getInt(KEY_TARGET, 100)
    fun setTarget(ctx: Context, value: Int) = prefs(ctx).edit().putInt(KEY_TARGET, value).apply()

    fun getMode(ctx: Context): String = prefs(ctx).getString(KEY_MODE, "both") ?: "both"
    fun setMode(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_MODE, value).apply()

    fun isMonitoring(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_MONITORING, false)
    fun setMonitoring(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_MONITORING, value).apply()
}
