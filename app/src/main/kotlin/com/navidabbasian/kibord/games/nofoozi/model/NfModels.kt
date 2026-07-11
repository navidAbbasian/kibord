package com.navidabbasian.kibord.games.nofoozi.model

import com.navidabbasian.kibord.games.esmfamil.model.sameName
import kotlinx.serialization.Serializable

/** یک جفت کلمه‌ی شبیه: شهروندها یکی را می‌گیرند و نفوذی آن یکی را */
@Serializable
data class NfPair(
    val word: String,
    val similar: String,
)

const val NF_MIN_PLAYERS = 3
const val NF_MAX_PLAYERS = 8

/** امتیاز شهروندها وقتی نفوذی لو برود */
const val NF_CITIZEN_SCORE = 10

/** امتیاز نفوذی وقتی قِسِر در برود */
const val NF_UNDERCOVER_SCORE = 20

@Serializable
data class NfPlayer(
    val name: String,
    val colorIndex: Int,
    val connected: Boolean = true,
    val totalScore: Int = 0,
)

@Serializable
enum class NfPhase { LOBBY, REVEAL, DISCUSSION, VOTE, ROUND_RESULT, GAME_OVER }

/**
 * عکس لحظه‌ای کامل وضعیت بازی — میزبان تنها مرجع حقیقت است و
 * بعد از هر تغییر برای همه پخش می‌شود. هر گوشی فقط کلمه‌ی خودش را نشان می‌دهد.
 */
@Serializable
data class NfSnapshot(
    val phase: NfPhase = NfPhase.LOBBY,
    val players: List<NfPlayer> = emptyList(),
    val hostName: String = "",
    val totalRounds: Int = 3,
    /** شماره‌ی راند جاری از ۱ */
    val roundIndex: Int = 0,
    /** کلمه‌ی هر بازیکن در این راند */
    val words: Map<String, String> = emptyMap(),
    /** نفوذیِ این راند — تا صفحه‌ی نتیجه هیچ‌جا نمایش داده نمی‌شود */
    val undercoverName: String = "",
    /** چه کسانی کلمه‌شان را دیده‌اند */
    val seen: List<String> = emptyList(),
    /** رای هر بازیکن: رای‌دهنده → متهم */
    val votes: Map<String, String> = emptyMap(),
    /** متهمِ نهایی راند — خالی یعنی رای‌ها پخش شد و کسی اکثریت نیاورد */
    val accusedName: String = "",
    /** نفوذی گرفتار شد؟ */
    val caught: Boolean = false,
) {
    fun player(name: String): NfPlayer? = players.firstOrNull { sameName(it.name, name) }

    fun wordOf(name: String): String =
        words.entries.firstOrNull { sameName(it.key, name) }?.value ?: ""

    fun hasSeen(name: String): Boolean = seen.any { sameName(it, name) }

    fun voteOf(name: String): String? =
        votes.entries.firstOrNull { sameName(it.key, name) }?.value

    val connectedPlayers: List<NfPlayer> get() = players.filter { it.connected }
}
