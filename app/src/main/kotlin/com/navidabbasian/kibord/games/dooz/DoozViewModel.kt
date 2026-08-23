package com.navidabbasian.kibord.games.dooz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.games.dooz.engine.DoozBoard
import com.navidabbasian.kibord.games.dooz.engine.DoozBot
import com.navidabbasian.kibord.games.dooz.engine.DoozDifficulty
import com.navidabbasian.kibord.games.dooz.engine.DoozMark
import com.navidabbasian.kibord.games.dooz.engine.DoozWin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** دو نفره روی یک گوشی، یا با ربات */
enum class DoozMode { PVP, BOT }

/** مرحله‌ی صفحه */
enum class DoozPhase { Setup, Play, SeriesOver }

/** صداهایی که ریشه‌ی کامپوزبل پخش می‌کند */
enum class DoozSoundEvent { TAP, ROUND_WIN, DRAW, SERIES_WIN }

/** نتیجه‌ی دستِ تمام‌شده: برنده (null = مساوی) و خط برنده */
data class DoozRoundResult(val winner: DoozMark?, val line: List<Int>?)

data class DoozUiState(
    val phase: DoozPhase = DoozPhase.Setup,
    val mode: DoozMode = DoozMode.PVP,
    val difficulty: DoozDifficulty = DoozDifficulty.MEDIUM,
    val targetWins: Int = 3,
    /** اسم بازیکن‌ها: [0] = ❌ ، [1] = ⭕ */
    val names: List<String> = listOf("", ""),
    val board: DoozBoard = DoozBoard(),
    val turn: DoozMark = DoozMark.X,
    /** چه کسی این دست را شروع کرده — برای تعیین شروع‌کننده‌ی دست بعد */
    val roundStarter: DoozMark = DoozMark.X,
    val roundNo: Int = 1,
    val xWins: Int = 0,
    val oWins: Int = 0,
    val draws: Int = 0,
    val roundResult: DoozRoundResult? = null,
    /** پس از مکث کوتاه، کارت نتیجه‌ی دست نشان داده می‌شود */
    val resultShown: Boolean = false,
    val botThinking: Boolean = false,
    /** آخرین خانه‌ی پرشده (برای انیمیشن) */
    val lastMove: Int? = null,
    val seriesWinner: DoozMark? = null,
) {
    val isBotGame: Boolean get() = mode == DoozMode.BOT

    fun isBot(mark: DoozMark): Boolean = isBotGame && mark == DoozMark.O

    fun displayName(mark: DoozMark): String = when (mark) {
        DoozMark.X -> names[0].ifBlank { "بازیکن ۱" }
        DoozMark.O -> if (isBotGame) "ربات" else names[1].ifBlank { "بازیکن ۲" }
    }

    fun wins(mark: DoozMark): Int = if (mark == DoozMark.X) xWins else oWins

    /** آیا الان لمس انسان روی صفحه مجاز است؟ */
    val humanCanMove: Boolean
        get() = phase == DoozPhase.Play && roundResult == null && !botThinking && !isBot(turn)
}

/**
 * دوز — سه‌تایی در یک خط. ربات سه سطح دارد و سِری «تا چند برد» ادامه دارد.
 * همه‌ی وضعیت در همین ویومدل می‌ماند تا با چرخش صفحه چیزی گم نشود.
 */
class DoozViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DoozUiState())
    val uiState: StateFlow<DoozUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<DoozSoundEvent>(extraBufferCapacity = 8)
    val soundEvents: SharedFlow<DoozSoundEvent> = _soundEvents.asSharedFlow()

    private val bot = DoozBot(Random.Default)
    private var botJob: Job? = null
    private var resultJob: Job? = null

    init {
        val app = application
        val modeName = GamePrefs.getString(app, KEY_MODE)
        val diffName = GamePrefs.getString(app, KEY_DIFFICULTY)
        _uiState.update {
            it.copy(
                mode = DoozMode.entries.firstOrNull { m -> m.name == modeName } ?: DoozMode.PVP,
                difficulty = DoozDifficulty.entries.firstOrNull { d -> d.name == diffName } ?: DoozDifficulty.MEDIUM,
                targetWins = GamePrefs.getInt(app, KEY_TARGET, 3).takeIf { t -> t in TARGETS } ?: 3,
                names = listOf(
                    GamePrefs.getString(app, KEY_NAME_X, "") ?: "",
                    GamePrefs.getString(app, KEY_NAME_O, "") ?: "",
                ),
            )
        }
    }

    private fun emit(event: DoozSoundEvent) {
        viewModelScope.launch { _soundEvents.emit(event) }
    }

    // ---------------- تنظیمات ----------------

    fun setMode(mode: DoozMode) {
        GamePrefs.setString(getApplication(), KEY_MODE, mode.name)
        _uiState.update { it.copy(mode = mode) }
    }

    fun setDifficulty(difficulty: DoozDifficulty) {
        GamePrefs.setString(getApplication(), KEY_DIFFICULTY, difficulty.name)
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    fun setTargetWins(target: Int) {
        GamePrefs.setInt(getApplication(), KEY_TARGET, target)
        _uiState.update { it.copy(targetWins = target) }
    }

    fun setName(index: Int, name: String) {
        _uiState.update { s -> s.copy(names = s.names.mapIndexed { i, n -> if (i == index) name else n }) }
    }

    /** شروع سِری از صفحه‌ی تنظیمات */
    fun startSeries() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setString(app, KEY_NAME_X, s.names[0].trim())
        GamePrefs.setString(app, KEY_NAME_O, s.names[1].trim())
        Analytics.gameSetup(
            "variant" to if (s.isBotGame) "bot" else "pvp",
            "difficulty" to if (s.isBotGame) s.difficulty.name.lowercase() else null,
            "target_wins" to s.targetWins,
        )
        beginSeries()
    }

    private fun beginSeries() {
        cancelJobs()
        _uiState.update {
            it.copy(
                phase = DoozPhase.Play,
                board = DoozBoard(),
                turn = DoozMark.X,
                roundStarter = DoozMark.X,
                roundNo = 1,
                xWins = 0,
                oWins = 0,
                draws = 0,
                roundResult = null,
                resultShown = false,
                botThinking = false,
                lastMove = null,
                seriesWinner = null,
            )
        }
        maybeBotMove()
    }

    // ---------------- بازی ----------------

    /** لمس انسان روی یک خانه */
    fun tapCell(index: Int) {
        val s = _uiState.value
        if (!s.humanCanMove || !s.board.canPlace(index)) return
        applyMove(index)
    }

    private fun applyMove(index: Int) {
        val s = _uiState.value
        if (s.phase != DoozPhase.Play || s.roundResult != null || !s.board.canPlace(index)) return
        val board = s.board.place(index, s.turn)
        emit(DoozSoundEvent.TAP)
        val win: DoozWin? = board.winner
        when {
            win != null -> finishRound(board, index, win)
            board.isFull -> finishRound(board, index, null)
            else -> {
                _uiState.update { it.copy(board = board, turn = it.turn.other, lastMove = index, botThinking = false) }
                maybeBotMove()
            }
        }
    }

    private fun finishRound(board: DoozBoard, index: Int, win: DoozWin?) {
        val s = _uiState.value
        val xWins = s.xWins + if (win?.mark == DoozMark.X) 1 else 0
        val oWins = s.oWins + if (win?.mark == DoozMark.O) 1 else 0
        val draws = s.draws + if (win == null) 1 else 0
        val seriesWinner = when {
            xWins >= s.targetWins -> DoozMark.X
            oWins >= s.targetWins -> DoozMark.O
            else -> null
        }
        _uiState.update {
            it.copy(
                board = board,
                lastMove = index,
                botThinking = false,
                roundResult = DoozRoundResult(win?.mark, win?.line),
                resultShown = false,
                xWins = xWins,
                oWins = oWins,
                draws = draws,
                seriesWinner = seriesWinner,
            )
        }
        emit(
            when {
                win == null -> DoozSoundEvent.DRAW
                seriesWinner != null -> DoozSoundEvent.SERIES_WIN
                else -> DoozSoundEvent.ROUND_WIN
            }
        )
        // مکث کوتاه تا خط برنده/شانه‌بالا دیده شود، بعد کارت نتیجه یا صفحه‌ی قهرمان
        resultJob?.cancel()
        resultJob = viewModelScope.launch {
            delay(if (seriesWinner != null) 1500 else 900)
            _uiState.update { st ->
                if (st.roundResult == null) st
                else if (st.seriesWinner != null) st.copy(phase = DoozPhase.SeriesOver, resultShown = true)
                else st.copy(resultShown = true)
            }
        }
    }

    /** دست بعدی: بازنده شروع می‌کند؛ اگر مساوی شد، نوبت شروع عوض می‌شود */
    fun nextRound() {
        val s = _uiState.value
        val result = s.roundResult ?: return
        if (s.seriesWinner != null) return
        val starter = when (result.winner) {
            null -> s.roundStarter.other
            else -> result.winner.other
        }
        resultJob?.cancel()
        _uiState.update {
            it.copy(
                board = DoozBoard(),
                turn = starter,
                roundStarter = starter,
                roundNo = it.roundNo + 1,
                roundResult = null,
                resultShown = false,
                botThinking = false,
                lastMove = null,
            )
        }
        maybeBotMove()
    }

    /** اگر نوبت ربات است، با کمی «فکر کردن» حرکت می‌کند */
    private fun maybeBotMove() {
        botJob?.cancel()
        val s = _uiState.value
        if (s.phase != DoozPhase.Play || s.roundResult != null || !s.isBot(s.turn)) return
        _uiState.update { it.copy(botThinking = true) }
        botJob = viewModelScope.launch {
            delay(Random.nextLong(500, 801))
            val current = _uiState.value
            if (current.phase != DoozPhase.Play || current.roundResult != null || !current.isBot(current.turn)) return@launch
            val move = try {
                withContext(Dispatchers.Default) {
                    bot.chooseMove(current.board, current.turn, current.difficulty)
                }
            } catch (_: Exception) {
                current.board.emptyCells.firstOrNull() ?: return@launch
            }
            applyMove(move)
        }
    }

    // ---------------- پایان و ناوبری ----------------

    /** دوباره با همین تنظیمات */
    fun playAgain() = beginSeries()

    /** برگشت به تنظیمات (سری فعلی دور ریخته می‌شود) */
    fun backToSetup() {
        cancelJobs()
        _uiState.update {
            it.copy(
                phase = DoozPhase.Setup,
                board = DoozBoard(),
                roundResult = null,
                resultShown = false,
                botThinking = false,
                seriesWinner = null,
                lastMove = null,
            )
        }
    }

    private fun cancelJobs() {
        botJob?.cancel()
        botJob = null
        resultJob?.cancel()
        resultJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelJobs()
    }

    companion object {
        val TARGETS = listOf(1, 3, 5)
        private const val KEY_MODE = "dooz_mode"
        private const val KEY_DIFFICULTY = "dooz_difficulty"
        private const val KEY_TARGET = "dooz_target"
        private const val KEY_NAME_X = "dooz_name_x"
        private const val KEY_NAME_O = "dooz_name_o"
    }
}
