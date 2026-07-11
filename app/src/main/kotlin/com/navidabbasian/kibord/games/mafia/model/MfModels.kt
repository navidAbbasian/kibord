package com.navidabbasian.kibord.games.mafia.model

import com.navidabbasian.kibord.games.esmfamil.model.sameName
import kotlinx.serialization.Serializable

@Serializable
enum class MfRole(val title: String, val emoji: String) {
    MAFIA("مافیا", "😈"),
    DOCTOR("دکتر", "🩺"),
    DETECTIVE("کارآگاه", "🕵️"),
    CITIZEN("شهروند", "🧑"),
}

const val MF_MIN_PLAYERS = 5
const val MF_MAX_PLAYERS = 8

/** تعداد مافیا بر اساس جمعیت: تا ۶ نفر یکی، از ۷ نفر دو تا */
fun mafiaCountFor(players: Int): Int = if (players >= 7) 2 else 1

@Serializable
data class MfPlayer(
    val name: String,
    val colorIndex: Int,
    val connected: Boolean = true,
    val alive: Boolean = true,
)

@Serializable
enum class MfPhase { LOBBY, ROLE_REVEAL, NIGHT, DAY_ANNOUNCE, DAY_VOTE, DAY_RESULT, GAME_OVER }

/** برنده‌ی بازی — خالی یعنی هنوز ادامه دارد */
@Serializable
enum class MfWinner { NONE, CITIZENS, MAFIA }

/**
 * عکس لحظه‌ای کامل وضعیت بازی — میزبان تنها مرجع حقیقت است و
 * بعد از هر تغییر برای همه پخش می‌شود. هر گوشی فقط نقش خودش را نشان می‌دهد.
 */
@Serializable
data class MfSnapshot(
    val phase: MfPhase = MfPhase.LOBBY,
    val players: List<MfPlayer> = emptyList(),
    val hostName: String = "",
    /** نقش هر بازیکن — گوشی‌ها فقط نقش خودشان را نمایش می‌دهند */
    val roles: Map<String, MfRole> = emptyMap(),
    /** شماره‌ی شب جاری از ۱ */
    val nightIndex: Int = 0,
    /** چه کسانی نقش‌شان را دیده‌اند */
    val seen: List<String> = emptyList(),
    /** رای شبانه‌ی هر مافیا: مافیا → قربانی */
    val mafiaVotes: Map<String, String> = emptyMap(),
    /** انتخاب دکتر برای نجات امشب */
    val doctorSave: String = "",
    /** استعلام کارآگاه امشب — نتیجه را گوشی خودش از نقش‌ها درمی‌آورد */
    val detectiveCheck: String = "",
    /** نتیجه‌ی شب: چه کسی کشته شد — خالی یعنی نجات پیدا کرد/کسی نمرد */
    val lastKilled: String = "",
    /** دکتر درست حدس زد و قربانی نجات یافت */
    val lastSaved: Boolean = false,
    /** رای روز: رای‌دهنده → متهم */
    val dayVotes: Map<String, String> = emptyMap(),
    /** اعدامیِ رای روز — خالی یعنی رای‌ها پخش شد */
    val lastLynched: String = "",
    val winner: MfWinner = MfWinner.NONE,
) {
    fun player(name: String): MfPlayer? = players.firstOrNull { sameName(it.name, name) }

    fun roleOf(name: String): MfRole? =
        roles.entries.firstOrNull { sameName(it.key, name) }?.value

    fun hasSeen(name: String): Boolean = seen.any { sameName(it, name) }

    fun dayVoteOf(name: String): String? =
        dayVotes.entries.firstOrNull { sameName(it.key, name) }?.value

    fun mafiaVoteOf(name: String): String? =
        mafiaVotes.entries.firstOrNull { sameName(it.key, name) }?.value

    val alivePlayers: List<MfPlayer> get() = players.filter { it.alive }

    val aliveMafia: List<MfPlayer>
        get() = players.filter { it.alive && roleOf(it.name) == MfRole.MAFIA }
}
