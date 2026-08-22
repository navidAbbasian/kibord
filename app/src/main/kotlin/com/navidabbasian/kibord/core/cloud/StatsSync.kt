package com.navidabbasian.kibord.core.cloud

import android.content.Context
import android.util.Log
import com.navidabbasian.kibord.core.stats.GameStats
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

/**
 * همگام‌سازی آمار محلی با ابر.
 *
 * قاعده‌ی بنیادی: **دفترچه‌ی محلی منبع حقیقت است.** بازی بدون اینترنت هم
 * ثبت می‌شود؛ این لایه فقط هر وقت شد آن را به ابر می‌رساند. اگر اینترنت
 * نبود یا کاربر لاگین نبود، هیچ اتفاقی نمی‌افتد و بازی هیچ آسیبی نمی‌بیند.
 *
 * ادغام با بیشترین مقدار انجام می‌شود: اگر کاربر روی گوشی دیگری بازی کرده
 * باشد، عدد بزرگ‌تر می‌ماند و آمار از بین نمی‌رود.
 */
object StatsSync {

    private const val TAG = "StatsSync"

    /**
     * آمار محلی را به ابر می‌فرستد. بی‌صداست: هر خطایی فقط لاگ می‌شود،
     * چون این کار هیچ‌وقت نباید جلوی بازی کردن را بگیرد.
     *
     * @return تعداد ردیف‌های همگام‌شده، یا null اگر اصلاً انجام نشد
     */
    suspend fun pushLocalStats(context: Context): Int? {
        val c = Cloud.client ?: return null
        val userId = AccountRepository.currentUserId() ?: return null

        val local = GameStats.playsByGame(context)
        if (local.isEmpty()) return 0

        return try {
            // آنچه ابر دارد را می‌خوانیم تا عقب‌گرد پیش نیاید
            val remote = c.from("game_stats")
                .select(Columns.ALL) { filter { eq("user_id", userId) } }
                .decodeList<CloudGameStat>()
                .associateBy { it.gameId }

            val merged = local.map { (gameId, plays) ->
                val cloudRow = remote[gameId]
                CloudGameStat(
                    userId = userId,
                    gameId = gameId,
                    plays = maxOf(plays, cloudRow?.plays ?: 0),
                    wins = cloudRow?.wins ?: 0,
                )
            }

            // کلید اصلی جدول (user_id, game_id) است، پس upsert خودش
            // ردیف موجود را به‌روز می‌کند و ردیف تازه را می‌سازد
            c.from("game_stats").upsert(merged)
            merged.size
        } catch (e: Exception) {
            Log.w(TAG, "همگام‌سازی آمار انجام نشد", e)
            null
        }
    }

    /** جدول رتبه‌بندی برای بخش پز دادن */
    suspend fun leaderboard(limit: Int = 50): CloudResult<List<LeaderboardRow>> {
        val c = Cloud.client ?: return CloudResult.Failed("بخش آنلاین فعال نیست")
        return try {
            val rows = c.from("leaderboard")
                .select(Columns.ALL) {
                    order("total_wins", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<LeaderboardRow>()
            CloudResult.Ok(rows)
        } catch (e: Exception) {
            Log.w(TAG, "خواندن جدول رتبه‌بندی شکست خورد", e)
            CloudResult.Failed("جدول رتبه‌بندی نیامد — اینترنت را چک کن")
        }
    }
}
