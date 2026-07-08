package com.navidabbasian.kibord.games.pantomime.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.formatMillisAsClock
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.pantomime.model.PantoAttempt
import com.navidabbasian.kibord.games.pantomime.model.PantoResult
import com.navidabbasian.kibord.core.ui.components.breathing

/** نمایش مخفیانه‌ی کلمه به اجراکننده — فقط او صفحه را ببیند! */
@Composable
fun PantoWordRevealScreen(
    attempt: PantoAttempt,
    performerTeamName: String,
    teamColor: androidx.compose.ui.graphics.Color,
    onStartPerform: () -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val extras = kiExtras

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = performerTeamName,
            style = MaterialTheme.typography.titleLarge,
            color = teamColor
        )
        Text(
            text = "اجراکننده گوشی رو بگیره — بقیه نبینن! 🤫",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${attempt.categoryEmoji} ${attempt.categoryName}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (attempt.isGolden) "🏆 طلایی" else "${attempt.points.toPersianDigits()} امتیازی",
                style = MaterialTheme.typography.labelLarge,
                color = if (attempt.isGolden) extras.gold else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        (if (attempt.isGolden) extras.gold else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            strong = true,
            borderColor = if (attempt.isGolden) extras.gold.copy(alpha = 0.7f) else teamColor.copy(alpha = 0.6f),
            onClick = if (!revealed) ({ revealed = true }) else null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (revealed) {
                    Text(
                        text = attempt.word,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 44.sp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🙈", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لمس کن تا کلمه رو ببینی",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "⏱ زمان اجرا: ${formatMillisAsClock(attempt.durationMillis)}" +
                if (attempt.isGolden) "\n⚠️ حدس نزنید، حذف می‌شوید!" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (attempt.isGolden) extras.danger else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            KButton(
                text = "حفظ شدم — شروع اجرا!",
                onClick = onStartPerform,
                enabled = revealed
            )
        }
    }
}

/** صفحه‌ی اجرای پانتومیم: تایمر بزرگ + دکمه‌های حدس زد / نشد */
@Composable
fun PantoPerformScreen(
    attempt: PantoAttempt,
    timeLeftMillis: Long,
    performerTeamName: String,
    teamColor: androidx.compose.ui.graphics.Color,
    onSuccess: () -> Unit,
    onFail: () -> Unit,
) {
    val extras = kiExtras
    val seconds = (timeLeftMillis / 1000).toInt()
    val urgent = seconds <= 10

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val progress by animateFloatAsState(
        targetValue = timeLeftMillis.toFloat() / attempt.durationMillis,
        animationSpec = tween(120),
        label = "panto_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = performerTeamName,
            style = MaterialTheme.typography.titleLarge,
            color = teamColor
        )
        Text(
            text = if (attempt.isGolden) "🏆 اجرای طلایی — ۳۰ امتیاز یا حذف!" else "${attempt.points.toPersianDigits()} امتیازی + پاداش زمان",
            style = MaterialTheme.typography.bodyMedium,
            color = if (attempt.isGolden) extras.gold else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .size(210.dp)
                .then(if (urgent) Modifier.breathing(intensity = 0.05f, periodMs = 600) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(210.dp)) {
                drawArc(
                    color = extras.glassStrong,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = if (urgent) extras.danger else teamColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = formatMillisAsClock(timeLeftMillis),
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                color = if (urgent) extras.danger else MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "کلمه (فقط داورها ببینن):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = attempt.word,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            KButton(
                text = "درست حدس زد! ✓",
                onClick = onSuccess,
                accent = extras.success,
            )
            Spacer(modifier = Modifier.height(10.dp))
            KButton(
                text = "نشد ✗",
                onClick = onFail,
                style = KButtonStyle.Danger
            )
        }
    }
}

/** نتیجه‌ی یک اجرای عادی (حذف طلایی صفحه‌ی مخصوص خودش را دارد) */
@Composable
fun PantoResultScreen(
    result: PantoResult,
    performerTeamName: String,
    teamColor: androidx.compose.ui.graphics.Color,
    scoresContent: @Composable () -> Unit,
    onProceed: () -> Unit,
    proceedLabel: String = "ادامه",
) {
    val extras = kiExtras

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = if (result.success) "🎉" else "⏰", fontSize = 72.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (result.success) "حدس زد!" else "حدس زده نشد",
            style = MaterialTheme.typography.displayMedium,
            color = if (result.success) extras.success else extras.warning,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "«${result.attempt.word}»",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (result.success) {
            Text(
                text = buildString {
                    append("$performerTeamName: ")
                    append("+${result.earned.toPersianDigits()} امتیاز")
                    if (result.bonus > 0) {
                        append(" (${result.attempt.let { if (it.isGolden) 30 else it.points }.toPersianDigits()} پایه + ${result.bonus.toPersianDigits()} پاداش زمان)")
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                color = teamColor,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "بدون امتیاز — نوبت می‌چرخه",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(22.dp))
        scoresContent()
        Spacer(modifier = Modifier.height(26.dp))
        KButton(text = proceedLabel, onClick = onProceed, modifier = Modifier.navigationBarsPadding())
    }
}
