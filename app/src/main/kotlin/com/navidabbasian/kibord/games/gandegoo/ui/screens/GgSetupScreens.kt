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
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits

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
        Text(text = "🎭", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "چند تیم هستید؟",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "هر تیم دو نفره است",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))

        listOf(2 to "۴ نفر", 3 to "۶ نفر").forEach { (count, people) ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    sound?.playButtonClick()
                    onTeamCountSelected(count)
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${count.toPersianDigits()} تیم",
                        style = MaterialTheme.typography.displayMedium,
                        color = accent
                    )
                    Text(
                        text = people,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
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
        Text(text = "🧮", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "چند کتگوری؟",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "هر کتگوری سه سوال ۲۰، ۴۰ و ۶۰ امتیازی دارد",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        listOf(
            Triple(3, "کوتاه", "حدود ۱۵ دقیقه — ۹ سوال"),
            Triple(4, "متوسط", "حدود ۲۰ دقیقه — ۱۲ سوال"),
            Triple(6, "کامل", "حدود ۳۵ دقیقه — ۱۸ سوال"),
        ).forEach { (count, title, subtitle) ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    sound?.playButtonClick()
                    onStart(count)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${count.toPersianDigits()}",
                        style = MaterialTheme.typography.displayMedium,
                        color = accent
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
