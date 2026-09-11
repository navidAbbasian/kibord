package com.navidabbasian.kibord.games.shelem

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.net.NetSession
import com.navidabbasian.kibord.core.net.NetUiState
import com.navidabbasian.kibord.core.net.online.StoredOnlineRoom
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.games.shelem.engine.ShelemBot
import com.navidabbasian.kibord.games.shelem.engine.ShelemEngine
import com.navidabbasian.kibord.games.shelem.engine.ShelemPhase
import com.navidabbasian.kibord.games.shelem.engine.ShelemRules
import com.navidabbasian.kibord.games.shelem.engine.ShelemSettings
import com.navidabbasian.kibord.games.shelem.engine.ShelemState
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

/** صندلی انسان در بازی محلی همیشه ۰ است؛ یارش ۲؛ حریف‌ها ۱ و ۳ (در چندگوشی: [ShelemUiState.mySeat]) */
const val SHELEM_HUMAN = 0

enum class ShelemStage { Setup, NetEntry, NetJoin, NetLobby, Playing }

enum class ShelemSoundEvent { DEAL, BID, PASS, CARD, TRICK_WON, TRICK_LOST, HAND_END, MATCH_WON, MATCH_LOST }

/** اسم‌های ربات‌ها — سه تا از این‌ها تصادفی انتخاب می‌شود */
private val BOT_NAMES = listOf(
    "کامران", "سهراب", "نرگس", "بهرام", "مهتاب", "فرهاد", "شیرین", "داریوش", "پریسا", "کیوان", "لاله", "آرش",
)

data class ShelemUiState(
    val stage: ShelemStage = ShelemStage.Setup,
    val playerName: String = "",
    /** اسم صندلی‌های ۱، ۲، ۳ (به ترتیب) — بازی محلی */
    val botNames: List<String> = listOf("سهراب", "کامران", "نرگس"),
    val target: Int = ShelemRules.DEFAULT_TARGET,
    val shelemBonus: Boolean = true,
    val game: ShelemState? = null,
    /** کارت‌های انتخاب‌شده برای خواباندن (فقط وقتی خودم حاکمم) */
    val selectedDiscards: Set<Card> = emptySet(),
    /** انیمیشن پخش کارت در جریان است؛ ورودی‌ها قفل */
    val dealing: Boolean = false,
    /** حباب اعلام شرط هر صندلی در دور شرط‌بندی: «۱۲۰» یا «پاس» */
    val bidBubbles: Map<Int, String> = emptyMap(),
    /** صندلی رباتی که الان دارد فکر می‌کند (برای نشانگر) */
    val thinkingSeat: Int? = null,
    /** بعد از پایان مسابقه: صفحه‌ی برنده نشان داده شود (بعد از دیدن خلاصه‌ی دست آخر) */
    val showFinal: Boolean = false,
    /** بازی چندگوشی است؟ */
    val netMode: Boolean = false,
    /** صندلی‌های میز چندگوشی (۴ تا؛ میزبان ۰ و یارش ۲) */
    val netSeats: List<ShelemNetSeat> = emptyList(),
    /** صندلی خودم: در محلی ۰، در چندگوشی صندلی‌ای که میزبان داده */
    val mySeat: Int = SHELEM_HUMAN,
) {
    fun seatName(seat: Int): String = when {
        netMode -> netSeats.getOrNull(seat)?.name?.ifBlank { "ربات" } ?: "ربات"
        seat == SHELEM_HUMAN -> playerName.ifBlank { "تو" }
        else -> botNames.getOrElse(seat - 1) { "ربات" }
    }

    /** تیم خودم */
    val myTeam: Int get() = ShelemRules.teamOf(mySeat)

    fun teamName(team: Int): String = if (team == myTeam) "ما" else "اون‌ها"

    /** این صندلی را آدم بازی می‌کند (نه ربات)؟ */
    fun isHuman(seat: Int): Boolean =
        if (netMode) netSeats.getOrNull(seat)?.isHuman == true else seat == SHELEM_HUMAN

    /** لابی چندگوشی: دست‌کم یک دوستِ وصل */
    val netCanStart: Boolean get() = netSeats.any { it.kind == ShelemNetSeatKind.GUEST && it.connected }
}

/**
 * شلم — چندگوشی: میزبان صندلی ۰ است و موتور و ربات‌ها را می‌گرداند؛ هر مهمان فقط
 * عکسِ سانسورشده‌ی خودش را می‌گیرد (دستِ بقیه و ویدو پنهان) و فرمان می‌فرستد.
 */
class ShelemViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ShelemUiState())
    val uiState: StateFlow<ShelemUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<ShelemSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<ShelemSoundEvent> = _soundEvents.asSharedFlow()

    private val random = Random(System.nanoTime())
    private var botJob: Job? = null

    private val session = NetSession(
        app = application,
        scope = viewModelScope,
        gameId = GAME_ID,
        encode = { m: ShelemMessage -> m.encode() },
        decode = ::decodeShelemMessage,
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
                playerName = GamePrefs.getString(app, "shelem_name", "") ?: "",
                target = GamePrefs.getInt(app, "shelem_target", ShelemRules.DEFAULT_TARGET)
                    .takeIf { t -> t in ShelemRules.TARGETS } ?: ShelemRules.DEFAULT_TARGET,
                shelemBonus = GamePrefs.getBool(app, "shelem_bonus", true),
            )
        }
    }

    private fun emit(event: ShelemSoundEvent) {
        _soundEvents.tryEmit(event)
    }

    /** تغییر وضعیت + (اگر میزبانیم) عکس تازه برای هر مهمان */
    private inline fun mutate(block: (ShelemUiState) -> ShelemUiState) {
        _uiState.update(block)
        pushState()
    }

    // ------------------------------------------------------------------
    // تنظیمات
    // ------------------------------------------------------------------

    fun setPlayerName(name: String) {
        _uiState.update { it.copy(playerName = name.take(20)) }
    }

    fun setTarget(target: Int) {
        _uiState.update { it.copy(target = target) }
    }

    fun setShelemBonus(on: Boolean) {
        _uiState.update { it.copy(shelemBonus = on) }
    }

    private fun savePrefs() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setString(app, "shelem_name", s.playerName.trim())
        GamePrefs.setInt(app, "shelem_target", s.target)
        GamePrefs.setBool(app, "shelem_bonus", s.shelemBonus)
    }

    /** شروع مسابقه از صفحه‌ی تنظیمات */
    fun startMatch() {
        val s = _uiState.value
        savePrefs()
        Analytics.gameSetup("target" to s.target, "shelem_bonus" to s.shelemBonus)
        launchMatch()
    }

    private fun launchMatch() {
        val s = _uiState.value
        val names = BOT_NAMES.shuffled(random).take(3)
        val game = ShelemEngine.newMatch(ShelemSettings(targetScore = s.target, shelemBonus = s.shelemBonus), random)
        mutate {
            it.copy(
                stage = ShelemStage.Playing,
                botNames = if (it.netMode) it.botNames else names,
                game = game,
                selectedDiscards = emptySet(),
                dealing = true,
                bidBubbles = emptyMap(),
                thinkingSeat = null,
                showFinal = false,
            )
        }
        emit(ShelemSoundEvent.DEAL)
        runBots()
    }

    /** «دوباره بازی» از صفحه‌ی برنده (در شبکه: مهمان از میزبان می‌خواهد) */
    fun playAgain() {
        if (isClient) {
            session.send(ShelemMessage.PlayAgain)
            return
        }
        Analytics.gameReplay()
        launchMatch()
    }

    /** بازگشت به تنظیمات (ترک مسابقه) — در چندگوشی یعنی ترک میز */
    fun backToSetup() {
        botJob?.cancel()
        if (_uiState.value.netMode) session.leave()
        _uiState.update {
            it.copy(
                stage = ShelemStage.Setup,
                game = null,
                dealing = false,
                thinkingSeat = null,
                showFinal = false,
                netMode = false,
                netSeats = emptyList(),
                mySeat = SHELEM_HUMAN,
                selectedDiscards = emptySet(),
            )
        }
    }

    // ------------------------------------------------------------------
    // چندگوشی: ورود، میزبانی، پیوستن
    // ------------------------------------------------------------------

    fun chooseNetworkMode() {
        botJob?.cancel()
        session.clearError()
        _uiState.update { it.copy(stage = ShelemStage.NetEntry, game = null) }
    }

    fun backFromNetEntry() {
        session.clearError()
        _uiState.update { it.copy(stage = ShelemStage.Setup) }
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
        _uiState.update { it.copy(stage = ShelemStage.NetJoin) }
        session.startDiscovery()
    }

    fun joinLan(address: String, port: Int) = session.joinLan(address, port)

    fun joinOnline(code: String) = session.joinOnline(code)

    fun backFromJoin() {
        session.backFromJoin()
        _uiState.update { it.copy(stage = ShelemStage.NetEntry, netMode = false, netSeats = emptyList(), mySeat = SHELEM_HUMAN) }
    }

    fun cancelHosting() {
        botJob?.cancel()
        session.cancelHosting()
        _uiState.update { it.copy(stage = ShelemStage.NetEntry, netMode = false, netSeats = emptyList(), mySeat = SHELEM_HUMAN, game = null) }
    }

    fun resumeOnline() = session.resumeOnline()

    fun discardResume() = session.discardResume()

    fun reconnectOnline() = session.reconnectOnline()

    /** میزبان: شروع بازی چندگوشی — صندلی‌های خالی ربات می‌شوند */
    fun startNetGame() {
        val s = _uiState.value
        if (!isHost || s.stage != ShelemStage.NetLobby || !s.netCanStart) return
        val bots = BOT_NAMES.shuffled(random).iterator()
        val seats = s.netSeats.map { seat ->
            if (seat.kind == ShelemNetSeatKind.EMPTY) ShelemNetSeat(name = bots.next(), kind = ShelemNetSeatKind.BOT) else seat
        }
        _uiState.update { it.copy(netSeats = seats) }
        Analytics.gameSetup(
            "humans" to seats.count { it.isHuman },
            "net" to session.analyticsNet,
            "target" to s.target,
            "shelem_bonus" to s.shelemBonus,
        )
        launchMatch()
    }

    private fun snapshotFor(seat: Int): ShelemRoomSnapshot {
        val s = _uiState.value
        return ShelemRoomSnapshot(
            seats = s.netSeats,
            target = s.target,
            shelemBonus = s.shelemBonus,
            started = s.stage == ShelemStage.Playing && s.game != null,
            game = s.game?.redactedFor(seat),
            dealing = s.dealing,
            bidBubbles = s.bidBubbles,
            thinkingSeat = s.thinkingSeat,
        )
    }

    private fun seatOfGuest(name: String): Int? {
        val seats = _uiState.value.netSeats
        val i = seats.indexOfFirst { it.kind == ShelemNetSeatKind.GUEST && it.name.trim() == name.trim() }
        return i.takeIf { it >= 0 }
    }

    private fun pushState() {
        if (!isHost) return
        val guests = _uiState.value.netSeats.filter { it.kind == ShelemNetSeatKind.GUEST }.map { it.name }
        session.pushToAll(guests)
    }

    /** مهمان (و میزبان موقع ادامه): عکس میز روی وضعیت خودمان */
    private fun applyRoom(room: ShelemRoomSnapshot, asHost: Boolean) {
        val before = _uiState.value
        val mySeat = if (asHost) 0 else {
            val me = session.current.myName.trim()
            room.seats.indexOfFirst { it.kind == ShelemNetSeatKind.GUEST && it.name.trim() == me }.coerceAtLeast(0)
        }
        val stage = if (room.started && room.game != null) ShelemStage.Playing else ShelemStage.NetLobby
        _uiState.update {
            it.copy(
                stage = stage,
                netMode = true,
                netSeats = room.seats,
                target = room.target,
                shelemBonus = room.shelemBonus,
                game = room.game,
                dealing = room.dealing,
                bidBubbles = room.bidBubbles,
                thinkingSeat = room.thinkingSeat,
                mySeat = mySeat,
                // انتخاب خواباندن فقط تا وقتی که هنوز حاکمم و در فاز خواباندنم
                selectedDiscards = if (room.game?.phase == ShelemPhase.DISCARDING && room.game.declarer == mySeat) it.selectedDiscards else emptySet(),
                showFinal = if (room.game?.matchWinner == null) false else it.showFinal,
            )
        }
        if (asHost) return
        val g = room.game ?: return
        val b = before.game
        if (b == null || g.handNumber != b.handNumber) {
            if (room.dealing) emit(ShelemSoundEvent.DEAL)
            return
        }
        // صداهای مهمان از روی تفاوت‌ها
        val newBubbles = room.bidBubbles.filterKeys { it !in before.bidBubbles }
        if (newBubbles.isNotEmpty()) emit(if (newBubbles.values.any { it == "پاس" }) ShelemSoundEvent.PASS else ShelemSoundEvent.BID)
        if (g.trick.size > b.trick.size) emit(ShelemSoundEvent.CARD)
        if (b.phase == ShelemPhase.DISCARDING && g.phase == ShelemPhase.TRUMP) emit(ShelemSoundEvent.CARD)
        if (b.phase == ShelemPhase.TRUMP && g.phase == ShelemPhase.PLAYING) emit(ShelemSoundEvent.BID)
        if (g.tricksTaken != b.tricksTaken) {
            val myTeam = ShelemRules.teamOf(mySeat)
            emit(if (g.tricksTaken[myTeam] > b.tricksTaken[myTeam]) ShelemSoundEvent.TRICK_WON else ShelemSoundEvent.TRICK_LOST)
        }
        val overNow = g.phase == ShelemPhase.HAND_OVER || g.phase == ShelemPhase.MATCH_OVER
        val overBefore = b.phase == ShelemPhase.HAND_OVER || b.phase == ShelemPhase.MATCH_OVER
        if (overNow && !overBefore) emit(ShelemSoundEvent.HAND_END)
    }

    /** آنچه میزبان باید جواب بدهد */
    private inner class HostSide : NetSession.HostCallbacks<ShelemMessage> {

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
            if (s.stage != ShelemStage.NetLobby) return "بازی شروع شده — دفعه‌ی بعد زودتر بیا!"
            val free = s.netSeats.indexOfFirst { it.kind == ShelemNetSeatKind.EMPTY }
            if (free < 0) return "میز پره — شلم چهار نفره‌ست!"
            mutate { st ->
                st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == free) ShelemNetSeat(name = name.trim(), kind = ShelemNetSeatKind.GUEST) else x })
            }
            return null
        }

        override fun onCommand(name: String, msg: ShelemMessage) {
            val seat = seatOfGuest(name) ?: return
            val g = _uiState.value.game ?: return
            when (msg) {
                is ShelemMessage.Bid -> doBid(seat, msg.amount)
                ShelemMessage.Pass -> doPass(seat)
                is ShelemMessage.Discard -> doDiscard(seat, msg.cards)
                is ShelemMessage.ChooseTrump -> doChooseTrump(seat, msg.suit)
                is ShelemMessage.Play -> doPlay(seat, msg.card)
                ShelemMessage.NextHand -> nextHand()
                ShelemMessage.PlayAgain -> if (g.phase == ShelemPhase.MATCH_OVER) playAgain()
                is ShelemMessage.State -> Unit
            }
        }

        override fun onDisconnected(name: String) {
            val seat = seatOfGuest(name) ?: return
            mutate { st -> st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == seat) x.copy(connected = false) else x }) }
        }

        override fun stateFor(name: String): ShelemMessage {
            val seat = if (name.isBlank()) -1 else seatOfGuest(name) ?: -1
            return ShelemMessage.State(snapshotFor(seat))
        }

        override fun onRoomReady() {
            botJob?.cancel()
            _uiState.update {
                it.copy(
                    stage = ShelemStage.NetLobby,
                    netMode = true,
                    netSeats = listOf(ShelemNetSeat(name = session.current.myName.trim(), kind = ShelemNetSeatKind.HOST)) + List(3) { ShelemNetSeat() },
                    mySeat = 0,
                    game = null,
                    showFinal = false,
                )
            }
        }

        override fun onResumeHost(stored: StoredOnlineRoom, decoded: ShelemMessage?) {
            botJob?.cancel()
            val room = (decoded as? ShelemMessage.State)?.room
            if (room == null) {
                _uiState.update {
                    it.copy(
                        stage = ShelemStage.NetLobby,
                        netMode = true,
                        netSeats = listOf(ShelemNetSeat(name = stored.name, kind = ShelemNetSeatKind.HOST)) + List(3) { ShelemNetSeat() },
                        mySeat = 0,
                        game = null,
                    )
                }
                return
            }
            val seats = room.seats.map { if (it.kind == ShelemNetSeatKind.GUEST) it.copy(connected = false) else it }
            applyRoom(room.copy(seats = seats, thinkingSeat = null, dealing = false), asHost = true)
            if (_uiState.value.game != null) runBots()
        }

        override fun onRoomFailed() {
            _uiState.update { it.copy(stage = ShelemStage.NetEntry, netMode = false, game = null) }
        }
    }

    /** مهمان: هرچه میزبان فرستاد، همان حقیقت است */
    private inner class GuestSide : NetSession.GuestCallbacks<ShelemMessage> {
        override fun onMessage(msg: ShelemMessage) {
            val room = (msg as? ShelemMessage.State)?.room ?: return
            applyRoom(room, asHost = false)
        }
    }

    // ------------------------------------------------------------------
    // اعمال انسان (روی این گوشی) — در چندگوشی مهمان فرمان می‌فرستد
    // ------------------------------------------------------------------

    private val mySeat: Int get() = _uiState.value.mySeat

    fun humanBid(amount: Int) {
        if (isClient) {
            session.send(ShelemMessage.Bid(amount))
            return
        }
        doBid(mySeat, amount)
    }

    fun humanPass() {
        if (isClient) {
            session.send(ShelemMessage.Pass)
            return
        }
        doPass(mySeat)
    }

    fun toggleDiscard(card: Card) {
        _uiState.update { s ->
            val g = s.game ?: return@update s
            if (g.phase != ShelemPhase.DISCARDING || g.declarer != s.mySeat) return@update s
            val sel = s.selectedDiscards
            val next = when {
                card in sel -> sel - card
                sel.size >= ShelemRules.KITTY_SIZE -> sel
                else -> sel + card
            }
            s.copy(selectedDiscards = next)
        }
    }

    fun confirmDiscard() {
        val s = _uiState.value
        if (s.selectedDiscards.size != ShelemRules.KITTY_SIZE) return
        val cards = s.selectedDiscards.toList()
        if (isClient) {
            session.send(ShelemMessage.Discard(cards))
            _uiState.update { it.copy(selectedDiscards = emptySet()) }
            return
        }
        doDiscard(mySeat, cards)
    }

    fun humanChooseTrump(suit: Suit) {
        if (isClient) {
            session.send(ShelemMessage.ChooseTrump(suit))
            return
        }
        doChooseTrump(mySeat, suit)
    }

    fun humanPlay(card: Card) {
        if (isClient) {
            session.send(ShelemMessage.Play(card))
            return
        }
        doPlay(mySeat, card)
    }

    /** دست بعدی بعد از دیدن خلاصه */
    fun nextHand() {
        if (isClient) {
            session.send(ShelemMessage.NextHand)
            return
        }
        val g = _uiState.value.game ?: return
        if (g.phase != ShelemPhase.HAND_OVER) return
        val next = ShelemEngine.nextHand(g, random)
        mutate { it.copy(game = next, dealing = true, bidBubbles = emptyMap(), selectedDiscards = emptySet()) }
        emit(ShelemSoundEvent.DEAL)
        runBots()
    }

    /** بعد از خلاصه‌ی دست آخر: برو به صفحه‌ی برنده (هر گوشی برای خودش) */
    fun showFinal() {
        val g = _uiState.value.game ?: return
        if (g.phase != ShelemPhase.MATCH_OVER) return
        _uiState.update { it.copy(showFinal = true) }
        emit(if (g.matchWinner == _uiState.value.myTeam) ShelemSoundEvent.MATCH_WON else ShelemSoundEvent.MATCH_LOST)
    }

    // ------------------------------------------------------------------
    // اعمال یک صندلیِ آدم (میزبان یا محلی) — با بررسی نوبت
    // ------------------------------------------------------------------

    private fun doBid(seat: Int, amount: Int) {
        val g = _uiState.value.game ?: return
        if (_uiState.value.dealing || g.phase != ShelemPhase.BIDDING || g.bidTurn != seat) return
        if (amount !in ShelemEngine.availableBids(g, seat)) return
        applyBid(seat, amount)
        runBots()
    }

    private fun doPass(seat: Int) {
        val g = _uiState.value.game ?: return
        if (_uiState.value.dealing || g.phase != ShelemPhase.BIDDING || g.bidTurn != seat) return
        if (!ShelemEngine.canPass(g, seat)) return
        applyPass(seat)
        runBots()
    }

    private fun doDiscard(seat: Int, cards: List<Card>) {
        val g = _uiState.value.game ?: return
        if (g.phase != ShelemPhase.DISCARDING || g.declarer != seat) return
        if (cards.size != ShelemRules.KITTY_SIZE || cards.any { it !in g.hands[seat] }) return
        val next = runCatching { ShelemEngine.discard(g, cards) }.getOrNull() ?: return
        mutate { it.copy(game = next, selectedDiscards = emptySet()) }
        emit(ShelemSoundEvent.CARD)
    }

    private fun doChooseTrump(seat: Int, suit: Suit) {
        val g = _uiState.value.game ?: return
        if (g.phase != ShelemPhase.TRUMP || g.declarer != seat) return
        val next = runCatching { ShelemEngine.chooseTrump(g, suit) }.getOrNull() ?: return
        mutate { it.copy(game = next) }
        emit(ShelemSoundEvent.BID)
        runBots()
    }

    private fun doPlay(seat: Int, card: Card) {
        val g = _uiState.value.game ?: return
        if (_uiState.value.dealing || g.phase != ShelemPhase.PLAYING || g.turn != seat || g.trickWinner != null) return
        if (card !in ShelemEngine.legalPlaysFor(g, seat)) return
        val next = runCatching { ShelemEngine.play(g, seat, card) }.getOrNull() ?: return
        mutate { it.copy(game = next) }
        emit(ShelemSoundEvent.CARD)
        runBots()
    }

    // ------------------------------------------------------------------
    // حرکت‌های مشترک
    // ------------------------------------------------------------------

    private fun applyBid(seat: Int, amount: Int) {
        val g = _uiState.value.game ?: return
        val next = runCatching { ShelemEngine.bid(g, seat, amount) }.getOrNull() ?: return
        mutate { it.copy(game = next, bidBubbles = it.bidBubbles + (seat to amount.toString())) }
        emit(ShelemSoundEvent.BID)
    }

    private fun applyPass(seat: Int) {
        val g = _uiState.value.game ?: return
        val next = runCatching { ShelemEngine.pass(g, seat) }.getOrNull() ?: return
        mutate { it.copy(game = next, bidBubbles = it.bidBubbles + (seat to "پاس")) }
        emit(ShelemSoundEvent.PASS)
    }

    // ------------------------------------------------------------------
    // ربات‌ها — یک حلقه‌ی واحد که تا رسیدن نوبت یک آدم جلو می‌رود (فقط میزبان / محلی)
    // ------------------------------------------------------------------

    private fun runBots() {
        if (isClient) return
        botJob?.cancel()
        botJob = viewModelScope.launch {
            try {
                if (_uiState.value.dealing) {
                    delay(1400)
                    mutate { it.copy(dealing = false) }
                }
                while (true) {
                    val g = _uiState.value.game ?: return@launch
                    val ui = _uiState.value
                    when {
                        g.phase == ShelemPhase.BIDDING && !ui.isHuman(g.bidTurn) -> {
                            think(g.bidTurn, 750)
                            val amount = ShelemBot.decideBid(g, g.bidTurn, random)
                            if (amount == null) applyPass(g.bidTurn) else applyBid(g.bidTurn, amount)
                        }

                        g.phase == ShelemPhase.DISCARDING && !ui.isHuman(g.declarer!!) -> {
                            val d = g.declarer!!
                            think(d, 1100)
                            val trump = ShelemBot.chooseTrump(g.hands[d])
                            val discards = ShelemBot.chooseDiscards(g.hands[d], trump)
                            val next = runCatching { ShelemEngine.discard(g, discards) }.getOrNull() ?: return@launch
                            mutate { it.copy(game = next) }
                            emit(ShelemSoundEvent.CARD)
                        }

                        g.phase == ShelemPhase.TRUMP && !ui.isHuman(g.declarer!!) -> {
                            val d = g.declarer!!
                            think(d, 700)
                            val trump = ShelemBot.chooseTrump(g.hands[d])
                            val next = runCatching { ShelemEngine.chooseTrump(g, trump) }.getOrNull() ?: return@launch
                            mutate { it.copy(game = next, bidBubbles = emptyMap()) }
                            emit(ShelemSoundEvent.BID)
                        }

                        g.phase == ShelemPhase.PLAYING && g.trickWinner != null -> {
                            // کارت‌ها کمی روی میز بمانند تا همه ببینند کی برد
                            delay(1150)
                            val next = runCatching { ShelemEngine.collectTrick(g) }.getOrNull() ?: return@launch
                            mutate { it.copy(game = next) }
                            val won = ShelemRules.teamOf(g.trickWinner) == ui.myTeam
                            emit(if (won) ShelemSoundEvent.TRICK_WON else ShelemSoundEvent.TRICK_LOST)
                            if (next.phase == ShelemPhase.HAND_OVER || next.phase == ShelemPhase.MATCH_OVER) {
                                delay(350)
                                emit(ShelemSoundEvent.HAND_END)
                            }
                        }

                        g.phase == ShelemPhase.PLAYING && !ui.isHuman(g.turn) -> {
                            think(g.turn, 700)
                            val card = ShelemBot.choosePlay(g, g.turn, random)
                            val next = runCatching { ShelemEngine.play(g, g.turn, card) }.getOrNull() ?: return@launch
                            mutate { it.copy(game = next) }
                            emit(ShelemSoundEvent.CARD)
                        }

                        else -> {
                            // نوبت یک آدم یا پایان دست؛ حباب‌های شرط بعد از شروع بازی پاک می‌شوند
                            if (g.phase == ShelemPhase.PLAYING && _uiState.value.bidBubbles.isNotEmpty()) {
                                mutate { it.copy(bidBubbles = emptyMap()) }
                            }
                            return@launch
                        }
                    }
                }
            } finally {
                mutate { it.copy(thinkingSeat = null) }
            }
        }
    }

    private suspend fun think(seat: Int, ms: Long) {
        mutate { it.copy(thinkingSeat = seat) }
        delay(ms)
        mutate { it.copy(thinkingSeat = null) }
    }

    /** بسته شدن صفحه یا کشته شدن اپ: شبکه جمع می‌شود ولی اتاقِ اینترنتی ذخیره می‌ماند */
    override fun onCleared() {
        botJob?.cancel()
        session.release()
        super.onCleared()
    }

    companion object {
        const val GAME_ID = "shelem"
    }
}
