package com.navidabbasian.kibord.games.mafia.ui

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
import com.navidabbasian.kibord.games.mafia.model.MF_MIN_PLAYERS
import com.navidabbasian.kibord.games.mafia.model.MfPlayer
import com.navidabbasian.kibord.games.mafia.model.MfRole
import com.navidabbasian.kibord.games.mafia.model.MfWinner
import com.navidabbasian.kibord.games.mafia.model.mafiaCountFor
import com.navidabbasian.kibord.games.mafia.net.MfDiscoveredGame
import com.navidabbasian.kibord.games.mafia.viewmodel.MafiaUiState

/** ورود: اسم خودت را بگو، بعد میزبان شو یا بپیوند */
@Composable
fun MfEntryScreen(
    state: MafiaUiState,
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
        BobbingEmoji(emoji = "🌙", fontSize = 58.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "شب مافیا", rotation = -2f)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "بدون گرداننده! نقش‌ها و شب‌ها روی گوشی خود بازیکن‌هاست — همه روی یک وای‌فای یا هات‌اسپات باشید",
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
        }
        state.connectError?.let {
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger)
        }
    }
}

/** پیوستن: بازی‌های پیداشده در شبکه + اتصال دستی با آدرس */
@Composable
fun MfJoinScreen(
    state: MafiaUiState,
    onJoin: (MfDiscoveredGame) -> Unit,
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
                            Text(text = "🌙", fontSize = 26.sp)
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

/** تراشه‌ی بازیکن: آواتار سنگریزه‌ای رنگی + اسم — مرده‌ها کم‌رنگ با 💀 */
@Composable
private fun MfPlayerChip(player: MfPlayer, isHost: Boolean) {
    val color = kiExtras.teamColors.teamColorFor(player.colorIndex)
    val faded = !player.connected || !player.alive
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { alpha = if (faded) 0.4f else 1f }
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
                text = if (!player.alive) "💀" else player.name.take(1),
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

/** فهرست هدف‌ها برای اقدام/رای — گزینه‌ی من پررنگ می‌شود */
@Composable
private fun MfTargetList(
    players: List<MfPlayer>,
    myChoice: String?,
    emoji: String,
    onPick: (String) -> Unit,
) {
    val sound = LocalSoundManager.current
    Column {
        players.forEach { p ->
            val color = kiExtras.teamColors.teamColorFor(p.colorIndex)
            val selected = myChoice?.let { sameName(it, p.name) } == true
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
                        onPick(p.name)
                    }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(text = if (selected) "🫵" else emoji, fontSize = 22.sp)
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
}

/** لابی: جمعِ بازیکن‌ها + شروع میزبان */
@Composable
fun MfLobbyScreen(
    state: MafiaUiState,
    onStart: () -> Unit,
) {
    val snapshot = state.snapshot
    val connected = snapshot.players.count { it.connected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        StickerTitle(text = "لابی مافیا", rotation = -2f, fontSize = 26.sp)
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
                            MfPlayerChip(player = p, isHost = sameName(p.name, snapshot.hostName))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "نقش‌ها: ${mafiaCountFor(connected.coerceAtLeast(MF_MIN_PLAYERS)).toPersianDigits()} مافیا 😈 — ۱ دکتر 🩺 — ۱ کارآگاه 🕵️ — بقیه شهروند",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (state.isHost) {
            if (state.hostAddress.isNotBlank()) {
                Text(
                    text = "اتصال دستی مهمان‌ها: ${state.hostAddress}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            KButton(
                text = if (connected < MF_MIN_PLAYERS) "منتظر حداقل ${MF_MIN_PLAYERS.toPersianDigits()} بازیکن…"
                else "شروع بازی!",
                enabled = connected >= MF_MIN_PLAYERS,
                onClick = onStart,
            )
        } else {
            BobbingEmoji(emoji = "🍿", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "منتظریم ${snapshot.hostName} بازی رو شروع کنه…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
    }
}

/** پخش نقش‌ها: هر کس نقش خودش را می‌بیند و تایید می‌کند */
@Composable
fun MfRoleRevealScreen(
    state: MafiaUiState,
    onSeen: () -> Unit,
) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    val snapshot = state.snapshot
    val role = state.myRole
    val waitingCount = snapshot.players.count { it.alive && it.connected && !snapshot.hasSeen(it.name) }
    val roleColor = when (role) {
        MfRole.MAFIA -> extras.danger
        MfRole.DOCTOR -> extras.success
        MfRole.DETECTIVE -> extras.gold
        else -> accent
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TicketCard(modifier = Modifier.fillMaxWidth(), accent = roleColor, tilt = -1.5f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "نقش تو — فقط خودت ببین 🤫",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.danger,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = role?.emoji ?: "❓", fontSize = 54.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = role?.title ?: "…",
                    style = MaterialTheme.typography.displayMedium,
                    color = roleColor,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (role == MfRole.MAFIA) {
                    val partners = snapshot.players.filter {
                        snapshot.roleOf(it.name) == MfRole.MAFIA && !sameName(it.name, state.myName)
                    }
                    if (partners.isNotEmpty()) {
                        Text(
                            text = "همدستت: ${partners.joinToString("، ") { it.name }} 🤝",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = extras.danger,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Text(
                    text = when (role) {
                        MfRole.MAFIA -> "شب‌ها یکی رو می‌زنی — روزها عادی رفتار کن که لو نری!"
                        MfRole.DOCTOR -> "هر شب یک نفر رو نجات می‌دی — حتی خودت رو!"
                        MfRole.DETECTIVE -> "هر شب یک نفر رو استعلام می‌کنی: مافیاست یا نه"
                        else -> "روزها با دقت بحث کن و مافیا رو پیدا کن!"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (state.iAmAlive && !state.iHaveSeen) {
            KButton(text = "دیدم، قایمش کن ✅", onClick = onSeen)
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

/** شب: هر نقش اقدام خودش را روی گوشی خودش انجام می‌دهد */
@Composable
fun MfNightScreen(
    state: MafiaUiState,
    onAction: (String) -> Unit,
) {
    val extras = kiExtras
    val snapshot = state.snapshot
    val role = state.myRole
    val alive = snapshot.alivePlayers

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "🌙", fontSize = 46.sp)
        Spacer(modifier = Modifier.height(6.dp))
        StickerTitle(text = "شب ${snapshot.nightIndex.toPersianDigits()}", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "شهر خوابه… سرها پایین، فقط به گوشی خودت نگاه کن! 🤫",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            !state.iAmAlive -> {
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "💀", fontSize = 54.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "تو از بازی رفتی — تماشا کن و هیچی لو نده!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            role == MfRole.MAFIA -> {
                Text(
                    text = "😈 امشب کی رو بزنیم؟",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = extras.danger,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    MfTargetList(
                        players = alive.filter { snapshot.roleOf(it.name) != MfRole.MAFIA },
                        myChoice = state.myMafiaVote,
                        emoji = "🔪",
                        onPick = onAction,
                    )
                }
            }

            role == MfRole.DOCTOR -> {
                Text(
                    text = "🩺 امشب کی رو نجات بدیم؟",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = extras.success,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    MfTargetList(
                        players = alive,
                        myChoice = snapshot.doctorSave.ifBlank { null },
                        emoji = "💊",
                        onPick = onAction,
                    )
                }
            }

            role == MfRole.DETECTIVE -> {
                Text(
                    text = "🕵️ امشب کی رو استعلام کنیم؟",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = extras.gold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (snapshot.detectiveCheck.isBlank()) {
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        MfTargetList(
                            players = alive.filter { !sameName(it.name, state.myName) },
                            myChoice = null,
                            emoji = "🔍",
                            onPick = onAction,
                        )
                    }
                } else {
                    val isMafia = snapshot.roleOf(snapshot.detectiveCheck) == MfRole.MAFIA
                    GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = if (isMafia) "😈" else "😇", fontSize = 44.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isMafia) "${snapshot.detectiveCheck} مافیاست!"
                                else "${snapshot.detectiveCheck} مافیا نیست",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isMafia) extras.danger else extras.success,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            else -> {
                Spacer(modifier = Modifier.height(20.dp))
                BobbingEmoji(emoji = "😴", fontSize = 54.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "تو خوابی… چشم‌هات رو ببند تا صبح بشه",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (state.iAmAlive && role != MfRole.CITIZEN && state.iActedTonight) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "انجام شد — منتظر بقیه…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
    }
}

/** صبح: اعلام نتیجه‌ی شب + بحث حضوری */
@Composable
fun MfDayAnnounceScreen(
    state: MafiaUiState,
    onStartVote: () -> Unit,
) {
    val extras = kiExtras
    val snapshot = state.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BobbingEmoji(emoji = "🌅", fontSize = 50.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "صبح شد!", rotation = 2f, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(16.dp))

        TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1.2f) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (snapshot.lastSaved) {
                    Text(text = "🩺✨", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "دیشب دکتر یک نفر رو از مرگ نجات داد!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = extras.success,
                        textAlign = TextAlign.Center,
                    )
                } else if (snapshot.lastKilled.isNotBlank()) {
                    Text(text = "🕯", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${snapshot.lastKilled} دیشب کشته شد!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = extras.danger,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "نقشش: ${snapshot.roleOf(snapshot.lastKilled)?.let { "${it.title} ${it.emoji}" } ?: "؟"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "حالا بحث کنید: کی مشکوکه؟ 🤔",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (state.isHost) {
            KButton(text = "بریم رای‌گیری! 🗳", onClick = onStartVote)
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

/** رای روز: اعدام مشکوک‌ترین */
@Composable
fun MfDayVoteScreen(
    state: MafiaUiState,
    onVote: (String) -> Unit,
) {
    val snapshot = state.snapshot
    val waitingCount = snapshot.players.count { it.alive && it.connected && snapshot.dayVoteOf(it.name) == null }

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
        StickerTitle(text = "کی بره بیرون؟", rotation = 2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (!state.iAmAlive) "تو تماشاچی هستی 💀"
            else if (state.myDayVote == null) "به مشکوک‌ترین نفر رای بده!"
            else "رای دادی — تا بسته‌شدن رای‌گیری می‌تونی عوضش کنی",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (state.iAmAlive) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MfTargetList(
                    players = snapshot.alivePlayers.filter { !sameName(it.name, state.myName) },
                    myChoice = state.myDayVote,
                    emoji = "🤨",
                    onPick = onVote,
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(
            text = "منتظر رای ${waitingCount.toPersianDigits()} نفر…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
    }
}

/** نتیجه‌ی رای روز */
@Composable
fun MfDayResultScreen(
    state: MafiaUiState,
    onProceed: () -> Unit,
) {
    val extras = kiExtras
    val snapshot = state.snapshot
    val lynchedRole = snapshot.lastLynched.takeIf { it.isNotBlank() }?.let { snapshot.roleOf(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (snapshot.lastLynched.isBlank()) {
            Text(text = "🤷", fontSize = 60.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "رای‌ها پخش شد — امروز کسی بیرون نرفت!",
                style = MaterialTheme.typography.displayMedium,
                fontSize = 30.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(text = if (lynchedRole == MfRole.MAFIA) "🎉" else "😱", fontSize = 60.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${snapshot.lastLynched} از بازی رفت!",
                style = MaterialTheme.typography.displayMedium,
                fontSize = 30.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "نقشش: ${lynchedRole?.let { "${it.title} ${it.emoji}" } ?: "؟"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (lynchedRole == MfRole.MAFIA) extras.success else extras.danger,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (state.isHost) {
            KButton(text = "ادامه", onClick = onProceed)
        } else {
            Text(
                text = "${snapshot.hostName} ادامه می‌ده…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** پایان بازی: برنده + رو شدن همه‌ی نقش‌ها */
@Composable
fun MfGameOverScreen(
    state: MafiaUiState,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val extras = kiExtras
    val snapshot = state.snapshot
    val mafiaWon = snapshot.winner == MfWinner.MAFIA

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
            Text(text = if (mafiaWon) "😈" else "🏆", fontSize = 76.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (mafiaWon) "مافیا شهر رو گرفت!" else "شهروندها بردن!",
                style = MaterialTheme.typography.displayMedium,
                color = if (mafiaWon) extras.danger else extras.success,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "نقش‌ها رو شدن:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    snapshot.players.forEach { p ->
                        val role = snapshot.roleOf(p.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (if (!p.alive) "💀 " else "") + p.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                            )
                            Text(
                                text = role?.let { "${it.title} ${it.emoji}" } ?: "—",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (role == MfRole.MAFIA) extras.danger else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            ShareWinButton(
                gameId = "mafia",
                gameTitle = "شب مافیا",
                gameEmoji = "🌙",
                winnerText = if (mafiaWon) "مافیا" else "شهروندها",
                scoreLines = snapshot.players.map { p ->
                    p.name to (snapshot.roleOf(p.name)?.title ?: "—")
                },
                winnerNames = snapshot.players
                    .filter { (snapshot.roleOf(it.name) == MfRole.MAFIA) == mafiaWon }
                    .map { it.name },
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
