package com.navidabbasian.kibord.core.net.online

import com.navidabbasian.kibord.core.cloud.Cloud
import com.navidabbasian.kibord.core.net.ClientLink
import com.navidabbasian.kibord.core.net.HostLink
import io.github.jan.supabase.realtime.PresenceAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
 * کانال «room_<کد>» رد می‌شوند. هیچ چیزی در پایگاه‌داده ذخیره نمی‌شود؛
 * اتاق با رفتن میزبان محو می‌شود.
 *
 * رویدادهای کانال:
 *  - join  : مهمان → میزبان   {name}
 *  - jack  : میزبان → مهمان   {to, ok, error}   (پاسخ به join)
 *  - cmd   : مهمان → میزبان   {from, body}
 *  - st    : میزبان → مهمان‌ها {to, body}        (to خالی = همه)
 *
 * پیام‌های Hello/Welcome پروتکل هر بازی این‌جا لازم نیستند؛ دست‌دادن را
 * خود این لایه انجام می‌دهد — دقیقاً همان‌طور که لایه‌ی سوکت انجام می‌داد.
 */
object OnlineRooms {

    /** بدون حروف گیج‌کننده مثل O/0 و I/1 تا گفتنِ شفاهیِ کد راحت باشد */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 6

    fun newCode(): String =
        (1..CODE_LENGTH).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")

    /** ورودی کاربر: فاصله و حروف کوچک و نویسه‌های نامعتبر پاک می‌شوند */
    fun normalizeCode(raw: String): String =
        raw.trim().uppercase().filter { it in ALPHABET }

    internal fun channelName(code: String) = "room_$code"

    /** کلید حضورِ میزبان — مهمان‌ها با رفتن این کلید می‌فهمند اتاق مُرد */
    internal const val HOST_KEY = "\$host"
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
) : HostLink<T> {

    val roomCode: String = OnlineRooms.newCode()

    private var channel: RealtimeChannel? = null
    private val jobs = mutableListOf<Job>()
    /** مهمان‌هایی که join دادند و پذیرفته شدند — برای تشخیص قطع شدن */
    private val joined = mutableSetOf<String>()

    /** برپایی اتاق؛ نتیجه روی نخ اصلی برمی‌گردد */
    fun start(onReady: (ok: Boolean) -> Unit) {
        val client = Cloud.client ?: return onReady(false)
        scope.launch {
            try {
                val ch = client.channel(OnlineRooms.channelName(roomCode)) {
                    presence { key = OnlineRooms.HOST_KEY }
                }
                channel = ch

                jobs += ch.broadcastFlow<JsonObject>(event = "join").onEach { p ->
                    val name = p["name"]?.jsonPrimitive?.content ?: return@onEach
                    val error = onClientJoin(name)
                    ch.broadcast(
                        "jack",
                        buildJsonObject {
                            put("to", name)
                            put("ok", error == null)
                            put("error", error ?: "")
                        },
                    )
                    if (error == null) {
                        joined += name
                        latestState()?.let { state ->
                            ch.broadcast(
                                "st",
                                buildJsonObject {
                                    put("to", name)
                                    put("body", encode(state))
                                },
                            )
                        }
                    }
                }.launchIn(scope)

                jobs += ch.broadcastFlow<JsonObject>(event = "cmd").onEach { p ->
                    val from = p["from"]?.jsonPrimitive?.content ?: return@onEach
                    val body = p["body"]?.jsonPrimitive?.content ?: return@onEach
                    decode(body)?.let { onCommand(from, it) }
                }.launchIn(scope)

                jobs += ch.presenceChangeFlow().onEach { action: PresenceAction ->
                    action.leaves.keys.forEach { key ->
                        if (key != OnlineRooms.HOST_KEY && joined.remove(key)) {
                            onClientDisconnected(key)
                        }
                    }
                }.launchIn(scope)

                ch.subscribe(blockUntilSubscribed = true)
                ch.track(buildJsonObject { put("role", "host") })
                onReady(true)
            } catch (_: Exception) {
                stop()
                onReady(false)
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
        jobs.forEach { it.cancel() }
        jobs.clear()
        joined.clear()
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
) : ClientLink<T> {

    private var channel: RealtimeChannel? = null
    private var myName: String = ""
    private val jobs = mutableListOf<Job>()
    private var handshakeDone = false
    /** میزبان را حداقل یک بار در حضور دیده‌ایم؟ (تا leave واقعی را بفهمیم) */
    private var sawHost = false

    /** نتیجه: تهی یعنی وصل شد؛ وگرنه پیام خطای فارسی */
    fun connect(roomCode: String, name: String, onResult: (String?) -> Unit) {
        val client = Cloud.client
            ?: return onResult("بخش آنلاین روی این نسخه فعال نیست")
        val code = roomCode.trim().uppercase()
        if (code.length != OnlineRooms.CODE_LENGTH) {
            return onResult("کد اتاق ${OnlineRooms.CODE_LENGTH} حرفیه — دوباره چک کن")
        }
        myName = name
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
                        onResult(null)
                    } else {
                        val err = p["error"]?.jsonPrimitive?.content.orEmpty()
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
                    if (action.joins.keys.contains(OnlineRooms.HOST_KEY)) sawHost = true
                    if (sawHost && action.leaves.keys.contains(OnlineRooms.HOST_KEY)) {
                        if (handshakeDone) {
                            close()
                            onDisconnected()
                        }
                    }
                }.launchIn(scope)

                ch.subscribe(blockUntilSubscribed = true)
                ch.track(buildJsonObject { put("role", "guest") })
                ch.broadcast("join", buildJsonObject { put("name", name) })

                // اگر میزبانی در کار نبود، بعد از مهلت کوتاه خطا بده
                kotlinx.coroutines.delay(JOIN_TIMEOUT_MS)
                if (!handshakeDone) {
                    handshakeDone = true
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
    }
}
