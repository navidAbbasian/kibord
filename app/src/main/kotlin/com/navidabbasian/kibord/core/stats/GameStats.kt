package com.navidabbasian.kibord.core.stats

import android.content.Context
import android.os.SystemClock

/**
 * دفترچه‌ی آمار محلی: شمار بازی‌های انجام‌شده و بردهای هر اسم.
 * همه‌چیز روی همین گوشی می‌ماند — هیچ سروری در کار نیست.
 */
object GameStats {

    private const val PREFS = "kibord_stats"
    private const val KEY_PLAYS = "plays_"
    private const val KEY_NAME_WINS = "namewins_"
    private const val KEY_GAME_IDS = "played_game_ids"
    private const val KEY_WINNER_NAMES = "winner_names"
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
    ) {
        val p = prefs(context)
        if (token != null) {
            val seen = p.getString(KEY_RECORDED_TOKENS, "").orEmpty().split('\n')
            if (token in seen) return
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
        if (last != Long.MIN_VALUE && now >= last && now - last < DUPLICATE_WINDOW_MILLIS) return

        val e = p.edit()
        e.putLong(identity, now)
        e.putInt(KEY_PLAYS + gameId, p.getInt(KEY_PLAYS + gameId, 0) + 1)
        e.putStringSet(KEY_GAME_IDS, (p.getStringSet(KEY_GAME_IDS, emptySet()) ?: emptySet()) + gameId)
        val knownNames = (p.getStringSet(KEY_WINNER_NAMES, emptySet()) ?: emptySet()).toMutableSet()
        winnerNames.map { it.trim() }.filter { it.isNotBlank() && it.length <= 24 }.forEach { name ->
            e.putInt(KEY_NAME_WINS + name, p.getInt(KEY_NAME_WINS + name, 0) + 1)
            knownNames += name
        }
        e.putStringSet(KEY_WINNER_NAMES, knownNames)
        e.apply()
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

    /** پرافتخارترین اسم‌ها: از بیشترین برد به کمترین */
    fun topWinners(context: Context, limit: Int = 5): List<Pair<String, Int>> {
        val p = prefs(context)
        return (p.getStringSet(KEY_WINNER_NAMES, emptySet()) ?: emptySet())
            .map { it to p.getInt(KEY_NAME_WINS + it, 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
    }

    /** بیشترین بردِ ثبت‌شده برای یک اسم */
    fun bestWinCount(context: Context): Int =
        topWinners(context, 1).firstOrNull()?.second ?: 0

    /** چند بازی متفاوت حداقل یک بار انجام شده */
    fun distinctGamesPlayed(context: Context): Int =
        playsByGame(context).size
}
