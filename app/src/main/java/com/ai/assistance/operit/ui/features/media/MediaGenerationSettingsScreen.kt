package com.ai.assistance.operit.ui.features.media

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.mediagen.model.MediaKind
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.MediaGenerationPreferences
import com.ai.assistance.operit.data.preferences.MediaGenerationSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGenerationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { MediaGenerationPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    val settings by preferences.settingsFlow.collectAsState(initial = MediaGenerationSettings())
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by remember { mutableStateOf<MediaGenerationProviderSetting?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaGenerationProviderSetting?>(null) }
    var showTypePicker by remember { mutableStateOf(false) }

    val current = editing
    if (current != null) {
        MediaGenerationProviderDetailScreen(
            provider = current,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    preferences.upsertProvider(updated)
                    editing = null
                    Toast.makeText(context, R.string.media_generation_save_success, Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                scope.launch {
                    preferences.deleteProvider(current.id)
                    editing = null
                }
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediaLargeTopBar(
                title = stringResource(R.string.settings_media_generation),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showTypePicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.media_generation_add_provider))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (settings.providers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                MediaEmptyState(
                    title = stringResource(R.string.media_generation_no_providers),
                    message = stringResource(R.string.settings_media_generation_subtitle),
                    actionLabel = stringResource(R.string.media_generation_add_provider),
                    onAction = { showTypePicker = true },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(settings.providers, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        onClick = { editing = provider },
                        onDelete = { deleteTarget = provider },
                    )
                }
            }
        }
    }

    if (showTypePicker) {
        ModalBottomSheet(onDismissRequest = { showTypePicker = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.media_generation_add_provider),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                providerFactories().forEach { factory ->
                    val provider = remember(factory) { factory() }
                    Surface(
                        onClick = {
                            showTypePicker = false
                            scope.launch {
                                preferences.upsertProvider(provider)
                                editing = provider
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = provider.typeLabel(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.media_generation_model_count, provider.models.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }

    deleteTarget?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.media_generation_delete_provider)) },
            text = { Text(stringResource(R.string.media_generation_delete_message, provider.displayName())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch { preferences.deleteProvider(provider.id) }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProviderCard(
    provider: MediaGenerationProviderSetting,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val imageLabel = stringResource(R.string.media_generation_image)
    val videoLabel = stringResource(R.string.media_generation_video)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = OrganicShape(provider.id.hashCode(), 7),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = provider.typeLabel().take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = provider.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = provider.typeLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                val counts = MediaKind.entries.map { kind ->
                    kind to provider.models.count { it.kind == kind }
                }.filter { it.second > 0 }
                if (counts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        counts.forEach { (kind, count) ->
                            val label = if (kind == MediaKind.IMAGE) imageLabel else videoLabel
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (kind == MediaKind.IMAGE) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                },
                            ) {
                                Text(
                                    text = "$label $count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (kind == MediaKind.IMAGE) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private fun providerFactories(): List<() -> MediaGenerationProviderSetting> = listOf(
    { MediaGenerationProviderSetting.OpenAI() },
    { MediaGenerationProviderSetting.Aliyun() },
    { MediaGenerationProviderSetting.Volcengine() },
    { MediaGenerationProviderSetting.MiniMax() },
    { MediaGenerationProviderSetting.OpenRouter() },
)
