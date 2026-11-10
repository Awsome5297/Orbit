package com.ai.assistance.operit.ui.features.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.MediaCreationSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import android.text.format.DateUtils

/** 会话封面的异形轮廓。用种子生成轻微起伏的圆角多边形，避免列表封面整齐划一。 */
internal class OrganicShape(
    private val seed: Int,
    private val lobes: Int = 7,
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        if (size.width <= 0f || size.height <= 0f) return Outline.Generic(path)
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val points = List(lobes) { index ->
            val angle = (2.0 * PI * index / lobes) - PI / 2.0
            val variation = ((seed ushr (index * 3 % 24)) and 0x7) / 7f
            val radius = 0.84f + 0.12f * variation
            androidx.compose.ui.geometry.Offset(
                x = centerX + (cos(angle) * centerX * radius).toFloat(),
                y = centerY + (sin(angle) * centerY * radius).toFloat(),
            )
        }
        fun midpoint(a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset) =
            androidx.compose.ui.geometry.Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

        val start = midpoint(points.last(), points.first())
        path.moveTo(start.x, start.y)
        points.forEachIndexed { index, point ->
            val next = points[(index + 1) % points.size]
            val end = midpoint(point, next)
            path.quadraticBezierTo(point.x, point.y, end.x, end.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

internal fun sessionCoverShape(id: String): Shape {
    val seed = id.hashCode()
    return OrganicShape(seed = seed, lobes = 6 + abs(seed) % 4)
}

internal fun isVideoFile(path: String?): Boolean {
    val extension = path?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
    return extension in setOf("mp4", "mov", "webm", "mkv", "avi", "m4v")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaLargeTopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaSmallTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
internal fun SessionLeading(
    session: MediaCreationSessionEntity,
    modifier: Modifier = Modifier,
) {
    val shape = remember(session.id) { sessionCoverShape(session.id) }
    val cover = remember(session.coverPath) { session.coverPath?.let(::File) }
    val hasCover = cover != null && cover.exists()
    val videoCover = hasCover && isVideoFile(cover?.path)
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when {
            session.activeCount > 0 -> androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                strokeWidth = 2.5.dp,
            )

            hasCover && !videoCover -> AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            else -> Icon(
                imageVector = if (videoCover) Icons.Default.PlayArrow else Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun MediaThumbnail(
    file: File,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (isVideo) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        } else {
            AsyncImage(
                model = file,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun sessionMetaText(session: MediaCreationSessionEntity): String {
    val relativeTime = remember(session.updatedAt) {
        DateUtils.getRelativeTimeSpanString(
            session.updatedAt,
            Date().time,
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    return if (session.activeCount > 0) {
        stringResource(R.string.media_creation_active_meta, session.activeCount, relativeTime)
    } else {
        "${stringResource(R.string.media_creation_items_count, session.nodeCount)} · $relativeTime"
    }
}

@Composable
internal fun MediaEmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Surface(
            modifier = Modifier.size(112.dp),
            shape = OrganicShape(seed = 7, lobes = 7),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

internal fun mediaMimeIsVideo(mimeType: String): Boolean = mimeType.startsWith("video/")

internal suspend fun saveMediaToGallery(
    context: Context,
    file: File,
    mimeType: String,
): Uri = withContext(Dispatchers.IO) {
    val isVideo = mediaMimeIsVideo(mimeType)
    val collection = if (isVideo) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = context.contentResolver.insert(collection, values)
        ?: throw IOException("无法创建媒体记录")
    try {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("无法写入媒体文件")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        uri
    } catch (error: Exception) {
        context.contentResolver.delete(uri, null, null)
        throw error
    }
}

internal fun shareMedia(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
