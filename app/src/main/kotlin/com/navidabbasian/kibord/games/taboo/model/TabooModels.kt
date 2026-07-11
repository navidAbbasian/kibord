package com.navidabbasian.kibord.games.taboo.model

import com.navidabbasian.kibord.core.util.toPersianDigits
import kotlinx.serialization.Serializable

/** یک کارت تابو: کلمه‌ی اصلی و پنج کلمه‌ی ممنوعه */
@Serializable
data class TabooCard(
    val word: String,
    val forbidden: List<String> = emptyList(),
)

sealed class TabooPhase {
    data object TeamNames : TabooPhase()
    /** تنظیمات: مدت نوبت و تعداد راند */
    data object Settings : TabooPhase()
    /** گوشی دست گوینده‌ی تیم بعدی */
    data class TurnReady(val team: Int) : TabooPhase()
    data object Turn : TabooPhase()
    data class TurnEnd(val team: Int, val correct: Int, val foul: Int) : TabooPhase()
    data object Winner : TabooPhase()
}

data class TabooUiState(
    val phase: TabooPhase = TabooPhase.TeamNames,
    val teamNames: List<String> = List(2) { "" },
    val scores: List<Int> = List(2) { 0 },
    val turnSeconds: Int = 60,
    val totalRounds: Int = 3,
    /** راند جاری از ۱ */
    val roundIndex: Int = 1,
    /** تیم در حال بازی */
    val currentTeam: Int = 0,
    val secondsLeft: Int = 0,
    val currentCard: TabooCard? = null,
    val turnCorrect: Int = 0,
    val turnFoul: Int = 0,
) {
    fun teamDisplayName(index: Int): String =
        teamNames.getOrNull(index)?.ifBlank { "تیم ${(index + 1).toPersianDigits()}" }
            ?: "تیم ${(index + 1).toPersianDigits()}"
}

/** رویدادهای صوتی برای اتصال در ریشه‌ی بازی */
enum class TabooSoundEvent { TICK, TICK_WARNING, TIME_UP, CORRECT, FOUL, SKIP, GAME_OVER }
