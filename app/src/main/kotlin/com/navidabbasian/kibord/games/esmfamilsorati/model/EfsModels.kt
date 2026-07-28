package com.navidabbasian.kibord.games.esmfamilsorati.model

import kotlinx.serialization.Serializable

/** ثابت‌های زمانی اسم فامیل سرعتی — همه به میلی‌ثانیه */
object EfsConstants {
    const val PLAYER_BANK_MILLIS = 60_000L
    const val BOMB_MILLIS = 30_000L
    const val BOMB_PENALTY_MILLIS = 5_000L
    const val PASS_PENALTY_MILLIS = 1_000L
    const val PASS_COOLDOWN_MILLIS = 2_000L
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 8
}

/**
 * کارت حرف: حروف هم‌آوا یک کارت‌اند و همه‌ی املاها با هم نمایش داده می‌شوند —
 * جواب با هر کدام از املاها شروع شود قبول است.
 */
@Serializable
data class EfsLetterCard(val spellings: List<String>) {
    val display: String get() = spellings.joinToString("  ")
    val isGroup: Boolean get() = spellings.size > 1
    /** شناسه‌ی پایدار کارت برای شمارش پاس‌ها و حذف از چرخه */
    val key: String get() = spellings.joinToString("")
}

/**
 * دسته‌ی کامل ۲۲ کارتی: ۶ کارت گروهیِ هم‌آوا + ۱۶ حرف تکی.
 * به خواست ارباب «ع» با «ا/آ» یکی است و «ژ» در دسته می‌ماند.
 */
val EFS_LETTER_CARDS: List<EfsLetterCard> = listOf(
    listOf("ا", "آ", "ع"),
    listOf("ت", "ط"),
    listOf("ث", "س", "ص"),
    listOf("ح", "ه"),
    listOf("ذ", "ز", "ض", "ظ"),
    listOf("غ", "ق"),
    listOf("ب"), listOf("پ"), listOf("ج"), listOf("چ"), listOf("خ"), listOf("د"),
    listOf("ر"), listOf("ژ"), listOf("ش"), listOf("ف"), listOf("ک"), listOf("گ"),
    listOf("ل"), listOf("م"), listOf("ن"), listOf("و"), listOf("ی"),
).map { EfsLetterCard(it) }

/** موضوع بازی — از بانک محتوا یا فهرست جایگزین */
@Serializable
data class EfsTopic(
    val id: String,
    val name: String,
    val emoji: String = "",
)

@Serializable
data class EfsPlayer(
    val id: Int,
    val name: String,
    val remainingTimeMillis: Long = EfsConstants.PLAYER_BANK_MILLIS,
    val eliminated: Boolean = false,
)

@Serializable
sealed class EfsPhase {
    @Serializable data object PlayerCount : EfsPhase()
    @Serializable data object PlayerNames : EfsPhase()
    @Serializable data object TopicSelect : EfsPhase()
    @Serializable data object Playing : EfsPhase()
    @Serializable data class PlayerEliminated(val player: EfsPlayer) : EfsPhase()
    @Serializable data class Winner(val player: EfsPlayer?) : EfsPhase()
}

@Serializable
data class EfsUiState(
    val phase: EfsPhase = EfsPhase.PlayerCount,
    val playerCount: Int = 4,
    val playerNames: List<String> = List(4) { "" },
    /** کل بانک موضوع‌ها برای صفحه‌ی انتخاب */
    val topics: List<EfsTopic> = emptyList(),
    /** موضوع انتخاب‌شده در صفحه‌ی انتخاب (پیش از تایید) و موضوع بازی پس از شروع */
    val topic: EfsTopic? = null,
    val players: List<EfsPlayer> = emptyList(),
    val currentPlayerIndex: Int = 0,
    val currentCard: EfsLetterCard? = null,
    /** باقیمانده‌ی دسته‌ی بُرخورده — سریالایز می‌شود تا بازیابی نشست، بدون‌تکراری را حفظ کند */
    val deck: List<EfsLetterCard> = emptyList(),
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val bombTimeLeftMillis: Long = EfsConstants.BOMB_MILLIS,
    val passCooldownLeftMillis: Long = 0,
    val canPass: Boolean = true,
    val showExplosion: Boolean = false,
    val showPauseDialog: Boolean = false,
    /** شمار پاس‌های هر کارت در این دست — کلید همان EfsLetterCard.key است */
    val passCounts: Map<String, Int> = emptyMap(),
    /** کارت‌هایی که ۲ بار پاس شدند و از چرخه‌ی این دست حذف‌اند (دسته آن حرف را ندارد) */
    val removedCardKeys: Set<String> = emptySet(),
) {
    val currentPlayer: EfsPlayer? get() = players.getOrNull(currentPlayerIndex)
}

/** رویدادهای صوتی/لرزشی یک‌بارمصرف که ریشه‌ی بازی به SoundManager می‌رساند */
enum class EfsSoundEvent {
    TICK_NORMAL,
    TICK_FAST,
    EXPLOSION,
    WORD_CORRECT,
    NEXT_TURN,
    PASS_LETTER,
    PLAYER_ELIMINATED,
    GAME_OVER,
    VIBRATE_LONG,
    VIBRATE_SHORT,
}
