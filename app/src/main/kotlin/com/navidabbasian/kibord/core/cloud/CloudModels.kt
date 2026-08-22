package com.navidabbasian.kibord.core.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** پروفایل بازیکن — آینه‌ی جدول profiles */
@Serializable
data class CloudProfile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
) {
    /** نامی که در رابط کاربری نشان داده می‌شود */
    val shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/** آمار یک بازی برای یک کاربر — آینه‌ی جدول game_stats */
@Serializable
data class CloudGameStat(
    @SerialName("user_id") val userId: String,
    @SerialName("game_id") val gameId: String,
    val plays: Int,
    val wins: Int,
)

/** یک ردیف از جدول رتبه‌بندی */
@Serializable
data class LeaderboardRow(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("total_plays") val totalPlays: Int,
    @SerialName("total_wins") val totalWins: Int,
    @SerialName("games_tried") val gamesTried: Int,
) {
    val shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/** نتیجه‌ی کارهای ابری — تا رابط کاربری بتواند خطای فارسی نشان دهد */
sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>
    data class Failed(val message: String) : CloudResult<Nothing>
}
