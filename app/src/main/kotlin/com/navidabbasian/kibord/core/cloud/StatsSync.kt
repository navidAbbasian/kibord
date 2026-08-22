package com.navidabbasian.kibord.core.cloud

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * آمار آنلاین بازیکن.
 *
 * قاعده‌ی بنیادی: **فقط بازی‌های اینترنتی شمرده می‌شوند.** بازی آفلاین یا
 * روی وای‌فای محلی هیچ ردی این‌جا نمی‌گذارد، چون اسم‌هایش دستی است و
 * قابل اعتماد برای رقابت نیست. هویت بازیکن در بازی اینترنتی همان یوزرنیم
 * حسابش است، پس هر گوشی نتیجه‌ی خودش را برای خودش ثبت می‌کند.
 */
object StatsSync {

    private const val TAG = "StatsSync"

    /**
     * نتیجه‌ی یک بازی اینترنتیِ تمام‌شده را برای کاربر لاگین‌شده ثبت می‌کند.
     * بی‌صداست: شکست فقط لاگ می‌شود و هیچ‌وقت جلوی بازی را نمی‌گیرد.
     */
    suspend fun recordOnlineResult(gameId: String, won: Boolean): Boolean {
        val c = Cloud.client ?: return false
        if (AccountRepository.currentUserId() == null) return false
        return try {
            c.postgrest.rpc(
                "record_online_result",
                buildJsonObject {
                    put("p_game_id", gameId)
                    put("p_won", won)
                },
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "ثبت نتیجه‌ی آنلاین انجام نشد", e)
            false
        }
    }

    /** آمار خودِ کاربر به تفکیک بازی */
    suspend fun myStats(): CloudResult<List<CloudGameStat>> {
        val c = Cloud.client ?: return CloudResult.Failed("بخش آنلاین فعال نیست")
        val userId = AccountRepository.currentUserId() ?: return CloudResult.Ok(emptyList())
        return try {
            val rows = c.from("game_stats")
                .select(Columns.ALL) {
                    filter { eq("user_id", userId) }
                    order("wins", Order.DESCENDING)
                }
                .decodeList<CloudGameStat>()
            CloudResult.Ok(rows)
        } catch (e: Exception) {
            Log.w(TAG, "خواندن آمار کاربر شکست خورد", e)
            CloudResult.Failed("آمار نیامد — اینترنت را چک کن")
        }
    }

    /** برترین‌های یک بازی: بیشترین برد اول */
    suspend fun gameLeaderboard(gameId: String, limit: Int = 30): CloudResult<List<GameLeaderboardRow>> {
        val c = Cloud.client ?: return CloudResult.Failed("بخش آنلاین فعال نیست")
        return try {
            val rows = c.from("game_leaderboard")
                .select(Columns.ALL) {
                    filter { eq("game_id", gameId) }
                    order("rank", Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<GameLeaderboardRow>()
            CloudResult.Ok(rows)
        } catch (e: Exception) {
            Log.w(TAG, "خواندن لیدربورد شکست خورد", e)
            CloudResult.Failed("جدول رتبه‌بندی نیامد — اینترنت را چک کن")
        }
    }

    /** جدول کلی همه‌ی بازی‌ها */
    suspend fun leaderboard(limit: Int = 50): CloudResult<List<LeaderboardRow>> {
        val c = Cloud.client ?: return CloudResult.Failed("بخش آنلاین فعال نیست")
        return try {
            val rows = c.from("leaderboard")
                .select(Columns.ALL) {
                    order("total_wins", Order.DESCENDING)
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
