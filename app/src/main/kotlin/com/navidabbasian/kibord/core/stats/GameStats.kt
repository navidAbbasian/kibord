package com.navidabbasian.kibord.core.stats

import android.content.Context
import com.navidabbasian.kibord.core.analytics.Analytics
import android.os.SystemClock

/**
 * دفترچه‌ی محلیِ این گوشی: فقط شمار بازی‌های انجام‌شده (برای نشان‌ها و
 * پیشنهاد امتیازدهی). آمارِ بازیکن‌ها — برد و باخت هر آدم — فقط در بازی
 * اینترنتی و روی حساب آنلاینش ثبت می‌شود؛ بازی آفلاین چیزی برای کسی
 * نمی‌نویسد چون اسم‌هایش دستی است.
 */
object GameStats {

    private const val PREFS = "kibord_stats"
    private const val KEY_PLAYS = "plays_"
    private const val KEY_GAME_IDS = "played_game_ids"
    private const val KEY_RECORDED_TOKENS = "recorded_tokens"
    private const val MAX_REMEMBERED_TOKENS = 64
    private const val KEY_LAST_RECORD = "lastrecord_"

    /**
     * دو ثبتِ یکسان که فاصله‌شان از این کمتر باشد، یک دستِ واحد شمرده می‌شود.
     * گذارِ صفحه‌ها محتوای تازه را چند صد میلی‌ثانیه دوبار می‌سازد؛ هیچ دستِ
     * واقعی‌ای هم در این بازه دو بار تمام نمی‌شود.
     */
    private const val DUPLICATE_WINDOW_MILLIS = 15_000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * ثبت یک بازیِ تمام‌شده به همراه برنده‌هایش.
     *
     * token شناسه‌ی یکتای همان «دستِ» تمام‌شده است: اگر صفحه‌ی برنده دوباره
     * ساخته شود (چرخش، بازساخت اکتیویتی، انیمیشن گذار…) همان token می‌آید و
     * ثبت تکراری نمی‌شود — ریشه‌ی باگِ «۱ دست بازی، ۲-۳ برد ثبت‌شده».
     */
    fun recordGameFinished(
        context: Context,
        gameId: String,
        winnerNames: List<String> = emptyList(),
        token: String? = null,
    ): Boolean {
        val p = prefs(context)
        if (token != null) {
            val seen = p.getString(KEY_RECORDED_TOKENS, "").orEmpty().split('\n')
            if (token in seen) return false
            p.edit().putString(
                KEY_RECORDED_TOKENS,
                (seen + token).takeLast(MAX_REMEMBERED_TOKENS).joinToString("\n"),
            ).apply()
        }

        // شناسه‌ی خودِ دستِ تمام‌شده — مستقل از اینکه رابط کاربری چند بار
        // ساخته شود. اگر همین دست همین الان ثبت شده، دوباره ثبت نمی‌شود.
        val names = winnerNames.map { it.trim() }.filter { it.isNotBlank() }.sorted()
        val identity = KEY_LAST_RECORD + gameId + "|" + names.joinToString(",")
        val now = SystemClock.elapsedRealtime()
        val last = p.getLong(identity, Long.MIN_VALUE)
        // now < last یعنی گوشی بین دو ثبت ری‌استارت شده؛ آن ثبت کهنه است
        if (last != Long.MIN_VALUE && now >= last && now - last < DUPLICATE_WINDOW_MILLIS) return false

        val e = p.edit()
        e.putLong(identity, now)
        e.putInt(KEY_PLAYS + gameId, p.getInt(KEY_PLAYS + gameId, 0) + 1)
        e.putStringSet(KEY_GAME_IDS, (p.getStringSet(KEY_GAME_IDS, emptySet()) ?: emptySet()) + gameId)
        e.apply()
        // این تنها جایی است که یک بازی «واقعاً تمام‌شده» شناخته می‌شود
        Analytics.gameFinished(gameId)
        return true
    }

    /** جمع همه‌ی بازی‌های انجام‌شده */
    fun totalPlays(context: Context): Int {
        val p = prefs(context)
        return (p.getStringSet(KEY_GAME_IDS, emptySet()) ?: emptySet())
            .sumOf { p.getInt(KEY_PLAYS + it, 0) }
    }

    /** شمار بازی‌ها به تفکیک شناسه‌ی بازی */
    fun playsByGame(context: Context): Map<String, Int> {
        val p = prefs(context)
        return (p.getStringSet(KEY_GAME_IDS, emptySet()) ?: emptySet())
            .associateWith { p.getInt(KEY_PLAYS + it, 0) }
            .filterValues { it > 0 }
    }


    /** چند بازی متفاوت حداقل یک بار انجام شده */
    fun distinctGamesPlayed(context: Context): Int =
        playsByGame(context).size
}
