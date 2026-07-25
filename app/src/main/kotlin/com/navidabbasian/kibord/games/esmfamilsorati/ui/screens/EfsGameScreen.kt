package com.navidabbasian.kibord.games.esmfamilsorati.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.components.CircularPlayerRing
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.animateRingRotation
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.formatMillisAsClock
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsConstants
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsUiState

/**
 * صفحه‌ی اصلی اسم فامیل سرعتی: بمب مشترک، پیل موضوع، تراشه‌های ساعت شخصی،
 * حلقه‌ی بازیکنان، کارت حرف در مرکز (لمس = گفتم و نوبت بعدی) و دکمه‌ی پاس.
 */
@Composable
fun EfsGameScreen(
    state: EfsUiState,
    onCenterTap: () -> Unit,
    onPass: () -> Unit,
    onPause: () -> Unit,
) {
    val extras = kiExtras

    // روشن نگه‌داشتن صفحه حین بازی
    val view = LocalView.current
    DisposableEffect(state.isPlaying) {
        view.keepScreenOn = state.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- نوار بالا: مکث + بمب ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPause) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(extras.glassStrong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "مکث",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            BombCounter(
                bombTimeLeftMillis = state.bombTimeLeftMillis,
                isPlaying = state.isPlaying
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(42.dp))
        }

        // ---- پیل موضوع — همیشه جلوی چشم ----
        state.topic?.let { topic ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                GlassCard(cornerRadius = 16.dp) {
                    Text(
                        text = "${topic.emoji} ${topic.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // ---- تراشه‌های ساعت شخصی بازیکنان ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            state.players.forEach { player ->
                val color = extras.teamColors.getOrElse(player.id) { extras.teamColors[0] }
                val isCurrent = player.id == state.currentPlayerIndex
                val scale by animateFloatAsState(
                    targetValue = if (isCurrent && !player.eliminated) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "chip_scale"
                )
                val lowTime = !player.eliminated && player.remainingTimeMillis <= 15_000
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .graphicsLayer { alpha = if (player.eliminated) 0.35f else if (isCurrent) 1f else 0.75f }
                        .background(color, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (player.eliminated) {
                            "${player.name} ✖"
                        } else {
                            "${player.name} ${formatMillisAsClock(player.remainingTimeMillis)}"
                        },
                        color = if (lowTime) extras.danger else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // ---- حلقه‌ی بازیکنان + کارت حرف در مرکز ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val rotation = animateRingRotation(
                seatIndex = state.currentPlayerIndex,
                seatCount = state.players.size
            )
            CircularPlayerRing(
                rotationOffsetDeg = rotation,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(8.dp),
                center = {
                    LetterCard(state = state, onTap = onCenterTap)
                },
                items = state.players.mapIndexed { i, player ->
                    {
                        val color = extras.teamColors.getOrElse(player.id) { extras.teamColors[0] }
                        val isCurrent = i == state.currentPlayerIndex
                        val seatScale by animateFloatAsState(
                            targetValue = if (isCurrent) 1.2f else 1f,
                            animationSpec = spring(dampingRatio = 0.75f),
                            label = "seat_scale"
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(76.dp)
                                .graphicsLayer {
                                    scaleX = seatScale
                                    scaleY = seatScale
                                    alpha = if (player.eliminated) 0.3f else if (isCurrent) 1f else 0.65f
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (state.players.size > 6) 18.dp else 24.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = player.name,
                                color = color,
                                fontSize = if (state.players.size > 6) 12.sp else 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            )
        }

        // ---- پایین: پاس حرف ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KButton(
                text = if (state.canPass) {
                    "پاس (−${(EfsConstants.PASS_PENALTY_MILLIS / 1000).toInt().toPersianDigits()} ثانیه)"
                } else {
                    "پاس (${((state.passCooldownLeftMillis + 999) / 1000).toPersianDigits()})"
                },
                onClick = onPass,
                style = KButtonStyle.Glass,
                enabled = state.canPass && state.isPlaying
            )
        }
    }

    // ---- روکش انفجار ----
    AnimatedVisibility(
        visible = state.showExplosion,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(extras.danger.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "💥", fontSize = 96.sp)
                Text(
                    text = "بمب منفجر شد!",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "-${(EfsConstants.BOMB_PENALTY_MILLIS / 1000).toInt().toPersianDigits()} ثانیه از ${state.currentPlayer?.name ?: ""}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BombCounter(bombTimeLeftMillis: Long, isPlaying: Boolean) {
    val extras = kiExtras
    val seconds = (bombTimeLeftMillis / 1000).toInt()
    val color = when {
        seconds <= 5 -> extras.danger
        seconds <= 10 -> extras.warning
        else -> extras.gold
    }
    val pulse by animateFloatAsState(
        targetValue = if (isPlaying && seconds <= 10) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bomb_pulse"
    )
    GlassCard(cornerRadius = 20.dp, modifier = Modifier.scale(pulse)) {
        Text(
            text = "💣 ${seconds.toPersianDigits()}",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = color,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LetterCard(
    state: EfsUiState,
    onTap: () -> Unit,
) {
    val extras = kiExtras
    val currentColor = extras.teamColors.getOrElse(state.currentPlayerIndex) { extras.teamColors[0] }
    val card = state.currentCard

    GlassCard(
        modifier = Modifier.size(190.dp),
        cornerRadius = 95.dp,
        strong = true,
        borderColor = currentColor.copy(alpha = 0.8f),
        onClick = onTap
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!state.isPlaying) {
                // آماده‌باش پس از انفجار: بازی متوقف است تا خود نوبت‌دار ادامه بدهد
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = state.currentPlayer?.name ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        color = currentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "آماده‌ای؟ لمس کن!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = state.currentPlayer?.name ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = currentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = card?.display ?: "",
                        fontSize = if (card?.isGroup == true) 34.sp else 64.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "گفتی؟ بزن بعدی!",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
