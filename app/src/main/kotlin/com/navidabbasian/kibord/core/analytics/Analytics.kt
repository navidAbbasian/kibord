package com.navidabbasian.kibord.core.analytics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.navidabbasian.kibord.BuildConfig
import com.navidabbasian.kibord.core.cloud.AccountRepository
import com.navidabbasian.kibord.core.cloud.Cloud
import com.navidabbasian.kibord.core.settings.GamePrefs
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * گزارش‌گیری ناشناسِ محصول: چه بازی‌هایی بازی می‌شوند، کجا رها می‌شوند،
 * چند نفر هم‌زمان آنلاین‌اند — برای تصمیم‌گیری درباره‌ی مسیر اپ.
 *
 * قواعد سفت و سخت:
 *  - هیچ داده‌ی شخصی: شناسه‌ی دستگاه یک UUID تصادفیِ ساخته‌شده در خود اپ است.
 *  - اول آفلاین: رویدادها در صف محلی می‌مانند و هر وقت نت بود دسته‌ای می‌روند.
 *  - هرگز سر راه بازی نیست: هر خطایی بی‌صدا لاگ می‌شود.
 *  - کاربر از تنظیمات می‌تواند کاملاً خاموشش کند.
 *  - اگر کلیدهای ابری نباشند، همه‌چیز بی‌اثر است.
 */
object Analytics {

    private const val TAG = "Analytics"
    private const val KEY_DEVICE_ID = "analytics_device_id"
    private const val KEY_OPT_OUT = "analytics_opt_out"
    private const val QUEUE_FILE = "analytics_queue.json"

    private const val FLUSH_EVERY_MS = 20_000L
    private const val FLUSH_AT_SIZE = 12
    private const val HEARTBEAT_EVERY_MS = 60_000L
    /** اگر بیشتر از این در پس‌زمینه بمانیم، برگشت = نشست تازه */
    private const val NEW_SESSION_AFTER_MS = 5 * 60_000L
    private const val MAX_QUEUE = 500

    @Serializable
    private data class EventRow(
        @SerialName("session_id") val sessionId: String?,
        @SerialName("device_id") val deviceId: String,
        @SerialName("user_id") val userId: String? = null,
        val name: String,
        val props: JsonObject,
        @SerialName("client_ts") val clientTs: String,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private var appContext: Context? = null
    private var deviceId: String = ""
    private var sessionId: String? = null
    private var sessionStartedAt = 0L
    private var backgroundedAt = 0L
    private var optOut = false

    private val queue = ArrayList<EventRow>()
    private var flushJob: Job? = null
    private var heartbeatJob: Job? = null

    /** بازیِ در حال انجام — برای تشخیص رها شدن */
    private var currentGame: String? = null
    private var currentGameMode: String = "local"
    private var currentGameStartedAt = 0L
    private var currentGameFinished = false
    private var inOnlineRoom = false

    // ================= چرخه‌ی حیات =================

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        optOut = GamePrefs.getBool(app, KEY_OPT_OUT, false)
        deviceId = GamePrefs.getString(app, KEY_DEVICE_ID) ?: UUID.randomUUID().toString().also {
            GamePrefs.setString(app, KEY_DEVICE_ID, it)
        }
        scope.launch { loadQueue() }
    }

    val isEnabled: Boolean get() = !optOut && Cloud.isConfigured

    fun isOptedOut(context: Context): Boolean = GamePrefs.getBool(context, KEY_OPT_OUT, false)

    /** کاربر از تنظیمات: ارسال آمار ناشناس روشن/خاموش */
    fun setOptOut(context: Context, out: Boolean) {
        optOut = out
        GamePrefs.setBool(context, KEY_OPT_OUT, out)
        if (out) {
            scope.launch { lock.withLock { queue.clear(); persistQueueLocked() } }
            stopHeartbeat()
        } else {
            // تازه روشن شده: نشست از همین لحظه
            if (sessionId == null) onForeground()
        }
    }

    /** اپ به جلو آمد (از MainActivity.onResume) */
    fun onForeground() {
        if (!isEnabled) return
        val now = SystemClock.elapsedRealtime()
        val stale = sessionId == null || (backgroundedAt > 0 && now - backgroundedAt > NEW_SESSION_AFTER_MS)
        if (stale) {
            sessionId = UUID.randomUUID().toString()
            sessionStartedAt = now
            track("app_open", buildJsonObject { put("first_session", GamePrefs.getBool(appContext!!, "analytics_seen", false).not()) })
            GamePrefs.setBool(appContext!!, "analytics_seen", true)
        }
        backgroundedAt = 0L
        startHeartbeat()
        startFlushLoop()
    }

    /** اپ به پس‌زمینه رفت (از MainActivity.onPause) */
    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
        stopHeartbeat()
        // آخرین شانس برای ارسال قبل از خواب
        scope.launch { flush() }
    }

    // ================= ثبت رویداد =================

    fun track(name: String, props: JsonObject = JsonObject(emptyMap())) {
        if (!isEnabled) return
        val row = EventRow(
            sessionId = sessionId,
            deviceId = deviceId,
            userId = runCatching { AccountRepository.currentUserId() }.getOrNull(),
            name = name,
            props = props,
            clientTs = Instant.now().toString(),
        )
        scope.launch {
            val big = lock.withLock {
                if (queue.size >= MAX_QUEUE) queue.removeAt(0)
                queue += row
                persistQueueLocked()
                queue.size >= FLUSH_AT_SIZE
            }
            if (big) flush()
        }
    }

    fun track(name: String, vararg props: Pair<String, Any?>) =
        track(name, buildJsonObject {
            props.forEach { (k, v) ->
                when (v) {
                    null -> Unit
                    is Boolean -> put(k, v)
                    is Int -> put(k, v)
                    is Long -> put(k, v)
                    is Double -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
        })

    // ================= بازی‌ها =================

    /** ورود به یک بازی (از ناوبری؛ شناسه = پسوند مسیر بعد از game/) */
    fun gameEntered(gameId: String) {
        // اگر بازی قبلی بدون پایان مانده بود، رهاشده حساب می‌شود
        currentGame?.let { if (!currentGameFinished) emitAbandon(it) }
        currentGame = gameId
        currentGameMode = "local"
        currentGameStartedAt = SystemClock.elapsedRealtime()
        currentGameFinished = false
        track("game_start", "game_id" to gameId, "mode" to currentGameMode)
    }

    /** بازی حالت چندگوشی گرفت: lan یا online — برای تفکیک در گزارش */
    fun gameModeChanged(mode: String) {
        if (currentGame == null) return
        currentGameMode = mode
        track("game_mode", "game_id" to currentGame, "mode" to mode)
    }

    /**
     * تنظیماتِ یک دست، درست لحظه‌ای که بازی واقعاً شروع می‌شود: چند نفر، چند تیم،
     * چند راند، تایمر، نوعِ بازی… هر بازی فقط آن‌چه دارد را می‌فرستد. شناسه‌ی
     * بازی و راهِ بازی خودکار اضافه می‌شود.
     */
    fun gameSetup(vararg props: Pair<String, Any?>) {
        val g = currentGame ?: return
        track("game_setup", "game_id" to g, "mode" to currentGameMode, *props)
    }

    /** «دوباره بازی» / دست بعدی بعد از صفحه‌ی برنده */
    fun gameReplay() {
        val g = currentGame ?: return
        // دستِ تازه شروع می‌شود: اگر دوباره رها شود، رهاشدنِ همان دست است
        currentGameFinished = false
        currentGameStartedAt = SystemClock.elapsedRealtime()
        track("game_replay", "game_id" to g, "mode" to currentGameMode)
    }

    /** از صفحه‌ی برنده: بازی واقعاً تمام شد */
    fun gameFinished(gameId: String) {
        val elapsed = elapsedGameSeconds()
        currentGameFinished = true
        track("game_finish", "game_id" to gameId, "mode" to currentGameMode, "elapsed_s" to elapsed)
    }

    /** از ناوبری: از مسیر بازی بیرون آمدیم */
    fun gameLeft() {
        val g = currentGame ?: return
        if (!currentGameFinished) emitAbandon(g)
        currentGame = null
        currentGameFinished = false
    }

    private fun emitAbandon(gameId: String) {
        val s = elapsedGameSeconds()
        track(
            "game_abandon",
            "game_id" to gameId,
            "mode" to currentGameMode,
            "elapsed_s" to s,
            "elapsed_bucket" to when {
                s < 30 -> "<30s"
                s < 120 -> "30s-2m"
                s < 600 -> "2m-10m"
                else -> ">10m"
            },
        )
    }

    private fun elapsedGameSeconds(): Long =
        if (currentGameStartedAt == 0L) 0 else (SystemClock.elapsedRealtime() - currentGameStartedAt) / 1000

    // ================= شبکه =================

    fun onlineEntered(role: String) {
        if (inOnlineRoom) return
        inOnlineRoom = true
        gameModeChanged("online")
        track("online_enter", "role" to role, "game_id" to currentGame)
    }

    fun onlineLeft() {
        if (!inOnlineRoom) return
        inOnlineRoom = false
        track("online_leave", "game_id" to currentGame)
    }

    fun onlineJoinFailed(reason: String) =
        track("online_join_failed", "game_id" to currentGame, "reason" to reason.take(60))

    fun lanHosted() {
        gameModeChanged("lan")
        track("lan_host", "game_id" to currentGame)
    }

    // ================= ارسال =================

    private fun startFlushLoop() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_EVERY_MS)
                flush()
            }
        }
    }

    private suspend fun flush() {
        val client = Cloud.client ?: return
        if (!isEnabled) return
        val batch = lock.withLock { if (queue.isEmpty()) null else ArrayList(queue) } ?: return
        try {
            client.from("events").insert(batch)
            lock.withLock {
                // فقط همان‌هایی که فرستادیم پاک می‌شوند؛ تازه‌رسیده‌ها می‌مانند
                repeat(batch.size) { if (queue.isNotEmpty()) queue.removeAt(0) }
                persistQueueLocked()
            }
        } catch (e: Exception) {
            Log.d(TAG, "ارسال رویدادها به بعد موکول شد: ${e.message}")
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive) {
                heartbeat()
                delay(HEARTBEAT_EVERY_MS)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun heartbeat() {
        val client = Cloud.client ?: return
        val sid = sessionId ?: return
        try {
            client.postgrest.rpc(
                "analytics_heartbeat",
                buildJsonObject {
                    put("p_session", sid)
                    put("p_device", deviceId)
                    val uid = runCatching { AccountRepository.currentUserId() }.getOrNull()
                    if (uid != null) put("p_user", uid) else put("p_user", JsonPrimitive(null as String?))
                    put("p_version", BuildConfig.VERSION_NAME)
                    put("p_sdk", Build.VERSION.SDK_INT)
                },
            )
        } catch (e: Exception) {
            Log.d(TAG, "ضربان نشست نرفت: ${e.message}")
        }
    }

    // ================= صف روی دیسک =================

    private fun queueFile(): File? = appContext?.let { File(it.filesDir, QUEUE_FILE) }

    private suspend fun loadQueue() {
        val f = queueFile() ?: return
        if (!f.exists()) return
        try {
            val rows = json.decodeFromString<List<EventRow>>(f.readText())
            lock.withLock { queue.addAll(0, rows.takeLast(MAX_QUEUE)) }
        } catch (_: Exception) {
            f.delete()
        }
    }

    /** باید داخل lock صدا زده شود */
    private fun persistQueueLocked() {
        val f = queueFile() ?: return
        try {
            if (queue.isEmpty()) f.delete() else f.writeText(json.encodeToString(queue))
        } catch (_: Exception) {
        }
    }
}
