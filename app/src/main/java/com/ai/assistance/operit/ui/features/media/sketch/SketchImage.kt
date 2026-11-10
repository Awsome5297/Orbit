package com.ai.assistance.operit.ui.features.media.sketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private const val TAG = "SketchImage"

private const val MAX_IMAGE_SIZE = 2048

private const val BLANK_EXPORT_SIZE = 1536f

internal class SketchResult(private val bitmap: Bitmap) {
    fun encode(): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}

internal suspend fun loadSketchBackground(context: Context, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIZE) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("Failed to decode $uri")

            decoded.upright(context, uri).asImageBitmap()
        }.onFailure {
            Log.e(TAG, "Failed to load $uri", it)
        }.getOrNull()
    }

private fun Bitmap.upright(context: Context, uri: Uri): Bitmap {
    if (context.contentResolver.getType(uri)?.startsWith("image/jpeg") != true) return this
    val exif = requireNotNull(context.contentResolver.openInputStream(uri)) {
        "Unable to read EXIF from $uri"
    }.use { ExifInterface(it) }
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

internal fun SketchState.toSketchResult(): SketchResult {
    val background = background
    val scale = if (background != null) {
        background.width / paperSize.width
    } else {
        BLANK_EXPORT_SIZE / maxOf(paperSize.width, paperSize.height)
    }
    val width = (paperSize.width * scale).roundToInt().coerceAtLeast(1)
    val height = (paperSize.height * scale).roundToInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = ComposeCanvas(bitmap),
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        drawSketch(this@toSketchResult, scale)
    }
    return SketchResult(bitmap.asAndroidBitmap())
}
