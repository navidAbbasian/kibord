package com.navidabbasian.kibord.games.dooz

import com.navidabbasian.kibord.games.dooz.engine.DoozMark
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * عکس کامل میز دوز که میزبان بعد از هر تغییر می‌فرستد — مهمان چیزی جز
 * همین را نمی‌داند و فقط همین را نقاشی می‌کند. دوز اطلاعات پنهان ندارد.
 * میزبان همیشه ❌ است و مهمان ⭕.
 */
@Serializable
data class DoozRoomSnapshot(
    val hostName: String = "",
    val guestName: String = "",
    val guestConnected: Boolean = false,
    /** سری شروع شده؟ (تا قبلش: لابی) */
    val started: Boolean = false,
    val targetWins: Int = 3,
    /** صفحه به شکل رشته‌ی ۹ حرفی "XO..X.O.." */
    val cells: String = ".........",
    val turn: DoozMark = DoozMark.X,
    val roundStarter: DoozMark = DoozMark.X,
    val roundNo: Int = 1,
    val xWins: Int = 0,
    val oWins: Int = 0,
    val draws: Int = 0,
    val hasResult: Boolean = false,
    val resultWinner: DoozMark? = null,
    val resultLine: List<Int>? = null,
    val resultShown: Boolean = false,
    val seriesWinner: DoozMark? = null,
    val lastMove: Int? = null,
)

/** پیام‌های شبکه‌ی دوز — مهمان فرمان می‌فرستد، میزبان وضعیت کامل برمی‌گرداند */
@Serializable
sealed class DoozMessage {

    @Serializable
    @SerialName("state")
    data class State(val room: DoozRoomSnapshot) : DoozMessage()

    /** مهمان: روی این خانه بزن — فقط در نوبت خودش پذیرفته می‌شود */
    @Serializable
    @SerialName("tap")
    data class Tap(val index: Int) : DoozMessage()

    @Serializable
    @SerialName("next")
    data object NextRound : DoozMessage()

    @Serializable
    @SerialName("again")
    data object PlayAgain : DoozMessage()
}

val doozJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun DoozMessage.encode(): String = doozJson.encodeToString(DoozMessage.serializer(), this)

fun decodeDoozMessage(line: String): DoozMessage? = try {
    doozJson.decodeFromString(DoozMessage.serializer(), line)
} catch (_: Exception) {
    null
}
