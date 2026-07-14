package com.navidabbasian.kibord.games.pantomime.rival

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.session.SessionStore
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.pantomime.data.PantomimeRepository
import com.navidabbasian.kibord.games.pantomime.model.PCategory
import com.navidabbasian.kibord.games.pantomime.model.PantoAttempt
import com.navidabbasian.kibord.games.pantomime.model.PantoResult
import com.navidabbasian.kibord.games.pantomime.model.PantoRules
import com.navidabbasian.kibord.games.pantomime.model.PantoSoundEvent
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** آدرس خانه‌ی جدول رقابتی: شماره‌ی کتگوری + رده‌ی امتیازی (۲/۴/۶) */
@Serializable
data class RivalCell(
    val categoryIndex: Int,
    val points: Int,
)

@Serializable
sealed class RivalPhase {
    @Serializable data object TeamCount : RivalPhase()
    @Serializable data object TeamNames : RivalPhase()
    @Serializable data object Board : RivalPhase()
    @Serializable data object Reveal : RivalPhase()
    @Serializable data object Perform : RivalPhase()
    @Serializable data object Result : RivalPhase()
    @Serializable data object Winner : RivalPhase()
}

@Serializable
data class RivalUiState(
    val phase: RivalPhase = RivalPhase.TeamCount,
    val teamCount: Int = 2,
    val teamNames: List<String> = List(3) { "" },
    val scores: List<Int> = List(3) { 0 },
    val categories: List<PCategory> = emptyList(),
    val usedCells: Set<RivalCell> = emptySet(),
    val pickingTeam: Int = 0,
    val attempt: PantoAttempt? = null,
    val timeLeftMillis: Long = 0,
    val lastResult: PantoResult? = null,
) {
    val totalCells: Int get() = categories.size * 3
    val allCellsPlayed: Boolean get() = categories.isNotEmpty() && usedCells.size >= totalCells

    fun teamDisplayName(index: Int): String =
        teamNames.getOrNull(index)?.ifBlank { "تیم ${(index + 1).toPersianDigits()}" }
            ?: "تیم ${(index + 1).toPersianDigits()}"
}

/**
 * موتور پانتومیم رقابتی: جدول ۶ کتگوری × (۲/۴/۶) مثل گنده‌گو؛
 * زمان‌بندی و امتیازدهی مثل پانتومیم کلاسیک (بدون موضوع طلایی).
 */
class RivalViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RivalUiState())
    val uiState: StateFlow<RivalUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<PantoSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<PantoSoundEvent> = _soundEvents.asSharedFlow()

    private val repository = PantomimeRepository(application)
    private var tickerJob: Job? = null
    private var lastWholeSecond = -1
    private var currentCell: RivalCell? = null
    private val json = Json { ignoreUnknownKeys = true }

    init {
        repository.load()
        // اگر نشستی از اجرای پیش از مرگ پروسه مانده، بازی از همان‌جا ادامه می‌یابد
        restoreSession()
    }

    // ---- مقاوم‌سازی در برابر مرگ پروسه ----

    /** آیا این فاز ارزش ذخیره‌شدن دارد؟ فقط وسطِ بازیِ واقعی */
    private fun RivalUiState.isResumable(): Boolean = phase == RivalPhase.Board ||
        phase == RivalPhase.Reveal || phase == RivalPhase.Perform || phase == RivalPhase.Result

    /** ذخیره‌ی وضعیت هنگام رفتن به پس‌زمینه (از ریشه صدا زده می‌شود) */
    /** پس از خروج عمدی به خانه دیگر ذخیره نمی‌کنیم تا نشستِ پاک‌شده دوباره برنگردد */
    private var leaving = false

    fun persistSession() {
        if (leaving) return
        val s = _uiState.value
        if (s.isResumable()) {
            try {
                SessionStore.save(getApplication(), KEY, json.encodeToString(RivalUiState.serializer(), s))
            } catch (_: Exception) {
            }
        } else {
            SessionStore.clear(getApplication(), KEY)
        }
    }

    private fun restoreSession(): Boolean {
        val raw = SessionStore.load(getApplication(), KEY) ?: return false
        val saved = try {
            json.decodeFromString(RivalUiState.serializer(), raw)
        } catch (_: Exception) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        if (!saved.isResumable()) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        // خانه‌ی جاری گذرا است؛ از روی اجرای ذخیره‌شده بازسازی می‌شود تا در پایان نوبت «سوخته» شود
        saved.attempt?.let { a ->
            val ci = saved.categories.indexOfFirst { it.name == a.categoryName }
            if (ci >= 0) currentCell = RivalCell(ci, a.points)
        }
        _uiState.value = saved
        // اجرا اگر وسطِ زمان‌گیری بوده، تیکر از نو راه می‌افتد
        if (saved.phase == RivalPhase.Perform) {
            lastWholeSecond = (saved.timeLeftMillis / 1000).toInt()
            startTicker()
        }
        return true
    }

    private fun clearSession() = SessionStore.clear(getApplication(), KEY)

    private fun emitSound(event: PantoSoundEvent) {
        _soundEvents.tryEmit(event)
    }

    // ---- راه‌اندازی ----

    fun setTeamCount(count: Int) {
        _uiState.update { it.copy(teamCount = count.coerceIn(2, 3), phase = RivalPhase.TeamNames) }
    }

    fun updateTeamName(index: Int, name: String) {
        _uiState.update { state ->
            state.copy(teamNames = state.teamNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmTeamNames() {
        repository.resetUsed()
        _uiState.update {
            it.copy(
                categories = repository.pickCategories(6),
                usedCells = emptySet(),
                scores = List(3) { 0 },
                pickingTeam = 0,
                phase = RivalPhase.Board,
            )
        }
    }

    // ---- جدول ----

    fun selectCell(cell: RivalCell) {
        val state = _uiState.value
        if (cell in state.usedCells) return
        val category = state.categories.getOrNull(cell.categoryIndex) ?: return
        val word = repository.drawWord(category, cell.points)
        if (word == null) {
            // بانک این رده تمام شده — خانه سوخته حساب می‌شود
            _uiState.update { it.copy(usedCells = it.usedCells + cell) }
            return
        }
        currentCell = cell
        _uiState.update {
            it.copy(
                attempt = PantoAttempt(
                    word = word,
                    points = cell.points,
                    isGolden = false,
                    categoryName = category.name,
                    categoryEmoji = category.emoji,
                    durationMillis = PantoRules.durationFor(cell.points, isGolden = false),
                ),
                phase = RivalPhase.Reveal,
            )
        }
    }

    // ---- اجرا ----

    fun startPerform() {
        val attempt = _uiState.value.attempt ?: return
        lastWholeSecond = (attempt.durationMillis / 1000).toInt()
        _uiState.update { it.copy(phase = RivalPhase.Perform, timeLeftMillis = attempt.durationMillis) }
        startTicker()
    }

    fun markSuccess() = resolve(success = true)

    fun markFail() = resolve(success = false)

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
                if (state.phase != RivalPhase.Perform) continue

                val newLeft = (state.timeLeftMillis - delta).coerceAtLeast(0)
                val second = (newLeft / 1000).toInt()
                if (second != lastWholeSecond) {
                    lastWholeSecond = second
                    emitSound(if (second <= 10) PantoSoundEvent.TICK_WARNING else PantoSoundEvent.TICK)
                }
                _uiState.update { it.copy(timeLeftMillis = newLeft) }

                if (newLeft <= 0) {
                    emitSound(PantoSoundEvent.TIME_UP)
                    resolve(success = false)
                }
            }
        }
    }

    private fun resolve(success: Boolean) {
        tickerJob?.cancel()
        val state = _uiState.value
        val attempt = state.attempt ?: return
        if (state.phase != RivalPhase.Perform) return

        val bonus = if (success) PantoRules.bonus(state.timeLeftMillis) else 0
        val earned = if (success) attempt.points + bonus else 0
        if (success) emitSound(PantoSoundEvent.SUCCESS)

        _uiState.update {
            it.copy(
                scores = it.scores.mapIndexed { i, s -> if (i == it.pickingTeam) s + earned else s },
                usedCells = currentCell?.let { cell -> it.usedCells + cell } ?: it.usedCells,
                lastResult = PantoResult(
                    attempt = attempt,
                    performingTeam = it.pickingTeam,
                    success = success,
                    remainingMillis = it.timeLeftMillis,
                    bonus = bonus,
                    earned = earned,
                ),
                phase = RivalPhase.Result,
            )
        }
    }

    fun proceedAfterResult() {
        val state = _uiState.value
        if (state.allCellsPlayed) {
            emitSound(PantoSoundEvent.GAME_OVER)
            clearSession()
            _uiState.update { it.copy(attempt = null, phase = RivalPhase.Winner) }
        } else {
            _uiState.update {
                it.copy(
                    attempt = null,
                    pickingTeam = (it.pickingTeam + 1) % it.teamCount,
                    phase = RivalPhase.Board,
                )
            }
        }
    }

    fun winners(): List<Int> {
        val state = _uiState.value
        val scores = state.scores.take(state.teamCount)
        val max = scores.maxOrNull() ?: return emptyList()
        return scores.withIndex().filter { it.value == max }.map { it.index }
    }

    fun playAgain() {
        tickerJob?.cancel()
        clearSession()
        repository.resetUsed()
        val old = _uiState.value
        _uiState.update {
            RivalUiState(
                teamCount = old.teamCount,
                teamNames = old.teamNames,
                categories = repository.pickCategories(6),
                phase = RivalPhase.Board,
            )
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            when (state.phase) {
                RivalPhase.TeamNames -> state.copy(phase = RivalPhase.TeamCount)
                else -> state
            }
        }
    }

    /** خروج به خانه: نشست پاک می‌شود تا دفعه‌ی بعد از نو شروع شود */
    fun leaveGame() {
        leaving = true
        tickerJob?.cancel()
        clearSession()
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }

    companion object {
        private const val KEY = "session_pantomime_rival"
    }
}
