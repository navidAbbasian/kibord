package com.navidabbasian.kibord.core.session

import android.content.Context
import android.util.Log
import java.io.File

/**
 * انبار نشستِ بازی برای مقاوم‌سازی در برابر مرگ پروسه:
 * وضعیت جاریِ بازی به‌صورت یک رشته‌ی JSON روی دیسک ذخیره می‌شود تا اگر
 * اندروید اپ را در پس‌زمینه بکشد، در بازگشت بازی از همان‌جا ادامه یابد.
 *
 * هر بازی یک کلید یکتا دارد؛ با پایان یا خروج، نشست پاک می‌شود.
 */
object SessionStore {

    private const val TAG = "SessionStore"
    private const val DIR = "session"

    private fun file(context: Context, key: String): File {
        val dir = File(context.applicationContext.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.json")
    }

    fun save(context: Context, key: String, json: String) {
        try {
            file(context, key).writeText(json)
        } catch (e: Exception) {
            Log.w(TAG, "ذخیره‌ی نشست $key شکست خورد", e)
        }
    }

    /** رشته‌ی ذخیره‌شده یا تهی اگر نشستی نباشد */
    fun load(context: Context, key: String): String? = try {
        val f = file(context, key)
        if (f.exists()) f.readText().ifBlank { null } else null
    } catch (e: Exception) {
        Log.w(TAG, "خواندن نشست $key شکست خورد", e)
        null
    }

    fun clear(context: Context, key: String) {
        try {
            file(context, key).delete()
        } catch (_: Exception) {
        }
    }
}
