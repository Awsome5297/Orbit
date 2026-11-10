package com.ai.assistance.operit.ui.features.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaKind
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.MediaGenerationPreferences
import com.ai.assistance.operit.data.preferences.MediaGenerationSettings
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGenerationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { MediaGenerationPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    val settings by preferences.settingsFlow.collectAsState(initial = MediaGenerationSettings())
    var showTypePicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MediaGenerationProviderSetting?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaGenerationProviderSetting?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_media_generation)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showTypePicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.media_generation_add_provider))
                    }
                },
            )
        },
    ) { padding ->
        if (settings.providers.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.media_generation_no_providers), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showTypePicker = true }) {
                    Text(stringResource(R.string.media_generation_add_provider))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(settings.providers, key = { it.id }) { provider ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(provider.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    provider.baseUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Text(
                                    stringResource(R.string.media_generation_model_count, provider.models.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { editing = provider }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                            }
                            IconButton(onClick = { pendingDelete = provider }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTypePicker) {
        AlertDialog(
            onDismissRequest = { showTypePicker = false },
            title = { Text(stringResource(R.string.media_generation_add_provider)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    providerTypes().forEach { type ->
                        TextButton(
                            onClick = {
                                showTypePicker = false
                                editing = defaultProvider(type)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(type) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTypePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    editing?.let { provider ->
        MediaGenerationProviderEditDialog(
            provider = provider,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch { preferences.upsertProvider(updated) }
            },
        )
    }

    pendingDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(provider.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch { preferences.deleteProvider(provider.id) }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MediaGenerationProviderEditDialog(
    provider: MediaGenerationProviderSetting,
    onDismiss: () -> Unit,
    onSave: (MediaGenerationProviderSetting) -> Unit,
) {
    var name by remember(provider.id) { mutableStateOf(provider.name) }
    var apiKey by remember(provider.id) { mutableStateOf(provider.apiKey) }
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var workspaceId by remember(provider.id) {
        mutableStateOf((provider as? MediaGenerationProviderSetting.Aliyun)?.workspaceId.orEmpty())
    }
    var modelsText by remember(provider.id) {
        mutableStateOf(provider.models.joinToString("\n") { "${it.modelId}|${it.kind.name}" })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.media_generation_edit_provider, provider.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.media_generation_name)) }, singleLine = true)
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") }, singleLine = true)
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true)
                if (provider is MediaGenerationProviderSetting.Aliyun) {
                    OutlinedTextField(
                        workspaceId,
                        { workspaceId = it },
                        label = { Text("Workspace ID") },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    modelsText,
                    { modelsText = it },
                    label = { Text(stringResource(R.string.media_generation_models)) },
                    supportingText = { Text(stringResource(R.string.media_generation_models_hint)) },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val models = parseModels(modelsText)
                    val updated = when (provider) {
                        is MediaGenerationProviderSetting.Aliyun -> provider.copy(
                            name = name,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            workspaceId = workspaceId,
                            models = models,
                        )
                        else -> provider.copyProvider(
                            name = name,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            models = models,
                        )
                    }
                    onSave(updated)
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && parseModels(modelsText).isNotEmpty(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun providerTypes(): List<String> = listOf("OpenAI", "Aliyun", "Volcengine", "MiniMax", "OpenRouter")

private fun defaultProvider(type: String): MediaGenerationProviderSetting = when (type) {
    "Aliyun" -> MediaGenerationProviderSetting.Aliyun()
    "Volcengine" -> MediaGenerationProviderSetting.Volcengine()
    "MiniMax" -> MediaGenerationProviderSetting.MiniMax()
    "OpenRouter" -> MediaGenerationProviderSetting.OpenRouter()
    else -> MediaGenerationProviderSetting.OpenAI()
}

private fun parseModels(text: String): List<MediaGenerationModel> =
    text.lineSequence().mapNotNull { line ->
        val value = line.trim()
        if (value.isBlank()) return@mapNotNull null
        val parts = value.split('|', limit = 2)
        val modelId = parts[0].trim()
        if (modelId.isBlank()) return@mapNotNull null
        val kind = if (parts.getOrNull(1)?.trim()?.equals("VIDEO", ignoreCase = true) == true) {
            MediaKind.VIDEO
        } else {
            MediaKind.IMAGE
        }
        MediaGenerationModel(id = UUID.randomUUID().toString(), modelId = modelId, kind = kind)
    }.toList()
