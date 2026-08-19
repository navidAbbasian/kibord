package com.navidabbasian.kibord.core.rate

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.core.stats.GameStats

/**
 * درخواست امتیاز در مایکت — بعد از اولین بازیِ تمام‌شده، درست وقتی
 * کاربر تازه از بازی لذت برده.
 *
 * قاعده‌ها: فقط یک بار در هر مرحله پرسیده می‌شود، «بعداً» درخواست را چند
 * بازی عقب می‌اندازد و امتیاز دادن یا «نه ممنون» برای همیشه خاموشش می‌کند.
 */
object RateApp {

    /** بعد از این تعداد بازیِ تمام‌شده اولین بار پرسیده می‌شود */
    private const val FIRST_PROMPT_AFTER_PLAYS = 1

    /** «بعداً» یعنی این تعداد بازیِ دیگر بعد دوباره بپرس */
    private const val POSTPONE_PLAYS = 4

    private const val KEY_DONE = "rate_prompt_done"
    private const val KEY_NEXT_AT = "rate_prompt_next_at"

    private const val MYKET_PACKAGE = "ir.mservices.market"

    /** آیا همین حالا وقتِ نشان‌دادن درخواست امتیاز است؟ */
    fun shouldPrompt(context: Context): Boolean {
        if (GamePrefs.getBool(context, KEY_DONE, false)) return false
        val nextAt = GamePrefs.getInt(context, KEY_NEXT_AT, FIRST_PROMPT_AFTER_PLAYS)
        return GameStats.totalPlays(context) >= nextAt
    }

    /** دیگر هیچ‌وقت نپرس — کاربر امتیاز داد یا گفت نه */
    fun stopAsking(context: Context) {
        GamePrefs.setBool(context, KEY_DONE, true)
    }

    /** فعلاً نه — چند بازیِ دیگر که گذشت دوباره بپرس */
    fun postpone(context: Context) {
        GamePrefs.setInt(context, KEY_NEXT_AT, GameStats.totalPlays(context) + POSTPONE_PLAYS)
    }

    /**
     * صفحه‌ی ثبت نظر اپ را در مایکت باز می‌کند؛ اگر مایکت نصب نبود،
     * صفحه‌ی اپ در وب مایکت باز می‌شود. در هر دو حالت دیگر پرسیده نمی‌شود.
     */
    fun openRating(context: Context) {
        stopAsking(context)
        val pkg = context.packageName
        val inApp = Intent(Intent.ACTION_EDIT).apply {
            data = Uri.parse("myket://comment?id=$pkg")
            setPackage(MYKET_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(inApp)
            return
        } catch (_: ActivityNotFoundException) {
            // مایکت نصب نیست — می‌رویم سراغ نسخه‌ی وب
        }
        val web = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://myket.ir/app/$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
            // نه مایکت، نه مرورگر — کاری از دستمان برنمی‌آید
        }
    }
}
