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
import com.navidabbasian.kibord.games.backgammon.engine.pipCount

// ---- پالت اتاق تاریک برگرفته از عکس مرجع — تخته در هر دو تم یک شکل است ----
private val FrameWood = Color(0xFF4A3427)
private val FrameEdge = Color(0xFF6A4C39)
private val FieldTop = Color(0xFFC98A45)
private val FieldBottom = Color(0xFFD9A35D)
private val PointLight = Color(0xFFE9C98F)
private val PointDark = Color(0xFF8E5A2B)
private val BarWood = Color(0xFF3E2B1F)
private val HingeGold = Color(0xFFD8B060)
private val HingeGoldDark = Color(0xFFA88338)
private val TrayDark = Color(0xFF1E1714)
private val PillDark = Color(0xFF1C1816)
private val CubeDark = Color(0xFF1A1512)
internal val BgCheckerWhite = Color(0xFFECECEC)
internal val BgCheckerWhiteRing = Color(0xFFCFCFCF)
internal val BgCheckerBlack = Color(0xFF2B2B2B)
internal val BgCheckerBlackRing = Color(0xFF444444)
internal val BgGoldGlow = Color(0xFFFFD56A)
internal val BgCrawfordGold = Color(0xFFF2A93B)

/** نسبت عرض به ارتفاع تخته — از عکس مرجع (۵۷۶ در ۶۸۰) */
internal const val BG_BOARD_ASPECT = 0.847f

/**
 * هندسه‌ی تخته‌ی تاریک: قاب دورتادور با حاشیه‌ی پهن بالا و پایین (جای
 * پیل‌های پیپ و سینی‌های خروج)، دو لنگه‌ی چوبی، بار وسط با لولاهای طلایی
 * و مکعب دوبل. همه‌چیز نسبتی است تا رابط بتواند تاس‌ها را هم روی همین
 * مختصات بنشاند.
 */
internal class BgBoardGeometry(val size: Size) {
    val w = size.width
    val h = size.height

    /** قاب باریک چپ و راست */
    val frameSide = w * 0.018f

    /** حاشیه‌ی پهن بالا و پایین قاب — پیل پیپ و سینی این‌جا می‌نشینند */
    val marginV = h * 0.082f

    val barW = w * 0.070f
    val colW = (w - 2f * frameSide - barW) / 12f

    val fieldTop = marginV
    val fieldBottom = h - marginV
    val fieldH = fieldBottom - fieldTop
    val pointH = fieldH * 0.44f

    /** لبه‌ی چپ بار وسط */
    val barX = frameSide + 6f * colW
    val barCx = barX + barW / 2f

    /** سینی‌های خروج: راستِ حاشیه‌ی بالا و پایین */
    val trayW = w * 0.235f
    val trayH = marginV * 0.72f
    val trayX = w - frameSide - trayW - w * 0.006f
    val topTray = Rect(trayX, (marginV - trayH) / 2f, trayX + trayW, (marginV + trayH) / 2f)
    val bottomTray = Rect(trayX, h - (marginV + trayH) / 2f, trayX + trayW, h - (marginV - trayH) / 2f)

    /** ضلع مکعب دوبل */
    val cubeS = w * 0.062f

    /** مرکز مکعب: وسط بار، یا سمتِ صاحبش (سیاه بالا، سفید پایین) */
    fun cubeCenter(owner: BgPlayer?): Offset = when (owner) {
        null -> Offset(barCx, h / 2f)
        BgPlayer.BLACK -> Offset(barCx, fieldTop + cubeS)
        BgPlayer.WHITE -> Offset(barCx, fieldBottom - cubeS)
    }

    /**
     * نگاشت شماره‌ی مطلق خانه (۱ تا ۲۴) به ستون و ردیف — تنها نقطه‌ی حقیقت:
     * ۱ تا ۱۲ ردیف پایین از راست به چپ، ۱۳ تا ۲۴ ردیف بالا از چپ به راست.
     */
    fun columnOf(abs: Int): Pair<Int, Boolean> =
        if (abs <= 12) (12 - abs) to false else (abs - 13) to true

    /** لبه‌ی چپ ستون داده‌شده */
    fun columnX(col: Int): Float = frameSide + col * colW + (if (col >= 6) barW else 0f)

    /** مرکز نیمه‌ی چپ و راست زمین — جای فرود تاس‌ها */
    fun leftHalfCx(): Float = frameSide + 3f * colW
    fun rightHalfCx(): Float = frameSide + 9f * colW + barW

    /** خانه‌ی مطلق زیر لمس — تهی یعنی بیرون از خانه‌ها */
    fun pointAt(x: Float, y: Float): Int? {
        if (y < fieldTop || y > fieldBottom) return null
        val isTop = y < h / 2f
        for (col in 0..11) {
            val left = columnX(col)
            if (x >= left && x < left + colW) {
                return if (isTop) col + 13 else 12 - col
            }
        }
        return null
    }

    /** لمس روی مکعب دوبل؟ (ناحیه‌ی کمی بزرگ‌تر از خود مکعب) */
    fun isCube(x: Float, y: Float, owner: BgPlayer?): Boolean {
        val c = cubeCenter(owner)
        val r = cubeS * 1.1f
        return x >= c.x - r && x <= c.x + r && y >= c.y - r && y <= c.y + r
    }

    /** لمس روی بار وسط (ورود از بار)؟ */
    fun isBar(x: Float, y: Float): Boolean =
        x >= barX && x < barX + barW && y >= fieldTop && y <= fieldBottom

    /** لمس روی یکی از سینی‌های خروج؟ */
    fun isTray(x: Float, y: Float): Boolean =
        (y < fieldTop || y > fieldBottom) && x >= trayX - colW / 2f
}

/**
 * تخته‌ی تاریک به سبک عکس مرجع: قاب قهوه‌ای سوخته، دو لنگه‌ی چوب گرم،
 * خانه‌های کرم/قهوه‌ای با نوک گرد، بار با لولاهای طلایی، پیل‌های پیپ،
 * سینی‌های خروج بالا و پایینِ راست، مکعب دوبل روی بار و هایلایت طلایی
 * برای مبدأ/مقصدهای قانونی.
 */
@Composable
fun BackgammonBoard(
    state: BgState,
    sourcesAbs: Set<Int>,
    selectedAbs: Int?,
    destsAbs: Set<Int>,
    offIsDest: Boolean,
    cubeValue: Int,
    cubeOwner: BgPlayer?,
    crawford: Boolean,
    cubeGlow: Boolean,
    whiteColor: Color = BgCheckerWhite,
    blackColor: Color = BgCheckerBlack,
    onTapPoint: (Int) -> Unit,
    onTapEntry: () -> Unit,
    onTapOff: () -> Unit,
    onTapCube: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // مهره‌ی زده از چوب وسط برمی‌گردد؛ واردنشده‌ی نرد هلندی از سینیِ خودش می‌آید
    val barEntry = state.phase == BgPhase.MOVING && state.turn != null && state.bar(state.turn!!) > 0
    val trayEntry = state.phase == BgPhase.MOVING && state.turn != null &&
        state.bar(state.turn!!) == 0 && state.isEntering(state.turn!!)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(BG_BOARD_ASPECT)
            .pointerInput(cubeOwner) {
                detectTapGestures { offset ->
                    val geo = BgBoardGeometry(Size(size.width.toFloat(), size.height.toFloat()))
                    when {
                        geo.isCube(offset.x, offset.y, cubeOwner) -> onTapCube()
                        geo.isTray(offset.x, offset.y) -> if (trayEntry) onTapEntry() else onTapOff()
                        geo.isBar(offset.x, offset.y) -> onTapEntry()
                        else -> geo.pointAt(offset.x, offset.y)?.let(onTapPoint)
                    }
                }
            },
    ) {
        val geo = BgBoardGeometry(size)
        drawFrameAndField(geo)
        for (abs in 1..24) drawPointTriangle(geo, abs, destsAbs.contains(abs))
        drawBar(geo, state, whiteColor, blackColor, barEntry)
        drawPipPills(geo, state)
        drawTrays(geo, state, offIsDest, trayEntry, whiteColor, blackColor)
        drawCube(geo, cubeValue, cubeOwner, crawford, cubeGlow)
        for (abs in 1..24) {
            drawCheckers(geo, abs, state, sourcesAbs.contains(abs), selectedAbs == abs, whiteColor, blackColor)
        }
    }
}

/** قاب تیره با لبه‌ی داخلی روشن‌تر و دو لنگه‌ی چوب گرم */
private fun DrawScope.drawFrameAndField(geo: BgBoardGeometry) {
    drawRoundRect(color = FrameWood, cornerRadius = CornerRadius(18f, 18f))
    drawRoundRect(
        color = FrameEdge,
        topLeft = Offset(2f, 2f),
        size = Size(size.width - 4f, size.height - 4f),
        cornerRadius = CornerRadius(16f, 16f),
        style = Stroke(width = 1.2f.dp.toPx()),
    )
    // دو لنگه‌ی زمین با گرادیان چوب گرم
    val halves = listOf(
        Rect(geo.frameSide, geo.fieldTop, geo.barX, geo.fieldBottom),
        Rect(geo.barX + geo.barW, geo.fieldTop, size.width - geo.frameSide, geo.fieldBottom),
    )
    halves.forEach { r ->
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(FieldTop, FieldBottom), startY = r.top, endY = r.bottom),
            topLeft = Offset(r.left, r.top),
            size = Size(r.width, r.height),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.20f),
            topLeft = Offset(r.left, r.top),
            size = Size(r.width, r.height),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 1.5f.dp.toPx()),
        )
    }
}

/** یک خانه‌ی بلند با نوک گرد — کرم و قهوه‌ای یک‌درمیان؛ مقصد مجاز طلایی می‌درخشد */
private fun DrawScope.drawPointTriangle(geo: BgBoardGeometry, abs: Int, isDest: Boolean) {
    val (col, isTop) = geo.columnOf(abs)
    val left = geo.columnX(col)
    val base = if (isTop) geo.fieldTop else geo.fieldBottom
    val apex = if (isTop) geo.fieldTop + geo.pointH else geo.fieldBottom - geo.pointH
    val cx = left + geo.colW / 2f
    val tipR = geo.colW * 0.10f
    val path = Path().apply {
        moveTo(left + 2f, base)
        lineTo(left + geo.colW - 2f, base)
        lineTo(cx, apex)
        close()
    }
    val light = abs % 2 == 0
    val color = if (light) PointLight else PointDark
    drawPath(
        path,
        Brush.verticalGradient(
            colors = listOf(color, if (light) PointLight.darken(0.10f) else PointDark.darken(0.15f)),
            startY = base,
            endY = apex,
        ),
    )
    // نوک نرم و گرد
    drawCircle(
        color = if (light) PointLight.darken(0.10f) else PointDark.darken(0.15f),
        radius = tipR,
        center = Offset(cx, apex + if (isTop) -tipR * 0.3f else tipR * 0.3f),
    )
    drawPath(path, Color.Black.copy(alpha = 0.10f), style = Stroke(width = 1.dp.toPx()))
    if (isDest) {
        drawPath(path, BgGoldGlow.copy(alpha = 0.35f))
        drawPath(path, BgGoldGlow, style = Stroke(width = 2.5f.dp.toPx()))
    }
}

/** بار وسط: چوب تیره با دو لولای طلایی — مهره‌های زده‌شده همین‌جا می‌نشینند */
private fun DrawScope.drawBar(
    geo: BgBoardGeometry,
    state: BgState,
    whiteColor: Color,
    blackColor: Color,
    entrySelectable: Boolean,
) {
    drawRect(
        color = BarWood,
        topLeft = Offset(geo.barX, geo.fieldTop),
        size = Size(geo.barW, geo.fieldH),
    )
    drawRect(
        color = Color.Black.copy(alpha = 0.25f),
        topLeft = Offset(geo.barX, geo.fieldTop),
        size = Size(geo.barW, geo.fieldH),
        style = Stroke(width = 1.dp.toPx()),
    )
    // دو لولای طلایی مثل عکس: بالای و پایین بار
    listOf(size.height * 0.243f, size.height * 0.765f).forEach { cy ->
        val hw = geo.barW * 0.38f
        val hh = size.height * 0.058f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(HingeGold, HingeGoldDark), startY = cy - hh / 2f, endY = cy + hh / 2f),
            topLeft = Offset(geo.barCx - hw / 2f, cy - hh / 2f),
            size = Size(hw, hh),
            cornerRadius = CornerRadius(5f, 5f),
        )
        listOf(cy - hh * 0.28f, cy + hh * 0.28f).forEach { sy ->
            drawCircle(HingeGoldDark.darken(0.3f), radius = 1.4f.dp.toPx(), center = Offset(geo.barCx, sy))
        }
    }
    // فقط مهره‌های زده‌شده روی چوب وسط می‌نشینند؛ واردنشده‌های نرد هلندی
    // در سینیِ خروجِ خودِ بازیکن منتظرند و از همان‌جا وارد می‌شوند.
    // سیاه بالای مرکز، سفید پایین — به سمت وسط پشته می‌شوند
    val r = minOf(geo.barW * 0.44f, geo.fieldH / 14f)
    val blackWaiting = state.barBlack
    val whiteWaiting = state.barWhite
    if (blackWaiting > 0) {
        val shown = minOf(blackWaiting, 3)
        for (i in 0 until shown) {
            drawChecker(
                center = Offset(geo.barCx, size.height * 0.36f - i * 2f * r * 0.62f),
                radius = r,
                color = blackColor,
                countLabel = if (i == shown - 1 && blackWaiting > 3) blackWaiting else 0,
            )
        }
    }
    if (whiteWaiting > 0) {
        val shown = minOf(whiteWaiting, 3)
        for (i in 0 until shown) {
            drawChecker(
                center = Offset(geo.barCx, size.height * 0.64f + i * 2f * r * 0.62f),
                radius = r,
                color = whiteColor,
                countLabel = if (i == shown - 1 && whiteWaiting > 3) whiteWaiting else 0,
            )
        }
    }
    // وقتی ورود اجباری است، بار به‌عنوان مبدأ لمس‌شدنی طلایی می‌درخشد
    if (entrySelectable) {
        drawRect(
            color = BgGoldGlow.copy(alpha = 0.20f),
            topLeft = Offset(geo.barX, geo.fieldTop),
            size = Size(geo.barW, geo.fieldH),
        )
        drawRect(
            color = BgGoldGlow,
            topLeft = Offset(geo.barX, geo.fieldTop),
            size = Size(geo.barW, geo.fieldH),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/** پیل‌های شمار پیپ: بالا-چپ برای بازیکن بالا (سیاه)، پایین-چپ برای سفید */
private fun DrawScope.drawPipPills(geo: BgBoardGeometry, state: BgState) {
    fun pill(cy: Float, pips: Int) {
        val text = pips.toPersianDigits()
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = geo.marginV * 0.42f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val tw = paint.measureText(text)
        val pw = tw + geo.marginV * 0.7f
        val ph = geo.marginV * 0.62f
        val left = geo.frameSide + geo.colW * 0.45f
        drawRoundRect(
            color = PillDark,
            topLeft = Offset(left, cy - ph / 2f),
            size = Size(pw, ph),
            cornerRadius = CornerRadius(ph / 2f, ph / 2f),
        )
        drawContext.canvas.nativeCanvas.drawText(
            text,
            left + pw / 2f,
            cy + paint.textSize * 0.35f,
            paint,
        )
    }
    pill(geo.marginV / 2f, state.pipCount(BgPlayer.BLACK))
    pill(size.height - geo.marginV / 2f, state.pipCount(BgPlayer.WHITE))
}

/** سینی‌های خروج: مستطیل تیره که مهره‌های خارج‌شده مثل تیغه‌های باریک تویش ردیف می‌شوند */
private fun DrawScope.drawTrays(
    geo: BgBoardGeometry,
    state: BgState,
    offIsDest: Boolean,
    trayEntry: Boolean,
    whiteColor: Color,
    blackColor: Color,
) {
    val mover = state.turn

    fun drawTray(rect: Rect, count: Int, color: Color, highlight: Boolean) {
        drawRoundRect(
            color = TrayDark,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = CornerRadius(8f, 8f),
        )
        // تیغه‌های باریک: هر مهره‌ی خارج‌شده یک تیغه‌ی ایستاده، از راست به چپ
        val inset = rect.height * 0.18f
        val slabW = (rect.width - 2f * inset) / 16.5f
        for (i in 0 until minOf(count, 15)) {
            val x = rect.right - inset - (i + 1) * slabW * 1.1f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, rect.top + inset),
                size = Size(slabW, rect.height - 2f * inset),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
        if (count > 0) {
            // قرص تیره پشت شماره تا روی تیغه‌های سفید هم خوانا بماند
            drawCircle(
                color = TrayDark.copy(alpha = 0.92f),
                radius = rect.height * 0.42f,
                center = Offset(rect.left + rect.height * 0.42f, rect.center.y),
            )
            val paint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = rect.height * 0.48f
                isAntiAlias = true
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                count.toPersianDigits(),
                rect.left + rect.height * 0.42f,
                rect.center.y + paint.textSize * 0.35f,
                paint,
            )
        }
        if (highlight) {
            drawRoundRect(
                color = BgGoldGlow.copy(alpha = 0.25f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(8f, 8f),
            )
            drawRoundRect(
                color = BgGoldGlow,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 2.5f.dp.toPx()),
            )
        }
    }

    // سیاه خانه‌اش بالاست پس سینی‌اش بالایی است؛ سفید پایین.
    // شمار سینی = خارج‌شده‌های آخر بازی + واردنشده‌های ابتدای نرد هلندی
    // (این دو هیچ‌وقت هم‌زمان ناصفر نیستند). وقتی نوبتِ واردکردن است،
    // سینیِ همان بازیکن مثل مبدأ طلایی می‌درخشد.
    drawTray(
        geo.topTray,
        state.borneOffBlack + state.offBoardBlack,
        blackColor.lighten(0.12f),
        (offIsDest || trayEntry) && mover == BgPlayer.BLACK,
    )
    drawTray(
        geo.bottomTray,
        state.borneOffWhite + state.offBoardWhite,
        whiteColor,
        (offIsDest || trayEntry) && mover == BgPlayer.WHITE,
    )
}

/** مکعب دوبل روی بار: وسط وقتی مال کسی نیست، سمت صاحبش وقتی گرفته شده */
private fun DrawScope.drawCube(
    geo: BgBoardGeometry,
    cubeValue: Int,
    cubeOwner: BgPlayer?,
    crawford: Boolean,
    cubeGlow: Boolean,
) {
    val c = geo.cubeCenter(cubeOwner)
    val s = geo.cubeS
    val alpha = if (crawford) 0.45f else 1f
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f * alpha),
        topLeft = Offset(c.x - s / 2f + 1.5f, c.y - s / 2f + 2.5f),
        size = Size(s, s),
        cornerRadius = CornerRadius(s * 0.2f, s * 0.2f),
    )
    drawRoundRect(
        color = CubeDark.copy(alpha = alpha),
        topLeft = Offset(c.x - s / 2f, c.y - s / 2f),
        size = Size(s, s),
        cornerRadius = CornerRadius(s * 0.2f, s * 0.2f),
    )
    if (cubeGlow) {
        drawRoundRect(
            color = BgGoldGlow,
            topLeft = Offset(c.x - s / 2f, c.y - s / 2f),
            size = Size(s, s),
            cornerRadius = CornerRadius(s * 0.2f, s * 0.2f),
            style = Stroke(width = 1.5f.dp.toPx()),
        )
    }
    val shown = if (cubeValue <= 1) 64 else cubeValue
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb((255 * alpha).toInt(), 255, 255, 255)
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = s * 0.48f
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        shown.toPersianDigits(),
        c.x,
        c.y + paint.textSize * 0.35f,
        paint,
    )
    // برچسب ریز «کرافورد»: این دست دوبل ندارد
    if (crawford) {
        val tag = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0xF2, 0xA9, 0x3B)
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = s * 0.34f
            isAntiAlias = true
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.drawText("کرافورد", c.x, c.y + s * 0.95f, tag)
    }
}

/** مهره‌های یک خانه با پشته‌ی حداکثر ۵تایی و برچسب تعداد؛ مبدأ/انتخاب طلایی می‌درخشد */
private fun DrawScope.drawCheckers(
    geo: BgBoardGeometry,
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
    val r = geo.colW * 0.42f
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
                color = BgGoldGlow.copy(alpha = if (isSelected) 1f else 0.8f),
                radius = r + 2.5f.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = if (isSelected) 3.5f.dp.toPx() else 2.5f.dp.toPx()),
            )
        }
    }
}

/**
 * یک مهره به سبک عکس مرجع: سایه‌ی نرم، بدنه، حلقه‌ی هم‌مرکز داخلی
 * و برق نور بالا-چپ (برای مهره‌ی سیاه پررنگ‌تر تا حجم بگیرد).
 */
private fun DrawScope.drawChecker(center: Offset, radius: Float, color: Color, countLabel: Int = 0) {
    val isDarkChecker = (color.red + color.green + color.blue) / 3f < 0.5f
    val ring = if (isDarkChecker) BgCheckerBlackRing else BgCheckerWhiteRing
    // سایه‌ی نرم زیر مهره
    drawCircle(color = Color.Black.copy(alpha = 0.30f), radius = radius, center = center + Offset(0f, radius * 0.13f))
    // بدنه با نور ملایم از بالا-چپ
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.lighten(if (isDarkChecker) 0.16f else 0.10f), color, color.darken(0.12f)),
            center = center + Offset(-radius * 0.3f, -radius * 0.35f),
            radius = radius * 1.6f,
        ),
        radius = radius,
        center = center,
    )
    // لبه و حلقه‌ی هم‌مرکز داخلی
    drawCircle(color = color.darken(0.25f), radius = radius, center = center, style = Stroke(width = radius * 0.07f))
    drawCircle(color = ring, radius = radius * 0.62f, center = center, style = Stroke(width = radius * 0.09f))
    // برق کوچک نور بالا-چپ
    drawCircle(
        color = Color.White.copy(alpha = if (isDarkChecker) 0.22f else 0.45f),
        radius = radius * 0.15f,
        center = center + Offset(-radius * 0.36f, -radius * 0.40f),
    )
    if (countLabel > 1) {
        val paint = android.graphics.Paint().apply {
            this.color = if (isDarkChecker) android.graphics.Color.WHITE else android.graphics.Color.rgb(0x2B, 0x2B, 0x2B)
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = radius * 1.05f
            isAntiAlias = true
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            countLabel.toPersianDigits(),
            center.x,
            center.y + radius * 0.36f,
            paint,
        )
    }
}

/** روشن‌کردن رنگ به سمت سفید */
internal fun Color.lighten(f: Float): Color = Color(
    red = red + (1f - red) * f,
    green = green + (1f - green) * f,
    blue = blue + (1f - blue) * f,
    alpha = alpha,
)

/** تیره‌کردن رنگ به سمت سیاه */
internal fun Color.darken(f: Float): Color = Color(
    red = red * (1f - f),
    green = green * (1f - f),
    blue = blue * (1f - f),
    alpha = alpha,
)
