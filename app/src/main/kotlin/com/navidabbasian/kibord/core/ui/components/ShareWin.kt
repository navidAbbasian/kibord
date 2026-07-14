package com.navidabbasian.kibord.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.share.WinnerCard
import com.navidabbasian.kibord.core.stats.GameStats

/**
 * دکمه‌ی «پز بده»: با اولین نمایش، برد را در دفترچه‌ی آمار ثبت می‌کند و
 * با لمس، کارت بردِ قابل اشتراک را می‌سازد و پنجره‌ی اشتراک را باز می‌کند.
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

    LaunchedEffect(Unit) {
        GameStats.recordGameFinished(context, gameId, winnerNames)
    }

    KButton(
        text = "پز بده! 📤",
        style = KButtonStyle.Glass,
        modifier = modifier,
        onClick = {
            sound?.playButtonClick()
            WinnerCard.share(context, gameTitle, gameEmoji, winnerText, scoreLines)
        },
    )
}
