package com.navidabbasian.kibord.games.backgammon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.cloud.Cloud
import com.navidabbasian.kibord.core.net.ClientLink
import com.navidabbasian.kibord.core.net.HostKeepAlive
import com.navidabbasian.kibord.core.net.HostLink
import com.navidabbasian.kibord.core.net.online.OnlineClient
import com.navidabbasian.kibord.core.net.online.OnlineHost
import com.navidabbasian.kibord.core.net.online.OnlineRooms
import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
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
import kotlinx.coroutines.launch

/** مرحله‌های صفحه‌ای بازی تخته‌نرد */
enum class BgStage { VariantSelect, ModeSelect, NetEntry, NetJoin, NetLobby, Playing }

/** نقش این گوشی: بدون شبکه (دو نفر روی همین گوشی)، میزبان یا مهمان */
enum class BgNetRole { NONE, HOST, CLIENT }

/** رویدادهای صوتی که رابط کاربری به صدای مناسب ترجمه می‌کند */
enum class BgSoundEvent { DICE, MOVE, HIT, BEAR_OFF, SKIP, WIN }

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
    /** کد اتاقِ ساخته‌شده — فقط برای میزبان اینترنتی */
    val roomCode: String = "",
) {
    val isNetPlay: Boolean get() = netRole != BgNetRole.NONE

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
        _uiState.value = BgUiState(
            stage = BgStage.Playing,
            variant = variant,
            game = e.createGame(),
            myName = _uiState.value.myName,
        )
    }

    /** بازی شبکه‌ای — برو به صفحه‌ی اسم و میزبان/مهمان */
    fun chooseNetworkMode() {
        _uiState.value = _uiState.value.copy(stage = BgStage.NetEntry, connectError = null)
    }

    fun setMyName(name: String) {
        _uiState.value = _uiState.value.copy(myName = name.take(16))
    }

    fun setOnlineMode(on: Boolean) {
        _uiState.value = _uiState.value.copy(onlineMode = on, connectError = null)
    }

    fun backFromModeSelect() {
        _uiState.value = _uiState.value.copy(stage = BgStage.VariantSelect)
    }

    fun backFromNetEntry() {
        _uiState.value = _uiState.value.copy(stage = BgStage.ModeSelect, connectError = null, connecting = false)
    }

    fun backFromJoin() {
        nsd.stopDiscovery()
        _uiState.value = _uiState.value.copy(stage = BgStage.NetEntry, connectError = null, connecting = false)
    }

    /** میزبان از لابی منصرف شد — سرور جمع می‌شود */
    fun cancelHosting() {
        stopNetworking()
        _uiState.value = _uiState.value.copy(
            stage = BgStage.NetEntry,
            netRole = BgNetRole.NONE,
            room = BgRoomSnapshot(),
            roomCode = "",
            hostAddress = "",
            game = null,
        )
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
        val name = _uiState.value.myName.trim()
        val variant = _uiState.value.variant ?: return
        if (name.isBlank()) return
        if (!Cloud.isConfigured) {
            _uiState.value = _uiState.value.copy(connectError = "بخش آنلاین روی این نسخه فعال نیست")
            return
        }
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
            keepAlive.acquire()
            becomeHost(name, variant, roomCode = host.roomCode, hostAddress = "")
        }
    }

    /** برپایی مشترک میزبان: بازی ساخته می‌شود و به لابی انتظار می‌رویم */
    private fun becomeHost(name: String, variant: BgVariant, roomCode: String, hostAddress: String) {
        val e = BgEngine(BgRules.of(variant))
        engine = e
        currentMoves = emptyList()
        rematchCount = 0
        _uiState.value = _uiState.value.copy(
            stage = BgStage.NetLobby,
            netRole = BgNetRole.HOST,
            myName = name,
            game = e.createGame(),
            room = BgRoomSnapshot(variant = variant, hostName = name),
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
        )
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
        val name = _uiState.value.myName.trim()
        if (name.isBlank()) return
        val clean = OnlineRooms.normalizeCode(code)
        _uiState.value = _uiState.value.copy(myName = name, connecting = true, connectError = null)
        val c = OnlineClient<BgMessage>(
            scope = viewModelScope,
            encode = { m: BgMessage -> m.encode() },
            decode = ::decodeBgMessage,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.netRole == BgNetRole.CLIENT) {
                    _uiState.value = _uiState.value.copy(lostConnection = true)
                }
            },
        )
        client = c
        c.connect(clean, name) { error ->
            if (error != null) {
                client = null
                _uiState.value = _uiState.value.copy(connecting = false, connectError = error)
            } else {
                _uiState.value = _uiState.value.copy(
                    netRole = BgNetRole.CLIENT,
                    connecting = false,
                    connectError = null,
                )
            }
        }
    }

    /** مهمان: هرچه میزبان پخش کرد، همان حقیقت است */
    private fun handleServerMessage(msg: BgMessage) {
        val room = (msg as? BgMessage.State)?.room ?: return
        val before = _uiState.value.game
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
        )
        if (before?.phase != BgPhase.FINISHED && room.game?.phase == BgPhase.FINISHED) {
            emitSound(BgSoundEvent.WIN)
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
        if (next.phase == BgPhase.MOVING) afterDiceReady() else pushState()
    }

    /** پرتاب دو تاس نوبت — مهمان درخواستش را برای میزبان می‌فرستد */
    fun rollDice() {
        val st = _uiState.value
        val game = st.game ?: return
        if (game.phase != BgPhase.ROLLING) return
        if (!st.isMyTurn) return
        if (st.netRole == BgNetRole.CLIENT) {
            // صدا و انیمیشن با رسیدن وضعیتِ تازه از میزبان کوک می‌شوند
            client?.send(BgMessage.RollRequest)
            return
        }
        emitSound(BgSoundEvent.DICE)
        setGame(engine?.rollTurn(game) ?: return)
        bumpRoll()
        afterDiceReady()
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
        }
        if (currentMoves.any { it.from == rel }) {
            select(rel)
        } else {
            clearSelection()
        }
    }

    /** لمس بار یا انبار مهره‌های واردنشده */
    fun tapEntry() {
        if (!_uiState.value.isMyTurn) return
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
        pushState()
    }

    /** بازی دوباره با همان روش — مهمان از میزبان درخواست می‌کند */
    fun playAgain() {
        if (_uiState.value.netRole == BgNetRole.CLIENT) {
            client?.send(BgMessage.RematchRequest)
            return
        }
        val e = engine ?: return
        currentMoves = emptyList()
        rematchCount++
        _uiState.value = _uiState.value.copy(
            game = e.createGame(),
            selectedSource = null,
            sourcesAbs = emptySet(),
            entryIsSource = false,
            destsAbs = emptySet(),
            offIsDest = false,
            skipMessage = null,
        )
        pushState()
    }

    /** برگشت به صفحه‌ی انتخاب روش — هر اتصال شبکه‌ای بسته می‌شود */
    fun backToVariants() {
        stopNetworking()
        engine = null
        currentMoves = emptyList()
        _uiState.value = BgUiState(myName = _uiState.value.myName)
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
                if (game.phase == BgPhase.ROLLING && game.turn == guest) {
                    emitSound(BgSoundEvent.DICE)
                    setGame(engine?.rollTurn(game) ?: return)
                    bumpRoll()
                    afterDiceReady()
                }
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
                    pushState()
                }
            }

            is BgMessage.RematchRequest -> {
                if (game.phase == BgPhase.FINISHED) playAgain()
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
        )
    }

    /** اگر میزبانیم، وضعیت تازه برای مهمان پخش می‌شود */
    private fun pushState() {
        if (_uiState.value.netRole != BgNetRole.HOST) return
        server?.broadcast(BgMessage.State(roomSnapshot()))
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
            pushState()
            return
        }
        if (next.remainingDice.isEmpty()) {
            // همه‌ی تاس‌ها مصرف شد — نوبت خودکار عوض می‌شود
            currentMoves = emptyList()
            setGame(e.endTurn(next))
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

    override fun onCleared() {
        super.onCleared()
        stopNetworking()
    }
}
