package com.navidabbasian.kibord.core.content

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * بانک محتوای بازی‌ها: بارگذاری فایل‌های JSON از کش محلی (filesDir/content)
 * و در نبودش از assets؛ به‌علاوه به‌روزرسانی دوره‌ای از مخزن گیت‌هاب.
 *
 * منبع به‌روزرسانی همان مخزن اپ است: با هر push روی شاخه‌ی main،
 * نسخه‌های نصب‌شده حداکثر ظرف یک روز بانک تازه را می‌گیرند.
 */
object ContentBank {

    private const val TAG = "ContentBank"
    private const val BASE_URL =
        "https://raw.githubusercontent.com/navidAbbasian/kibord/main/app/src/main/assets/"
    private val FILES = listOf(
        "words.json", "pantomime.json", "gandegoo.json",
        "taboo.json", "spy.json", "proverbs.json", "nofoozi.json",
    )

    private const val PREFS = "kibord_content_bank"
    private const val KEY_LAST_CHECK = "last_check_millis"
    private const val KEY_ETAG_PREFIX = "etag_"
    private const val KEY_APP_VERSION = "app_version_code"
    private const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

    /**
     * اگر نسخه‌ی اپ عوض شده، کش دانلودی پاک می‌شود تا بانک‌های تازه‌ی
     * داخل اپ زیر کشِ کهنه پنهان نمانند؛ اولین چک بعدی دوباره دانلود می‌کند.
     */
    private fun dropStaleCacheOnAppUpdate(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        } catch (_: Exception) {
            return
        }
        if (prefs.getLong(KEY_APP_VERSION, -1L) != current) {
            File(context.filesDir, "content").deleteRecursively()
            prefs.edit().clear().putLong(KEY_APP_VERSION, current).apply()
        }
    }

    /** متن یک فایل بانک: اول کش دانلودشده، بعد نسخه‌ی داخل اپ */
    fun open(context: Context, name: String): String {
        dropStaleCacheOnAppUpdate(context)
        val cached = cacheFile(context, name)
        if (cached.exists()) {
            try {
                val text = cached.readText()
                if (text.isNotBlank()) return text
            } catch (e: Exception) {
                Log.w(TAG, "خواندن کش $name شکست خورد", e)
            }
        }
        return context.assets.open(name).bufferedReader().use { it.readText() }
    }

    /**
     * اگر از آخرین بررسی بیش از یک روز گذشته، بانک‌ها را از گیت‌هاب تازه می‌کند.
     * خطای شبکه بی‌صدا نادیده گرفته می‌شود؛ بازی همیشه با بانک موجود بالا می‌آید.
     */
    suspend fun refreshIfStale(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MILLIS) return
        refreshNow(context)
    }

    /** به‌روزرسانی فوری همه‌ی بانک‌ها (دکمه‌ی تنظیمات) — true یعنی همه با موفقیت بررسی شدند */
    suspend fun refreshNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        dropStaleCacheOnAppUpdate(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var anyFailure = false
        for (name in FILES) {
            try {
                downloadIfChanged(context, name)
            } catch (e: Exception) {
                anyFailure = true
                Log.w(TAG, "به‌روزرسانی $name شکست خورد", e)
            }
        }
        // فقط وقتی همه‌ی فایل‌ها بررسی شدند زمان چک ثبت می‌شود تا خطاها دوباره تلاش شوند
        if (!anyFailure) {
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        }
        !anyFailure
    }

    private fun downloadIfChanged(context: Context, name: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val connection = URL(BASE_URL + name).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        // فقط وقتی کشِ سالم داریم ۳۰۴ معنا دارد؛ وگرنه دانلود کامل لازم است
        if (cacheFile(context, name).exists()) {
            prefs.getString(KEY_ETAG_PREFIX + name, null)?.let {
                connection.setRequestProperty("If-None-Match", it)
            }
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> return
                HttpURLConnection.HTTP_OK -> {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    require(isValidBank(text)) { "پاسخ $name یک آرایه‌ی JSON معتبر نیست" }
                    val target = cacheFile(context, name)
                    val temp = File(target.parentFile, "$name.tmp")
                    temp.writeText(text)
                    if (!temp.renameTo(target)) {
                        target.writeText(text)
                        temp.delete()
                    }
                    connection.getHeaderField("ETag")?.let {
                        prefs.edit().putString(KEY_ETAG_PREFIX + name, it).apply()
                    }
                    Log.i(TAG, "بانک $name به‌روز شد (${text.length} بایت)")
                }
                else -> error("HTTP ${connection.responseCode} برای $name")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** پاسخ باید آرایه‌ی JSON غیرخالی باشد تا یک بانک خراب جای بانک سالم را نگیرد */
    private fun isValidBank(text: String): Boolean = try {
        Json.parseToJsonElement(text).jsonArray.isNotEmpty()
    } catch (_: Exception) {
        false
    }

    private fun cacheFile(context: Context, name: String): File {
        val dir = File(context.filesDir, "content")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, name)
    }
}
