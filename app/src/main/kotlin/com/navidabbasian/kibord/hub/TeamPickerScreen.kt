package com.navidabbasian.kibord.hub

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import androidx.compose.ui.platform.LocalContext

/**
 * تیم‌چین: اسم‌ها را بگیر، قرعه بزن و عادلانه تیم‌بندی کن —
 * ابزار قبل از شروع همه‌ی بازی‌های تیمی.
 */
@Composable
fun TeamPickerScreen() {
    val sound = LocalSoundManager.current
    val extras = kiExtras
    val context = LocalContext.current

    val names = remember {
        mutableStateListOf<String>().apply {
            val saved = GamePrefs.getNames(context, "teampicker_names")
            if (saved.isEmpty()) addAll(List(4) { "" }) else addAll(saved)
            while (size < 4) add("")
        }
    }
    var teamCount by remember { mutableIntStateOf(GamePrefs.getInt(context, "teampicker_teams", 2)) }
    var result by remember { mutableStateOf<List<List<String>>>(emptyList()) }

    val filled = names.map { it.trim() }.filter { it.isNotBlank() }
    val canDraw = filled.size >= teamCount

    KiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            BobbingEmoji(emoji = "🎰", fontSize = 46.sp)
            Spacer(modifier = Modifier.height(6.dp))
            StickerTitle(text = "تیم‌چین", accent = VioletPrimary, rotation = -2f, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "اسم‌ها رو بنویس، قرعه بزن — بدون بحث و دعوا تیم شید!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ---- تعداد تیم ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(2, 3, 4).forEach { c ->
                    val selected = teamCount == c
                    val interaction = remember { MutableInteractionSource() }
                    Text(
                        text = "${c.toPersianDigits()} تیم",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(
                                if (selected) VioletPrimary else extras.glassStrong,
                                RoundedCornerShape(50)
                            )
                            .clickable(interactionSource = interaction, indication = null) {
                                sound?.playButtonClick()
                                teamCount = c
                                result = emptyList()
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ---- اسم‌ها ----
            names.forEachIndexed { i, name ->
                BlobTextField(
                    value = name,
                    onValueChange = {
                        names[i] = it.take(16)
                        result = emptyList()
                    },
                    placeholder = "نفر ${(i + 1).toPersianDigits()}",
                    color = kiExtras.teamColors.teamColorFor(i),
                    badge = (i + 1).toPersianDigits(),
                    tilt = if (i % 2 == 0) -0.8f else 0.8f,
                    phase = i * 1.3f,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            KButton(
                text = "➕ یه نفر دیگه",
                style = KButtonStyle.Glass,
                onClick = {
                    sound?.playButtonClick()
                    names.add("")
                },
            )
            Spacer(modifier = Modifier.height(12.dp))

            KButton(
                text = if (canDraw) "قرعه بزن! 🎲"
                else "حداقل ${teamCount.toPersianDigits()} اسم لازمه",
                enabled = canDraw,
                onClick = {
                    sound?.playButtonClick()
                    GamePrefs.setNames(context, "teampicker_names", names.toList())
                    GamePrefs.setInt(context, "teampicker_teams", teamCount)
                    val shuffled = filled.shuffled()
                    result = List(teamCount) { t ->
                        shuffled.filterIndexed { index, _ -> index % teamCount == t }
                    }
                },
            )

            // ---- نتیجه ----
            if (result.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Column {
                    Column {
                        result.forEachIndexed { t, members ->
                            val color = kiExtras.teamColors.teamColorFor(t)
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                strong = true,
                                tilt = if (t % 2 == 0) -1f else 1f,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "تیم ${(t + 1).toPersianDigits()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(lerp(color, Color.White, 0.15f), color)
                                                ),
                                                RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = members.joinToString("، "),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding().height(20.dp))
        }
    }
}
