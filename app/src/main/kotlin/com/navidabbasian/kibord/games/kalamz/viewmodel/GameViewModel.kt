package com.navidabbasian.kibord.games.kalamz.viewmodel

import android.app.Application
import android.os.CountDownTimer
import androidx.lifecycle.AndroidViewModel
import com.navidabbasian.kibord.core.session.SessionStore
import com.navidabbasian.kibord.games.kalamz.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timer: CountDownTimer? = null

    // Timer pause functionality
    private var remainingTimeWhenPaused: Long = 0L

    // Words collected from all players
    private var allWords: List<Word> = emptyList()

    // Track the play order: list of (teamIndex, playerSlot)
    private var playOrder: List<Pair<Int, Int>> = emptyList()

    // Track previous words for navigation
    private var previousWords: List<Word> = emptyList()

    // باید پیش از init ساخته شود وگرنه هنگام restore تهی می‌ماند
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // اگر نشستی از اجرای پیش از مرگ پروسه مانده، بازی از همان‌جا ادامه می‌یابد
        restoreSession()
    }

    // ---- مقاوم‌سازی در برابر مرگ پروسه ----

    /** آیا این فاز ارزش ذخیره‌شدن دارد؟ فقط وسطِ بازیِ واقعی */
    private fun GameUiState.isResumable(): Boolean = phase is GamePhase.RoundIntro ||
        phase is GamePhase.TurnReady || phase is GamePhase.TurnActive ||
        phase is GamePhase.TurnEnd || phase is GamePhase.RoundEnd

    /** ذخیره‌ی وضعیت هنگام رفتن به پس‌زمینه (از ریشه صدا زده می‌شود) */
    /** پس از خروج عمدی به خانه دیگر ذخیره نمی‌کنیم تا نشستِ پاک‌شده دوباره برنگردد */
    private var leaving = false

    fun persistSession() {
        if (leaving) return
        val s = _uiState.value
        if (s.isResumable()) {
            try {
                SessionStore.save(getApplication(), KEY, json.encodeToString(GameUiState.serializer(), s))
            } catch (_: Exception) {
            }
        } else {
            SessionStore.clear(getApplication(), KEY)
        }
    }

    private fun restoreSession(): Boolean {
        val raw = SessionStore.load(getApplication(), KEY) ?: return false
        val saved = try {
            json.decodeFromString(GameUiState.serializer(), raw)
        } catch (_: Exception) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        if (!saved.isResumable()) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        // وضعیت جاری بازگردانده می‌شود، سپس وضعیت‌های گذرا بازسازی می‌شوند
        _uiState.value = saved
        allWords = saved.wordBank
        buildPlayOrder()
        previousWords = emptyList()
        // اگر وسط نوبتِ فعال بودیم، تایمر از همان زمانِ باقی‌مانده دوباره راه می‌افتد
        if (saved.phase is GamePhase.TurnActive) {
            if (saved.isTimerPaused) remainingTimeWhenPaused = saved.timeLeftMillis
            else startTimer(saved.timeLeftMillis)
        }
        return true
    }

    private fun clearSession() = SessionStore.clear(getApplication(), KEY)

    // ---- SETUP ----

    fun setPlayerCount(count: Int) {
        val teamCount = count / 2
        val players = mutableListOf<Player>()
        val teams = mutableListOf<Team>()

        var playerId = 0
        for (i in 0 until teamCount) {
            val p1 = Player(id = playerId++, name = "", teamId = i)
            val p2 = Player(id = playerId++, name = "", teamId = i)
            players.add(p1)
            players.add(p2)
            teams.add(Team(id = i, player1 = p1, player2 = p2))
        }

        _uiState.update {
            it.copy(
                playerCount = count,
                allPlayers = players,
                teams = teams,
                phase = GamePhase.TeamSetup
            )
        }
    }

    fun updatePlayerName(playerId: Int, name: String) {
        _uiState.update { state ->
            val updatedPlayers = state.allPlayers.map { p ->
                if (p.id == playerId) p.copy(name = name) else p
            }
            val updatedTeams = state.teams.map { team ->
                val p1 = updatedPlayers.find { it.id == team.player1.id } ?: team.player1
                val p2 = updatedPlayers.find { it.id == team.player2.id } ?: team.player2
                team.copy(player1 = p1, player2 = p2)
            }
            state.copy(allPlayers = updatedPlayers, teams = updatedTeams)
        }
    }

    fun updateTeamName(teamId: Int, name: String) {
        _uiState.update { state ->
            val updatedTeams = state.teams.map { team ->
                if (team.id == teamId) team.copy(name = name) else team
            }
            state.copy(teams = updatedTeams)
        }
    }

    fun confirmTeams() {
        _uiState.update { it.copy(phase = GamePhase.CustomSettings) }
    }

    fun setGameSettings(wordsPerPlayer: Int, timerDurationMillis: Long) {
        _uiState.update {
            it.copy(
                wordsPerPlayer = wordsPerPlayer,
                timerDurationMillis = timerDurationMillis,
                phase = GamePhase.WordEntry(currentPlayerIndex = 0)
            )
        }
    }

    // ---- WORD ENTRY ----

    fun submitWordsForPlayer(playerIndex: Int, words: List<String>) {
        val player = _uiState.value.allPlayers[playerIndex]
        val startId = allWords.size
        val newWords = words.mapIndexed { i, text ->
            Word(id = startId + i, text = text.trim(), submittedByPlayerId = player.id)
        }
        allWords = allWords + newWords

        val nextIndex = playerIndex + 1
        if (nextIndex < _uiState.value.allPlayers.size) {
            _uiState.update {
                it.copy(phase = GamePhase.WordEntry(currentPlayerIndex = nextIndex))
            }
        } else {
            // All players have entered words, build the play order and start round 1
            buildPlayOrder()
            _uiState.update {
                it.copy(
                    wordBank = allWords,
                    remainingWords = allWords.shuffled(),
                    currentRound = RoundType.DESCRIBE,
                    playOrderIndex = 0,
                    phase = GamePhase.RoundIntro(RoundType.DESCRIBE)
                )
            }
        }
    }

    private fun buildPlayOrder() {
        val teamCount = _uiState.value.teams.size
        val order = mutableListOf<Pair<Int, Int>>()
        // First all Player 1s, then all Player 2s
        for (slot in 1..2) {
            for (teamIdx in 0 until teamCount) {
                order.add(Pair(teamIdx, slot))
            }
        }
        playOrder = order
    }

    // ---- ROUND FLOW ----

    fun startRound() {
        val state = _uiState.value
        val (teamIdx, slot) = playOrder[state.playOrderIndex]
        val team = state.teams[teamIdx]
        val playerName = if (slot == 1) team.player1.name else team.player2.name

        _uiState.update {
            it.copy(
                phase = GamePhase.TurnReady(playerName = playerName, teamId = teamIdx),
                currentTeamIndex = teamIdx,
                currentPlayerSlot = slot
            )
        }
    }

    fun startTurn() {
        val shuffled = _uiState.value.remainingWords.shuffled()
        val timerDuration = _uiState.value.timerDurationMillis
        previousWords = emptyList() // Clear previous words stack
        _uiState.update {
            it.copy(
                phase = GamePhase.TurnActive,
                remainingWords = shuffled,
                currentWord = shuffled.firstOrNull(),
                timeLeftMillis = timerDuration,
                turnCorrectWords = emptyList(),
                turnCorrectCount = 0,
                canGoToPrevious = false,
                isTimerPaused = false
            )
        }
        startTimer(timerDuration)
    }

    fun markCorrect() {
        val state = _uiState.value
        val word = state.currentWord ?: return

        val updatedRemaining = state.remainingWords.filter { it.id != word.id }
        val updatedTurnWords = state.turnCorrectWords + word.text
        val updatedCount = state.turnCorrectCount + 1

        // Update team score
        val roundIndex = state.currentRound.roundNumber - 1
        val updatedTeams = state.teams.map { team ->
            if (team.id == state.currentTeamIndex) {
                val newScores = team.scoresPerRound.toMutableList()
                newScores[roundIndex] = newScores[roundIndex] + 1
                val newCorrectWords = team.correctWordsPerRound.toMutableList()
                newCorrectWords[roundIndex] = newCorrectWords[roundIndex] + word.text
                team.copy(scoresPerRound = newScores, correctWordsPerRound = newCorrectWords)
            } else team
        }

        if (updatedRemaining.isEmpty()) {
            // Bank is empty - end round
            timer?.cancel()
            _uiState.update {
                it.copy(
                    remainingWords = updatedRemaining,
                    currentWord = null,
                    turnCorrectWords = updatedTurnWords,
                    turnCorrectCount = updatedCount,
                    teams = updatedTeams,
                    phase = GamePhase.TurnEnd(
                        correctCount = updatedCount,
                        correctWords = updatedTurnWords
                    )
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    remainingWords = updatedRemaining,
                    currentWord = updatedRemaining.firstOrNull(),
                    turnCorrectWords = updatedTurnWords,
                    turnCorrectCount = updatedCount,
                    teams = updatedTeams
                )
            }
        }
    }

    fun nextWord() {
        val state = _uiState.value
        val currentWord = state.currentWord ?: return

        // Add current word to previous words stack
        previousWords = previousWords + currentWord

        // Move current word to end of remaining
        val withoutCurrent = state.remainingWords.filter { it.id != currentWord.id }
        val updatedRemaining = withoutCurrent + currentWord

        _uiState.update {
            it.copy(
                remainingWords = updatedRemaining,
                currentWord = updatedRemaining.firstOrNull(),
                canGoToPrevious = previousWords.isNotEmpty()
            )
        }
    }

    fun previousWord() {
        if (previousWords.isEmpty()) return
        
        val state = _uiState.value
        val currentWord = state.currentWord

        // Get the last word from previous stack
        val lastPreviousWord = previousWords.last()
        previousWords = previousWords.dropLast(1)

        // Update remaining words
        val updatedRemaining = if (currentWord != null) {
            // If there's a current word, put it at the beginning of remaining
            listOf(currentWord) + state.remainingWords.filter { it.id != currentWord.id }
        } else {
            state.remainingWords
        }

        _uiState.update {
            it.copy(
                remainingWords = updatedRemaining,
                currentWord = lastPreviousWord,
                canGoToPrevious = previousWords.isNotEmpty()
            )
        }
    }

    fun removeCorrectWord(word: String) {
        val state = _uiState.value

        // Find the Word object in the wordBank
        val wordObj = allWords.find { it.text == word } ?: return

        // Remove from turnCorrectWords
        val updatedTurnWords = state.turnCorrectWords.toMutableList().also { it.remove(word) }
        val updatedCount = updatedTurnWords.size

        // Add back to remainingWords
        val updatedRemaining = state.remainingWords + wordObj

        // Revert team score and correctWordsPerRound
        val roundIndex = state.currentRound.roundNumber - 1
        val updatedTeams = state.teams.map { team ->
            if (team.id == state.currentTeamIndex) {
                val newScores = team.scoresPerRound.toMutableList()
                newScores[roundIndex] = (newScores[roundIndex] - 1).coerceAtLeast(0)
                val newCorrectWords = team.correctWordsPerRound.toMutableList()
                val wordList = newCorrectWords[roundIndex].toMutableList()
                wordList.remove(word)
                newCorrectWords[roundIndex] = wordList
                team.copy(scoresPerRound = newScores, correctWordsPerRound = newCorrectWords)
            } else team
        }

        _uiState.update {
            it.copy(
                turnCorrectWords = updatedTurnWords,
                turnCorrectCount = updatedCount,
                remainingWords = updatedRemaining,
                teams = updatedTeams,
                phase = GamePhase.TurnEnd(
                    correctCount = updatedCount,
                    correctWords = updatedTurnWords
                )
            )
        }
    }

    private fun startTimer(durationMillis: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(durationMillis, 100L) {
            override fun onTick(millisUntilFinished: Long) {
                _uiState.update { it.copy(timeLeftMillis = millisUntilFinished) }
            }

            override fun onFinish() {
                onTimerFinished()
            }
        }.start()
    }

    fun pauseTimer() {
        val state = _uiState.value
        if (state.phase is GamePhase.TurnActive && !state.isTimerPaused) {
            timer?.cancel()
            remainingTimeWhenPaused = state.timeLeftMillis
            _uiState.update { it.copy(isTimerPaused = true) }
        }
    }

    fun resumeTimer() {
        val state = _uiState.value
        if (state.phase is GamePhase.TurnActive && state.isTimerPaused) {
            _uiState.update { it.copy(isTimerPaused = false) }
            startTimer(remainingTimeWhenPaused)
        }
    }

    private fun onTimerFinished() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                timeLeftMillis = 0L,
                phase = GamePhase.TurnEnd(
                    correctCount = state.turnCorrectCount,
                    correctWords = state.turnCorrectWords
                )
            )
        }
    }

    fun proceedAfterTurn() {
        val state = _uiState.value

        // Check if word bank is empty (round ended)
        if (state.remainingWords.isEmpty()) {
            handleRoundEnd()
            return
        }

        // Move to next player in circular play order
        val nextOrderIndex = (state.playOrderIndex + 1) % playOrder.size
        val (teamIdx, slot) = playOrder[nextOrderIndex]
        val team = state.teams[teamIdx]
        val playerName = if (slot == 1) team.player1.name else team.player2.name

        _uiState.update {
            it.copy(
                playOrderIndex = nextOrderIndex,
                currentTeamIndex = teamIdx,
                currentPlayerSlot = slot,
                phase = GamePhase.TurnReady(playerName = playerName, teamId = teamIdx)
            )
        }
    }

    private fun handleRoundEnd() {
        val state = _uiState.value
        _uiState.update {
            it.copy(phase = GamePhase.RoundEnd(state.currentRound))
        }
    }

    fun proceedToNextRound() {
        val state = _uiState.value
        val nextRound = when (state.currentRound) {
            RoundType.DESCRIBE -> RoundType.ONE_WORD
            RoundType.ONE_WORD -> RoundType.PANTOMIME
            RoundType.PANTOMIME -> null
        }

        if (nextRound != null) {
            // Move to next player for the new round
            val nextOrderIndex = (state.playOrderIndex + 1) % playOrder.size
            _uiState.update {
                it.copy(
                    currentRound = nextRound,
                    remainingWords = allWords.shuffled(),
                    playOrderIndex = nextOrderIndex,
                    phase = GamePhase.RoundIntro(nextRound)
                )
            }
        } else {
            // Game over!
            clearSession()
            _uiState.update {
                it.copy(phase = GamePhase.GameOver)
            }
        }
    }

    fun resetGame() {
        timer?.cancel()
        clearSession()
        allWords = emptyList()
        playOrder = emptyList()
        _uiState.value = GameUiState(resetCount = _uiState.value.resetCount + 1)
    }

    /** خروج به خانه: نشست پاک می‌شود تا دفعه‌ی بعد از نو شروع شود */
    fun leaveGame() {
        leaving = true
        timer?.cancel()
        clearSession()
    }

    fun navigateBack() {
        val state = _uiState.value
        when (val phase = state.phase) {
            is GamePhase.TeamSetup -> {
                // برگشت به صفحه انتخاب تعداد بازیکنان
                _uiState.update { it.copy(phase = GamePhase.Setup) }
            }
            is GamePhase.CustomSettings -> {
                // برگشت به صفحه تنظیم تیم‌ها
                _uiState.update { it.copy(phase = GamePhase.TeamSetup) }
            }
            is GamePhase.WordEntry -> {
                val playerIndex = phase.currentPlayerIndex
                if (playerIndex == 0) {
                    // بازیکن اول هنوز کلمه‌ای ثبت نکرده، برگشت به تنظیمات بازی
                    _uiState.update { it.copy(phase = GamePhase.CustomSettings) }
                } else {
                    // حذف کلمات بازیکن قبلی تا بتواند دوباره وارد کند
                    val prevPlayer = state.allPlayers[playerIndex - 1]
                    allWords = allWords.filter { it.submittedByPlayerId != prevPlayer.id }
                    _uiState.update {
                        it.copy(phase = GamePhase.WordEntry(currentPlayerIndex = playerIndex - 1))
                    }
                }
            }
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        timer?.cancel()
    }

    companion object {
        private const val KEY = "session_kalamz"
    }
}

