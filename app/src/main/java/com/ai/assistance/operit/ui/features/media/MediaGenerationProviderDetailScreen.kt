package com.ai.assistance.operit.ui.features.media

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaKind
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.main.components.LocalSetScreenSoftInputMode
import com.ai.assistance.operit.ui.main.components.LocalSetUseScreenImePadding

internal fun MediaGenerationProviderSetting.typeLabel(): String = when (this) {
    is MediaGenerationProviderSetting.OpenAI -> "OpenAI"
    is MediaGenerationProviderSetting.Aliyun -> "阿里云百炼"
    is MediaGenerationProviderSetting.Volcengine -> "火山方舟"
    is MediaGenerationProviderSetting.MiniMax -> "MiniMax"
    is MediaGenerationProviderSetting.OpenRouter -> "OpenRouter"
}

internal fun MediaGenerationProviderSetting.displayName(): String =
    name.ifBlank { typeLabel() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaGenerationProviderDetailScreen(
    provider: MediaGenerationProviderSetting,
    onDismiss: () -> Unit,
    onSave: (MediaGenerationProviderSetting) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(provider.id) { mutableStateOf(provider) }
    var tab by remember { mutableIntStateOf(0) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val isCurrentScreen = LocalIsCurrentScreen.current
    val setScreenSoftInputMode = LocalSetScreenSoftInputMode.current
    val setUseScreenImePadding = LocalSetUseScreenImePadding.current
    BackHandler(onBack = onDismiss)

    SideEffect {
        if (isCurrentScreen) {
            setScreenSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setUseScreenImePadding(true)
        }
    }

    fun updateBasic(
        name: String = draft.name,
        apiKey: String = draft.apiKey,
        baseUrl: String = draft.baseUrl,
        workspaceId: String = (draft as? MediaGenerationProviderSetting.Aliyun)?.workspaceId.orEmpty(),
    ) {
        val current = draft
        draft = when (current) {
            is MediaGenerationProviderSetting.Aliyun -> current.copy(
                name = name,
                apiKey = apiKey,
                baseUrl = baseUrl,
                workspaceId = workspaceId,
            )
            else -> current.copyProvider(name = name, apiKey = apiKey, baseUrl = baseUrl)
        }
    }

    Scaffold(
        topBar = {
            MediaTopBar(
                title = draft.displayName(),
                onNavigate = onDismiss,
                titleLeading = { ProviderLogo(provider = draft, size = 24.dp) },
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { deleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.media_generation_delete_provider),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            onSave(
                                draft.copyProvider(
                                    models = draft.models.filter { it.modelId.isNotBlank() },
                                )
                            )
                        },
                        enabled = draft.name.isNotBlank() &&
                            draft.baseUrl.isNotBlank() &&
                            draft.models.any { it.modelId.isNotBlank() },
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.save))
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text(stringResource(R.string.media_generation_basic_tab)) },
                    leadingIcon = {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text(stringResource(R.string.media_generation_models_tab)) },
                    leadingIcon = {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
            if (tab == 0) {
                BasicSettings(
                    draft = draft,
                    onNameChange = { updateBasic(name = it) },
                    onApiKeyChange = { updateBasic(apiKey = it) },
                    onBaseUrlChange = { updateBasic(baseUrl = it) },
                    onWorkspaceIdChange = { updateBasic(workspaceId = it) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                ModelSettings(
                    draft = draft,
                    onModelsChange = { models ->
                        draft = draft.copyProvider(models = models)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.media_generation_delete_provider)) },
            text = { Text(stringResource(R.string.media_generation_delete_message, draft.displayName())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirm = false
                        onDelete()
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BasicSettings(
    draft: MediaGenerationProviderSetting,
    onNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onWorkspaceIdChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.media_generation_type),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = draft.typeLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = draft.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.media_generation_name)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = draft.apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.media_generation_api_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = draft.baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.media_generation_base_url)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        if (draft is MediaGenerationProviderSetting.Aliyun) {
            OutlinedTextField(
                value = draft.workspaceId,
                onValueChange = onWorkspaceIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.media_generation_workspace_id)) },
                supportingText = { Text(stringResource(R.string.media_generation_workspace_id_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

@Composable
private fun ModelSettings(
    draft: MediaGenerationProviderSetting,
    onModelsChange: (List<MediaGenerationModel>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val supportedKinds = draft.supportedKinds
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (draft.models.isEmpty()) {
            Text(
                text = stringResource(R.string.media_generation_no_models),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        draft.models.forEach { model ->
            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = model.modelId,
                            onValueChange = { value ->
                                onModelsChange(
                                    draft.models.map {
                                        if (it.id == model.id) it.copy(modelId = value.trim()) else it
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.media_generation_model_id)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                        )
                        IconButton(
                            onClick = { onModelsChange(draft.models.filterNot { it.id == model.id }) }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    if (supportedKinds.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            supportedKinds.forEach { kind ->
                                FilterChip(
                                    selected = model.kind == kind,
                                    onClick = {
                                        onModelsChange(
                                            draft.models.map {
                                                if (it.id == model.id) it.copy(kind = kind) else it
                                            }
                                        )
                                    },
                                    label = { Text(modelKindLabel(kind)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        Card(
            onClick = {
                onModelsChange(
                    draft.models + MediaGenerationModel(
                        modelId = "",
                        kind = supportedKinds.first(),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.media_generation_add_model),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.media_generation_add_model_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun modelKindLabel(kind: MediaKind): String = stringResource(
    when (kind) {
        MediaKind.IMAGE -> R.string.media_generation_image
        MediaKind.VIDEO -> R.string.media_generation_video
    }
)
