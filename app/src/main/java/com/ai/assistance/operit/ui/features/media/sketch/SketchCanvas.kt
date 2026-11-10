package com.ai.assistance.operit.ui.features.media.sketch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt

private val SelectionHandleReach = 24.dp

private const val MIN_SELECTION_SCALE = 0.1f
private const val MAX_SELECTION_SCALE = 10f

@Composable
internal fun SketchCanvas(state: SketchState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.onSizeChanged {
            if (it.width > 0 && it.height > 0) state.fit(it.toSize())
        },
        contentAlignment = Alignment.Center,
    ) {
        val paper = state.paperSize
        if (paper.isSpecified) {
            val density = LocalDensity.current
            val selectionColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .aspectRatio(paper.width / paper.height)
                    .clip(MaterialTheme.shapes.medium)
                    .systemGestureExclusion()
                    .pointerInput(state, paper, density) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            state.pickingColor = false
                            down.consume()

                            val scale = size.width / paper.width
                            val center = Offset(size.width / 2f, size.height / 2f)
                            fun toPaper(offset: Offset): Offset = (offset - center) / scale
                            val baseWidth = with(density) { state.width.toPx() } / scale
                            val stylus = down.type == PointerType.Stylus

                            when (state.tool) {
                                SketchTool.Brush -> {
                                    state.beginStroke(
                                        point = toPaper(down.position),
                                        width = baseWidth,
                                        pressure = down.pressure,
                                        stylus = stylus,
                                        time = down.uptimeMillis,
                                    )
                                    try {
                                        while (true) {
                                            val change = awaitPointerEvent(
                                                PointerEventPass.Initial,
                                            ).changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            change.historical.forEach { historical ->
                                                state.extendStroke(
                                                    point = toPaper(historical.position),
                                                    pressure = change.pressure,
                                                    stylus = stylus,
                                                    time = historical.uptimeMillis,
                                                )
                                            }
                                            state.extendStroke(
                                                point = toPaper(change.position),
                                                pressure = change.pressure,
                                                stylus = stylus,
                                                time = change.uptimeMillis,
                                            )
                                            change.consume()
                                        }
                                    } finally {
                                        state.finishStroke()
                                    }
                                }

                                SketchTool.Eraser -> {
                                    val line = state.erase(toPaper(down.position), baseWidth)
                                    try {
                                        while (true) {
                                            val change = awaitPointerEvent(
                                                PointerEventPass.Initial,
                                            ).changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            change.historical.forEach { historical ->
                                                state.extendErase(
                                                    line,
                                                    toPaper(historical.position),
                                                )
                                            }
                                            state.extendErase(line, toPaper(change.position))
                                            change.consume()
                                        }
                                    } finally {
                                        state.finishErase(line)
                                    }
                                }

                                SketchTool.Text -> {
                                    val up = waitForUpOrCancellation(PointerEventPass.Initial)
                                        ?: return@awaitEachGesture
                                    up.consume()
                                    state.beginText(toPaper(up.position), baseWidth)
                                }

                                SketchTool.Select -> selectGesture(
                                    state = state,
                                    down = down,
                                    scale = scale,
                                    toPaper = { toPaper(it) },
                                )
                            }
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    state.revision
                    drawSketch(state, size.width / paper.width)
                    drawSelectionOverlay(state, size.width / paper.width, selectionColor)
                }
            }
        }
    }
}

internal fun DrawScope.drawSketch(state: SketchState, scale: Float) {
    drawRect(SketchDefaults.PaperColor)
    state.background?.let { image ->
        drawImage(
            image = image,
            dstSize = androidx.compose.ui.unit.IntSize(
                size.width.roundToInt(),
                size.height.roundToInt(),
            ),
        )
    }

    val steps = state.steps
    val first = steps.indexOfLast { it is Clear } + 1
    val placements = state.placements()
    val layerPaint = Paint()

    // 橡皮用清除混合模式，笔画单独成层，擦除不会伤到画纸和底图
    drawIntoCanvas { canvas -> canvas.saveLayer(Rect(Offset.Zero, size), layerPaint) }
    translate(size.width / 2f, size.height / 2f) {
        scale(scale, pivot = Offset.Zero) {
            for (index in first until steps.size) {
                when (val step = steps[index]) {
                    is SketchItem -> {
                        val placement = placements[step] ?: continue
                        translate(placement.offset.x, placement.offset.y) {
                            scale(placement.scale, pivot = Offset.Zero) {
                                drawSketchItem(step)
                            }
                        }
                    }

                    is Erase -> drawErase(step)
                    else -> Unit
                }
            }
            state.draft?.let { draft ->
                drawInkPoints(
                    brush = draft.brush,
                    color = draft.color,
                    baseWidth = draft.baseWidth,
                    points = draft.points,
                )
            }
        }
    }
    drawIntoCanvas { canvas -> canvas.restore() }
}

private fun DrawScope.drawSketchItem(item: SketchItem) {
    when (item) {
        is Ink -> {
            val first = item.points.firstOrNull() ?: return
            if (item.points.size == 1) {
                drawCircle(item.color, first.width / 2f, first.offset)
            } else {
                drawInkPaths(
                    brush = item.brush,
                    color = item.color,
                    baseWidth = item.baseWidth,
                    centerPath = item.centerPath,
                    outlinePath = item.outlinePath,
                )
                if (item.brush.outline) drawOutlineCaps(item.points, item.color)
            }
        }

        is TextItem -> drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(item.position.x, item.position.y)
            item.layout.draw(native)
            native.restore()
        }
    }
}

private fun DrawScope.drawInkPoints(
    brush: SketchBrush,
    color: Color,
    baseWidth: Float,
    points: List<SketchPoint>,
) {
    val first = points.firstOrNull() ?: return
    if (points.size == 1) {
        drawCircle(color, first.width / 2f, first.offset)
        return
    }
    drawInkPaths(
        brush = brush,
        color = color,
        baseWidth = baseWidth,
        centerPath = buildCenterPath(points),
        outlinePath = if (brush.outline) buildOutlinePath(points) else Path(),
    )
    if (brush.outline) drawOutlineCaps(points, color)
}

private fun DrawScope.drawOutlineCaps(points: List<SketchPoint>, color: Color) {
    val first = points.first()
    val last = points.last()
    drawCircle(color, first.width / 2f, first.offset)
    drawCircle(color, last.width / 2f, last.offset)
}

private fun DrawScope.drawInkPaths(
    brush: SketchBrush,
    color: Color,
    baseWidth: Float,
    centerPath: Path,
    outlinePath: Path,
) {
    val strokeWidth = baseWidth * brush.widthScale
    when {
        brush.outline -> drawPath(outlinePath, color)
        brush == SketchBrush.Dashed -> drawPath(
            path = centerPath,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(strokeWidth * 2.5f, strokeWidth * 1.8f),
                ),
            ),
        )

        else -> drawPath(
            path = centerPath,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun DrawScope.drawErase(erase: Erase) {
    if (erase.moved) {
        drawPath(
            path = erase.path,
            color = Color.Black,
            style = Stroke(
                width = erase.width,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = BlendMode.Clear,
        )
    } else {
        drawCircle(
            color = Color.Black,
            radius = erase.width / 2f,
            center = erase.start,
            blendMode = BlendMode.Clear,
        )
    }
}

private val Rect.corners: List<Offset>
    get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

private suspend fun AwaitPointerEventScope.dragPointer(
    down: PointerInputChange,
    toPaper: (Offset) -> Offset,
    onMove: (Offset) -> Unit,
) {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial)
            .changes
            .firstOrNull { it.id == down.id } ?: break
        change.consume()
        if (!change.pressed) break
        onMove(toPaper(change.position))
    }
}

private suspend fun AwaitPointerEventScope.selectGesture(
    state: SketchState,
    down: PointerInputChange,
    scale: Float,
    toPaper: (Offset) -> Offset,
) {
    val start = toPaper(down.position)
    val reach = SelectionHandleReach.toPx() / scale
    val bounds = state.selectionBounds
    val corner = bounds?.corners
        ?.filter { (it - start).getDistance() <= reach }
        ?.minByOrNull { (it - start).getDistance() }

    try {
        when {
            bounds != null && corner != null -> {
                val pivot = bounds.center * 2f - corner
                val from = (start - pivot).getDistance()
                dragPointer(down, toPaper) { point ->
                    if (from > 0f) {
                        val by = ((point - pivot).getDistance() / from)
                            .coerceIn(MIN_SELECTION_SCALE, MAX_SELECTION_SCALE)
                        state.moving = Placement.scaleBy(by, pivot)
                    }
                }
            }

            bounds != null && bounds.contains(start) -> dragPointer(down, toPaper) { point ->
                state.moving = Placement.move(point - start)
            }

            else -> {
                state.deselect()
                dragPointer(down, toPaper) { point ->
                    if (state.marquee != null ||
                        (point - start).getDistance() * scale > viewConfiguration.touchSlop
                    ) {
                        state.marquee = Rect(
                            left = minOf(start.x, point.x),
                            top = minOf(start.y, point.y),
                            right = maxOf(start.x, point.x),
                            bottom = maxOf(start.y, point.y),
                        )
                    }
                }
                val marquee = state.marquee
                state.select(
                    area = marquee ?: Rect(center = start, radius = reach / 2f),
                    topmost = marquee == null,
                )
            }
        }
    } finally {
        state.marquee = null
        state.settle()
    }
}

internal fun DrawScope.drawSelectionOverlay(state: SketchState, scale: Float, color: Color) {
    val marquee = state.marquee
    val bounds = state.selectionBounds
    if (marquee == null && bounds == null) return

    translate(size.width / 2f, size.height / 2f) {
        scale(scale, pivot = Offset.Zero) {
            val outline = Stroke(
                width = 1.5.dp.toPx() / scale,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx() / scale, 4.dp.toPx() / scale),
                ),
            )
            if (marquee != null) {
                drawRect(
                    color = color.copy(alpha = 0.12f),
                    topLeft = marquee.topLeft,
                    size = marquee.size,
                )
                drawRect(
                    color = color,
                    topLeft = marquee.topLeft,
                    size = marquee.size,
                    style = outline,
                )
            }
            if (bounds != null) {
                drawRect(
                    color = color,
                    topLeft = bounds.topLeft,
                    size = bounds.size,
                    style = outline,
                )
                val radius = 5.dp.toPx() / scale
                bounds.corners.forEach { corner ->
                    drawCircle(Color.White, radius, corner)
                    drawCircle(color, radius, corner, style = Stroke(2.dp.toPx() / scale))
                }
            }
        }
    }
}
