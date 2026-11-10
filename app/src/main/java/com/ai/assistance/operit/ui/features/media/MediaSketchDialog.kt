package com.ai.assistance.operit.ui.features.media

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

private data class SketchStroke(
    val color: Color,
    val width: Float,
    val points: List<Offset>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaSketchDialog(
    onDismiss: () -> Unit,
    onSave: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var strokes by remember { mutableStateOf<List<SketchStroke>>(emptyList()) }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var brushColor by remember { mutableStateOf(Color.Black) }
    var brushWidth by remember { mutableStateOf(8f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    val colors = listOf(Color.Black, Color(0xFF266489), Color(0xFFD9536F))
    val widths = listOf(4f, 8f, 16f)

    fun save() {
        if (strokes.isEmpty() || canvasSize.width == 0 || canvasSize.height == 0 || saving) return
        saving = true
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
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    strokes.forEach { stroke ->
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
                    TextButton(
                        onClick = { strokes = strokes.dropLast(1) },
                        enabled = strokes.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.media_creation_sketch_undo))
                    }
                    TextButton(
                        onClick = {
                            strokes = emptyList()
                            currentPoints = emptyList()
                        },
                        enabled = strokes.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.media_creation_sketch_clear))
                    }
                    TextButton(onClick = ::save, enabled = strokes.isNotEmpty() && !saving) {
                        Text(stringResource(R.string.save))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    colors.forEach { color ->
                        Surface(
                            onClick = { brushColor = color },
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = color,
                            border = if (brushColor == color) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                            } else {
                                null
                            },
                        ) {}
                    }
                    Spacer(Modifier.weight(1f))
                    widths.forEach { width ->
                        FilterChip(
                            selected = brushWidth == width,
                            onClick = { brushWidth = width },
                            label = { Text(width.toInt().toString()) },
                        )
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
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(brushColor, brushWidth) {
                                detectDragGestures(
                                    onDragStart = { currentPoints = listOf(it) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints = currentPoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentPoints.size > 1) {
                                            strokes = strokes + SketchStroke(
                                                color = brushColor,
                                                width = brushWidth,
                                                points = currentPoints,
                                            )
                                        }
                                        currentPoints = emptyList()
                                    },
                                    onDragCancel = { currentPoints = emptyList() },
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
                        drawStroke(SketchStroke(brushColor, brushWidth, currentPoints))
                    }
                }
            }
        }
    }
}
