package com.navidabbasian.kibord.core.ui.net

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.net.NetUiState
import com.navidabbasian.kibord.core.net.lan.LanDiscoveredGame
import com.navidabbasian.kibord.core.net.online.OnlineRooms
import com.navidabbasian.kibord.core.net.online.StoredOnlineRoom
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.OnlineIdentityField
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits

/**
 * صفحه‌های مشترکِ بازیِ چندگوشی — همان چیزی که تخته‌نرد دارد، برای همه‌ی
 * بازی‌ها: ورود (اسم + میزبان/بپیوند + سوییچ اینترنتی)، پیوستن (کشف خودکار
 * یا کد اتاق)، لابی (صندلی‌ها) و پرده‌های قطعی. هر بازی فقط ایموجی و
 * تیتر و لیست صندلی‌های خودش را می‌دهد.
 */

/** یک صندلی در لابی */
data class LobbySeat(
    val name: String,
    val kind: LobbySeatKind,
    /** برای مهمان‌ها: وصل است؟ */
    val connected: Boolean = true,
    /** برچسب کوچک کنار اسم — مثلاً تیم یا رنگ مهره */
    val tag: String? = null,
)

enum class LobbySeatKind { HOST, GUEST, BOT, EMPTY }

/** انتخاب راه بازی: با ربات روی همین گوشی، یا با دوستان روی چند گوشی */
@Composable
fun NetModeBubbles(
    localMain: String,
    localSub: String,
    localEmoji: String,
    onLocal: () -> Unit,
    onNetwork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
    ) {
        ChoiceBubble(
            main = localMain,
            sub = localSub,
            emoji = localEmoji,
            size = 148.dp,
            mainFontSize = 22.sp,
            tilt = -3f,
            onClick = onLocal,
        )
        ChoiceBubble(
            main = "چند گوشی",
            sub = "هات‌اسپات یا\nاینترنتی با دوستان",
            emoji = "📶",
            size = 148.dp,
            mainFontSize = 22.sp,
            tilt = 3f,
            phase = 1.5f,
            modifier = Modifier.offset(y = 26.dp),
            onClick = onNetwork,
        )
    }
}

/** کارت پهنِ «چند گوشی» زیر گزینه‌های محلیِ صفحه‌ی تنظیمات هر بازی */
@Composable
fun NetModeCard(onClick: () -> Unit, modifier: Modifier = Modifier, sub: String = "با دوستات روی هات‌اسپات یا اینترنت — هر کس با گوشی خودش") {
    GlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 22.dp, strong = true, onClick = onClick, tilt = -0.6f) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "📶", fontSize = 26.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "چند گوشی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = "🌐", fontSize = 20.sp)
        }
    }
}

/** ورود شبکه‌ای: اسم خودت را بگو، بعد میزبان شو یا بپیوند */
@Composable
fun NetEntryScreen(
    net: NetUiState,
    emoji: String,
    title: String,
    onNameChanged: (String) -> Unit,
    onToggleOnline: (Boolean) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onResume: () -> Unit,
    onDiscardResume: () -> Unit,
    subtitle: String = "هر کدوم با گوشی خودتون — یکی میزبان می‌شه و بقیه بهش می‌پیوندن",
    /** تنظیمات خاص بازی (تعداد بازیکن، هدف…) که فقط میزبان انتخاب می‌کند */
    hostOptions: (@Composable () -> Unit)? = null,
) {
    var showNameError by remember { mutableStateOf(false) }
    val guardName: (() -> Unit) -> Unit = { action ->
        if (net.myName.isBlank()) showNameError = true else action()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        BobbingEmoji(emoji = emoji, fontSize = 58.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = title, rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        net.resumable?.let { stored ->
            NetResumeCard(stored = stored, busy = net.connecting, onResume = onResume, onDiscard = onDiscardResume)
            Spacer(modifier = Modifier.height(18.dp))
        }

        if (net.online) {
            OnlineIdentityField(username = net.myName)
        } else BlobTextField(
            value = net.myName,
            onValueChange = {
                onNameChanged(it)
                if (it.isNotBlank()) showNameError = false
            },
            placeholder = "اسمت چیه؟ (شناسه‌ی تو در بازی)",
            badge = "👤",
            tilt = -1f,
        )

        hostOptions?.let {
            Spacer(modifier = Modifier.height(20.dp))
            it()
        }
        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
        ) {
            ChoiceBubble(
                main = "میزبان شو",
                sub = "میز رو بچین و\nدوستات رو دعوت کن",
                emoji = "👑",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = -3f,
                onClick = { guardName(onHost) },
            )
            ChoiceBubble(
                main = "بپیوند",
                sub = "به میزِ ساخته‌شده\nوصل شو",
                emoji = "🚪",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = 3f,
                phase = 1.5f,
                modifier = Modifier.offset(y = 26.dp),
                onClick = { guardName(onJoin) },
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
        NetOnlineSwitch(online = net.online, onToggle = onToggleOnline)

        if (showNameError && net.myName.isBlank()) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.breathing(intensity = 0.04f, periodMs = 1100)) {
                StickerTitle(text = "✋ اول اسمت رو بنویس!", accent = kiExtras.danger, rotation = -2f, fontSize = 22.sp)
            }
        }
        net.connectError?.let {
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** سوییچ راهِ بازی: وای‌فای/هات‌اسپات محلی یا اینترنت با کد اتاق */
@Composable
private fun NetOnlineSwitch(online: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(kiExtras.glassStrong, RoundedCornerShape(18.dp))
            .border(1.5.dp, if (online) LocalGameAccent.current else kiExtras.glassBorder, RoundedCornerShape(18.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle(!online) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = if (online) "🌐" else "📶", fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "بازی اینترنتی",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (online) "با کد اتاق — دوستات هر جای دنیا باشن" else "الان: وای‌فای یا هات‌اسپات مشترک",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = if (online) "✅" else "⬜", fontSize = 20.sp)
    }
}

/** کارت «بازی اینترنتی نیمه‌کاره داری» */
@Composable
fun NetResumeCard(
    stored: StoredOnlineRoom,
    busy: Boolean,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    error: String? = null,
) {
    TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "🔁 بازی اینترنتی نیمه‌کاره داری",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "اتاق ${stored.code} — ${if (stored.isHost) "میزبان بودی" else "مهمان بودی"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            error?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger, textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KButton(text = if (busy) "یه لحظه…" else "ادامه بده", enabled = !busy, onClick = onResume, modifier = Modifier.weight(1f))
                KButton(text = "بی‌خیال", style = KButtonStyle.Glass, enabled = !busy, onClick = onDiscard, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** پیوستن: بازی‌های پیداشده در شبکه + اتصال دستی، یا کد اتاق اینترنتی */
@Composable
fun NetJoinScreen(
    net: NetUiState,
    emoji: String,
    onJoin: (LanDiscoveredGame) -> Unit,
    onManualJoin: (String) -> Unit,
    onJoinOnline: (String) -> Unit,
    hostLabel: (hostName: String) -> String = { "میزِ $it" },
) {
    var roomCode by rememberSaveable { mutableStateOf("") }

    if (net.online) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            BobbingEmoji(emoji = "🌐", fontSize = 52.sp)
            Spacer(modifier = Modifier.height(10.dp))
            StickerTitle(text = "کد اتاق رو بزن", rotation = 2f, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "میزبان بعد از ساختن بازی یه کد ${OnlineRooms.CODE_LENGTH.toPersianDigits()} حرفی می‌بینه — ازش بگیر و همین‌جا بنویس",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            BlobTextField(
                value = roomCode,
                onValueChange = { roomCode = it.uppercase().take(OnlineRooms.CODE_LENGTH) },
                placeholder = "مثلاً H7KQ2M",
                badge = "🔑",
                tilt = -1f,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Spacer(modifier = Modifier.height(14.dp))
            KButton(
                text = if (net.connecting) "در حال اتصال…" else "برو تو اتاق!",
                enabled = !net.connecting && roomCode.length == OnlineRooms.CODE_LENGTH,
                onClick = { onJoinOnline(roomCode) },
            )
            net.connectError?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger, textAlign = TextAlign.Center)
            }
        }
        return
    }

    var manualAddress by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "📡", fontSize = 46.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "به کدوم میز بپیوندیم؟", rotation = 2f, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (net.discovered.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1800)) {
                        Text(text = "🔎", fontSize = 30.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "دنبال بازی می‌گردم…\nمیزبان باید بازی رو ساخته باشه و همه روی یک وای‌فای یا هات‌اسپات باشید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(count = net.discovered.size, key = { net.discovered[it].hostName }) { i ->
                    val game = net.discovered[i]
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
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = emoji, fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hostLabel(game.hostName),
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
            text = if (net.connecting) "در حال اتصال…" else "اتصال دستی",
            enabled = !net.connecting && manualAddress.isNotBlank(),
            onClick = { onManualJoin(manualAddress.trim()) },
        )
        net.connectError?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
    }
}

/**
 * لابی: صندلی‌ها، کد اتاق یا آدرس، و دکمه‌ی شروع برای میزبان.
 * صندلی‌های خالی موقع شروع با ربات پر می‌شوند — لابی همین را می‌گوید.
 */
@Composable
fun NetLobbyScreen(
    net: NetUiState,
    emoji: String,
    title: String,
    seats: List<LobbySeat>,
    isHost: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    /** توضیح زیر لیست — مثلاً «حداقل یک دوست لازمه» */
    hint: String? = null,
    /** تنظیمات میزبان که مهمان‌ها هم باید ببینند (مثلاً «تا ۵ برد») */
    summary: String? = null,
    /** بازی دو نفره: با پیوستن حریف خودکار شروع می‌شود، دکمه‌ی شروع ندارد */
    autoStart: Boolean = false,
    /** میزبان روی یک صندلی بزند (مثلاً ربات/خالی کردنش) */
    onSeatTap: ((Int) -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        BobbingEmoji(emoji = emoji, fontSize = 52.sp)
        Spacer(modifier = Modifier.height(12.dp))
        StickerTitle(text = "لابی $title", rotation = -2f, fontSize = 24.sp)
        summary?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(18.dp))

        if (net.roomCode.isNotBlank()) {
            Text(
                text = "این کد رو به دوستات بگو:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = net.roomCode,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                color = LocalGameAccent.current,
                modifier = Modifier
                    .background(kiExtras.glassStrong, RoundedCornerShape(14.dp))
                    .border(1.5.dp, LocalGameAccent.current.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
        } else if (net.hostAddress.isNotBlank()) {
            Text(
                text = "اتصال دستی دوستات: ${net.hostAddress}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                seats.forEachIndexed { i, seat ->
                    LobbySeatRow(index = i, seat = seat, onTap = onSeatTap?.takeIf { isHost && seat.kind != LobbySeatKind.HOST && seat.kind != LobbySeatKind.GUEST }?.let { f -> { f(i) } })
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (isHost && autoStart) {
            Box(modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1800)) {
                Text(text = "⏳", fontSize = 30.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (net.roomCode.isNotBlank()) {
                    "منتظر حریفیم…\nتا با کد وارد شد، بازی خودش شروع می‌شه"
                } else {
                    "منتظر حریفیم…\nروی یک وای‌فای یا هات‌اسپات باشید تا پیدات کنه"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else if (isHost) {
            KButton(text = "شروع بازی 🎮", enabled = canStart, onClick = onStart)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint ?: if (net.roomCode.isNotBlank()) {
                    "دوستات با کد وارد می‌شن؛ صندلی‌های خالی موقع شروع با ربات پر می‌شه"
                } else {
                    "همه روی یک وای‌فای یا هات‌اسپات باشید؛ صندلی‌های خالی موقع شروع با ربات پر می‌شه"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Box(modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1800)) {
                Text(text = "⏳", fontSize = 30.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "منتظر میزبانیم تا بازی رو شروع کنه…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun LobbySeatRow(index: Int, seat: LobbySeat, onTap: (() -> Unit)? = null) {
    val (icon, label, dim) = when (seat.kind) {
        LobbySeatKind.HOST -> Triple("👑", seat.name, false)
        LobbySeatKind.GUEST -> Triple(if (seat.connected) "🟢" else "🔴", seat.name, !seat.connected)
        LobbySeatKind.BOT -> Triple("🤖", seat.name.ifBlank { "ربات" }, false)
        LobbySeatKind.EMPTY -> Triple("💺", if (onTap != null) "خالی — منتظر دوست (لمس: ربات)" else "خالی — منتظر دوست", true)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(kiExtras.glassStrong, RoundedCornerShape(14.dp))
            .border(1.dp, kiExtras.glassBorder, RoundedCornerShape(14.dp))
            .then(
                if (onTap != null) {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() }
                } else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "${(index + 1).toPersianDigits()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (dim) FontWeight.Normal else FontWeight.Bold,
            color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        seat.tag?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = LocalGameAccent.current)
        }
        if (seat.kind == LobbySeatKind.GUEST && !seat.connected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "قطع شده", style = MaterialTheme.typography.labelSmall, color = kiExtras.danger)
        }
    }
}

/** بنر کوچکِ «میزبان لحظه‌ای قطع شده» — سمت مهمان اینترنتی، بازی را نمی‌بندد */
@Composable
fun NetHostAwayBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(kiExtras.glassStrong, RoundedCornerShape(14.dp))
            .border(1.dp, kiExtras.glassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.breathing(intensity = 0.06f, periodMs = 1400)) {
            Text(text = "⏳", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "میزبان لحظه‌ای قطع شده — منتظر برگشتش…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * پرده‌های وضعیت اتصال روی بازی: بنر غیبت میزبان و پرده‌ی «ارتباط قطع شد».
 * داخل [com.navidabbasian.kibord.core.ui.components.KiBackground] بعد از محتوای صفحه صدا زده می‌شود.
 */
@Composable
fun NetConnectionOverlays(
    net: NetUiState,
    showAwayBanner: Boolean,
    onReconnect: () -> Unit,
    onLeave: () -> Unit,
) {
    if (net.hostAway && !net.lostConnection && showAwayBanner) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            NetHostAwayBanner()
        }
    }

    if (net.lostConnection) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            contentAlignment = Alignment.Center,
        ) {
            TicketCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                tilt = -1.5f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BobbingEmoji(emoji = "📴", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ارتباط با میزبان قطع شد!",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (net.online) {
                            "اینترنت رو چک کن؛ اتاق هنوز هست — می‌تونی با همون اسم دوباره وصل شی"
                        } else {
                            "وای‌فای رو چک کنید؛ اگر میزبان برگشت، دوباره با همون اسم بپیوندید"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    net.connectError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger, textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (net.online && net.roomCode.isNotBlank()) {
                        KButton(
                            text = if (net.reconnecting) "یه لحظه…" else "دوباره وصل شو 🔁",
                            enabled = !net.reconnecting,
                            onClick = onReconnect,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        KButton(text = "ترک بازی", style = KButtonStyle.Glass, onClick = onLeave)
                    } else {
                        KButton(text = "باشه", onClick = onLeave)
                    }
                }
            }
        }
    }
}
