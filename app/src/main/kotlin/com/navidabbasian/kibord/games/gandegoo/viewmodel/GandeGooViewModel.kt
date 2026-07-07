package com.navidabbasian.kibord.games.gandegoo.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.games.gandegoo.data.GandeGooRepository
import com.navidabbasian.kibord.games.gandegoo.model.GandeGooUiState
import com.navidabbasian.kibord.games.gandegoo.model.GgCell
import com.navidabbasian.kibord.games.gandegoo.model.GgOutcome
import com.navidabbasian.kibord.games.gandegoo.model.GgPhase
import com.navidabbasian.kibord.games.gandegoo.model.GgSoundEvent
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
 * موتور بازی گنده‌گو.
 *
 * قواعد امتیاز (P = امتیاز خانه، N = ادعا، C = تعداد گفته‌شده):
 * - C >= N: تیم بازی‌کننده P کامل می‌گیرد.
 * - C >= N/2 (مساوی نصف هم همین حالت): بدون منفی؛ در ۲ تیمه حریف P/2 و
 *   در ۳ تیمه هر یک از دو تیم دیگر P/4 می‌گیرند.
 * - C < N/2: بازی‌کننده در ۲ تیمه P/2- و در ۳ تیمه P/4- می‌گیرد؛
 *   حریف‌ها همان مقدار را مثبت می‌گیرند.
 */
class GandeGooViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GandeGooUiState())
    val uiState: StateFlow<GandeGooUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<GgSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<GgSoundEvent> = _soundEvents.asSharedFlow()

    private val repository = GandeGooRepository(application)
    private var tickerJob: Job? = null
    private var lastWholeSecond = -1

    init {
        repository.load()
    }

    private fun emitSound(event: GgSoundEvent) {
        _soundEvents.tryEmit(event)
    }

    // ---- راه‌اندازی ----

    fun setTeamCount(count: Int) {
        _uiState.update { it.copy(teamCount = count.coerceIn(2, 3), phase = GgPhase.TeamNames) }
    }

    fun updateTeamName(index: Int, name: String) {
        _uiState.update { state ->
            state.copy(teamNames = state.teamNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmTeamNames() {
        _uiState.update { it.copy(phase = GgPhase.Setup) }
    }

    fun startGame(categoryCount: Int) {
        val categories = repository.pickCategories(categoryCount)
        _uiState.update {
            it.copy(
                categoryCount = categoryCount,
                categories = categories,
                usedCells = emptySet(),
                scores = List(3) { 0 },
                pickingTeam = 0,
                phase = GgPhase.Board,
            )
        }
    }

    // ---- جدول و ثبت ادعا ----

    fun selectCell(cell: GgCell) {
        val state = _uiState.value
        if (cell in state.usedCells) return
        _uiState.update {
            it.copy(
                selectedCell = cell,
                claimingTeam = it.pickingTeam,
                claim = 5,
                phase = GgPhase.Bid,
            )
        }
    }

    fun cancelBid() {
        _uiState.update { it.copy(selectedCell = null, phase = GgPhase.Board) }
    }

    fun setClaimingTeam(team: Int) {
        _uiState.update { it.copy(claimingTeam = team.coerceIn(0, it.teamCount - 1)) }
    }

    fun setClaim(claim: Int) {
        _uiState.update { it.copy(claim = claim.coerceIn(1, 99)) }
    }

    // ---- اجرای دست ----

    fun startAttempt() {
        lastWholeSecond = (GandeGooUiState.TURN_MILLIS / 1000).toInt()
        _uiState.update {
            it.copy(
                phase = GgPhase.Play,
                timeLeftMillis = GandeGooUiState.TURN_MILLIS,
                counted = 0,
            )
        }
        startTicker()
    }

    fun incrementCount() {
        val state = _uiState.value
        if (state.phase != GgPhase.Play) return
        val newCount = state.counted + 1
        _uiState.update { it.copy(counted = newCount) }
        if (newCount >= state.claim) {
            // به ادعا رسید — موفقیت کامل، بدون انتظار برای پایان تایمر
            emitSound(GgSoundEvent.FULL_SUCCESS)
            resolveAttempt()
        }
    }

    fun decrementCount() {
        val state = _uiState.value
        if (state.phase != GgPhase.Play) return
        _uiState.update { it.copy(counted = (it.counted - 1).coerceAtLeast(0)) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val delta = now - last
                last = now
                val state = _uiState.value
                if (state.phase != GgPhase.Play) continue

                val newLeft = (state.timeLeftMillis - delta).coerceAtLeast(0)
                val second = (newLeft / 1000).toInt()
                if (second != lastWholeSecond) {
                    lastWholeSecond = second
                    emitSound(if (second <= 5) GgSoundEvent.TICK_WARNING else GgSoundEvent.TICK)
                }
                _uiState.update { it.copy(timeLeftMillis = newLeft) }

                if (newLeft <= 0) {
                    emitSound(GgSoundEvent.TIME_UP)
                    resolveAttempt()
                }
            }
        }
    }

    private fun resolveAttempt() {
        tickerJob?.cancel()
        val state = _uiState.value
        val cell = state.selectedCell ?: return
        val points = state.selectedQuestion?.points ?: return
        val n = state.teamCount
        val claim = state.claim
        val counted = state.counted

        val deltas = MutableList(n) { 0 }
        val kind = when {
            counted >= claim -> {
                deltas[state.claimingTeam] += points
                GgOutcome.Kind.FULL
            }

            counted * 2 >= claim -> {
                // نصف یا بیشتر: بدون منفی، امتیاز به حریف(ها)
                val share = if (n == 2) points / 2 else points / 4
                for (t in 0 until n) if (t != state.claimingTeam) deltas[t] += share
                GgOutcome.Kind.PARTIAL
            }

            else -> {
                // کمتر از نصف: منفی برای بازی‌کننده، مثبت برای بقیه
                val share = if (n == 2) points / 2 else points / 4
                deltas[state.claimingTeam] -= share
                for (t in 0 until n) if (t != state.claimingTeam) deltas[t] += share
                GgOutcome.Kind.FAIL
            }
        }

        val outcome = GgOutcome(
            cell = cell,
            claimingTeam = state.claimingTeam,
            claim = claim,
            counted = counted,
            points = points,
            deltas = deltas,
            kind = kind,
        )

        _uiState.update {
            it.copy(
                scores = it.scores.mapIndexed { i, s -> s + (deltas.getOrNull(i) ?: 0) },
                usedCells = it.usedCells + cell,
                lastOutcome = outcome,
                phase = GgPhase.Result,
            )
        }
    }

    /** ادامه از صفحه‌ی نتیجه: نوبت انتخاب می‌چرخد؛ اگر جدول تمام شده برنده اعلام می‌شود */
    fun proceedAfterResult() {
        val state = _uiState.value
        if (state.allCellsPlayed) {
            emitSound(GgSoundEvent.GAME_OVER)
            _uiState.update { it.copy(phase = GgPhase.Winner, selectedCell = null) }
        } else {
            _uiState.update {
                it.copy(
                    phase = GgPhase.Board,
                    selectedCell = null,
                    pickingTeam = (it.pickingTeam + 1) % it.teamCount,
                )
            }
        }
    }

    /** فهرست تیم‌های دارای بیشترین امتیاز (برای اعلام برنده یا تساوی) */
    fun winners(): List<Int> {
        val state = _uiState.value
        val scores = state.scores.take(state.teamCount)
        val max = scores.maxOrNull() ?: return emptyList()
        return scores.withIndex().filter { it.value == max }.map { it.index }
    }

    fun playAgain() {
        tickerJob?.cancel()
        val old = _uiState.value
        _uiState.update {
            GandeGooUiState(
                teamCount = old.teamCount,
                teamNames = old.teamNames,
                categoryCount = old.categoryCount,
                phase = GgPhase.Setup,
            )
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            when (state.phase) {
                GgPhase.TeamNames -> state.copy(phase = GgPhase.TeamCount)
                GgPhase.Setup -> state.copy(phase = GgPhase.TeamNames)
                GgPhase.Bid -> state.copy(phase = GgPhase.Board, selectedCell = null)
                else -> state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}
