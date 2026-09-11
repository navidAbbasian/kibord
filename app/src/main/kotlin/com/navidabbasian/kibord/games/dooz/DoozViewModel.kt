package com.navidabbasian.kibord.games.dooz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.net.NetSession
import com.navidabbasian.kibord.core.net.NetUiState
import com.navidabbasian.kibord.core.net.online.StoredOnlineRoom
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

/** دو نفره روی یک گوشی، با ربات، یا چند گوشی (شبکه‌ی محلی / اینترنت) */
enum class DoozMode { PVP, BOT, NET }

/** مرحله‌ی صفحه */
enum class DoozPhase { Setup, NetEntry, NetJoin, NetLobby, Play, SeriesOver }

/** صداهایی که ریشه‌ی کامپوزبل پخش می‌کند */
enum class DoozSoundEvent { TAP, ROUND_WIN, DRAW, SERIES_WIN }

/** نتیجه‌ی دستِ تمام‌شده: برنده (null = مساوی) و خط برنده */
data class DoozRoundResult(val winner: DoozMark?, val line: List<Int>?)

data class DoozUiState(
    val phase: DoozPhase = DoozPhase.Setup,
    val mode: DoozMode = DoozMode.PVP,
    val difficulty: DoozDifficulty = DoozDifficulty.MEDIUM,
    val targetWins: Int = 3,
    /** اسم بازیکن‌ها: [0] = ❌ ، [1] = ⭕ (در شبکه: میزبان، مهمان) */
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
    /** در بازی چندگوشی: مهره‌ی خودم (میزبان ❌، مهمان ⭕)؛ در بازی محلی تهی */
    val myMark: DoozMark? = null,
    /** در بازی چندگوشی: حریف وصل است؟ */
    val opponentConnected: Boolean = true,
) {
    val isBotGame: Boolean get() = mode == DoozMode.BOT
    val isNetGame: Boolean get() = mode == DoozMode.NET

    fun isBot(mark: DoozMark): Boolean = isBotGame && mark == DoozMark.O

    fun displayName(mark: DoozMark): String = when (mark) {
        DoozMark.X -> names[0].ifBlank { "بازیکن ۱" }
        DoozMark.O -> if (isBotGame) "ربات" else names[1].ifBlank { "بازیکن ۲" }
    }

    fun wins(mark: DoozMark): Int = if (mark == DoozMark.X) xWins else oWins

    /** آیا الان لمس انسان روی صفحه مجاز است؟ (در شبکه فقط در نوبت خودم) */
    val humanCanMove: Boolean
        get() = phase == DoozPhase.Play && roundResult == null && !botThinking && !isBot(turn) &&
            (myMark == null || turn == myMark)
}

/**
 * دوز — سه‌تایی در یک خط. ربات سه سطح دارد و سِری «تا چند برد» ادامه دارد.
 * همه‌ی وضعیت در همین ویومدل می‌ماند تا با چرخش صفحه چیزی گم نشود.
 *
 * چندگوشی: میزبان ❌ است و مرجع حقیقت؛ مهمان ⭕ فقط فرمان می‌فرستد و عکس
 * وضعیت می‌گیرد. راه (وای‌فای/اینترنت) را [NetSession] مدیریت می‌کند.
 */
class DoozViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DoozUiState())
    val uiState: StateFlow<DoozUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<DoozSoundEvent>(extraBufferCapacity = 8)
    val soundEvents: SharedFlow<DoozSoundEvent> = _soundEvents.asSharedFlow()

    private val bot = DoozBot(Random.Default)
    private var botJob: Job? = null
    private var resultJob: Job? = null

    private val session = NetSession(
        app = application,
        scope = viewModelScope,
        gameId = GAME_ID,
        encode = { m: DoozMessage -> m.encode() },
        decode = ::decodeDoozMessage,
        host = HostSide(),
        guest = GuestSide(),
    )

    /** وضعیت اتصال برای صفحه‌های مشترک شبکه */
    val net: StateFlow<NetUiState> = session.state

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

    private val isHost: Boolean get() = session.current.isHost
    private val isClient: Boolean get() = session.current.isClient

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
        if (s.mode == DoozMode.NET) {
            chooseNetworkMode()
            return
        }
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
        pushState()
        maybeBotMove()
    }

    // ---------------- چندگوشی: ورود، میزبانی، پیوستن ----------------

    /** از تنظیمات به صفحه‌ی ورود شبکه‌ای */
    fun chooseNetworkMode() {
        cancelJobs()
        GamePrefs.setString(getApplication(), KEY_MODE, DoozMode.NET.name)
        session.clearError()
        _uiState.update { it.copy(mode = DoozMode.NET, phase = DoozPhase.NetEntry, myMark = null) }
    }

    fun backFromNetEntry() {
        session.clearError()
        _uiState.update { it.copy(phase = DoozPhase.Setup) }
    }

    fun setMyName(name: String) = session.setMyName(name)

    fun setOnline(on: Boolean) {
        session.setOnline(on)
    }

    /** میزبان شو — روی وای‌فای یا اینترنت، بسته به سوییچ */
    fun hostGame() {
        if (session.current.online) session.hostOnline() else session.hostLan()
    }

    fun openJoin() {
        if (session.current.myName.isBlank()) return
        _uiState.update { it.copy(phase = DoozPhase.NetJoin) }
        session.startDiscovery()
    }

    fun joinLan(address: String, port: Int) = session.joinLan(address, port)

    fun joinOnline(code: String) = session.joinOnline(code)

    fun backFromJoin() {
        session.backFromJoin()
        _uiState.update { it.copy(phase = DoozPhase.NetEntry, myMark = null) }
    }

    /** میزبان از لابی منصرف شد */
    fun cancelHosting() {
        session.cancelHosting()
        cancelJobs()
        _uiState.update { it.copy(phase = DoozPhase.NetEntry, names = listOf(it.names[0], ""), myMark = null) }
    }

    fun resumeOnline() = session.resumeOnline()

    fun discardResume() = session.discardResume()

    fun reconnectOnline() = session.reconnectOnline()

    /** میزبان: عکس فعلی میز */
    private fun snapshot(): DoozRoomSnapshot {
        val s = _uiState.value
        return DoozRoomSnapshot(
            hostName = s.names[0],
            guestName = s.names[1],
            guestConnected = s.opponentConnected,
            started = s.phase == DoozPhase.Play || s.phase == DoozPhase.SeriesOver,
            targetWins = s.targetWins,
            cells = s.board.pattern(),
            turn = s.turn,
            roundStarter = s.roundStarter,
            roundNo = s.roundNo,
            xWins = s.xWins,
            oWins = s.oWins,
            draws = s.draws,
            hasResult = s.roundResult != null,
            resultWinner = s.roundResult?.winner,
            resultLine = s.roundResult?.line,
            resultShown = s.resultShown,
            seriesWinner = s.seriesWinner,
            lastMove = s.lastMove,
        )
    }

    /** اگر میزبانیم، وضعیت تازه برای مهمان (و در اینترنتی روی دیسک) */
    private fun pushState() {
        if (!isHost) return
        val guest = _uiState.value.names[1]
        session.pushToAll(if (guest.isBlank()) emptyList() else listOf(guest))
    }

    /** هر دو طرف: عکس میز را روی وضعیت خودمان می‌نشانیم (میزبان موقع ادامه، مهمان همیشه) */
    private fun applyRoom(room: DoozRoomSnapshot, asHost: Boolean) {
        val before = _uiState.value
        val result = if (room.hasResult) DoozRoundResult(room.resultWinner, room.resultLine) else null
        val phase = when {
            !room.started -> DoozPhase.NetLobby
            room.seriesWinner != null && room.resultShown -> DoozPhase.SeriesOver
            else -> DoozPhase.Play
        }
        _uiState.update {
            it.copy(
                phase = phase,
                mode = DoozMode.NET,
                names = listOf(room.hostName, room.guestName),
                targetWins = room.targetWins,
                board = DoozBoard.of(room.cells),
                turn = room.turn,
                roundStarter = room.roundStarter,
                roundNo = room.roundNo,
                xWins = room.xWins,
                oWins = room.oWins,
                draws = room.draws,
                roundResult = result,
                resultShown = room.resultShown,
                botThinking = false,
                lastMove = room.lastMove,
                seriesWinner = room.seriesWinner,
                myMark = if (asHost) DoozMark.X else DoozMark.O,
                opponentConnected = room.guestConnected,
            )
        }
        if (asHost) return
        // صداهای مهمان از روی تفاوت‌ها
        val boardChanged = before.board.pattern() != room.cells && room.started
        if (boardChanged && result == null) emit(DoozSoundEvent.TAP)
        if (before.roundResult == null && result != null) {
            emit(
                when {
                    result.winner == null -> DoozSoundEvent.DRAW
                    room.seriesWinner != null -> DoozSoundEvent.SERIES_WIN
                    else -> DoozSoundEvent.ROUND_WIN
                },
            )
        }
    }

    /** آنچه میزبان باید جواب بدهد */
    private inner class HostSide : NetSession.HostCallbacks<DoozMessage> {

        override fun acceptJoin(name: String): String? {
            val s = _uiState.value
            if (s.mode != DoozMode.NET) return "بازی‌ای در کار نیست"
            val guest = s.names[1]
            return when {
                name.isBlank() -> "اسم خالی است"
                name.trim() == s.names[0].trim() -> "این اسم مالِ میزبانه — یه اسم دیگه انتخاب کن"
                guest.isBlank() -> {
                    Analytics.gameSetup("variant" to "net", "net" to session.analyticsNet, "target_wins" to s.targetWins)
                    _uiState.update { it.copy(names = listOf(it.names[0], name), opponentConnected = true) }
                    // اولین حریف: بازی خودکار شروع می‌شود
                    beginSeries()
                    null
                }

                guest.trim() == name.trim() -> {
                    // برگشتِ همان حریف
                    _uiState.update { it.copy(opponentConnected = true) }
                    pushState()
                    null
                }

                else -> "دوز دو نفره‌ست — این میز پره!"
            }
        }

        override fun onCommand(name: String, msg: DoozMessage) {
            val s = _uiState.value
            if (name.trim() != s.names[1].trim()) return
            when (msg) {
                is DoozMessage.Tap -> {
                    if (s.phase == DoozPhase.Play && s.roundResult == null && s.turn == DoozMark.O) applyMove(msg.index)
                }

                DoozMessage.NextRound -> nextRoundInternal()
                DoozMessage.PlayAgain -> if (s.phase == DoozPhase.SeriesOver) beginSeries()
                is DoozMessage.State -> Unit
            }
        }

        override fun onDisconnected(name: String) {
            if (name.trim() != _uiState.value.names[1].trim()) return
            _uiState.update { it.copy(opponentConnected = false) }
            pushState()
        }

        override fun stateFor(name: String): DoozMessage = DoozMessage.State(snapshot())

        override fun onRoomReady() {
            cancelJobs()
            _uiState.update {
                it.copy(
                    phase = DoozPhase.NetLobby,
                    mode = DoozMode.NET,
                    names = listOf(session.current.myName, ""),
                    myMark = DoozMark.X,
                    opponentConnected = false,
                    board = DoozBoard(),
                    roundResult = null,
                    resultShown = false,
                    seriesWinner = null,
                    lastMove = null,
                    botThinking = false,
                )
            }
        }

        override fun onResumeHost(stored: StoredOnlineRoom, decoded: DoozMessage?) {
            cancelJobs()
            val room = (decoded as? DoozMessage.State)?.room
            if (room == null) {
                _uiState.update {
                    it.copy(phase = DoozPhase.NetLobby, mode = DoozMode.NET, names = listOf(stored.name, ""), myMark = DoozMark.X, opponentConnected = false)
                }
                return
            }
            applyRoom(room.copy(hostName = stored.name, guestConnected = false), asHost = true)
            // اگر دست تمام شده بود و کارتش هنوز نیامده، زمان‌بندش دوباره روشن شود
            val s = _uiState.value
            if (s.roundResult != null && !s.resultShown) scheduleResultCard(s.seriesWinner != null)
        }

        override fun onRoomFailed() {
            _uiState.update { it.copy(phase = DoozPhase.NetEntry) }
        }
    }

    /** مهمان: هرچه میزبان فرستاد، همان حقیقت است */
    private inner class GuestSide : NetSession.GuestCallbacks<DoozMessage> {
        override fun onMessage(msg: DoozMessage) {
            val room = (msg as? DoozMessage.State)?.room ?: return
            applyRoom(room, asHost = false)
        }
    }

    // ---------------- بازی ----------------

    /** لمس انسان روی یک خانه */
    fun tapCell(index: Int) {
        val s = _uiState.value
        if (!s.humanCanMove || !s.board.canPlace(index)) return
        if (isClient) {
            session.send(DoozMessage.Tap(index))
            return
        }
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
                pushState()
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
        pushState()
        scheduleResultCard(seriesWinner != null)
    }

    /** مکث کوتاه تا خط برنده/شانه‌بالا دیده شود، بعد کارت نتیجه یا صفحه‌ی قهرمان */
    private fun scheduleResultCard(seriesOver: Boolean) {
        resultJob?.cancel()
        resultJob = viewModelScope.launch {
            delay(if (seriesOver) 1500 else 900)
            _uiState.update { st ->
                if (st.roundResult == null) st
                else if (st.seriesWinner != null) st.copy(phase = DoozPhase.SeriesOver, resultShown = true)
                else st.copy(resultShown = true)
            }
            pushState()
        }
    }

    /** دست بعدی: بازنده شروع می‌کند؛ اگر مساوی شد، نوبت شروع عوض می‌شود */
    fun nextRound() {
        if (isClient) {
            session.send(DoozMessage.NextRound)
            return
        }
        nextRoundInternal()
    }

    private fun nextRoundInternal() {
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
        pushState()
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

    /** دوباره با همین تنظیمات (در شبکه: مهمان از میزبان می‌خواهد) */
    fun playAgain() {
        if (isClient) {
            session.send(DoozMessage.PlayAgain)
            return
        }
        beginSeries()
    }

    /** برگشت به تنظیمات (سری فعلی دور ریخته می‌شود؛ در شبکه یعنی ترک میز) */
    fun backToSetup() {
        cancelJobs()
        if (_uiState.value.isNetGame) session.leave()
        _uiState.update {
            it.copy(
                phase = DoozPhase.Setup,
                board = DoozBoard(),
                roundResult = null,
                resultShown = false,
                botThinking = false,
                seriesWinner = null,
                lastMove = null,
                myMark = null,
                opponentConnected = true,
            )
        }
    }

    private fun cancelJobs() {
        botJob?.cancel()
        botJob = null
        resultJob?.cancel()
        resultJob = null
    }

    /** بسته شدن صفحه یا کشته شدن اپ: شبکه جمع می‌شود ولی اتاقِ اینترنتی ذخیره می‌ماند */
    override fun onCleared() {
        super.onCleared()
        cancelJobs()
        session.release()
    }

    companion object {
        const val GAME_ID = "dooz"
        val TARGETS = listOf(1, 3, 5)
        private const val KEY_MODE = "dooz_mode"
        private const val KEY_DIFFICULTY = "dooz_difficulty"
        private const val KEY_TARGET = "dooz_target"
        private const val KEY_NAME_X = "dooz_name_x"
        private const val KEY_NAME_O = "dooz_name_o"
    }
}
