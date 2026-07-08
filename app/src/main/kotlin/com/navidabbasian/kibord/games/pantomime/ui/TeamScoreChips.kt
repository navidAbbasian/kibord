package com.navidabbasian.kibord.games.pantomime.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.navidabbasian.kibord.core.ui.components.TeamMedallions

/** ردیف امتیاز تیم‌ها — نمای مدالی مشترک اپ (امضای قدیمی حفظ شده) */
@Composable
fun TeamScoreChips(
    count: Int,
    nameOf: (Int) -> String,
    scoreOf: (Int) -> Int,
    highlight: Int = -1,
    modifier: Modifier = Modifier,
) {
    TeamMedallions(
        count = count,
        nameOf = nameOf,
        scoreOf = scoreOf,
        highlight = highlight,
        modifier = modifier,
    )
}
