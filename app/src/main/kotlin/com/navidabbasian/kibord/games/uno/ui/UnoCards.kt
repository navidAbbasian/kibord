package com.navidabbasian.kibord.games.uno.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
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
 * هنر کارت‌های اونو — تماماً ترسیمی، بدون هیچ فایل تصویری:
 * مستطیل گرد رنگی، نوار بیضی سفید مورب، نماد بزرگ مرکزی و ایندکس‌های کوچک گوشه.
 * وایلدها دایره‌ی چهاررنگ چرخان دارند و پشت کارت قرمز تیره با بیضی «اونو» است.
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

/** نشانه‌ی گوشه و مرکز به‌صورت متن (برای عدد و برعکس و ‎+۲/+۴) */
private fun cornerLabel(card: UnoCard): String = when (card.kind) {
    UnoKind.NUMBER -> card.number.toPersianDigits()
    UnoKind.SKIP -> "Ø"
    UnoKind.REVERSE -> "⇄"
    UnoKind.DRAW_TWO -> "+۲"
    UnoKind.WILD -> "✦"
    UnoKind.WILD_DRAW_FOUR -> "+۴"
}

/**
 * روی یک کارت اونو. اندازه با `width` مقیاس می‌شود (نسبت ۲ به ۳)
 * و در ۶۴dp دست و اندازه‌ی بزرگ‌ترِ دسته‌ی رد خوانا می‌ماند.
 */
@Composable
fun UnoCardFace(
    card: UnoCard,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val height = width * 1.5f
    val body = card.color?.let { unoColorOf(it) } ?: WildBody
    val corner = RoundedCornerShape(width * 0.14f)
    val bigWild = card.kind == UnoKind.WILD || card.kind == UnoKind.WILD_DRAW_FOUR

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(width * 0.04f, corner)
            .clip(corner),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // بدنه + هاله‌ی روشن گوشه برای حجم
            drawRect(body)
            drawRect(
                color = lerp(body, Color.White, 0.22f).copy(alpha = 0.5f),
                size = Size(size.width, size.height * 0.24f),
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
            // نوار بیضی سفید مورب
            rotate(degrees = -32f) {
                drawOval(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.22f),
                    size = Size(size.width * 0.80f, size.height * 0.56f),
                )
            }
            when (card.kind) {
                UnoKind.SKIP -> drawSkipGlyph(body)
                UnoKind.WILD, UnoKind.WILD_DRAW_FOUR -> drawColorWheel()
                else -> Unit
            }
        }

        // نماد مرکزی متنی
        val centerText = when (card.kind) {
            UnoKind.NUMBER -> card.number.toPersianDigits()
            UnoKind.REVERSE -> "⇄"
            UnoKind.DRAW_TWO -> "+۲"
            UnoKind.WILD_DRAW_FOUR -> "+۴"
            else -> null
        }
        if (centerText != null) {
            Text(
                text = centerText,
                color = if (bigWild) Color.White else body,
                fontSize = (width.value * if (card.kind == UnoKind.NUMBER) 0.52f else 0.40f).sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ایندکس‌های گوشه
        val indexSize = (width.value * 0.20f).sp
        val indexColor = Color.White
        Text(
            text = cornerLabel(card),
            color = indexColor,
            fontSize = indexSize,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = width * 0.10f, vertical = width * 0.05f),
        )
        Text(
            text = cornerLabel(card),
            color = indexColor,
            fontSize = indexSize,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = width * 0.10f, vertical = width * 0.05f),
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

/** دایره‌ی چهاررنگ وایلدها */
private fun DrawScope.drawColorWheel() {
    val r = size.width * 0.30f
    val c = Offset(size.width / 2f, size.height / 2f)
    val rect = Offset(c.x - r, c.y - r)
    val d = Size(r * 2, r * 2)
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
        radius = r + size.width * 0.02f,
        center = c,
        style = Stroke(width = size.width * 0.045f),
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
