package com.navidabbasian.kibord.games.kalamz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Groups2
import androidx.compose.material.icons.filled.Groups3
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import com.navidabbasian.kibord.core.util.toPersianDigits

private data class PlayerOption(val count: Int, val icon: ImageVector, val color: Color)

@Composable
fun PlayerCountScreen(onPlayerCountSelected: (Int) -> Unit) {
    val teamColors = kiExtras.teamColors
    val sound = LocalSoundManager.current

    val options = listOf(
        PlayerOption(4,  Icons.Default.People,    teamColors[1]),
        PlayerOption(6,  Icons.Default.Groups,    teamColors[2]),
        PlayerOption(8,  Icons.Default.Groups2,   teamColors[3]),
        PlayerOption(10, Icons.Default.Groups3,   teamColors[4]),
        PlayerOption(12, Icons.Default.Groups,    teamColors[5]),
        PlayerOption(14, Icons.Default.Whatshot,  teamColors[6]),
        PlayerOption(16, Icons.Default.PersonAdd, teamColors[7]),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        Icon(
            Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "چند نفرید؟",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "تعداد بازیکنان رو انتخاب کنید",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            items(options) { option ->
                val i = options.indexOf(option)
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ChoiceBubble(
                        main = option.count.toPersianDigits(),
                        sub = "نفر — ${(option.count / 2).toPersianDigits()} تیم",
                        size = 128.dp,
                        accent = option.color,
                        tilt = if (i % 2 == 0) -3f else 3f,
                        phase = i * 1.1f,
                        modifier = Modifier.offset(y = if (i % 2 == 0) 0.dp else 12.dp),
                        onClick = { onPlayerCountSelected(option.count) }
                    )
                }
            }
        }
    }
}
