package com.navidabbasian.kibord.games.uno

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.net.NetSession
import com.navidabbasian.kibord.core.net.NetUiState
import com.navidabbasian.kibord.core.net.online.StoredOnlineRoom
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.uno.engine.UnoBot
import com.navidabbasian.kibord.games.uno.engine.UnoCard
import com.navidabbasian.kibord.games.uno.engine.UnoColor
import com.navidabbasian.kibord.games.uno.engine.UnoEngine
import com.navidabbasian.kibord.games.uno.engine.UnoKind
import com.navidabbasian.kibord.games.uno.engine.UnoMode
import com.navidabbasian.kibord.games.uno.engine.UnoPhase
import com.navidabbasian.kibord.games.uno.engine.UnoRules
import com.navidabbasian.kibord.games.uno.engine.UnoSettings
import com.navidabbasian.kibord.games.uno.engine.UnoState
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

/** صندلی انسان در بازی محلی همیشه ۰ است (در چندگوشی: [UnoUiState.mySeat]) */
const val UNO_HUMAN = 0

enum class UnoStage { Setup, NetEntry, NetJoin, NetLobby, Playing }

enum class UnoSoundEvent { PLAY, DRAW, HIT, UNO_CALL, CAUGHT, ROUND_END, MATCH_WON, MATCH_LOST }

/** انیمیشن جابه‌جایی دست‌ها در هفت-صفر */
data class UnoHandAnim(
    val id: Int,
    /** جفت‌های (از، به) که کارت‌های بسته بینشان پرواز می‌کنند */
    val moves: List<Pair<Int, Int>>,
)

private val BOT_NAMES = listOf(
    "سارا", "نیما", "مهسا", "رضا", "شبنم", "پیمان", "غزل", "بابک", "ترانه", "امید", "نگار", "کاوه",
)

/** مهلت اعلام «اونو!» برای انسان */
const val UNO_WINDOW_MS = 2500L

data class UnoUiState(
    val stage: UnoStage = UnoStage.Setup,
    val playerName: String = "",
    val mode: UnoMode = UnoMode.CLASSIC,
    val players: Int = 4,
    val target: Int = UnoRules.DEFAULT_TARGET,
    /** اسم صندلی‌های ۱ به بعد (بازی محلی) */
    val botNames: List<String> = listOf("سارا", "نیما", "مهسا"),
    val game: UnoState? = null,
    val thinkingSeat: Int? = null,
    /** پیام گذرای رویدادها */
    val toast: String? = null,
    val toastId: Int = 0,
    /** حباب «اونو!» روی صندلی */
    val unoBubbleSeat: Int? = null,
    val handAnim: UnoHandAnim? = null,
    val dealing: Boolean = false,
    /** بعد از رویت خلاصه‌ی دست آخر، صفحه‌ی برنده (هر گوشی برای خودش) */
    val showFinal: Boolean = false,
    /** بازی چندگوشی است؟ */
    val netMode: Boolean = false,
    /** صندلی‌های میز چندگوشی (اندازه = تعداد بازیکن) */
    val netSeats: List<UnoNetSeat> = emptyList(),
    /** صندلی خودم: در محلی ۰، در چندگوشی صندلی‌ای که میزبان داده */
    val mySeat: Int = UNO_HUMAN,
) {
    fun seatName(seat: Int): String = when {
        netMode -> netSeats.getOrNull(seat)?.name?.ifBlank { "ربات" } ?: "ربات"
        seat == UNO_HUMAN -> playerName.ifBlank { "تو" }
        else -> botNames.getOrElse(seat - 1) { "ربات" }
    }

    /** این صندلی را آدم بازی می‌کند (نه ربات)؟ */
    fun isHuman(seat: Int): Boolean =
        if (netMode) netSeats.getOrNull(seat)?.isHuman == true else seat == UNO_HUMAN

    /** حریف‌ها به ترتیبِ چرخش از دیدِ من — برای چیدن روی صفحه */
    fun opponentSeats(players: Int): List<Int> = (1 until players).map { (mySeat + it) % players }

    /** لابی چندگوشی: دست‌کم یک دوستِ وصل */
    val netCanStart: Boolean get() = netSeats.any { it.kind == UnoNetSeatKind.GUEST && it.connected }
}

/**
 * اونو — سه مدل (کلاسیک، هفت-صفر، بی‌رحم). چندگوشی: میزبان صندلی ۰ است و موتور
 * و ربات‌ها را می‌گرداند؛ هر مهمان فقط عکسِ سانسورشده‌ی خودش را می‌گیرد و
 * فرمان‌هایش را با شناسه‌ی برگ می‌فرستد.
 */
class UnoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UnoUiState())
    val uiState: StateFlow<UnoUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<UnoSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<UnoSoundEvent> = _soundEvents.asSharedFlow()

    private val random = Random(System.nanoTime())
    private var botJob: Job? = null
    private var unoTimerJob: Job? = null
    private var toastJob: Job? = null
    private var animJob: Job? = null
    private var animId = 0

    private val session = NetSession(
        app = application,
        scope = viewModelScope,
        gameId = GAME_ID,
        encode = { m: UnoMessage -> m.encode() },
        decode = ::decodeUnoMessage,
        host = HostSide(),
        guest = GuestSide(),
    )

    /** وضعیت اتصال برای صفحه‌های مشترک شبکه */
    val net: StateFlow<NetUiState> = session.state

    private val isHost: Boolean get() = session.current.isHost
    private val isClient: Boolean get() = session.current.isClient

    init {
        val app = getApplication<Application>()
        _uiState.update {
            it.copy(
                playerName = GamePrefs.getString(app, "uno_name", "") ?: "",
                mode = runCatching { UnoMode.valueOf(GamePrefs.getString(app, "uno_mode", UnoMode.CLASSIC.name) ?: "") }
                    .getOrDefault(UnoMode.CLASSIC),
                players = GamePrefs.getInt(app, "uno_players", 4).coerceIn(2, 4),
                target = GamePrefs.getInt(app, "uno_target", UnoRules.DEFAULT_TARGET)
                    .takeIf { t -> t in UnoRules.TARGETS } ?: UnoRules.DEFAULT_TARGET,
            )
        }
    }

    private fun emit(event: UnoSoundEvent) {
        _soundEvents.tryEmit(event)
    }

    /** تغییر وضعیت + (اگر میزبانیم) عکس تازه برای هر مهمان */
    private inline fun mutate(block: (UnoUiState) -> UnoUiState) {
        _uiState.update(block)
        pushState()
    }

    private fun showToast(message: String) {
        toastJob?.cancel()
        mutate { it.copy(toast = message, toastId = it.toastId + 1) }
        toastJob = viewModelScope.launch {
            delay(2300)
            mutate { it.copy(toast = null) }
        }
    }

    // ------------------------------------------------------------------
    // تنظیمات
    // ------------------------------------------------------------------

    fun setPlayerName(name: String) = _uiState.update { it.copy(playerName = name.take(20)) }

    fun setMode(mode: UnoMode) = _uiState.update { it.copy(mode = mode) }

    fun setPlayers(count: Int) = _uiState.update { it.copy(players = count.coerceIn(2, 4)) }

    fun setTarget(target: Int) = _uiState.update { it.copy(target = target) }

    private fun savePrefs() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setString(app, "uno_name", s.playerName.trim())
        GamePrefs.setString(app, "uno_mode", s.mode.name)
        GamePrefs.setInt(app, "uno_players", s.players)
        GamePrefs.setInt(app, "uno_target", s.target)
    }

    fun startMatch() {
        val s = _uiState.value
        savePrefs()
        Analytics.gameSetup(
            "variant" to s.mode.name.lowercase(),
            "players" to s.players,
            "target" to s.target,
        )
        launchMatch()
    }

    private fun launchMatch() {
        val s = _uiState.value
        val game = UnoEngine.newMatch(UnoSettings(mode = s.mode, players = s.players, target = s.target), random)
        mutate {
            it.copy(
                stage = UnoStage.Playing,
                botNames = if (it.netMode) it.botNames else BOT_NAMES.shuffled(random).take(s.players - 1),
                game = game,
                thinkingSeat = null,
                toast = null,
                unoBubbleSeat = null,
                handAnim = null,
                dealing = true,
                showFinal = false,
            )
        }
        runBots()
    }

    fun playAgain() {
        if (isClient) {
            session.send(UnoMessage.PlayAgain)
            return
        }
        Analytics.gameReplay()
        launchMatch()
    }

    /** برگشت به تنظیمات — در چندگوشی یعنی ترک میز */
    fun backToSetup() {
        botJob?.cancel()
        unoTimerJob?.cancel()
        if (_uiState.value.netMode) session.leave()
        _uiState.update {
            it.copy(
                stage = UnoStage.Setup,
                game = null,
                thinkingSeat = null,
                dealing = false,
                showFinal = false,
                netMode = false,
                netSeats = emptyList(),
                mySeat = UNO_HUMAN,
            )
        }
    }

    // ------------------------------------------------------------------
    // چندگوشی: ورود، میزبانی، پیوستن
    // ------------------------------------------------------------------

    fun chooseNetworkMode() {
        botJob?.cancel()
        session.clearError()
        _uiState.update { it.copy(stage = UnoStage.NetEntry, game = null) }
    }

    fun backFromNetEntry() {
        session.clearError()
        _uiState.update { it.copy(stage = UnoStage.Setup) }
    }

    fun setMyName(name: String) = session.setMyName(name)

    fun setOnline(on: Boolean) {
        session.setOnline(on)
    }

    fun hostGame() {
        savePrefs()
        if (session.current.online) session.hostOnline() else session.hostLan()
    }

    fun openJoin() {
        if (session.current.myName.isBlank()) return
        _uiState.update { it.copy(stage = UnoStage.NetJoin) }
        session.startDiscovery()
    }

    fun joinLan(address: String, port: Int) = session.joinLan(address, port)

    fun joinOnline(code: String) = session.joinOnline(code)

    fun backFromJoin() {
        session.backFromJoin()
        _uiState.update { it.copy(stage = UnoStage.NetEntry, netMode = false, netSeats = emptyList(), mySeat = UNO_HUMAN) }
    }

    fun cancelHosting() {
        botJob?.cancel()
        session.cancelHosting()
        _uiState.update { it.copy(stage = UnoStage.NetEntry, netMode = false, netSeats = emptyList(), mySeat = UNO_HUMAN, game = null) }
    }

    fun resumeOnline() = session.resumeOnline()

    fun discardResume() = session.discardResume()

    fun reconnectOnline() = session.reconnectOnline()

    /** میزبان: شروع بازی چندگوشی — صندلی‌های خالی ربات می‌شوند */
    fun startNetGame() {
        val s = _uiState.value
        if (!isHost || s.stage != UnoStage.NetLobby || !s.netCanStart) return
        val bots = BOT_NAMES.shuffled(random).iterator()
        val seats = s.netSeats.map { seat ->
            if (seat.kind == UnoNetSeatKind.EMPTY) UnoNetSeat(name = bots.next(), kind = UnoNetSeatKind.BOT) else seat
        }
        _uiState.update { it.copy(netSeats = seats) }
        Analytics.gameSetup(
            "variant" to s.mode.name.lowercase(),
            "players" to s.players,
            "humans" to seats.count { it.isHuman },
            "net" to session.analyticsNet,
            "target" to s.target,
        )
        launchMatch()
    }

    private fun snapshotFor(seat: Int): UnoRoomSnapshot {
        val s = _uiState.value
        return UnoRoomSnapshot(
            seats = s.netSeats,
            mode = s.mode,
            players = s.players,
            target = s.target,
            started = s.stage == UnoStage.Playing && s.game != null,
            game = s.game?.redactedFor(seat),
            thinkingSeat = s.thinkingSeat,
            toast = s.toast,
            toastId = s.toastId,
            unoBubbleSeat = s.unoBubbleSeat,
            handAnim = s.handAnim?.let { a -> UnoNetHandAnim(a.id, a.moves.map { listOf(it.first, it.second) }) },
            dealing = s.dealing,
        )
    }

    private fun seatOfGuest(name: String): Int? {
        val seats = _uiState.value.netSeats
        val i = seats.indexOfFirst { it.kind == UnoNetSeatKind.GUEST && it.name.trim() == name.trim() }
        return i.takeIf { it >= 0 }
    }

    private fun pushState() {
        if (!isHost) return
        val guests = _uiState.value.netSeats.filter { it.kind == UnoNetSeatKind.GUEST }.map { it.name }
        session.pushToAll(guests)
    }

    /** مهمان (و میزبان موقع ادامه): عکس میز روی وضعیت خودمان */
    private fun applyRoom(room: UnoRoomSnapshot, asHost: Boolean) {
        val before = _uiState.value
        val mySeat = if (asHost) 0 else {
            val me = session.current.myName.trim()
            room.seats.indexOfFirst { it.kind == UnoNetSeatKind.GUEST && it.name.trim() == me }.coerceAtLeast(0)
        }
        val stage = if (room.started && room.game != null) UnoStage.Playing else UnoStage.NetLobby
        _uiState.update {
            it.copy(
                stage = stage,
                netMode = true,
                netSeats = room.seats,
                mode = room.mode,
                players = room.players,
                target = room.target,
                game = room.game,
                thinkingSeat = room.thinkingSeat,
                toast = room.toast,
                toastId = room.toastId,
                unoBubbleSeat = room.unoBubbleSeat,
                handAnim = room.handAnim?.let { a -> UnoHandAnim(a.id, a.moves.map { m -> m[0] to m[1] }) },
                dealing = room.dealing,
                mySeat = mySeat,
                // مسابقه‌ی تازه: صفحه‌ی برنده‌ی قبلی بسته می‌شود
                showFinal = if (room.game?.matchWinner == null) false else it.showFinal,
            )
        }
        if (asHost) return
        val g = room.game ?: return
        val b = before.game
        // صداهای مهمان از روی تفاوت‌ها
        if (b != null && b.roundNumber == g.roundNumber) {
            if (g.discard.size > b.discard.size) emit(UnoSoundEvent.PLAY)
            val myBefore = b.hands.getOrNull(mySeat)?.size ?: 0
            val myNow = g.hands.getOrNull(mySeat)?.size ?: 0
            if (myNow > myBefore && g.discard.size == b.discard.size) emit(if (b.pendingDraw > 0) UnoSoundEvent.HIT else UnoSoundEvent.DRAW)
            if (room.unoBubbleSeat != null && before.unoBubbleSeat != room.unoBubbleSeat) emit(UnoSoundEvent.UNO_CALL)
            val overNow = g.phase == UnoPhase.ROUND_OVER || g.phase == UnoPhase.MATCH_OVER
            val overBefore = b.phase == UnoPhase.ROUND_OVER || b.phase == UnoPhase.MATCH_OVER
            if (overNow && !overBefore) emit(UnoSoundEvent.ROUND_END)
        }
    }

    /** آنچه میزبان باید جواب بدهد */
    private inner class HostSide : NetSession.HostCallbacks<UnoMessage> {

        override fun acceptJoin(name: String): String? {
            val s = _uiState.value
            if (!s.netMode) return "بازی‌ای در کار نیست"
            if (name.isBlank()) return "اسم خالی است"
            if (name.trim() == session.current.myName.trim()) return "این اسم مالِ میزبانه — یه اسم دیگه انتخاب کن"
            val existing = seatOfGuest(name)
            if (existing != null) {
                mutate { st -> st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == existing) x.copy(connected = true) else x }) }
                return null
            }
            if (s.stage == UnoStage.Playing) return "بازی شروع شده — دفعه‌ی بعد زودتر بیا!"
            val free = s.netSeats.indexOfFirst { it.kind == UnoNetSeatKind.EMPTY }
            if (free < 0) return "میز پره — جا نداره!"
            mutate { st ->
                st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == free) UnoNetSeat(name = name.trim(), kind = UnoNetSeatKind.GUEST) else x })
            }
            return null
        }

        override fun onCommand(name: String, msg: UnoMessage) {
            val seat = seatOfGuest(name) ?: return
            val g = gameOrNull() ?: return
            when (msg) {
                is UnoMessage.Play -> {
                    val card = g.hands.getOrNull(seat)?.firstOrNull { it.id == msg.cardId } ?: return
                    doPlay(seat, card, msg.color)
                }

                UnoMessage.DrawTap -> doDrawTap(seat)
                is UnoMessage.PlayDrawn -> doPlayDrawn(seat, msg.color)
                UnoMessage.KeepDrawn -> doKeepDrawn(seat)
                is UnoMessage.StartColor -> doChooseStartColor(seat, msg.color)
                is UnoMessage.Swap -> doChooseSwap(seat, msg.target)
                UnoMessage.CallUno -> doCallUno(seat)
                UnoMessage.NextRound -> nextRound()
                UnoMessage.PlayAgain -> if (g.phase == UnoPhase.MATCH_OVER) playAgain()
                is UnoMessage.State -> Unit
            }
        }

        override fun onDisconnected(name: String) {
            val seat = seatOfGuest(name) ?: return
            mutate { st -> st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == seat) x.copy(connected = false) else x }) }
        }

        override fun stateFor(name: String): UnoMessage {
            val seat = if (name.isBlank()) -1 else seatOfGuest(name) ?: -1
            return UnoMessage.State(snapshotFor(seat))
        }

        override fun onRoomReady() {
            botJob?.cancel()
            _uiState.update {
                it.copy(
                    stage = UnoStage.NetLobby,
                    netMode = true,
                    netSeats = listOf(UnoNetSeat(name = session.current.myName.trim(), kind = UnoNetSeatKind.HOST)) +
                        List(it.players - 1) { UnoNetSeat() },
                    mySeat = 0,
                    game = null,
                    showFinal = false,
                )
            }
        }

        override fun onResumeHost(stored: StoredOnlineRoom, decoded: UnoMessage?) {
            botJob?.cancel()
            val room = (decoded as? UnoMessage.State)?.room
            if (room == null) {
                _uiState.update {
                    it.copy(
                        stage = UnoStage.NetLobby,
                        netMode = true,
                        netSeats = listOf(UnoNetSeat(name = stored.name, kind = UnoNetSeatKind.HOST)) + List(it.players - 1) { UnoNetSeat() },
                        mySeat = 0,
                        game = null,
                    )
                }
                return
            }
            val seats = room.seats.map { if (it.kind == UnoNetSeatKind.GUEST) it.copy(connected = false) else it }
            applyRoom(room.copy(seats = seats, thinkingSeat = null, dealing = false), asHost = true)
            animId = maxOf(animId, room.handAnim?.id ?: 0)
            if (_uiState.value.game != null) runBots()
        }

        override fun onRoomFailed() {
            _uiState.update { it.copy(stage = UnoStage.NetEntry, netMode = false, game = null) }
        }
    }

    /** مهمان: هرچه میزبان فرستاد، همان حقیقت است */
    private inner class GuestSide : NetSession.GuestCallbacks<UnoMessage> {
        override fun onMessage(msg: UnoMessage) {
            val room = (msg as? UnoMessage.State)?.room ?: return
            applyRoom(room, asHost = false)
        }
    }

    // ------------------------------------------------------------------
    // اعمال انسان (روی این گوشی) — در چندگوشی مهمان فرمان می‌فرستد
    // ------------------------------------------------------------------

    private val mySeat: Int get() = _uiState.value.mySeat

    /** بازی یک برگ از دست (وایلدها با رنگ انتخابی می‌آیند) */
    fun humanPlay(card: UnoCard, chosenColor: UnoColor? = null) {
        if (isClient) {
            session.send(UnoMessage.Play(card.id, chosenColor))
            return
        }
        doPlay(mySeat, card, chosenColor)
    }

    /** لمس دسته: یا کشیدن عادی، یا قبول کل جریمه‌ی انباشته */
    fun humanDrawTap() {
        if (isClient) {
            session.send(UnoMessage.DrawTap)
            return
        }
        doDrawTap(mySeat)
    }

    /** «بازی کن» برای برگ تازه‌کشیده */
    fun humanPlayDrawn(chosenColor: UnoColor? = null) {
        if (isClient) {
            session.send(UnoMessage.PlayDrawn(chosenColor))
            return
        }
        doPlayDrawn(mySeat, chosenColor)
    }

    /** «نگه دار» برای برگ تازه‌کشیده */
    fun humanKeepDrawn() {
        if (isClient) {
            session.send(UnoMessage.KeepDrawn)
            return
        }
        doKeepDrawn(mySeat)
    }

    /** انتخاب رنگ برای برگ شروع وایلد */
    fun humanChooseStartColor(color: UnoColor) {
        if (isClient) {
            session.send(UnoMessage.StartColor(color))
            return
        }
        doChooseStartColor(mySeat, color)
    }

    /** انتخاب هم‌بازی برای تعویض دست بعد از ۷ */
    fun humanChooseSwap(target: Int) {
        if (isClient) {
            session.send(UnoMessage.Swap(target))
            return
        }
        doChooseSwap(mySeat, target)
    }

    /** دکمه‌ی «اونو!» */
    fun humanCallUno() {
        if (isClient) {
            session.send(UnoMessage.CallUno)
            return
        }
        doCallUno(mySeat)
    }

    /** «دست بعدی» از خلاصه‌ی دست */
    fun nextRound() {
        if (isClient) {
            session.send(UnoMessage.NextRound)
            return
        }
        val g = gameOrNull() ?: return
        if (g.phase != UnoPhase.ROUND_OVER) return
        mutate { it.copy(game = UnoEngine.newRound(g, random), dealing = true, handAnim = null) }
        runBots()
    }

    /** بعد از خلاصه‌ی دست آخر: صفحه‌ی برنده (هر گوشی برای خودش) */
    fun showFinal() {
        val g = gameOrNull() ?: return
        if (g.phase != UnoPhase.MATCH_OVER) return
        _uiState.update { it.copy(showFinal = true) }
        emit(if (g.matchWinner == mySeat) UnoSoundEvent.MATCH_WON else UnoSoundEvent.MATCH_LOST)
    }

    // ------------------------------------------------------------------
    // اعمال یک صندلیِ آدم (میزبان یا محلی) — با بررسی نوبت
    // ------------------------------------------------------------------

    private fun doPlay(seat: Int, card: UnoCard, chosenColor: UnoColor?) {
        val g = gameOrNull() ?: return
        if (_uiState.value.dealing || g.turn != seat || g.phase != UnoPhase.PLAYING) return
        if (g.drawnCard != null) return
        val next = runCatching { UnoEngine.playCard(g, seat, card, chosenColor) }.getOrNull() ?: return
        afterPlay(g, next, seat, card)
    }

    private fun doDrawTap(seat: Int) {
        val g = gameOrNull() ?: return
        if (_uiState.value.dealing || g.turn != seat || g.phase != UnoPhase.PLAYING) return
        if (g.pendingDraw > 0) {
            val amount = g.pendingDraw
            val next = runCatching { UnoEngine.resolvePendingDraw(g) }.getOrNull() ?: return
            updateGame(next)
            emit(UnoSoundEvent.HIT)
            showToast(eatToast(seat, amount))
            runBots()
            return
        }
        if (g.drawnCard != null) return
        val next = runCatching { UnoEngine.drawCard(g, seat) }.getOrNull() ?: return
        updateGame(next)
        emit(UnoSoundEvent.DRAW)
        runBots()
    }

    private fun doPlayDrawn(seat: Int, chosenColor: UnoColor?) {
        val g = gameOrNull() ?: return
        val card = g.drawnCard ?: return
        if (g.turn != seat) return
        val next = runCatching { UnoEngine.playDrawn(g, chosenColor) }.getOrNull() ?: return
        afterPlay(g, next, seat, card)
    }

    private fun doKeepDrawn(seat: Int) {
        val g = gameOrNull() ?: return
        if (g.turn != seat || g.drawnCard == null) return
        val next = runCatching { UnoEngine.keepDrawn(g) }.getOrNull() ?: return
        updateGame(next)
        runBots()
    }

    private fun doChooseStartColor(seat: Int, color: UnoColor) {
        val g = gameOrNull() ?: return
        if (g.phase != UnoPhase.CHOOSE_COLOR || g.turn != seat) return
        updateGame(UnoEngine.chooseStartColor(g, color))
        runBots()
    }

    private fun doChooseSwap(seat: Int, target: Int) {
        val g = gameOrNull() ?: return
        if (g.phase != UnoPhase.CHOOSE_SWAP || g.swapSeat != seat) return
        val next = runCatching { UnoEngine.chooseSwap(g, target) }.getOrNull() ?: return
        playHandAnim(listOf(seat to target, target to seat))
        updateGame(next)
        val ui = _uiState.value
        showToast(
            if (seat == ui.mySeat && !ui.netMode) "دستت با ${ui.seatName(target)} عوض شد! 🔄"
            else "${ui.seatName(seat)} دستش رو با ${ui.seatName(target)} عوض کرد! 🔄",
        )
        runBots()
    }

    private fun doCallUno(seat: Int) {
        val g = gameOrNull() ?: return
        if (g.unoPending != seat) return
        unoTimerJob?.cancel()
        updateGame(UnoEngine.callUno(g, seat))
        emit(UnoSoundEvent.UNO_CALL)
        bubble(seat)
    }

    /** متن جریمه‌خوردن — در محلی خطاب به «تو»، در چندگوشی با اسم صندلی */
    private fun eatToast(seat: Int, amount: Int): String {
        val ui = _uiState.value
        return if (!ui.netMode && seat == ui.mySeat) "+${amount.toPersianDigits()} خوردی! 😵"
        else "${ui.seatName(seat)} +${amount.toPersianDigits()} خورد!"
    }

    // ------------------------------------------------------------------
    // جریان مشترک بعد از هر بازی برگ
    // ------------------------------------------------------------------

    private fun afterPlay(before: UnoState, next: UnoState, seat: Int, card: UnoCard) {
        // انیمیشن چرخش دست‌ها برای صفرِ هفت-صفر
        if (before.settings.mode == UnoMode.SEVEN_ZERO &&
            card.kind == UnoKind.NUMBER && card.number == 0 &&
            next.phase == UnoPhase.PLAYING
        ) {
            val n = before.players
            playHandAnim(List(n) { i -> i to before.nextSeat(i) })
            showToast("همه‌ی دست‌ها چرخید! 🔄")
        }
        if (_uiState.value.netMode && card.isWild && next.currentColor != null) {
            showToast("${_uiState.value.seatName(seat)} رنگ رو ${unoColorNameFa(next.currentColor!!)} کرد")
        }
        updateGame(next)
        emit(UnoSoundEvent.PLAY)
        when (next.phase) {
            UnoPhase.ROUND_OVER, UnoPhase.MATCH_OVER -> emit(UnoSoundEvent.ROUND_END)
            else -> Unit
        }
        runBots()
    }

    private fun gameOrNull(): UnoState? = _uiState.value.game

    private fun updateGame(next: UnoState) {
        mutate { it.copy(game = next) }
        armUnoTimer(next)
    }

    /** پنجره‌ی «اونو!»: ربات‌ها فوری می‌گویند؛ آدم‌ها ~۲.۵ ثانیه فرصت دارند */
    private fun armUnoTimer(g: UnoState) {
        val seat = g.unoPending
        if (seat == null) {
            unoTimerJob?.cancel()
            return
        }
        if (!_uiState.value.isHuman(seat)) return // ربات‌ها در حلقه‌ی خودشان اعلام می‌کنند
        if (unoTimerJob?.isActive == true) return
        unoTimerJob = viewModelScope.launch {
            delay(UNO_WINDOW_MS)
            val now = gameOrNull() ?: return@launch
            if (now.unoPending == seat) {
                val caught = runCatching { UnoEngine.penalizeUno(now) }.getOrNull() ?: return@launch
                mutate { it.copy(game = caught) }
                emit(UnoSoundEvent.CAUGHT)
                val ui = _uiState.value
                showToast(if (!ui.netMode || seat == ui.mySeat) "مچت رو گرفتن! +۲ 😱" else "مچ ${ui.seatName(seat)} رو گرفتن! +۲ 😱")
            }
        }
    }

    private fun bubble(seat: Int) {
        mutate { it.copy(unoBubbleSeat = seat) }
        viewModelScope.launch {
            delay(1500)
            mutate { if (it.unoBubbleSeat == seat) it.copy(unoBubbleSeat = null) else it }
        }
    }

    private fun playHandAnim(moves: List<Pair<Int, Int>>) {
        animJob?.cancel()
        val anim = UnoHandAnim(id = ++animId, moves = moves)
        mutate { it.copy(handAnim = anim) }
        animJob = viewModelScope.launch {
            delay(950)
            mutate { if (it.handAnim?.id == anim.id) it.copy(handAnim = null) else it }
        }
    }

    // ------------------------------------------------------------------
    // حلقه‌ی ربات‌ها (فقط میزبان / بازی محلی)
    // ------------------------------------------------------------------

    private fun runBots() {
        if (isClient) return
        botJob?.cancel()
        botJob = viewModelScope.launch {
            try {
                if (_uiState.value.dealing) {
                    delay(1100)
                    mutate { it.copy(dealing = false) }
                }
                while (true) {
                    val g = gameOrNull() ?: return@launch
                    val ui = _uiState.value
                    when {
                        g.phase == UnoPhase.ROUND_OVER || g.phase == UnoPhase.MATCH_OVER -> return@launch

                        // ربات به یک برگ رسیده: همیشه فوری «اونو!» می‌گوید
                        g.unoPending != null && !ui.isHuman(g.unoPending!!) -> {
                            val seat = g.unoPending!!
                            mutate { it.copy(game = UnoEngine.callUno(g, seat)) }
                            emit(UnoSoundEvent.UNO_CALL)
                            bubble(seat)
                        }

                        g.phase == UnoPhase.CHOOSE_COLOR -> {
                            if (ui.isHuman(g.turn)) return@launch
                            think(g.turn)
                            updateGame(UnoEngine.chooseStartColor(g, UnoBot.pickColor(g.hands[g.turn], random)))
                        }

                        g.phase == UnoPhase.CHOOSE_SWAP -> {
                            val seat = g.swapSeat!!
                            if (ui.isHuman(seat)) return@launch
                            think(seat)
                            val target = UnoBot.chooseSwapTarget(g, seat)
                            playHandAnim(listOf(seat to target, target to seat))
                            updateGame(UnoEngine.chooseSwap(g, target))
                            showToast("${ui.seatName(seat)} دستش رو با ${ui.seatName(target)} عوض کرد! 🔄")
                        }

                        g.pendingDraw > 0 && !ui.isHuman(g.turn) -> {
                            think(g.turn)
                            val stack = UnoBot.chooseStack(g, g.turn, random)
                            if (stack != null) {
                                val color = if (stack.isWild) UnoBot.pickColor(g.hands[g.turn], random) else null
                                val next = UnoEngine.playCard(g, g.turn, stack, color)
                                updateGame(next)
                                emit(UnoSoundEvent.PLAY)
                                showToast("${ui.seatName(g.turn)} سوارش کرد: +${next.pendingDraw.toPersianDigits()}! 🔥")
                                if (next.phase != UnoPhase.PLAYING) emit(UnoSoundEvent.ROUND_END)
                            } else {
                                val amount = g.pendingDraw
                                updateGame(UnoEngine.resolvePendingDraw(g))
                                emit(UnoSoundEvent.DRAW)
                                showToast("${ui.seatName(g.turn)} +${amount.toPersianDigits()} خورد!")
                            }
                        }

                        g.pendingDraw > 0 && ui.isHuman(g.turn) -> {
                            // در بی‌رحم اگر برگی برای سوار کردن هست، انتخاب با آدم است
                            if (UnoEngine.legalPlays(g, g.turn).isNotEmpty()) return@launch
                            delay(900)
                            val amount = g.pendingDraw
                            updateGame(UnoEngine.resolvePendingDraw(g))
                            emit(UnoSoundEvent.HIT)
                            showToast(eatToast(g.turn, amount))
                        }

                        g.drawnCard != null -> {
                            if (ui.isHuman(g.turn)) return@launch
                            think(g.turn, short = true)
                            val card = g.drawnCard!!
                            val color = if (card.isWild) UnoBot.pickColor(g.hands[g.turn], random) else null
                            val next = UnoEngine.playDrawn(g, color)
                            afterBotPlay(g, next, g.turn, card)
                        }

                        !ui.isHuman(g.turn) -> {
                            think(g.turn)
                            val card = UnoBot.choosePlay(g, g.turn, random)
                            if (card == null) {
                                updateGame(UnoEngine.drawCard(g, g.turn))
                                emit(UnoSoundEvent.DRAW)
                            } else {
                                val color = if (card.isWild) UnoBot.pickColor(g.hands[g.turn], random) else null
                                val next = UnoEngine.playCard(g, g.turn, card, color)
                                afterBotPlay(g, next, g.turn, card)
                            }
                        }

                        else -> return@launch
                    }
                }
            } finally {
                mutate { it.copy(thinkingSeat = null) }
            }
        }
    }

    /** اثرهای جانبی بازی ربات: صدا، انیمیشن صفر، اعلام رنگ وایلد */
    private fun afterBotPlay(before: UnoState, next: UnoState, seat: Int, card: UnoCard) {
        val ui = _uiState.value
        if (before.settings.mode == UnoMode.SEVEN_ZERO &&
            card.kind == UnoKind.NUMBER && card.number == 0 &&
            next.phase == UnoPhase.PLAYING
        ) {
            playHandAnim(List(before.players) { i -> i to before.nextSeat(i) })
            showToast("همه‌ی دست‌ها چرخید! 🔄")
        }
        if (card.isWild && next.currentColor != null) {
            showToast("${ui.seatName(seat)} رنگ رو ${unoColorNameFa(next.currentColor!!)} کرد")
        }
        updateGame(next)
        emit(UnoSoundEvent.PLAY)
        if (next.phase == UnoPhase.ROUND_OVER || next.phase == UnoPhase.MATCH_OVER) {
            emit(UnoSoundEvent.ROUND_END)
        }
    }

    private suspend fun think(seat: Int, short: Boolean = false) {
        mutate { it.copy(thinkingSeat = seat) }
        delay(if (short) 450 else 600L + random.nextLong(300))
        mutate { it.copy(thinkingSeat = null) }
    }

    /** بسته شدن صفحه یا کشته شدن اپ: شبکه جمع می‌شود ولی اتاقِ اینترنتی ذخیره می‌ماند */
    override fun onCleared() {
        botJob?.cancel()
        unoTimerJob?.cancel()
        session.release()
        super.onCleared()
    }

    companion object {
        const val GAME_ID = "uno"
    }
}

private fun unoColorNameFa(color: UnoColor): String = when (color) {
    UnoColor.RED -> "قرمز"
    UnoColor.YELLOW -> "زرد"
    UnoColor.GREEN -> "سبز"
    UnoColor.BLUE -> "آبی"
}
