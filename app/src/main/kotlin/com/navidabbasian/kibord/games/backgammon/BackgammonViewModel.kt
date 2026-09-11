package com.navidabbasian.kibord.games.backgammon

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.cloud.Cloud
import com.navidabbasian.kibord.core.cloud.AccountRepository
import com.navidabbasian.kibord.core.net.ClientLink
import com.navidabbasian.kibord.core.net.HostKeepAlive
import com.navidabbasian.kibord.core.net.HostLink
import com.navidabbasian.kibord.core.net.online.OnlineClient
import com.navidabbasian.kibord.core.net.online.OnlineHost
import com.navidabbasian.kibord.core.net.online.OnlineRooms
import com.navidabbasian.kibord.core.net.online.OnlineSessionStore
import com.navidabbasian.kibord.core.net.online.StoredOnlineRoom
import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
import com.navidabbasian.kibord.games.backgammon.engine.BgGameEnd
import com.navidabbasian.kibord.games.backgammon.engine.BgMatch
import com.navidabbasian.kibord.games.backgammon.engine.BgMatchRules
import com.navidabbasian.kibord.games.backgammon.engine.BgMove
import com.navidabbasian.kibord.games.backgammon.engine.BgMoveGenerator
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import com.navidabbasian.kibord.games.backgammon.engine.BgState
import com.navidabbasian.kibord.games.backgammon.engine.BgVariant
import com.navidabbasian.kibord.games.backgammon.engine.relToAbs
import com.navidabbasian.kibord.games.backgammon.net.BgClient
import com.navidabbasian.kibord.games.backgammon.net.BgDiscoveredGame
import com.navidabbasian.kibord.games.backgammon.net.BgMessage
import com.navidabbasian.kibord.games.backgammon.net.BgNsd
import com.navidabbasian.kibord.games.backgammon.net.BgRoomSnapshot
import com.navidabbasian.kibord.games.backgammon.net.BgServer
import com.navidabbasian.kibord.games.backgammon.net.decodeBgMessage
import com.navidabbasian.kibord.games.backgammon.net.encode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** مرحله‌های صفحه‌ای بازی تخته‌نرد */
enum class BgStage { VariantSelect, ModeSelect, NetEntry, NetJoin, NetLobby, Playing }

/** نقش این گوشی: بدون شبکه (دو نفر روی همین گوشی)، میزبان یا مهمان */
enum class BgNetRole { NONE, HOST, CLIENT }

/** رویدادهای صوتی که رابط کاربری به صدای مناسب ترجمه می‌کند */
enum class BgSoundEvent { DICE, MOVE, HIT, BEAR_OFF, SKIP, WIN, DOUBLE, CHAT, TIMEOUT }

/** حباب چت سریع بالای آواتار یک بازیکن — شناسه برای محو خودکار بعد از چند ثانیه */
data class BgChatBubble(val text: String, val id: Int)

/** گزینه‌های طول مسابقه در صفحه‌ی تنظیم */
val BG_MATCH_LENGTHS = listOf(1, 3, 5, 7)

/** گزینه‌های ساعت هر بازیکن (دقیقه) — صفر یعنی بدون ساعت */
val BG_CLOCK_MINUTES = listOf(0, 2, 5, 10)

/**
 * وضعیت رابط کاربری تخته‌نرد — مبدأ و مقصدهای مجاز به شماره‌ی مطلق صفحه
 * ترجمه شده‌اند تا صفحه‌ی نقاشی مستقیم مصرف‌شان کند.
 */
data class BgUiState(
    val stage: BgStage = BgStage.VariantSelect,
    val variant: BgVariant? = null,
    val game: BgState? = null,
    /** مبدأ انتخاب‌شده از دید بازیکن نوبت — ۲۵ یعنی ورود از بار/بیرون */
    val selectedSource: Int? = null,
    /** خانه‌های مطلقِ مبدأ مجاز */
    val sourcesAbs: Set<Int> = emptySet(),
    /** آیا ورود (بار یا مهره‌های بیرون) مبدأ مجاز است؟ */
    val entryIsSource: Boolean = false,
    /** خانه‌های مطلقِ مقصد مجاز برای مبدأ انتخاب‌شده */
    val destsAbs: Set<Int> = emptySet(),
    /** آیا خارج‌کردن مهره مقصد مجاز مبدأ انتخاب‌شده است؟ */
    val offIsDest: Boolean = false,
    /** پیام رد شدن نوبت — null یعنی پیامی نیست */
    val skipMessage: String? = null,
    /** شمارنده‌ی پرتاب تاس — هر پرتاب تازه (محلی یا رسیده از میزبان) یکی بالا می‌رود
     *  تا انیمیشن غلت تاس حتی با اعداد تکراری دوباره اجرا شود */
    val rollNonce: Int = 0,

    // ---- شبکه ----
    val netRole: BgNetRole = BgNetRole.NONE,
    val myName: String = "",
    /** عکس اتاق شبکه‌ای — اسم‌ها و وضعیت اتصال حریف */
    val room: BgRoomSnapshot = BgRoomSnapshot(),
    /** بازی‌های پیداشده در شبکه برای پیوستن */
    val discovered: List<BgDiscoveredGame> = emptyList(),
    val connecting: Boolean = false,
    val connectError: String? = null,
    /** آدرس این گوشی برای اتصال دستی مهمان */
    val hostAddress: String = "",
    /** ارتباط با میزبان قطع شد (سمت مهمان) */
    val lostConnection: Boolean = false,
    /** بازی اینترنتی با کد اتاق، به‌جای وای‌فای محلی */
    val onlineMode: Boolean = false,
    /** کد اتاق اینترنتی — برای میزبان کدِ ساخته‌شده، برای مهمان کدی که با آن وصل شده */
    val roomCode: String = "",
    /** اتاق اینترنتیِ نیمه‌کاره‌ای که روی دیسک مانده و می‌شود ادامه‌اش داد */
    val resumable: StoredOnlineRoom? = null,
    /** میزبان لحظه‌ای غایب شده (سمت مهمان) — منتظر برگشتش هستیم */
    val hostAway: Boolean = false,
    /** مهمان دارد دوباره به همان اتاق وصل می‌شود */
    val reconnecting: Boolean = false,

    // ---- مسابقه، مکعب، ساعت، چت ----
    /** وضعیت مسابقه‌ی جاری: امتیازها، مکعب دوبل، کرافورد و بانک ساعت‌ها */
    val match: BgMatch = BgMatch(),
    /** زمانِ این گوشی (elapsedRealtime) در لحظه‌ای که بانک ساعت‌ها آخرین بار تسویه شد */
    val clockStampMs: Long = 0L,
    /** انتخاب صفحه‌ی تنظیم: مسابقه تا چند امتیاز؟ */
    val matchLength: Int = 1,
    /** انتخاب صفحه‌ی تنظیم: ساعت هر بازیکن به دقیقه (صفر = بدون ساعت) */
    val clockMinutes: Int = 0,
    /** حباب‌های چت سریعِ زنده — هر بازیکن حداکثر یکی */
    val chatBubbles: Map<BgPlayer, BgChatBubble> = emptyMap(),
    /** پنل چت سریع باز است؟ */
    val chatOpen: Boolean = false,
    /** راهنمای یک‌باره‌ی «دست به مهره» ی روش ایرانی دیده شد؟ */
    val touchMoveHintSeen: Boolean = false,
) {
    val isNetPlay: Boolean get() = netRole != BgNetRole.NONE

    /** آیا این روش مکعب دوبل دارد؟ در ایرانی مکعب اصلاً وجود ندارد */
    val cubeAllowed: Boolean
        get() = variant?.let { BgRules.of(it).usesCube } != false

    /** قانون «دست به مهره»ی روش ایرانی فعال است؟ */
    val touchMoveActive: Boolean
        get() = variant?.let { BgRules.of(it).touchMove } == true

    /** دست تمام شده ولی مسابقه ادامه دارد — پرده‌ی «دست بعدی» */
    val gameOverMatchContinues: Boolean
        get() = game?.phase == BgPhase.FINISHED && match.matchWinner == null

    /** مسابقه تمام شده — صفحه‌ی برنده‌ی نهایی */
    val matchOver: Boolean
        get() = game?.phase == BgPhase.FINISHED && match.matchWinner != null

    /** آیا «این گوشی» باید به پیشنهاد دوبل جواب بدهد؟ */
    val mustAnswerDouble: Boolean
        get() {
            val offerer = match.doubleOfferedBy ?: return false
            return !isNetPlay || myPlayer == offerer.opponent
        }

    /** آیا بازیکنِ نوبت همین حالا (پیش از تاس) می‌تواند دوبل کند؟ */
    val canOfferDouble: Boolean
        get() {
            if (!cubeAllowed) return false
            val g = game ?: return false
            val p = g.turn ?: return false
            return g.phase == BgPhase.ROLLING && isMyTurn && BgMatchRules.canDouble(match, p)
        }

    /**
     * اتصال سالم است؟ ساعت‌ها فقط در این حالت می‌دوند: محلی همیشه؛ میزبان وقتی
     * مهمان وصل است؛ مهمان وقتی نه ارتباط قطع شده و نه میزبان غایب است.
     */
    val roomHealthy: Boolean
        get() = when (netRole) {
            BgNetRole.NONE -> true
            BgNetRole.HOST -> room.guestConnected
            BgNetRole.CLIENT -> !lostConnection && !hostAway
        }

    /** ساعت‌ها باید این لحظه کم شوند؟ */
    val clocksTicking: Boolean
        get() = match.hasClocks && match.clockRunning != null && stage == BgStage.Playing &&
            game?.phase != BgPhase.FINISHED && game?.phase != BgPhase.OPENING_ROLL && roomHealthy

    /** زمان باقی‌مانده‌ی نمایشیِ یک بازیکن در لحظه‌ی [nowMs] (elapsedRealtime این گوشی) */
    fun clockRemaining(p: BgPlayer, nowMs: Long): Long =
        if (clocksTicking) BgMatchRules.remainingNow(match, p, nowMs - clockStampMs) else match.clock(p)

    /** مهره‌های من در بازی شبکه‌ای — میزبان سفید، مهمان سیاه */
    val myPlayer: BgPlayer?
        get() = when (netRole) {
            BgNetRole.NONE -> null
            BgNetRole.HOST -> BgPlayer.WHITE
            BgNetRole.CLIENT -> room.guestPlayer
        }

    /** در حالت محلی همیشه نوبت «این گوشی» است؛ در شبکه فقط وقتی مهره‌های من بازی می‌کنند */
    val isMyTurn: Boolean
        get() = !isNetPlay || (game?.turn != null && game.turn == myPlayer)

    /** اسم نمایشی هر رنگ: در شبکه اسم واقعی، محلی «بازیکن ۱/۲» */
    fun displayName(p: BgPlayer): String = when {
        !isNetPlay -> if (p == BgPlayer.WHITE) "بازیکن ۱" else "بازیکن ۲"
        p == room.guestPlayer -> room.guestName.ifBlank { "حریف" }
        else -> room.hostName.ifBlank { "میزبان" }
    }
}

/**
 * موتورگردان تخته‌نرد — سه راه بازی:
 * دو نفر روی همین گوشی، شبکه‌ی محلی (وای‌فای/هات‌اسپات) و اینترنتی با کد اتاق.
 * در شبکه میزبان مرجع حقیقت است: فرمان مهمان را با موتور می‌سنجد، اعمال می‌کند
 * و وضعیت کامل را پخش می‌کند؛ مهمان فقط همان را نقاشی می‌کند.
 */
class BackgammonViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BgUiState())
    val uiState: StateFlow<BgUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<BgSoundEvent>(extraBufferCapacity = 8)
    val soundEvents: SharedFlow<BgSoundEvent> = _soundEvents.asSharedFlow()

    private val nsd = BgNsd(application)
    private val keepAlive = HostKeepAlive(application)
    private var server: HostLink<BgMessage>? = null
    private var client: ClientLink<BgMessage>? = null

    /** موتور دستِ جاری — مهمان موتور ندارد و همه‌چیز را از میزبان می‌گیرد */
    private var engine: BgEngine? = null

    /** حرکت‌های آغازین مجاز این لحظه — با هر تغییر وضعیت تازه می‌شود */
    private var currentMoves: List<BgMove> = emptyList()

    /** شمار دست‌های بازی‌شده در این اتاق شبکه‌ای */
    private var rematchCount = 0

    init {
        // اتاق اینترنتیِ نیمه‌کاره‌ای مانده؟ پیشنهادِ «ادامه بده» روی صفحه‌ی ورود
        _uiState.value = _uiState.value.copy(
            resumable = OnlineSessionStore.load(getApplication(), GAME_ID),
        )
    }

    private fun emitSound(event: BgSoundEvent) {
        viewModelScope.launch { _soundEvents.emit(event) }
    }

    /** یک پرتاب تازه اتفاق افتاد — انیمیشن تاس با همین شماره دوباره کوک می‌شود */
    private fun bumpRoll() {
        _uiState.value = _uiState.value.copy(rollNonce = _uiState.value.rollNonce + 1)
    }

    // ================= انتخاب روش و راه بازی =================

    /** انتخاب روش بازی — بعدش می‌پرسیم روی یک گوشی یا شبکه‌ای؟ */
    fun chooseVariant(variant: BgVariant) {
        _uiState.value = _uiState.value.copy(stage = BgStage.ModeSelect, variant = variant)
    }

    /** دو نفر روی همین گوشی — همان جریان همیشگی */
    fun chooseLocalMode() {
        val variant = _uiState.value.variant ?: return
        val e = BgEngine(BgRules.of(variant))
        engine = e
        currentMoves = emptyList()
        val st = _uiState.value
        Analytics.gameSetup(
            "variant" to variant.analyticsName,
            "net" to "local",
            "match_len" to st.matchLength,
            "clock_min" to st.clockMinutes,
        )
        _uiState.value = BgUiState(
            stage = BgStage.Playing,
            variant = variant,
            game = e.createGame(),
            myName = st.myName,
            resumable = st.resumable,
            matchLength = st.matchLength,
            // نرد ایرانی ساعت ندارد — وقت آزاد
            clockMinutes = if (variant == BgVariant.IRANI) 0 else st.clockMinutes,
            match = BgMatchRules.newMatch(
                st.matchLength,
                if (variant == BgVariant.IRANI) 0L else st.clockMinutes * 60_000L,
            ),
            clockStampMs = SystemClock.elapsedRealtime(),
        )
        startClockTicker()
    }

    /** صفحه‌ی تنظیم: مسابقه تا چند امتیاز؟ (۱ = تک‌دست) */
    fun setMatchLength(length: Int) {
        if (length !in BG_MATCH_LENGTHS) return
        _uiState.value = _uiState.value.copy(matchLength = length)
    }

    /** صفحه‌ی تنظیم: ساعت هر بازیکن به دقیقه (صفر = بدون ساعت) */
    fun setClockMinutes(minutes: Int) {
        if (minutes !in BG_CLOCK_MINUTES) return
        _uiState.value = _uiState.value.copy(clockMinutes = minutes)
    }

    /** بازی شبکه‌ای — برو به صفحه‌ی اسم و میزبان/مهمان */
    fun chooseNetworkMode() {
        _uiState.value = _uiState.value.copy(stage = BgStage.NetEntry, connectError = null)
    }

    fun setMyName(name: String) {
        // در حالت اینترنتی اسم قفل است: همان یوزرنیم حساب
        if (_uiState.value.onlineMode) return
        _uiState.value = _uiState.value.copy(myName = name.take(16))
    }

    fun setOnlineMode(on: Boolean) {
        if (!on) {
            _uiState.value = _uiState.value.copy(onlineMode = false, connectError = null)
            return
        }
        // بازی اینترنتی فقط با حساب: اسم بازیکن همان یوزرنیم است
        val me = AccountRepository.onlineIdentity()
        if (me == null) {
            _uiState.value = _uiState.value.copy(onlineMode = false, connectError = AccountRepository.NEED_ACCOUNT_MESSAGE)
            return
        }
        _uiState.value = _uiState.value.copy(onlineMode = true, myName = me, connectError = null)
    }

    fun backFromModeSelect() {
        _uiState.value = _uiState.value.copy(stage = BgStage.VariantSelect)
    }

    fun backFromNetEntry() {
        _uiState.value = _uiState.value.copy(stage = BgStage.ModeSelect, connectError = null, connecting = false)
    }

    fun backFromJoin() {
        nsd.stopDiscovery()
        // اگر وصل شده بودیم ولی هنوز وضعیت نرسیده، این یعنی خروجِ خواسته از اتاق
        if (_uiState.value.netRole == BgNetRole.CLIENT) {
            stopNetworking()
            clearStoredRoom()
        }
        _uiState.value = _uiState.value.copy(
            stage = BgStage.NetEntry,
            netRole = BgNetRole.NONE,
            connectError = null,
            connecting = false,
            reconnecting = false,
            hostAway = false,
            lostConnection = false,
        )
    }

    /** میزبان از لابی منصرف شد — سرور جمع می‌شود و اتاقِ ذخیره‌شده هم می‌رود */
    fun cancelHosting() {
        stopNetworking()
        clearStoredRoom()
        _uiState.value = _uiState.value.copy(
            stage = BgStage.NetEntry,
            netRole = BgNetRole.NONE,
            room = BgRoomSnapshot(),
            roomCode = "",
            hostAddress = "",
            game = null,
        )
    }

    // ================= اتاق پایدار: ذخیره و ادامه‌ی بازی اینترنتی =================

    /** وضعیت کامل میزبان به همان شکلی که روی شبکه می‌رود — برای ذخیره روی دیسک */
    private fun encodedHostState(): String = BgMessage.State(roomSnapshot()).encode()

    /** میزبان اینترنتی: اتاق با کد و وضعیت کاملش روی دیسک می‌ماند */
    private fun storeHostRoom(code: String, name: String) {
        OnlineSessionStore.save(
            getApplication(),
            StoredOnlineRoom(
                gameId = GAME_ID,
                role = "host",
                code = code,
                name = name,
                savedAt = System.currentTimeMillis(),
                hostState = encodedHostState(),
            ),
        )
    }

    /** مهمان اینترنتی: فقط کد و اسم لازم است — وضعیت را میزبان می‌دهد */
    private fun storeGuestRoom(code: String, name: String) {
        OnlineSessionStore.save(
            getApplication(),
            StoredOnlineRoom(
                gameId = GAME_ID,
                role = "guest",
                code = code,
                name = name,
                savedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** میزبان اینترنتی بعد از هر تغییر وضعیت — ارزان است */
    private fun persistHostState() {
        if (_uiState.value.netRole != BgNetRole.HOST || server !is OnlineHost<*>) return
        OnlineSessionStore.saveHostState(getApplication(), GAME_ID, encodedHostState())
    }

    /** خروجِ خواسته‌ی بازیکن: اتاق ذخیره‌شده دیگر معنی ندارد */
    private fun clearStoredRoom() {
        OnlineSessionStore.clear(getApplication())
        _uiState.value = _uiState.value.copy(resumable = null)
    }

    /** «بی‌خیال» — اتاق نیمه‌کاره فراموش می‌شود */
    fun discardResume() {
        clearStoredRoom()
    }

    /** «ادامه بده» — میزبان با همان کد اتاق را دوباره می‌سازد، مهمان دوباره می‌پیوندد */
    fun resumeOnline() {
        val stored = _uiState.value.resumable ?: return
        if (_uiState.value.connecting) return
        if (!Cloud.isConfigured) {
            _uiState.value = _uiState.value.copy(connectError = "بخش آنلاین روی این نسخه فعال نیست")
            return
        }
        if (stored.isHost) resumeAsHost(stored) else resumeAsGuest(stored)
    }

    /**
     * میزبان برمی‌گردد: موقعیت، نوبت و تاس‌ها دقیقاً از وضعیت ذخیره‌شده، حریف تا
     * برگشتنش «قطع» حساب می‌شود، و اتاق با همان کد قبلی بالا می‌آید تا مهمانِ
     * منتظر خودکار وصل شود.
     */
    private fun resumeAsHost(stored: StoredOnlineRoom) {
        val name = stored.name
        val room = stored.hostState
            ?.let { decodeBgMessage(it) as? BgMessage.State }
            ?.room
        val before = _uiState.value
        if (room != null) {
            // همان قواعدِ قبلی: روش بازی از خودِ وضعیت ذخیره‌شده می‌آید
            val e = BgEngine(BgRules.of(room.variant))
            engine = e
            rematchCount = room.rematchCount
            currentMoves = emptyList()
            _uiState.value = before.copy(
                stage = if (room.guestName.isNotBlank()) BgStage.Playing else BgStage.NetLobby,
                variant = room.variant,
                game = room.game ?: e.createGame(),
                netRole = BgNetRole.HOST,
                myName = name,
                room = room.copy(hostName = name, guestConnected = false),
                roomCode = stored.code,
                hostAddress = "",
                onlineMode = true,
                connecting = true,
                connectError = null,
                skipMessage = room.skipMessage,
                selectedSource = null,
                sourcesAbs = emptySet(),
                entryIsSource = false,
                destsAbs = emptySet(),
                offIsDest = false,
                lostConnection = false,
                hostAway = false,
                match = room.match,
                matchLength = room.match.length,
                clockMinutes = (room.match.clockTotalMs / 60_000L).toInt(),
                clockStampMs = SystemClock.elapsedRealtime(),
                chatBubbles = emptyMap(),
                chatOpen = false,
            )
            refreshMoves()
            startClockTicker()
        } else {
            // وضعیت خوانا نبود: لابیِ تازه با همان کد
            val variant = before.variant ?: BgVariant.STANDARD
            _uiState.value = before.copy(onlineMode = true, myName = name)
            becomeHost(name, variant, roomCode = stored.code, hostAddress = "")
            _uiState.value = _uiState.value.copy(connecting = true)
        }
        val host = OnlineHost<BgMessage>(
            scope = viewModelScope,
            encode = { m: BgMessage -> m.encode() },
            onClientJoin = ::acceptJoin,
            onCommand = ::handleGuestCommand,
            onClientDisconnected = ::handleGuestDisconnect,
            latestState = { BgMessage.State(roomSnapshot()) },
            decode = ::decodeBgMessage,
            roomCode = stored.code,
        )
        host.start { ok ->
            if (!ok) {
                // برگرد به صفحه‌ی ورود؛ کارت «ادامه بده» می‌ماند تا دوباره امتحان کند
                engine = null
                currentMoves = emptyList()
                _uiState.value = _uiState.value.copy(
                    stage = BgStage.NetEntry,
                    netRole = BgNetRole.NONE,
                    room = BgRoomSnapshot(),
                    roomCode = "",
                    game = null,
                    connecting = false,
                    connectError = "اتاق دوباره ساخته نشد — اینترنت رو چک کن",
                )
                return@start
            }
            server = host
            keepAlive.acquire(lan = false)
            _uiState.value = _uiState.value.copy(connecting = false, connectError = null, resumable = null)
            storeHostRoom(code = stored.code, name = name)
        }
    }

    /** مهمان برمی‌گردد: با همان اسم و کد می‌پیوندد؛ میزبان او را «برگشته» می‌شناسد */
    private fun resumeAsGuest(stored: StoredOnlineRoom) {
        _uiState.value = _uiState.value.copy(onlineMode = true, myName = stored.name, connectError = null)
        connectOnline(code = stored.code, name = stored.name) { error ->
            if (error != null) {
                // کارت «ادامه بده» می‌ماند تا دوباره امتحان کند یا بی‌خیال شود
                _uiState.value = _uiState.value.copy(connecting = false, connectError = error)
            } else {
                _uiState.value = _uiState.value.copy(resumable = null)
            }
        }
    }

    /** مهمان روی صفحه‌ی «ارتباط قطع شد»: دوباره به همان اتاق با همان اسم وصل شو */
    fun reconnectOnline() {
        val st = _uiState.value
        if (!st.onlineMode || st.roomCode.isBlank() || st.myName.isBlank()) return
        if (st.reconnecting) return
        client?.close()
        client = null
        _uiState.value = st.copy(lostConnection = false, reconnecting = true, connectError = null, hostAway = false)
        connectOnline(code = st.roomCode, name = st.myName) { error ->
            if (error != null) {
                // همان‌جا می‌مانیم؛ خطا را نشان بده تا دوباره بزند یا بی‌خیال شود
                _uiState.value = _uiState.value.copy(
                    reconnecting = false,
                    connecting = false,
                    lostConnection = true,
                    connectError = error,
                )
            } else {
                _uiState.value = _uiState.value.copy(reconnecting = false)
            }
        }
    }

    // ================= میزبانی =================

    /** میزبانی روی شبکه‌ی محلی: سرور سوکتی + اعلام سرویس برای کشف خودکار */
    fun startHosting() {
        val name = _uiState.value.myName.trim()
        val variant = _uiState.value.variant ?: return
        if (name.isBlank()) return
        val srv = BgServer(
            scope = viewModelScope,
            onClientJoin = ::acceptJoin,
            onCommand = ::handleGuestCommand,
            onClientDisconnected = ::handleGuestDisconnect,
            latestState = { BgMessage.State(roomSnapshot()) },
        )
        if (!srv.start()) {
            _uiState.value = _uiState.value.copy(connectError = "سرور روی این گوشی راه نیفتاد")
            return
        }
        server = srv
        keepAlive.acquire()
        nsd.register(name, srv.port)
        becomeHost(name, variant, roomCode = "", hostAddress = BgNsd.localIpAddress() ?: "")
    }

    /** میزبانی اینترنتی: به‌جای سوکت محلی، اتاقی با کد شش‌حرفی ساخته می‌شود */
    fun startHostingOnline() {
        val variant = _uiState.value.variant ?: return
        if (!Cloud.isConfigured) {
            _uiState.value = _uiState.value.copy(connectError = "بخش آنلاین روی این نسخه فعال نیست")
            return
        }
        val name = AccountRepository.onlineIdentity() ?: run {
            _uiState.value = _uiState.value.copy(connectError = AccountRepository.NEED_ACCOUNT_MESSAGE)
            return
        }
        _uiState.value = _uiState.value.copy(myName = name)
        _uiState.value = _uiState.value.copy(connecting = true, connectError = null)
        val host = OnlineHost<BgMessage>(
            scope = viewModelScope,
            encode = { m: BgMessage -> m.encode() },
            onClientJoin = ::acceptJoin,
            onCommand = ::handleGuestCommand,
            onClientDisconnected = ::handleGuestDisconnect,
            latestState = { BgMessage.State(roomSnapshot()) },
            decode = ::decodeBgMessage,
        )
        host.start { ok ->
            if (!ok) {
                _uiState.value = _uiState.value.copy(
                    connecting = false,
                    connectError = "اتاق ساخته نشد — اینترنت رو چک کن",
                )
                return@start
            }
            server = host
            keepAlive.acquire(lan = false)
            becomeHost(name, variant, roomCode = host.roomCode, hostAddress = "")
            // اتاق روی دیسک می‌ماند تا با بسته شدن اپ یا قفل گوشی از دست نرود
            storeHostRoom(code = host.roomCode, name = name)
        }
    }

    /** برپایی مشترک میزبان: بازی ساخته می‌شود و به لابی انتظار می‌رویم */
    private fun becomeHost(name: String, variant: BgVariant, roomCode: String, hostAddress: String) {
        val e = BgEngine(BgRules.of(variant))
        engine = e
        currentMoves = emptyList()
        rematchCount = 0
        val st = _uiState.value
        // نرد ایرانی ساعت ندارد — وقت آزاد
        val match = BgMatchRules.newMatch(
            st.matchLength,
            if (variant == BgVariant.IRANI) 0L else st.clockMinutes * 60_000L,
        )
        _uiState.value = st.copy(
            stage = BgStage.NetLobby,
            netRole = BgNetRole.HOST,
            myName = name,
            game = e.createGame(),
            room = BgRoomSnapshot(variant = variant, hostName = name, match = match),
            roomCode = roomCode,
            hostAddress = hostAddress,
            connecting = false,
            connectError = null,
            selectedSource = null,
            sourcesAbs = emptySet(),
            entryIsSource = false,
            destsAbs = emptySet(),
            offIsDest = false,
            skipMessage = null,
            match = match,
            clockStampMs = SystemClock.elapsedRealtime(),
            chatBubbles = emptyMap(),
            chatOpen = false,
        )
        startClockTicker()
    }

    /** بررسی ورود مهمان — تهی یعنی خوش آمدی؛ تخته‌نرد فقط یک مهمان دارد */
    private fun acceptJoin(name: String): String? {
        val st = _uiState.value
        if (st.netRole != BgNetRole.HOST) return "بازی‌ای در کار نیست"
        val guest = st.room.guestName
        return when {
            name.isBlank() -> "اسم خالی است"
            name.trim() == st.myName.trim() -> "این اسم مالِ میزبانه — یه اسم دیگه انتخاب کن"
            guest.isBlank() -> {
                // اولین مهمان: صندلی سیاه مال اوست و بازی خودکار شروع می‌شود
                Analytics.gameSetup(
                    "variant" to st.room.variant.analyticsName,
                    "net" to if (st.onlineMode) "online" else "lan",
                    "match_len" to st.match.length,
                    "clock_min" to (st.match.clockTotalMs / 60_000L).toInt(),
                )
                _uiState.value = _uiState.value.copy(
                    stage = BgStage.Playing,
                    room = st.room.copy(guestName = name, guestConnected = true),
                )
                refreshMoves()
                pushState()
                null
            }

            guest.trim() == name.trim() && !st.room.guestConnected -> {
                // برگشتِ همان حریفِ قطع‌شده
                _uiState.value = _uiState.value.copy(room = st.room.copy(guestConnected = true))
                pushState()
                null
            }

            else -> "تخته‌نرد دو نفره‌ست — این اتاق پره!"
        }
    }

    private fun handleGuestDisconnect(name: String) {
        val st = _uiState.value
        if (st.netRole != BgNetRole.HOST) return
        if (st.room.guestName.trim() != name.trim()) return
        _uiState.value = _uiState.value.copy(room = st.room.copy(guestConnected = false))
    }

    // ================= پیوستن مهمان =================

    /** باز کردن صفحه‌ی پیوستن؛ در حالت محلی کشف خودکار شروع می‌شود */
    fun openJoinScreen() {
        if (_uiState.value.myName.isBlank()) return
        _uiState.value = _uiState.value.copy(
            stage = BgStage.NetJoin,
            discovered = emptyList(),
            connectError = null,
        )
        if (_uiState.value.onlineMode) return // اینترنتی: با کد می‌آیند
        nsd.discover(
            onFound = { game ->
                _uiState.value = _uiState.value.let { st ->
                    st.copy(discovered = st.discovered.filter { it.hostName != game.hostName } + game)
                }
            },
            onLost = { name ->
                _uiState.value = _uiState.value.let { st ->
                    st.copy(discovered = st.discovered.filter { it.hostName != name })
                }
            },
        )
    }

    /** پیوستن محلی با آدرس (از کشف خودکار یا دستی) */
    fun joinGame(address: String, port: Int = BgServer.BASE_PORT) {
        val name = _uiState.value.myName.trim()
        if (name.isBlank() || address.isBlank()) return
        _uiState.value = _uiState.value.copy(myName = name, connecting = true, connectError = null)
        val c = BgClient(
            scope = viewModelScope,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.netRole == BgNetRole.CLIENT) {
                    _uiState.value = _uiState.value.copy(lostConnection = true)
                }
            },
        )
        client = c
        c.connect(address, port, name) { error ->
            if (error != null) {
                client = null
                _uiState.value = _uiState.value.copy(connecting = false, connectError = error)
            } else {
                nsd.stopDiscovery()
                _uiState.value = _uiState.value.copy(
                    netRole = BgNetRole.CLIENT,
                    connecting = false,
                    connectError = null,
                )
            }
        }
    }

    /** پیوستن اینترنتی با کد اتاق */
    fun joinOnlineRoom(code: String) {
        val name = AccountRepository.onlineIdentity() ?: run {
            _uiState.value = _uiState.value.copy(connectError = AccountRepository.NEED_ACCOUNT_MESSAGE)
            return
        }
        connectOnline(code = OnlineRooms.normalizeCode(code), name = name)
    }

    /**
     * هسته‌ی پیوستن اینترنتی — پیوستن تازه، «ادامه بده» و «دوباره وصل شو» همه از
     * همین می‌گذرند. نتیجه‌ی وصل شدن ذخیره می‌شود تا قطعیِ بعدی قابل جبران باشد.
     */
    private fun connectOnline(code: String, name: String, onDone: (error: String?) -> Unit = {}) {
        _uiState.value = _uiState.value.copy(myName = name, connecting = true, connectError = null)
        val c = OnlineClient<BgMessage>(
            scope = viewModelScope,
            encode = { m: BgMessage -> m.encode() },
            decode = ::decodeBgMessage,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.netRole == BgNetRole.CLIENT) {
                    _uiState.value = _uiState.value.copy(lostConnection = true, hostAway = false)
                }
            },
            onHostAway = { away ->
                _uiState.value = _uiState.value.copy(hostAway = away)
            },
        )
        client = c
        c.connect(code, name) { error ->
            if (error != null) {
                client = null
                _uiState.value = _uiState.value.copy(connecting = false, connectError = error)
            } else {
                _uiState.value = _uiState.value.copy(
                    netRole = BgNetRole.CLIENT,
                    roomCode = code,
                    connecting = false,
                    connectError = null,
                    lostConnection = false,
                    hostAway = false,
                )
                storeGuestRoom(code = code, name = name)
            }
            onDone(error)
        }
    }

    /** مهمان: هرچه میزبان پخش کرد، همان حقیقت است */
    private fun handleServerMessage(msg: BgMessage) {
        if (msg is BgMessage.Chat) {
            val st = _uiState.value
            // پژواک پیام خودم را دوباره نشان نده — همان لحظه‌ی فرستادن نشانش دادم
            if (msg.from.trim() == st.myName.trim()) return
            val from = if (msg.from.trim() == st.room.guestName.trim()) st.room.guestPlayer else st.room.guestPlayer.opponent
            showChat(from, msg.text)
            return
        }
        val room = (msg as? BgMessage.State)?.room ?: return
        val beforeState = _uiState.value
        val before = beforeState.game
        // وضعیت فقط از میزبانِ همین اتصال می‌آید — نقش همین‌جا قطعی می‌شود تا
        // اگر پیام وضعیت زودتر از پایان دست‌دادن برسد، ورودی‌ها قاطی نشوند
        _uiState.value = _uiState.value.copy(
            stage = BgStage.Playing,
            netRole = BgNetRole.CLIENT,
            room = room,
            variant = room.variant,
            game = room.game,
            skipMessage = room.skipMessage,
            selectedSource = null,
            destsAbs = emptySet(),
            offIsDest = false,
            match = room.match,
            clockStampMs = SystemClock.elapsedRealtime(),
        )
        startClockTicker()
        if (before?.phase != BgPhase.FINISHED && room.game?.phase == BgPhase.FINISHED) {
            emitSound(if (room.match.lastGameEnd == BgGameEnd.TIMEOUT) BgSoundEvent.TIMEOUT else BgSoundEvent.WIN)
        }
        // پیشنهاد دوبل تازه رسید؟ صدا فقط برای کسی که باید جواب بدهد
        if (beforeState.match.doubleOfferedBy == null && room.match.doubleOfferedBy != null &&
            room.match.doubleOfferedBy != room.guestPlayer
        ) {
            emitSound(BgSoundEvent.DOUBLE)
        }
        // پرتاب تازه‌ی میزبان رسید؟ (تاس نوبت یا تک‌تاس‌های شروع) → صدا و انیمیشن غلت
        val g = room.game
        val newTurnDice = g != null && g.dice.isNotEmpty() &&
            (before == null || before.dice != g.dice || before.turn != g.turn)
        val newOpeningDice = g != null && g.phase == BgPhase.OPENING_ROLL &&
            g.openingDieWhite != null &&
            (
                before == null ||
                    before.openingDieWhite != g.openingDieWhite ||
                    before.openingDieBlack != g.openingDieBlack
                )
        if (newTurnDice || newOpeningDice) {
            emitSound(BgSoundEvent.DICE)
            bumpRoll()
        }
        refreshMoves()
    }

    // ================= چرخه‌ی نوبت =================

    /** پرتاب تک‌تاس شروع — در شبکه فقط میزبان می‌اندازد */
    fun rollOpening() {
        if (_uiState.value.netRole == BgNetRole.CLIENT) return
        val e = engine ?: return
        val game = _uiState.value.game ?: return
        if (game.phase != BgPhase.OPENING_ROLL) return
        emitSound(BgSoundEvent.DICE)
        val next = e.rollOpening(game)
        setGame(next)
        bumpRoll()
        // نفر اول مشخص شد: ساعتِ او از همین لحظه می‌دود
        next.turn?.let { runClockFor(it) }
        if (next.phase == BgPhase.MOVING) afterDiceReady() else pushState()
    }

    /** پرتاب دو تاس نوبت — مهمان درخواستش را برای میزبان می‌فرستد */
    fun rollDice() {
        val st = _uiState.value
        val game = st.game ?: return
        if (game.phase != BgPhase.ROLLING) return
        if (!st.isMyTurn) return
        if (st.match.doubleOfferedBy != null) return
        if (st.netRole == BgNetRole.CLIENT) {
            // صدا و انیمیشن با رسیدن وضعیتِ تازه از میزبان کوک می‌شوند
            client?.send(BgMessage.RollRequest)
            return
        }
        settleClocks()
        emitSound(BgSoundEvent.DICE)
        setGame(engine?.rollTurn(game) ?: return)
        bumpRoll()
        afterDiceReady()
    }

    // ================= مکعب دوبل، تسلیم، دست بعدی =================

    /** پیشنهاد دوبل — فقط پیش از تاس ریختن، در نوبت خودت، با مکعب وسط یا مال خودت */
    fun offerDouble() {
        val st = _uiState.value
        if (!st.canOfferDouble) return
        val p = st.game?.turn ?: return
        if (st.netRole == BgNetRole.CLIENT) {
            client?.send(BgMessage.DoubleOffer)
            return
        }
        doOfferDouble(p)
    }

    private fun doOfferDouble(p: BgPlayer) {
        settleClocks()
        val st = _uiState.value
        val m = BgMatchRules.offerDouble(st.match, p)
        if (m.doubleOfferedBy == null) return
        // تا حریف تصمیم بگیرد، ساعتِ خودش می‌دود
        _uiState.value = st.copy(match = BgMatchRules.setClockRunning(m, p.opponent))
        emitSound(BgSoundEvent.DOUBLE)
        pushState()
    }

    /** پاسخ به دوبل: قبول (مکعب دو برابر و مال من) یا رد (حریف دست را می‌برد) */
    fun answerDouble(take: Boolean) {
        val st = _uiState.value
        if (!st.mustAnswerDouble) return
        if (st.netRole == BgNetRole.CLIENT) {
            client?.send(BgMessage.DoubleAnswer(take))
            return
        }
        doAnswerDouble(take)
    }

    private fun doAnswerDouble(take: Boolean) {
        settleClocks()
        val st = _uiState.value
        val offerer = st.match.doubleOfferedBy ?: return
        val game = st.game ?: return
        if (take) {
            val m = BgMatchRules.takeDouble(st.match)
            _uiState.value = st.copy(match = BgMatchRules.setClockRunning(m, offerer))
            emitSound(BgSoundEvent.MOVE)
            pushState()
        } else {
            val (finished, m) = BgMatchRules.dropDouble(st.match, game)
            currentMoves = emptyList()
            setGame(finished)
            _uiState.value = _uiState.value.copy(match = m, skipMessage = null)
            emitSound(BgSoundEvent.WIN)
            pushState()
        }
    }

    /** تسلیمِ دست جاری: محلی → بازیکنِ نوبت؛ شبکه → خودم. حریف دست را تکی با مقدار مکعب می‌برد */
    fun resign() {
        val st = _uiState.value
        val game = st.game ?: return
        if (game.phase == BgPhase.FINISHED) return
        if (st.netRole == BgNetRole.CLIENT) {
            client?.send(BgMessage.Resign)
            return
        }
        val loser = if (st.isNetPlay) st.myPlayer else game.turn
        doConcede(loser ?: return, BgGameEnd.RESIGN)
    }

    private fun doConcede(loser: BgPlayer, reason: BgGameEnd) {
        settleClocks()
        val st = _uiState.value
        val game = st.game ?: return
        if (game.phase == BgPhase.FINISHED) return
        val (finished, m) = BgMatchRules.concede(st.match, game, loser, reason)
        currentMoves = emptyList()
        setGame(finished)
        _uiState.value = _uiState.value.copy(match = m, skipMessage = null)
        emitSound(if (reason == BgGameEnd.TIMEOUT) BgSoundEvent.TIMEOUT else BgSoundEvent.WIN)
        pushState()
    }

    /** دست بعدیِ مسابقه — مهمان از میزبان می‌خواهد */
    fun nextGame() {
        val st = _uiState.value
        if (!st.gameOverMatchContinues) return
        if (st.netRole == BgNetRole.CLIENT) {
            client?.send(BgMessage.NextGameRequest)
            return
        }
        val e = engine ?: return
        currentMoves = emptyList()
        rematchCount++
        _uiState.value = st.copy(
            game = e.createGame(),
            match = BgMatchRules.beginGame(st.match),
            clockStampMs = SystemClock.elapsedRealtime(),
            selectedSource = null,
            sourcesAbs = emptySet(),
            entryIsSource = false,
            destsAbs = emptySet(),
            offIsDest = false,
            skipMessage = null,
        )
        pushState()
    }

    // ================= چت سریع =================

    fun setChatOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(chatOpen = open)
    }

    /** فرستادن یک پیام آماده — فقط در بازی شبکه‌ای؛ حباب همین لحظه روی گوشی خودم هم می‌آید */
    fun sendChat(text: String) {
        val st = _uiState.value
        val me = st.myPlayer ?: return
        val clean = text.trim().take(40)
        if (clean.isBlank()) return
        _uiState.value = _uiState.value.copy(chatOpen = false)
        showChat(me, clean)
        val msg = BgMessage.Chat(from = st.myName, text = clean)
        when (st.netRole) {
            BgNetRole.HOST -> server?.broadcast(msg)
            BgNetRole.CLIENT -> client?.send(msg)
            BgNetRole.NONE -> Unit
        }
    }

    private var chatSeq = 0

    private fun showChat(from: BgPlayer, text: String) {
        val id = ++chatSeq
        _uiState.value = _uiState.value.copy(
            chatBubbles = _uiState.value.chatBubbles + (from to BgChatBubble(text, id)),
        )
        emitSound(BgSoundEvent.CHAT)
        viewModelScope.launch {
            delay(CHAT_BUBBLE_MS)
            val cur = _uiState.value.chatBubbles
            if (cur[from]?.id == id) {
                _uiState.value = _uiState.value.copy(chatBubbles = cur - from)
            }
        }
    }

    // ================= ساعت‌ها =================

    private var clockJob: Job? = null

    /**
     * تیک‌تاک ساعت: هر چند صد میلی‌ثانیه بانکِ بازیکنِ در حال اجرا تسویه می‌شود.
     * وقتی بازی یا اتصال سالم نیست چیزی کم نمی‌شود (فقط مهر زمان جلو می‌رود).
     * مرجعِ پایان وقت میزبان/محلی است؛ مهمان فقط برای نمایش تسویه می‌کند.
     */
    private fun startClockTicker() {
        if (clockJob?.isActive == true) return
        clockJob = viewModelScope.launch {
            while (isActive) {
                delay(CLOCK_TICK_MS)
                val st = _uiState.value
                if (!st.match.hasClocks) continue
                if (st.stage != BgStage.Playing) continue
                settleClocks()
                val after = _uiState.value
                val running = after.match.clockRunning ?: continue
                if (after.netRole != BgNetRole.CLIENT && after.clocksTicking &&
                    BgMatchRules.isOutOfTime(after.match, running)
                ) {
                    doConcede(running, BgGameEnd.TIMEOUT)
                }
            }
        }
    }

    /** تسویه‌ی ساعت‌ها تا همین لحظه: اگر باید بدود کم می‌شود، وگرنه فقط مهر زمان تازه می‌شود */
    private fun settleClocks() {
        val st = _uiState.value
        val now = SystemClock.elapsedRealtime()
        if (!st.match.hasClocks) return
        val m = if (st.clocksTicking) BgMatchRules.settleClock(st.match, now - st.clockStampMs) else st.match
        _uiState.value = st.copy(match = m, clockStampMs = now)
    }

    /** ساعتِ این بازیکن از همین لحظه می‌دود (تهی = هیچ‌کس) */
    private fun runClockFor(p: BgPlayer?) {
        settleClocks()
        val st = _uiState.value
        _uiState.value = st.copy(match = BgMatchRules.setClockRunning(st.match, p))
    }

    /** لمس یک خانه‌ی مطلق صفحه: انتخاب مبدأ یا اجرای حرکت به مقصد */
    fun tapPoint(abs: Int) {
        val st = _uiState.value
        val game = st.game ?: return
        val player = game.turn ?: return
        if (game.phase != BgPhase.MOVING || !st.isMyTurn) return
        val rel = relToAbs(player, abs)
        val selected = st.selectedSource
        if (selected != null) {
            val move = currentMoves.firstOrNull { it.from == selected && it.to == rel }
            if (move != null) {
                submitMove(move)
                return
            }
            // دست به مهره (ایرانی): انتخاب قفل است — لمس جای دیگر عوضش نمی‌کند
            if (st.touchMoveActive) return
        }
        if (currentMoves.any { it.from == rel }) {
            select(rel)
        } else {
            clearSelection()
        }
    }

    /** لمس بار یا انبار مهره‌های واردنشده */
    fun tapEntry() {
        val st = _uiState.value
        if (!st.isMyTurn) return
        // دست به مهره: مهره‌ی انتخاب‌شده باید بازی شود — انتخاب عوض نمی‌شود
        if (st.touchMoveActive && st.selectedSource != null && st.selectedSource != BgMove.ENTRY) return
        if (currentMoves.any { it.from == BgMove.ENTRY }) select(BgMove.ENTRY)
    }

    /** لمس سینی مهره‌های خارج‌شده — اجرای خروج اگر مجاز باشد */
    fun tapOff() {
        val st = _uiState.value
        if (!st.isMyTurn) return
        val selected = st.selectedSource ?: return
        val move = currentMoves.firstOrNull { it.from == selected && it.to == BgMove.OFF } ?: return
        submitMove(move)
    }

    /** حرکت انتخاب‌شده: مهمان به میزبان می‌فرستد، بقیه مستقیم اعمال می‌کنند */
    private fun submitMove(move: BgMove) {
        // اولین حرکت در روش ایرانی: راهنمای «دست به مهره» دیگر لازم نیست
        val st = _uiState.value
        if (st.touchMoveActive && !st.touchMoveHintSeen) {
            _uiState.value = st.copy(touchMoveHintSeen = true)
        }
        if (_uiState.value.netRole == BgNetRole.CLIENT) {
            emitSound(BgSoundEvent.MOVE)
            client?.send(BgMessage.MoveRequest(move))
            clearSelection()
            return
        }
        applyMove(move)
    }

    /** تایید پیام «حرکتی نداری» و واگذاری نوبت */
    fun confirmSkip() {
        val st = _uiState.value
        if (st.skipMessage == null) return
        if (st.isNetPlay && !st.isMyTurn) return
        if (st.netRole == BgNetRole.CLIENT) {
            client?.send(BgMessage.SkipAck)
            return
        }
        val e = engine ?: return
        val game = st.game ?: return
        currentMoves = emptyList()
        _uiState.value = st.copy(
            game = e.endTurn(game),
            skipMessage = null,
            selectedSource = null,
            sourcesAbs = emptySet(),
            entryIsSource = false,
            destsAbs = emptySet(),
            offIsDest = false,
        )
        runClockFor(game.turn?.opponent)
        pushState()
    }

    /** بازی دوباره با همان روش — مهمان از میزبان درخواست می‌کند */
    fun playAgain() {
        if (_uiState.value.netRole == BgNetRole.CLIENT) {
            client?.send(BgMessage.RematchRequest)
            return
        }
        val e = engine ?: return
        Analytics.gameReplay()
        currentMoves = emptyList()
        rematchCount++
        val st = _uiState.value
        _uiState.value = st.copy(
            game = e.createGame(),
            match = BgMatchRules.newMatch(st.match.length, st.match.clockTotalMs),
            clockStampMs = SystemClock.elapsedRealtime(),
            selectedSource = null,
            sourcesAbs = emptySet(),
            entryIsSource = false,
            destsAbs = emptySet(),
            offIsDest = false,
            skipMessage = null,
            chatBubbles = emptyMap(),
        )
        pushState()
    }

    /** برگشت به صفحه‌ی انتخاب روش — هر اتصال شبکه‌ای بسته می‌شود */
    fun backToVariants() {
        val st = _uiState.value
        // ترکِ خواسته‌ی یک بازی اینترنتی: اتاق ذخیره‌شده هم پاک می‌شود
        // (بازی محلی یا وای‌فای به اتاقِ نیمه‌کاره‌ی قبلی دست نمی‌زند)
        val leavingOnline = st.isNetPlay && st.onlineMode
        stopNetworking()
        clockJob?.cancel()
        clockJob = null
        engine = null
        currentMoves = emptyList()
        if (leavingOnline) OnlineSessionStore.clear(getApplication())
        _uiState.value = BgUiState(
            myName = st.myName,
            resumable = if (leavingOnline) null else st.resumable,
            matchLength = st.matchLength,
            clockMinutes = st.clockMinutes,
        )
    }

    // ================= فرمان‌های مهمان (سمت میزبان) =================

    /** میزبان: فرمان مهمان فقط در نوبت خودِ او و بعد از تایید موتور اثر می‌کند */
    private fun handleGuestCommand(playerName: String, msg: BgMessage) {
        val st = _uiState.value
        if (st.netRole != BgNetRole.HOST) return
        if (playerName.trim() != st.room.guestName.trim()) return
        val game = st.game ?: return
        val guest = st.room.guestPlayer
        when (msg) {
            is BgMessage.RollRequest -> {
                if (game.phase == BgPhase.ROLLING && game.turn == guest && st.match.doubleOfferedBy == null) {
                    settleClocks()
                    emitSound(BgSoundEvent.DICE)
                    setGame(engine?.rollTurn(game) ?: return)
                    bumpRoll()
                    afterDiceReady()
                }
            }

            is BgMessage.DoubleOffer -> {
                if (st.cubeAllowed && game.phase == BgPhase.ROLLING && game.turn == guest &&
                    BgMatchRules.canDouble(st.match, guest)
                ) {
                    doOfferDouble(guest)
                }
            }

            is BgMessage.DoubleAnswer -> {
                if (st.match.doubleOfferedBy == guest.opponent) doAnswerDouble(msg.take)
            }

            is BgMessage.Resign -> {
                if (game.phase != BgPhase.FINISHED) doConcede(guest, BgGameEnd.RESIGN)
            }

            is BgMessage.NextGameRequest -> {
                if (st.gameOverMatchContinues) nextGame()
            }

            is BgMessage.Chat -> {
                val clean = msg.text.trim().take(40)
                if (clean.isBlank()) return
                showChat(guest, clean)
                // بازپخش با همان فرستنده؛ خودِ مهمان پژواکش را نادیده می‌گیرد
                server?.broadcast(BgMessage.Chat(from = playerName, text = clean))
            }

            is BgMessage.MoveRequest -> {
                if (game.phase == BgPhase.MOVING && game.turn == guest &&
                    currentMoves.contains(msg.move)
                ) {
                    applyMove(msg.move)
                }
            }

            is BgMessage.SkipAck -> {
                if (st.skipMessage != null && game.turn == guest) {
                    val e = engine ?: return
                    currentMoves = emptyList()
                    _uiState.value = _uiState.value.copy(
                        game = e.endTurn(game),
                        skipMessage = null,
                        selectedSource = null,
                        sourcesAbs = emptySet(),
                        entryIsSource = false,
                        destsAbs = emptySet(),
                        offIsDest = false,
                    )
                    runClockFor(guest.opponent)
                    pushState()
                }
            }

            is BgMessage.RematchRequest -> {
                if (st.matchOver) playAgain()
            }

            else -> Unit
        }
    }

    // ================= هسته‌ی همگام‌سازی =================

    /** عکس اتاق برای پخش — همه‌چیز از همین می‌آید */
    private fun roomSnapshot(): BgRoomSnapshot {
        val st = _uiState.value
        return st.room.copy(
            variant = st.variant ?: st.room.variant,
            game = st.game,
            hostName = st.myName,
            skipMessage = st.skipMessage,
            rematchCount = rematchCount,
            match = st.match,
        )
    }

    /** اگر میزبانیم، وضعیت تازه برای مهمان پخش می‌شود (و در اینترنتی روی دیسک هم می‌ماند) */
    private fun pushState() {
        if (_uiState.value.netRole != BgNetRole.HOST) return
        // بانک ساعت‌ها تا همین لحظه تسویه می‌شود تا عکسِ پخش‌شده تازه باشد
        settleClocks()
        server?.broadcast(BgMessage.State(roomSnapshot()))
        persistHostState()
    }

    private fun setGame(game: BgState) {
        _uiState.value = _uiState.value.copy(
            game = game,
            selectedSource = null,
            sourcesAbs = emptySet(),
            entryIsSource = false,
            destsAbs = emptySet(),
            offIsDest = false,
        )
    }

    /** بعد از آماده‌شدن تاس‌ها: حرکت‌ها را بساز و اگر هیچ نبود پیام رد شدن بده */
    private fun afterDiceReady() {
        refreshMoves()
        if (currentMoves.isEmpty()) {
            emitSound(BgSoundEvent.SKIP)
            _uiState.value = _uiState.value.copy(
                skipMessage = "هیچ حرکت قانونی‌ای نداری! نوبتت می‌سوزه 😬",
            )
        }
        pushState()
    }

    /**
     * حرکت‌های مجاز این لحظه — روی هر دو گوشی از همان موتور خالص حساب می‌شود:
     * میزبان برای اعتبارسنجی و اعمال، مهمان فقط برای هایلایت و ساختن درخواست.
     */
    private fun refreshMoves() {
        val st = _uiState.value
        val game = st.game
        val player = game?.turn
        currentMoves = if (player == null || game.phase != BgPhase.MOVING) {
            emptyList()
        } else {
            BgMoveGenerator.legalMoves(game)
        }
        // در شبکه وقتی نوبت من نیست، هایلایت و انتخابی در کار نیست
        if (st.isNetPlay && !st.isMyTurn) {
            _uiState.value = _uiState.value.copy(
                selectedSource = null,
                sourcesAbs = emptySet(),
                entryIsSource = false,
                destsAbs = emptySet(),
                offIsDest = false,
            )
            return
        }
        val sourcesAbs = currentMoves
            .filter { it.from != BgMove.ENTRY }
            .map { relToAbs(player!!, it.from) }
            .toSet()
        val entryIsSource = currentMoves.any { it.from == BgMove.ENTRY }
        _uiState.value = _uiState.value.copy(
            selectedSource = null,
            sourcesAbs = sourcesAbs,
            entryIsSource = entryIsSource,
            destsAbs = emptySet(),
            offIsDest = false,
        )
        // وقتی تنها مبدأ ممکن ورود است، خودکار انتخابش کن تا مقصدها فوری دیده شوند
        if (entryIsSource) select(BgMove.ENTRY)
        else {
            val distinctSources = currentMoves.map { it.from }.distinct()
            if (distinctSources.size == 1) select(distinctSources.first())
        }
    }

    private fun select(source: Int) {
        val game = _uiState.value.game ?: return
        val player = game.turn ?: return
        val moves = currentMoves.filter { it.from == source }
        _uiState.value = _uiState.value.copy(
            selectedSource = source,
            destsAbs = moves.filter { it.to != BgMove.OFF }.map { relToAbs(player, it.to) }.toSet(),
            offIsDest = moves.any { it.to == BgMove.OFF },
        )
    }

    private fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedSource = null,
            destsAbs = emptySet(),
            offIsDest = false,
        )
    }

    /** اعمال حرکت روی موتور (میزبان یا حالت محلی) + پخش وضعیت تازه */
    private fun applyMove(move: BgMove) {
        val e = engine ?: return
        val game = _uiState.value.game ?: return
        val next = e.applyMove(game, move)
        emitSound(
            when {
                next.phase == BgPhase.FINISHED -> BgSoundEvent.WIN
                move.hit -> BgSoundEvent.HIT
                move.to == BgMove.OFF -> BgSoundEvent.BEAR_OFF
                else -> BgSoundEvent.MOVE
            },
        )
        setGame(next)
        if (next.phase == BgPhase.FINISHED) {
            currentMoves = emptyList()
            settleClocks()
            val winner = next.winner ?: game.turn!!
            _uiState.value = _uiState.value.copy(
                match = BgMatchRules.recordGameResult(_uiState.value.match, winner, next.resultScore),
            )
            pushState()
            return
        }
        if (next.remainingDice.isEmpty()) {
            // همه‌ی تاس‌ها مصرف شد — نوبت خودکار عوض می‌شود
            currentMoves = emptyList()
            setGame(e.endTurn(next))
            runClockFor(game.turn?.opponent)
            refreshMoves()
            pushState()
            return
        }
        refreshMoves()
        if (currentMoves.isEmpty()) {
            emitSound(BgSoundEvent.SKIP)
            _uiState.value = _uiState.value.copy(
                skipMessage = "با تاس‌های باقی‌مونده حرکتی نداری — نوبت رد می‌شه",
            )
        }
        pushState()
    }

    // ================= خروج و پاک‌سازی =================

    private fun stopNetworking() {
        nsd.release()
        client?.close()
        client = null
        server?.stop()
        server = null
        keepAlive.release()
    }

    /** بسته شدن صفحه یا کشته شدن اپ: شبکه جمع می‌شود ولی اتاقِ ذخیره‌شده می‌ماند تا ادامه بدهد */
    override fun onCleared() {
        super.onCleared()
        stopNetworking()
    }

    companion object {
        /** شناسه‌ی بازی در حافظه‌ی اتاق‌های اینترنتی */
        const val GAME_ID = "backgammon"

        /** فاصله‌ی تیک ساعت‌ها */
        const val CLOCK_TICK_MS = 250L

        /** حباب چت چند میلی‌ثانیه می‌ماند */
        const val CHAT_BUBBLE_MS = 3000L
    }
}

/** نام روش بازی برای گزارش‌گیری — رشته‌ی ثابت و مستقل از ترجمه */
private val BgVariant.analyticsName: String
    get() = when (this) {
        BgVariant.STANDARD -> "standard"
        BgVariant.DUTCH -> "dutch"
        BgVariant.HYPER -> "hypergammon"
        BgVariant.IRANI -> "irani"
    }
