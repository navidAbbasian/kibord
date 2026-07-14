package com.navidabbasian.kibord.games.whoami.model

import com.navidabbasian.kibord.games.esmfamil.model.sameName
import kotlinx.serialization.Serializable

const val WA_MIN_PLAYERS = 3
const val WA_MAX_PLAYERS = 8

@Serializable
data class WaPlayer(
    val name: String,
    val colorIndex: Int,
    val connected: Boolean = true,
    val totalScore: Int = 0,
)

@Serializable
enum class WaPhase { LOBBY, WRITE, PLAY, ROUND_RESULT, GAME_OVER }

/**
 * عکس لحظه‌ای کامل وضعیت بازی — میزبان تنها مرجع حقیقت است.
 * هر گوشی فقط اسمِ روی پیشانیِ صاحبش را بزرگ نشان می‌دهد.
 */
@Serializable
data class WaSnapshot(
    val phase: WaPhase = WaPhase.LOBBY,
    val players: List<WaPlayer> = emptyList(),
    val hostName: String = "",
    /** شماره‌ی راند جاری از ۱ — پایان بازی دست خود بازیکن‌هاست */
    val roundIndex: Int = 0,
    /** نویسنده → کسی که برایش اسم می‌نویسد (هر راند می‌چرخد) */
    val targets: Map<String, String> = emptyMap(),
    /** صاحب پیشانی → اسم مخفی نوشته‌شده برایش */
    val assignments: Map<String, String> = emptyMap(),
    /** نویسنده‌هایی که اسم‌شان را فرستاده‌اند */
    val submitted: List<String> = emptyList(),
    /** ترتیب حدس‌زدن‌ها در این راند — لیست انتظار راند بعد */
    val guessedOrder: List<String> = emptyList(),
) {
    fun player(name: String): WaPlayer? = players.firstOrNull { sameName(it.name, name) }

    fun targetOf(writer: String): String =
        targets.entries.firstOrNull { sameName(it.key, writer) }?.value ?: ""

    fun assignmentOf(owner: String): String =
        assignments.entries.firstOrNull { sameName(it.key, owner) }?.value ?: ""

    fun hasSubmitted(name: String): Boolean = submitted.any { sameName(it, name) }

    fun hasGuessed(name: String): Boolean = guessedOrder.any { sameName(it, name) }

    val connectedPlayers: List<WaPlayer> get() = players.filter { it.connected }
}
