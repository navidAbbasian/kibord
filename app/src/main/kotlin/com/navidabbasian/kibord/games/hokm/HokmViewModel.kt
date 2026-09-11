package com.navidabbasian.kibord.games.hokm

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
import com.navidabbasian.kibord.games.hokm.engine.AceDeal
import com.navidabbasian.kibord.games.hokm.engine.HokmBot
import com.navidabbasian.kibord.games.hokm.engine.HokmPhase
import com.navidabbasian.kibord.games.hokm.engine.HokmRules
import com.navidabbasian.kibord.games.hokm.engine.HokmState
import com.navidabbasian.kibord.games.hokm.engine.HokmVariant
import com.navidabbasian.kibord.games.hokm.engine.MordabadiRules
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
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** صفحه‌های بازی حکم */
@Serializable
enum class HokmStage { Setup, NetEntry, NetJoin, NetLobby, AceDeal, Playing, MatchOver }

/** رویدادهای صوتی که رابط کاربری به صدا ترجمه می‌کند */
enum class HokmSoundEvent { CARD, TRUMP, TRICK_WON, TRICK_LOST, HAND_WON, HAND_LOST, MATCH_OVER, YOUR_TURN }

/** مردابادی: بدهکارِ انسانی باید به جای خالی که ندارد یک کارت دلخواه بدهد */
@Serializable
data class DebtorPick(val collector: Int, val debtor: Int, val suit: Suit, val trumpDemand: Boolean)

/**
 * مردابادی: انیمیشن یک تبادلِ وصول — دو کارت بین دو صندلی پرواز می‌کنند.
 * میزبان هر دو رو را نگه می‌دارد؛ رابط فقط رویِ سمتِ خودِ بیننده را نشان می‌دهد
 * و عکسِ فرستاده‌شده برای هر مهمان همان‌طور سانسور می‌شود تا چیزی لو نرود.
 */
@Serializable
data class ExchangeFx(
    val collector: Int,
    val debtor: Int,
    /** کارتی که طلبکار می‌دهد */
    val collectorFace: Card?,
    /** کارتی که بدهکار می‌دهد */
    val debtorFace: Card?,
    val id: Long,
)

/** وضعیت رابط کاربری حکم */
data class HokmUiState(
    val stage: HokmStage = HokmStage.Setup,
    val variant: HokmVariant = HokmVariant.FOUR,
    val target: Int = 7,
    /** مردابادی: سقف بدهی که به حذف می‌رسد */
    val debtLimit: Int = 17,
    val playerName: String = "",
    val game: HokmState? = null,
    /** آس‌کِشی برای حاکم اول و تعداد کارت‌های رو‌شده تا این لحظه */
    val aceDeal: AceDeal? = null,
    val aceRevealed: Int = 0,
    /** کارت‌های میز دارند به سمت برنده جمع می‌شوند */
    val sweeping: Boolean = false,
    /** پیام گذرای وسط میز (مثلاً اعلام حکم یا وصول طلب) */
    val notice: String? = null,
    /** دوئل پایانی مردابادی: نگاشتِ صندلی دوئل → صندلی اصلی */
    val duelSeats: List<Int>? = null,
    /** وضعیت نهایی مردابادی، منجمد هنگام شروع دوئل (برای صفحه‌ی برنده) */
    val mordabadiGame: HokmState? = null,
    /** درخواست کارت از بدهکار انسانی که خال را ندارد */
    val debtorPick: DebtorPick? = null,
    /** مردابادی: بنر «وصول طلب‌ها» ابتدای فاز وصول */
    val collectionBanner: Boolean = false,
    /** مردابادی: تبادلِ در حال پخش (کارت‌های در حال پرواز) */
    val exchangeFx: ExchangeFx? = null,
    /** مردابادی: کارتی که همین الان به دست بازیکن رسیده — چند لحظه برجسته می‌ماند */
    val receivedCard: Card? = null,
    /** بازی چندگوشی است؟ */
    val netMode: Boolean = false,
    /** صندلی‌های میز چندگوشی (اندازه = تعداد بازیکن) */
    val netSeats: List<HokmNetSeat> = emptyList(),
    /** صندلی اصلیِ خودم: در محلی ۰، در چندگوشی صندلی‌ای که میزبان داده */
    val mySeat: Int = 0,
) {
    /** اسم‌های صندلی‌های اصلی */
    val names: List<String>
        get() {
            if (netMode) return netSeats.map { it.name.ifBlank { "ربات" } }
            val me = playerName.trim().ifBlank { "تو" }
            return (listOf(me) + BOT_NAMES).take(variant.playerCount)
        }

    /** اسم صندلیِ وضعیتِ در حال نمایش؛ در دوئل صندلی‌ها به بازمانده‌ها نگاشت می‌شوند */
    fun nameOf(seat: Int): String {
        val mapped = duelSeats?.getOrNull(seat) ?: seat
        return names.getOrElse(mapped) { "؟" }
    }

    /** اسم صندلیِ اصلی مردابادی (بدون نگاشتِ دوئل) */
    fun mordabadiNameOf(seat: Int): String = names.getOrElse(seat) { "؟" }

    /** اسم تیم از دید بازیکن: در چهار نفره «ما/اون‌ها»، وگرنه اسم بازیکن */
    fun teamName(team: Int): String = when (variant) {
        HokmVariant.FOUR -> if (team == myTeam) "ما" else "اون‌ها"
        else -> nameOf(team)
    }

    /** تیم خودم (صندلی اصلی) */
    val myTeam: Int get() = variant.teamOf(mySeat.coerceIn(0, variant.playerCount - 1))

    val humanTeam: Int get() = myTeam

    /** صندلی خودم در وضعیتِ در حال نمایش — منفی یعنی بازی نمی‌کنم (دوئلِ بقیه) */
    val mySeatInGame: Int get() = duelSeats?.indexOf(mySeat) ?: mySeat

    val humanSeatInGame: Int get() = mySeatInGame

    /** صندلی اصلی را آدم بازی می‌کند (نه ربات)؟ */
    fun isHumanBase(seat: Int): Boolean =
        if (netMode) netSeats.getOrNull(seat)?.isHuman == true else seat == 0

    /** صندلیِ وضعیتِ در حال نمایش (با نگاشت دوئل) را آدم بازی می‌کند؟ */
    fun isHumanInGame(gameSeat: Int): Boolean = isHumanBase(duelSeats?.getOrNull(gameSeat) ?: gameSeat)

    /** صندلی اصلی → صندلی در وضعیت جاری (منفی = در دوئل نیست) */
    fun gameSeatOf(baseSeat: Int): Int = duelSeats?.indexOf(baseSeat) ?: baseSeat

    /** لابی چندگوشی: دست‌کم یک دوستِ وصل */
    val netCanStart: Boolean get() = netSeats.any { it.kind == HokmNetSeatKind.GUEST && it.connected }

    companion object {
        val BOT_NAMES = listOf("رضا", "سارا", "نیما")
    }
}

/**
 * موتورگردان حکم: وضعیت بازی، نوبت ربات‌ها (با تأخیر کوتاه)، جمع‌کردن میز،
 * فاز وصول مردابادی و پخش کارت — همه در viewModelScope تا چرخش صفحه چیزی را نخورد.
 *
 * چندگوشی: میزبان صندلی ۰ است و موتور و ربات‌ها را می‌گرداند؛ هر مهمان فقط عکسِ
 * سانسورشده‌ی خودش را می‌گیرد (دستِ بقیه فقط تعداد) و فرمان می‌فرستد.
 */
class HokmViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HokmUiState())
    val uiState: StateFlow<HokmUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<HokmSoundEvent>(extraBufferCapacity = 8)
    val soundEvents: SharedFlow<HokmSoundEvent> = _soundEvents.asSharedFlow()

    private val random = Random.Default
    private var driver: Job? = null
    private var noticeJob: Job? = null
    private var fxJob: Job? = null

    /** بنر «وصول طلب‌ها» فقط یک بار در ابتدای هر فاز وصول نشان داده می‌شود */
    private var collectionIntroDone = false

    /** لحظه‌ی آخرین تبادل وصول — برای فاصله‌ی نفس‌گیر بین قدم‌ها */
    private var lastExchangeAt = 0L

    private val session = NetSession(
        app = application,
        scope = viewModelScope,
        gameId = GAME_ID,
        encode = { m: HokmMessage -> m.encode() },
        decode = ::decodeHokmMessage,
        host = HostSide(),
        guest = GuestSide(),
    )

    /** وضعیت اتصال برای صفحه‌های مشترک شبکه */
    val net: StateFlow<NetUiState> = session.state

    private val isHost: Boolean get() = session.current.isHost
    private val isClient: Boolean get() = session.current.isClient

    init {
        val app = getApplication<Application>()
        val variant = GamePrefs.getString(app, KEY_VARIANT)
            ?.let { v -> HokmVariant.entries.firstOrNull { it.name == v } } ?: HokmVariant.FOUR
        _uiState.value = HokmUiState(
            variant = variant,
            target = GamePrefs.getInt(app, KEY_TARGET, 7).takeIf { it in listOf(3, 5, 7) } ?: 7,
            debtLimit = GamePrefs.getInt(app, KEY_DEBT_LIMIT, 17)
                .takeIf { it in MordabadiRules.DEBT_LIMITS } ?: 17,
            playerName = GamePrefs.getString(app, KEY_NAME, "") ?: "",
        )
    }

    private fun emit(event: HokmSoundEvent) {
        viewModelScope.launch { _soundEvents.emit(event) }
    }

    /** تغییر وضعیت + (اگر میزبانیم) عکس تازه برای هر مهمان */
    private inline fun mutate(block: (HokmUiState) -> HokmUiState) {
        _uiState.update(block)
        pushState()
    }

    // ---------- تنظیمات ----------

    fun setPlayerName(name: String) {
        _uiState.update { it.copy(playerName = name.take(14)) }
    }

    fun setVariant(variant: HokmVariant) {
        _uiState.update { it.copy(variant = variant) }
    }

    fun setTarget(target: Int) {
        _uiState.update { it.copy(target = target) }
    }

    fun setDebtLimit(limit: Int) {
        if (limit !in MordabadiRules.DEBT_LIMITS) return
        _uiState.update { it.copy(debtLimit = limit) }
    }

    private fun savePrefs() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setString(app, KEY_NAME, s.playerName.trim())
        GamePrefs.setString(app, KEY_VARIANT, s.variant.name)
        GamePrefs.setInt(app, KEY_TARGET, s.target)
        GamePrefs.setInt(app, KEY_DEBT_LIMIT, s.debtLimit)
    }

    /** شروع مسابقه: تنظیمات ذخیره و بسته به روش، آس‌کِشی یا مردابادی آغاز می‌شود */
    fun startMatch() {
        val s = _uiState.value
        savePrefs()
        if (s.variant == HokmVariant.THREE) {
            Analytics.gameSetup("variant" to "mordabadi", "debt_limit" to s.debtLimit)
            beginMordabadi()
        } else {
            Analytics.gameSetup("variant" to s.variant.analyticsName, "target" to s.target)
            beginAceDeal()
        }
    }

    /** مردابادی: بدون آس‌کِشی — سهمیه‌ها تصادفی، صاحبِ ۹ حاکم و یک دو بیرون */
    private fun beginMordabadi() {
        driver?.cancel()
        fxJob?.cancel()
        collectionIntroDone = false
        lastExchangeAt = 0L
        val s = _uiState.value
        val match = MordabadiRules.newMatch(s.debtLimit, random)
        val hand = HokmRules.startHand(match, random)
        mutate {
            it.copy(
                stage = HokmStage.Playing,
                game = hand,
                aceDeal = null,
                aceRevealed = 0,
                sweeping = false,
                notice = null,
                duelSeats = null,
                mordabadiGame = null,
                debtorPick = null,
                collectionBanner = false,
                exchangeFx = null,
                receivedCard = null,
            )
        }
        val removed = match.removedCard
        if (removed != null) showNotice("دوِ ${removed.suit.persian} از بازی بیرونه! 🃏", 2800)
        drive()
    }

    private fun beginAceDeal() {
        driver?.cancel()
        val s = _uiState.value
        val deal = HokmRules.firstHakemDeal(s.variant, random)
        mutate {
            it.copy(
                stage = HokmStage.AceDeal,
                aceDeal = deal,
                aceRevealed = 0,
                game = null,
                sweeping = false,
                notice = null,
                duelSeats = null,
                mordabadiGame = null,
                debtorPick = null,
            )
        }
        runAceDeal()
    }

    private fun runAceDeal() {
        driver = viewModelScope.launch {
            while (true) {
                val st = _uiState.value
                val d = st.aceDeal ?: return@launch
                if (st.aceRevealed >= d.cards.size) break
                delay(if (st.aceRevealed == 0) 500 else 170)
                mutate { it.copy(aceRevealed = it.aceRevealed + 1) }
                emit(HokmSoundEvent.CARD)
            }
            delay(1600)
            startPlaying()
        }
    }

    /** رد کردن انیمیشن آس‌کِشی (در شبکه: مهمان از میزبان می‌خواهد) */
    fun skipAceDeal() {
        if (isClient) {
            session.send(HokmMessage.SkipAceDeal)
            return
        }
        val st = _uiState.value
        val d = st.aceDeal ?: return
        if (st.aceRevealed >= d.cards.size) return
        driver?.cancel()
        mutate { it.copy(aceRevealed = d.cards.size) }
        driver = viewModelScope.launch {
            delay(1200)
            startPlaying()
        }
    }

    private fun startPlaying() {
        val s = _uiState.value
        val hakem = s.aceDeal?.hakem ?: 0
        val match = HokmRules.newMatch(s.variant, s.target, hakem)
        val hand = HokmRules.startHand(match, random)
        mutate { it.copy(stage = HokmStage.Playing, game = hand, sweeping = false, notice = null) }
        drive()
    }

    // ---------- چندگوشی: ورود، میزبانی، پیوستن ----------

    fun chooseNetworkMode() {
        driver?.cancel()
        session.clearError()
        _uiState.update { it.copy(stage = HokmStage.NetEntry, game = null) }
    }

    fun backFromNetEntry() {
        session.clearError()
        _uiState.update { it.copy(stage = HokmStage.Setup) }
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
        _uiState.update { it.copy(stage = HokmStage.NetJoin) }
        session.startDiscovery()
    }

    fun joinLan(address: String, port: Int) = session.joinLan(address, port)

    fun joinOnline(code: String) = session.joinOnline(code)

    fun backFromJoin() {
        session.backFromJoin()
        _uiState.update { it.copy(stage = HokmStage.NetEntry, netMode = false, netSeats = emptyList(), mySeat = 0) }
    }

    fun cancelHosting() {
        driver?.cancel()
        session.cancelHosting()
        _uiState.update { it.copy(stage = HokmStage.NetEntry, netMode = false, netSeats = emptyList(), mySeat = 0, game = null) }
    }

    fun resumeOnline() = session.resumeOnline()

    fun discardResume() = session.discardResume()

    fun reconnectOnline() = session.reconnectOnline()

    /** میزبان: شروع بازی چندگوشی — صندلی‌های خالی ربات می‌شوند */
    fun startNetGame() {
        val s = _uiState.value
        if (!isHost || s.stage != HokmStage.NetLobby || !s.netCanStart) return
        val bots = HokmUiState.BOT_NAMES.iterator()
        val seats = s.netSeats.map { seat ->
            if (seat.kind == HokmNetSeatKind.EMPTY) HokmNetSeat(name = bots.next(), kind = HokmNetSeatKind.BOT) else seat
        }
        _uiState.update { it.copy(netSeats = seats) }
        Analytics.gameSetup(
            "variant" to if (s.variant == HokmVariant.THREE) "mordabadi" else s.variant.analyticsName,
            "humans" to seats.count { it.isHuman },
            "net" to session.analyticsNet,
            "target" to s.target,
        )
        if (s.variant == HokmVariant.THREE) beginMordabadi() else beginAceDeal()
    }

    /** عکس میز از دیدِ صندلی اصلی [base] (منفی = نسخه‌ی کامل برای ذخیره) */
    private fun snapshotFor(base: Int): HokmRoomSnapshot {
        val s = _uiState.value
        val gameSeat = if (base < 0) -1 else s.gameSeatOf(base)
        val fx = s.exchangeFx
        val redactedFx = if (base < 0 || fx == null) fx else fx.copy(
            collectorFace = fx.collectorFace?.takeIf { fx.collector == base },
            debtorFace = fx.debtorFace?.takeIf { fx.debtor == base },
        )
        val received = when {
            base < 0 -> s.receivedCard
            fx == null || s.receivedCard == null && s.exchangeFx == null -> null
            fx.collector == base -> fx.debtorFace
            fx.debtor == base -> fx.collectorFace
            else -> null
        }
        return HokmRoomSnapshot(
            seats = s.netSeats,
            variant = s.variant,
            target = s.target,
            debtLimit = s.debtLimit,
            started = s.stage == HokmStage.AceDeal || s.stage == HokmStage.Playing || s.stage == HokmStage.MatchOver,
            stage = s.stage,
            game = s.game?.redactedFor(gameSeat),
            aceDeal = s.aceDeal,
            aceRevealed = s.aceRevealed,
            sweeping = s.sweeping,
            notice = s.notice,
            duelSeats = s.duelSeats,
            mordabadiGame = s.mordabadiGame,
            debtorPick = s.debtorPick,
            collectionBanner = s.collectionBanner,
            exchangeFx = redactedFx,
            receivedCard = received,
        )
    }

    private fun seatOfGuest(name: String): Int? {
        val seats = _uiState.value.netSeats
        val i = seats.indexOfFirst { it.kind == HokmNetSeatKind.GUEST && it.name.trim() == name.trim() }
        return i.takeIf { it >= 0 }
    }

    private fun pushState() {
        if (!isHost) return
        val guests = _uiState.value.netSeats.filter { it.kind == HokmNetSeatKind.GUEST }.map { it.name }
        session.pushToAll(guests)
    }

    /** مهمان (و میزبان موقع ادامه): عکس میز روی وضعیت خودمان */
    private fun applyRoom(room: HokmRoomSnapshot, asHost: Boolean) {
        val before = _uiState.value
        val mySeat = if (asHost) 0 else {
            val me = session.current.myName.trim()
            room.seats.indexOfFirst { it.kind == HokmNetSeatKind.GUEST && it.name.trim() == me }.coerceAtLeast(0)
        }
        val stage = if (!room.started) HokmStage.NetLobby else room.stage
        _uiState.update {
            it.copy(
                stage = stage,
                netMode = true,
                netSeats = room.seats,
                variant = room.variant,
                target = room.target,
                debtLimit = room.debtLimit,
                game = room.game,
                aceDeal = room.aceDeal,
                aceRevealed = room.aceRevealed,
                sweeping = room.sweeping,
                notice = room.notice,
                duelSeats = room.duelSeats,
                mordabadiGame = room.mordabadiGame,
                debtorPick = room.debtorPick,
                collectionBanner = room.collectionBanner,
                exchangeFx = room.exchangeFx,
                receivedCard = room.receivedCard,
                mySeat = mySeat,
            )
        }
        if (asHost) return
        // صداهای مهمان از روی تفاوت‌ها
        val now = _uiState.value
        val g = room.game
        val b = before.game
        if (room.aceRevealed > before.aceRevealed) emit(HokmSoundEvent.CARD)
        if (g != null && b != null && g.handNumber == b.handNumber) {
            if (g.trick.size > b.trick.size) emit(HokmSoundEvent.CARD)
            if (g.trump != null && b.trump == null) emit(HokmSoundEvent.TRUMP)
            if (g.tricksWon != b.tricksWon && g.phase == HokmPhase.PLAYING) {
                val myTeam = now.myTeam
                val mineBefore = (0 until b.tricksWon.size).filter { b.variant.teamOf(now.duelSeats?.getOrNull(it) ?: it) == myTeam }.sumOf { b.tricksWon[it] }
                val mineNow = (0 until g.tricksWon.size).filter { g.variant.teamOf(now.duelSeats?.getOrNull(it) ?: it) == myTeam }.sumOf { g.tricksWon[it] }
                emit(if (mineNow > mineBefore) HokmSoundEvent.TRICK_WON else HokmSoundEvent.TRICK_LOST)
            }
            if (g.phase == HokmPhase.HAND_OVER && b.phase != HokmPhase.HAND_OVER) {
                val won = if (g.isMordabadi) (g.lastResult?.deltas?.getOrNull(now.mySeat) ?: 0) >= 0
                else g.lastResult?.winnerTeam == now.myTeam
                emit(if (won) HokmSoundEvent.HAND_WON else HokmSoundEvent.HAND_LOST)
            }
            val me = now.mySeatInGame
            if (g.phase == HokmPhase.PLAYING && !g.trickComplete && g.turn == me && (b.turn != me || b.trickComplete)) emit(HokmSoundEvent.YOUR_TURN)
        }
        if (stage == HokmStage.MatchOver && before.stage != HokmStage.MatchOver) emit(HokmSoundEvent.MATCH_OVER)
    }

    /** آنچه میزبان باید جواب بدهد */
    private inner class HostSide : NetSession.HostCallbacks<HokmMessage> {

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
            if (s.stage != HokmStage.NetLobby) return "بازی شروع شده — دفعه‌ی بعد زودتر بیا!"
            val free = s.netSeats.indexOfFirst { it.kind == HokmNetSeatKind.EMPTY }
            if (free < 0) return "میز پره — جا نداره!"
            mutate { st ->
                st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == free) HokmNetSeat(name = name.trim(), kind = HokmNetSeatKind.GUEST) else x })
            }
            return null
        }

        override fun onCommand(name: String, msg: HokmMessage) {
            val base = seatOfGuest(name) ?: return
            val s = _uiState.value
            val gameSeat = s.gameSeatOf(base)
            when (msg) {
                is HokmMessage.ChooseTrump -> if (gameSeat >= 0) doChooseTrump(gameSeat, msg.suit)
                is HokmMessage.Play -> if (gameSeat >= 0) doPlay(gameSeat, msg.card)
                is HokmMessage.Exchange -> doHumanExchange(base, msg.debtor, msg.suit)
                is HokmMessage.GiveDebtCard -> doGiveDebtCard(base, msg.card)
                HokmMessage.SkipAceDeal -> skipAceDeal()
                HokmMessage.NextHand -> nextHand()
                HokmMessage.PlayAgain -> if (s.stage == HokmStage.MatchOver) playAgain()
                is HokmMessage.State -> Unit
            }
        }

        override fun onDisconnected(name: String) {
            val seat = seatOfGuest(name) ?: return
            mutate { st -> st.copy(netSeats = st.netSeats.mapIndexed { i, x -> if (i == seat) x.copy(connected = false) else x }) }
        }

        override fun stateFor(name: String): HokmMessage {
            val base = if (name.isBlank()) -1 else seatOfGuest(name) ?: -1
            return HokmMessage.State(snapshotFor(base))
        }

        override fun onRoomReady() {
            driver?.cancel()
            _uiState.update {
                it.copy(
                    stage = HokmStage.NetLobby,
                    netMode = true,
                    netSeats = listOf(HokmNetSeat(name = session.current.myName.trim(), kind = HokmNetSeatKind.HOST)) +
                        List(it.variant.playerCount - 1) { HokmNetSeat() },
                    mySeat = 0,
                    game = null,
                    aceDeal = null,
                    duelSeats = null,
                    mordabadiGame = null,
                )
            }
        }

        override fun onResumeHost(stored: StoredOnlineRoom, decoded: HokmMessage?) {
            driver?.cancel()
            fxJob?.cancel()
            val room = (decoded as? HokmMessage.State)?.room
            if (room == null) {
                _uiState.update {
                    it.copy(
                        stage = HokmStage.NetLobby,
                        netMode = true,
                        netSeats = listOf(HokmNetSeat(name = stored.name, kind = HokmNetSeatKind.HOST)) + List(it.variant.playerCount - 1) { HokmNetSeat() },
                        mySeat = 0,
                        game = null,
                    )
                }
                return
            }
            val seats = room.seats.map { if (it.kind == HokmNetSeatKind.GUEST) it.copy(connected = false) else it }
            applyRoom(room.copy(seats = seats, sweeping = false, exchangeFx = null, receivedCard = null, collectionBanner = false), asHost = true)
            collectionIntroDone = true
            lastExchangeAt = 0L
            when (_uiState.value.stage) {
                HokmStage.AceDeal -> runAceDeal()
                HokmStage.Playing -> drive()
                else -> Unit
            }
        }

        override fun onRoomFailed() {
            _uiState.update { it.copy(stage = HokmStage.NetEntry, netMode = false, game = null) }
        }
    }

    /** مهمان: هرچه میزبان فرستاد، همان حقیقت است */
    private inner class GuestSide : NetSession.GuestCallbacks<HokmMessage> {
        override fun onMessage(msg: HokmMessage) {
            val room = (msg as? HokmMessage.State)?.room ?: return
            applyRoom(room, asHost = false)
        }
    }

    // ---------- بازی ----------

    /** حلقه‌ی پیش‌برنده: هر کاری که به آدم‌ها وابسته نیست را با تأخیر انجام می‌دهد */
    private fun drive() {
        if (isClient) return
        driver?.cancel()
        driver = viewModelScope.launch {
            while (true) {
                val st = _uiState.value
                val g = st.game ?: return@launch
                if (st.stage != HokmStage.Playing) return@launch
                when {
                    g.phase == HokmPhase.CHOOSE_TRUMP && !st.isHumanInGame(g.hakem) -> {
                        delay(1100)
                        val suit = HokmBot.chooseTrump(g.hands[g.hakem])
                        applyTrump(suit)
                    }

                    g.phase == HokmPhase.CHOOSE_TRUMP -> return@launch

                    g.phase == HokmPhase.COLLECTION -> {
                        if (st.debtorPick != null) return@launch // منتظر کارتِ بدهکار انسانی
                        // بنر شروع فاز — فقط اگر واقعاً وصولی در کار است
                        if (!collectionIntroDone) {
                            collectionIntroDone = true
                            if (MordabadiRules.collector(g) != null) {
                                mutate { it.copy(collectionBanner = true) }
                                delay(1500)
                                mutate { it.copy(collectionBanner = false) }
                            }
                        }
                        val seat = MordabadiRules.collector(g)
                        when {
                            seat == null -> {
                                awaitExchangeGap(SETTLE_GAP_MS) // مکث کوتاه قبل از شروع بازی
                                mutate { it.copy(game = MordabadiRules.advance(g)) }
                            }
                            st.isHumanInGame(seat) -> return@launch // شیت وصول آن آدم باز است
                            else -> {
                                val pick = MordabadiRules.botCollect(g)
                                if (pick == null) {
                                    awaitExchangeGap(SETTLE_GAP_MS)
                                    mutate { it.copy(game = MordabadiRules.advance(g)) }
                                } else if (st.isHumanInGame(pick.debtor) && MordabadiRules.debtorVoidIn(g, pick.debtor, pick.suit)) {
                                    // بدهکارِ انسانی خال را ندارد → خودش کارت بدهد
                                    awaitExchangeGap(EXCHANGE_GAP_MS)
                                    delay(700)
                                    mutate {
                                        it.copy(debtorPick = DebtorPick(seat, pick.debtor, pick.suit, pick.suit == g.trump))
                                    }
                                    showNotice(
                                        "${st.nameOf(seat)} از ${st.nameOf(pick.debtor)} ${pick.suit.persian} خواست — یه کارت به جای بدهی!",
                                        2600,
                                    )
                                    return@launch
                                } else {
                                    // قدم‌های وصول یکی‌یکی و با فاصله پخش می‌شوند
                                    awaitExchangeGap(EXCHANGE_GAP_MS)
                                    delay(500)
                                    doExchange(pick.debtor, pick.suit, debtorGive = null)
                                }
                            }
                        }
                    }

                    g.trickComplete -> {
                        delay(900)
                        mutate { it.copy(sweeping = true) }
                        delay(320)
                        collect()
                    }

                    g.phase == HokmPhase.PLAYING && !st.isHumanInGame(g.turn) -> {
                        // اگر همین الان فاز وصول تمام شده، اول بگذار آخرین تبادل دیده شود
                        awaitExchangeGap(SETTLE_GAP_MS)
                        delay(700)
                        val card = HokmBot.choosePlay(g, g.turn)
                        applyPlay(g.turn, card)
                    }

                    else -> return@launch
                }
            }
        }
    }

    /** حاکمِ انسانی حکم را انتخاب کرد */
    fun chooseTrump(suit: Suit) {
        if (isClient) {
            session.send(HokmMessage.ChooseTrump(suit))
            return
        }
        doChooseTrump(_uiState.value.mySeatInGame, suit)
    }

    private fun doChooseTrump(seat: Int, suit: Suit) {
        val g = _uiState.value.game ?: return
        if (g.phase != HokmPhase.CHOOSE_TRUMP || g.hakem != seat) return
        applyTrump(suit)
        drive()
    }

    private fun applyTrump(suit: Suit) {
        val st = _uiState.value
        val g = st.game ?: return
        val next = HokmRules.chooseTrump(g, suit)
        mutate { it.copy(game = next) }
        emit(HokmSoundEvent.TRUMP)
        showNotice("${st.nameOf(g.hakem)} حکم کرد: ${suit.symbol} ${suit.persian}", 1800)
    }

    // ---------- وصول طلب (مردابادی) ----------

    /** طلبکار انسانی خال و بدهکار را انتخاب کرد؛ خالِ حکم یعنی حکم‌خواهی (۳ طلب) */
    fun humanExchange(debtor: Int, suit: Suit) {
        if (isClient) {
            session.send(HokmMessage.Exchange(debtor, suit))
            return
        }
        doHumanExchange(_uiState.value.mySeat, debtor, suit)
    }

    private fun doHumanExchange(collector: Int, debtor: Int, suit: Suit) {
        val g = _uiState.value.game ?: return
        if (g.phase != HokmPhase.COLLECTION || MordabadiRules.collector(g) != collector) return
        doExchange(debtor, suit, debtorGive = null)
        drive()
    }

    /** بدهکار انسانی که خال را نداشت، کارتش را داد */
    fun giveDebtCard(card: Card) {
        if (isClient) {
            session.send(HokmMessage.GiveDebtCard(card))
            return
        }
        doGiveDebtCard(_uiState.value.mySeat, card)
    }

    private fun doGiveDebtCard(debtor: Int, card: Card) {
        val st = _uiState.value
        val pick = st.debtorPick ?: return
        val g = st.game ?: return
        if (pick.debtor != debtor || card !in g.hands[debtor]) return
        mutate { it.copy(debtorPick = null) }
        doExchange(debtor = debtor, suit = pick.suit, debtorGive = card)
        drive()
    }

    /**
     * یک تبادل وصول را اجرا و «پخش» می‌کند: وضعیت عوض می‌شود، دو کارت بین صندلی‌ها
     * پرواز می‌کنند و پیام اعلامش می‌ماند. محرمانگی: پیام‌های همگانی فقط خال را
     * می‌گویند (نه رتبه‌ی کارت)؛ رتبه فقط در بازی محلی و برای خودِ بازیکن گفته می‌شود.
     */
    private fun doExchange(debtor: Int, suit: Suit, debtorGive: Card?) {
        val st = _uiState.value
        val g = st.game ?: return
        val outcome = runCatching { MordabadiRules.exchange(g, debtor, suit, debtorGive) }.getOrNull() ?: return
        val me = st.mySeat
        val iCollect = outcome.collector == me
        val iAmDebtor = outcome.debtor == me
        val fx = ExchangeFx(
            collector = outcome.collector,
            debtor = outcome.debtor,
            collectorFace = outcome.gaveCard,
            debtorFace = outcome.tookCard,
            id = System.nanoTime(),
        )
        val received = when {
            iCollect -> outcome.tookCard
            iAmDebtor -> outcome.gaveCard
            else -> null
        }
        lastExchangeAt = System.currentTimeMillis()
        mutate { it.copy(game = outcome.state, exchangeFx = fx, receivedCard = received) }
        emit(HokmSoundEvent.CARD)
        fxJob?.cancel()
        fxJob = viewModelScope.launch {
            delay(2000)
            mutate {
                if (it.exchangeFx?.id == fx.id) it.copy(exchangeFx = null, receivedCard = null) else it
            }
        }
        val cName = st.nameOf(outcome.collector)
        val dName = st.nameOf(outcome.debtor)
        val private = !st.netMode
        val msg = when {
            // بازی محلی، انسان طلبکار است: هر دو کارتِ خودش را می‌بیند
            private && iCollect && outcome.trumpDemand ->
                "حکم خواستی — از $dName گرفتی: ${outcome.tookCard.persianName} (۳ طلب)"
            private && iCollect && outcome.debtorWasVoid ->
                "$dName ${suit.persian} نداشت — بهت داد: ${outcome.tookCard.persianName}"
            private && iCollect ->
                "${outcome.gaveCard.persianName} رو دادی و از $dName گرفتی: ${outcome.tookCard.persianName}"
            // بازی محلی، انسان بدهکار است: می‌بیند چه ازش رفت و چه بهش رسید
            private && iAmDebtor && outcome.trumpDemand ->
                "$cName حکم طلب کرد! (۳ طلب) — ازت گرفت: ${outcome.tookCard.persianName} • بهت داد: ${outcome.gaveCard.persianName}"
            private && iAmDebtor && outcome.debtorWasVoid ->
                "دادی: ${outcome.tookCard.persianName} • بهت داد: ${outcome.gaveCard.persianName}"
            private && iAmDebtor ->
                "$cName از تو بالاترین ${suit.persian}ت رو گرفت: ${outcome.tookCard.persianName} • بهت داد: ${outcome.gaveCard.persianName}"
            // همگانی: فقط خال اعلام می‌شود؛ رتبه‌ی کارت‌ها محرمانه می‌ماند
            outcome.trumpDemand ->
                "$cName از $dName حکم طلب کرد! (۳ طلب)"
            outcome.debtorWasVoid ->
                "$dName ${suit.persian} نداشت — یه کارت به $cName داد"
            else ->
                "$cName از $dName خالِ ${suit.persian} طلب گرفت"
        }
        showNotice(msg, 2400)
    }

    /** آن‌قدر صبر می‌کند تا از آخرین تبادل [gapMs] گذشته باشد — قدم‌ها روی هم نیفتند */
    private suspend fun awaitExchangeGap(gapMs: Long) {
        val elapsed = System.currentTimeMillis() - lastExchangeAt
        if (elapsed in 0 until gapMs) delay(gapMs - elapsed)
    }

    // ---------- کارت‌ها ----------

    /** بازیکن این گوشی کارتی را لمس کرد */
    fun playCard(card: Card) {
        if (isClient) {
            session.send(HokmMessage.Play(card))
            return
        }
        doPlay(_uiState.value.mySeatInGame, card)
    }

    private fun doPlay(seat: Int, card: Card) {
        val st = _uiState.value
        if (seat < 0 || !st.isHumanInGame(seat)) return
        val g = st.game ?: return
        if (g.phase != HokmPhase.PLAYING || g.turn != seat || g.trickComplete) return
        if (!HokmRules.isLegal(g, seat, card)) return
        applyPlay(seat, card)
        drive()
    }

    private fun applyPlay(seat: Int, card: Card) {
        val st = _uiState.value
        val g = st.game ?: return
        if (!HokmRules.isLegal(g, seat, card)) return
        val next = HokmRules.play(g, seat, card)
        mutate { it.copy(game = next) }
        emit(HokmSoundEvent.CARD)
        val me = st.mySeatInGame
        if (!next.trickComplete && next.turn == me && seat != me) emit(HokmSoundEvent.YOUR_TURN)
    }

    private fun collect() {
        val st = _uiState.value
        val g = st.game ?: return
        if (!g.trickComplete) return
        val winner = g.trickLeader ?: return
        val next = HokmRules.collectTrick(g)
        mutate { it.copy(game = next, sweeping = false) }
        val me = st.mySeatInGame
        val myTeam = st.myTeam
        when (next.phase) {
            HokmPhase.HAND_OVER -> {
                val won = if (next.isMordabadi) {
                    (next.lastResult?.deltas?.getOrNull(st.mySeat) ?: 0) >= 0
                } else {
                    next.lastResult?.winnerTeam == myTeam
                }
                emit(if (won) HokmSoundEvent.HAND_WON else HokmSoundEvent.HAND_LOST)
            }
            HokmPhase.MATCH_OVER -> {
                emit(HokmSoundEvent.MATCH_OVER)
                viewModelScope.launch {
                    delay(1400)
                    mutate {
                        if (it.stage == HokmStage.Playing && it.game?.phase == HokmPhase.MATCH_OVER) {
                            it.copy(stage = HokmStage.MatchOver)
                        } else it
                    }
                }
            }
            else -> {
                val winnerBase = st.duelSeats?.getOrNull(winner) ?: winner
                val mine = winner == me || (g.variant == HokmVariant.FOUR && g.variant.teamOf(winnerBase) == myTeam)
                emit(if (mine) HokmSoundEvent.TRICK_WON else HokmSoundEvent.TRICK_LOST)
            }
        }
        if (next.phase == HokmPhase.PLAYING && next.turn == me) emit(HokmSoundEvent.YOUR_TURN)
    }

    /** از پرده‌ی پایان دست: دست بعدی — یا اگر کسی حذف شده، دوئل پایانی */
    fun nextHand() {
        if (isClient) {
            session.send(HokmMessage.NextHand)
            return
        }
        val g = _uiState.value.game ?: return
        if (g.phase != HokmPhase.HAND_OVER) return
        if (g.isMordabadi && g.lastResult?.eliminatedSeat != null) {
            beginDuel()
            return
        }
        fxJob?.cancel()
        collectionIntroDone = false
        lastExchangeAt = 0L
        val hand = HokmRules.startHand(g, random)
        mutate {
            it.copy(game = hand, sweeping = false, notice = null, collectionBanner = false, exchangeFx = null, receivedCard = null)
        }
        if (g.isMordabadi) {
            val hakemName = _uiState.value.mordabadiNameOf(hand.hakem)
            showNotice("سهمیه‌ها چرخید — $hakemName حاکمِ ۹دستی شد 👑", 2200)
        }
        drive()
    }

    /** دوئل پایانی مردابادی: دو بازمانده یک دستِ حکم دو نفره بازی می‌کنند */
    private fun beginDuel() {
        val st = _uiState.value
        val g = st.game ?: return
        fxJob?.cancel()
        collectionIntroDone = false
        lastExchangeAt = 0L
        val setup = MordabadiRules.startDuel(g, random)
        mutate {
            it.copy(
                game = setup.state,
                mordabadiGame = g,
                duelSeats = setup.seats,
                sweeping = false,
                notice = null,
                debtorPick = null,
                collectionBanner = false,
                exchangeFx = null,
                receivedCard = null,
            )
        }
        val a = st.mordabadiNameOf(setup.seats[0])
        val b = st.mordabadiNameOf(setup.seats[1])
        showNotice("دوئل نهایی! $a و $b — تک به تک تا ۷ دست ⚔️", 2600)
        drive()
    }

    private fun showNotice(text: String, ms: Long) {
        noticeJob?.cancel()
        mutate { it.copy(notice = text) }
        noticeJob = viewModelScope.launch {
            delay(ms)
            mutate { if (it.notice == text) it.copy(notice = null) else it }
        }
    }

    // ---------- پایان ----------

    /** دوباره بازی با همان تنظیمات (در شبکه: مهمان از میزبان می‌خواهد) */
    fun playAgain() {
        if (isClient) {
            session.send(HokmMessage.PlayAgain)
            return
        }
        Analytics.gameReplay()
        if (_uiState.value.variant == HokmVariant.THREE) beginMordabadi() else beginAceDeal()
    }

    /** برگشت به صفحه‌ی تنظیمات (و لغو همه‌ی کارهای پس‌زمینه) — در شبکه یعنی ترک میز */
    fun backToSetup() {
        driver?.cancel()
        noticeJob?.cancel()
        fxJob?.cancel()
        collectionIntroDone = false
        lastExchangeAt = 0L
        if (_uiState.value.netMode) session.leave()
        _uiState.update {
            it.copy(
                stage = HokmStage.Setup,
                game = null,
                aceDeal = null,
                aceRevealed = 0,
                sweeping = false,
                notice = null,
                duelSeats = null,
                mordabadiGame = null,
                debtorPick = null,
                collectionBanner = false,
                exchangeFx = null,
                receivedCard = null,
                netMode = false,
                netSeats = emptyList(),
                mySeat = 0,
            )
        }
    }

    /** بسته شدن صفحه یا کشته شدن اپ: شبکه جمع می‌شود ولی اتاقِ اینترنتی ذخیره می‌ماند */
    override fun onCleared() {
        driver?.cancel()
        noticeJob?.cancel()
        fxJob?.cancel()
        session.release()
        super.onCleared()
    }

    companion object {
        const val GAME_ID = "hokm"

        /** حداقل فاصله‌ی دو قدمِ وصول: هر تبادل دست‌کم این‌قدر روی صحنه می‌ماند */
        private const val EXCHANGE_GAP_MS = 2300L

        /** مکثِ نشستن بعد از آخرین تبادل، قبل از شروع اولین دست */
        private const val SETTLE_GAP_MS = 2800L

        private const val KEY_NAME = "hokm_player_name"
        private const val KEY_VARIANT = "hokm_variant"
        private const val KEY_TARGET = "hokm_target"
        private const val KEY_DEBT_LIMIT = "hokm_debt_limit"
    }
}
