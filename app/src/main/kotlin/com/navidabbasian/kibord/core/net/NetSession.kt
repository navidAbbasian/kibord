package com.navidabbasian.kibord.core.net

import android.app.Application
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.cloud.AccountRepository
import com.navidabbasian.kibord.core.cloud.Cloud
import com.navidabbasian.kibord.core.net.lan.LanClient
import com.navidabbasian.kibord.core.net.lan.LanDiscoveredGame
import com.navidabbasian.kibord.core.net.lan.LanNsd
import com.navidabbasian.kibord.core.net.lan.LanServer
import com.navidabbasian.kibord.core.net.online.OnlineClient
import com.navidabbasian.kibord.core.net.online.OnlineHost
import com.navidabbasian.kibord.core.net.online.OnlineRooms
import com.navidabbasian.kibord.core.net.online.OnlineSessionStore
import com.navidabbasian.kibord.core.net.online.StoredOnlineRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** نقش این گوشی در بازی چندگوشی */
enum class NetRole { NONE, HOST, CLIENT }

/**
 * وضعیتِ اتصالِ یک بازی چندگوشی — مستقل از خودِ بازی. وی‌مدلِ هر بازی این را
 * کنار وضعیت خودش نگه می‌دارد و صفحه‌های مشترکِ ورود/پیوستن/لابی همین را
 * نقاشی می‌کنند.
 */
data class NetUiState(
    val role: NetRole = NetRole.NONE,
    /** راه اینترنتی (کد اتاق) به‌جای وای‌فای محلی */
    val online: Boolean = false,
    val myName: String = "",
    /** کد شش‌حرفی اتاق اینترنتی (خالی در حالت محلی) */
    val roomCode: String = "",
    /** آدرس محلی میزبان برای اتصال دستی (خالی در حالت اینترنتی) */
    val hostAddress: String = "",
    val discovered: List<LanDiscoveredGame> = emptyList(),
    val connecting: Boolean = false,
    val connectError: String? = null,
    /** مهمان: ارتباط با میزبان قطع شد */
    val lostConnection: Boolean = false,
    /** مهمان اینترنتی: میزبان لحظه‌ای غایب است */
    val hostAway: Boolean = false,
    val reconnecting: Boolean = false,
    /** اتاق اینترنتیِ نیمه‌کاره‌ای که می‌شود ادامه‌اش داد */
    val resumable: StoredOnlineRoom? = null,
) {
    val isNetPlay: Boolean get() = role != NetRole.NONE
    val isHost: Boolean get() = role == NetRole.HOST
    val isClient: Boolean get() = role == NetRole.CLIENT
}

/**
 * جلسه‌ی چندگوشیِ یک بازی: میزبانی یا پیوستن، روی شبکه‌ی محلی یا اینترنت،
 * با کشف خودکار، اتاق پایدار و وصل‌شدنِ دوباره — همه‌ی چیزهایی که بین
 * بازی‌ها مشترک است. وی‌مدلِ بازی فقط منطقِ خودِ بازی را می‌نویسد.
 *
 * قرارداد: میزبان مرجع حقیقت است؛ مهمان فرمان می‌فرستد و وضعیت می‌گیرد.
 * [T] نوع پیام‌های بازی است (یک sealed class @Serializable).
 */
class NetSession<T>(
    private val app: Application,
    private val scope: CoroutineScope,
    /** شناسه‌ی بازی در حافظه‌ی اتاق‌های اینترنتی و پیشوند سرویس محلی */
    private val gameId: String,
    private val encode: (T) -> String,
    private val decode: (String) -> T?,
    private val host: HostCallbacks<T>,
    private val guest: GuestCallbacks<T>,
) {

    /** آنچه میزبان باید جواب بدهد */
    interface HostCallbacks<T> {
        /** بررسی ورود: پیام خطای فارسی یعنی رد، تهی یعنی خوش آمدی */
        fun acceptJoin(name: String): String?
        fun onCommand(name: String, msg: T)
        fun onDisconnected(name: String)
        fun onRejoined(name: String) {}

        /** عکس وضعیت از دیدِ همین مهمان (برای پنهان ماندنِ دست بقیه) */
        fun stateFor(name: String): T?

        /** اتاق بالا آمد (محلی یا اینترنتی) — وقتِ رفتن به لابی */
        fun onRoomReady()

        /**
         * ادامه‌ی بازی اینترنتی: وضعیت ذخیره‌شده‌ی میزبان (همان رشته‌ی
         * [persistHostState]) یا تهی اگر خوانا نبود. بعدش اتاق با همان کد
         * دوباره ساخته می‌شود.
         */
        fun onResumeHost(stored: StoredOnlineRoom, decoded: T?)

        /** اتاق ساخته نشد یا دوباره ساخته نشد — وی‌مدل برگردد به ورود */
        fun onRoomFailed()
    }

    interface GuestCallbacks<T> {
        /** هر چه میزبان فرستاد، همان حقیقت است */
        fun onMessage(msg: T)
    }

    private val _state = MutableStateFlow(
        NetUiState(resumable = OnlineSessionStore.load(app, gameId)),
    )
    val state: StateFlow<NetUiState> = _state.asStateFlow()

    private val nsd = LanNsd(app, namePrefix = "$gameId-")
    private val keepAlive = HostKeepAlive(app)
    private var server: TargetedHostLink<T>? = null
    private var client: ClientLink<T>? = null

    val current: NetUiState get() = _state.value

    private fun update(block: (NetUiState) -> NetUiState) {
        _state.value = block(_state.value)
    }

    // ================= هویت و راه =================

    fun setMyName(name: String) {
        // در حالت اینترنتی اسم قفل است: همان یوزرنیم حساب
        if (current.online) return
        update { it.copy(myName = name.take(16)) }
    }

    /** نتیجه: تهی یعنی انجام شد؛ وگرنه پیام خطا (بدون حساب) */
    fun setOnline(on: Boolean): String? {
        if (!on) {
            update { it.copy(online = false, connectError = null) }
            return null
        }
        val me = AccountRepository.onlineIdentity()
        if (me == null) {
            update { it.copy(online = false, connectError = AccountRepository.NEED_ACCOUNT_MESSAGE) }
            return AccountRepository.NEED_ACCOUNT_MESSAGE
        }
        update { it.copy(online = true, myName = me, connectError = null) }
        return null
    }

    fun clearError() = update { it.copy(connectError = null, connecting = false) }

    // ================= میزبانی =================

    /** میزبانی روی شبکه‌ی محلی: سرور سوکتی + اعلام سرویس برای کشف خودکار */
    fun hostLan(): Boolean {
        val name = current.myName.trim()
        if (name.isBlank()) return false
        val srv = LanServer(
            scope = scope,
            encode = encode,
            decode = decode,
            onClientJoin = host::acceptJoin,
            onCommand = host::onCommand,
            onClientDisconnected = host::onDisconnected,
            latestStateFor = host::stateFor,
        )
        if (!srv.start()) {
            update { it.copy(connectError = "سرور روی این گوشی راه نیفتاد") }
            return false
        }
        server = srv
        keepAlive.acquire()
        nsd.register(name, srv.port)
        update {
            it.copy(
                role = NetRole.HOST,
                myName = name,
                roomCode = "",
                hostAddress = LanNsd.localIpAddress() ?: "",
                connecting = false,
                connectError = null,
                lostConnection = false,
            )
        }
        host.onRoomReady()
        return true
    }

    /** میزبانی اینترنتی: به‌جای سوکت محلی، اتاقی با کد شش‌حرفی ساخته می‌شود */
    fun hostOnline() {
        if (!Cloud.isConfigured) {
            update { it.copy(connectError = "بخش آنلاین روی این نسخه فعال نیست") }
            return
        }
        val name = AccountRepository.onlineIdentity() ?: run {
            update { it.copy(connectError = AccountRepository.NEED_ACCOUNT_MESSAGE) }
            return
        }
        update { it.copy(myName = name, online = true, connecting = true, connectError = null) }
        startOnlineHost(name = name, roomCode = null, resumed = false)
    }

    private fun startOnlineHost(name: String, roomCode: String?, resumed: Boolean) {
        val onlineHost = OnlineHost(
            scope = scope,
            encode = encode,
            onClientJoin = host::acceptJoin,
            onCommand = host::onCommand,
            onClientDisconnected = host::onDisconnected,
            latestState = { host.stateFor("") },
            decode = decode,
            roomCode = roomCode ?: OnlineRooms.newCode(),
            onClientRejoined = host::onRejoined,
            latestStateFor = host::stateFor,
        )
        onlineHost.start { ok ->
            if (!ok) {
                update {
                    it.copy(
                        role = NetRole.NONE,
                        roomCode = "",
                        connecting = false,
                        connectError = if (resumed) "اتاق دوباره ساخته نشد — اینترنت رو چک کن" else "اتاق ساخته نشد — اینترنت رو چک کن",
                    )
                }
                host.onRoomFailed()
                return@start
            }
            server = onlineHost
            keepAlive.acquire(lan = false)
            update {
                it.copy(
                    role = NetRole.HOST,
                    myName = name,
                    roomCode = onlineHost.roomCode,
                    hostAddress = "",
                    connecting = false,
                    connectError = null,
                    resumable = null,
                    lostConnection = false,
                )
            }
            if (!resumed) host.onRoomReady()
            // اتاق روی دیسک می‌ماند تا با بسته شدن اپ یا قفل گوشی از دست نرود
            OnlineSessionStore.save(
                app,
                StoredOnlineRoom(
                    gameId = gameId,
                    role = "host",
                    code = onlineHost.roomCode,
                    name = name,
                    savedAt = System.currentTimeMillis(),
                    hostState = host.stateFor("")?.let(encode),
                ),
            )
        }
    }

    /** میزبان اینترنتی بعد از هر تغییر وضعیت — ارزان است */
    fun persistHostState() {
        if (!current.isHost || server !is OnlineHost<*>) return
        val encoded = host.stateFor("")?.let(encode) ?: return
        OnlineSessionStore.saveHostState(app, gameId, encoded)
    }

    /** میزبان: وضعیت تازه برای همه (هر مهمان عکسِ خودش را می‌گیرد) */
    fun pushToAll(names: Collection<String>) {
        val srv = server ?: return
        names.forEach { n -> host.stateFor(n)?.let { srv.sendTo(n, it) } }
        persistHostState()
    }

    /** میزبان: یک پیامِ یکسان برای همه (مثلاً چت) */
    fun broadcast(msg: T) {
        server?.broadcast(msg)
    }

    fun sendTo(name: String, msg: T) {
        server?.sendTo(name, msg)
    }

    /** مهمان: فرمان به میزبان */
    fun send(msg: T) {
        client?.send(msg)
    }

    /** میزبان از لابی منصرف شد — سرور جمع می‌شود و اتاقِ ذخیره‌شده هم می‌رود */
    fun cancelHosting() {
        stopNetworking()
        clearStoredRoom()
        update { it.copy(role = NetRole.NONE, roomCode = "", hostAddress = "", connecting = false) }
    }

    // ================= پیوستن =================

    fun startDiscovery() {
        update { it.copy(discovered = emptyList(), connectError = null) }
        if (current.online) return // اینترنتی: با کد می‌آیند
        nsd.discover(
            onFound = { game ->
                update { st -> st.copy(discovered = st.discovered.filter { it.hostName != game.hostName } + game) }
            },
            onLost = { name ->
                update { st -> st.copy(discovered = st.discovered.filter { it.hostName != name }) }
            },
        )
    }

    fun stopDiscovery() = nsd.stopDiscovery()

    /** مهمان از صفحه‌ی پیوستن برگشت — اگر وصل شده بود، این خروجِ خواسته است */
    fun backFromJoin() {
        nsd.stopDiscovery()
        if (current.isClient) {
            stopNetworking()
            clearStoredRoom()
        }
        update {
            it.copy(
                role = NetRole.NONE,
                connectError = null,
                connecting = false,
                reconnecting = false,
                hostAway = false,
                lostConnection = false,
            )
        }
    }

    /** پیوستن محلی با آدرس (از کشف خودکار یا دستی) */
    fun joinLan(address: String, port: Int = LanServer.BASE_PORT) {
        val name = current.myName.trim()
        if (name.isBlank() || address.isBlank()) return
        update { it.copy(myName = name, connecting = true, connectError = null) }
        val c = LanClient(
            scope = scope,
            encode = encode,
            decode = decode,
            onMessage = guest::onMessage,
            onDisconnected = { if (current.isClient) update { it.copy(lostConnection = true) } },
        )
        client = c
        c.connect(address, port, name) { error ->
            if (error != null) {
                client = null
                update { it.copy(connecting = false, connectError = error) }
            } else {
                nsd.stopDiscovery()
                update { it.copy(role = NetRole.CLIENT, connecting = false, connectError = null) }
            }
        }
    }

    /** پیوستن اینترنتی با کد اتاق */
    fun joinOnline(code: String) {
        val name = AccountRepository.onlineIdentity() ?: run {
            update { it.copy(connectError = AccountRepository.NEED_ACCOUNT_MESSAGE) }
            return
        }
        connectOnline(code = OnlineRooms.normalizeCode(code), name = name)
    }

    private fun connectOnline(code: String, name: String, onDone: (error: String?) -> Unit = {}) {
        update { it.copy(myName = name, online = true, connecting = true, connectError = null) }
        val c = OnlineClient(
            scope = scope,
            encode = encode,
            decode = decode,
            onMessage = guest::onMessage,
            onDisconnected = { if (current.isClient) update { it.copy(lostConnection = true, hostAway = false) } },
            onHostAway = { away -> update { it.copy(hostAway = away) } },
        )
        client = c
        c.connect(code, name) { error ->
            if (error != null) {
                client = null
                update { it.copy(connecting = false, connectError = error) }
            } else {
                update {
                    it.copy(
                        role = NetRole.CLIENT,
                        roomCode = code,
                        connecting = false,
                        connectError = null,
                        lostConnection = false,
                        hostAway = false,
                    )
                }
                OnlineSessionStore.save(
                    app,
                    StoredOnlineRoom(gameId = gameId, role = "guest", code = code, name = name, savedAt = System.currentTimeMillis()),
                )
            }
            onDone(error)
        }
    }

    /** مهمان روی صفحه‌ی «ارتباط قطع شد»: دوباره به همان اتاق با همان اسم وصل شو */
    fun reconnectOnline() {
        val st = current
        if (!st.online || st.roomCode.isBlank() || st.myName.isBlank() || st.reconnecting) return
        client?.close()
        client = null
        update { it.copy(lostConnection = false, reconnecting = true, connectError = null, hostAway = false) }
        connectOnline(code = st.roomCode, name = st.myName) { error ->
            update {
                if (error != null) it.copy(reconnecting = false, connecting = false, lostConnection = true, connectError = error)
                else it.copy(reconnecting = false)
            }
        }
    }

    // ================= اتاق پایدار =================

    fun discardResume() = clearStoredRoom()

    /** «ادامه بده» — میزبان با همان کد اتاق را دوباره می‌سازد، مهمان دوباره می‌پیوندد */
    fun resumeOnline() {
        val stored = current.resumable ?: return
        if (current.connecting) return
        if (!Cloud.isConfigured) {
            update { it.copy(connectError = "بخش آنلاین روی این نسخه فعال نیست") }
            return
        }
        update { it.copy(online = true, myName = stored.name, connectError = null, connecting = true) }
        if (stored.isHost) {
            host.onResumeHost(stored, stored.hostState?.let(decode))
            startOnlineHost(name = stored.name, roomCode = stored.code, resumed = true)
        } else {
            connectOnline(code = stored.code, name = stored.name) { error ->
                if (error == null) update { it.copy(resumable = null) }
            }
        }
    }

    private fun clearStoredRoom() {
        OnlineSessionStore.clear(app)
        update { it.copy(resumable = null) }
    }

    // ================= خروج و پاک‌سازی =================

    /** خروجِ خواسته‌ی بازیکن: شبکه جمع می‌شود و اتاق ذخیره‌شده هم پاک */
    fun leave() {
        stopNetworking()
        clearStoredRoom()
        _state.value = NetUiState()
    }

    /** بسته شدن صفحه یا کشته شدن اپ: شبکه جمع می‌شود ولی اتاقِ ذخیره‌شده می‌ماند تا ادامه بدهد */
    fun release() = stopNetworking()

    private fun stopNetworking() {
        nsd.release()
        client?.close()
        client = null
        server?.stop()
        server = null
        keepAlive.release()
    }

    /** برای گزارش‌گیری: راهِ بازی */
    val analyticsNet: String get() = if (current.online) "online" else "lan"

    @Suppress("unused")
    private val analytics = Analytics
}
