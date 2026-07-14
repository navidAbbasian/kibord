package com.navidabbasian.kibord.games.gandegoo.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.session.SessionStore
import com.navidabbasian.kibord.games.gandegoo.data.GandeGooRepository
import com.navidabbasian.kibord.games.gandegoo.model.GandeGooUiState
import com.navidabbasian.kibord.games.gandegoo.model.GgCell
import com.navidabbasian.kibord.games.gandegoo.model.GgMode
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
import kotlinx.serialization.json.Json

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

    /** سوال‌های نمایش‌داده‌شده برای خانه‌ی فعلی — تعویض همان‌ها را دوباره نمی‌آورد */
    private val shownThisCell = mutableSetOf<String>()
    private var lastSwapCell: GgCell? = null
    private val json = Json { ignoreUnknownKeys = true }

    init {
        repository.load()
        // اگر نشستی از اجرای پیش از مرگ پروسه مانده، بازی از همان‌جا ادامه می‌یابد
        if (!restoreSession()) {
            _uiState.update { it.copy(availableCategories = repository.allCategories) }
        }
    }

    // ---- مقاوم‌سازی در برابر مرگ پروسه ----

    /** آیا این فاز ارزش ذخیره‌شدن دارد؟ فقط وسطِ بازیِ واقعی */
    private fun GandeGooUiState.isResumable(): Boolean = phase == GgPhase.Board ||
        phase == GgPhase.Bid || phase == GgPhase.Play || phase == GgPhase.Review ||
        phase == GgPhase.Result

    /** ذخیره‌ی وضعیت هنگام رفتن به پس‌زمینه (از ریشه صدا زده می‌شود) */
    /** پس از خروج عمدی به خانه دیگر ذخیره نمی‌کنیم تا نشستِ پاک‌شده دوباره برنگردد */
    private var leaving = false

    fun persistSession() {
        if (leaving) return
        val s = _uiState.value
        if (s.isResumable()) {
            try {
                SessionStore.save(getApplication(), KEY, json.encodeToString(GandeGooUiState.serializer(), s))
            } catch (_: Exception) {
            }
        } else {
            SessionStore.clear(getApplication(), KEY)
        }
    }

    private fun restoreSession(): Boolean {
        val raw = SessionStore.load(getApplication(), KEY) ?: return false
        val saved = try {
            json.decodeFromString(GandeGooUiState.serializer(), raw)
        } catch (_: Exception) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        if (!saved.isResumable()) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        // فهرست دسته‌های بانک گذرا است و از مخزن تازه می‌آید؛ ردگیری تعویض هم بازسازی می‌شود
        val restored = saved.copy(availableCategories = repository.allCategories)
        lastSwapCell = restored.selectedCell
        shownThisCell.clear()
        restored.selectedQuestion?.text?.let(shownThisCell::add)
        _uiState.value = restored
        // اگر فاز شمارنده‌دار بود، تیک‌زن از همان زمان باقی‌مانده دوباره راه می‌افتد
        if (restored.phase == GgPhase.Play) {
            lastWholeSecond = (restored.timeLeftMillis / 1000).toInt()
            startTicker()
        } else if (restored.phase == GgPhase.Review) {
            lastWholeSecond = (restored.reviewTimeLeftMillis / 1000).toInt()
            startTicker()
        }
        return true
    }

    private fun clearSession() = SessionStore.clear(getApplication(), KEY)

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
        _uiState.update { it.copy(phase = GgPhase.Mode) }
    }

    /** انتخاب حالت بازی (کامل/سریع) و رفتن به صفحه‌ی انتخاب دسته‌ها */
    fun setMode(mode: GgMode) {
        _uiState.update { it.copy(mode = mode, phase = GgPhase.Setup) }
    }

    /** افزودن/برداشتن یک دسته در صفحه‌ی انتخاب */
    fun toggleCategory(id: String) {
        _uiState.update { state ->
            state.copy(
                chosenCategoryIds = if (id in state.chosenCategoryIds) state.chosenCategoryIds - id
                else state.chosenCategoryIds + id
            )
        }
    }

    fun startGame() {
        val state = _uiState.value
        val ids = state.chosenCategoryIds
        if (ids.size < GandeGooUiState.MIN_CATEGORIES) return
        lastSwapCell = null
        shownThisCell.clear()
        val categories = repository.buildGameCategories(ids, state.mode.tiers)
        _uiState.update {
            it.copy(
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
        // سهمیه‌ی تعویض فقط برای خانه‌ی تازه ریست می‌شود — لغو و انتخاب دوباره‌ی همان خانه سهمیه‌ی نو نمی‌دهد
        val freshCell = cell != lastSwapCell
        if (freshCell) {
            lastSwapCell = cell
            shownThisCell.clear()
            state.categories.getOrNull(cell.categoryIndex)
                ?.questions?.getOrNull(cell.questionIndex)
                ?.text?.let(shownThisCell::add)
        }
        _uiState.update {
            it.copy(
                selectedCell = cell,
                swapsLeft = if (freshCell) GandeGooUiState.SWAPS_PER_QUESTION else it.swapsLeft,
                claimingTeam = it.pickingTeam,
                claim = 5,
                phase = GgPhase.Bid,
            )
        }
    }

    /** تعویض سوال خانه‌ی انتخاب‌شده با سوالی تازه از همان سطح امتیازی */
    fun swapQuestion() {
        val state = _uiState.value
        if (state.phase != GgPhase.Bid || state.swapsLeft <= 0) return
        val cell = state.selectedCell ?: return
        val category = state.categories.getOrNull(cell.categoryIndex) ?: return
        val current = category.questions.getOrNull(cell.questionIndex) ?: return
        val replacement = repository.pickFreshQuestion(category.id, current.points, shownThisCell) ?: return
        shownThisCell += replacement.text
        _uiState.update { s ->
            s.copy(
                categories = s.categories.mapIndexed { ci, cat ->
                    if (ci != cell.categoryIndex) cat
                    else cat.copy(questions = cat.questions.mapIndexed { qi, q ->
                        if (qi == cell.questionIndex) replacement else q
                    })
                },
                swapsLeft = s.swapsLeft - 1,
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
                isVideoCheck = false,
                reviewTimeLeftMillis = GandeGooUiState.REVIEW_MILLIS,
            )
        }
        startTicker()
    }

    fun incrementCount() {
        val state = _uiState.value
        when (state.phase) {
            GgPhase.Play -> {
                if (state.isVideoCheck) return
                val newCount = state.counted + 1
                _uiState.update { it.copy(counted = newCount) }
                if (newCount >= state.claim) {
                    // به ادعا رسید — جشن، و بعد بازبینی نهایی
                    emitSound(GgSoundEvent.FULL_SUCCESS)
                    enterReview()
                }
            }
            // در بازبینی فقط عدد اصلاح می‌شود؛ رسیدن به ادعا چیزی را نمی‌بندد
            GgPhase.Review -> _uiState.update { it.copy(counted = (it.counted + 1).coerceAtMost(99)) }
            else -> return
        }
    }

    fun decrementCount() {
        val state = _uiState.value
        if (state.phase != GgPhase.Play && state.phase != GgPhase.Review) return
        if (state.phase == GgPhase.Play && state.isVideoCheck) return
        _uiState.update { it.copy(counted = (it.counted - 1).coerceAtLeast(0)) }
    }

    // ---- ویدیو چک: توقف بازی برای داوری انسانی ----

    fun startVideoCheck() {
        val state = _uiState.value
        if (state.phase != GgPhase.Play || state.isVideoCheck) return
        _uiState.update { it.copy(isVideoCheck = true) }
    }

    fun endVideoCheck() {
        if (!_uiState.value.isVideoCheck) return
        _uiState.update { it.copy(isVideoCheck = false) }
    }

    /** تایید فوری شمارش در بازبینی — بدون انتظار برای پایان وقت */
    fun confirmReview() {
        if (_uiState.value.phase != GgPhase.Review) return
        resolveAttempt()
    }

    /** ورود به ۱۵ ثانیه‌ی بازبینی نهایی شمارش */
    private fun enterReview() {
        lastWholeSecond = (GandeGooUiState.REVIEW_MILLIS / 1000).toInt()
        _uiState.update {
            it.copy(
                phase = GgPhase.Review,
                isVideoCheck = false,
                reviewTimeLeftMillis = GandeGooUiState.REVIEW_MILLIS,
            )
        }
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

                when {
                    // ویدیو چک: زمان یخ می‌زند
                    state.phase == GgPhase.Play && state.isVideoCheck -> continue

                    state.phase == GgPhase.Play -> {
                        val newLeft = (state.timeLeftMillis - delta).coerceAtLeast(0)
                        val second = (newLeft / 1000).toInt()
                        if (second != lastWholeSecond) {
                            lastWholeSecond = second
                            emitSound(if (second <= 5) GgSoundEvent.TICK_WARNING else GgSoundEvent.TICK)
                        }
                        _uiState.update { it.copy(timeLeftMillis = newLeft) }

                        if (newLeft <= 0) {
                            emitSound(GgSoundEvent.TIME_UP)
                            enterReview()
                        }
                    }

                    state.phase == GgPhase.Review -> {
                        val newLeft = (state.reviewTimeLeftMillis - delta).coerceAtLeast(0)
                        val second = (newLeft / 1000).toInt()
                        if (second != lastWholeSecond) {
                            lastWholeSecond = second
                            emitSound(GgSoundEvent.TICK)
                        }
                        _uiState.update { it.copy(reviewTimeLeftMillis = newLeft) }

                        if (newLeft <= 0) {
                            resolveAttempt()
                        }
                    }

                    else -> continue
                }
            }
        }
    }

    private fun resolveAttempt() {
        tickerJob?.cancel()
        val state = _uiState.value
        val cell = state.selectedCell ?: return
        val question = state.selectedQuestion ?: return
        val points = question.points

        // فقط سوالی که واقعاً بازی شد در سابقه‌ی «بازی‌شده» ثبت می‌شود؛
        // سوال‌های تعویض‌شده در بازی‌های بعدی دوباره می‌آیند.
        state.selectedCategory?.let { repository.markQuestionPlayed(it.id, question) }
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
            clearSession()
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
        clearSession()
        lastSwapCell = null
        shownThisCell.clear()
        val old = _uiState.value
        _uiState.update {
            GandeGooUiState(
                teamCount = old.teamCount,
                teamNames = old.teamNames,
                mode = old.mode,
                availableCategories = old.availableCategories,
                chosenCategoryIds = old.chosenCategoryIds,
                phase = GgPhase.Setup,
            )
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            when (state.phase) {
                GgPhase.TeamNames -> state.copy(phase = GgPhase.TeamCount)
                GgPhase.Mode -> state.copy(phase = GgPhase.TeamNames)
                GgPhase.Setup -> state.copy(phase = GgPhase.Mode)
                GgPhase.Bid -> state.copy(phase = GgPhase.Board, selectedCell = null)
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
        private const val KEY = "session_gandegoo"
    }
}
