package com.navidabbasian.kibord.games.kalamz.model

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: Int,
    val name: String = "",
    val teamId: Int
)

@Serializable
data class Team(
    val id: Int,
    val name: String = "",
    val player1: Player,
    val player2: Player,
    val scoresPerRound: List<Int> = listOf(0, 0, 0),
    val correctWordsPerRound: List<List<String>> = listOf(emptyList(), emptyList(), emptyList())
) {
    val totalScore: Int get() = scoresPerRound.sum()
}

@Serializable
data class Word(
    val id: Int,
    val text: String,
    val submittedByPlayerId: Int
)

@Serializable
enum class RoundType(val roundNumber: Int, val persianTitle: String, val persianDescription: String) {
    DESCRIBE(1, "راند اول: توضیح بده!", "کلمه رو با حرف زدن توضیح بده\nمی‌تونی هر چند تا کلمه که بخوای استفاده کنی"),
    ONE_WORD(2, "راند دوم: یک کلمه!", "فقط با یک کلمه باید کلمه رو به هم‌تیمیت بفهمونی!"),
    PANTOMIME(3, "راند سوم: پانتومیم!", "بدون هیچ حرفی، فقط با ادا و اشاره کلمه رو نشون بده!")
}

@Serializable
sealed class GamePhase {
    @Serializable data object Setup : GamePhase()
    @Serializable data object TeamSetup : GamePhase()
    @Serializable data object CustomSettings : GamePhase()
    @Serializable data class WordEntry(val currentPlayerIndex: Int) : GamePhase()
    @Serializable data class RoundIntro(val round: RoundType) : GamePhase()
    @Serializable data class TurnReady(val playerName: String, val teamId: Int) : GamePhase()
    @Serializable data object TurnActive : GamePhase()
    @Serializable data class TurnEnd(val correctCount: Int, val correctWords: List<String>) : GamePhase()
    @Serializable data class RoundEnd(val round: RoundType) : GamePhase()
    @Serializable data object GameOver : GamePhase()
}

@Serializable
data class GameUiState(
    val phase: GamePhase = GamePhase.Setup,
    val playerCount: Int = 4,
    val wordsPerPlayer: Int = 5,
    val timerDurationMillis: Long = 45_000L,
    val teams: List<Team> = emptyList(),
    val allPlayers: List<Player> = emptyList(),
    val wordBank: List<Word> = emptyList(),
    val remainingWords: List<Word> = emptyList(),
    val currentRound: RoundType = RoundType.DESCRIBE,
    val currentTeamIndex: Int = 0,
    val currentPlayerSlot: Int = 1, // 1 یا 2
    val currentWord: Word? = null,
    val timeLeftMillis: Long = 45_000L,
    val turnCorrectWords: List<String> = emptyList(),
    val turnCorrectCount: Int = 0,
    val playOrderIndex: Int = 0,
    val canGoToPrevious: Boolean = false,
    val isTimerPaused: Boolean = false,
    val resetCount: Int = 0
)
