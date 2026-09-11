package com.navidabbasian.kibord.games.uno.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.net.NetModeCard
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.uno.UnoUiState
import com.navidabbasian.kibord.games.uno.engine.UnoCard
import com.navidabbasian.kibord.games.uno.engine.UnoColor
import com.navidabbasian.kibord.games.uno.engine.UnoKind
import com.navidabbasian.kibord.games.uno.engine.UnoMode
import com.navidabbasian.kibord.games.uno.engine.UnoRules

/** صفحه‌ی تنظیمات اونو: اسم، تعداد بازیکن، مدل بازی و سقف امتیاز */
@Composable
fun UnoSetupScreen(
    state: UnoUiState,
    onName: (String) -> Unit,
    onMode: (UnoMode) -> Unit,
    onPlayers: (Int) -> Unit,
    onTarget: (Int) -> Unit,
    onStart: () -> Unit,
    onNetwork: () -> Unit,
) {
    val sound = LocalSoundManager.current

    Box(modifier = Modifier.fillMaxSize()) {
        GameHelpButton(gameId = "uno", modifier = Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // بادبزن کوچک کارت‌های نمونه به‌جای ایموجی
            SetupCardFan()

            Spacer(modifier = Modifier.height(10.dp))
            StickerTitle(text = "اونو")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "رنگ به رنگ جلو برو، +۲ و +۴ بنداز و اول از همه دستت رو خالی کن!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))

            BlobTextField(
                value = state.playerName,
                onValueChange = onName,
                placeholder = "اسمت چیه؟",
                badge = "🌈",
                tilt = -1f,
            )

            Spacer(modifier = Modifier.height(22.dp))
            UnoMatchOptions(state = state, onMode = onMode, onPlayers = onPlayers, onTarget = onTarget)

            Spacer(modifier = Modifier.height(24.dp))
            NetModeCard(onClick = onNetwork)
            Spacer(modifier = Modifier.height(28.dp))
            KButton(text = "بزن بریم! 🌈", onClick = { sound?.playButtonClick(); onStart() })
            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}


/** گزینه‌های مسابقه: تعداد نفرات، مدل و سقف امتیاز — هم در تنظیمات محلی، هم برای میزبان چندگوشی */
@Composable
fun UnoMatchOptions(
    state: UnoUiState,
    onMode: (UnoMode) -> Unit,
    onPlayers: (Int) -> Unit,
    onTarget: (Int) -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "چند نفره؟",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(2, 3, 4).forEachIndexed { i, n ->
                ChoiceBubble(
                    main = n.toPersianDigits(),
                    sub = "نفر",
                    size = 92.dp,
                    mainFontSize = 28.sp,
                    accent = if (state.players == n) accent else unoColorOf(UnoColor.entries[(i + 1) % 4]).copy(alpha = 0.55f),
                    tilt = if (i % 2 == 0) -3f else 3f,
                    phase = i * 1.1f,
                    modifier = Modifier.offset(y = if (i == 1) 12.dp else 0.dp),
                    onClick = { onPlayers(n) },
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "کدوم مدل؟",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))
        ModeCard(
            title = "کلاسیک",
            emoji = "🎯",
            desc = "قوانین اصلی اونو — ساده و همیشه‌خوب",
            selected = state.mode == UnoMode.CLASSIC,
            onClick = { onMode(UnoMode.CLASSIC) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        ModeCard(
            title = "هفت-صفر",
            emoji = "🔄",
            desc = "با ۷ دستت رو با یکی عوض کن؛ با ۰ دست همه می‌چرخه!",
            selected = state.mode == UnoMode.SEVEN_ZERO,
            onClick = { onMode(UnoMode.SEVEN_ZERO) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        ModeCard(
            title = "بی‌رحم",
            emoji = "🔥",
            desc = "‏+۲ روی +۲ و +۴ روی +۴ سوار کن تا جریمه گنده شه!",
            selected = state.mode == UnoMode.MERCILESS,
            onClick = { onMode(UnoMode.MERCILESS) },
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "تا چند امتیاز؟",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            UnoRules.TARGETS.forEachIndexed { i, t ->
                ChoiceBubble(
                    main = if (t == 0) "تک‌دست" else t.toPersianDigits(),
                    sub = if (t == 0) "یه دور" else "امتیاز",
                    size = 96.dp,
                    mainFontSize = if (t == 0) 18.sp else 26.sp,
                    accent = if (state.target == t) accent else extras.glassBorderStrong,
                    tilt = if (i % 2 == 0) -3f else 3f,
                    phase = i * 1.3f,
                    modifier = Modifier.offset(y = if (i == 1) 12.dp else 0.dp),
                    onClick = { onTarget(t) },
                )
            }
        }
    }
}

/** بادبزن چهار کارت رنگی برای تیتر صفحه */
@Composable
private fun SetupCardFan() {
    val cards = remember {
        listOf(
            UnoCard(-1, UnoKind.NUMBER, UnoColor.RED, 7),
            UnoCard(-2, UnoKind.DRAW_TWO, UnoColor.YELLOW),
            UnoCard(-3, UnoKind.REVERSE, UnoColor.GREEN),
            UnoCard(-4, UnoKind.NUMBER, UnoColor.BLUE, 4),
        )
    }
    Box(contentAlignment = Alignment.Center) {
        cards.forEachIndexed { i, card ->
            UnoCardFace(
                card = card,
                width = 52.dp,
                modifier = Modifier
                    .offset(x = 34.dp * (i - 1.5f))
                    .graphicsLayer { rotationZ = (i - 1.5f) * 9f; translationY = if (i == 1 || i == 2) -8f else 6f },
            )
        }
    }
}

/** کارت انتخاب مدل بازی */
@Composable
private fun ModeCard(
    title: String,
    emoji: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) accent.copy(alpha = 0.18f) else extras.glass, shape)
            .border(2.dp, if (selected) accent else extras.glassBorder, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                sound?.playButtonClick()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, fontSize = 26.sp)
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Text(text = "✓", fontSize = 20.sp, fontWeight = FontWeight.Black, color = accent)
        }
    }
}
