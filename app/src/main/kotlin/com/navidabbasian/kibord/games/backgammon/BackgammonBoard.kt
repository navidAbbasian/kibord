package com.navidabbasian.kibord.games.backgammon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgState

// ---- رنگ‌های چوبی ثابت صفحه — تخته‌ی فیزیکی در هر دو تم یک شکل است ----
private val FrameColor = Color(0xFF5E3A1E)
private val FieldColor = Color(0xFFB9834C)
private val TriangleDark = Color(0xFF8A5A2C)
private val TriangleLight = Color(0xFFE5C08D)
private val BarColor = Color(0xFF4C2F17)
private val TrayColor = Color(0xFF3F2712)
private val HighlightSource = Color(0xFFFFE082)
private val HighlightDest = Color(0xFF7CE87C)

/** هندسه‌ی صفحه: عرض سینی خروج، عرض بار و عرض هر ستون */
private class BoardGeometry(val size: Size) {
    val trayW = size.width * 0.085f
    val barW = size.width * 0.075f
    val colW = (size.width - trayW - barW) / 12f
    val pointH = size.height * 0.40f

    /**
     * نگاشت شماره‌ی مطلق خانه (۱ تا ۲۴) به صفحه — تنها نقطه‌ی حقیقتِ چیدمان:
     * خانه‌های ۱ تا ۱۲ ردیف پایین از راست به چپ و ۱۳ تا ۲۴ ردیف بالا از چپ به راست.
     * ستون‌ها ۰ تا ۱۱ از چپ‌اند و بار بین ستون ۵ و ۶ می‌نشیند.
     */
    fun columnOf(abs: Int): Pair<Int, Boolean> =
        if (abs <= 12) (12 - abs) to false else (abs - 13) to true

    /** لبه‌ی چپ ستون داده‌شده روی بوم */
    fun columnX(col: Int): Float = trayW + col * colW + (if (col >= 6) barW else 0f)

    /** خانه‌ی مطلق زیر مختصات لمس‌شده — null یعنی بیرون از خانه‌ها */
    fun pointAt(x: Float, y: Float): Int? {
        if (x < trayW) return null
        val isTop = y < size.height / 2f
        for (col in 0..11) {
            val left = columnX(col)
            if (x >= left && x < left + colW) {
                return if (isTop) col + 13 else 12 - col
            }
        }
        return null
    }

    /** آیا لمس روی نوار بار بوده؟ */
    fun isBar(x: Float): Boolean {
        val barLeft = trayW + 6 * colW
        return x >= barLeft && x < barLeft + barW
    }

    /** آیا لمس روی سینی خروج بوده؟ */
    fun isTray(x: Float): Boolean = x < trayW
}

/**
 * صفحه‌ی تخته‌نرد با بوم کامپوز: ۲۴ خانه‌ی مثلثی در دو ردیف با بار وسط،
 * سینی مهره‌های خارج‌شده در چپ، مهره‌های دایره‌ای با برچسب تعداد،
 * و هایلایت مبدأ و مقصدهای قانونی.
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
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
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
        drawFrame(geo)
        for (abs in 1..24) drawPointTriangle(geo, abs, destsAbs.contains(abs))
        drawBar(geo, state, whiteColor, blackColor)
        drawTray(geo, state, offIsDest, whiteColor, blackColor)
        for (abs in 1..24) {
            drawCheckers(geo, abs, state, sourcesAbs.contains(abs), selectedAbs == abs, whiteColor, blackColor)
        }
    }
}

private fun DrawScope.drawFrame(geo: BoardGeometry) {
    drawRoundRect(color = FrameColor, cornerRadius = CornerRadius(24f, 24f))
    drawRect(
        color = FieldColor,
        topLeft = Offset(geo.trayW, 0f),
        size = Size(size.width - geo.trayW, size.height),
    )
}

private fun DrawScope.drawPointTriangle(geo: BoardGeometry, abs: Int, isDest: Boolean) {
    val (col, isTop) = geo.columnOf(abs)
    val left = geo.columnX(col)
    val base = if (isTop) 0f else size.height
    val apex = if (isTop) geo.pointH else size.height - geo.pointH
    val path = Path().apply {
        moveTo(left, base)
        lineTo(left + geo.colW, base)
        lineTo(left + geo.colW / 2f, apex)
        close()
    }
    drawPath(path, if (abs % 2 == 0) TriangleLight else TriangleDark)
    if (isDest) {
        drawPath(path, HighlightDest.copy(alpha = 0.45f))
        drawPath(path, HighlightDest, style = Stroke(width = 3.dp.toPx()))
    }
}

private fun DrawScope.drawBar(geo: BoardGeometry, state: BgState, whiteColor: Color, blackColor: Color) {
    val barLeft = geo.trayW + 6 * geo.colW
    drawRect(color = BarColor, topLeft = Offset(barLeft, 0f), size = Size(geo.barW, size.height))
    val r = geo.barW * 0.42f
    val cx = barLeft + geo.barW / 2f
    // مهره‌های بارِ سفید پایین وسط و سیاه بالای وسط
    if (state.barWhite > 0) {
        drawChecker(Offset(cx, size.height * 0.68f), r, whiteColor, state.barWhite)
    }
    if (state.barBlack > 0) {
        drawChecker(Offset(cx, size.height * 0.32f), r, blackColor, state.barBlack)
    }
}

private fun DrawScope.drawTray(
    geo: BoardGeometry,
    state: BgState,
    offIsDest: Boolean,
    whiteColor: Color,
    blackColor: Color,
) {
    drawRect(color = TrayColor, topLeft = Offset.Zero, size = Size(geo.trayW, size.height))
    if (offIsDest) {
        drawRect(
            color = HighlightDest.copy(alpha = 0.35f),
            topLeft = Offset.Zero,
            size = Size(geo.trayW, size.height),
        )
        drawRect(
            color = HighlightDest,
            topLeft = Offset.Zero,
            size = Size(geo.trayW, size.height),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
    // خارج‌شده‌ها به شکل تخته‌های خوابیده: سیاه بالا و سفید پایین
    val slabW = geo.trayW * 0.72f
    val slabH = size.height * 0.022f
    val gap = slabH * 1.5f
    val cx = geo.trayW / 2f
    repeat(state.borneOffBlack) { i ->
        drawRoundRect(
            color = blackColor,
            topLeft = Offset(cx - slabW / 2f, 10f + i * gap),
            size = Size(slabW, slabH),
            cornerRadius = CornerRadius(4f, 4f),
        )
    }
    repeat(state.borneOffWhite) { i ->
        drawRoundRect(
            color = whiteColor,
            topLeft = Offset(cx - slabW / 2f, size.height - 10f - slabH - i * gap),
            size = Size(slabW, slabH),
            cornerRadius = CornerRadius(4f, 4f),
        )
    }
}

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
    val r = minOf(geo.colW * 0.44f, size.height / 11.5f)
    val color = if (owner == BgPlayer.WHITE) whiteColor else blackColor
    val shown = minOf(point.count, 5)
    for (i in 0 until shown) {
        val cy = if (isTop) r + i * 2f * r * 0.92f else size.height - r - i * 2f * r * 0.92f
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

/** یک مهره‌ی دایره‌ای با حلقه‌ی داخلی و در صورت نیاز برچسب تعداد فارسی */
private fun DrawScope.drawChecker(center: Offset, radius: Float, color: Color, countLabel: Int = 0) {
    drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = radius, center = center + Offset(0f, 2f))
    drawCircle(color = color, radius = radius, center = center)
    drawCircle(color = Color.White.copy(alpha = 0.45f), radius = radius * 0.62f, center = center, style = Stroke(width = radius * 0.14f))
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
