package com.navidabbasian.kibord.games.uno.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    dimmed: Boolean = false,
) {
    val height = width * 1.5f
    val body = card.color?.let { unoColorOf(it) } ?: WildBody
    val corner = RoundedCornerShape(width * 0.14f)

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(width * 0.04f, corner)
            .clip(corner),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // آناتومی کارتِ واقعی اونو: لبه‌ی سفیدِ بیرونی، میدانِ رنگی داخلش،
            // بیضیِ سفیدِ مورب از پایین-چپ به بالا-راست
            drawRect(Color.White)
            val inset = size.width * 0.06f
            drawRoundRect(
                color = body,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.10f),
            )
            // درخشش خیلی ملایم بالای میدان
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.14f),
                    1f to Color.Transparent,
                    endY = size.height * 0.30f,
                ),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height * 0.30f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.10f),
            )
            // بیضی مورب — امضای کارت اونو؛ وایلدها بیضیِ چهارپاره‌ی رنگی دارند
            val ovalTL = Offset(size.width * 0.10f, size.height * 0.235f)
            val ovalSize = Size(size.width * 0.80f, size.height * 0.53f)
            rotate(degrees = -33f) {
                when (card.kind) {
                    UnoKind.WILD, UnoKind.WILD_DRAW_FOUR -> {
                        // بیضیِ چهاررنگ کلاسیکِ وایلد
                        val segs = listOf(
                            unoColorOf(UnoColor.RED),
                            unoColorOf(UnoColor.BLUE),
                            unoColorOf(UnoColor.GREEN),
                            unoColorOf(UnoColor.YELLOW),
                        )
                        segs.forEachIndexed { i, col ->
                            drawArc(
                                color = col,
                                startAngle = -90f + i * 90f,
                                sweepAngle = 90f,
                                useCenter = true,
                                topLeft = ovalTL,
                                size = ovalSize,
                            )
                        }
                        drawOval(
                            color = Color.White,
                            topLeft = ovalTL,
                            size = ovalSize,
                            style = Stroke(width = size.width * 0.035f),
                        )
                    }
                    else -> drawOval(color = Color.White, topLeft = ovalTL, size = ovalSize)
                }
            }
            // نمادهای ترسیمیِ مرکز (روی بیضی)
            when (card.kind) {
                UnoKind.SKIP -> drawSkipGlyph(body)
                UnoKind.REVERSE -> drawReverseGlyph(body)
                UnoKind.DRAW_TWO -> drawMiniCards(listOf(body, body))
                UnoKind.WILD_DRAW_FOUR -> drawMiniCards(
                    listOf(
                        unoColorOf(UnoColor.RED),
                        unoColorOf(UnoColor.BLUE),
                        unoColorOf(UnoColor.GREEN),
                        unoColorOf(UnoColor.YELLOW),
                    ),
                )
                else -> Unit
            }
        }

        // عدد مرکزی: هم‌رنگ کارت با سایه‌ی تیره — مثل چاپ واقعی
        if (card.kind == UnoKind.NUMBER) {
            Text(
                text = card.number.toPersianDigits(),
                color = body,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.35f),
                        offset = Offset(width.value * 0.05f, width.value * 0.06f),
                        blurRadius = 1f,
                    ),
                ),
                fontSize = (width.value * 0.58f).sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ایندکس گوشه‌ی بالا-آغاز — سفید با سایه، مثل کارت واقعی
        val label = cornerLabel(card)
        val indexFraction = if (compact) 0.24f else 0.20f
        if (label != null) {
            Text(
                text = label,
                color = Color.White,
                style = TextStyle(shadow = IndexShadow),
                fontSize = (width.value * indexFraction).sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = width * 0.11f, vertical = width * 0.045f),
            )
        } else {
            CornerWheelDot(
                size = width * indexFraction,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = width * 0.11f, vertical = width * 0.07f),
            )
        }

        // ایندکس وارونه‌ی پایین-پایان
        if (!compact) {
            if (label != null) {
                Text(
                    text = label,
                    color = Color.White,
                    style = TextStyle(shadow = IndexShadow),
                    fontSize = (width.value * 0.17f).sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = width * 0.11f, vertical = width * 0.045f)
                        .graphicsLayer { rotationZ = 180f },
                )
            } else {
                CornerWheelDot(
                    size = width * 0.17f,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = width * 0.11f, vertical = width * 0.07f),
                )
            }
        }
        // کارتِ غیرمجاز: پرده‌ی تیره‌ی مات — رنگ‌ها شسته نمی‌شوند و کارت پشتی دیده نمی‌شود
        if (dimmed) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
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

/** نماد «برعکس»: دو پیکانِ قرینه مثل کارت واقعی */
private fun DrawScope.drawReverseGlyph(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val u = size.width * 0.052f
    fun arrow(sign: Float) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x + sign * (-3.4f * u), c.y + sign * (0.4f * u))
            lineTo(c.x + sign * (-0.6f * u), c.y + sign * (-2.4f * u))
            lineTo(c.x + sign * (-0.6f * u), c.y + sign * (-1.1f * u))
            lineTo(c.x + sign * (2.2f * u), c.y + sign * (-1.1f * u))
            lineTo(c.x + sign * (2.2f * u), c.y + sign * (1.9f * u))
            lineTo(c.x + sign * (0.8f * u), c.y + sign * (0.6f * u))
            lineTo(c.x + sign * (0.8f * u), c.y + sign * (0.3f * u))
            lineTo(c.x + sign * (-0.6f * u), c.y + sign * (0.3f * u))
            close()
        }
        drawPath(p, color = color)
    }
    rotate(degrees = -33f) {
        arrow(1f)
        arrow(-1f)
    }
}

/** کارت‌های کوچکِ روی‌همِ مرکز (+۲ دو تا هم‌رنگ، +۴ چهار تا چهاررنگ) */
private fun DrawScope.drawMiniCards(colors: List<Color>) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val w = size.width * 0.20f
    val h = w * 1.5f
    val n = colors.size
    colors.forEachIndexed { i, col ->
        val t = i - (n - 1) / 2f
        val cx = c.x + t * w * 0.55f
        val cy = c.y + t * h * 0.16f
        rotate(degrees = -12f + i * 8f, pivot = Offset(cx, cy)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - w / 2f - size.width * 0.016f, cy - h / 2f - size.width * 0.016f),
                size = Size(w + size.width * 0.032f, h + size.width * 0.032f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.22f),
            )
            drawRoundRect(
                color = col,
                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f),
            )
        }
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
            // پشتِ کلاسیک: لبه‌ی سفید، میدان مشکی، بیضی قرمزِ مورب
            drawRect(Color.White)
            val inset = size.width * 0.06f
            drawRoundRect(
                color = Color(0xFF17161C),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.10f),
            )
            rotate(degrees = -33f) {
                drawOval(
                    color = Color(0xFFD32F2F),
                    topLeft = Offset(size.width * 0.10f, size.height * 0.235f),
                    size = Size(size.width * 0.80f, size.height * 0.53f),
                )
            }
        }
        Text(
            text = "اونو",
            color = Color(0xFFF7C948),
            style = TextStyle(
                shadow = Shadow(
                    color = Color.White,
                    offset = Offset(0f, 0f),
                    blurRadius = 6f,
                ),
            ),
            fontSize = (width.value * 0.30f).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { rotationZ = -18f },
        )
    }
}
