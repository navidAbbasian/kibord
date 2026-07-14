package com.navidabbasian.kibord.games.whoami.net

import com.navidabbasian.kibord.games.whoami.model.WaSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * پیام‌های شبکه‌ی من کی‌ام — هر پیام یک خط JSON روی سوکت است.
 * مهمان‌ها فرمان می‌فرستند، میزبان عکس کامل وضعیت را برمی‌گرداند.
 */
@Serializable
sealed class WaMessage {

    /** اولین پیام مهمان: معرفی با اسم */
    @Serializable
    @SerialName("hello")
    data class Hello(val name: String) : WaMessage()

    /** پاسخ میزبان به معرفی */
    @Serializable
    @SerialName("welcome")
    data class Welcome(val ok: Boolean, val error: String = "") : WaMessage()

    /** پخش وضعیت کامل از میزبان */
    @Serializable
    @SerialName("state")
    data class State(val snapshot: WaSnapshot) : WaMessage()

    /** اسم مخفی‌ای که برای هدفم نوشتم */
    @Serializable
    @SerialName("submit")
    data class SubmitName(val text: String) : WaMessage()

    /** «حدس زدم!» — می‌روم توی لیست انتظار */
    @Serializable
    @SerialName("guessed")
    data object Guessed : WaMessage()
}

val waJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun WaMessage.encode(): String = waJson.encodeToString(WaMessage.serializer(), this)

fun decodeWaMessage(line: String): WaMessage? = try {
    waJson.decodeFromString(WaMessage.serializer(), line)
} catch (_: Exception) {
    null
}
