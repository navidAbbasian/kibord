package com.navidabbasian.kibord.core.util

/** تبدیل ارقام لاتین به فارسی */
fun String.toPersianDigits(): String =
    map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

fun Int.toPersianDigits(): String = toString().toPersianDigits()

fun Long.toPersianDigits(): String = toString().toPersianDigits()

/** قالب m:ss با ارقام فارسی برای نمایش زمان تیم */
fun formatMillisAsClock(millis: Long): String {
    val total = (millis.coerceAtLeast(0) / 1000).toInt()
    val minutes = total / 60
    val seconds = total % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}".toPersianDigits()
}
