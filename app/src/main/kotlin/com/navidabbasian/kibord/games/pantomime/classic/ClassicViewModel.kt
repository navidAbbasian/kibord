package com.navidabbasian.kibord.games.pantomime.classic

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

@Serializable
sealed class ClassicPhase {
    @Serializable data object TeamNames : ClassicPhase()
    @Serializable data object Rounds : ClassicPhase()
    @Serializable data object Pick : ClassicPhase()
    @Serializable data object Reveal : ClassicPhase()
    @Serializable data object Perform : ClassicPhase()
    @Serializable data object Result : ClassicPhase()
    /** شکست در موضوع طلایی: حذف فوری و پیروزی حریف */
    @Serializable data class GoldenLoss(val loserTeam: Int) : ClassicPhase()
    @Serializable data object Winner : ClassicPhase()
}

@Serializable
data class ClassicUiState(
    val phase: ClassicPhase = ClassicPhase.TeamNames,
    val teamNames: List<String> = List(2) { "" },
    val scores: List<Int> = List(2) { 0 },
    val totalRounds: Int = 3,
    val currentRound: Int = 1,
    val performingTeam: Int = 0,
    val goldenUsed: List<Boolean> = List(2) { false },
    val categories: List<PCategory> = emptyList(),
    val attempt: PantoAttempt? = null,
    val timeLeftMillis: Long = 0,
    val lastResult: PantoResult? = null,
) {
    fun teamDisplayName(index: Int): String =
        teamNames.getOrNull(index)?.ifBlank { "تیم ${(index + 1).toPersianDigits()}" }
            ?: "تیم ${(index + 1).toPersianDigits()}"
}

/** موتور پانتومیم کلاسیک: ۲ تیم، راندهای انتخابی، موضوع طلایی با ریسک حذف */
class ClassicViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ClassicUiState())
    val uiState: StateFlow<ClassicUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<PantoSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<PantoSoundEvent> = _soundEvents.asSharedFlow()

    private val repository = PantomimeRepository(application)
    private var tickerJob: Job? = null
    private var lastWholeSecond = -1
    private val json = Json { ignoreUnknownKeys = true }

    init {
        repository.load()
        // اگر نشستی از اجرای پیش از مرگ پروسه مانده، بازی از همان‌جا ادامه می‌یابد
        if (!restoreSession()) {
            _uiState.update { it.copy(categories = repository.categories) }
        }
    }

    // ---- مقاوم‌سازی در برابر مرگ پروسه ----

    /** آیا این فاز ارزش ذخیره‌شدن دارد؟ فقط وسطِ بازیِ واقعی */
    private fun ClassicUiState.isResumable(): Boolean = phase == ClassicPhase.Pick ||
        phase == ClassicPhase.Reveal || phase == ClassicPhase.Perform || phase == ClassicPhase.Result

    /** ذخیره‌ی وضعیت هنگام رفتن به پس‌زمینه (از ریشه صدا زده می‌شود) */
    /** پس از خروج عمدی به خانه دیگر ذخیره نمی‌کنیم تا نشستِ پاک‌شده دوباره برنگردد */
    private var leaving = false

    fun persistSession() {
        if (leaving) return
        val s = _uiState.value
        if (s.isResumable()) {
            try {
                SessionStore.save(getApplication(), KEY, json.encodeToString(ClassicUiState.serializer(), s))
            } catch (_: Exception) {
            }
        } else {
            SessionStore.clear(getApplication(), KEY)
        }
    }

    private fun restoreSession(): Boolean {
        val raw = SessionStore.load(getApplication(), KEY) ?: return false
        val saved = try {
            json.decodeFromString(ClassicUiState.serializer(), raw)
        } catch (_: Exception) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        if (!saved.isResumable()) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        _uiState.value = saved
        // اجرا اگر وسطِ زمان‌گیری بوده، تیکر از نو راه می‌افتد
        if (saved.phase == ClassicPhase.Perform) {
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

    fun updateTeamName(index: Int, name: String) {
        _uiState.update { state ->
            state.copy(teamNames = state.teamNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmTeamNames() {
        _uiState.update { it.copy(phase = ClassicPhase.Rounds) }
    }

    fun setRounds(rounds: Int) {
        _uiState.update {
            it.copy(
                totalRounds = rounds.coerceIn(1, 10),
                currentRound = 1,
                performingTeam = 0,
                phase = ClassicPhase.Pick,
            )
        }
    }

    // ---- انتخاب کلمه ----

    fun hasWords(category: PCategory, points: Int): Boolean = repository.hasWords(category, points)

    fun hasGolden(): Boolean = repository.hasGoldenWord()

    fun pickWord(category: PCategory, points: Int) {
        val word = repository.drawWord(category, points) ?: return
        _uiState.update {
            it.copy(
                attempt = PantoAttempt(
                    word = word,
                    points = points,
                    isGolden = false,
                    categoryName = category.name,
                    categoryEmoji = category.emoji,
                    durationMillis = PantoRules.durationFor(points, isGolden = false),
                ),
                phase = ClassicPhase.Reveal,
            )
        }
    }

    fun pickGolden() {
        val state = _uiState.value
        if (state.goldenUsed[state.performingTeam]) return
        val word = repository.drawGoldenWord() ?: return
        _uiState.update {
            it.copy(
                goldenUsed = it.goldenUsed.mapIndexed { i, used -> used || i == it.performingTeam },
                attempt = PantoAttempt(
                    word = word,
                    points = PantoRules.GOLDEN_POINTS,
                    isGolden = true,
                    categoryName = "موضوع طلایی",
                    categoryEmoji = "⭐",
                    durationMillis = PantoRules.durationFor(0, isGolden = true),
                ),
                phase = ClassicPhase.Reveal,
            )
        }
    }

    // ---- اجرا ----

    fun startPerform() {
        val attempt = _uiState.value.attempt ?: return
        lastWholeSecond = (attempt.durationMillis / 1000).toInt()
        _uiState.update { it.copy(phase = ClassicPhase.Perform, timeLeftMillis = attempt.durationMillis) }
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
                if (state.phase != ClassicPhase.Perform) continue

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
        if (state.phase != ClassicPhase.Perform) return

        if (!success && attempt.isGolden) {
            // ریسک طلایی جواب نداد — حذف فوری
            emitSound(PantoSoundEvent.GOLDEN_FAIL)
            clearSession()
            _uiState.update { it.copy(phase = ClassicPhase.GoldenLoss(state.performingTeam)) }
            return
        }

        val base = if (attempt.isGolden) PantoRules.GOLDEN_POINTS else attempt.points
        val bonus = if (success) PantoRules.bonus(state.timeLeftMillis) else 0
        val earned = if (success) base + bonus else 0
        if (success) emitSound(PantoSoundEvent.SUCCESS)

        _uiState.update {
            it.copy(
                scores = it.scores.mapIndexed { i, s -> if (i == it.performingTeam) s + earned else s },
                lastResult = PantoResult(
                    attempt = attempt,
                    performingTeam = it.performingTeam,
                    success = success,
                    remainingMillis = it.timeLeftMillis,
                    bonus = bonus,
                    earned = earned,
                ),
                phase = ClassicPhase.Result,
            )
        }
    }

    fun proceedAfterResult() {
        val state = _uiState.value
        if (state.performingTeam == 0) {
            _uiState.update { it.copy(performingTeam = 1, attempt = null, phase = ClassicPhase.Pick) }
        } else if (state.currentRound >= state.totalRounds) {
            emitSound(PantoSoundEvent.GAME_OVER)
            clearSession()
            _uiState.update { it.copy(attempt = null, phase = ClassicPhase.Winner) }
        } else {
            _uiState.update {
                it.copy(
                    currentRound = it.currentRound + 1,
                    performingTeam = 0,
                    attempt = null,
                    phase = ClassicPhase.Pick,
                )
            }
        }
    }

    /** تیم(های) دارای بیشترین امتیاز */
    fun winners(): List<Int> {
        val scores = _uiState.value.scores
        val max = scores.maxOrNull() ?: return emptyList()
        return scores.withIndex().filter { it.value == max }.map { it.index }
    }

    fun playAgain() {
        tickerJob?.cancel()
        clearSession()
        repository.resetUsed()
        val old = _uiState.value
        _uiState.update {
            ClassicUiState(
                teamNames = old.teamNames,
                totalRounds = old.totalRounds,
                categories = repository.categories,
                phase = ClassicPhase.Rounds,
            )
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            when (state.phase) {
                ClassicPhase.Rounds -> state.copy(phase = ClassicPhase.TeamNames)
                ClassicPhase.Reveal -> state.copy(phase = ClassicPhase.Pick, attempt = null)
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
        private const val KEY = "session_pantomime_classic"
    }
}
