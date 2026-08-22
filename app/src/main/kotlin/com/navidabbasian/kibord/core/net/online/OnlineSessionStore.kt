package com.navidabbasian.kibord.core.net.online

import android.content.Context
import com.navidabbasian.kibord.core.settings.GamePrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * یک اتاق اینترنتیِ نیمه‌کاره که روی دیسک مانده.
 *
 * برای میزبان، [hostState] آخرین وضعیت کاملِ بازی است (همان رشته‌ای که روی
 * شبکه می‌رود) تا اگر اپ بسته شد یا گوشی آن را کشت، با همان کد اتاق
 * برگردد و مهمان‌ها دوباره وصل شوند. برای مهمان فقط کد و اسم کافی است —
 * وضعیت را میزبان می‌دهد.
 */
@Serializable
data class StoredOnlineRoom(
    val gameId: String,
    /** "host" یا "guest" */
    val role: String,
    val code: String,
    val name: String,
    val savedAt: Long,
    val hostState: String? = null,
) {
    val isHost: Boolean get() = role == "host"
}

/**
 * حافظه‌ی «بازی اینترنتیِ در جریان». در هر لحظه حداکثر یک اتاق ذخیره است.
 *
 * قاعده: خروجِ خواسته‌ی بازیکن (دکمه‌ی ترک بازی) پاکش می‌کند؛ بسته شدن
 * صفحه، قفل گوشی یا کشته شدن اپ نه — همان‌هایی که بازی نباید با آن‌ها بپرد.
 */
object OnlineSessionStore {

    private const val KEY = "online_room"

    /** بعد از این مدت، اتاق ذخیره‌شده کهنه حساب می‌شود و پیشنهاد نمی‌شود */
    const val MAX_AGE_MS = 30 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(context: Context, room: StoredOnlineRoom) {
        GamePrefs.setString(context, KEY, json.encodeToString(room))
    }

    /** میزبان بعد از هر تغییر وضعیت این را صدا می‌زند — ارزان است */
    fun saveHostState(context: Context, gameId: String, state: String) {
        val cur = load(context, gameId, ignoreAge = true) ?: return
        if (!cur.isHost) return
        save(context, cur.copy(hostState = state, savedAt = System.currentTimeMillis()))
    }

    /** اتاق ذخیره‌شده برای این بازی، یا null اگر نیست/کهنه است */
    fun load(context: Context, gameId: String, ignoreAge: Boolean = false): StoredOnlineRoom? {
        val r = current(context, ignoreAge) ?: return null
        return if (r.gameId == gameId) r else null
    }

    /** هر اتاقی که ذخیره است (برای بنرِ هاب) */
    fun current(context: Context, ignoreAge: Boolean = false): StoredOnlineRoom? {
        val raw = GamePrefs.getString(context, KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        val r = runCatching { json.decodeFromString<StoredOnlineRoom>(raw) }.getOrNull() ?: return null
        if (!ignoreAge && System.currentTimeMillis() - r.savedAt > MAX_AGE_MS) {
            clear(context)
            return null
        }
        return r
    }

    fun clear(context: Context) {
        GamePrefs.setString(context, KEY, "")
    }
}
