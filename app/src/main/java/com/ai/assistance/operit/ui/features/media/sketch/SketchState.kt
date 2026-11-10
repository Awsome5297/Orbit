package com.ai.assistance.operit.ui.features.media.sketch

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.atan2
import kotlin.math.sqrt

internal class InkDraft(
    val brush: SketchBrush,
    val color: Color,
    val baseWidth: Float,
) {
    val points = mutableStateListOf<SketchPoint>()

    var lastTime = 0L

    var traveled = 0f
}

@Stable
internal class SketchState(aspectRatio: Float? = null) {
    private var available = Size.Unspecified

    var paperSize by mutableStateOf(Size.Unspecified)
        private set

    var aspectRatio by mutableStateOf(aspectRatio)
        private set

    var background by mutableStateOf<ImageBitmap?>(null)
        private set

    val steps = mutableStateListOf<SketchStep>()

    private val undone = mutableStateListOf<SketchStep>()

    var color by mutableStateOf(SketchDefaults.BrushColors.first())
        private set

    var customColor by mutableStateOf<Color?>(null)
        private set

    var usingCustomColor by mutableStateOf(false)
        private set

    var pickingColor by mutableStateOf(false)

    var brush by mutableStateOf(SketchBrush.Pen)
        private set

    var width by mutableStateOf(SketchDefaults.BrushWidths[1])

    var tool by mutableStateOf(SketchTool.Brush)
        private set

    var writingAt by mutableStateOf<Offset?>(null)
        private set

    private var writingSize = 0f

    var selection by mutableStateOf<List<SketchItem>>(emptyList())
        private set

    var moving by mutableStateOf(Placement.None)

    var marquee by mutableStateOf<Rect?>(null)

    var revision by mutableIntStateOf(0)
        private set

    var draft by mutableStateOf<InkDraft?>(null)
        private set

    val erasing: Boolean
        get() = tool == SketchTool.Eraser

    val isBlank: Boolean
        get() = steps.isEmpty() || steps.last() is Clear

    private val hasSteps: Boolean
        get() = steps.isNotEmpty() || undone.isNotEmpty()

    val canUndo: Boolean
        get() = steps.isNotEmpty()

    val canRedo: Boolean
        get() = undone.isNotEmpty()

    fun usePreset(color: Color) {
        this.color = color
        usingCustomColor = false
        pickingColor = false
        if (erasing) tool = SketchTool.Brush
    }

    fun useCustom(color: Color) {
        customColor = color
        this.color = color
        usingCustomColor = true
        if (erasing) tool = SketchTool.Brush
    }

    fun useBrush(brush: SketchBrush) {
        this.brush = brush
        useTool(SketchTool.Brush)
    }

    fun useEraser(enabled: Boolean) = useTool(if (enabled) SketchTool.Eraser else SketchTool.Brush)

    fun useText(enabled: Boolean) = useTool(if (enabled) SketchTool.Text else SketchTool.Brush)

    fun useSelect(enabled: Boolean) = useTool(if (enabled) SketchTool.Select else SketchTool.Brush)

    private fun useTool(tool: SketchTool) {
        this.tool = tool
        pickingColor = false
        deselect()
    }

    fun fit(size: Size) {
        available = size
        if (!hasSteps) layout()
    }

    fun useAspectRatio(aspectRatio: Float?) {
        this.aspectRatio = aspectRatio
        layout()
    }

    fun useBackground(image: ImageBitmap?) {
        background = image
        layout()
    }

    private fun layout() {
        if (available.isUnspecified) return
        val image = background
        val explicit = aspectRatio
        val ratio = when {
            image != null -> image.width.toFloat() / image.height
            explicit != null -> explicit
            else -> available.width / available.height
        }
        // 已经落笔时换比例只重新裁一块画纸，保持笔画在屏幕上的大小不变
        val scale = if (hasSteps && paperSize.isSpecified) {
            available.fitRatio(paperSize.width / paperSize.height).width / paperSize.width
        } else {
            1f
        }
        paperSize = available.fitRatio(ratio) / scale
    }

    fun beginStroke(
        point: Offset,
        width: Float,
        pressure: Float,
        stylus: Boolean,
        time: Long,
    ) {
        val draft = InkDraft(brush, color.copy(alpha = brush.alpha), width)
        draft.lastTime = time
        val pointWidth = brush.pointWidth(width, 0f, 0f, pressure, stylus, 0f)
        draft.points += SketchPoint(point.x, point.y, pointWidth)
        this.draft = draft
    }

    fun extendStroke(
        point: Offset,
        pressure: Float,
        stylus: Boolean,
        time: Long,
    ) {
        val draft = draft ?: return
        val last = draft.points.lastOrNull() ?: return
        val dx = point.x - last.x
        val dy = point.y - last.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < 0.6f) return

        val elapsed = (time - draft.lastTime).coerceAtLeast(1L)
        val speed = distance / elapsed * 1000f
        draft.traveled += distance
        val direction = atan2(dy, dx)
        val pointWidth = draft.brush.pointWidth(
            width = draft.baseWidth,
            traveled = draft.traveled,
            speed = speed,
            pressure = pressure,
            stylus = stylus,
            direction = direction,
        )
        draft.points += SketchPoint(point.x, point.y, pointWidth)
        draft.lastTime = time
    }

    fun finishStroke() {
        val draft = draft ?: return
        if (draft.points.isNotEmpty()) {
            steps += Ink(draft.brush, draft.color, draft.baseWidth, draft.points.toList())
            undone.clear()
        }
        this.draft = null
    }

    fun erase(point: Offset, width: Float): Erase {
        val line = Erase(point, width * 4f)
        steps += line
        undone.clear()
        return line
    }

    fun extendErase(line: Erase, point: Offset) {
        line.extendTo(point)
        revision++
    }

    fun finishErase(line: Erase) {
        line.finish()
        revision++
    }

    fun beginText(point: Offset, width: Float) {
        writingAt = point
        writingSize = width * 4f
    }

    fun write(text: String) {
        val start = writingAt ?: return
        writingAt = null
        val content = text.trim()
        if (content.isEmpty()) return
        steps += TextItem(
            text = content,
            start = start,
            color = color,
            size = writingSize,
            maxWidth = (paperSize.width / 2f - start.x).coerceAtLeast(writingSize),
        )
        undone.clear()
    }

    fun cancelText() {
        writingAt = null
    }

    fun placements(): Map<SketchItem, Placement> {
        val result = LinkedHashMap<SketchItem, Placement>()
        val first = steps.indexOfLast { it is Clear } + 1
        for (index in first until steps.size) {
            when (val step = steps[index]) {
                is SketchItem -> result[step] = Placement.None
                is Transform -> step.items.forEach { item ->
                    result[item]?.let { result[item] = it.then(step.placement) }
                }

                else -> Unit
            }
        }
        if (moving != Placement.None) {
            selection.forEach { item ->
                result[item]?.let { result[item] = it.then(moving) }
            }
        }
        return result
    }

    val selectionBounds: Rect?
        get() {
            if (selection.isEmpty()) return null
            val placements = placements()
            return selection
                .mapNotNull { item -> placements[item]?.apply(item.bounds) }
                .reduceOrNull { all, next ->
                    Rect(
                        minOf(all.left, next.left),
                        minOf(all.top, next.top),
                        maxOf(all.right, next.right),
                        maxOf(all.bottom, next.bottom),
                    )
                }
        }

    fun select(area: Rect, topmost: Boolean) {
        val touched = placements()
            .filter { (item, placement) -> item.touches(placement.invert(area)) }
            .keys
            .toList()
        selection = if (topmost) listOfNotNull(touched.lastOrNull()) else touched
    }

    fun deselect() {
        selection = emptyList()
        moving = Placement.None
        marquee = null
    }

    fun settle() {
        val placement = moving
        moving = Placement.None
        if (placement == Placement.None || selection.isEmpty()) return
        steps += Transform(selection, placement)
        undone.clear()
    }

    fun clear() {
        if (isBlank) return
        deselect()
        steps += Clear
        undone.clear()
    }

    fun undo() {
        deselect()
        steps.removeLastOrNull()?.let { undone += it }
    }

    fun redo() {
        deselect()
        undone.removeLastOrNull()?.let { steps += it }
    }
}
