package com.navidabbasian.kibord.games.ludo.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import com.navidabbasian.kibord.games.ludo.LUDO_HOP_MS
import com.navidabbasian.kibord.games.ludo.LUDO_POOF_MS
import com.navidabbasian.kibord.games.ludo.LudoMoveAnim
import com.navidabbasian.kibord.games.ludo.engine.GOAL
import com.navidabbasian.kibord.games.ludo.engine.LudoCell
import com.navidabbasian.kibord.games.ludo.engine.LudoColor
import com.navidabbasian.kibord.games.ludo.engine.LudoGeometry
import com.navidabbasian.kibord.games.ludo.engine.LudoState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** رنگ مهره و پایگاه هر رنگ — شاد و خوانا روی صفحه‌ی کرم */
fun LudoColor.paint(): Color = when (this) {
    LudoColor.RED -> Color(0xFFE8505B)
    LudoColor.GREEN -> Color(0xFF3FB37A)
    LudoColor.YELLOW -> Color(0xFFF4B53A)
    LudoColor.BLUE -> Color(0xFF4C8DF5)
}

private val BoardCream = Color(0xFFFBF5E8)
private val BoardCreamDark = Color(0xFFEFE5D0)
private val GridLine = Color(0xFFB9AE98)
private val BoardFrame = Color(0xFF5B4636)

/** هندسه‌ی پیکسلی صفحه: هر خانه‌ی ۱۵×۱۵ چند پیکسل است */
private class BoardPx(val size: Float) {
    val cell = size / LudoGeometry.GRID
    fun center(c: LudoCell): Offset = Offset((c.col + 0.5f) * cell, (c.row + 0.5f) * cell)
    fun topLeft(r: Int, c: Int): Offset = Offset(c * cell, r * cell)
}

/** یک مهره‌ی کشیده‌شده: موقعیت، اندازه و این‌که مال کیست */
private data class DrawnToken(
    val color: LudoColor,
    val token: Int,
    val center: Offset,
    val scale: Float,
)

/** هرچه لمس برای پیدا کردن مهره لازم دارد */
private data class TapCtx(val game: LudoState, val legal: Set<Int>, val skip: Set<Pair<LudoColor, Int>>)

/**
 * چینش مهره‌ها روی صفحه: مهره‌های هم‌خانه خوشه می‌شوند و کوچک‌تر کشیده می‌شوند.
 * مهره‌ی در حال پرش و مهره‌ی زده‌شده‌ی در حال «پوف» از چینش معمولی کنار می‌روند.
 */
private fun layoutTokens(
    game: LudoState,
    px: BoardPx,
    skip: Set<Pair<LudoColor, Int>>,
): List<DrawnToken> {
    val result = ArrayList<DrawnToken>(16)
    // گروه‌بندی بر اساس خانه‌ی واقعی (پایگاه و هدف تک‌نفره‌اند)
    val groups = LinkedHashMap<LudoCell, MutableList<Pair<LudoColor, Int>>>()
    LudoColor.entries.forEach { c ->
        if (!game.isActive(c)) return@forEach
        game.tokensOf(c).forEachIndexed { i, step ->
            if (c to i in skip) return@forEachIndexed
            val cell = LudoGeometry.cellOf(c, i, step)
            groups.getOrPut(cell) { mutableListOf() }.add(c to i)
        }
    }
    groups.forEach { (cell, members) ->
        val center = px.center(cell)
        val step = game.tokensOf(members[0].first)[members[0].second]
        val baseScale = if (step == GOAL) 0.62f else 1f
        val n = members.size
        val offsets: List<Offset> = when {
            n == 1 -> listOf(Offset.Zero)
            n == 2 -> listOf(Offset(-0.2f, 0.08f), Offset(0.2f, -0.08f))
            n == 3 -> listOf(Offset(-0.22f, 0.14f), Offset(0.22f, 0.14f), Offset(0f, -0.2f))
            else -> List(n) { k ->
                val ang = -PI.toFloat() / 2 + k * (2f * PI.toFloat() / n)
                Offset(cos(ang) * 0.24f, sin(ang) * 0.24f)
            }
        }
        val scale = baseScale * when (n) {
            1 -> 1f
            2 -> 0.8f
            3 -> 0.72f
            else -> 0.62f
        }
        members.forEachIndexed { k, (c, i) ->
            result += DrawnToken(c, i, center + offsets[k] * px.cell, scale)
        }
    }
    return result
}

/**
 * صفحه‌ی منچ: نقاشی کامل با Canvas — پایگاه‌ها، مسیر، ستون‌های رنگی، مرکز، مهره‌ها،
 * پرش انیمیشنی مهره‌ی در حال حرکت، «پوف» مهره‌ی زده‌شده و حلقه‌ی تپنده‌ی مهره‌های مجاز.
 */
@Composable
fun LudoBoard(
    game: LudoState,
    anim: LudoMoveAnim?,
    legalTokens: Set<Int>,
    autoToken: Int?,
    onTapToken: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }
    val poof = remember { Animatable(1f) }
    var shownNonce by remember { mutableIntStateOf(-1) }

    // انیمیشن حرکت: از جایی که مانده ادامه می‌دهد (بعد از چرخش صفحه از نو پخش نمی‌شود)
    LaunchedEffect(anim?.nonce) {
        val a = anim ?: return@LaunchedEffect
        val hopTotal = a.move.hops * LUDO_HOP_MS
        val elapsed = SystemClock.elapsedRealtime() - a.startedAt
        if (elapsed >= hopTotal + (if (a.captured != null) LUDO_POOF_MS else 0L)) {
            progress.snapTo(1f)
            poof.snapTo(1f)
            shownNonce = a.nonce
            return@LaunchedEffect
        }
        if (elapsed < hopTotal) {
            progress.snapTo((elapsed.toFloat() / hopTotal).coerceIn(0f, 1f))
            poof.snapTo(0f)
            shownNonce = a.nonce
            progress.animateTo(1f, tween((hopTotal - elapsed).toInt().coerceAtLeast(1), easing = LinearEasing))
        } else {
            progress.snapTo(1f)
            poof.snapTo(((elapsed - hopTotal).toFloat() / LUDO_POOF_MS).coerceIn(0f, 1f))
            shownNonce = a.nonce
        }
        if (a.captured != null) {
            poof.animateTo(1f, tween((LUDO_POOF_MS * (1f - poof.value)).toInt().coerceAtLeast(1), easing = LinearEasing))
        } else {
            poof.snapTo(1f)
        }
    }

    // تپش حلقه‌ی مهره‌های مجاز
    val pulseTransition = rememberInfiniteTransition(label = "ludo_pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "ludo_pulse_t",
    )

    val animActive = anim != null && (shownNonce != anim.nonce || progress.value < 1f)
    val poofActive = anim?.captured != null && (shownNonce != anim.nonce || poof.value < 1f)
    val movingKey = anim?.let { it.move.color to it.move.token }
    val capturedKey = anim?.captured?.let { it.color to it.token }

    val skip = buildSet {
        if (animActive) movingKey?.let { add(it) }
        if (animActive || poofActive) capturedKey?.let { add(it) }
    }
    val tapCtx = rememberUpdatedState(TapCtx(game, legalTokens, skip))

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ctx = tapCtx.value
                    val px = BoardPx(size.width.toFloat())
                    val tokens = layoutTokens(ctx.game, px, ctx.skip)
                    val hit = tokens
                        .filter { it.color == ctx.game.turn && it.token in ctx.legal }
                        .minByOrNull { hypot(it.center.x - offset.x, it.center.y - offset.y) }
                        ?: return@detectTapGestures
                    if (hypot(hit.center.x - offset.x, hit.center.y - offset.y) <= px.cell * 1.1f) {
                        onTapToken(hit.token)
                    }
                }
            },
    ) {
        val px = BoardPx(size.minDimension)
        drawBoardBase(px, game)
        drawTrackAndColumns(px, game)
        drawCenter(px)

        val tokens = layoutTokens(game, px, skip)

        // مهره‌های ساکن
        tokens.forEach { t ->
            val isTurn = t.color == game.turn
            val legal = isTurn && t.token in legalTokens && !animActive
            val auto = legal && autoToken == t.token
            if (legal) {
                val ring = px.cell * (0.48f + 0.1f * pulse) * t.scale
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f + 0.35f * pulse),
                    radius = ring,
                    center = t.center,
                    style = Stroke(width = px.cell * (if (auto) 0.11f else 0.07f)),
                )
                drawCircle(
                    color = t.color.paint().copy(alpha = 0.25f + 0.2f * pulse),
                    radius = ring * 1.15f,
                    center = t.center,
                )
            }
            drawToken(t.color, t.center, px.cell * 0.38f * t.scale, dim = false)
        }

        // مهره‌ی زده‌شده: تا مهره‌ی مهاجم برسد سر جایش است، بعد «پوف» می‌شود
        anim?.captured?.let { cap ->
            if (capturedKey != null && capturedKey in skip) {
                val center = px.center(LudoGeometry.track[cap.trackIndex])
                if (animActive) {
                    drawToken(cap.color, center, px.cell * 0.38f, dim = false)
                } else if (poofActive) {
                    drawPoof(cap.color, center, px.cell, poof.value)
                }
            }
        }

        // مهره‌ی در حال پرش
        if (anim != null && animActive && movingKey != null) {
            val path = LudoGeometry.pathOf(anim.move).map { px.center(it) }
            val segs = (path.size - 1).coerceAtLeast(1)
            val p = if (shownNonce != anim.nonce) 0f else progress.value
            val raw = p * segs
            val seg = raw.toInt().coerceIn(0, segs - 1)
            val t = (raw - seg).coerceIn(0f, 1f)
            val a = path[seg]
            val b = path[(seg + 1).coerceAtMost(path.size - 1)]
            val hop = sin(t * PI.toFloat())
            val pos = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t - hop * px.cell * 0.45f)
            // سایه روی زمین
            drawCircle(
                color = Color.Black.copy(alpha = 0.18f * (1f - 0.5f * hop)),
                radius = px.cell * 0.3f * (1f - 0.3f * hop),
                center = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t + px.cell * 0.08f),
            )
            drawToken(anim.move.color, pos, px.cell * 0.38f * (1f + 0.25f * hop), dim = false)
        }
    }
}

/** صفحه‌ی کرم، قاب گرد، چهار پایگاه رنگی با حیاط سفید و چهار جایگاه */
private fun DrawScope.drawBoardBase(px: BoardPx, game: LudoState) {
    val s = px.size
    val corner = CornerRadius(px.cell * 0.9f, px.cell * 0.9f)
    // سایه و قاب
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.22f),
        topLeft = Offset(0f, px.cell * 0.12f),
        size = Size(s, s),
        cornerRadius = corner,
    )
    drawRoundRect(
        brush = Brush.linearGradient(listOf(BoardCream, BoardCreamDark), start = Offset.Zero, end = Offset(s, s)),
        size = Size(s, s),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = BoardFrame.copy(alpha = 0.55f),
        size = Size(s, s),
        cornerRadius = corner,
        style = Stroke(width = px.cell * 0.12f),
    )

    LudoColor.entries.forEach { c ->
        val o = LudoGeometry.baseOrigin(c)
        val tl = px.topLeft(o.row.toInt(), o.col.toInt())
        val paint = c.paint()
        val active = game.isActive(c)
        val fill = if (active) paint else lerp(paint, BoardCreamDark, 0.7f)
        val rr = CornerRadius(px.cell * 0.7f, px.cell * 0.7f)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(lerp(fill, Color.White, 0.12f), lerp(fill, Color.Black, 0.1f)),
                start = tl,
                end = tl + Offset(px.cell * 6, px.cell * 6),
            ),
            topLeft = tl,
            size = Size(px.cell * 6, px.cell * 6),
            cornerRadius = rr,
        )
        // حیاط سفید
        val yard = tl + Offset(px.cell, px.cell)
        drawRoundRect(
            color = Color.White.copy(alpha = if (active) 0.92f else 0.6f),
            topLeft = yard,
            size = Size(px.cell * 4, px.cell * 4),
            cornerRadius = CornerRadius(px.cell * 0.55f, px.cell * 0.55f),
        )
        // چهار جایگاه
        for (i in 0 until 4) {
            val center = px.center(LudoGeometry.baseSlot(c, i))
            drawCircle(
                color = lerp(fill, Color.White, 0.55f),
                radius = px.cell * 0.5f,
                center = center,
            )
            drawCircle(
                color = lerp(fill, Color.Black, 0.15f).copy(alpha = 0.55f),
                radius = px.cell * 0.5f,
                center = center,
                style = Stroke(width = px.cell * 0.06f),
            )
        }
    }
}

/** خانه‌های مسیر، خانه‌های شروع و ستون‌های رنگی خانه */
private fun DrawScope.drawTrackAndColumns(px: BoardPx, game: LudoState) {
    val cellSize = Size(px.cell, px.cell)
    val stroke = Stroke(width = (px.cell * 0.045f).coerceAtLeast(1f))
    val inset = px.cell * 0.04f
    fun drawCell(cell: LudoCell, fill: Color) {
        val tl = px.topLeft(cell.row.toInt(), cell.col.toInt())
        drawRoundRect(
            color = fill,
            topLeft = tl + Offset(inset, inset),
            size = Size(cellSize.width - inset * 2, cellSize.height - inset * 2),
            cornerRadius = CornerRadius(px.cell * 0.18f, px.cell * 0.18f),
        )
        drawRoundRect(
            color = GridLine.copy(alpha = 0.55f),
            topLeft = tl + Offset(inset, inset),
            size = Size(cellSize.width - inset * 2, cellSize.height - inset * 2),
            cornerRadius = CornerRadius(px.cell * 0.18f, px.cell * 0.18f),
            style = stroke,
        )
    }

    LudoGeometry.track.forEachIndexed { index, cell ->
        val startOf = LudoColor.entries.firstOrNull { it.startIndex == index }
        val fill = if (startOf != null) lerp(startOf.paint(), Color.White, 0.12f) else Color.White
        drawCell(cell, fill)
        if (startOf != null) {
            // فلش جهت حرکت روی خانه‌ی شروع
            drawStartArrow(px, cell, startOf)
            if (game.rules.safeStartSquares) {
                drawStar(px.center(cell) + Offset(0f, px.cell * 0.02f), px.cell * 0.22f, Color.White.copy(alpha = 0.95f))
            }
        }
    }
    // ستون‌های خانه
    LudoColor.entries.forEach { c ->
        for (k in 0 until 5) {
            drawCell(LudoGeometry.homeColumnCell(c, k), lerp(c.paint(), Color.White, 0.18f))
        }
    }
}

/** فلش کوچک سفید روی خانه‌ی شروع در جهت حرکت */
private fun DrawScope.drawStartArrow(px: BoardPx, cell: LudoCell, color: LudoColor) {
    val c = px.center(cell)
    val r = px.cell * 0.26f
    val path = Path()
    // جهت: قرمز → راست، سبز → پایین، زرد → چپ، آبی → بالا
    val (dx, dy) = when (color) {
        LudoColor.RED -> 1f to 0f
        LudoColor.GREEN -> 0f to 1f
        LudoColor.YELLOW -> -1f to 0f
        LudoColor.BLUE -> 0f to -1f
    }
    val tip = c + Offset(dx * r, dy * r)
    val back = c - Offset(dx * r * 0.6f, dy * r * 0.6f)
    val side = Offset(-dy, dx) * (r * 0.7f)
    path.moveTo(tip.x, tip.y)
    path.lineTo(back.x + side.x, back.y + side.y)
    path.lineTo(back.x - side.x, back.y - side.y)
    path.close()
    drawPath(path, Color.White.copy(alpha = 0.85f))
}

/** ستاره‌ی پنج‌پر برای خانه‌های امن */
private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val ang = -PI.toFloat() / 2 + i * PI.toFloat() / 5
        val p = center + Offset(cos(ang) * r, sin(ang) * r)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
    drawPath(path, Color.Black.copy(alpha = 0.18f), style = Stroke(width = radius * 0.12f))
}

/** مرکز: چهار مثلث رنگی که به وسط می‌رسند */
private fun DrawScope.drawCenter(px: BoardPx) {
    val tl = px.topLeft(6, 6)
    val sz = px.cell * 3
    val c = tl + Offset(sz / 2, sz / 2)
    val corners = listOf(
        tl,                          // بالا-چپ
        tl + Offset(sz, 0f),         // بالا-راست
        tl + Offset(sz, sz),         // پایین-راست
        tl + Offset(0f, sz),         // پایین-چپ
    )
    // قرمز: مثلث چپ (بین بالا-چپ و پایین-چپ)؛ سبز: بالا؛ زرد: راست؛ آبی: پایین
    val tris = listOf(
        LudoColor.RED to (corners[0] to corners[3]),
        LudoColor.GREEN to (corners[0] to corners[1]),
        LudoColor.YELLOW to (corners[1] to corners[2]),
        LudoColor.BLUE to (corners[2] to corners[3]),
    )
    drawRect(color = Color.White, topLeft = tl, size = Size(sz, sz))
    tris.forEach { (color, pts) ->
        val path = Path().apply {
            moveTo(pts.first.x, pts.first.y)
            lineTo(pts.second.x, pts.second.y)
            lineTo(c.x, c.y)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(lerp(color.paint(), Color.White, 0.08f), lerp(color.paint(), Color.Black, 0.12f)), start = pts.first, end = c))
        drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke(width = px.cell * 0.05f))
    }
    // نگین مرکزی
    drawCircle(Color.White, radius = px.cell * 0.3f, center = c)
    drawCircle(Color(0xFFFFD98E), radius = px.cell * 0.2f, center = c)
}

/** یک مهره‌ی گرد براق با لبه‌ی تیره */
private fun DrawScope.drawToken(color: LudoColor, center: Offset, radius: Float, dim: Boolean) {
    val paint = color.paint()
    val alpha = if (dim) 0.5f else 1f
    drawCircle(Color.Black.copy(alpha = 0.22f * alpha), radius = radius, center = center + Offset(0f, radius * 0.18f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(paint, Color.White, 0.35f), paint, lerp(paint, Color.Black, 0.25f)),
            center = center + Offset(-radius * 0.3f, -radius * 0.35f),
            radius = radius * 1.4f,
        ),
        radius = radius,
        center = center,
        alpha = alpha,
    )
    drawCircle(
        color = lerp(paint, Color.Black, 0.45f).copy(alpha = 0.9f * alpha),
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.16f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.75f * alpha),
        radius = radius * 0.22f,
        center = center + Offset(-radius * 0.32f, -radius * 0.36f),
    )
}

/** پوف: حلقه‌ی گشادشونده، مهره‌ی کوچک‌شونده و چند ذره‌ی پخش‌شونده */
private fun DrawScope.drawPoof(color: LudoColor, center: Offset, cell: Float, t: Float) {
    val paint = color.paint()
    drawToken(color, center, cell * 0.38f * (1f - t), dim = false)
    drawCircle(
        color = Color.White.copy(alpha = (1f - t) * 0.9f),
        radius = cell * (0.25f + 0.65f * t),
        center = center,
        style = Stroke(width = cell * 0.1f * (1f - t) + 1f),
    )
    for (i in 0 until 8) {
        val ang = i * PI.toFloat() / 4 + t * 0.6f
        val d = cell * (0.2f + 0.75f * t)
        drawCircle(
            color = lerp(paint, Color.White, 0.3f).copy(alpha = (1f - t)),
            radius = cell * 0.08f * (1f - t * 0.6f),
            center = center + Offset(cos(ang) * d, sin(ang) * d),
        )
    }
}
