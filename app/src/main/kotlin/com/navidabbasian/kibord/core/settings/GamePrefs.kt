package com.navidabbasian.kibord.core.settings

import android.content.Context

/**
 * حافظه‌ی تنظیمات بازی‌ها: آخرین اسم تیم‌ها، تعداد راند و ثانیه‌ها
 * ذخیره می‌شود تا دفعه‌ی بعد شروعِ بازی یک‌ضربه‌ای باشد.
 */
object GamePrefs {

    private const val PREFS = "kibord_game_prefs"
    private const val NAME_SEPARATOR = ""

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getInt(context: Context, key: String, default: Int): Int =
        prefs(context).getInt(key, default)

    fun getBool(context: Context, key: String, default: Boolean): Boolean =
        prefs(context).getBoolean(key, default)

    fun setBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun setInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }

    fun getNames(context: Context, key: String): List<String> =
        prefs(context).getString(key, null)
            ?.split(NAME_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun setNames(context: Context, key: String, names: List<String>) {
        prefs(context).edit()
            .putString(key, names.joinToString(NAME_SEPARATOR) { it.trim() })
            .apply()
    }
}
