package com.navidabbasian.kibord.games.whoami.model

import com.navidabbasian.kibord.games.esmfamil.model.sameName
import kotlinx.serialization.Serializable

const val WA_MIN_PLAYERS = 2
const val WA_MAX_PLAYERS = 8

/** گزینه‌های تعداد سوال هر راند */
val WA_QUESTION_CHOICES = listOf(10, 20)

@Serializable
data class WaPlayer(
    val name: String,
    val colorIndex: Int,
    val connected: Boolean = true,
    val totalScore: Int = 0,
)

@Serializable
enum class WaPhase { LOBBY, WRITE, COUNTDOWN, PLAY, ROUND_RESULT, GAME_OVER }

/**
 * عکس لحظه‌ای کامل وضعیت بازی — میزبان تنها مرجع حقیقت است.
 * هر گوشی فقط اسمِ روی پیشانیِ صاحبش را بزرگ نشان می‌دهد.
 */
@Serializable
data class WaSnapshot(
    val phase: WaPhase = WaPhase.LOBBY,
    val players: List<WaPlayer> = emptyList(),
    val hostName: String = "",
    /** شماره‌ی راند جاری از ۱ */
    val roundIndex: Int = 0,
    /** تعداد راندها — میزبان موقع ساخت بازی تعیین می‌کند */
    val totalRounds: Int = 3,
    /** سقف سوال هر بازیکن در هر راند: ۱۰ یا ۲۰ */
    val questionsTotal: Int = 10,
    /** نویسنده → کسانی که برایشان می‌نویسد (میزبان در تعداد فرد دو هدف دارد) */
    val targets: Map<String, List<String>> = emptyMap(),
    /** صاحب پیشانی → اسم مخفی نوشته‌شده برایش */
    val assignments: Map<String, String> = emptyMap(),
    /** بازیکن → تعداد سوالی که در این راند پرسیده (هر تکان سر یکی) */
    val questionsUsed: Map<String, Int> = emptyMap(),
    /** ترتیب جواب‌دادن‌ها در این راند — لیست راند بعد */
    val guessedOrder: List<String> = emptyList(),
    /** کسانی که سوال‌هایشان تمام شد و جواب ندادند — بازنده‌ی راند */
    val outPlayers: List<String> = emptyList(),
) {
    fun player(name: String): WaPlayer? = players.firstOrNull { sameName(it.name, name) }

    /** همه‌ی کسانی که این نویسنده باید برایشان اسم بنویسد */
    fun targetsOf(writer: String): List<String> =
        targets.entries.firstOrNull { sameName(it.key, writer) }?.value ?: emptyList()

    fun assignmentOf(owner: String): String =
        assignments.entries.firstOrNull { sameName(it.key, owner) }?.value ?: ""

    /** هدف‌هایی که این نویسنده هنوز برایشان ننوشته */
    fun pendingTargetsOf(writer: String): List<String> =
        targetsOf(writer).filter { assignmentOf(it).isBlank() }

    fun questionsUsedOf(name: String): Int =
        questionsUsed.entries.firstOrNull { sameName(it.key, name) }?.value ?: 0

    fun questionsLeftOf(name: String): Int =
        (questionsTotal - questionsUsedOf(name)).coerceAtLeast(0)

    fun hasGuessed(name: String): Boolean = guessedOrder.any { sameName(it, name) }

    fun isOut(name: String): Boolean = outPlayers.any { sameName(it, name) }

    /** هنوز وسط راند است: کلمه دارد، نه جواب داده نه سوخته */
    fun stillPlaying(name: String): Boolean =
        assignmentOf(name).isNotBlank() && !hasGuessed(name) && !isOut(name)

    val connectedPlayers: List<WaPlayer> get() = players.filter { it.connected }
}
