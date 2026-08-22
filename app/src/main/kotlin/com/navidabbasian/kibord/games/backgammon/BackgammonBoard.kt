package com.navidabbasian.kibord.games.backgammon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgState

// ---- پالت چوبی برگرفته از عکس مرجع — تخته‌ی فیزیکی در هر دو تم یک شکل است ----
private val FrameWoodLight = Color(0xFF9A6332)
private val FrameWoodDark = Color(0xFF5E3A1E)
private val FieldLight = Color(0xFFDCA85E)
private val FieldDark = Color(0xFFC38B44)
private val TriRed = Color(0xFFA93226)
private val TriRedDark = Color(0xFF7E241B)
private val TriCream = Color(0xFFF0DCB2)
private val TriCreamDark = Color(0xFFD9BF8E)
private val HingeWoodLight = Color(0xFF6B4322)
private val HingeWoodDark = Color(0xFF432610)
private val Brass = Color(0xFFE2B04A)
private val BrassDark = Color(0xFF9C7420)
private val TrayWood = Color(0xFF8A5730)
private val TrayRecess = Color(0xFF3E2410)
private val HighlightSource = Color(0xFFFFE082)
private val HighlightDest = Color(0xFF7CE87C)

/**
 * هندسه‌ی تخته‌ی دولنگه: قاب چوبی دورتادور، دو لنگه‌ی بازی با لولای وسط،
 * و سینی‌های جمع‌کردن مهره در لبه‌ی بیرونی (راست) — بالا مال سیاه، پایین مال سفید.
 */
private class BoardGeometry(val size: Size) {
    val frameW = size.width * 0.030f
    val frameH = size.height * 0.045f
    val trayW = size.width * 0.095f
    val barW = size.width * 0.080f
    val colW = (size.width - 2f * frameW - trayW - barW) / 12f

    /** بازه‌ی عمودی زمین بازی (داخل قاب) */
    val fieldTop = frameH
    val fieldBottom = size.height - frameH
    val fieldH = fieldBottom - fieldTop
    val pointH = fieldH * 0.42f

    /** لبه‌ی چپ لولای وسط و سینی راست */
    val barX = frameW + 6f * colW
    val trayX = frameW + 12f * colW + barW

    /**
     * نگاشت شماره‌ی مطلق خانه (۱ تا ۲۴) به صفحه — تنها نقطه‌ی حقیقتِ چیدمان:
     * خانه‌های ۱ تا ۱۲ ردیف پایین از راست به چپ و ۱۳ تا ۲۴ ردیف بالا از چپ به راست.
     * ستون‌ها ۰ تا ۱۱ از چپ‌اند و لولا بین ستون ۵ و ۶ می‌نشیند.
     */
    fun columnOf(abs: Int): Pair<Int, Boolean> =
        if (abs <= 12) (12 - abs) to false else (abs - 13) to true

    /** لبه‌ی چپ ستون داده‌شده روی بوم */
    fun columnX(col: Int): Float = frameW + col * colW + (if (col >= 6) barW else 0f)

    /** خانه‌ی مطلق زیر مختصات لمس‌شده — null یعنی بیرون از خانه‌ها */
    fun pointAt(x: Float, y: Float): Int? {
        val isTop = y < size.height / 2f
        for (col in 0..11) {
            val left = columnX(col)
            if (x >= left && x < left + colW) {
                return if (isTop) col + 13 else 12 - col
            }
        }
        return null
    }

    /** آیا لمس روی لولای وسط (جای مهره‌های زده‌شده) بوده؟ */
    fun isBar(x: Float): Boolean = x >= barX && x < barX + barW

    /** آیا لمس روی سینی جمع‌کردن بوده؟ */
    fun isTray(x: Float): Boolean = x >= trayX
}

/**
 * تخته‌ی چوبی کلاسیک به سبک عکس مرجع: دو لنگه با لولای برنجی وسط،
 * ۲۴ خانه‌ی مثلثی قرمز/کرم یک‌درمیان، مهره‌های زده‌شده روی لولا،
 * سینی‌های جمع‌کردن مهره در گوشه‌های بیرونی و هایلایت مبدأ/مقصدهای قانونی.
 */
@Composable
fun BackgammonBoard(
    state: BgState,
    sourcesAbs: Set<Int>,
    selectedAbs: Int?,
    destsAbs: Set<Int>,
    offIsDest: Boolean,
    whiteColor: Color,
    blackColor: Color,
    onTapPoint: (Int) -> Unit,
    onTapEntry: () -> Unit,
    onTapOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entrySelectable = state.phase == BgPhase.MOVING && state.turn != null &&
        (state.bar(state.turn!!) > 0 || state.isEntering(state.turn!!))
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.30f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val geo = BoardGeometry(Size(size.width.toFloat(), size.height.toFloat()))
                    when {
                        geo.isTray(offset.x) -> onTapOff()
                        geo.isBar(offset.x) -> onTapEntry()
                        else -> geo.pointAt(offset.x, offset.y)?.let(onTapPoint)
                    }
                }
            },
    ) {
        val geo = BoardGeometry(size)
        drawFrameAndField(geo)
        for (abs in 1..24) drawPointTriangle(geo, abs, destsAbs.contains(abs))
        drawHinge(geo, state, whiteColor, blackColor, entrySelectable)
        drawTrays(geo, state, offIsDest, whiteColor, blackColor)
        for (abs in 1..24) {
            drawCheckers(geo, abs, state, sourcesAbs.contains(abs), selectedAbs == abs, whiteColor, blackColor)
        }
    }
}

/** قاب چوبی دورتادور و دو لنگه‌ی روشنِ زمین بازی */
private fun DrawScope.drawFrameAndField(geo: BoardGeometry) {
    // قاب بیرونی با گرادیان چوب و گوشه‌ی گرد
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(FrameWoodLight, FrameWoodDark)),
        cornerRadius = CornerRadius(26f, 26f),
    )
    // سایه‌ی داخلی لبه‌ی قاب
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.25f),
        cornerRadius = CornerRadius(26f, 26f),
        style = Stroke(width = 3.dp.toPx()),
    )
    // دو لنگه‌ی زمین: چپ و راستِ لولا — هر کدام یک تخته‌ی جدا
    val halves = listOf(
        Rect(geo.frameW, geo.fieldTop, geo.barX, geo.fieldBottom),
        Rect(geo.barX + geo.barW, geo.fieldTop, geo.trayX, geo.fieldBottom),
    )
    halves.forEach { r ->
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(FieldLight, FieldDark), startY = r.top, endY = r.bottom),
            topLeft = Offset(r.left, r.top),
            size = Size(r.width, r.height),
            cornerRadius = CornerRadius(10f, 10f),
        )
        // رگه‌ی محو نور از بالا و سایه‌ی گودیِ لنگه
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.18f),
            topLeft = Offset(r.left, r.top),
            size = Size(r.width, r.height),
            cornerRadius = CornerRadius(10f, 10f),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/** یک خانه‌ی مثلثی — قرمز تیره و کرم یک‌درمیان مثل عکس؛ مقصد مجاز سبز می‌درخشد */
private fun DrawScope.drawPointTriangle(geo: BoardGeometry, abs: Int, isDest: Boolean) {
    val (col, isTop) = geo.columnOf(abs)
    val left = geo.columnX(col)
    val base = if (isTop) geo.fieldTop else geo.fieldBottom
    val apex = if (isTop) geo.fieldTop + geo.pointH else geo.fieldBottom - geo.pointH
    val path = Path().apply {
        moveTo(left + 2f, base)
        lineTo(left + geo.colW - 2f, base)
        lineTo(left + geo.colW / 2f, apex)
        close()
    }
    val red = abs % 2 == 1
    // گرادیان از پایه به نوک تا مثلث حجم بگیرد
    drawPath(
        path,
        Brush.verticalGradient(
            colors = if (red) listOf(TriRed, TriRedDark) else listOf(TriCream, TriCreamDark),
            startY = base,
            endY = apex,
        ),
    )
    drawPath(path, Color.Black.copy(alpha = 0.12f), style = Stroke(width = 1.dp.toPx()))
    if (isDest) {
        drawPath(path, HighlightDest.copy(alpha = 0.40f))
        drawPath(path, HighlightDest, style = Stroke(width = 3.dp.toPx()))
    }
}

/** لولای وسط: نوار چوب تیره با دو لولای برنجی — مهره‌های زده‌شده همین‌جا می‌نشینند */
private fun DrawScope.drawHinge(
    geo: BoardGeometry,
    state: BgState,
    whiteColor: Color,
    blackColor: Color,
    entrySelectable: Boolean,
) {
    // نوار لولا با شیار وسط
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(HingeWoodDark, HingeWoodLight, HingeWoodDark),
            startX = geo.barX,
            endX = geo.barX + geo.barW,
        ),
        topLeft = Offset(geo.barX, 0f),
        size = Size(geo.barW, size.height),
    )
    drawLine(
        color = Color.Black.copy(alpha = 0.45f),
        start = Offset(geo.barX + geo.barW / 2f, 0f),
        end = Offset(geo.barX + geo.barW / 2f, size.height),
        strokeWidth = 1.5f.dp.toPx(),
    )
    // دو لولای برنجی مثل عکس: یک‌چهارم بالا و یک‌چهارم پایین
    listOf(size.height * 0.16f, size.height * 0.84f).forEach { cy ->
        val hw = geo.barW * 0.62f
        val hh = size.height * 0.055f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Brass, BrassDark), startY = cy - hh / 2f, endY = cy + hh / 2f),
            topLeft = Offset(geo.barX + (geo.barW - hw) / 2f, cy - hh / 2f),
            size = Size(hw, hh),
            cornerRadius = CornerRadius(4f, 4f),
        )
        // پیچ‌های ریز لولا
        listOf(cy - hh * 0.22f, cy + hh * 0.22f).forEach { sy ->
            drawCircle(BrassDark, radius = 1.6f.dp.toPx(), center = Offset(geo.barX + geo.barW / 2f, sy))
        }
    }
    // مهره‌های زده‌شده: سیاه بالای لولا، سفید پایین — مبدأ اجباری برگشت به بازی
    val r = minOf(geo.barW * 0.44f, geo.fieldH / 13f)
    val cx = geo.barX + geo.barW / 2f
    if (state.barBlack > 0) {
        val shown = minOf(state.barBlack, 3)
        for (i in 0 until shown) {
            drawChecker(
                center = Offset(cx, size.height * 0.30f - i * 2f * r * 0.6f),
                radius = r,
                color = blackColor,
                countLabel = if (i == shown - 1 && state.barBlack > 3) state.barBlack else 0,
            )
        }
    }
    if (state.barWhite > 0) {
        val shown = minOf(state.barWhite, 3)
        for (i in 0 until shown) {
            drawChecker(
                center = Offset(cx, size.height * 0.70f + i * 2f * r * 0.6f),
                radius = r,
                color = whiteColor,
                countLabel = if (i == shown - 1 && state.barWhite > 3) state.barWhite else 0,
            )
        }
    }
    // وقتی ورود اجباری است، لولا به‌عنوان مبدأ لمس‌شدنی می‌درخشد
    if (entrySelectable) {
        drawRect(
            color = HighlightSource.copy(alpha = 0.22f),
            topLeft = Offset(geo.barX, 0f),
            size = Size(geo.barW, size.height),
        )
        drawRect(
            color = HighlightSource,
            topLeft = Offset(geo.barX, 0f),
            size = Size(geo.barW, size.height),
            style = Stroke(width = 2.5f.dp.toPx()),
        )
    }
}

/** سینی‌های گوشه: شیار عمودی چوبی که مهره‌های خارج‌شده مثل تخته‌های خوابیده تویش می‌نشینند */
private fun DrawScope.drawTrays(
    geo: BoardGeometry,
    state: BgState,
    offIsDest: Boolean,
    whiteColor: Color,
    blackColor: Color,
) {
    val trayGap = size.height * 0.03f
    val topTray = Rect(geo.trayX, geo.fieldTop, geo.trayX + geo.trayW, size.height / 2f - trayGap)
    val bottomTray = Rect(geo.trayX, size.height / 2f + trayGap, geo.trayX + geo.trayW, geo.fieldBottom)
    val mover = state.turn

    fun drawTray(rect: Rect, count: Int, color: Color, highlight: Boolean) {
        // بدنه‌ی چوبی سینی و گودی داخلش
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(TrayWood, FrameWoodDark), startY = rect.top, endY = rect.bottom),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = CornerRadius(8f, 8f),
        )
        val inset = 3.dp.toPx()
        drawRoundRect(
            color = TrayRecess,
            topLeft = Offset(rect.left + inset, rect.top + inset),
            size = Size(rect.width - 2 * inset, rect.height - 2 * inset),
            cornerRadius = CornerRadius(6f, 6f),
        )
        // مهره‌های جمع‌شده: تخته‌های خوابیده که از کف سینی بالا می‌آیند؛
        // اگر شمارشان زیاد شود، پشته خودش فشرده می‌شود تا هر ۱۵ تا جا شوند
        val slabW = rect.width - 4 * inset
        val slabH = maxOf(rect.height * 0.055f, 3.dp.toPx())
        val room = rect.height - 4 * inset - slabH
        val gap = if (count > 1) minOf(slabH * 1.35f, room / (count - 1)) else 0f
        for (i in 0 until count) {
            val y = rect.bottom - inset * 2 - slabH - i * gap
            if (y < rect.top + inset) break
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.35f).compositeOverOn(color), color),
                    startY = y,
                    endY = y + slabH,
                ),
                topLeft = Offset(rect.left + 2 * inset, y),
                size = Size(slabW, slabH),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }
        if (highlight) {
            drawRoundRect(
                color = HighlightDest.copy(alpha = 0.30f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(8f, 8f),
            )
            drawRoundRect(
                color = HighlightDest,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }

    // سیاه خانه‌اش بالاست پس سینی‌اش گوشه‌ی بالایی است؛ سفید پایین
    drawTray(topTray, state.borneOffBlack, blackColor, offIsDest && mover == BgPlayer.BLACK)
    drawTray(bottomTray, state.borneOffWhite, whiteColor, offIsDest && mover == BgPlayer.WHITE)
}

/** مهره‌های یک خانه با پشته‌ی حداکثر ۵تایی و برچسب تعداد */
private fun DrawScope.drawCheckers(
    geo: BoardGeometry,
    abs: Int,
    state: BgState,
    isSource: Boolean,
    isSelected: Boolean,
    whiteColor: Color,
    blackColor: Color,
) {
    val point = state.pointAt(abs)
    val owner = point.owner ?: return
    if (point.count == 0) return
    val (col, isTop) = geo.columnOf(abs)
    val cx = geo.columnX(col) + geo.colW / 2f
    val r = minOf(geo.colW * 0.46f, geo.fieldH / 11.5f)
    val color = if (owner == BgPlayer.WHITE) whiteColor else blackColor
    val shown = minOf(point.count, 5)
    for (i in 0 until shown) {
        val cy = if (isTop) {
            geo.fieldTop + r + i * 2f * r * 0.92f
        } else {
            geo.fieldBottom - r - i * 2f * r * 0.92f
        }
        val isLast = i == shown - 1
        drawChecker(
            center = Offset(cx, cy),
            radius = r,
            color = color,
            countLabel = if (isLast && point.count > 5) point.count else 0,
        )
        if (isLast && (isSource || isSelected)) {
            drawCircle(
                color = if (isSelected) HighlightDest else HighlightSource,
                radius = r + 3.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = if (isSelected) 4.dp.toPx() else 3.dp.toPx()),
            )
        }
    }
}

/** ترکیب یک رنگ نیمه‌شفاف روی رنگ پایه — برای سرِ روشنِ تخته‌های سینی */
private fun Color.compositeOverOn(base: Color): Color {
    val a = alpha
    return Color(
        red = red * a + base.red * (1 - a),
        green = green * a + base.green * (1 - a),
        blue = blue * a + base.blue * (1 - a),
        alpha = 1f,
    )
}

/** یک مهره‌ی سه‌بعدی: سایه، بدنه‌ی گرادیانی با نور بالا-چپ، لبه‌ی تیره و شیار داخلی */
private fun DrawScope.drawChecker(center: Offset, radius: Float, color: Color, countLabel: Int = 0) {
    // سایه‌ی نرم زیر مهره
    drawCircle(color = Color.Black.copy(alpha = 0.30f), radius = radius, center = center + Offset(0f, radius * 0.14f))
    // بدنه با نور از بالا-چپ
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.lighten(0.35f), color, color.darken(0.25f)),
            center = center + Offset(-radius * 0.3f, -radius * 0.35f),
            radius = radius * 1.6f,
        ),
        radius = radius,
        center = center,
    )
    // لبه‌ی تیره و شیار تزیینی داخلی
    drawCircle(color = color.darken(0.4f), radius = radius, center = center, style = Stroke(width = radius * 0.09f))
    drawCircle(
        color = color.darken(0.22f).copy(alpha = 0.7f),
        radius = radius * 0.62f,
        center = center,
        style = Stroke(width = radius * 0.10f),
    )
    // برق کوچک نور
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = radius * 0.16f,
        center = center + Offset(-radius * 0.34f, -radius * 0.38f),
    )
    if (countLabel > 1) {
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = radius * 1.1f
            isAntiAlias = true
            isFakeBoldText = true
            setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
        }
        drawContext.canvas.nativeCanvas.drawText(
            countLabel.toPersianDigits(),
            center.x,
            center.y + radius * 0.38f,
            paint,
        )
    }
}

/** روشن‌کردن رنگ به سمت سفید */
private fun Color.lighten(f: Float): Color = Color(
    red = red + (1f - red) * f,
    green = green + (1f - green) * f,
    blue = blue + (1f - blue) * f,
    alpha = alpha,
)

/** تیره‌کردن رنگ به سمت سیاه */
private fun Color.darken(f: Float): Color = Color(
    red = red * (1f - f),
    green = green * (1f - f),
    blue = blue * (1f - f),
    alpha = alpha,
)
