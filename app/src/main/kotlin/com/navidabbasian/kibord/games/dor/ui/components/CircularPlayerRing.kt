package com.navidabbasian.kibord.games.dor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * پورت کامپوزی CircularPlayerLayout:
 * فرزند مرکزی وسط می‌نشیند و بقیه روی دایره با زاویه‌ی
 * ‎90° + i·(360/N) + rotationOffset‎ چیده می‌شوند.
 */
@Composable
fun CircularPlayerRing(
    rotationOffsetDeg: Float,
    modifier: Modifier = Modifier,
    radiusRatio: Float = 0.38f,
    center: @Composable () -> Unit,
    items: List<@Composable () -> Unit>,
) {
    Layout(
        modifier = modifier,
        content = {
            center()
            items.forEach { it() }
        }
    ) { measurables, constraints ->
        val size = min(constraints.maxWidth, constraints.maxHeight)
        val childConstraints = Constraints(maxWidth = size, maxHeight = size)

        val placeables = measurables.map { it.measure(childConstraints) }
        val centerPlaceable = placeables.first()
        val ringPlaceables = placeables.drop(1)

        val centerSize = max(centerPlaceable.width, centerPlaceable.height)
        val childSize = ringPlaceables.maxOfOrNull { max(it.width, it.height) } ?: 0

        val minRadius = centerSize / 2 + childSize / 2 + 16.dp.roundToPx()
        val maxRadius = size / 2 - childSize / 2 - 8.dp.roundToPx()
        val radius = max(minRadius, min(maxRadius, (size * radiusRatio).roundToInt()))

        layout(size, size) {
            val cx = size / 2
            val cy = size / 2

            centerPlaceable.place(cx - centerPlaceable.width / 2, cy - centerPlaceable.height / 2)

            val n = ringPlaceables.size
            ringPlaceables.forEachIndexed { i, placeable ->
                val angle = Math.toRadians(90.0 + (360.0 / n) * i + rotationOffsetDeg)
                val px = cx + (radius * cos(angle)).roundToInt() - placeable.width / 2
                val py = cy + (radius * sin(angle)).roundToInt() - placeable.height / 2
                placeable.place(px, py)
            }
        }
    }
}

/**
 * چرخش نرم حلقه تا بازیکن فعلی پایین بنشیند —
 * با نرمال‌سازی ±۱۸۰ درجه تا همیشه کوتاه‌ترین مسیر طی شود.
 */
@Composable
fun animateRingRotation(seatIndex: Int, seatCount: Int): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(seatIndex, seatCount) {
        if (seatCount > 0) {
            var target = -seatIndex * (360f / seatCount)
            val current = anim.value
            while (target - current > 180f) target -= 360f
            while (target - current < -180f) target += 360f
            anim.animateTo(target, tween(durationMillis = 400, easing = FastOutSlowInEasing))
        }
    }
    return anim.value
}
