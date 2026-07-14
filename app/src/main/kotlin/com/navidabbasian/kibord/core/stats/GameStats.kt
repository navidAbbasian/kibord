package com.navidabbasian.kibord.core.stats

import android.content.Context

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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** ثبت یک بازیِ تمام‌شده به همراه برنده‌هایش */
    fun recordGameFinished(context: Context, gameId: String, winnerNames: List<String> = emptyList()) {
        val p = prefs(context)
        val e = p.edit()
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
