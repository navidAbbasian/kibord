package com.navidabbasian.kibord.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.share.WinnerCard
import com.navidabbasian.kibord.core.stats.GameStats
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.cloud.AccountRepository
import com.navidabbasian.kibord.core.cloud.StatsSync
import com.navidabbasian.kibord.core.net.online.OnlineRooms
import java.util.UUID

/**
 * دکمه‌ی «پز بده»: با اولین نمایش، برد را در دفترچه‌ی آمار ثبت می‌کند و
 * با لمس، کارت بردِ قابل اشتراک را می‌سازد و پنجره‌ی اشتراک را باز می‌کند.
 *
 * چون این دکمه روی صفحه‌ی برنده‌ی همه‌ی بازی‌هاست، درخواست امتیازِ مایکت
 * هم از همین‌جا بالا می‌آید — درست بعد از ثبتِ بازیِ تمام‌شده.
 */
@Composable
fun ShareWinButton(
    gameId: String,
    gameTitle: String,
    gameEmoji: String,
    winnerText: String,
    scoreLines: List<Pair<String, String>>,
    winnerNames: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sound = LocalSoundManager.current

    // شناسه‌ی یکتای این دستِ تمام‌شده: با چرخش صفحه و بازساخت اکتیویتی حفظ
    // می‌شود تا هر دست فقط یک بار در آمار ثبت شود
    val recordToken = rememberSaveable { UUID.randomUUID().toString() }
    LaunchedEffect(recordToken) {
        val fresh = GameStats.recordGameFinished(context, gameId, winnerNames, token = recordToken)
        // فقط بازیِ اینترنتی به آمار آنلاین بازیکن می‌رود؛ هویتش همان یوزرنیم حساب است
        if (fresh && OnlineRooms.active) {
            val me = AccountRepository.onlineIdentity()
            if (me != null) {
                val won = winnerNames.any { it.trim().equals(me, ignoreCase = true) }
                StatsSync.recordOnlineResult(gameId, won)
            }
        }
    }

    // بعد از اولین بازیِ تمام‌شده، درخواست امتیاز در مایکت
    RatePromptDialog()

    KButton(
        text = "پز بده! 📤",
        style = KButtonStyle.Glass,
        modifier = modifier,
        onClick = {
            sound?.playButtonClick()
            Analytics.track("share_win", "game_id" to gameId)
            WinnerCard.share(context, gameTitle, gameEmoji, winnerText, scoreLines)
        },
    )
}
