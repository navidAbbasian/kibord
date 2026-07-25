package com.navidabbasian.kibord.games.esmfamilsorati.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsConstants

/** انتخاب تعداد بازیکنان اسم فامیل سرعتی: ۲ تا ۸ نفر انفرادی */
@Composable
fun EfsPlayerCountScreen(onPlayerCountSelected: (Int) -> Unit) {
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Text(text = "⚡", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "چند نفرید؟",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "هرکی برای خودش بازی می‌کنه — آخرین بازمانده برنده‌ست",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        val options = (EfsConstants.MIN_PLAYERS..EfsConstants.MAX_PLAYERS).toList()
        options.chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally)
            ) {
                row.forEachIndexed { colIndex, count ->
                    val zig = (rowIndex + colIndex) % 2 == 0
                    val index = rowIndex * 2 + colIndex
                    ChoiceBubble(
                        main = count.toPersianDigits(),
                        sub = "نفر",
                        size = 120.dp,
                        accent = teamColors[index % teamColors.size],
                        tilt = if (zig) -3f else 3f,
                        phase = index * 1.3f,
                        modifier = Modifier.offset(y = if (zig) 0.dp else 18.dp),
                        onClick = { onPlayerCountSelected(count) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
