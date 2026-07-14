package com.navidabbasian.kibord.games.nofoozi.ui

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
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.esmfamil.model.sameName
import com.navidabbasian.kibord.games.nofoozi.model.NF_MIN_PLAYERS
import com.navidabbasian.kibord.games.nofoozi.model.NfPlayer
import com.navidabbasian.kibord.games.nofoozi.net.NfDiscoveredGame
import com.navidabbasian.kibord.games.nofoozi.viewmodel.NofooziUiState

/** ورود: اسم خودت را بگو، بعد میزبان شو یا بپیوند */
@Composable
fun NfEntryScreen(
    state: NofooziUiState,
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
        BobbingEmoji(emoji = "🥸", fontSize = 58.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "کلمه‌ی نفوذی", rotation = -2f)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "همه یک کلمه می‌گیرن، یکی یه کلمه‌ی شبیه! نفوذی رو از توصیف‌هاش پیدا کنید — همه روی یک وای‌فای یا هات‌اسپات باشید",
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
fun NfJoinScreen(
    state: NofooziUiState,
    onJoin: (NfDiscoveredGame) -> Unit,
    onManualJoin: (String) -> Unit,
) {
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
                            Text(text = "🥸", fontSize = 26.sp)
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
private fun NfPlayerChip(player: NfPlayer, isHost: Boolean, badge: String = "") {
    val color = kiExtras.teamColors.teamColorFor(player.colorIndex)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { alpha = if (player.connected) 1f else 0.4f }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    Brush.radialGradient(listOf(lerp(color, Color.White, 0.25f), color)),
                    blobShape(seed = player.name.hashCode())
                )
                .border(2.dp, Color.White.copy(alpha = 0.4f), blobShape(seed = player.name.hashCode())),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (badge.isNotBlank()) badge else player.name.take(1),
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

/** تراشه‌ی قرصی انتخاب‌شدنی */
@Composable
private fun NfPillChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
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
            ) {
                sound?.playButtonClick()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** لابی: جمعِ بازیکن‌ها + تنظیمات میزبان */
@Composable
fun NfLobbyScreen(
    state: NofooziUiState,
    onTotalRounds: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val snapshot = state.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        StickerTitle(text = "لابی نفوذی", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(14.dp))

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
                            NfPlayerChip(player = p, isHost = sameName(p.name, snapshot.hostName))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isHost) {
            Text(
                text = "🔁 تعداد راند",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NfPillChip(text = "−", selected = false, onClick = { onTotalRounds(snapshot.totalRounds - 1) })
                Text(
                    text = snapshot.totalRounds.toPersianDigits(),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
                NfPillChip(text = "+", selected = false, onClick = { onTotalRounds(snapshot.totalRounds + 1) })
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (state.hostAddress.isNotBlank()) {
                Text(
                    text = "اتصال دستی مهمان‌ها: ${state.hostAddress}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val connected = snapshot.players.count { it.connected }
            KButton(
                text = if (connected < NF_MIN_PLAYERS) "منتظر حداقل ${NF_MIN_PLAYERS.toPersianDigits()} بازیکن…"
                else "شروع بازی!",
                enabled = connected >= NF_MIN_PLAYERS,
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${snapshot.totalRounds.toPersianDigits()} راند بازی می‌کنیم",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
    }
}

/** پخش کلمه‌ها: هر کس کلمه‌ی خودش را می‌بیند و تایید می‌کند */
@Composable
fun NfRevealScreen(
    state: NofooziUiState,
    onSeen: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val snapshot = state.snapshot
    val waitingCount = snapshot.connectedPlayers.count { !snapshot.hasSeen(it.name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "راند ${snapshot.roundIndex.toPersianDigits()} از ${snapshot.totalRounds.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))

        TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1.5f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "کلمه‌ی تو — فقط خودت ببین 🤫",
                    style = MaterialTheme.typography.labelMedium,
                    color = kiExtras.danger,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.myWord.ifBlank { "…" },
                    style = MaterialTheme.typography.displayMedium,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "شاید کلمه‌ی بقیه همین باشه، شاید هم تو نفوذی باشی و مال تو فرق کنه — هیچ‌کس نمی‌دونه!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (!state.iHaveSeen) {
            KButton(text = "دیدم، بریم! ✅", onClick = onSeen)
        } else {
            BobbingEmoji(emoji = "⏳", fontSize = 34.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "منتظر ${waitingCount.toPersianDigits()} نفر دیگه…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** بحث حضوری: هر کس یک توصیف می‌گوید؛ میزبان رای‌گیری را شروع می‌کند */
@Composable
fun NfDiscussionScreen(
    state: NofooziUiState,
    onStartVoting: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val snapshot = state.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BobbingEmoji(emoji = "🗣", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "دور صحبت!", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "به نوبت، هر کس یک توصیف کوتاه از کلمه‌ش بگه — نه خیلی رو، نه خیلی دور!\nنفوذی باید جوری وانمود کنه که تابلو نشه",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "کلمه‌ی تو:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.myWord.ifBlank { "…" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (state.isHost) {
            KButton(text = "بریم رای‌گیری! 🗳", onClick = onStartVoting)
        } else {
            Text(
                text = "هر وقت بحث تموم شد، ${snapshot.hostName} رای‌گیری رو شروع می‌کنه",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** رای‌گیری: به نظرت نفوذی کیه؟ */
@Composable
fun NfVoteScreen(
    state: NofooziUiState,
    onVote: (String) -> Unit,
) {
    val snapshot = state.snapshot
    val waitingCount = snapshot.connectedPlayers.count { snapshot.voteOf(it.name) == null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(26.dp))
        BobbingEmoji(emoji = "🗳", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "نفوذی کیه؟", rotation = 2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (state.myVote == null) "به مشکوک‌ترین نفر رای بده!"
            else "رای دادی — تا بسته‌شدن رای‌گیری می‌تونی عوضش کنی",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            snapshot.players.filter { !sameName(it.name, state.myName) }.forEach { p ->
                val color = kiExtras.teamColors.teamColorFor(p.colorIndex)
                val selected = state.myVote?.let { sameName(it, p.name) } == true
                val sound = LocalSoundManager.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(
                            if (selected) color else kiExtras.glassStrong,
                            RoundedCornerShape(22.dp)
                        )
                        .border(
                            2.dp,
                            if (selected) Color.White.copy(alpha = 0.7f) else kiExtras.glassBorder,
                            RoundedCornerShape(22.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            sound?.playButtonClick()
                            onVote(p.name)
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text(text = if (selected) "🫵" else "🥸", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = p.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Text(
            text = "منتظر رای ${waitingCount.toPersianDigits()} نفر…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
    }
}

/** نتیجه‌ی راند: نفوذی رو شد! */
@Composable
fun NfRoundResultScreen(
    state: NofooziUiState,
    onProceed: () -> Unit,
) {
    val extras = kiExtras
    val snapshot = state.snapshot
    val undercover = snapshot.player(snapshot.undercoverName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = if (snapshot.caught) "🎉" else "😎", fontSize = 60.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (snapshot.caught) "نفوذی گرفتار شد!" else "نفوذی قِسِر در رفت!",
            style = MaterialTheme.typography.displayMedium,
            color = if (snapshot.caught) extras.success else extras.danger,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "نفوذی این راند: ${snapshot.undercoverName}" +
                if (state.iWasUndercover) " (خودت! 🥸)" else "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = kiExtras.teamColors.teamColorFor(undercover?.colorIndex ?: 0),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (snapshot.accusedName.isBlank()) "رای‌ها پخش شد و کسی اکثریت نیاورد!"
            else "بیشترین رای: ${snapshot.accusedName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "کلمه‌ی جمع: ${
                        snapshot.players.firstOrNull { !sameName(it.name, snapshot.undercoverName) }
                            ?.let { snapshot.wordOf(it.name) } ?: "—"
                    }   •   کلمه‌ی نفوذی: ${snapshot.wordOf(snapshot.undercoverName)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // راند آخر: مجموع‌ها تا «کی برد؟» مخفی می‌مانند تا هیجان بماند
        val suspense = snapshot.roundIndex >= snapshot.totalRounds
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                (if (suspense) snapshot.players else snapshot.players.sortedByDescending { it.totalScore })
                    .forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (if (sameName(p.name, snapshot.undercoverName)) "🥸 " else "") + p.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                            )
                            if (suspense) {
                                Box(modifier = Modifier.breathing(intensity = 0.06f, periodMs = 1400, phase = p.colorIndex * 1.1f)) {
                                    Text(
                                        text = "؟",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = kiExtras.gold,
                                    )
                                }
                            } else {
                                Text(
                                    text = p.totalScore.toPersianDigits(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                if (suspense) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🤫 مجموع‌ها مخفیه… بزن ببین کی برد!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = kiExtras.gold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        if (state.isHost) {
            KButton(
                text = if (snapshot.roundIndex >= snapshot.totalRounds) "کی برد؟ 🏆" else "راند بعد!",
                onClick = onProceed,
            )
        } else {
            Text(
                text = "${snapshot.hostName} راند بعد رو شروع می‌کنه…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
    }
}

/** اعلام برنده */
@Composable
fun NfWinnerScreen(
    state: NofooziUiState,
    winners: List<NfPlayer>,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val snapshot = state.snapshot

    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            Text(text = "🏆", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = winners.joinToString(" و ") { it.name },
                style = MaterialTheme.typography.displayMedium,
                color = kiExtras.teamColors.teamColorFor(winners.firstOrNull()?.colorIndex ?: 0),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    snapshot.players
                        .sortedByDescending { it.totalScore }
                        .forEachIndexed { rank, p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (p in winners) "🥇" else (rank + 1).toPersianDigits(),
                                        fontSize = 17.sp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = p.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                                    )
                                }
                                Text(
                                    text = p.totalScore.toPersianDigits(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            ShareWinButton(
                gameId = "nofoozi",
                gameTitle = "کلمه‌ی نفوذی",
                gameEmoji = "🥸",
                winnerText = winners.joinToString(" و ") { it.name },
                scoreLines = snapshot.players.sortedByDescending { it.totalScore }
                    .map { it.name to it.totalScore.toPersianDigits() },
                winnerNames = winners.map { it.name },
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (state.isHost) {
                KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
                Spacer(modifier = Modifier.height(10.dp))
            }
            KButton(text = "بازگشت به خانه", onClick = onExit, style = KButtonStyle.Glass)
        }
    }
}
