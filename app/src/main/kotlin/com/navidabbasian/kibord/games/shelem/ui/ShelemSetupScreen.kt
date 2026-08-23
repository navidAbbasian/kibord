package com.navidabbasian.kibord.games.shelem.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.shelem.ShelemUiState
import com.navidabbasian.kibord.games.shelem.engine.ShelemRules

/** صفحه‌ی تنظیمات شلم: اسم بازیکن، سقف امتیاز و حساب شدنِ شلم */
@Composable
fun ShelemSetupScreen(
    state: ShelemUiState,
    onName: (String) -> Unit,
    onTarget: (Int) -> Unit,
    onShelemBonus: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current

    Box(modifier = Modifier.fillMaxSize()) {
        GameHelpButton(gameId = "shelem", modifier = Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            BobbingEmoji(emoji = "♠️", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(10.dp))
            StickerTitle(text = "شلم")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "تو و یارت مقابل دو ربات؛ شرط ببند، حکم کن، ۱۶۵ امتیاز رو بچین!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(22.dp))

            BlobTextField(
                value = state.playerName,
                onValueChange = onName,
                placeholder = "اسمت چیه؟",
                badge = "😎",
                tilt = -1f,
            )

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "بازی تا چند؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShelemRules.TARGETS.forEachIndexed { i, t ->
                    ChoiceBubble(
                        main = t.toPersianDigits(),
                        sub = "امتیاز",
                        size = 100.dp,
                        mainFontSize = 26.sp,
                        accent = if (state.target == t) accent else extras.teamColors.teamColorFor(i + 2),
                        tilt = if (i % 2 == 0) -3f else 3f,
                        phase = i * 1.2f,
                        modifier = Modifier.offset(y = if (i == 1) 14.dp else 0.dp),
                        onClick = { onTarget(t) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                strong = true,
                onClick = { sound?.playButtonClick(); onShelemBonus(!state.shelemBonus) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "شلم حساب بشه",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "اگه تیمی همه‌ی ۱۶۵ امتیاز دست رو ببره، امتیازش دوبرابر می‌شه (۳۳۰)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    ShelemToggle(on = state.shelemBonus, onToggle = { onShelemBonus(it) })
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            KButton(text = "بزن بریم! 🃏", onClick = { sound?.playButtonClick(); onStart() })
            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/** کلید روشن/خاموشِ ساده‌ی هم‌خانواده با بقیه‌ی اپ */
@Composable
private fun ShelemToggle(on: Boolean, onToggle: (Boolean) -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val track by animateColorAsState(if (on) accent else extras.glassBorderStrong, label = "track")
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(30.dp)
            .background(track, RoundedCornerShape(15.dp))
            .border(1.dp, extras.glassBorder, RoundedCornerShape(15.dp))
            .clickable(interactionSource = MutableInteractionSource(), indication = null) { onToggle(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (on) "✓" else "",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
