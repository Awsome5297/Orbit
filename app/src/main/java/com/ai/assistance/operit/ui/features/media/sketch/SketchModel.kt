package com.ai.assistance.operit.ui.features.media.sketch

import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object SketchDefaults {
    val PaperColor = Color.White

    val BrushColors = listOf(
        Color(0xFF000000),
        Color(0xFFE53935),
        Color(0xFFFB8C00),
        Color(0xFF43A047),
        Color(0xFF1E88E5),
        Color(0xFF8E24AA),
    )

    val HueColors = List(7) { Color.hsv(it * 60f, 1f, 1f) }

    val CustomColor = Color(0xFFE91E63)

    val BrushWidths = listOf(3.dp, 6.dp, 12.dp)

    val AspectRatios = listOf(1 to 1, 4 to 3, 3 to 4, 16 to 9, 9 to 16)
}

internal enum class SketchTool {
    Brush,
    Eraser,
    Text,
    Select,
}

internal enum class SketchBrush(
    val label: Int,
    val widthScale: Float = 1f,
    val alpha: Float = 1f,
) {
    Pen(R.string.media_creation_sketch_brush_pen),
    Marker(R.string.media_creation_sketch_brush_marker),
    Flat(R.string.media_creation_sketch_brush_flat, widthScale = 2.2f),
    Calligraphy(R.string.media_creation_sketch_brush_calligraphy, widthScale = 2.2f),
    Highlighter(R.string.media_creation_sketch_brush_highlighter, widthScale = 4f, alpha = 0.4f),
    Dashed(R.string.media_creation_sketch_brush_dashed),
    ;

    val outline: Boolean
        get() = this == Pen || this == Flat || this == Calligraphy
}

internal fun SketchBrush.pointWidth(
    width: Float,
    traveled: Float,
    speed: Float,
    pressure: Float,
    stylus: Boolean,
    direction: Float,
): Float {
    val base = width.coerceAtLeast(0.5f)
    return when (this) {
        SketchBrush.Pen -> base * if (stylus) {
            (0.4f + 0.9f * pressure).coerceIn(0.35f, 1.4f)
        } else {
            0.7f + 0.3f * (traveled / (base * 1.5f)).coerceIn(0f, 1f)
        }

        SketchBrush.Marker -> base

        SketchBrush.Flat -> base * widthScale *
            (0.3f + 0.7f * abs(sin(direction - PI.toFloat() / 4f)))

        SketchBrush.Calligraphy -> base * widthScale *
            (0.2f + 0.8f * (traveled / (base * 1.5f)).coerceIn(0f, 1f)) *
            if (stylus) {
                (0.35f + pressure).coerceIn(0.35f, 1.35f)
            } else {
                1.25f - 0.5f * (speed / 2500f).coerceIn(0f, 1f)
            }

        SketchBrush.Highlighter -> base * widthScale

        SketchBrush.Dashed -> base
    }
}

internal data class SketchPoint(
    val x: Float,
    val y: Float,
    val width: Float,
) {
    val offset: Offset
        get() = Offset(x, y)
}

internal sealed interface SketchStep

internal sealed interface SketchItem : SketchStep {
    val bounds: Rect

    fun touches(area: Rect): Boolean
}

internal class Ink(
    val brush: SketchBrush,
    val color: Color,
    val baseWidth: Float,
    val points: List<SketchPoint>,
) : SketchItem {
    val centerPath: Path by lazy { buildCenterPath(points) }

    val outlinePath: Path by lazy { buildOutlinePath(points) }

    override val bounds: Rect = boundsOf(points)

    override fun touches(area: Rect): Boolean {
        if (!bounds.overlaps(area)) return false
        val first = points.firstOrNull() ?: return false
        if (points.size == 1) {
            return (first.offset - area.center).getDistance() <=
                first.width / 2f + maxOf(area.width, area.height) / 2f
        }
        return points.zipWithNext().any { (start, end) ->
            segmentIntersectsRect(
                start = start.offset,
                end = end.offset,
                rect = area.inflate(maxOf(start.width, end.width) / 2f),
            )
        }
    }
}

internal class TextItem(
    val text: String,
    start: Offset,
    val color: Color,
    val size: Float,
    val maxWidth: Float,
) : SketchItem {
    val layout: StaticLayout = run {
        val flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
        val paint = TextPaint(flags).apply {
            textSize = size
            this.color = color.toArgb()
        }
        val width = ceil(min(maxWidth, Layout.getDesiredWidth(text, paint))).toInt().coerceAtLeast(1)
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
    }

    val position: Offset = Offset(start.x, start.y - layout.getLineBottom(0) / 2f)

    override val bounds: Rect = Rect(
        position,
        Size(layout.width.toFloat(), layout.height.toFloat()),
    )

    override fun touches(area: Rect): Boolean = bounds.overlaps(area)
}

internal class Erase(
    val start: Offset,
    val width: Float,
) : SketchStep {
    val path: Path = Path().apply {
        moveTo(start.x, start.y)
    }

    private var last = start

    var moved = false
        private set

    fun extendTo(point: Offset) {
        if ((point - last).getDistance() < 0.5f) return
        val middle = (last + point) / 2f
        path.quadraticBezierTo(last.x, last.y, middle.x, middle.y)
        last = point
        moved = true
    }

    fun finish() {
        if (moved) path.lineTo(last.x, last.y)
    }
}

internal class Transform(
    val items: List<SketchItem>,
    val placement: Placement,
) : SketchStep

internal data object Clear : SketchStep

internal data class Placement(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
) {
    fun then(next: Placement): Placement =
        Placement(scale * next.scale, offset * next.scale + next.offset)

    fun apply(point: Offset): Offset = point * scale + offset

    fun apply(rect: Rect): Rect = Rect(apply(rect.topLeft), apply(rect.bottomRight))

    fun invert(rect: Rect): Rect =
        Rect((rect.topLeft - offset) / scale, (rect.bottomRight - offset) / scale)

    companion object {
        val None = Placement()

        fun move(by: Offset): Placement = Placement(offset = by)

        fun scaleBy(factor: Float, around: Offset): Placement =
            Placement(factor, around * (1 - factor))
    }
}

internal fun buildCenterPath(points: List<SketchPoint>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        lineTo(points[0].x, points[0].y)
        return@apply
    }
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val middleX = (previous.x + current.x) / 2f
        val middleY = (previous.y + current.y) / 2f
        quadraticBezierTo(previous.x, previous.y, middleX, middleY)
    }
    val last = points.last()
    lineTo(last.x, last.y)
}

internal fun buildOutlinePath(points: List<SketchPoint>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    if (points.size == 1) {
        path.addOval(Rect(center = points[0].offset, radius = points[0].width / 2f))
        return path
    }

    val left = ArrayList<Offset>(points.size)
    val right = ArrayList<Offset>(points.size)
    points.forEachIndexed { index, point ->
        val previous = points[(index - 1).coerceAtLeast(0)]
        val next = points[(index + 1).coerceAtMost(points.lastIndex)]
        var dx = next.x - previous.x
        var dy = next.y - previous.y
        val length = sqrt(dx * dx + dy * dy)
        if (length < 0.0001f) {
            dx = 1f
            dy = 0f
        } else {
            dx /= length
            dy /= length
        }
        val normalX = -dy
        val normalY = dx
        val radius = point.width / 2f
        left += Offset(point.x + normalX * radius, point.y + normalY * radius)
        right += Offset(point.x - normalX * radius, point.y - normalY * radius)
    }

    path.moveTo(left[0].x, left[0].y)
    for (index in 1 until left.size) {
        path.lineTo(left[index].x, left[index].y)
    }
    for (index in right.indices.reversed()) {
        path.lineTo(right[index].x, right[index].y)
    }
    path.close()
    return path
}

internal fun boundsOf(points: List<SketchPoint>): Rect {
    if (points.isEmpty()) return Rect.Zero
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    points.forEach { point ->
        val radius = point.width / 2f
        left = min(left, point.x - radius)
        top = min(top, point.y - radius)
        right = maxOf(right, point.x + radius)
        bottom = maxOf(bottom, point.y + radius)
    }
    return Rect(left, top, right, bottom)
}

internal fun segmentIntersectsRect(start: Offset, end: Offset, rect: Rect): Boolean {
    if (rect.contains(start) || rect.contains(end)) return true

    var enter = 0f
    var exit = 1f
    val dx = end.x - start.x
    val dy = end.y - start.y
    val edges = floatArrayOf(-dx, dx, -dy, dy)
    val distances = floatArrayOf(
        start.x - rect.left,
        rect.right - start.x,
        start.y - rect.top,
        rect.bottom - start.y,
    )

    for (index in 0..3) {
        val edge = edges[index]
        val distance = distances[index]
        if (abs(edge) < 0.0001f) {
            if (distance < 0f) return false
        } else {
            val ratio = distance / edge
            if (edge < 0f) {
                if (ratio > exit) return false
                if (ratio > enter) enter = ratio
            } else {
                if (ratio < enter) return false
                if (ratio < exit) exit = ratio
            }
        }
    }
    return true
}

internal fun Size.fitRatio(aspectRatio: Float): Size =
    if (width / height > aspectRatio) {
        Size(height * aspectRatio, height)
    } else {
        Size(width, width / aspectRatio)
    }
