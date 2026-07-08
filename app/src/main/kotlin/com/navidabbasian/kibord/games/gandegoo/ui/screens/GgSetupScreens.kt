package com.navidabbasian.kibord.games.gandegoo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import androidx.compose.foundation.layout.offset

/** انتخاب تعداد تیم‌ها: ۲ یا ۳ تیم دونفره */
@Composable
fun GgTeamCountScreen(onTeamCountSelected: (Int) -> Unit) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        BobbingEmoji(emoji = "🎭", fontSize = 60.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "چند تیم هستید؟", rotation = -2f)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "هر تیم دو نفره است",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally)
        ) {
            listOf(2 to "۴ نفر", 3 to "۶ نفر").forEachIndexed { i, (count, people) ->
                ChoiceBubble(
                    main = count.toPersianDigits(),
                    sub = "تیم — $people",
                    size = 148.dp,
                    tilt = if (i % 2 == 0) -3f else 3f,
                    phase = i * 1.5f,
                    modifier = Modifier.offset(y = if (i % 2 == 0) 0.dp else 30.dp),
                    onClick = { onTeamCountSelected(count) }
                )
            }
        }
    }
}

/** ورود نام تیم‌ها */
@Composable
fun GgTeamNamesScreen(
    teamCount: Int,
    teamNames: List<String>,
    onNameChanged: (Int, String) -> Unit,
    onConfirm: () -> Unit,
) {
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "نام تیم‌ها",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "چه اسمی روی خودتون می‌ذارید؟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        repeat(teamCount) { i ->
            val color = teamColors.getOrElse(i) { teamColors[0] }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(color, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = teamNames.getOrElse(i) { "" },
                    onValueChange = { onNameChanged(i, it.take(20)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "تیم ${(i + 1).toPersianDigits()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = color,
                        unfocusedBorderColor = kiExtras.glassBorder,
                        focusedContainerColor = kiExtras.glass,
                        unfocusedContainerColor = kiExtras.glass,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = color,
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "ادامه", onClick = onConfirm)
        }
    }
}

/** انتخاب تعداد کتگوری‌های بازی */
@Composable
fun GgSetupScreen(onStart: (categoryCount: Int) -> Unit) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        BobbingEmoji(emoji = "🧮", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "چند کتگوری؟", rotation = 2f)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "هر کتگوری سه سوال ۲۰، ۴۰ و ۶۰ امتیازی دارد",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
        ) {
            listOf(
                Triple(3, "کوتاه", "۹ سوال"),
                Triple(4, "متوسط", "۱۲ سوال"),
                Triple(6, "کامل", "۱۸ سوال"),
            ).forEachIndexed { i, (count, title, subtitle) ->
                ChoiceBubble(
                    main = count.toPersianDigits(),
                    sub = "$title\n$subtitle",
                    size = 112.dp,
                    mainFontSize = 30.sp,
                    tilt = if (i % 2 == 0) -3f else 3f,
                    phase = i * 1.2f,
                    modifier = Modifier.offset(y = if (i == 1) 34.dp else 0.dp),
                    onClick = { onStart(count) }
                )
            }
        }
    }
}
