package com.navidabbasian.kibord.games.pantomime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits

/** ردیف تراشه‌های امتیاز تیم‌ها با رنگ اختصاصی هر تیم */
@Composable
fun TeamScoreChips(
    count: Int,
    nameOf: (Int) -> String,
    scoreOf: (Int) -> Int,
    highlight: Int = -1,
    modifier: Modifier = Modifier,
) {
    val teamColors = kiExtras.teamColors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        repeat(count) { i ->
            val color = teamColors.getOrElse(i) { teamColors[0] }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(
                        color.copy(alpha = if (highlight < 0 || i == highlight) 1f else 0.55f),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = nameOf(i),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    text = scoreOf(i).toPersianDigits(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
            }
        }
    }
}
