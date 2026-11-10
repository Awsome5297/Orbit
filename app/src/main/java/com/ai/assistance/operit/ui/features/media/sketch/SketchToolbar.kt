package com.ai.assistance.operit.ui.features.media.sketch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import kotlin.math.abs

private val ColorSliderThumbRadius = 14.dp

@Composable
internal fun SketchToolbar(state: SketchState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SketchDefaults.BrushWidths.forEach { width ->
                    WidthOption(
                        width = width,
                        selected = state.width == width,
                        onClick = { state.width = width },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            SketchBrushMenu(state = state)
            FilledTonalIconToggleButton(
                checked = state.tool == SketchTool.Text,
                onCheckedChange = state::useText,
            ) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = stringResource(R.string.media_creation_sketch_text),
                )
            }
            FilledTonalIconToggleButton(
                checked = state.tool == SketchTool.Select,
                onCheckedChange = state::useSelect,
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.media_creation_sketch_select),
                )
            }
            FilledTonalIconToggleButton(
                checked = state.erasing,
                onCheckedChange = state::useEraser,
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = stringResource(R.string.media_creation_sketch_eraser),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SketchDefaults.BrushColors.forEach { color ->
                ColorOption(
                    color = color,
                    selected = !state.erasing && !state.usingCustomColor && state.color == color,
                    onClick = { state.usePreset(color) },
                )
            }
            val customSelected = !state.erasing && state.usingCustomColor
            ColorOption(
                color = state.customColor,
                selected = customSelected,
                custom = true,
                onClick = {
                    val custom = state.customColor
                    when {
                        custom == null -> {
                            state.useCustom(SketchDefaults.CustomColor)
                            state.pickingColor = true
                        }

                        !customSelected -> state.useCustom(custom)
                        else -> state.pickingColor = !state.pickingColor
                    }
                },
            )
        }
    }
}

@Composable
private fun WidthOption(width: androidx.compose.ui.unit.Dp, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = CircleShape,
                ),
        ) {}
    }
}

@Composable
private fun ColorOption(
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    custom: Boolean = false,
) {
    val customLabel = stringResource(R.string.media_creation_sketch_custom_color)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(
                if (custom) {
                    Modifier.semantics { contentDescription = customLabel }
                } else {
                    Modifier
                },
            )
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .padding(6.dp)
            .then(
                if (custom) {
                    Modifier
                        .background(
                            brush = GradientBrush.sweepGradient(SketchDefaults.HueColors),
                            shape = CircleShape,
                        )
                        .padding(4.dp)
                } else {
                    Modifier
                },
            )
            .then(
                if (color != null) {
                    Modifier.background(color = color, shape = CircleShape)
                } else {
                    Modifier
                },
            )
            .then(
                if (custom) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                },
            ),
    ) {}
}

@Composable
private fun SketchBrushMenu(state: SketchState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconToggleButton(
            checked = state.tool == SketchTool.Brush,
            onCheckedChange = {
                if (state.tool == SketchTool.Brush) {
                    expanded = true
                } else {
                    state.useBrush(state.brush)
                }
            },
        ) {
            Icon(
                imageVector = brushIcon(state.brush),
                contentDescription = stringResource(state.brush.label),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SketchBrush.entries.forEach { brush ->
                DropdownMenuItem(
                    text = { Text(stringResource(brush.label)) },
                    leadingIcon = { Icon(brushIcon(brush), contentDescription = null) },
                    trailingIcon = if (state.brush == brush) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        state.useBrush(brush)
                    },
                )
            }
        }
    }
}

private fun brushIcon(brush: SketchBrush): ImageVector = when (brush) {
    SketchBrush.Pen -> Icons.Default.Edit
    SketchBrush.Marker -> Icons.Default.Brush
    SketchBrush.Flat -> Icons.Default.FormatSize
    SketchBrush.Calligraphy -> Icons.Default.DriveFileRenameOutline
    SketchBrush.Highlighter -> Icons.Default.ColorLens
    SketchBrush.Dashed -> Icons.Default.MoreHoriz
}

@Composable
internal fun SketchPaperMenu(state: SketchState, onChooseImage: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val hasImage = state.background != null

    @Composable
    fun RatioItem(text: String, ratio: Float?) {
        val current = state.aspectRatio
        val selected = !hasImage && if (ratio == null || current == null) {
            ratio == current
        } else {
            abs(ratio - current) < 0.01f
        }
        DropdownMenuItem(
            text = { Text(text) },
            enabled = !hasImage,
            trailingIcon = if (selected) {
                { Icon(Icons.Default.Check, contentDescription = null) }
            } else {
                null
            },
            onClick = {
                expanded = false
                state.useAspectRatio(ratio)
            },
        )
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = stringResource(R.string.media_creation_sketch_paper),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RatioItem(
                text = stringResource(R.string.media_creation_sketch_paper_auto),
                ratio = null,
            )
            SketchDefaults.AspectRatios.forEach { (width, height) ->
                RatioItem(text = "$width:$height", ratio = width.toFloat() / height)
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.media_creation_sketch_choose_image)) },
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                onClick = {
                    expanded = false
                    onChooseImage()
                },
            )
            if (hasImage) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.media_creation_sketch_remove_image)) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                    onClick = {
                        expanded = false
                        state.useBackground(null)
                    },
                )
            }
        }
    }
}

@Composable
internal fun SketchColorPicker(state: SketchState, modifier: Modifier = Modifier) {
    val hsv = remember {
        FloatArray(3).also { values ->
            android.graphics.Color.colorToHSV(
                (state.customColor ?: SketchDefaults.CustomColor).toArgb(),
                values,
            )
        }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, saturation, value)

    fun update(newHue: Float, newSaturation: Float, newValue: Float) {
        hue = newHue
        saturation = newSaturation
        value = newValue
        state.useCustom(Color.hsv(newHue, newSaturation, newValue))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            ColorSlider(
                value = hue,
                valueRange = 0f..360f,
                trackColors = SketchDefaults.HueColors,
                thumbColor = Color.hsv(hue, 1f, 1f),
                onValueChange = { update(it, saturation, value) },
            )
            ColorSlider(
                value = saturation,
                valueRange = 0f..1f,
                trackColors = listOf(
                    Color.hsv(hue, 0f, value),
                    Color.hsv(hue, 1f, value),
                ),
                thumbColor = color,
                onValueChange = { update(hue, it, value) },
            )
            ColorSlider(
                value = value,
                valueRange = 0f..1f,
                trackColors = listOf(
                    Color.Black,
                    Color.hsv(hue, saturation, 1f),
                ),
                thumbColor = color,
                onValueChange = { update(hue, saturation, it) },
            )
        }
    }
}

@Composable
private fun ColorSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    trackColors: List<Color>,
    thumbColor: Color,
    onValueChange: (Float) -> Unit,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val span = valueRange.endInclusive - valueRange.start
    val outline = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress {
                    currentOnValueChange(it.coerceIn(valueRange))
                    true
                }
            }
            .pointerInput(valueRange) {
                val inset = ColorSliderThumbRadius.toPx()

                fun update(x: Float) {
                    val fraction = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    currentOnValueChange(valueRange.start + fraction * span)
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    update(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position.x)
                    }
                }
            },
    ) {
        val inset = ColorSliderThumbRadius.toPx()
        val trackHeight = 16.dp.toPx()
        drawRoundRect(
            brush = GradientBrush.horizontalGradient(
                colors = trackColors,
                startX = inset,
                endX = size.width - inset,
            ),
            topLeft = Offset(inset - trackHeight / 2f, center.y - trackHeight / 2f),
            size = Size(size.width - 2 * inset + trackHeight, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )
        val thumb = Offset(
            x = inset + (value - valueRange.start) / span * (size.width - 2 * inset),
            y = center.y,
        )
        drawCircle(Color.White, radius = inset, center = thumb)
        drawCircle(outline, radius = inset, center = thumb, style = Stroke(1.dp.toPx()))
        drawCircle(thumbColor, radius = inset - 3.dp.toPx(), center = thumb)
    }
}
