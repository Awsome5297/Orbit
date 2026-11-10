package com.ai.assistance.operit.ui.features.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.MediaCreationSessionEntity
import com.ai.assistance.operit.ui.common.icons.ProviderLogoLoader
import com.ai.assistance.operit.ui.common.icons.rememberAssetLogoPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
            val radius = 0.72f + 0.26f * variation
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
    return OrganicShape(seed = seed, lobes = 5 + abs(seed) % 4)
}

internal fun isVideoFile(path: String?): Boolean {
    val extension = path?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
    return extension in setOf("mp4", "mov", "webm", "mkv", "avi", "m4v")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaLargeTopBar(
    title: String,
    onNavigate: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    circularNavigation: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            NavigationButton(
                onClick = onNavigate,
                icon = navigationIcon,
                circular = circularNavigation,
            )
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaSmallTopBar(
    title: String,
    subtitle: String? = null,
    onNavigate: () -> Unit,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    circularNavigation: Boolean = true,
    titleLeading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Row {
                if (titleLeading != null) {
                    titleLeading()
                    Spacer(Modifier.size(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
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
            }
        },
        navigationIcon = {
            NavigationButton(
                onClick = onNavigate,
                icon = navigationIcon,
                circular = circularNavigation,
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun NavigationButton(
    onClick: () -> Unit,
    icon: ImageVector,
    circular: Boolean,
) {
    if (circular) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null)
        }
    }
}

internal fun MediaGenerationProviderSetting.logoTypeId(): String = when (this) {
    is MediaGenerationProviderSetting.OpenAI -> "OPENAI"
    is MediaGenerationProviderSetting.Aliyun -> "ALIYUN"
    is MediaGenerationProviderSetting.Volcengine -> "DOUBAO"
    is MediaGenerationProviderSetting.MiniMax -> "MINIMAX"
    is MediaGenerationProviderSetting.OpenRouter -> "OPENROUTER"
}

@Composable
internal fun ProviderLogo(
    provider: MediaGenerationProviderSetting,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
    withContainer: Boolean = true,
) {
    val context = LocalContext.current
    val path = remember(provider.id, provider.logoTypeId()) {
        ProviderLogoLoader.findLogoAssetPath(context, provider.logoTypeId())
    }
    val painter = if (path != null) {
        rememberAssetLogoPainter(assetPath = path, size = size)
    } else {
        null
    }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (withContainer) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            Color.Transparent
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(if (withContainer) 5.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) {
                Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    text = provider.typeLabel().take(1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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

            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(12.dp),
                )
            }
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
    val time = remember(session.updatedAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.updatedAt))
    }
    return if (session.activeCount > 0) {
        stringResource(R.string.media_creation_active_meta, session.nodeCount, time)
    } else {
        "${stringResource(R.string.media_creation_items_count, session.nodeCount)} · $time"
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
