package com.navidabbasian.kibord.core.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.util.toPersianDigits
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

// ----------------------------------------------------------------
// پالتِ مشترک میز چوبی — حکم، شلم و هر بازی کارتیِ بعدی
// ----------------------------------------------------------------

val TableWoodPlankColors = listOf(Color(0xFFB67B3E), Color(0xFFC68A4A), Color(0xFFA96F33))
val TableWoodSeam = Color(0xFF7A5122)
val TablePillBrown = Color(0xDB4A2F1B)
val TablePillGold = Color(0xFFC9A24B)
val TablePillCream = Color(0xFFF5E3B8)
val TableBadgeBlue = Color(0xFF2D7DF6)
val TableGlowCyan = Color(0xFF35D6E8)
val TableBackBlueTop = Color(0xFF1F3A6E)
val TableBackBlueBottom = Color(0xFF0F2547)

// ----------------------------------------------------------------
// زمینه‌ی چوبی
// ----------------------------------------------------------------

/** میز چوبی: تخته‌های عمودی گرم با درز، رگه و تیرگی ملایم گوشه‌ها */
@Composable
fun WoodPlankBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val plankW = 62.dp.toPx()
        var x = 0f
        var i = 0
        while (x < size.width) {
            drawRect(
                color = TableWoodPlankColors[i % TableWoodPlankColors.size],
                topLeft = Offset(x, 0f),
                size = Size(plankW, size.height),
            )
            // درز باریک تیره بین تخته‌ها
            drawLine(
                color = TableWoodSeam.copy(alpha = 0.8f),
                start = Offset(x + plankW, 0f),
                end = Offset(x + plankW, size.height),
                strokeWidth = 2.dp.toPx(),
            )
            // چند رگه‌ی کوتاه افقی
            val rnd = Random(i * 131 + 7)
            repeat(4) {
                val gy = rnd.nextFloat() * size.height
                val gx = x + 6.dp.toPx() + rnd.nextFloat() * (plankW - 24.dp.toPx())
                drawLine(
                    color = TableWoodSeam.copy(alpha = 0.20f),
                    start = Offset(gx, gy),
                    end = Offset(gx + 10.dp.toPx() + rnd.nextFloat() * 14.dp.toPx(), gy + rnd.nextFloat() * 3f),
                    strokeWidth = 1.5f,
                )
            }
            x += plankW
            i++
        }
        // تیرگی ملایم به سمت گوشه‌ها
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Transparent, Color(0x59000000)),
                center = center,
                radius = size.maxDimension * 0.72f,
            ),
            size = size,
        )
    }
}

// ----------------------------------------------------------------
// پشتِ کارت تزئینی
// ----------------------------------------------------------------

/** پشتِ کارتِ سرمه‌ای با قاب دوخطِ طلایی و ترنجِ وسط */
@Composable
fun OrnateCardBack(width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width, width * 1.4f)) {
        val r = size.width * 0.13f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(TableBackBlueTop, TableBackBlueBottom)),
            cornerRadius = CornerRadius(r, r),
        )
        val stroke = Stroke(width = (size.width * 0.022f).coerceAtLeast(1f))
        val i1 = size.width * 0.055f
        val i2 = size.width * 0.115f
        drawRoundRect(
            color = TablePillGold,
            topLeft = Offset(i1, i1),
            size = Size(size.width - 2 * i1, size.height - 2 * i1),
            cornerRadius = CornerRadius(r * 0.75f, r * 0.75f),
            style = stroke,
        )
        drawRoundRect(
            color = TablePillGold.copy(alpha = 0.7f),
            topLeft = Offset(i2, i2),
            size = Size(size.width - 2 * i2, size.height - 2 * i2),
            cornerRadius = CornerRadius(r * 0.5f, r * 0.5f),
            style = Stroke(width = stroke.width * 0.7f),
        )
        // ترنج مرکزی: مربع‌های تودرتو (یکی چرخیده) + نقطه‌ی وسط
        val s = size.width * 0.36f
        val c = center
        rotate(degrees = 45f, pivot = c) {
            drawRect(
                color = TablePillGold,
                topLeft = Offset(c.x - s / 2, c.y - s / 2),
                size = Size(s, s),
                style = stroke,
            )
        }
        val s2 = s * 0.68f
        drawRect(
            color = TablePillGold.copy(alpha = 0.85f),
            topLeft = Offset(c.x - s2 / 2, c.y - s2 / 2),
            size = Size(s2, s2),
            style = Stroke(width = stroke.width * 0.7f),
        )
        rotate(degrees = 45f, pivot = c) {
            val s3 = s * 0.3f
            drawRect(
                color = TablePillGold.copy(alpha = 0.45f),
                topLeft = Offset(c.x - s3 / 2, c.y - s3 / 2),
                size = Size(s3, s3),
            )
        }
        drawCircle(color = TablePillGold, radius = size.width * 0.035f, center = c)
        // گل‌های کوچک گوشه‌ها
        val fi = size.width * 0.19f
        listOf(
            Offset(fi, fi),
            Offset(size.width - fi, fi),
            Offset(fi, size.height - fi),
            Offset(size.width - fi, size.height - fi),
        ).forEach { p ->
            drawCircle(color = TablePillGold.copy(alpha = 0.65f), radius = size.width * 0.032f, center = p)
        }
    }
}

// ----------------------------------------------------------------
// بادبزن‌های پشتِ کارت حریف‌ها
// ----------------------------------------------------------------

/** بادبزن بالای میز: کمان پشتِ کارت‌ها */
@Composable
fun TopBackFan(count: Int, modifier: Modifier = Modifier, cardWidth: Dp = 42.dp, maxCards: Int = 13) {
    if (count <= 0) return
    val n = min(count, maxCards)
    val step = min(20f, 150f / n)
    Box(modifier = modifier.height(cardWidth * 1.4f + 14.dp), contentAlignment = Alignment.Center) {
        for (i in 0 until n) {
            val rel = i - (n - 1) / 2f
            OrnateCardBack(
                width = cardWidth,
                modifier = Modifier
                    .offset(x = (rel * step).dp, y = (abs(rel) * abs(rel) * 0.55f).dp)
                    .graphicsLayer { rotationZ = rel * 4.5f },
            )
        }
    }
}

/** بادبزن کناری: پشتِ کارت‌های چرخیده‌ی ۹۰ درجه چسبیده به لبه */
@Composable
fun SideBackFan(count: Int, rightSide: Boolean, modifier: Modifier = Modifier, cardWidth: Dp = 36.dp, maxCards: Int = 17) {
    if (count <= 0) return
    val n = min(count, maxCards)
    val stepY = min(15f, 220f / n)
    val edgeX = if (rightSide) 10f else -10f
    Box(modifier = modifier.width(cardWidth * 1.4f), contentAlignment = Alignment.Center) {
        for (i in 0 until n) {
            val rel = i - (n - 1) / 2f
            OrnateCardBack(
                width = cardWidth,
                modifier = Modifier
                    .offset(
                        x = (edgeX + abs(rel) * abs(rel) * 0.12f * (if (rightSide) 1f else -1f)).dp,
                        y = (rel * stepY).dp,
                    )
                    .graphicsLayer { rotationZ = (if (rightSide) 90f else -90f) + rel * 2f },
            )
        }
    }
}

// ----------------------------------------------------------------
// قرص‌ها و چیپ‌ها
// ----------------------------------------------------------------

/** قرص کوچک قهوه‌ای با حاشیه‌ی طلایی — برای نوار بالای میز */
@Composable
fun WoodPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(TablePillBrown, RoundedCornerShape(14.dp))
            .border(1.dp, TablePillGold.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TablePillCream,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * قرص اسم بازیکن: قهوه‌ای تیره با حاشیه‌ی طلایی و نشانِ رنگیِ کنارش.
 * [crowned] تاج حاکم، [glowing] هاله‌ی نوبت، [vertical]+[rotate] برای کناره‌های میز.
 */
@Composable
fun TablePill(
    name: String,
    crowned: Boolean,
    badge: String,
    glowing: Boolean,
    vertical: Boolean = false,
    rotate: Float = 0f,
    badgeColor: Color = TableBadgeBlue,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (glowing) TableGlowCyan else TablePillGold
    Row(
        modifier = modifier
            .then(if (vertical) Modifier.graphicsLayer { rotationZ = rotate } else Modifier)
            .then(if (glowing) Modifier.breathing(intensity = 0.04f, periodMs = 1400) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(TablePillBrown, RoundedCornerShape(14.dp))
                .border(if (glowing) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = (if (crowned) "👑 " else "") + name,
                style = MaterialTheme.typography.labelLarge,
                color = TablePillCream,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .background(badgeColor, RoundedCornerShape(50))
                .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

/**
 * چیپ درخشان وسطِ بالای میز: قرص قهوه‌ای با هاله‌ی فیروزه‌ای.
 * [glowing] = اعلام‌شده (هاله‌ی پررنگ)؛ محتوا آزاد است (متن، خال و…).
 */
@Composable
fun GlowChip(
    glowing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .border(6.dp, TableGlowCyan.copy(alpha = if (glowing) 0.22f else 0.10f), RoundedCornerShape(22.dp))
            .padding(3.dp)
            .border(3.dp, TableGlowCyan.copy(alpha = if (glowing) 0.45f else 0.20f), RoundedCornerShape(19.dp))
            .padding(2.dp),
    ) {
        Row(
            modifier = Modifier
                .background(TablePillBrown, RoundedCornerShape(17.dp))
                .border(1.5.dp, TableGlowCyan.copy(alpha = if (glowing) 0.95f else 0.45f), RoundedCornerShape(17.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** دسته‌ی دست‌های برده: دو پشتِ کارت کوچک روی هم + نشانِ عددی با حاشیه‌ی فیروزه‌ای */
@Composable
fun WonTrickPile(count: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(52.dp, 54.dp), contentAlignment = Alignment.Center) {
        OrnateCardBack(
            width = 26.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { rotationZ = -8f },
        )
        OrnateCardBack(
            width = 26.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 6.dp, y = 2.dp)
                .graphicsLayer { rotationZ = 9f },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .background(TablePillBrown, CircleShape)
                .border(1.5.dp, TableGlowCyan, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toPersianDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
