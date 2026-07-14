package com.navidabbasian.kibord.games.taboo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.games.taboo.data.TabooRepository
import com.navidabbasian.kibord.games.taboo.model.TabooCard
import com.navidabbasian.kibord.games.taboo.model.TabooPhase
import com.navidabbasian.kibord.games.taboo.model.TabooSoundEvent
import com.navidabbasian.kibord.games.taboo.model.TabooUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * موتور تابو: کلمه را بگو بدون پنج کلمه‌ی ممنوعه.
 * درست ۱+، تخلف ۱−، رد بدون امتیاز (کارت ته دسته می‌رود).
 */
class TabooViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TabooUiState())
    val uiState: StateFlow<TabooUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<TabooSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<TabooSoundEvent> = _soundEvents.asSharedFlow()

    private val repository = TabooRepository(application)
    private var tickerJob: Job? = null
    private var deck: ArrayDeque<TabooCard> = ArrayDeque()

    init {
        repository.load()
        // آخرین تنظیمات همین بازی از حافظه برمی‌گردد تا شروع یک‌ضربه‌ای باشد
        val count = GamePrefs.getInt(application, "taboo_teams", 2).coerceIn(2, 3)
        val names = GamePrefs.getNames(application, "taboo_names")
        _uiState.update {
            it.copy(
                teamCount = count,
                teamNames = List(count) { i -> names.getOrElse(i) { "" } },
                scores = List(count) { 0 },
                turnSeconds = GamePrefs.getInt(application, "taboo_seconds", 60),
                totalRounds = GamePrefs.getInt(application, "taboo_rounds", 3),
            )
        }
    }

    private fun emit(e: TabooSoundEvent) = _soundEvents.tryEmit(e)

    // ---- راه‌اندازی ----

    fun updateTeamName(index: Int, name: String) {
        _uiState.update { s ->
            s.copy(teamNames = s.teamNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    /** انتخاب دو یا سه تیم — اسم‌های قبلی حفظ می‌شوند و می‌رویم سراغ اسم‌ها */
    fun setTeamCount(count: Int) {
        val c = count.coerceIn(2, 3)
        _uiState.update { s ->
            s.copy(
                teamCount = c,
                teamNames = List(c) { i -> s.teamNames.getOrElse(i) { "" } },
                scores = List(c) { 0 },
                phase = TabooPhase.TeamNames,
            )
        }
    }

    fun confirmTeamNames() = _uiState.update { it.copy(phase = TabooPhase.Settings) }

    fun setTurnSeconds(seconds: Int) = _uiState.update { it.copy(turnSeconds = seconds) }

    fun setTotalRounds(rounds: Int) =
        _uiState.update { it.copy(totalRounds = rounds.coerceIn(1, 10)) }

    fun startGame() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setInt(app, "taboo_teams", s.teamCount)
        GamePrefs.setNames(app, "taboo_names", s.teamNames)
        GamePrefs.setInt(app, "taboo_seconds", s.turnSeconds)
        GamePrefs.setInt(app, "taboo_rounds", s.totalRounds)
        deck = ArrayDeque(repository.prepareDeck())
        _uiState.update {
            it.copy(
                phase = TabooPhase.TurnReady(0),
                scores = List(it.teamCount) { 0 },
                roundIndex = 1,
                currentTeam = 0,
            )
        }
    }

    // ---- نوبت ----

    fun startTurn() {
        val s = _uiState.value
        val team = (s.phase as? TabooPhase.TurnReady)?.team ?: return
        _uiState.update {
            it.copy(
                phase = TabooPhase.Turn,
                currentTeam = team,
                secondsLeft = it.turnSeconds,
                turnCorrect = 0,
                turnFoul = 0,
                turnBonus = 0,
                currentCard = drawCard(),
            )
        }
        startTicker()
    }

    private fun drawCard(): TabooCard? {
        if (deck.isEmpty()) deck = ArrayDeque(repository.prepareDeck())
        return deck.removeFirstOrNull()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value
                if (s.phase != TabooPhase.Turn) break
                val left = s.secondsLeft - 1
                emit(if (left <= 5) TabooSoundEvent.TICK_WARNING else TabooSoundEvent.TICK)
                if (left <= 0) {
                    emit(TabooSoundEvent.TIME_UP)
                    endTurn()
                    break
                }
                _uiState.update { it.copy(secondsLeft = left) }
            }
        }
    }

    /** درست گفت: ۱+ و کارت بعدی */
    fun markCorrect() {
        val s = _uiState.value
        if (s.phase != TabooPhase.Turn) return
        emit(TabooSoundEvent.CORRECT)
        s.currentCard?.let(repository::markPlayed)
        _uiState.update {
            it.copy(
                scores = it.scores.mapIndexed { i, v -> if (i == it.currentTeam) v + 1 else v },
                turnCorrect = it.turnCorrect + 1,
                currentCard = drawCard(),
            )
        }
    }

    /** تخلف (کلمه‌ی ممنوعه گفته شد): ۱− و کارت بعدی */
    fun markFoul() {
        val s = _uiState.value
        if (s.phase != TabooPhase.Turn) return
        emit(TabooSoundEvent.FOUL)
        s.currentCard?.let(repository::markPlayed)
        _uiState.update {
            it.copy(
                scores = it.scores.mapIndexed { i, v -> if (i == it.currentTeam) v - 1 else v },
                turnFoul = it.turnFoul + 1,
                currentCard = drawCard(),
            )
        }
    }

    /** رد: بدون امتیاز، کارت ته دسته برمی‌گردد */
    fun skipCard() {
        val s = _uiState.value
        if (s.phase != TabooPhase.Turn) return
        emit(TabooSoundEvent.SKIP)
        s.currentCard?.let(deck::addLast)
        _uiState.update { it.copy(currentCard = drawCard()) }
    }

    private fun endTurn() {
        tickerJob?.cancel()
        val s = _uiState.value
        _uiState.update {
            it.copy(phase = TabooPhase.TurnEnd(s.currentTeam, s.turnCorrect, s.turnFoul))
        }
    }

    /** بررسی امتیاز نوبت: داور جمع می‌تواند امتیاز تیم را کم و زیاد کند */
    fun adjustTurnScore(delta: Int) {
        val s = _uiState.value
        val team = (s.phase as? TabooPhase.TurnEnd)?.team ?: return
        _uiState.update {
            it.copy(
                scores = it.scores.mapIndexed { i, v -> if (i == team) v + delta else v },
                turnBonus = it.turnBonus + delta,
            )
        }
    }

    /** بعد از جمع‌بندی نوبت: تیم بعد یا راند بعد یا برنده */
    fun proceedAfterTurn() {
        val s = _uiState.value
        if (s.currentTeam < s.teamCount - 1) {
            _uiState.update { it.copy(phase = TabooPhase.TurnReady(s.currentTeam + 1)) }
        } else if (s.roundIndex >= s.totalRounds) {
            emit(TabooSoundEvent.GAME_OVER)
            _uiState.update { it.copy(phase = TabooPhase.Winner) }
        } else {
            _uiState.update {
                it.copy(roundIndex = it.roundIndex + 1, phase = TabooPhase.TurnReady(0))
            }
        }
    }

    /** فهرست تیم‌های دارای بیشترین امتیاز */
    fun winners(): List<Int> {
        val scores = _uiState.value.scores
        val max = scores.maxOrNull() ?: return emptyList()
        return scores.withIndex().filter { it.value == max }.map { it.index }
    }

    fun playAgain() {
        tickerJob?.cancel()
        val old = _uiState.value
        _uiState.value = TabooUiState(
            teamCount = old.teamCount,
            teamNames = old.teamNames,
            scores = List(old.teamCount) { 0 },
            turnSeconds = old.turnSeconds,
            totalRounds = old.totalRounds,
            phase = TabooPhase.Settings,
        )
    }

    fun navigateBack() {
        _uiState.update { s ->
            when (s.phase) {
                TabooPhase.TeamNames -> s.copy(phase = TabooPhase.TeamCount)
                TabooPhase.Settings -> s.copy(phase = TabooPhase.TeamNames)
                else -> s
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}
