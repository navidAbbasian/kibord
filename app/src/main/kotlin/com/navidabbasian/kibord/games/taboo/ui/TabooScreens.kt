package com.navidabbasian.kibord.games.taboo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TeamMedallions
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.taboo.model.TabooUiState

/** ورود نام تیم‌ها + انتخاب دو یا سه تیم */
@Composable
fun TabooTeamNamesScreen(
    state: TabooUiState,
    onTeamCount: (Int) -> Unit,
    onNameChanged: (Int, String) -> Unit,
    onConfirm: () -> Unit,
) {
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(26.dp))
        BobbingEmoji(emoji = "🤐", fontSize = 54.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "کلمه ممنوعه!", rotation = -2f)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "کلمه رو برسون بدون گفتنِ پنج کلمه‌ی ممنوعه — مچ‌گیری آزاد!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(2, 3).forEach { c ->
                TabooPill(
                    text = "${c.toPersianDigits()} تیم",
                    selected = state.teamCount == c,
                    onClick = { onTeamCount(c) },
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        repeat(state.teamCount) { i ->
            BlobTextField(
                value = state.teamNames.getOrElse(i) { "" },
                onValueChange = { onNameChanged(i, it.take(20)) },
                placeholder = "تیم ${(i + 1).toPersianDigits()}",
                color = teamColors.teamColorFor(i),
                badge = (i + 1).toPersianDigits(),
                tilt = if (i % 2 == 0) -1.2f else 1.2f,
                phase = i * 1.6f,
                modifier = Modifier
                    .padding(vertical = 7.dp)
                    .offset(x = if (i % 2 == 0) (-6).dp else 6.dp)
            )
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

/** تراشه‌ی قرصی انتخاب‌شدنی تنظیمات */
@Composable
private fun TabooPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(if (selected) accent else extras.glassStrong, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null) {
                sound?.playButtonClick()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** تنظیمات: مدت نوبت و تعداد راند */
@Composable
fun TabooSettingsScreen(
    state: TabooUiState,
    onSeconds: (Int) -> Unit,
    onRounds: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val accent = LocalGameAccent.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        BobbingEmoji(emoji = "⏱", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "تنظیمات بازی", rotation = 2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "مدت هر نوبت",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(60, 90, 120).forEach { sec ->
                TabooPill(
                    text = "${sec.toPersianDigits()} ثانیه",
                    selected = state.turnSeconds == sec,
                    onClick = { onSeconds(sec) },
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "تعداد راند (هر راند یک نوبت برای هر تیم)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TabooPill(text = "−", selected = false, onClick = { onRounds(state.totalRounds - 1) })
            Text(
                text = state.totalRounds.toPersianDigits(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = accent,
            )
            TabooPill(text = "+", selected = false, onClick = { onRounds(state.totalRounds + 1) })
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "شروع بازی!", onClick = onStart)
        }
    }
}

/** گوشی دست گوینده‌ی تیم بعدی */
@Composable
fun TabooTurnReadyScreen(
    state: TabooUiState,
    team: Int,
    onStart: () -> Unit,
) {
    val color = kiExtras.teamColors.teamColorFor(team)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TeamMedallions(
            count = state.teamCount,
            nameOf = state::teamDisplayName,
            scoreOf = { state.scores[it] },
            highlight = team,
        )
        Spacer(modifier = Modifier.height(26.dp))
        Text(
            text = "راند ${state.roundIndex.toPersianDigits()} از ${state.totalRounds.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BobbingEmoji(emoji = "🤐", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "نوبت ${state.teamDisplayName(team)}",
            style = MaterialTheme.typography.displayMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "گوشی دست گوینده — بقیه‌ی تیمت حدس می‌زنن،\nتیم حریف مچ می‌گیره که کلمه‌ی ممنوعه نگی!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        KButton(text = "آماده‌ام — شروع!", onClick = onStart, accent = color)
    }
}

/** نوبت فعال: کارت کلمه + پنج ممنوعه + سه دکمه */
@Composable
fun TabooTurnScreen(
    state: TabooUiState,
    onCorrect: () -> Unit,
    onFoul: () -> Unit,
    onSkip: () -> Unit,
) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    val urgent = state.secondsLeft <= 5

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val progress by animateFloatAsState(
        targetValue = if (state.turnSeconds == 0) 0f else state.secondsLeft.toFloat() / state.turnSeconds,
        animationSpec = tween(300),
        label = "taboo_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(84.dp)
                .then(if (urgent) Modifier.breathing(intensity = 0.06f, periodMs = 600) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(84.dp)) {
                drawArc(
                    color = extras.glassStrong,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = if (urgent) extras.danger else extras.gold,
                    startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = state.secondsLeft.toPersianDigits(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (urgent) extras.danger else MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "✅ ${state.turnCorrect.toPersianDigits()}   🚫 ${state.turnFoul.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // ---- کارت کلمه ----
        val card = state.currentCard
        TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1.2f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = card?.word ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "نگــو! 🤐",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.danger,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                card?.forbidden?.forEach { f ->
                    Text(
                        text = f,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ---- دکمه‌ها ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            KButton(text = "درست گفت! ✅", onClick = onCorrect, accent = extras.success)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    KButton(text = "تخلف! 🚫", onClick = onFoul, style = KButtonStyle.Danger)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    KButton(text = "رد ⏭", onClick = onSkip, style = KButtonStyle.Glass)
                }
            }
        }
    }
}

/** جمع‌بندی نوبت + بررسی داورانه‌ی امتیاز */
@Composable
fun TabooTurnEndScreen(
    state: TabooUiState,
    team: Int,
    correct: Int,
    foul: Int,
    onAdjust: (Int) -> Unit,
    onProceed: () -> Unit,
) {
    val color = kiExtras.teamColors.teamColorFor(team)
    val extras = kiExtras
    val turnScore = correct - foul + state.turnBonus

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BobbingEmoji(emoji = "⏰", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "وقت تموم شد!", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(18.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.teamDisplayName(team),
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "✅ ${correct.toPersianDigits()} درست    🚫 ${foul.toPersianDigits()} تخلف",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ---- بررسی امتیاز: جمع می‌تواند حکم نهایی را کم و زیاد کند ----
                Text(
                    text = "بررسی امتیاز این نوبت",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TabooPill(text = "−۱", selected = false, onClick = { onAdjust(-1) })
                    Text(
                        text = turnScore.toPersianDigits(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = if (turnScore >= 0) extras.success else extras.danger,
                    )
                    TabooPill(text = "+۱", selected = false, onClick = { onAdjust(1) })
                }
                if (state.turnBonus != 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "حکم داورها: ${if (state.turnBonus > 0) "+" else "−"}${kotlin.math.abs(state.turnBonus).toPersianDigits()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.gold,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TeamMedallions(
            count = state.teamCount,
            nameOf = state::teamDisplayName,
            scoreOf = { state.scores[it] },
        )
        Spacer(modifier = Modifier.height(26.dp))
        KButton(text = "ادامه", onClick = onProceed, modifier = Modifier.navigationBarsPadding())
    }
}

/** اعلام برنده */
@Composable
fun TabooWinnerScreen(
    state: TabooUiState,
    winners: List<Int>,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val teamColors = kiExtras.teamColors

    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🏆", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (winners.size > 1) "مساوی! ${winners.joinToString(" و ") { state.teamDisplayName(it) }}"
                else state.teamDisplayName(winners.firstOrNull() ?: 0),
                style = MaterialTheme.typography.displayMedium,
                color = teamColors.teamColorFor(winners.firstOrNull() ?: 0),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            TeamMedallions(
                count = state.teamCount,
                nameOf = state::teamDisplayName,
                scoreOf = { state.scores[it] },
            )
            Spacer(modifier = Modifier.height(28.dp))
            KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
        }
    }
}
