package com.navidabbasian.kibord.games.gandegoo.model

import com.navidabbasian.kibord.core.util.toPersianDigits
import kotlinx.serialization.Serializable

@Serializable
data class GgQuestion(
    val points: Int,
    val text: String,
)

@Serializable
data class GgCategory(
    val id: String,
    val name: String,
    val emoji: String = "",
    val questions: List<GgQuestion> = emptyList(),
)

/** آدرس یک خانه‌ی جدول: شماره‌ی کتگوری + شماره‌ی سوال (۰ تا ۲) */
data class GgCell(
    val categoryIndex: Int,
    val questionIndex: Int,
)

/** نتیجه‌ی یک دست بازی */
data class GgOutcome(
    val cell: GgCell,
    val claimingTeam: Int,
    val claim: Int,
    val counted: Int,
    val points: Int,
    /** تغییر امتیاز هر تیم در این دست */
    val deltas: List<Int>,
    val kind: Kind,
) {
    enum class Kind {
        /** به ادعا رسید — امتیاز کامل */
        FULL,
        /** نصف یا بیشترِ ادعا — امتیاز به حریف(ها)، بدون منفی */
        PARTIAL,
        /** کمتر از نصف — منفی برای بازی‌کننده و مثبت برای بقیه */
        FAIL,
    }
}

sealed class GgPhase {
    data object TeamCount : GgPhase()
    data object TeamNames : GgPhase()
    data object Setup : GgPhase()
    data object Board : GgPhase()
    /** ثبت نتیجه‌ی گنده‌گویی حضوری: کدام تیم با چه ادعایی */
    data object Bid : GgPhase()
    /** ۳۰ ثانیه شمارش با دکمه */
    data object Play : GgPhase()
    data object Result : GgPhase()
    data object Winner : GgPhase()
}

data class GandeGooUiState(
    val phase: GgPhase = GgPhase.TeamCount,
    val teamCount: Int = 2,
    val teamNames: List<String> = List(3) { "" },
    val scores: List<Int> = List(3) { 0 },
    /** تعداد کتگوری‌های این بازی (۳/۴/۶) */
    val categoryCount: Int = 6,
    /** کتگوری‌های انتخاب‌شده برای این بازی */
    val categories: List<GgCategory> = emptyList(),
    val usedCells: Set<GgCell> = emptySet(),
    /** نوبت انتخاب کتگوری با کدام تیم است */
    val pickingTeam: Int = 0,
    val selectedCell: GgCell? = null,
    val claimingTeam: Int = 0,
    val claim: Int = 5,
    val timeLeftMillis: Long = TURN_MILLIS,
    val counted: Int = 0,
    val lastOutcome: GgOutcome? = null,
) {
    val selectedQuestion: GgQuestion?
        get() = selectedCell?.let { categories.getOrNull(it.categoryIndex)?.questions?.getOrNull(it.questionIndex) }

    val selectedCategory: GgCategory?
        get() = selectedCell?.let { categories.getOrNull(it.categoryIndex) }

    val totalCells: Int get() = categories.size * 3

    val allCellsPlayed: Boolean get() = categories.isNotEmpty() && usedCells.size >= totalCells

    fun teamDisplayName(index: Int): String =
        teamNames.getOrNull(index)?.ifBlank { "تیم ${(index + 1).toPersianDigits()}" }
            ?: "تیم ${(index + 1).toPersianDigits()}"

    companion object {
        const val TURN_MILLIS = 30_000L
    }
}

/** رویدادهای صوتی یک‌بارمصرف برای اتصال به SoundManager در ریشه‌ی بازی */
enum class GgSoundEvent {
    TICK,
    TICK_WARNING,
    TIME_UP,
    FULL_SUCCESS,
    GAME_OVER,
}
