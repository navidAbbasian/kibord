package com.navidabbasian.kibord.core.content

import android.content.Context

/**
 * سابقه‌ی محتوای بازی‌شده در هر بازی — روی SharedPreferences.
 *
 * هر بازی یک کلید دارد و مجموعه‌ی شناسه‌ی آیتم‌های دیده‌شده را نگه می‌دارد؛
 * تا وقتی همه‌ی آیتم‌های یک بانک یک بار بازی نشده‌اند، آیتم تکراری نمی‌آید.
 * خواندن/نوشتن همگام است چون مخزن‌های کلمات همگام کار می‌کنند.
 */
class PlayedContentStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("kibord_played_content", Context.MODE_PRIVATE)

    fun played(gameKey: String): Set<String> =
        prefs.getStringSet(gameKey, emptySet())?.toSet() ?: emptySet()

    fun markPlayed(gameKey: String, itemKey: String) = markPlayed(gameKey, listOf(itemKey))

    fun markPlayed(gameKey: String, itemKeys: Collection<String>) {
        if (itemKeys.isEmpty()) return
        val updated = played(gameKey) + itemKeys
        prefs.edit().putStringSet(gameKey, updated).apply()
    }

    /** حذف سابقه‌ی آیتم‌های مشخص — وقتی یک دورِ کامل از آن زیرمجموعه تمام شده */
    fun forget(gameKey: String, itemKeys: Collection<String>) {
        if (itemKeys.isEmpty()) return
        val updated = played(gameKey) - itemKeys.toSet()
        prefs.edit().putStringSet(gameKey, updated).apply()
    }

    fun clear(gameKey: String) {
        prefs.edit().remove(gameKey).apply()
    }

    companion object {
        const val GAME_DOR = "dor"
        const val GAME_PANTOMIME = "pantomime"
        const val GAME_PANTOMIME_CATEGORIES = "pantomime_categories"
        const val GAME_SEDASAZI = "sedasazi"
        const val GAME_SEDASAZI_CATEGORIES = "sedasazi_categories"
        const val GAME_GANDEGOO = "gandegoo"
        const val GAME_GANDEGOO_QUESTIONS = "gandegoo_questions"
    }
}
