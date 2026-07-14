package com.navidabbasian.kibord.games.taboo.model

import com.navidabbasian.kibord.core.util.toPersianDigits
import kotlinx.serialization.Serializable

/** یک کارت تابو: کلمه‌ی اصلی و پنج کلمه‌ی ممنوعه */
@Serializable
data class TabooCard(
    val word: String,
    val forbidden: List<String> = emptyList(),
)

@Serializable
sealed class TabooPhase {
    /** انتخاب دو یا سه تیم با حباب */
    @Serializable data object TeamCount : TabooPhase()
    @Serializable data object TeamNames : TabooPhase()
    /** تنظیمات: مدت نوبت و تعداد راند */
    @Serializable data object Settings : TabooPhase()
    /** گوشی دست گوینده‌ی تیم بعدی */
    @Serializable data class TurnReady(val team: Int) : TabooPhase()
    @Serializable data object Turn : TabooPhase()
    @Serializable data class TurnEnd(val team: Int, val correct: Int, val foul: Int) : TabooPhase()
    @Serializable data object Winner : TabooPhase()
}

@Serializable
data class TabooUiState(
    val phase: TabooPhase = TabooPhase.TeamCount,
    /** دو یا سه تیم */
    val teamCount: Int = 2,
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
    /** جمع دستکاری داورانه‌ی امتیازِ همین نوبت در بخش بررسی */
    val turnBonus: Int = 0,
) {
    fun teamDisplayName(index: Int): String =
        teamNames.getOrNull(index)?.ifBlank { "تیم ${(index + 1).toPersianDigits()}" }
            ?: "تیم ${(index + 1).toPersianDigits()}"
}

/** رویدادهای صوتی برای اتصال در ریشه‌ی بازی */
enum class TabooSoundEvent { TICK, TICK_WARNING, TIME_UP, CORRECT, FOUL, SKIP, GAME_OVER }
