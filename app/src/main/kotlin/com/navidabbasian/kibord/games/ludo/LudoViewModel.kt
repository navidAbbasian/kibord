package com.navidabbasian.kibord.games.ludo

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.net.NetSession
import com.navidabbasian.kibord.core.net.NetUiState
import com.navidabbasian.kibord.core.net.online.StoredOnlineRoom
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.games.ludo.engine.LudoBot
import com.navidabbasian.kibord.games.ludo.engine.LudoCapture
import com.navidabbasian.kibord.games.ludo.engine.LudoColor
import com.navidabbasian.kibord.games.ludo.engine.LudoEngine
import com.navidabbasian.kibord.games.ludo.engine.LudoEvent
import com.navidabbasian.kibord.games.ludo.engine.LudoMove
import com.navidabbasian.kibord.games.ludo.engine.LudoPhase
import com.navidabbasian.kibord.games.ludo.engine.LudoRules
import com.navidabbasian.kibord.games.ludo.engine.LudoSeat
import com.navidabbasian.kibord.games.ludo.engine.LudoSeatKind
import com.navidabbasian.kibord.games.ludo.engine.LudoState
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
import kotlin.random.Random

/** مرحله‌های صفحه‌ای منچ */
enum class LudoStage { Setup, NetEntry, NetJoin, NetLobby, Playing }

/** رویدادهای صوتی که رابط به صدای مناسب ترجمه می‌کند */
enum class LudoSoundEvent { DICE, HOP, ENTER, CAPTURE, GOAL, SKIP, TURN, BONUS, WIN }

/** تنظیم یک صندلی در صفحه‌ی شروع */
data class LudoSeatConfig(
    val kind: LudoSeatKind,
    val name: String,
)

/** زمان هر پرش مهره روی یک خانه — رابط و ویومدل هر دو همین را می‌شناسند */
const val LUDO_HOP_MS = 130L
/** مدت انیمیشن تاس */
const val LUDO_DICE_MS = 650L
/** مدت «پوف» مهره‌ی زده‌شده */
const val LUDO_POOF_MS = 480L

/** انیمیشن در جریان: مهره‌ای که دارد می‌پرد و مهره‌ای که قرار است زده شود */
data class LudoMoveAnim(
    val nonce: Int,
    val move: LudoMove,
    val captured: LudoCapture?,
    /** زمان شروع (elapsedRealtime) تا بعد از چرخش صفحه از نو پخش نشود */
    val startedAt: Long,
) {
    val durationMs: Long get() = move.hops * LUDO_HOP_MS + (if (captured != null) LUDO_POOF_MS else 0L)
}

data class LudoUiState(
    val stage: LudoStage = LudoStage.Setup,
    val seatConfigs: List<LudoSeatConfig> = defaultSeatConfigs(),
    val tripleSixRule: Boolean = true,
    val safeStart: Boolean = false,
    val game: LudoState? = null,
    /** هر پرتاب یکی بالا می‌رود تا انیمیشن تاس دوباره اجرا شود */
    val rollNonce: Int = 0,
    /** تاس در حال غلتیدن است */
    val rolling: Boolean = false,
    /** انیمیشن حرکت جاری */
    val anim: LudoMoveAnim? = null,
    /** مهره‌های مجاز برای لمس (شماره‌ی مهره‌ی بازیکن نوبت) */
    val legalTokens: Set<Int> = emptySet(),
    /** مهره‌ای که به‌زودی خودکار حرکت می‌کند (تنها حرکت مجاز) */
    val autoToken: Int? = null,
    /** پیام کوتاه زیر صفحه */
    val message: String? = null,
    /** موتور مشغول است (انیمیشن، ربات، مکث) — دکمه‌ها قفل */
    val busy: Boolean = false,
    /** بازی چندگوشی است؟ (لابی و بازی) */
    val netMode: Boolean = false,
    /** صندلی‌های میز چندگوشی به ترتیب رنگ */
    val netSeats: List<LudoNetSeat> = List(4) { LudoNetSeat() },
    /** رنگ خودم در بازی چندگوشی (میزبان قرمز) */
    val myColor: LudoColor? = null,
) {
    val activeSeatCount: Int get() = seatConfigs.count { it.kind != LudoSeatKind.EMPTY }
    val botCount: Int get() = seatConfigs.count { it.kind == LudoSeatKind.BOT }
    val canStart: Boolean get() = activeSeatCount >= 2

    /** لابی چندگوشی: دست‌کم یک دوستِ وصل و دو صندلی پر */
    val netCanStart: Boolean
        get() = netSeats.any { it.kind == LudoNetSeatKind.GUEST && it.connected } &&
            netSeats.count { it.kind != LudoNetSeatKind.EMPTY } >= 2

    /** اسم نمایشی صندلی (پیش‌فرض اگر خالی بود) */
    fun displayName(color: LudoColor): String {
        val seat = game?.seat(color)
        val name = seat?.name ?: if (netMode) netSeats[color.ordinal].name else seatConfigs[color.ordinal].name
        val kind = seat?.kind ?: if (netMode) {
            if (netSeats[color.ordinal].kind == LudoNetSeatKind.BOT) LudoSeatKind.BOT else LudoSeatKind.HUMAN
        } else seatConfigs[color.ordinal].kind
        return name.ifBlank { defaultName(color, kind) }
    }

    /** آیا نوبت کسی است که روی همین گوشی بازی می‌کند؟ */
    fun isLocalHumanTurn(g: LudoState): Boolean =
        if (netMode) g.turn == myColor else !g.currentSeat.isBot

    /** در چندگوشی: صاحب این رنگ قطع شده؟ */
    fun isDisconnected(color: LudoColor): Boolean =
        netMode && netSeats[color.ordinal].let { it.kind == LudoNetSeatKind.GUEST && !it.connected }
}

private fun defaultName(color: LudoColor, kind: LudoSeatKind): String =
    if (kind == LudoSeatKind.BOT) "ربات" else "بازیکن ${(color.ordinal + 1).toPersianDigits()}"

private fun defaultSeatConfigs(): List<LudoSeatConfig> = listOf(
    LudoSeatConfig(LudoSeatKind.HUMAN, ""),
    LudoSeatConfig(LudoSeatKind.BOT, ""),
    LudoSeatConfig(LudoSeatKind.EMPTY, ""),
    LudoSeatConfig(LudoSeatKind.EMPTY, ""),
)

/**
 * ویومدل منچ: تنظیم صندلی‌ها، جریان نوبت‌ها (تاس → حرکت → پرتاب اضافه/نوبت بعدی)،
 * ربات‌ها و زمان‌بندی انیمیشن‌ها — همه در یک Job دنباله‌دار تا با چرخش صفحه زنده بماند.
 *
 * چندگوشی: میزبان (قرمز) موتور و ربات‌ها را می‌گرداند و بعد از هر تغییر عکس میز را
 * می‌فرستد؛ مهمان‌ها فقط «تاس بریز» و «این مهره» می‌فرستند.
 */
class LudoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LudoUiState())
    val uiState: StateFlow<LudoUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<LudoSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<LudoSoundEvent> = _soundEvents.asSharedFlow()

    private val random = Random.Default
    private var flowJob: Job? = null
    private var animNonce = 0

    private val session = NetSession(
        app = application,
        scope = viewModelScope,
        gameId = GAME_ID,
        encode = { m: LudoMessage -> m.encode() },
        decode = ::decodeLudoMessage,
        host = HostSide(),
        guest = GuestSide(),
    )

    /** وضعیت اتصال برای صفحه‌های مشترک شبکه */
    val net: StateFlow<NetUiState> = session.state

    private val isHost: Boolean get() = session.current.isHost
    private val isClient: Boolean get() = session.current.isClient

    init {
        val app = getApplication<Application>()
        val seats = LudoColor.entries.map { c ->
            val kindName = GamePrefs.getString(app, "ludo_seat_${c.ordinal}", null)
            val kind = LudoSeatKind.entries.firstOrNull { it.name == kindName }
                ?: defaultSeatConfigs()[c.ordinal].kind
            LudoSeatConfig(kind = kind, name = GamePrefs.getString(app, "ludo_name_${c.ordinal}", "") ?: "")
        }
        _uiState.update {
            it.copy(
                seatConfigs = seats,
                tripleSixRule = GamePrefs.getBool(app, "ludo_triple_six", true),
                safeStart = GamePrefs.getBool(app, "ludo_safe_start", false),
            )
        }
    }

    private fun sound(e: LudoSoundEvent) {
        _soundEvents.tryEmit(e)
    }

    /** تغییر وضعیت + (اگر میزبانیم) پخش عکس تازه برای مهمان‌ها */
    private inline fun mutate(block: (LudoUiState) -> LudoUiState) {
        _uiState.update(block)
        pushState()
    }

    // ---- صفحه‌ی تنظیم ----

    fun setSeatKind(color: LudoColor, kind: LudoSeatKind) {
        _uiState.update { s ->
            s.copy(seatConfigs = s.seatConfigs.mapIndexed { i, cfg -> if (i == color.ordinal) cfg.copy(kind = kind) else cfg })
        }
    }

    fun setSeatName(color: LudoColor, name: String) {
        _uiState.update { s ->
            s.copy(seatConfigs = s.seatConfigs.mapIndexed { i, cfg -> if (i == color.ordinal) cfg.copy(name = name.take(14)) else cfg })
        }
    }

    fun setTripleSixRule(on: Boolean) = _uiState.update { it.copy(tripleSixRule = on) }
    fun setSafeStart(on: Boolean) = _uiState.update { it.copy(safeStart = on) }

    private fun savePrefs() {
        val app = getApplication<Application>()
        val s = _uiState.value
        s.seatConfigs.forEachIndexed { i, cfg ->
            GamePrefs.setString(app, "ludo_seat_$i", cfg.kind.name)
            GamePrefs.setString(app, "ludo_name_$i", cfg.name.trim())
        }
        GamePrefs.setBool(app, "ludo_triple_six", s.tripleSixRule)
        GamePrefs.setBool(app, "ludo_safe_start", s.safeStart)
    }

    /** شروع دست از صفحه‌ی تنظیم */
    fun startGame() {
        val s = _uiState.value
        if (!s.canStart) return
        savePrefs()
        Analytics.gameSetup(
            "players" to s.activeSeatCount,
            "bots" to s.botCount,
            "triple_six_rule" to s.tripleSixRule,
            "safe_start" to s.safeStart,
        )
        launchGame(localSeats())
    }

    private fun localSeats(): List<LudoSeat> {
        val s = _uiState.value
        return LudoColor.entries.map { c ->
            val cfg = s.seatConfigs[c.ordinal]
            LudoSeat(color = c, kind = cfg.kind, name = cfg.name.trim().ifBlank { defaultName(c, cfg.kind) })
        }
    }

    private fun netSeatsAsGame(): List<LudoSeat> {
        val s = _uiState.value
        return LudoColor.entries.map { c ->
            val seat = s.netSeats[c.ordinal]
            val kind = when (seat.kind) {
                LudoNetSeatKind.HOST, LudoNetSeatKind.GUEST -> LudoSeatKind.HUMAN
                LudoNetSeatKind.BOT -> LudoSeatKind.BOT
                LudoNetSeatKind.EMPTY -> LudoSeatKind.EMPTY
            }
            LudoSeat(color = c, kind = kind, name = seat.name.ifBlank { defaultName(c, kind) })
        }
    }

    /** دوباره بازی با همان تنظیم‌ها (در شبکه: مهمان از میزبان می‌خواهد) */
    fun playAgain() {
        if (isClient) {
            session.send(LudoMessage.PlayAgain)
            return
        }
        Analytics.gameReplay()
        launchGame(if (_uiState.value.netMode) netSeatsAsGame() else localSeats())
    }

    private fun launchGame(seats: List<LudoSeat>) {
        flowJob?.cancel()
        val s = _uiState.value
        val active = seats.filter { it.active }.map { it.color }
        if (active.size < 2) return
        val game = LudoEngine.newGame(
            seats = seats,
            rules = LudoRules(tripleSixLosesTurn = s.tripleSixRule, safeStartSquares = s.safeStart),
            firstTurn = active.random(random),
        )
        mutate {
            it.copy(
                stage = LudoStage.Playing,
                game = game,
                rollNonce = 0,
                rolling = false,
                anim = null,
                legalTokens = emptySet(),
                autoToken = null,
                message = null,
                busy = false,
            )
        }
        runFlow { onTurnStart(announce = false) }
    }

    /** برگشت به صفحه‌ی تنظیم (از صفحه‌ی برنده یا خروج) — در شبکه یعنی ترک میز */
    fun backToSetup() {
        flowJob?.cancel()
        if (_uiState.value.netMode) session.leave()
        _uiState.update {
            it.copy(
                stage = LudoStage.Setup,
                game = null,
                anim = null,
                legalTokens = emptySet(),
                autoToken = null,
                message = null,
                busy = false,
                rolling = false,
                netMode = false,
                netSeats = List(4) { LudoNetSeat() },
                myColor = null,
            )
        }
    }

    // ---- چندگوشی: ورود، میزبانی، پیوستن ----

    fun chooseNetworkMode() {
        flowJob?.cancel()
        session.clearError()
        _uiState.update { it.copy(stage = LudoStage.NetEntry, game = null) }
    }

    fun backFromNetEntry() {
        session.clearError()
        _uiState.update { it.copy(stage = LudoStage.Setup) }
    }

    fun setMyName(name: String) = session.setMyName(name)

    fun setOnline(on: Boolean) {
        session.setOnline(on)
    }

    fun hostGame() {
        if (session.current.online) session.hostOnline() else session.hostLan()
    }

    fun openJoin() {
        if (session.current.myName.isBlank()) return
        _uiState.update { it.copy(stage = LudoStage.NetJoin) }
        session.startDiscovery()
    }

    fun joinLan(address: String, port: Int) = session.joinLan(address, port)

    fun joinOnline(code: String) = session.joinOnline(code)

    fun backFromJoin() {
        session.backFromJoin()
        _uiState.update { it.copy(stage = LudoStage.NetEntry, netMode = false, myColor = null) }
    }

    fun cancelHosting() {
        flowJob?.cancel()
        session.cancelHosting()
        _uiState.update { it.copy(stage = LudoStage.NetEntry, netMode = false, netSeats = List(4) { LudoNetSeat() }, myColor = null, game = null) }
    }

    fun resumeOnline() = session.resumeOnline()

    fun discardResume() = session.discardResume()

    fun reconnectOnline() = session.reconnectOnline()

    /** میزبان در لابی: صندلی خالی ↔ ربات */
    fun toggleLobbySeat(index: Int) {
        if (!isHost) return
        val s = _uiState.value
        if (s.stage != LudoStage.NetLobby) return
        val seat = s.netSeats.getOrNull(index) ?: return
        val next = when (seat.kind) {
            LudoNetSeatKind.EMPTY -> seat.copy(kind = LudoNetSeatKind.BOT, name = "ربات")
            LudoNetSeatKind.BOT -> LudoNetSeat()
            else -> return
        }
        mutate { st -> st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == index) next else x }) }
    }

    /** میزبان: شروع بازی چندگوشی با صندلی‌های لابی */
    fun startNetGame() {
        val s = _uiState.value
        if (!isHost || s.stage != LudoStage.NetLobby || !s.netCanStart) return
        Analytics.gameSetup(
            "players" to s.netSeats.count { it.isHuman },
            "bots" to s.netSeats.count { it.kind == LudoNetSeatKind.BOT },
            "net" to session.analyticsNet,
            "triple_six_rule" to s.tripleSixRule,
            "safe_start" to s.safeStart,
        )
        launchGame(netSeatsAsGame())
    }

    private fun snapshot(): LudoRoomSnapshot {
        val s = _uiState.value
        return LudoRoomSnapshot(
            seats = s.netSeats,
            tripleSix = s.tripleSixRule,
            safeStart = s.safeStart,
            started = s.stage == LudoStage.Playing && s.game != null,
            game = s.game,
            rollNonce = s.rollNonce,
            rolling = s.rolling,
            anim = s.anim?.let { LudoNetAnim(it.nonce, it.move, it.captured) },
            legalTokens = s.legalTokens,
            autoToken = s.autoToken,
            message = s.message,
            busy = s.busy,
        )
    }

    private fun pushState() {
        if (!isHost) return
        val guests = _uiState.value.netSeats.filter { it.kind == LudoNetSeatKind.GUEST }.map { it.name }
        session.pushToAll(guests)
    }

    /** عکس میز روی وضعیت خودمان (میزبان موقع ادامه، مهمان همیشه) */
    private fun applyRoom(room: LudoRoomSnapshot, asHost: Boolean) {
        val before = _uiState.value
        val myColor = if (asHost) LudoColor.RED else {
            val me = session.current.myName.trim()
            LudoColor.entries.firstOrNull { room.seats[it.ordinal].name.trim() == me && room.seats[it.ordinal].kind == LudoNetSeatKind.GUEST }
        }
        val anim = room.anim?.let { a ->
            if (before.anim?.nonce == a.nonce) before.anim
            else LudoMoveAnim(nonce = a.nonce, move = a.move, captured = a.captured, startedAt = SystemClock.elapsedRealtime())
        }
        val stage = if (room.started && room.game != null) LudoStage.Playing else LudoStage.NetLobby
        _uiState.update {
            it.copy(
                stage = stage,
                netMode = true,
                netSeats = room.seats,
                tripleSixRule = room.tripleSix,
                safeStart = room.safeStart,
                game = room.game,
                rollNonce = room.rollNonce,
                rolling = room.rolling,
                anim = anim,
                legalTokens = room.legalTokens,
                autoToken = room.autoToken,
                message = room.message,
                busy = room.busy,
                myColor = myColor,
            )
        }
        if (asHost) {
            animNonce = maxOf(animNonce, room.anim?.nonce ?: 0)
            return
        }
        // صداهای مهمان از روی تفاوت‌ها
        val g = room.game ?: return
        if (room.rollNonce > before.rollNonce) sound(LudoSoundEvent.DICE)
        if (room.anim != null && room.anim.nonce != before.anim?.nonce) {
            sound(if (room.anim.move.entersBoard) LudoSoundEvent.ENTER else LudoSoundEvent.HOP)
            if (room.anim.captured != null) sound(LudoSoundEvent.CAPTURE)
            else if (room.anim.move.reachesGoal) sound(LudoSoundEvent.GOAL)
        }
        val wasFinished = before.game?.phase == LudoPhase.FINISHED
        if (g.phase == LudoPhase.FINISHED && !wasFinished) sound(LudoSoundEvent.WIN)
        if (g.turn == myColor && before.game?.turn != myColor && g.phase == LudoPhase.ROLLING) sound(LudoSoundEvent.TURN)
    }

    private fun colorOfGuest(name: String): LudoColor? {
        val seats = _uiState.value.netSeats
        return LudoColor.entries.firstOrNull { seats[it.ordinal].kind == LudoNetSeatKind.GUEST && seats[it.ordinal].name.trim() == name.trim() }
    }

    /** آنچه میزبان باید جواب بدهد */
    private inner class HostSide : NetSession.HostCallbacks<LudoMessage> {

        override fun acceptJoin(name: String): String? {
            val s = _uiState.value
            if (!s.netMode) return "بازی‌ای در کار نیست"
            if (name.isBlank()) return "اسم خالی است"
            if (name.trim() == session.current.myName.trim()) return "این اسم مالِ میزبانه — یه اسم دیگه انتخاب کن"
            val existing = colorOfGuest(name)
            if (existing != null) {
                // برگشتِ همان آدم
                mutate { st -> st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == existing.ordinal) x.copy(connected = true) else x }) }
                return null
            }
            if (s.stage == LudoStage.Playing) return "بازی شروع شده — دفعه‌ی بعد زودتر بیا!"
            val free = s.netSeats.indexOfFirst { it.kind == LudoNetSeatKind.EMPTY }
            if (free < 0) return "میز پره — چهار نفر بیشتر جا نداره!"
            mutate { st ->
                st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == free) LudoNetSeat(name = name.trim(), kind = LudoNetSeatKind.GUEST) else x })
            }
            return null
        }

        override fun onCommand(name: String, msg: LudoMessage) {
            val color = colorOfGuest(name) ?: return
            val s = _uiState.value
            val game = s.game ?: return
            when (msg) {
                LudoMessage.Roll -> {
                    if (s.stage == LudoStage.Playing && !s.busy && game.phase == LudoPhase.ROLLING && game.turn == color) {
                        runFlow { doRoll() }
                    }
                }

                is LudoMessage.Tap -> {
                    if (s.stage == LudoStage.Playing && !s.busy && game.phase == LudoPhase.MOVING && game.turn == color) {
                        val move = LudoEngine.legalMoves(game).firstOrNull { it.token == msg.token } ?: return
                        runFlow { performMove(move) }
                    }
                }

                LudoMessage.Continue -> continueForOthers()
                LudoMessage.PlayAgain -> if (game.phase == LudoPhase.FINISHED) playAgain()
                is LudoMessage.State -> Unit
            }
        }

        override fun onDisconnected(name: String) {
            val color = colorOfGuest(name) ?: return
            mutate { st -> st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == color.ordinal) x.copy(connected = false) else x }) }
        }

        override fun stateFor(name: String): LudoMessage = LudoMessage.State(snapshot())

        override fun onRoomReady() {
            flowJob?.cancel()
            _uiState.update {
                it.copy(
                    stage = LudoStage.NetLobby,
                    netMode = true,
                    netSeats = listOf(LudoNetSeat(name = session.current.myName.trim(), kind = LudoNetSeatKind.HOST)) + List(3) { LudoNetSeat() },
                    myColor = LudoColor.RED,
                    game = null,
                    anim = null,
                    legalTokens = emptySet(),
                    autoToken = null,
                    message = null,
                    busy = false,
                    rolling = false,
                )
            }
        }

        override fun onResumeHost(stored: StoredOnlineRoom, decoded: LudoMessage?) {
            flowJob?.cancel()
            val room = (decoded as? LudoMessage.State)?.room
            if (room == null) {
                _uiState.update {
                    it.copy(
                        stage = LudoStage.NetLobby,
                        netMode = true,
                        netSeats = listOf(LudoNetSeat(name = stored.name, kind = LudoNetSeatKind.HOST)) + List(3) { LudoNetSeat() },
                        myColor = LudoColor.RED,
                        game = null,
                    )
                }
                return
            }
            val seats = room.seats.map { if (it.kind == LudoNetSeatKind.GUEST) it.copy(connected = false) else it }
            applyRoom(room.copy(seats = seats, rolling = false, busy = false), asHost = true)
            val g = _uiState.value.game ?: return
            // جریان از همان‌جا که مانده بود ادامه می‌یابد
            runFlow {
                when (g.phase) {
                    LudoPhase.ROLLING -> onTurnStart(announce = false)
                    LudoPhase.MOVING, LudoPhase.PASSING -> resolveAfterRoll()
                    LudoPhase.FINISHED -> Unit
                }
            }
        }

        override fun onRoomFailed() {
            _uiState.update { it.copy(stage = LudoStage.NetEntry, netMode = false, game = null) }
        }
    }

    /** مهمان: هرچه میزبان فرستاد، همان حقیقت است */
    private inner class GuestSide : NetSession.GuestCallbacks<LudoMessage> {
        override fun onMessage(msg: LudoMessage) {
            val room = (msg as? LudoMessage.State)?.room ?: return
            applyRoom(room, asHost = false)
        }
    }

    // ---- جریان نوبت ----

    private fun runFlow(block: suspend () -> Unit) {
        flowJob?.cancel()
        flowJob = viewModelScope.launch { block() }
    }

    /** آغاز نوبت بازیکن فعلی: آدم منتظر لمس «تاس بریز» می‌ماند، ربات خودش می‌ریزد */
    private suspend fun onTurnStart(announce: Boolean = true) {
        val game = _uiState.value.game ?: return
        // لرزش کوتاه فقط وقتی نوبت به آدمِ همین گوشی می‌رسد — نشانه‌ی «گوشی دست توئه»
        if (announce && _uiState.value.isLocalHumanTurn(game)) sound(LudoSoundEvent.TURN)
        mutate { it.copy(message = null, legalTokens = emptySet(), autoToken = null, busy = false) }
        if (game.currentSeat.isBot) {
            mutate { it.copy(busy = true) }
            delay(if (announce) 850 else 600)
            doRoll()
        }
    }

    /** لمس «تاس بریز» توسط آدمِ همین گوشی */
    fun rollDice() {
        val s = _uiState.value
        val game = s.game ?: return
        if (s.busy || game.phase != LudoPhase.ROLLING || !s.isLocalHumanTurn(game)) return
        if (isClient) {
            session.send(LudoMessage.Roll)
            return
        }
        runFlow { doRoll() }
    }

    private suspend fun doRoll() {
        val game = _uiState.value.game ?: return
        if (game.phase != LudoPhase.ROLLING) return
        val value = random.nextInt(1, 7)
        val rolled = LudoEngine.roll(game, value)
        sound(LudoSoundEvent.DICE)
        mutate {
            it.copy(game = rolled, rollNonce = it.rollNonce + 1, rolling = true, busy = true, message = null, legalTokens = emptySet(), autoToken = null)
        }
        delay(LUDO_DICE_MS)
        mutate { it.copy(rolling = false) }
        resolveAfterRoll()
    }

    private suspend fun resolveAfterRoll() {
        val s = _uiState.value
        val game = s.game ?: return
        val name = s.displayName(game.turn)
        when (game.phase) {
            LudoPhase.PASSING -> {
                val msg = when (game.event) {
                    LudoEvent.TRIPLE_SIX -> "سه تا شش پشت هم! نوبت $name سوخت 😅"
                    else -> "$name حرکتی نداره — نوبت بعدی"
                }
                sound(LudoSoundEvent.SKIP)
                mutate { it.copy(message = msg, busy = true) }
                delay(1400)
                val g = _uiState.value.game ?: return
                if (g.phase != LudoPhase.PASSING) return
                mutate { it.copy(game = LudoEngine.endTurn(g)) }
                onTurnStart()
            }

            LudoPhase.MOVING -> {
                val moves = LudoEngine.legalMoves(game)
                if (moves.isEmpty()) {
                    // نباید پیش بیاید (roll خودش PASSING می‌کند) — ایمنی
                    mutate { it.copy(game = LudoEngine.endTurn(game)) }
                    onTurnStart()
                    return
                }
                if (game.currentSeat.isBot) {
                    mutate { it.copy(busy = true, legalTokens = moves.map { m -> m.token }.toSet()) }
                    delay(650)
                    val pick = LudoBot.choose(game, moves, random) ?: moves.first()
                    performMove(pick)
                } else if (moves.size == 1) {
                    // تنها حرکت مجاز: کمی برجسته کن و خودش برو
                    mutate { it.copy(busy = true, legalTokens = setOf(moves[0].token), autoToken = moves[0].token) }
                    delay(520)
                    performMove(moves[0])
                } else {
                    mutate {
                        it.copy(
                            busy = false,
                            legalTokens = moves.map { m -> m.token }.toSet(),
                            message = "یه مهره‌ی روشن رو لمس کن",
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    /** لمس یک مهره‌ی بازیکن نوبت توسط آدمِ همین گوشی */
    fun tapToken(token: Int) {
        val s = _uiState.value
        val game = s.game ?: return
        if (s.busy || game.phase != LudoPhase.MOVING || !s.isLocalHumanTurn(game)) return
        if (isClient) {
            if (token in s.legalTokens) session.send(LudoMessage.Tap(token))
            return
        }
        val move = LudoEngine.legalMoves(game).firstOrNull { it.token == token } ?: return
        runFlow { performMove(move) }
    }

    private suspend fun performMove(move: LudoMove) {
        val game = _uiState.value.game ?: return
        if (game.phase != LudoPhase.MOVING) return
        val after = try {
            LudoEngine.applyMove(game, move)
        } catch (_: IllegalArgumentException) {
            return
        }
        val anim = LudoMoveAnim(
            nonce = ++animNonce,
            move = move,
            captured = after.lastMove?.captured,
            startedAt = SystemClock.elapsedRealtime(),
        )
        mutate {
            it.copy(game = after, anim = anim, legalTokens = emptySet(), autoToken = null, busy = true, message = null)
        }
        sound(if (move.entersBoard) LudoSoundEvent.ENTER else LudoSoundEvent.HOP)
        delay(move.hops * LUDO_HOP_MS + 60)
        if (anim.captured != null) {
            sound(LudoSoundEvent.CAPTURE)
            delay(LUDO_POOF_MS)
        } else if (move.reachesGoal) {
            sound(LudoSoundEvent.GOAL)
        }
        val s = _uiState.value
        val name = s.displayName(move.color)
        when (after.phase) {
            LudoPhase.FINISHED -> {
                sound(LudoSoundEvent.WIN)
                mutate { it.copy(busy = false, message = null) }
            }

            LudoPhase.ROLLING -> {
                // پرتاب اضافه: همان بازیکن
                sound(LudoSoundEvent.BONUS)
                val why = if (anim.captured != null) "زدی! 🎯" else "شش آوردی! 🎁"
                mutate { it.copy(message = "$why یه تاس دیگه برای $name") }
                if (after.currentSeat.isBot) {
                    delay(900)
                    doRoll()
                } else {
                    mutate { it.copy(busy = false) }
                }
            }

            else -> {
                delay(150)
                onTurnStart()
            }
        }
    }

    /** از صفحه‌ی برنده: بقیه برای رتبه‌های بعدی ادامه می‌دهند */
    fun continueForOthers() {
        if (isClient) {
            session.send(LudoMessage.Continue)
            return
        }
        val game = _uiState.value.game ?: return
        if (game.phase != LudoPhase.FINISHED || game.gameOver) return
        mutate { it.copy(game = LudoEngine.continueAfterFinish(game), anim = null) }
        runFlow { onTurnStart() }
    }

    /** بسته شدن صفحه یا کشته شدن اپ: شبکه جمع می‌شود ولی اتاقِ اینترنتی ذخیره می‌ماند */
    override fun onCleared() {
        flowJob?.cancel()
        session.release()
        super.onCleared()
    }

    companion object {
        const val GAME_ID = "ludo"
    }
}
