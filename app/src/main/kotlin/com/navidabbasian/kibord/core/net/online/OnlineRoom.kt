package com.navidabbasian.kibord.core.net.online

import android.util.Log
import com.navidabbasian.kibord.core.cloud.Cloud
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.net.ClientLink
import com.navidabbasian.kibord.core.net.HostLink
import io.github.jan.supabase.realtime.PresenceAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * اتاق بازی اینترنتی روی کانال بلادرنگ Supabase.
 *
 * همان معماری ستاره‌ایِ بازی‌های محلی: میزبان مرجع حقیقت است و مهمان‌ها
 * فقط فرمان می‌فرستند و وضعیت می‌گیرند — این‌جا به‌جای سوکت، پیام‌ها از
 * کانال «room_<کد>» رد می‌شوند. هیچ چیزی در پایگاه‌داده ذخیره نمی‌شود.
 *
 * رویدادهای کانال:
 *  - join  : مهمان → میزبان   {name}            (اولین ورود یا بازگشت)
 *  - jack  : میزبان → مهمان   {to, ok, error}   (پاسخ به join)
 *  - cmd   : مهمان → میزبان   {from, body}
 *  - st    : میزبان → مهمان‌ها {to, body}        (to خالی = همه)
 *
 * پایداری — بازی نباید با قفل گوشی، پریدن اینترنت یا بستن موقت اپ بپرد:
 *  - اگر سوکت افتاد، کتابخانه دوباره وصل می‌شود و کانال را می‌گیرد؛ ما هم
 *    حضورمان را دوباره اعلام می‌کنیم و (میزبان) آخرین وضعیت را می‌فرستیم
 *    یا (مهمان) دوباره join می‌زنیم تا میزبان ما را «برگشته» ثبت کند.
 *  - هویت مهمان همان یوزرنیم یکتاست، پس join با اسمی که قبلاً پذیرفته شده
 *    یعنی بازگشتِ همان آدم — بی‌چون‌وچرا پذیرفته می‌شود.
 *  - اگر حضور میزبان رفت، مهمان تا [OnlineClient.HOST_GRACE_MS] صبر می‌کند
 *    (میزبان شاید فقط صفحه‌اش قفل شده یا دارد اپ را دوباره باز می‌کند) و
 *    فقط بعد از آن «ارتباط قطع شد» اعلام می‌کند.
 *  - میزبان می‌تواند با همان کد قبلی دوباره اتاق بسازد ([OnlineHost] با
 *    roomCode داده‌شده) تا مهمان‌های منتظر خودکار برگردند.
 */
object OnlineRooms {

    /** بدون حروف گیج‌کننده مثل O/0 و I/1 تا گفتنِ شفاهیِ کد راحت باشد */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 6

    /**
     * آیا همین الان اتاق اینترنتیِ زنده‌ای داریم؟ صفحه‌ی برنده از روی همین
     * می‌فهمد نتیجه‌ی بازی باید در آمار آنلاین بازیکن ثبت شود یا نه.
     */
    @Volatile
    var active: Boolean = false
        internal set

    fun newCode(): String =
        (1..CODE_LENGTH).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")

    /** ورودی کاربر: فاصله و حروف کوچک و نویسه‌های نامعتبر پاک می‌شوند */
    fun normalizeCode(raw: String): String =
        raw.trim().uppercase().filter { it in ALPHABET }

    internal fun channelName(code: String) = "room_$code"

    /** کلید حضورِ میزبان — مهمان‌ها با رفتن این کلید می‌فهمند میزبان غایب شد */
    internal const val HOST_KEY = "\$host"

    /** هر چند وقت یک بار سلامت کانال چک شود */
    internal const val WATCHDOG_MS = 5_000L

    internal const val TAG = "OnlineRoom"
}

/** میزبان اینترنتی: هم‌امضای سرور سوکتی تا وی‌مدل‌ها بی‌درد جابه‌جا شوند */
class OnlineHost<T>(
    private val scope: CoroutineScope,
    private val encode: (T) -> String,
    /** بررسی ورود: پیام خطای فارسی یعنی رد، تهی یعنی خوش آمدی */
    private val onClientJoin: (name: String) -> String?,
    private val onCommand: (playerName: String, msg: T) -> Unit,
    private val onClientDisconnected: (playerName: String) -> Unit,
    private val latestState: () -> T?,
    private val decode: (String) -> T?,
    /** کد اتاق: برای «ادامه‌ی بازی قبلی» همان کد قبلی داده می‌شود */
    val roomCode: String = OnlineRooms.newCode(),
    /** بازگشتِ مهمانی که قطع شده بود (بعد از آنکه دوباره پذیرفته شد) */
    private val onClientRejoined: (playerName: String) -> Unit = {},
) : HostLink<T> {

    private var channel: RealtimeChannel? = null
    private val jobs = mutableListOf<Job>()
    /** مهمان‌هایی که join دادند و پذیرفته شدند */
    private val joined = mutableSetOf<String>()
    /** پذیرفته‌شده‌هایی که حضورشان رفته و هنوز برنگشته‌اند */
    private val away = mutableSetOf<String>()
    @Volatile private var stopped = false

    /** برپایی اتاق؛ نتیجه روی نخ اصلی برمی‌گردد */
    fun start(onReady: (ok: Boolean) -> Unit) {
        val client = Cloud.client ?: return onReady(false)
        stopped = false
        scope.launch {
            try {
                val ch = client.channel(OnlineRooms.channelName(roomCode)) {
                    presence { key = OnlineRooms.HOST_KEY }
                }
                channel = ch

                jobs += ch.broadcastFlow<JsonObject>(event = "join").onEach { p ->
                    val name = p["name"]?.jsonPrimitive?.content ?: return@onEach
                    handleJoin(ch, name)
                }.launchIn(scope)

                jobs += ch.broadcastFlow<JsonObject>(event = "cmd").onEach { p ->
                    val from = p["from"]?.jsonPrimitive?.content ?: return@onEach
                    val body = p["body"]?.jsonPrimitive?.content ?: return@onEach
                    decode(body)?.let { onCommand(from, it) }
                }.launchIn(scope)

                jobs += ch.presenceChangeFlow().onEach { action: PresenceAction ->
                    Log.d(OnlineRooms.TAG, "host presence joins=${action.joins.keys} leaves=${action.leaves.keys}")
                    action.leaves.keys.forEach { key ->
                        if (key != OnlineRooms.HOST_KEY && key in joined && away.add(key)) {
                            onClientDisconnected(key)
                        }
                    }
                }.launchIn(scope)

                ch.subscribe(blockUntilSubscribed = true)
                ch.track(hostPresence())
                OnlineRooms.active = true
                Analytics.onlineEntered("host")
                watchLink(ch)
                onReady(true)
            } catch (_: Exception) {
                stop()
                onReady(false)
            }
        }
    }

    private fun hostPresence() = buildJsonObject { put("role", "host") }

    private suspend fun handleJoin(ch: RealtimeChannel, name: String) {
        val returning = name in joined
        Log.d(OnlineRooms.TAG, "join from $name returning=$returning away=${name in away}")
        // بازگشتِ همان آدم: اگر قطع‌شده بود، وی‌مدل باید دوباره وصلش کند؛
        // اگر فقط سوکتش لرزیده و ما متوجه نشده بودیم، چیزی برای تصمیم نیست
        val error = if (!returning || name in away) onClientJoin(name) else null
        ch.broadcast(
            "jack",
            buildJsonObject {
                put("to", name)
                put("ok", error == null)
                put("error", error ?: "")
            },
        )
        if (error != null) return
        joined += name
        val wasAway = away.remove(name)
        latestState()?.let { state ->
            ch.broadcast("st", buildJsonObject { put("to", name); put("body", encode(state)) })
        }
        if (returning && wasAway) onClientRejoined(name)
    }

    /**
     * نگهبان اتصال: اگر کانال افتاد و دوباره بالا آمد، حضور و وضعیت را
     * دوباره می‌فرستد؛ اگر کتابخانه خودش بلند نشد، هلش می‌دهد.
     */
    private fun watchLink(ch: RealtimeChannel) {
        var wasDown = false
        jobs += ch.status.onEach { st ->
            Log.d(OnlineRooms.TAG, "host channel status=$st")
            if (stopped) return@onEach
            if (st == RealtimeChannel.Status.SUBSCRIBED && wasDown) {
                wasDown = false
                runCatching { ch.track(hostPresence()) }
                latestState()?.let { broadcast(it) }
            } else if (st == RealtimeChannel.Status.UNSUBSCRIBED) {
                wasDown = true
            }
        }.launchIn(scope)
        jobs += scope.launch {
            while (isActive && !stopped) {
                delay(OnlineRooms.WATCHDOG_MS)
                nudge(ch)
            }
        }
    }

    override fun broadcast(msg: T) {
        val ch = channel ?: return
        val body = encode(msg)
        scope.launch {
            try {
                ch.broadcast("st", buildJsonObject { put("to", ""); put("body", body) })
            } catch (_: Exception) {
            }
        }
    }

    override fun stop() {
        stopped = true
        OnlineRooms.active = false
        Analytics.onlineLeft()
        jobs.forEach { it.cancel() }
        jobs.clear()
        joined.clear()
        away.clear()
        val ch = channel ?: return
        channel = null
        scope.launch {
            try {
                Cloud.client?.realtime?.removeChannel(ch)
            } catch (_: Exception) {
            }
        }
    }
}

/** مهمان اینترنتی: هم‌امضای کلاینت سوکتی */
class OnlineClient<T>(
    private val scope: CoroutineScope,
    private val encode: (T) -> String,
    private val decode: (String) -> T?,
    private val onMessage: (T) -> Unit,
    private val onDisconnected: () -> Unit,
    /** میزبان لحظه‌ای غایب شد / برگشت — برای نشان دادن «منتظر میزبان…» */
    private val onHostAway: (away: Boolean) -> Unit = {},
) : ClientLink<T> {

    private var channel: RealtimeChannel? = null
    private var myName: String = ""
    private val jobs = mutableListOf<Job>()
    private var handshakeDone = false
    /** میزبان را حداقل یک بار در حضور دیده‌ایم؟ (تا leave واقعی را بفهمیم) */
    private var sawHost = false
    private var hostGraceJob: Job? = null
    @Volatile private var closed = false

    /** نتیجه: تهی یعنی وصل شد؛ وگرنه پیام خطای فارسی */
    fun connect(roomCode: String, name: String, onResult: (String?) -> Unit) {
        val client = Cloud.client
            ?: return onResult("بخش آنلاین روی این نسخه فعال نیست")
        val code = roomCode.trim().uppercase()
        if (code.length != OnlineRooms.CODE_LENGTH) {
            return onResult("کد اتاق ${OnlineRooms.CODE_LENGTH} حرفیه — دوباره چک کن")
        }
        myName = name
        closed = false
        scope.launch {
            try {
                val ch = client.channel(OnlineRooms.channelName(code)) {
                    presence { key = name }
                }
                channel = ch

                jobs += ch.broadcastFlow<JsonObject>(event = "jack").onEach { p ->
                    if (p["to"]?.jsonPrimitive?.content != name) return@onEach
                    if (handshakeDone) return@onEach
                    handshakeDone = true
                    val ok = p["ok"]?.jsonPrimitive?.content == "true"
                    if (ok) {
                        // پاسخِ میزبان یعنی قطعاً حاضر است — حتی اگر همگام‌سازیِ اولیه‌ی
                        // حضور، کلیدش را به ما نداده باشد
                        sawHost = true
                        OnlineRooms.active = true
                        Analytics.onlineEntered("guest")
                        onResult(null)
                    } else {
                        val err = p["error"]?.jsonPrimitive?.content.orEmpty()
                        Analytics.onlineJoinFailed(err.ifBlank { "rejected" })
                        close()
                        onResult(err.ifBlank { "اتصال برقرار نشد" })
                    }
                }.launchIn(scope)

                jobs += ch.broadcastFlow<JsonObject>(event = "st").onEach { p ->
                    val to = p["to"]?.jsonPrimitive?.content ?: ""
                    if (to.isNotEmpty() && to != name) return@onEach
                    val body = p["body"]?.jsonPrimitive?.content ?: return@onEach
                    decode(body)?.let { onMessage(it) }
                }.launchIn(scope)

                jobs += ch.presenceChangeFlow().onEach { action ->
                    Log.d(OnlineRooms.TAG, "guest presence joins=${action.joins.keys} leaves=${action.leaves.keys}")
                    if (action.joins.keys.contains(OnlineRooms.HOST_KEY)) {
                        val wasAway = hostGraceJob != null
                        hostGraceJob?.cancel()
                        hostGraceJob = null
                        if (sawHost && wasAway && handshakeDone) {
                            // میزبان برگشت: خودمان را دوباره معرفی می‌کنیم تا وضعیت تازه بدهد
                            runCatching { ch.broadcast("join", buildJsonObject { put("name", name) }) }
                            onHostAway(false)
                        }
                        sawHost = true
                    }
                    if (sawHost && action.leaves.keys.contains(OnlineRooms.HOST_KEY) && handshakeDone) {
                        startHostGrace()
                    }
                }.launchIn(scope)

                ch.subscribe(blockUntilSubscribed = true)
                ch.track(guestPresence())
                ch.broadcast("join", buildJsonObject { put("name", name) })
                watchLink(ch)

                // اگر میزبانی در کار نبود، بعد از مهلت کوتاه خطا بده
                delay(JOIN_TIMEOUT_MS)
                if (!handshakeDone) {
                    handshakeDone = true
                    Analytics.onlineJoinFailed("timeout")
                    close()
                    onResult("اتاقی با این کد پیدا نشد — کد و اینترنت رو چک کن")
                }
            } catch (_: Exception) {
                if (!handshakeDone) {
                    handshakeDone = true
                    close()
                    onResult("اتصال برقرار نشد — اینترنت رو چک کن")
                }
            }
        }
    }

    private fun guestPresence() = buildJsonObject { put("role", "guest") }

    /** میزبان غایب شد: مهلت می‌دهیم برگردد، بعد قطع اعلام می‌کنیم */
    private fun startHostGrace() {
        if (hostGraceJob != null) return
        Log.d(OnlineRooms.TAG, "host away — grace started")
        onHostAway(true)
        hostGraceJob = scope.launch {
            delay(HOST_GRACE_MS)
            hostGraceJob = null
            if (!closed) {
                close()
                onDisconnected()
            }
        }
    }

    /** بعد از هر بازگشتِ کانال: حضور و join دوباره، تا میزبان ما را برگشته ببیند */
    private fun watchLink(ch: RealtimeChannel) {
        var wasDown = false
        jobs += ch.status.onEach { st ->
            Log.d(OnlineRooms.TAG, "guest channel status=$st")
            if (closed) return@onEach
            if (st == RealtimeChannel.Status.SUBSCRIBED && wasDown) {
                wasDown = false
                runCatching { ch.track(guestPresence()) }
                runCatching { ch.broadcast("join", buildJsonObject { put("name", myName) }) }
            } else if (st == RealtimeChannel.Status.UNSUBSCRIBED) {
                wasDown = true
            }
        }.launchIn(scope)
        jobs += scope.launch {
            while (isActive && !closed) {
                delay(OnlineRooms.WATCHDOG_MS)
                nudge(ch)
            }
        }
    }

    override fun send(msg: T) {
        val ch = channel ?: return
        val body = encode(msg)
        scope.launch {
            try {
                ch.broadcast("cmd", buildJsonObject { put("from", myName); put("body", body) })
            } catch (_: Exception) {
            }
        }
    }

    override fun close() {
        closed = true
        OnlineRooms.active = false
        Analytics.onlineLeft()
        hostGraceJob?.cancel()
        hostGraceJob = null
        jobs.forEach { it.cancel() }
        jobs.clear()
        val ch = channel ?: return
        channel = null
        scope.launch {
            try {
                Cloud.client?.realtime?.removeChannel(ch)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val JOIN_TIMEOUT_MS = 8_000L

        /** چقدر منتظر برگشتِ میزبان بمانیم (قفل گوشی، پریدن نت، بازکردن دوباره‌ی اپ) */
        const val HOST_GRACE_MS = 120_000L
    }
}

/**
 * اگر سوکت یا کانال خوابیده ماند، بیدارش می‌کند. کتابخانه خودش تلاش
 * می‌کند؛ این فقط پشتیبان است تا هیچ‌وقت بی‌صدا در حالت قطع نمانیم.
 */
private suspend fun nudge(ch: RealtimeChannel) {
    val rt = Cloud.client?.realtime ?: return
    try {
        if (rt.status.value == Realtime.Status.DISCONNECTED) rt.connect()
        if (ch.status.value == RealtimeChannel.Status.UNSUBSCRIBED) ch.subscribe()
    } catch (_: Exception) {
    }
}
