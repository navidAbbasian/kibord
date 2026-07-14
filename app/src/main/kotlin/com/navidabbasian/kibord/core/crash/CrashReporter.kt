package com.navidabbasian.kibord.core.crash

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * نگهبان کرش بدون سرور: اگر اپ با خطای مهلک بسته شود، ردپای خطا
 * روی گوشی ذخیره می‌شود و در اجرای بعدی از بازیکن می‌پرسیم که
 * گزارشش را با ایمیل برای سازنده بفرستد یا نه.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val FEEDBACK_EMAIL = "abbasian.navid@gmail.com"
    private const val MAX_TRACE_CHARS = 6000

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                val version = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) {
                    "?"
                }
                File(context.filesDir, FILE_NAME).writeText(
                    "نسخه اپ: $version\n" +
                        "دستگاه: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "اندروید: ${Build.VERSION.RELEASE}\n\n" +
                        trace.take(MAX_TRACE_CHARS)
                )
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** گزارش کرشِ اجرای قبل — تهی یعنی همه‌چیز مرتب بوده */
    fun pendingReport(context: Context): String? = try {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText().ifBlank { null } else null
    } catch (_: Exception) {
        null
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
        }
    }

    /** باز کردن اپ ایمیل با گزارش آماده؛ برگشتی یعنی موفق بود یا نه */
    fun sendByEmail(context: Context, report: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "گزارش کرش — کی برد؟")
            putExtra(Intent.EXTRA_TEXT, "سلام! اپ دفعه‌ی قبل بسته شد. گزارش فنی:\n\n$report")
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
