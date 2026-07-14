package com.navidabbasian.kibord.games.gandegoo.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.gandegoo.model.GandeGooUiState
import com.navidabbasian.kibord.games.gandegoo.model.GgOutcome
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.blobShape
import androidx.compose.foundation.border

/** ثبت نتیجه‌ی گنده‌گویی حضوری: تیم برنده‌ی مزایده و عدد ادعا */
@Composable
fun GgBidScreen(
    state: GandeGooUiState,
    onClaimingTeamChanged: (Int) -> Unit,
    onClaimChanged: (Int) -> Unit,
    onSwapQuestion: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current
    val teamColors = kiExtras.teamColors
    val category = state.selectedCategory
    val question = state.selectedQuestion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1.5f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${category?.emoji ?: ""} ${category?.name ?: ""}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(question?.points ?: 0).toPersianDigits()} امتیازی",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = question?.text ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- تعویض سوال: تراشه‌ی سنگریزه‌ای با سهمیه‌ی محدود ----
        val canSwap = state.swapsLeft > 0
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = -1.5f
                    alpha = if (canSwap) 1f else 0.45f
                }
                .background(kiExtras.glassStrong, blobShape(seed = 14))
                .border(1.5.dp, accent.copy(alpha = 0.7f), blobShape(seed = 14))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canSwap,
                    onClick = onSwapQuestion
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(text = "🔄", fontSize = 15.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (canSwap) "تعویض سوال — ${state.swapsLeft.toPersianDigits()} بار مونده"
                else "تعویض‌ها تموم شد",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "گنده‌گویی کنید! 🎭",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "هر تیم ادعاشو بلند بگه؛ بزرگ‌ترین ادعا بازی می‌کنه.\nتیم برنده‌ی مزایده و عددش رو اینجا ثبت کن:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        // ---- انتخاب تیم ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(state.teamCount) { i ->
                val color = teamColors.getOrElse(i) { teamColors[0] }
                val selected = i == state.claimingTeam
                Text(
                    text = state.teamDisplayName(i),
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .background(
                            if (selected) color else kiExtras.glass,
                            RoundedCornerShape(50)
                        )
                        .clickable {
                            sound?.playButtonClick()
                            onClaimingTeamChanged(i)
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- عدد ادعا ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { sound?.playButtonClick(); onClaimChanged(state.claim + 1) }) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(kiExtras.glassStrong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "بیشتر", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.claim.toPersianDigits(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = accent
                )
                Text(
                    text = "مورد",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            IconButton(
                onClick = { sound?.playButtonClick(); onClaimChanged(state.claim - 1) },
                enabled = state.claim > 1
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(kiExtras.glassStrong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "کمتر", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            KButton(text = "شروع — ۳۰ ثانیه!", onClick = onStart)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به جدول", onClick = onCancel, style = KButtonStyle.Glass)
        }
    }
}

/** صفحه‌ی اجرای دست: تایمر ۳۰ ثانیه + دکمه‌ی شمارنده‌ی بزرگ */
@Composable
fun GgPlayScreen(
    state: GandeGooUiState,
    onCount: () -> Unit,
    onUndo: () -> Unit,
    onStartVideoCheck: () -> Unit,
    onEndVideoCheck: () -> Unit,
) {
    val extras = kiExtras
    val teamColors = extras.teamColors
    val teamColor = teamColors.getOrElse(state.claimingTeam) { teamColors[0] }
    val seconds = (state.timeLeftMillis / 1000).toInt()
    val urgent = seconds <= 5

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val progress by animateFloatAsState(
        targetValue = state.timeLeftMillis.toFloat() / GandeGooUiState.TURN_MILLIS,
        animationSpec = tween(120),
        label = "gg_progress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ---- تایمر دایره‌ای ----
        Box(
            modifier = Modifier
                .size(110.dp)
                .then(if (urgent) Modifier.breathing(intensity = 0.06f, periodMs = 600) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(110.dp)) {
                drawArc(
                    color = extras.glassStrong,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = if (urgent) extras.danger else extras.gold,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = seconds.toPersianDigits(),
                fontSize = if (urgent) 40.sp else 34.sp,
                fontWeight = FontWeight.Black,
                color = if (urgent) extras.danger else MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = state.teamDisplayName(state.claimingTeam),
            style = MaterialTheme.typography.titleLarge,
            color = teamColor
        )
        Text(
            text = state.selectedQuestion?.text ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ---- شمارش ----
        Text(
            text = "${state.counted.toPersianDigits()} از ${state.claim.toPersianDigits()}",
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.weight(1f))

        // ---- دکمه‌ی بزرگ شمارنده ----
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.9f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "count_scale"
        )
        Box(
            modifier = Modifier
                .size(190.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(
                    Brush.radialGradient(listOf(teamColor, lerp(teamColor, Color.Black, 0.3f))),
                    CircleShape
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onCount
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "+۱", fontSize = 52.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    text = "گفت؟ بزن!",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUndo, enabled = state.counted > 0) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(extras.glassStrong, blobShape(seed = 4)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "اصلاح شمارش",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "اشتباه شمردی؟ کم کن",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(18.dp))
            // ---- ویدیو چک: توقف بازی برای داوری ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .graphicsLayer { rotationZ = 1.5f }
                    .background(extras.glassStrong, blobShape(seed = 9))
                    .border(1.5.dp, extras.warning.copy(alpha = 0.7f), blobShape(seed = 9))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStartVideoCheck
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(text = "📹", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ویدیو چک",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // ---- روکش ویدیو چک: زمان یخ زده تا داورها جواب را بررسی کنند ----
    if (state.isVideoCheck) {
        GgVideoCheckOverlay(
            state = state,
            onBackToGame = onEndVideoCheck
        )
    }
    }
}

/** روکش تمام‌صفحه‌ی ویدیو چک — بازی و تایمر متوقف است */
@Composable
private fun GgVideoCheckOverlay(
    state: GandeGooUiState,
    onBackToGame: () -> Unit,
) {
    val extras = kiExtras
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* بلعیدن لمس‌ها — شمارش پشت روکش نخورد */ },
        contentAlignment = Alignment.Center
    ) {
        TicketCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            accent = extras.warning,
            tilt = -1.5f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BobbingEmoji(emoji = "📹", fontSize = 46.sp)
                Spacer(modifier = Modifier.height(8.dp))
                StickerTitle(text = "ویدیو چک", accent = extras.warning, rotation = -2f, fontSize = 26.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "بازی متوقف شد ⏸\nصحت جواب‌ها رو با خیال راحت بررسی کنید",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "⏱ ${(state.timeLeftMillis / 1000).toInt().toPersianDigits()} ثانیه مانده — " +
                        "شمارش: ${state.counted.toPersianDigits()} از ${state.claim.toPersianDigits()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(20.dp))
                KButton(
                    text = "برگشت به بازی ▶️",
                    onClick = onBackToGame,
                    accent = extras.warning
                )
            }
        }
    }
}

/** بازبینی نهایی: ۱۵ ثانیه فرصت اصلاح شمارش با دکمه‌های کم/زیاد — یا تایید فوری */
@Composable
fun GgReviewScreen(
    state: GandeGooUiState,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onConfirm: () -> Unit,
) {
    val extras = kiExtras
    val teamColors = extras.teamColors
    val teamColor = teamColors.getOrElse(state.claimingTeam) { teamColors[0] }
    val seconds = (state.reviewTimeLeftMillis / 1000).toInt()

    val reviewProgress by animateFloatAsState(
        targetValue = state.reviewTimeLeftMillis.toFloat() / GandeGooUiState.REVIEW_MILLIS,
        animationSpec = tween(120),
        label = "review_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "🧐", fontSize = 50.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "بازبینی نهایی", rotation = 2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "شمارش درست بود؟ تا آخر وقت می‌تونید اصلاحش کنید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ---- شمارش‌گر معکوس بازبینی ----
        Box(
            modifier = Modifier
                .size(92.dp)
                .then(if (seconds <= 3) Modifier.breathing(intensity = 0.06f, periodMs = 600) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(92.dp)) {
                drawArc(
                    color = extras.glassStrong,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = if (seconds <= 3) extras.danger else extras.warning,
                    startAngle = -90f,
                    sweepAngle = 360f * reviewProgress,
                    useCenter = false,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = seconds.toPersianDigits(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = if (seconds <= 3) extras.danger else MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // ---- عدد نهایی و دکمه‌های اصلاح ----
        Text(
            text = state.teamDisplayName(state.claimingTeam),
            style = MaterialTheme.typography.titleLarge,
            color = teamColor
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { rotationZ = 4f }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(lerp(extras.success, Color.White, 0.2f), extras.success),
                            center = androidx.compose.ui.geometry.Offset(0.3f, 0.25f),
                            radius = 190f
                        ),
                        blobShape(seed = 21)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onIncrement
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "زیاد کن", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.counted.toPersianDigits(),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "از ${state.claim.toPersianDigits()} ادعا",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { rotationZ = -4f }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(lerp(extras.danger, Color.White, 0.2f), extras.danger),
                            center = androidx.compose.ui.geometry.Offset(0.3f, 0.25f),
                            radius = 190f
                        ),
                        blobShape(seed = 27)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDecrement
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, contentDescription = "کم کن", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KButton(
                text = "همینه، تایید!",
                onClick = onConfirm,
                accent = extras.success,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "تایید کنی یا وقت تموم شه، امتیاز با همین عدد حساب می‌شه",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** نتیجه‌ی دست: چه اتفاقی افتاد و امتیازها چطور تغییر کرد */
@Composable
fun GgResultScreen(
    state: GandeGooUiState,
    onProceed: () -> Unit,
) {
    val extras = kiExtras
    val teamColors = extras.teamColors
    val outcome = state.lastOutcome ?: return

    val (emoji, title, color) = when (outcome.kind) {
        GgOutcome.Kind.FULL -> Triple("🎉", "گنده‌گو راست می‌گفت!", extras.success)
        GgOutcome.Kind.PARTIAL -> Triple("😅", "به ادعا نرسید!", extras.warning)
        GgOutcome.Kind.FAIL -> Triple("💥", "گنده‌گویی گران تمام شد!", extras.danger)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = emoji, fontSize = 72.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = color,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${state.teamDisplayName(outcome.claimingTeam)} گفت ${outcome.claim.toPersianDigits()} مورد، " +
                "${outcome.counted.toPersianDigits()} مورد گفت",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                repeat(state.teamCount) { i ->
                    val delta = outcome.deltas.getOrNull(i) ?: 0
                    val teamColor = teamColors.getOrElse(i) { teamColors[0] }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.teamDisplayName(i),
                            style = MaterialTheme.typography.titleMedium,
                            color = teamColor,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (delta != 0) {
                                Text(
                                    text = if (delta > 0) "+${delta.toPersianDigits()}" else "-${(-delta).toPersianDigits()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (delta > 0) extras.success else extras.danger
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(
                                text = state.scores[i].toPersianDigits(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        KButton(
            text = if (state.allCellsPlayed) "کی برد؟ 🏆" else "ادامه",
            onClick = onProceed,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

/** اعلام برنده‌ی گنده‌گو */
@Composable
fun GgWinnerScreen(
    state: GandeGooUiState,
    winners: List<Int>,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val extras = kiExtras
    val teamColors = extras.teamColors

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
            Text(text = "🏆", fontSize = 84.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (winners.size > 1) {
                    "مساوی! ${winners.joinToString(" و ") { state.teamDisplayName(it) }}"
                } else {
                    state.teamDisplayName(winners.firstOrNull() ?: 0)
                },
                style = MaterialTheme.typography.displayMedium,
                color = teamColors.getOrElse(winners.firstOrNull() ?: 0) { teamColors[0] },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    (0 until state.teamCount)
                        .sortedByDescending { state.scores[it] }
                        .forEachIndexed { rank, team ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (team in winners) "🥇" else "${(rank + 1).toPersianDigits()}",
                                        fontSize = 20.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = state.teamDisplayName(team),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = teamColors.getOrElse(team) { teamColors[0] }
                                    )
                                }
                                Text(
                                    text = state.scores[team].toPersianDigits(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            ShareWinButton(
                gameId = "gandegoo",
                gameTitle = "گنده گو",
                gameEmoji = "🎭",
                winnerText = if (winners.size > 1) "مساوی!" else state.teamDisplayName(winners.firstOrNull() ?: 0),
                scoreLines = (0 until state.teamCount).sortedByDescending { state.scores[it] }
                    .map { state.teamDisplayName(it) to state.scores[it].toPersianDigits() },
                winnerNames = winners.map { state.teamDisplayName(it) },
            )
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(12.dp))
            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
        }

    }
}
