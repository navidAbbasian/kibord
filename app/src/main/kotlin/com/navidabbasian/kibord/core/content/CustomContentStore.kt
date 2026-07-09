package com.navidabbasian.kibord.core.content

import android.content.Context

/**
 * محتوای سفارشی کاربر (کتگوری‌های ساخته‌شده در تنظیمات) — روی SharedPreferences.
 *
 * هر بازی یک کلید دارد و مقدارش JSON آرایه‌ای از کتگوری‌ها با همان قالب
 * فایل‌های assets است؛ مخزن‌های بازی هنگام بارگذاری آن را ادغام می‌کنند.
 * جدا از کش دانلودی است تا به‌روزرسانی از گیت‌هاب پاکش نکند.
 */
class CustomContentStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("kibord_custom_content", Context.MODE_PRIVATE)

    fun getJson(gameKey: String): String = prefs.getString(gameKey, "") ?: ""

    fun setJson(gameKey: String, json: String) {
        prefs.edit().putString(gameKey, json).apply()
    }

    companion object {
        const val DOR = "dor_categories"
        const val GANDEGOO = "gandegoo_categories"
        const val PANTOMIME = "pantomime_categories"
    }
}
