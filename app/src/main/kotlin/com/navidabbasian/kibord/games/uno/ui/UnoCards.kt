package com.navidabbasian.kibord.games.uno.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.uno.engine.UnoCard
import com.navidabbasian.kibord.games.uno.engine.UnoColor
import com.navidabbasian.kibord.games.uno.engine.UnoKind

/*
 * هنر کارت‌های اونو — تماماً ترسیمی، بدون هیچ فایل تصویری.
 *
 * دو حالت رندر داریم:
 *  - «کامل»: بیضی سفید مورب + نماد بزرگ مرکزی + ایندکس کوچک وارونه در گوشه‌ی پایین.
 *    فقط وقتی که کارت به‌قدر کافی پهن باشد (>= 56dp) و روی هم نیفتاده باشد.
 *  - «فشرده» (بادبزن دست): فقط بدنه‌ی رنگی و نشان درشت گوشه‌ی بالا-آغاز؛
 *    چون از کارتِ زیرِ بادبزن فقط همان نوارِ آغازین دیده می‌شود، هیچ عنصر
 *    دیگری نباید داخل آن نوار بیاید تا دست شلوغ نشود.
 */

/** رنگ‌های رسمی چهارگانه‌ی کی‌برد برای اونو */
fun unoColorOf(color: UnoColor): Color = when (color) {
    UnoColor.RED -> Color(0xFFE64A3C)
    UnoColor.YELLOW -> Color(0xFFF4B63F)
    UnoColor.GREEN -> Color(0xFF3FB37A)
    UnoColor.BLUE -> Color(0xFF3D7BF2)
}

/** برچسب فارسی رنگ برای پیام‌ها */
fun unoColorName(color: UnoColor): String = when (color) {
    UnoColor.RED -> "قرمز"
    UnoColor.YELLOW -> "زرد"
    UnoColor.GREEN -> "سبز"
    UnoColor.BLUE -> "آبی"
}

private val WildBody = Color(0xFF272134)
private val BackBody = Color(0xFF8E1F14)

/** سایه‌ی ملایم زیر متن‌های سفید تا روی زرد هم خوانا بمانند */
private val IndexShadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)

/** نشانه‌ی متنی گوشه (وایلد ساده به‌جای متن، دایره‌ی چهاررنگ می‌گیرد) */
private fun cornerLabel(card: UnoCard): String? = when (card.kind) {
    UnoKind.NUMBER -> card.number.toPersianDigits()
    UnoKind.SKIP -> "Ø"
    UnoKind.REVERSE -> "⇄"
    UnoKind.DRAW_TWO -> "+۲"
    UnoKind.WILD -> null
    UnoKind.WILD_DRAW_FOUR -> "+۴"
}

/**
 * روی یک کارت اونو. اندازه با `width` مقیاس می‌شود (نسبت ۲ به ۳).
 *
 * `compact` را برای کارت‌های روی‌هم‌افتاده‌ی بادبزن دست بده: نماد بزرگ مرکزی
 * و ایندکس پایین حذف می‌شوند و فقط نشان گوشه‌ی بالا-آغاز می‌ماند، پس کارت
 * از همان نوار باریکِ دیدنی‌اش قابل شناسایی است. رندر کامل خودبه‌خود فقط
 * برای پهنای ۵۶dp به بالا فعال می‌شود.
 */
@Composable
fun UnoCardFace(
    card: UnoCard,
    width: Dp,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val height = width * 1.5f
    val body = card.color?.let { unoColorOf(it) } ?: WildBody
    val corner = RoundedCornerShape(width * 0.14f)
    val full = !compact && width >= 56.dp

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(width * 0.04f, corner)
            .clip(corner),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // بدنه + درخشش ملایم بالای کارت (گرادیان نرم تا نوار گوشه تمیز بماند)
            drawRect(body)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.20f),
                    1f to Color.Transparent,
                    endY = size.height * 0.30f,
                ),
                size = Size(size.width, size.height * 0.30f),
            )
            // قاب سفید داخلی
            val inset = size.width * 0.055f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f),
                style = Stroke(width = size.width * 0.035f),
            )
            if (full) {
                // نوار بیضی سفید مورب — کمی جمع‌تر از قبل تا به نشان گوشه نرسد
                rotate(degrees = -32f) {
                    drawOval(
                        color = Color.White,
                        topLeft = Offset(size.width * 0.13f, size.height * 0.27f),
                        size = Size(size.width * 0.74f, size.height * 0.46f),
                    )
                }
                when (card.kind) {
                    UnoKind.SKIP -> drawSkipGlyph(body)
                    UnoKind.WILD, UnoKind.WILD_DRAW_FOUR -> drawColorWheel(
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width * 0.30f,
                        ringWidth = size.width * 0.045f,
                    )
                    else -> Unit
                }
            }
        }

        // نماد مرکزی متنی — فقط در رندر کامل
        if (full) {
            val centerText = when (card.kind) {
                UnoKind.NUMBER -> card.number.toPersianDigits()
                UnoKind.REVERSE -> "⇄"
                UnoKind.DRAW_TWO -> "+۲"
                UnoKind.WILD_DRAW_FOUR -> "+۴"
                else -> null
            }
            if (centerText != null) {
                val onWheel = card.kind == UnoKind.WILD_DRAW_FOUR
                Text(
                    text = centerText,
                    color = if (onWheel) Color.White else body,
                    style = if (onWheel) TextStyle(shadow = IndexShadow) else TextStyle.Default,
                    fontSize = (width.value * if (card.kind == UnoKind.NUMBER) 0.52f else 0.40f).sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // نشان گوشه‌ی بالا-آغاز: تنها چیزی که در نوار دیدنیِ کارتِ زیر بادبزن می‌ماند
        val label = cornerLabel(card)
        val indexFraction = if (full) 0.20f else 0.26f
        if (label != null) {
            Text(
                text = label,
                color = Color.White,
                style = TextStyle(shadow = IndexShadow),
                fontSize = (width.value * indexFraction).sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = width * 0.10f, vertical = width * 0.05f),
            )
        } else {
            // وایلد ساده: نقطه‌ی چهاررنگ کوچک
            CornerWheelDot(
                size = width * indexFraction,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = width * 0.10f, vertical = width * 0.08f),
            )
        }

        // ایندکس کوچک وارونه در پایین-پایان — فقط در رندر کامل
        if (full) {
            if (label != null) {
                Text(
                    text = label,
                    color = Color.White,
                    style = TextStyle(shadow = IndexShadow),
                    fontSize = (width.value * 0.16f).sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = width * 0.10f, vertical = width * 0.05f)
                        .graphicsLayer { rotationZ = 180f },
                )
            } else {
                CornerWheelDot(
                    size = width * 0.16f,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = width * 0.10f, vertical = width * 0.08f),
                )
            }
        }
    }
}

/** نقطه‌ی چهاررنگ گوشه برای وایلد ساده */
@Composable
private fun CornerWheelDot(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        drawColorWheel(
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            radius = this.size.minDimension / 2f * 0.85f,
            ringWidth = this.size.minDimension * 0.10f,
        )
    }
}

/** نماد «رد شدن»: دایره با خط مورب */
private fun DrawScope.drawSkipGlyph(color: Color) {
    val r = size.width * 0.24f
    val c = Offset(size.width / 2f, size.height / 2f)
    val stroke = size.width * 0.075f
    drawCircle(color = color, radius = r, center = c, style = Stroke(width = stroke))
    val d = r * 0.72f
    drawLine(
        color = color,
        start = Offset(c.x - d, c.y + d),
        end = Offset(c.x + d, c.y - d),
        strokeWidth = stroke,
    )
}

/** دایره‌ی چهاررنگ وایلدها (مرکز و شعاع دلخواه تا برای نقطه‌ی گوشه هم به کار برود) */
private fun DrawScope.drawColorWheel(center: Offset, radius: Float, ringWidth: Float) {
    val rect = Offset(center.x - radius, center.y - radius)
    val d = Size(radius * 2, radius * 2)
    val colors = listOf(
        unoColorOf(UnoColor.RED),
        unoColorOf(UnoColor.BLUE),
        unoColorOf(UnoColor.YELLOW),
        unoColorOf(UnoColor.GREEN),
    )
    colors.forEachIndexed { i, col ->
        drawArc(
            color = col,
            startAngle = -90f + i * 90f,
            sweepAngle = 90f,
            useCenter = true,
            topLeft = rect,
            size = d,
        )
    }
    drawCircle(
        color = Color.White,
        radius = radius + ringWidth / 2f,
        center = center,
        style = Stroke(width = ringWidth),
    )
}

/** پشت کارت: قرمز تیره با بیضی مورب و «اونو» */
@Composable
fun UnoCardBack(
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val height = width * 1.5f
    val corner = RoundedCornerShape(width * 0.14f)
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(width * 0.04f, corner)
            .clip(corner),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(BackBody)
            drawRect(
                color = lerp(BackBody, Color.White, 0.16f).copy(alpha = 0.6f),
                size = Size(size.width, size.height * 0.24f),
            )
            val inset = size.width * 0.055f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f),
                style = Stroke(width = size.width * 0.035f),
            )
            rotate(degrees = -32f) {
                drawOval(
                    color = Color(0xFFE64A3C),
                    topLeft = Offset(size.width * 0.10f, size.height * 0.24f),
                    size = Size(size.width * 0.80f, size.height * 0.52f),
                )
                drawOval(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.24f),
                    size = Size(size.width * 0.80f, size.height * 0.52f),
                    style = Stroke(width = size.width * 0.03f),
                )
            }
        }
        Text(
            text = "اونو",
            color = Color.White,
            fontSize = (width.value * 0.30f).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
