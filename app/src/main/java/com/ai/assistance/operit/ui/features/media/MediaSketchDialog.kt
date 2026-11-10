package com.ai.assistance.operit.ui.features.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.min

private data class SketchStroke(
    val color: Color,
    val width: Float,
    val points: List<Offset>,
)

private enum class SketchTool {
    BRUSH,
    STROKE_ERASER,
    AREA_ERASER,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MediaSketchDialog(
    onDismiss: () -> Unit,
    onSave: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eraserRadius = with(LocalDensity.current) { 24.dp.toPx() }
    var strokes by remember { mutableStateOf<List<SketchStroke>>(emptyList()) }
    var undoStack by remember { mutableStateOf<List<List<SketchStroke>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<SketchStroke>>>(emptyList()) }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var tool by remember { mutableStateOf(SketchTool.BRUSH) }
    var brushColor by remember { mutableStateOf(Color.Black) }
    var brushWidthText by remember { mutableStateOf("8") }
    var brushWidth by remember { mutableStateOf<Float?>(8f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var eraseSnapshot by remember { mutableStateOf<List<SketchStroke>?>(null) }
    var eraserPoint by remember { mutableStateOf<Offset?>(null) }

    val palette = listOf(
        Color.Black,
        Color(0xFFD9534F),
        Color(0xFFF0A202),
        Color(0xFF3C9D4E),
        Color(0xFF266489),
        Color(0xFF6A4C93),
        Color(0xFF8B5E3C),
        Color(0xFF9AA0A6),
    )

    val pickBaseImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
                if (bitmap != null) {
                    baseBitmap = bitmap
                }
            }
        }
    }

    fun commit(next: List<SketchStroke>) {
        undoStack = undoStack + strokes
        redoStack = emptyList()
        strokes = next
    }

    fun undo() {
        currentPoints = emptyList()
        if (undoStack.isNotEmpty()) {
            redoStack = redoStack + strokes
            strokes = undoStack.last()
            undoStack = undoStack.dropLast(1)
        }
    }

    fun redo() {
        currentPoints = emptyList()
        if (redoStack.isNotEmpty()) {
            undoStack = undoStack + strokes
            strokes = redoStack.last()
            redoStack = redoStack.dropLast(1)
        }
    }

    fun save() {
        if ((strokes.isEmpty() && baseBitmap == null) || canvasSize.width == 0 || canvasSize.height == 0 || saving) {
            return
        }
        saving = true
        val base = baseBitmap
        val snapshot = strokes
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val bitmap = Bitmap.createBitmap(
                        canvasSize.width,
                        canvasSize.height,
                        Bitmap.Config.ARGB_8888,
                    )
                    val canvas = AndroidCanvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    if (base != null) {
                        drawBaseImage(canvas, base, canvasSize.width, canvasSize.height)
                    }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    snapshot.forEach { stroke ->
                        paint.color = stroke.color.toArgb()
                        paint.strokeWidth = stroke.width
                        for (index in 1 until stroke.points.size) {
                            val start = stroke.points[index - 1]
                            val end = stroke.points[index]
                            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
                        }
                    }
                    val target = File(context.cacheDir, "sketch_${UUID.randomUUID()}.png")
                    target.outputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    bitmap.recycle()
                    target
                }
                onSave(file)
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                    Text(
                        text = stringResource(R.string.media_creation_sketch),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) {
                        Text(stringResource(R.string.media_creation_sketch_undo))
                    }
                    TextButton(onClick = ::redo, enabled = redoStack.isNotEmpty()) {
                        Text(stringResource(R.string.media_creation_sketch_redo))
                    }
                    TextButton(
                        onClick = ::save,
                        enabled = (strokes.isNotEmpty() || baseBitmap != null) && !saving,
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    palette.forEach { color ->
                        Surface(
                            onClick = { brushColor = color },
                            modifier = Modifier.size(26.dp),
                            shape = CircleShape,
                            color = color,
                            border = if (brushColor == color) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                            } else {
                                null
                            },
                        ) {}
                    }
                    Spacer(Modifier.size(4.dp))
                    OutlinedTextField(
                        value = brushWidthText,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit) && value.length <= 2) {
                                brushWidthText = value
                                brushWidth = value.toIntOrNull()?.takeIf { it in 1..64 }?.toFloat()
                            }
                        },
                        modifier = Modifier.width(92.dp),
                        label = { Text(stringResource(R.string.media_creation_sketch_brush_size)) },
                        supportingText = {
                            if (brushWidth == null) {
                                Text(stringResource(R.string.media_creation_sketch_brush_size_hint))
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedButton(onClick = { pickBaseImage.launch("image/*") }) {
                        Text(stringResource(R.string.media_creation_sketch_base_image))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = tool == SketchTool.BRUSH,
                        onClick = {
                            tool = SketchTool.BRUSH
                            currentPoints = emptyList()
                        },
                        label = { Text(stringResource(R.string.media_creation_sketch_tool_brush)) },
                    )
                    FilterChip(
                        selected = tool == SketchTool.STROKE_ERASER,
                        onClick = {
                            tool = SketchTool.STROKE_ERASER
                            currentPoints = emptyList()
                        },
                        label = { Text(stringResource(R.string.media_creation_sketch_tool_stroke_eraser)) },
                    )
                    FilterChip(
                        selected = tool == SketchTool.AREA_ERASER,
                        onClick = {
                            tool = SketchTool.AREA_ERASER
                            currentPoints = emptyList()
                        },
                        label = { Text(stringResource(R.string.media_creation_sketch_tool_area_eraser)) },
                    )
                    TextButton(
                        onClick = {
                            strokes = emptyList()
                            undoStack = emptyList()
                            redoStack = emptyList()
                            currentPoints = emptyList()
                        },
                        enabled = strokes.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.media_creation_sketch_clear))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .onSizeChanged { canvasSize = it },
                ) {
                    baseBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable { baseBitmap = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(tool, brushColor, brushWidth, eraserRadius) {
                                detectDragGestures(
                                    onDragStart = { point ->
                                        when (tool) {
                                            SketchTool.BRUSH -> {
                                                if (brushWidth != null) {
                                                    currentPoints = listOf(point)
                                                }
                                            }
                                            SketchTool.STROKE_ERASER,
                                            SketchTool.AREA_ERASER -> {
                                                eraseSnapshot = strokes
                                                eraserPoint = point
                                                strokes = if (tool == SketchTool.STROKE_ERASER) {
                                                    eraseStrokeAt(strokes, point, eraserRadius)
                                                } else {
                                                    eraseAreaAt(strokes, point, eraserRadius)
                                                }
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val point = change.position
                                        when (tool) {
                                            SketchTool.BRUSH -> {
                                                if (brushWidth != null) {
                                                    currentPoints = currentPoints + point
                                                }
                                            }
                                            SketchTool.STROKE_ERASER -> {
                                                eraserPoint = point
                                                strokes = eraseStrokeAt(strokes, point, eraserRadius)
                                            }
                                            SketchTool.AREA_ERASER -> {
                                                eraserPoint = point
                                                strokes = eraseAreaAt(strokes, point, eraserRadius)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (tool == SketchTool.BRUSH) {
                                            val width = brushWidth
                                            if (width != null && currentPoints.size > 1) {
                                                commit(
                                                    strokes + SketchStroke(
                                                        color = brushColor,
                                                        width = width,
                                                        points = currentPoints,
                                                    )
                                                )
                                            }
                                            currentPoints = emptyList()
                                        } else {
                                            val snapshot = eraseSnapshot
                                            if (snapshot != null && strokes != snapshot) {
                                                undoStack = undoStack + snapshot
                                                redoStack = emptyList()
                                            }
                                            eraseSnapshot = null
                                            eraserPoint = null
                                        }
                                    },
                                    onDragCancel = {
                                        eraseSnapshot?.let { snapshot -> strokes = snapshot }
                                        currentPoints = emptyList()
                                        eraseSnapshot = null
                                        eraserPoint = null
                                    },
                                )
                            },
                    ) {
                        fun drawStroke(stroke: SketchStroke) {
                            for (index in 1 until stroke.points.size) {
                                drawLine(
                                    color = stroke.color,
                                    start = stroke.points[index - 1],
                                    end = stroke.points[index],
                                    strokeWidth = stroke.width,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                        strokes.forEach { stroke -> drawStroke(stroke) }
                        drawStroke(SketchStroke(brushColor, brushWidth ?: 1f, currentPoints))
                        eraserPoint?.let { point ->
                            drawCircle(
                                color = Color.Gray.copy(alpha = 0.25f),
                                radius = eraserRadius,
                                center = point,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun drawBaseImage(canvas: AndroidCanvas, bitmap: Bitmap, width: Int, height: Int) {
    val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
    val targetWidth = bitmap.width * scale
    val targetHeight = bitmap.height * scale
    val left = (width - targetWidth) / 2f
    val top = (height - targetHeight) / 2f
    canvas.drawBitmap(
        bitmap,
        null,
        RectF(left, top, left + targetWidth, top + targetHeight),
        Paint(Paint.FILTER_BITMAP_FLAG),
    )
}

private fun eraseStrokeAt(
    strokes: List<SketchStroke>,
    point: Offset,
    radius: Float,
): List<SketchStroke> = strokes.filterNot { stroke ->
    stroke.points.any { candidate -> (candidate - point).getDistance() <= radius }
}

private fun eraseAreaAt(
    strokes: List<SketchStroke>,
    point: Offset,
    radius: Float,
): List<SketchStroke> {
    val result = mutableListOf<SketchStroke>()
    strokes.forEach { stroke ->
        var segment = mutableListOf<Offset>()
        stroke.points.forEach { candidate ->
            if ((candidate - point).getDistance() <= radius) {
                if (segment.size > 1) {
                    result.add(stroke.copy(points = segment))
                }
                segment = mutableListOf()
            } else {
                segment.add(candidate)
            }
        }
        if (segment.size > 1) {
            result.add(stroke.copy(points = segment))
        }
    }
    return result
}
