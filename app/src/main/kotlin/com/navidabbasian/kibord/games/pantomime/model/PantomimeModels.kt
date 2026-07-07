package com.navidabbasian.kibord.games.pantomime.model

import kotlinx.serialization.Serializable

@Serializable
data class PCategory(
    val id: String,
    val name: String,
    val emoji: String = "",
    val words2: List<String> = emptyList(),
    val words4: List<String> = emptyList(),
    val words6: List<String> = emptyList(),
    val golden: String = "",
) {
    fun wordsFor(points: Int): List<String> = when (points) {
        2 -> words2
        4 -> words4
        else -> words6
    }
}

/** یک اجرای پانتومیم: کلمه، امتیاز پایه و سقف زمان */
data class PantoAttempt(
    val word: String,
    val points: Int,
    val isGolden: Boolean,
    val categoryName: String,
    val categoryEmoji: String,
    val durationMillis: Long,
)

/** نتیجه‌ی یک اجرا */
data class PantoResult(
    val attempt: PantoAttempt,
    val performingTeam: Int,
    val success: Boolean,
    val remainingMillis: Long,
    val bonus: Int,
    val earned: Int,
)

/** قواعد مشترک زمان و امتیاز هر دو پانتومیم */
object PantoRules {
    const val GOLDEN_POINTS = 30

    /** ۲ امتیازی: ۶۰ ثانیه، ۴: ۹۰، ۶: ۱۲۰، طلایی: ۱۸۰ */
    fun durationFor(points: Int, isGolden: Boolean): Long = when {
        isGolden -> 180_000L
        points <= 2 -> 60_000L
        points == 4 -> 90_000L
        else -> 120_000L
    }

    /** هر ۳۰ ثانیه‌ی کاملِ باقی‌مانده از زمان اجرا = ۱ امتیاز */
    fun bonus(remainingMillis: Long): Int = (remainingMillis / 30_000L).toInt()
}

/** رویدادهای صوتی یک‌بارمصرف برای اتصال به SoundManager در ریشه */
enum class PantoSoundEvent {
    TICK,
    TICK_WARNING,
    TIME_UP,
    SUCCESS,
    GOLDEN_FAIL,
    GAME_OVER,
}
