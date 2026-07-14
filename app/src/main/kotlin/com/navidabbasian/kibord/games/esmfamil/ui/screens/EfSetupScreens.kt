package com.navidabbasian.kibord.games.esmfamil.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.esmfamil.model.DEFAULT_TOPICS
import com.navidabbasian.kibord.games.esmfamil.model.EfPlayer
import com.navidabbasian.kibord.games.esmfamil.model.MIN_PLAYERS
import com.navidabbasian.kibord.games.esmfamil.model.sameName
import com.navidabbasian.kibord.games.esmfamil.net.EfDiscoveredGame
import com.navidabbasian.kibord.games.esmfamil.viewmodel.EsmFamilUiState

/** ورود: اسم خودت را بگو، بعد میزبان شو یا بپیوند */
@Composable
fun EfEntryScreen(
    state: EsmFamilUiState,
    onNameChanged: (String) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
) {
    // هشدار تم‌دار وقتی بدون نوشتن اسم روی دکمه‌ها بزند
    var showNameError by remember { mutableStateOf(false) }
    val guardName: (() -> Unit) -> Unit = { action ->
        if (state.myName.isBlank()) showNameError = true else action()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        BobbingEmoji(emoji = "✍️", fontSize = 58.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "اسم فامیل", rotation = -2f)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "چند نفری با گوشی‌های خودتون! همه روی یک وای‌فای یا هات‌اسپات باشید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        BlobTextField(
            value = state.myName,
            onValueChange = {
                onNameChanged(it)
                if (it.isNotBlank()) showNameError = false
            },
            placeholder = "اسمت چیه؟ (شناسه‌ی تو در بازی)",
            badge = "👤",
            tilt = -1f,
        )
        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally)
        ) {
            ChoiceBubble(
                main = "میزبان شو",
                sub = "بازی بساز و\nرفقا رو دعوت کن",
                emoji = "👑",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = -3f,
                onClick = { guardName(onHost) },
            )
            ChoiceBubble(
                main = "بپیوند",
                sub = "به بازیِ ساخته‌شده\nوصل شو",
                emoji = "🚪",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = 3f,
                phase = 1.5f,
                modifier = Modifier.offset(y = 26.dp),
                onClick = { guardName(onJoin) },
            )
        }

        if (showNameError && state.myName.isBlank()) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.breathing(intensity = 0.04f, periodMs = 1100)) {
                StickerTitle(
                    text = "✋ اول اسمت رو بنویس!",
                    accent = kiExtras.danger,
                    rotation = -2f,
                    fontSize = 22.sp,
                )
            }
        } else if (state.myName.isBlank()) {
            Spacer(modifier = Modifier.height(34.dp))
            Box(modifier = Modifier.breathing(intensity = 0.03f, periodMs = 2000)) {
                StickerTitle(
                    text = "✍️ اول اسمت رو بنویس",
                    accent = LocalGameAccent.current,
                    rotation = -2f,
                    fontSize = 18.sp,
                )
            }
        }
        state.connectError?.let {
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger)
        }
    }
}

/** پیوستن: بازی‌های پیداشده در شبکه + اتصال دستی با آدرس */
@Composable
fun EfJoinScreen(
    state: EsmFamilUiState,
    onJoin: (EfDiscoveredGame) -> Unit,
    onManualJoin: (String) -> Unit,
) {
    val accent = LocalGameAccent.current
    var manualAddress by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "📡", fontSize = 46.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "به کدوم بازی بپیوندیم؟", rotation = 2f, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (state.discovered.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1800)) {
                        Text(text = "🔎", fontSize = 30.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "دنبال بازی می‌گردم…\nمیزبان باید بازی رو ساخته باشه و روی همین شبکه باشید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(count = state.discovered.size, key = { state.discovered[it].hostName }) { i ->
                    val game = state.discovered[i]
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        strong = true,
                        tilt = if (i % 2 == 0) -1f else 1f,
                        onClick = { onJoin(game) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "✍️", fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "بازیِ ${game.hostName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "لمس کن تا وصل شی",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(text = "🚪", fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- اتصال دستی وقتی کشف خودکار جواب نداد ----
        Text(
            text = "پیدا نشد؟ آدرسِ نمایش‌داده‌شده در لابی میزبان رو بزن:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BlobTextField(
            value = manualAddress,
            onValueChange = { manualAddress = it },
            placeholder = "مثلاً 192.168.1.5",
            badge = "🔗",
            tilt = 0.8f,
        )
        Spacer(modifier = Modifier.height(10.dp))
        KButton(
            text = if (state.connecting) "در حال اتصال…" else "اتصال دستی",
            enabled = !state.connecting && manualAddress.isNotBlank(),
            onClick = { onManualJoin(manualAddress.trim()) },
        )
        state.connectError?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
    }
}

/** تراشه‌ی بازیکن: آواتار سنگریزه‌ای رنگی + اسم */
@Composable
fun EfPlayerChip(player: EfPlayer, isHost: Boolean, highlight: Boolean = false) {
    val color = kiExtras.teamColors.teamColorFor(player.colorIndex)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { alpha = if (player.connected) 1f else 0.4f }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .then(if (highlight) Modifier.breathing(intensity = 0.05f, periodMs = 1600) else Modifier)
                .background(
                    Brush.radialGradient(listOf(lerp(color, Color.White, 0.25f), color)),
                    blobShape(seed = player.name.hashCode())
                )
                .border(
                    2.dp,
                    if (highlight) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f),
                    blobShape(seed = player.name.hashCode())
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = player.name.take(1),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = if (isHost) "${player.name} 👑" else player.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

/** لابی: جمعِ بازیکن‌ها + تنظیمات میزبان */
@Composable
fun EfLobbyScreen(
    state: EsmFamilUiState,
    onToggleTopic: (String) -> Unit,
    onAddTopic: (String) -> Unit,
    onRoundSeconds: (Int) -> Unit,
    onTotalRounds: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val snapshot = state.snapshot
    var customTopic by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        StickerTitle(text = "لابی بازی", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(14.dp))

        // ---- بازیکن‌ها ----
        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text(
                    text = "بازیکن‌ها (${snapshot.players.size.toPersianDigits()} از ۸)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                snapshot.players.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                    ) {
                        row.forEach { p ->
                            EfPlayerChip(player = p, isHost = sameName(p.name, snapshot.hostName))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (state.isHost) {
            // ---- مدت راند ----
            SettingLabel("⏱ مدت هر راند")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(60, 90, 120).forEach { s ->
                    EfPillChip(
                        text = "${s.toPersianDigits()} ثانیه",
                        selected = snapshot.settings.roundSeconds == s,
                        onClick = { sound?.playButtonClick(); onRoundSeconds(s) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ---- تعداد راند ----
            SettingLabel("🔁 تعداد راند")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EfPillChip(text = "−", selected = false, onClick = {
                    sound?.playButtonClick(); onTotalRounds(snapshot.settings.totalRounds - 1)
                })
                Text(
                    text = snapshot.settings.totalRounds.toPersianDigits(),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
                EfPillChip(text = "+", selected = false, onClick = {
                    sound?.playButtonClick(); onTotalRounds(snapshot.settings.totalRounds + 1)
                })
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ---- موضوعات ----
            SettingLabel("🗂 موضوعات این بازی (${snapshot.settings.topics.size.toPersianDigits()} تا)")
            val allTopics = (DEFAULT_TOPICS + snapshot.settings.topics).distinct()
            allTopics.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    row.forEach { topic ->
                        EfPillChip(
                            text = topic,
                            selected = topic in snapshot.settings.topics,
                            onClick = { sound?.playButtonClick(); onToggleTopic(topic) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    BlobTextField(
                        value = customTopic,
                        onValueChange = { customTopic = it },
                        placeholder = "موضوع دلخواه…",
                        tilt = -0.6f,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                EfPillChip(text = "➕ اضافه", selected = customTopic.isNotBlank(), onClick = {
                    sound?.playButtonClick()
                    onAddTopic(customTopic)
                    customTopic = ""
                })
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (state.hostAddress.isNotBlank()) {
                Text(
                    text = "اتصال دستی مهمان‌ها: ${state.hostAddress}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val canStart = snapshot.players.count { it.connected } >= MIN_PLAYERS &&
                snapshot.settings.topics.size >= 2
            KButton(
                text = when {
                    snapshot.players.count { it.connected } < MIN_PLAYERS -> "منتظر حداقل ${MIN_PLAYERS.toPersianDigits()} بازیکن…"
                    snapshot.settings.topics.size < 2 -> "حداقل ۲ موضوع انتخاب کن"
                    else -> "شروع بازی!"
                },
                enabled = canStart,
                onClick = onStart,
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            BobbingEmoji(emoji = "🍿", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "منتظریم ${snapshot.hostName} بازی رو شروع کنه…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "موضوعات: ${snapshot.settings.topics.joinToString("، ")}\n" +
                    "هر راند ${snapshot.settings.roundSeconds.toPersianDigits()} ثانیه — " +
                    "${snapshot.settings.totalRounds.toPersianDigits()} راند",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        textAlign = TextAlign.Right,
    )
}

/** تراشه‌ی قرصی انتخاب‌شدنی — زبان مشترک تنظیمات لابی */
@Composable
fun EfPillChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(
                if (selected) Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.12f), accent))
                else Brush.verticalGradient(listOf(extras.glassStrong, extras.glassStrong)),
                RoundedCornerShape(50)
            )
            .border(
                1.5.dp,
                if (selected) Color.White.copy(alpha = 0.5f) else extras.glassBorder,
                RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
