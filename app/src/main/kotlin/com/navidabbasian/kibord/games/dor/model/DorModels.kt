package com.navidabbasian.kibord.games.dor.model

import kotlinx.serialization.Serializable

@Serializable
data class DorWord(
    val text: String,
    val category: String = "",
    val difficulty: Int = 1,
)

@Serializable
data class DorCategory(
    val id: String,
    val name: String,
    val emoji: String = "",
    val words: List<DorWord> = emptyList(),
)

/** حالت‌های بازی دور — زمان‌ها به میلی‌ثانیه */
enum class DorGameMode(
    val teamTimeMillis: Long,
    val bombTimeMillis: Long,
    val penaltyMillis: Long,
    val skipCooldownMillis: Long,
) {
    QUICK(90_000, 25_000, 15_000, 5_000),
    PROFESSIONAL(165_000, 40_000, 15_000, 5_000);
}

data class DorGameEvent(
    val type: EventType,
    val wordText: String,
    val timeSpentMillis: Long,
    val penaltyMillis: Long = 0,
) {
    enum class EventType { WORD_GUESSED, BOMB_EXPLODED }
}

data class DorTeam(
    val id: Int,
    val players: List<String>,
    val remainingTimeMillis: Long,
    val eliminated: Boolean = false,
    val events: List<DorGameEvent> = emptyList(),
)

/** جایگاه هر بازیکن در حلقه: نام + شماره تیم */
data class DorSeat(
    val name: String,
    val teamIndex: Int,
)

sealed class DorPhase {
    data object PlayerCount : DorPhase()
    data object PlayerNames : DorPhase()
    data object Categories : DorPhase()
    data object Mode : DorPhase()
    data object Playing : DorPhase()
    data class TeamEliminated(val team: DorTeam) : DorPhase()
    data class Winner(val team: DorTeam?) : DorPhase()
}

data class DorUiState(
    val phase: DorPhase = DorPhase.PlayerCount,
    val playerCount: Int = 4,
    val playerNames: List<String> = List(4) { "" },
    val categories: List<DorCategory> = emptyList(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val mode: DorGameMode = DorGameMode.QUICK,
    val teams: List<DorTeam> = emptyList(),
    val circularOrder: List<DorSeat> = emptyList(),
    val currentTeamIndex: Int = 0,
    /** کدام بازیکن هر تیم (۰ یا ۱) در حال بازی است */
    val currentPlayerSlot: Int = 0,
    val currentWord: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val bombTimeLeftMillis: Long = DorGameMode.QUICK.bombTimeMillis,
    val skipCooldownLeftMillis: Long = 0,
    val canSkip: Boolean = false,
    val showExplosion: Boolean = false,
    val showPauseDialog: Boolean = false,
) {
    val currentTeam: DorTeam? get() = teams.getOrNull(currentTeamIndex)

    val currentPlayerName: String
        get() = currentTeam?.players?.getOrNull(currentPlayerSlot) ?: ""

    /** جایگاه بازیکن فعلی در حلقه: اول همه‌ی نفرهای اول، بعد نفرهای دوم */
    val currentSeatIndex: Int
        get() = currentTeamIndex + currentPlayerSlot * teams.size
}

/** رویدادهای صوتی/لرزشی یک‌بارمصرف که ریشه‌ی بازی به SoundManager می‌رساند */
enum class DorSoundEvent {
    TICK_NORMAL,
    TICK_FAST,
    START_TENSION,
    STOP_TENSION,
    EXPLOSION,
    VIBRATE_LONG,
    VIBRATE_SHORT,
    WORD_CORRECT,
    NEXT_TURN,
    TEAM_ELIMINATED,
    GAME_OVER,
}
