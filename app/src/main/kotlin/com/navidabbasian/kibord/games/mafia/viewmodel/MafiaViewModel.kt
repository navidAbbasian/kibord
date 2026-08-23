package com.navidabbasian.kibord.games.mafia.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.net.HostKeepAlive
import com.navidabbasian.kibord.core.cloud.Cloud
import com.navidabbasian.kibord.core.cloud.AccountRepository
import com.navidabbasian.kibord.core.net.ClientLink
import com.navidabbasian.kibord.core.net.HostLink
import com.navidabbasian.kibord.core.net.online.OnlineClient
import com.navidabbasian.kibord.core.net.online.OnlineHost
import com.navidabbasian.kibord.core.net.online.OnlineRooms
import com.navidabbasian.kibord.core.net.online.OnlineSessionStore
import com.navidabbasian.kibord.core.net.online.StoredOnlineRoom
import com.navidabbasian.kibord.games.mafia.net.decodeMfMessage
import com.navidabbasian.kibord.games.mafia.net.encode
import com.navidabbasian.kibord.games.esmfamil.model.nameKey
import com.navidabbasian.kibord.games.esmfamil.model.sameName
import com.navidabbasian.kibord.games.mafia.model.MF_MAX_PLAYERS
import com.navidabbasian.kibord.games.mafia.model.MF_MIN_PLAYERS
import com.navidabbasian.kibord.games.mafia.model.MfPhase
import com.navidabbasian.kibord.games.mafia.model.MfPlayer
import com.navidabbasian.kibord.games.mafia.model.MfRole
import com.navidabbasian.kibord.games.mafia.model.MfSnapshot
import com.navidabbasian.kibord.games.mafia.model.MfWinner
import com.navidabbasian.kibord.games.mafia.model.mafiaCountFor
import com.navidabbasian.kibord.games.mafia.net.MfClient
import com.navidabbasian.kibord.games.mafia.net.MfDiscoveredGame
import com.navidabbasian.kibord.games.mafia.net.MfMessage
import com.navidabbasian.kibord.games.mafia.net.MfNsd
import com.navidabbasian.kibord.games.mafia.net.MfServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** نقش این گوشی در ارتباط شبکه */
enum class MfNetRole { NONE, HOST, CLIENT }

/** صفحه‌های محلیِ قبل از ورود به جریان مشترک بازی */
enum class MfLocalScreen { ENTRY, JOIN, IN_GAME }

data class MafiaUiState(
    val role: MfNetRole = MfNetRole.NONE,
    val localScreen: MfLocalScreen = MfLocalScreen.ENTRY,
    val myName: String = "",
    val snapshot: MfSnapshot = MfSnapshot(),
    /** بازی‌های پیداشده در شبکه برای پیوستن */
    val discovered: List<MfDiscoveredGame> = emptyList(),
    val connecting: Boolean = false,
    val connectError: String? = null,
    /** آدرس این گوشی برای اتصال دستی مهمان‌ها */
    val hostAddress: String = "",
    val hostPort: Int = 0,
    /** ارتباط با میزبان قطع شد */
    val lostConnection: Boolean = false,
    /** بازی اینترنتی با کد اتاق، به‌جای وای‌فای محلی */
    val onlineMode: Boolean = false,
    /** کد اتاق اینترنتی — میزبان: کدِ ساخته‌شده؛ مهمان: کدی که باهاش وصل شده */
    val roomCode: String = "",
    /** بازی اینترنتیِ نیمه‌کاره‌ای که روی گوشی مانده و می‌شود ادامه‌اش داد */
    val resumable: StoredOnlineRoom? = null,
    /** مهمان: میزبان لحظه‌ای غایب شده و منتظر برگشتش هستیم */
    val hostAway: Boolean = false,
    /** مهمان: در حال وصل‌شدن دوباره به همان اتاق */
    val reconnecting: Boolean = false,
) {
    val isHost: Boolean get() = role == MfNetRole.HOST
    val me: MfPlayer? get() = snapshot.player(myName)
    val myRole: MfRole? get() = snapshot.roleOf(myName)
    val iAmAlive: Boolean get() = me?.alive == true
    val iHaveSeen: Boolean get() = snapshot.hasSeen(myName)
    val myDayVote: String? get() = snapshot.dayVoteOf(myName)
    val myMafiaVote: String? get() = snapshot.mafiaVoteOf(myName)

    /** من امشب اقدامم را انجام داده‌ام؟ */
    val iActedTonight: Boolean
        get() = when (myRole) {
            MfRole.MAFIA -> myMafiaVote != null
            MfRole.DOCTOR -> snapshot.doctorSave.isNotBlank()
            MfRole.DETECTIVE -> snapshot.detectiveCheck.isNotBlank()
            else -> true
        }
}

/**
 * موتور شب مافیا — دو نقش در یک ویومدل:
 * میزبان: مرجع حقیقت؛ نقش‌ها را قرعه می‌زند، شب را جمع‌بندی و رای روز را می‌شمارد.
 * مهمان: فرمان می‌فرستد و هرچه از میزبان رسید همان را نشان می‌دهد.
 */
class MafiaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MafiaUiState())
    val uiState: StateFlow<MafiaUiState> = _uiState.asStateFlow()

    private val nsd = MfNsd(application)
    private val keepAlive = HostKeepAlive(application)
    private var server: HostLink<MfMessage>? = null
    private var client: ClientLink<MfMessage>? = null

    /** آخرین آدرس محلی‌ای که مهمان بهش وصل شد — برای «دوباره وصل شو» در وای‌فای */
    private var lastLanAddress: String = ""
    private var lastLanPort: Int = MfServer.BASE_PORT

    private val ctx: Application get() = getApplication()

    init {
        // اگر بازی اینترنتیِ نیمه‌کاره‌ای روی گوشی مانده، در صفحه‌ی ورود پیشنهادش بده
        _uiState.update { it.copy(resumable = OnlineSessionStore.load(ctx, GAME_ID)) }
    }

    // ================= ورود =================

    fun setMyName(name: String) {
        // در حالت اینترنتی اسم قفل است: همان یوزرنیم حساب
        if (_uiState.value.onlineMode) return
        _uiState.update { it.copy(myName = name.take(16)) }
    }

    // ================= میزبانی =================

    fun startHosting() {
        val name = _uiState.value.myName.trim()
        if (name.isBlank()) return
        val srv = MfServer(
            scope = viewModelScope,
            onClientJoin = ::acceptJoin,
            onCommand = ::handleCommand,
            onClientDisconnected = ::handleDisconnect,
            latestState = { MfMessage.State(_uiState.value.snapshot) },
        )
        if (!srv.start()) {
            _uiState.update { it.copy(connectError = "سرور روی این گوشی راه نیفتاد") }
            return
        }
        server = srv
        keepAlive.acquire()
        nsd.register(name, srv.port)
        val snapshot = MfSnapshot(
            phase = MfPhase.LOBBY,
            players = listOf(MfPlayer(name = name, colorIndex = 0)),
            hostName = name,
        )
        _uiState.update {
            it.copy(
                role = MfNetRole.HOST,
                localScreen = MfLocalScreen.IN_GAME,
                // اسم محلی باید دقیقاً همان اسمِ ثبت‌شده در بازی باشد وگرنه
                // مقایسه‌ی نقش/رای با فاصله‌ی انتهایی کیبورد به هم می‌ریزد
                myName = name,
                hostAddress = MfNsd.localIpAddress() ?: "",
                hostPort = srv.port,
                connectError = null,
            )
        }
        setSnapshot(snapshot)
    }

    fun setOnlineMode(on: Boolean) {
        if (!on) {
            _uiState.update { it.copy(onlineMode = false, connectError = null) }
            return
        }
        // بازی اینترنتی فقط با حساب: اسم بازیکن همان یوزرنیم است تا آمار و
        // رتبه‌بندی به آدم درست بچسبد
        val me = AccountRepository.onlineIdentity()
        if (me == null) {
            _uiState.update { it.copy(onlineMode = false, connectError = AccountRepository.NEED_ACCOUNT_MESSAGE) }
            return
        }
        _uiState.update { it.copy(onlineMode = true, myName = me, connectError = null) }
    }

    /** میزبانی اینترنتی: به‌جای سوکت محلی، اتاقی با کد شش‌حرفی ساخته می‌شود */
    fun startHostingOnline() {
        if (!Cloud.isConfigured) {
            _uiState.update { it.copy(connectError = "بخش آنلاین روی این نسخه فعال نیست") }
            return
        }
        val name = AccountRepository.onlineIdentity() ?: run {
            _uiState.update { it.copy(connectError = AccountRepository.NEED_ACCOUNT_MESSAGE) }
            return
        }
        _uiState.update { it.copy(myName = name, onlineMode = true) }
        val snapshot = MfSnapshot(
            phase = MfPhase.LOBBY,
            players = listOf(MfPlayer(name = name, colorIndex = 0)),
            hostName = name,
        )
        hostOnline(name = name, snapshot = snapshot, roomCode = null)
    }

    /**
     * برپایی اتاق اینترنتی با وضعیت داده‌شده. [roomCode] تهی یعنی اتاق تازه؛
     * وگرنه همان اتاق قبلی دوباره ساخته می‌شود تا مهمان‌های منتظر برگردند.
     */
    private fun hostOnline(name: String, snapshot: MfSnapshot, roomCode: String?) {
        _uiState.update { it.copy(connecting = true, connectError = null) }
        val host = OnlineHost<MfMessage>(
            scope = viewModelScope,
            encode = { m: MfMessage -> m.encode() },
            onClientJoin = ::acceptJoin,
            onCommand = ::handleCommand,
            onClientDisconnected = ::handleDisconnect,
            latestState = { MfMessage.State(_uiState.value.snapshot) },
            decode = ::decodeMfMessage,
            roomCode = roomCode ?: OnlineRooms.newCode(),
        )
        host.start { ok ->
            if (!ok) {
                _uiState.update {
                    it.copy(connecting = false, connectError = "اتاق ساخته نشد — اینترنت رو چک کن")
                }
                return@start
            }
            server = host
            // زیر قفل گوشی هم اتاق زنده بماند
            keepAlive.acquire(lan = false)
            _uiState.update {
                it.copy(
                    role = MfNetRole.HOST,
                    localScreen = MfLocalScreen.IN_GAME,
                    myName = name,
                    roomCode = host.roomCode,
                    hostAddress = "",
                    hostPort = 0,
                    connecting = false,
                    connectError = null,
                    resumable = null,
                    lostConnection = false,
                    hostAway = false,
                )
            }
            setSnapshot(snapshot)
            // اتاق روی دیسک می‌ماند تا با بسته‌شدن اپ یا قفل گوشی نپرد
            OnlineSessionStore.save(
                ctx,
                StoredOnlineRoom(
                    gameId = GAME_ID,
                    role = "host",
                    code = host.roomCode,
                    name = name,
                    savedAt = System.currentTimeMillis(),
                    hostState = MfMessage.State(snapshot).encode(),
                ),
            )
        }
    }

    /** پیوستن اینترنتی با کد اتاق */
    fun joinOnlineRoom(code: String) {
        val name = AccountRepository.onlineIdentity() ?: run {
            _uiState.update { it.copy(connectError = AccountRepository.NEED_ACCOUNT_MESSAGE) }
            return
        }
        _uiState.update { it.copy(myName = name, onlineMode = true) }
        connectOnline(OnlineRooms.normalizeCode(code), name) { error ->
            if (error != null) {
                _uiState.update { it.copy(connecting = false, connectError = error) }
            }
        }
    }

    /**
     * اتصال مهمان اینترنتی به اتاق. اگر وصل شد خودش وارد بازی می‌شود و اتاق را
     * ذخیره می‌کند؛ نتیجه (تهی = موفق) به [onDone] هم می‌رسد تا هر مسیر
     * خطایش را جای خودش نشان دهد.
     */
    private fun connectOnline(code: String, name: String, onDone: (String?) -> Unit) {
        _uiState.update { it.copy(connecting = true, connectError = null) }
        val c = OnlineClient<MfMessage>(
            scope = viewModelScope,
            encode = { m: MfMessage -> m.encode() },
            decode = ::decodeMfMessage,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.role == MfNetRole.CLIENT) {
                    _uiState.update { it.copy(lostConnection = true, hostAway = false) }
                }
            },
            onHostAway = { away ->
                if (_uiState.value.role == MfNetRole.CLIENT) {
                    _uiState.update { it.copy(hostAway = away) }
                }
            },
        )
        client = c
        c.connect(code, name) { error ->
            if (error != null) {
                if (client === c) client = null
                _uiState.update { it.copy(connecting = false, reconnecting = false) }
                onDone(error)
            } else {
                _uiState.update {
                    it.copy(
                        role = MfNetRole.CLIENT,
                        localScreen = MfLocalScreen.IN_GAME,
                        roomCode = code,
                        connecting = false,
                        reconnecting = false,
                        connectError = null,
                        resumable = null,
                        lostConnection = false,
                        hostAway = false,
                    )
                }
                OnlineSessionStore.save(
                    ctx,
                    StoredOnlineRoom(
                        gameId = GAME_ID,
                        role = "guest",
                        code = code,
                        name = name,
                        savedAt = System.currentTimeMillis(),
                    ),
                )
                onDone(null)
            }
        }
    }

    // ================= ادامه‌ی بازی نیمه‌کاره =================

    /** ادامه‌ی بازی اینترنتی‌ای که روی گوشی مانده — میزبان یا مهمان */
    fun resumeOnline() {
        val room = _uiState.value.resumable ?: return
        if (_uiState.value.connecting) return
        if (!Cloud.isConfigured) {
            _uiState.update { it.copy(connectError = "بخش آنلاین روی این نسخه فعال نیست") }
            return
        }
        // اسم همان اسمِ ذخیره‌شده است؛ اگر حساب عوض شده باشد، میزبان ما را نمی‌شناسد
        val name = room.name
        _uiState.update { it.copy(myName = name, onlineMode = true, connectError = null) }
        if (room.isHost) {
            // وضعیت کامل از روی دیسک؛ اگر خراب بود، لابی تازه با همان کد
            val saved = room.hostState
                ?.let { decodeMfMessage(it) as? MfMessage.State }
                ?.snapshot
                ?.takeIf { it.players.any { p -> sameName(p.name, name) } }
            val fresh = MfSnapshot(
                phase = MfPhase.LOBBY,
                players = listOf(MfPlayer(name = name, colorIndex = 0)),
                hostName = name,
            )
            // بقیه را «قطع» علامت می‌زنیم تا با join دوباره‌شان وصل شوند
            val snapshot = (saved ?: fresh).let { s ->
                s.copy(
                    hostName = name,
                    players = s.players.map { p -> p.copy(connected = sameName(p.name, name)) },
                )
            }
            hostOnline(name = name, snapshot = snapshot, roomCode = room.code)
        } else {
            connectOnline(room.code, name) { error ->
                // خطا را همان‌جا نشان می‌دهیم و کارت «ادامه» می‌ماند تا دوباره بزند یا بی‌خیال شود
                if (error != null) _uiState.update { it.copy(connectError = error) }
            }
        }
    }

    /** بی‌خیالِ بازی نیمه‌کاره — اتاق از روی گوشی پاک می‌شود */
    fun discardResume() {
        OnlineSessionStore.clear(ctx)
        _uiState.update { it.copy(resumable = null, connectError = null) }
    }

    /** مهمان: بعد از «ارتباط قطع شد»، با همان اسم به همان اتاق/میزبان برگرد */
    fun reconnect() {
        val st = _uiState.value
        if (st.role != MfNetRole.CLIENT || st.reconnecting) return
        client?.close()
        client = null
        _uiState.update { it.copy(lostConnection = false, reconnecting = true, connectError = null) }
        if (st.onlineMode) {
            if (st.roomCode.isBlank()) {
                _uiState.update { it.copy(lostConnection = true, reconnecting = false) }
                return
            }
            connectOnline(st.roomCode, st.myName) { error ->
                if (error != null) _uiState.update { it.copy(lostConnection = true, connectError = error) }
            }
        } else {
            if (lastLanAddress.isBlank()) {
                _uiState.update { it.copy(lostConnection = true, reconnecting = false) }
                return
            }
            connectLan(lastLanAddress, lastLanPort, st.myName) { error ->
                if (error != null) _uiState.update { it.copy(lostConnection = true, connectError = error) }
            }
        }
    }

    /** بررسی ورود مهمان جدید — تهی یعنی پذیرفته شد */
    private fun acceptJoin(name: String): String? {
        val snapshot = _uiState.value.snapshot
        val existing = snapshot.player(name)
        return when {
            name.isBlank() -> "اسم خالی است"
            existing != null && !existing.connected -> {
                // برگشتِ بازیکن قطع‌شده با همان اسم
                mutateSnapshot { s ->
                    s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = true) else it })
                }
                null
            }

            existing != null -> "این اسم الان توی بازیه — یک اسم دیگر انتخاب کن"
            snapshot.phase != MfPhase.LOBBY -> "بازی شروع شده — منتظر دست بعدی باش"
            snapshot.players.size >= MF_MAX_PLAYERS -> "ظرفیت بازی پر است"
            else -> {
                mutateSnapshot { s ->
                    s.copy(players = s.players + MfPlayer(name = name, colorIndex = s.players.size))
                }
                null
            }
        }
    }

    private fun handleDisconnect(name: String) {
        if (_uiState.value.role != MfNetRole.HOST) return
        mutateSnapshot { s ->
            s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = false) else it })
        }
        // با رفتن یک نفر، شرط «همه دیدند/همه اقدام کردند» شاید همین حالا برقرار شود
        maybeFinishReveal()
        maybeResolveNight()
        maybeFinishDayVote()
    }

    // ================= شروع بازی =================

    fun startGame() = hostOnly {
        val s = _uiState.value.snapshot
        val connected = s.players.filter { it.connected }
        if (connected.size < MF_MIN_PLAYERS) return@hostOnly
        // قرعه‌ی نقش‌ها: مافیا(ها)، دکتر، کارآگاه و بقیه شهروند
        val shuffled = connected.shuffled()
        val mafiaCount = mafiaCountFor(connected.size)
        Analytics.gameSetup("players" to connected.size, "mafia_count" to mafiaCount)
        val roles = mutableMapOf<String, MfRole>()
        shuffled.forEachIndexed { i, p ->
            roles[p.name] = when {
                i < mafiaCount -> MfRole.MAFIA
                i == mafiaCount -> MfRole.DOCTOR
                i == mafiaCount + 1 -> MfRole.DETECTIVE
                else -> MfRole.CITIZEN
            }
        }
        mutateSnapshot { snap ->
            snap.copy(
                phase = MfPhase.ROLE_REVEAL,
                players = snap.players.map { it.copy(alive = it.connected) },
                roles = roles,
                nightIndex = 1,
                seen = emptyList(),
                mafiaVotes = emptyMap(),
                doctorSave = "",
                detectiveCheck = "",
                lastKilled = "",
                lastSaved = false,
                dayVotes = emptyMap(),
                lastLynched = "",
                winner = MfWinner.NONE,
            )
        }
    }

    // ================= پیوستن مهمان =================

    fun openJoinScreen() {
        if (_uiState.value.myName.isBlank()) return
        _uiState.update { it.copy(localScreen = MfLocalScreen.JOIN, discovered = emptyList(), connectError = null) }
        if (_uiState.value.onlineMode) return // اینترنتی: با کد می‌آیند
        nsd.discover(
            onFound = { game ->
                _uiState.update { st ->
                    st.copy(discovered = st.discovered.filter { it.hostName != game.hostName } + game)
                }
            },
            onLost = { name ->
                _uiState.update { st -> st.copy(discovered = st.discovered.filter { it.hostName != name }) }
            },
        )
    }

    fun joinGame(address: String, port: Int = MfServer.BASE_PORT) {
        val name = _uiState.value.myName.trim()
        if (name.isBlank() || address.isBlank()) return
        // اسم محلی از همین‌جا با اسمِ ارسالی به میزبان یکسان می‌شود (بدون فاصله‌های سر و ته)
        _uiState.update { it.copy(myName = name) }
        connectLan(address, port, name) { error ->
            if (error != null) _uiState.update { it.copy(connectError = error) }
        }
    }

    /** اتصال سوکتی به میزبان محلی؛ نتیجه (تهی = موفق) به [onDone] می‌رسد */
    private fun connectLan(address: String, port: Int, name: String, onDone: (String?) -> Unit) {
        _uiState.update { it.copy(connecting = true, connectError = null) }
        val c = MfClient(
            scope = viewModelScope,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.role == MfNetRole.CLIENT) {
                    _uiState.update { it.copy(lostConnection = true) }
                }
            },
        )
        client = c
        c.connect(address, port, name) { error ->
            if (error != null) {
                if (client === c) client = null
                _uiState.update { it.copy(connecting = false, reconnecting = false) }
                onDone(error)
            } else {
                nsd.stopDiscovery()
                lastLanAddress = address
                lastLanPort = port
                _uiState.update {
                    it.copy(
                        role = MfNetRole.CLIENT,
                        localScreen = MfLocalScreen.IN_GAME,
                        connecting = false,
                        reconnecting = false,
                        connectError = null,
                        lostConnection = false,
                    )
                }
                onDone(null)
            }
        }
    }

    private fun handleServerMessage(msg: MfMessage) {
        when (msg) {
            is MfMessage.State -> setSnapshot(msg.snapshot)
            else -> Unit
        }
    }

    // ================= فرمان‌های داخل بازی =================

    /** «نقشم رو دیدم» */
    fun markSeen() {
        val st = _uiState.value
        if (st.snapshot.phase != MfPhase.ROLE_REVEAL || st.iHaveSeen) return
        if (st.isHost) hostMarkSeen(st.myName) else client?.send(MfMessage.Seen)
    }

    /** اقدام شبانه‌ی من — معنایش را نقش من تعیین می‌کند */
    fun nightAction(target: String) {
        val st = _uiState.value
        if (st.snapshot.phase != MfPhase.NIGHT || !st.iAmAlive) return
        if (st.isHost) hostNightAction(st.myName, target) else client?.send(MfMessage.NightAction(target))
    }

    /** میزبان: بحث روز تمام، بریم رای‌گیری */
    fun startDayVote() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.DAY_ANNOUNCE) return@hostOnly
        mutateSnapshot { it.copy(phase = MfPhase.DAY_VOTE, dayVotes = emptyMap()) }
    }

    /** رای روز — تا بسته‌شدن رای‌گیری قابل عوض‌کردن است */
    fun dayVote(target: String) {
        val st = _uiState.value
        if (st.snapshot.phase != MfPhase.DAY_VOTE || !st.iAmAlive || sameName(target, st.myName)) return
        if (st.isHost) hostApplyDayVote(st.myName, target) else client?.send(MfMessage.DayVote(target))
    }

    /** میزبان: از نتیجه‌ی روز به شب بعد (یا پایان اگر برنده مشخص شد) */
    fun proceedFromDayResult() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.DAY_RESULT) return@hostOnly
        val winner = computeWinner(s)
        if (winner != MfWinner.NONE) {
            mutateSnapshot { it.copy(phase = MfPhase.GAME_OVER, winner = winner) }
        } else {
            mutateSnapshot {
                it.copy(
                    phase = MfPhase.NIGHT,
                    nightIndex = it.nightIndex + 1,
                    mafiaVotes = emptyMap(),
                    doctorSave = "",
                    detectiveCheck = "",
                    lastKilled = "",
                    lastSaved = false,
                    dayVotes = emptyMap(),
                    lastLynched = "",
                )
            }
        }
    }

    /** میزبان: بازی دوباره با همان جمع */
    fun playAgain() = hostOnly {
        Analytics.gameReplay()
        mutateSnapshot { s ->
            s.copy(
                phase = MfPhase.LOBBY,
                players = s.players.map { it.copy(alive = true) },
                roles = emptyMap(),
                nightIndex = 0,
                seen = emptyList(),
                mafiaVotes = emptyMap(),
                doctorSave = "",
                detectiveCheck = "",
                lastKilled = "",
                lastSaved = false,
                dayVotes = emptyMap(),
                lastLynched = "",
                winner = MfWinner.NONE,
            )
        }
    }

    // ================= منطق میزبان =================

    private fun handleCommand(playerName: String, msg: MfMessage) {
        when (msg) {
            is MfMessage.Seen -> hostMarkSeen(playerName)
            is MfMessage.NightAction -> hostNightAction(playerName, msg.target)
            is MfMessage.DayVote -> hostApplyDayVote(playerName, msg.target)
            else -> Unit
        }
    }

    private fun hostMarkSeen(playerName: String) {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.ROLE_REVEAL) return
        mutateSnapshot { snap ->
            snap.copy(seen = snap.seen.filterNot { sameName(it, playerName) } + playerName)
        }
        maybeFinishReveal()
    }

    private fun maybeFinishReveal() {
        if (_uiState.value.role != MfNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.ROLE_REVEAL) return
        val waiting = s.players.filter { it.alive && it.connected && !s.hasSeen(it.name) }
        if (waiting.isEmpty()) {
            mutateSnapshot { it.copy(phase = MfPhase.NIGHT) }
        }
    }

    private fun hostNightAction(playerName: String, target: String) {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.NIGHT) return
        val actor = s.player(playerName) ?: return
        val victim = s.player(target) ?: return
        if (!actor.alive || !victim.alive) return
        when (s.roleOf(playerName)) {
            MfRole.MAFIA -> {
                if (sameName(target, playerName)) return
                mutateSnapshot { snap ->
                    snap.copy(mafiaVotes = snap.mafiaVotes.filterKeys { !sameName(it, playerName) } + (playerName to target))
                }
            }

            MfRole.DOCTOR -> mutateSnapshot { it.copy(doctorSave = target) }

            MfRole.DETECTIVE -> {
                if (sameName(target, playerName)) return
                mutateSnapshot { it.copy(detectiveCheck = target) }
            }

            else -> return
        }
        maybeResolveNight()
    }

    /** وقتی همه‌ی نقش‌های فعالِ زنده اقدام کردند، شب جمع‌بندی می‌شود */
    private fun maybeResolveNight() {
        if (_uiState.value.role != MfNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.NIGHT) return

        fun aliveConnectedWith(role: MfRole) =
            s.players.filter { it.alive && it.connected && s.roleOf(it.name) == role }

        val mafiaPending = aliveConnectedWith(MfRole.MAFIA).any { s.mafiaVoteOf(it.name) == null }
        val doctorPending = aliveConnectedWith(MfRole.DOCTOR).isNotEmpty() && s.doctorSave.isBlank()
        val detectivePending = aliveConnectedWith(MfRole.DETECTIVE).isNotEmpty() && s.detectiveCheck.isBlank()
        if (mafiaPending || doctorPending || detectivePending) return
        if (s.mafiaVotes.isEmpty()) return

        // قربانی: بیشترین رای مافیا؛ تساوی → اولین انتخاب بین پررای‌ترین‌ها
        val tally = s.mafiaVotes.values.groupingBy { nameKey(it) }.eachCount()
        val maxVotes = tally.values.max()
        val topKeys = tally.filterValues { it == maxVotes }.keys
        val victimKey = s.mafiaVotes.values.map { nameKey(it) }.first { it in topKeys }
        val victim = s.players.firstOrNull { nameKey(it.name) == victimKey }?.name ?: return
        val saved = s.doctorSave.isNotBlank() && sameName(s.doctorSave, victim)

        mutateSnapshot { snap ->
            snap.copy(
                phase = MfPhase.DAY_ANNOUNCE,
                lastKilled = if (saved) "" else victim,
                lastSaved = saved,
                players = if (saved) snap.players
                else snap.players.map { if (sameName(it.name, victim)) it.copy(alive = false) else it },
            )
        }

        // شاید مافیا با همین کشتن برنده شده باشد
        val after = _uiState.value.snapshot
        val winner = computeWinner(after)
        if (winner != MfWinner.NONE) {
            mutateSnapshot { it.copy(phase = MfPhase.GAME_OVER, winner = winner) }
        }
    }

    private fun hostApplyDayVote(voter: String, target: String) {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.DAY_VOTE || sameName(voter, target)) return
        val v = s.player(voter) ?: return
        val t = s.player(target) ?: return
        if (!v.alive || !t.alive) return
        mutateSnapshot { snap ->
            snap.copy(dayVotes = snap.dayVotes.filterKeys { !sameName(it, voter) } + (voter to target))
        }
        maybeFinishDayVote()
    }

    private fun maybeFinishDayVote() {
        if (_uiState.value.role != MfNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.DAY_VOTE) return
        val waiting = s.players.filter { it.alive && it.connected && s.dayVoteOf(it.name) == null }
        if (waiting.isNotEmpty()) return

        // اکثریت نسبی؛ تساوی یعنی امروز کسی اعدام نمی‌شود
        val tally = s.dayVotes.values.groupingBy { nameKey(it) }.eachCount()
        val top = tally.maxByOrNull { it.value }
        val lynchedKey = top?.takeIf { t -> tally.count { it.value == t.value } == 1 }?.key ?: ""
        val lynched = s.players.firstOrNull { nameKey(it.name) == lynchedKey }?.name ?: ""

        mutateSnapshot { snap ->
            snap.copy(
                phase = MfPhase.DAY_RESULT,
                lastLynched = lynched,
                players = if (lynched.isBlank()) snap.players
                else snap.players.map { if (sameName(it.name, lynched)) it.copy(alive = false) else it },
            )
        }
    }

    private fun computeWinner(s: MfSnapshot): MfWinner {
        val aliveMafia = s.players.count { it.alive && s.roleOf(it.name) == MfRole.MAFIA }
        val aliveOthers = s.players.count { it.alive && s.roleOf(it.name) != MfRole.MAFIA }
        return when {
            aliveMafia == 0 -> MfWinner.CITIZENS
            aliveMafia >= aliveOthers -> MfWinner.MAFIA
            else -> MfWinner.NONE
        }
    }

    // ================= هسته‌ی همگام‌سازی =================

    /** تغییر وضعیت توسط میزبان + پخش برای همه */
    private fun mutateSnapshot(transform: (MfSnapshot) -> MfSnapshot) {
        val next = transform(_uiState.value.snapshot)
        setSnapshot(next)
        val msg = MfMessage.State(next)
        server?.broadcast(msg)
        // میزبان اینترنتی: هر وضعیت تازه روی دیسک هم می‌ماند تا بازی با بسته‌شدن اپ نپرد
        if (server is OnlineHost<*>) OnlineSessionStore.saveHostState(ctx, GAME_ID, msg.encode())
    }

    private fun setSnapshot(next: MfSnapshot) {
        _uiState.update { it.copy(snapshot = next) }
    }

    private inline fun hostOnly(block: () -> Unit) {
        if (_uiState.value.role == MfNetRole.HOST) block()
    }

    // ================= خروج و پاک‌سازی =================

    /** خروجِ خواسته‌ی بازیکن: شبکه بسته و اتاق اینترنتیِ ذخیره‌شده هم پاک می‌شود */
    fun leaveGame() {
        shutdownNetworking()
        OnlineSessionStore.clear(ctx)
        val name = _uiState.value.myName
        _uiState.value = MafiaUiState(myName = name)
    }

    /** فقط بستن شبکه — اتاق ذخیره‌شده می‌ماند تا بعداً ادامه داده شود */
    private fun shutdownNetworking() {
        nsd.release()
        client?.close()
        client = null
        server?.stop()
        server = null
        keepAlive.release()
    }

    fun backToEntryFromJoin() {
        nsd.stopDiscovery()
        _uiState.update { it.copy(localScreen = MfLocalScreen.ENTRY, connectError = null, connecting = false) }
    }

    override fun onCleared() {
        super.onCleared()
        // بسته‌شدن صفحه/کشته‌شدن اپ خروجِ خواسته نیست: اتاق روی دیسک می‌ماند
        shutdownNetworking()
    }

    private companion object {
        const val GAME_ID = "mafia"
    }
}
