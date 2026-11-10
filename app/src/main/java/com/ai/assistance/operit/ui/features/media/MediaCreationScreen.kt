package com.ai.assistance.operit.ui.features.media

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ai.assistance.mediagen.model.ImageRole
import com.ai.assistance.mediagen.model.MediaGenerationInput
import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaGenerationRequest
import com.ai.assistance.mediagen.model.MediaGenerationStatus
import com.ai.assistance.mediagen.model.MediaInputKind
import com.ai.assistance.mediagen.model.MediaKind
import com.ai.assistance.mediagen.provider.MediaGenerationManager
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
import com.ai.assistance.mediagen.provider.capabilities
import com.ai.assistance.mediagen.provider.providers.aliyun.AliyunMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.minimax.MiniMaxMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.openai.OpenAIMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.openrouter.OpenRouterMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.volcengine.VolcengineMediaGenerationProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.media.MediaCreationRepository
import com.ai.assistance.operit.data.model.MediaCreationOutputEntity
import com.ai.assistance.operit.data.model.MediaCreationRecordEntity
import com.ai.assistance.operit.data.preferences.MediaGenerationPreferences
import com.ai.assistance.operit.data.preferences.MediaGenerationSettings
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private data class MediaPreview(val file: File, val isVideo: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCreationScreen(
    sessionId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { MediaCreationRepository.getInstance(context) }
    val preferences = remember(context) { MediaGenerationPreferences.getInstance(context) }
    val settings by preferences.settingsFlow.collectAsState(initial = MediaGenerationSettings())
    val session by repository.observeSession(sessionId).collectAsState(initial = null)
    val records by repository.observeRecords(sessionId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val client = remember { OkHttpClient.Builder().readTimeout(10, TimeUnit.MINUTES).build() }
    val manager = remember(client) {
        MediaGenerationManager(
            OpenAIMediaGenerationProvider(client),
            AliyunMediaGenerationProvider(client),
            VolcengineMediaGenerationProvider(client),
            MiniMaxMediaGenerationProvider(client),
            OpenRouterMediaGenerationProvider(client),
        )
    }
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    val generationModels = settings.providers.flatMap { provider ->
        provider.models.mapNotNull { model ->
            val supported = (provider is MediaGenerationProviderSetting.OpenAI && model.kind == MediaKind.IMAGE) ||
                provider is MediaGenerationProviderSetting.Aliyun ||
                provider is MediaGenerationProviderSetting.Volcengine ||
                provider is MediaGenerationProviderSetting.MiniMax ||
                provider is MediaGenerationProviderSetting.OpenRouter
            if (supported) provider to model else null
        }
    }

    var selected by remember { mutableStateOf<Pair<MediaGenerationProviderSetting, MediaGenerationModel>?>(null) }
    var prompt by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var inputRole by remember { mutableStateOf(ImageRole.REFERENCE) }
    var selectedInputs by remember { mutableStateOf<List<MediaGenerationInput>>(emptyList()) }
    var preview by remember { mutableStateOf<MediaPreview?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaCreationRecordEntity?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyToCache(context, uri) }
                selectedInputs = selectedInputs + MediaGenerationInput(
                    kind = MediaInputKind.IMAGE,
                    role = inputRole,
                    filePath = path,
                    mimeType = context.contentResolver.getType(uri),
                )
            }
        }
    }

    LaunchedEffect(generationModels) {
        if (selected == null || generationModels.none {
                it.first.id == selected?.first?.id && it.second.id == selected?.second?.id
            }
        ) {
            selected = generationModels.firstOrNull()
        }
    }
    LaunchedEffect(records.size) {
        if (records.isNotEmpty()) listState.animateScrollToItem(records.lastIndex)
    }

    fun startGeneration(
        provider: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        text: String,
        inputs: List<MediaGenerationInput>,
    ) {
        if (text.isBlank() || generating) return
        val capabilities = provider.capabilities(model.kind) ?: return
        if (capabilities.requiresRemoteInputs && inputs.isNotEmpty()) return
        generating = true
        scope.launch {
            val recordId = repository.createRecord(
                sessionId = sessionId,
                providerId = provider.id,
                providerName = provider.name,
                modelId = model.modelId,
                kind = model.kind,
                prompt = text,
                inputs = inputs,
            )
            try {
                manager.generate(
                    setting = provider,
                    model = model,
                    request = MediaGenerationRequest(prompt = text, count = 1, inputs = inputs),
                ).collect { task ->
                    repository.finishRecord(recordId, task.status.name, task.id, task.error)
                    if (task.status == MediaGenerationStatus.SUCCEEDED) {
                        val directory = repository.recordDir(sessionId, recordId)
                        task.outputs.forEachIndexed { index, output ->
                            val bytes = output.data ?: output.url?.let { url ->
                                withContext(Dispatchers.IO) { download(client, provider, url) }
                            } ?: return@forEachIndexed
                            val mimeType = output.mimeType ?: "image/png"
                            val extension = mimeType.substringAfter('/', "png").substringBefore('+')
                            val file = File(directory, "output_${index}.$extension")
                            withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                            repository.saveOutput(
                                sessionId = sessionId,
                                recordId = recordId,
                                output = MediaCreationOutputEntity(
                                    id = UUID.randomUUID().toString(),
                                    recordId = recordId,
                                    path = file.absolutePath,
                                    mimeType = mimeType,
                                ),
                            )
                        }
                    }
                }
                prompt = ""
            } catch (error: Exception) {
                repository.finishRecord(recordId, MediaGenerationStatus.FAILED.name, error = error.message)
            } finally {
                generating = false
            }
        }
    }

    fun generate() {
        val selection = selected ?: return
        startGeneration(selection.first, selection.second, prompt.trim(), selectedInputs)
    }

    fun regenerate(record: MediaCreationRecordEntity) {
        val provider = settings.providers.find { it.id == record.providerId } ?: return
        val model = provider.models.find { it.modelId == record.modelId } ?: return
        val inputs = json.decodeFromString<List<MediaGenerationInput>>(record.inputsJson)
        startGeneration(provider, model, record.prompt, inputs)
    }

    fun editRecord(record: MediaCreationRecordEntity) {
        val provider = settings.providers.find { it.id == record.providerId }
        val model = provider?.models?.find { it.modelId == record.modelId }
        if (provider != null && model != null) {
            selected = provider to model
        }
        prompt = record.prompt
        selectedInputs = json.decodeFromString<List<MediaGenerationInput>>(record.inputsJson)
        inputRole = selectedInputs.firstOrNull()?.role ?: ImageRole.REFERENCE
    }

    val currentTitle = when {
        session == null -> stringResource(R.string.media_creation_title)
        session?.title.isNullOrBlank() -> stringResource(R.string.media_creation_unnamed)
        else -> session?.title.orEmpty()
    }
    val selectedModelLabel = selected?.second?.let { model ->
        model.displayName.ifBlank { model.modelId }
    } ?: stringResource(R.string.media_creation_select_model)
    val selectedCapabilities = selected?.let { (provider, model) -> provider.capabilities(model.kind) }
    val requiresRemote = selectedCapabilities?.requiresRemoteInputs == true && selectedInputs.isNotEmpty()

    Scaffold(
        topBar = {
            MediaSmallTopBar(
                title = currentTitle,
                subtitle = selected?.let { (provider, model) ->
                    "${model.displayName.ifBlank { model.modelId }} · ${provider.name}"
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            if (generationModels.isNotEmpty()) {
                MediaComposer(
                    selectedModelLabel = selectedModelLabel,
                    models = generationModels,
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    inputs = selectedInputs,
                    inputRole = inputRole,
                    onRoleChange = { inputRole = it },
                    onPickMedia = { pickMedia.launch("image/*") },
                    onRemoveInput = { input ->
                        selectedInputs = selectedInputs.filterNot { it == input }
                    },
                    generating = generating,
                    requiresRemote = requiresRemote,
                    onGenerate = ::generate,
                    onSelectModel = { selected = it },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when {
            generationModels.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                MediaEmptyState(
                    title = stringResource(R.string.media_creation_no_provider_title),
                    message = stringResource(R.string.media_creation_no_provider),
                    actionLabel = stringResource(R.string.media_creation_configure_provider),
                    onAction = onOpenSettings,
                )
            }

            records.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                MediaEmptyState(
                    title = stringResource(R.string.media_creation_empty),
                    message = stringResource(R.string.media_creation_empty_hint),
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    val regenerateRecord: (() -> Unit)? = if (
                        settings.providers.any { provider ->
                            provider.id == record.providerId &&
                                provider.models.any { it.modelId == record.modelId }
                        }
                    ) {
                        { regenerate(record) }
                    } else {
                        null
                    }
                    MediaRecordCard(
                        record = record,
                        onPreview = { preview = it },
                        onEdit = { editRecord(record) },
                        onRegenerate = regenerateRecord,
                        onDelete = { deleteTarget = record },
                    )
                }
            }
        }
    }

    preview?.let { target ->
        MediaPreviewDialog(target = target, onDismiss = { preview = null })
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.media_creation_delete_title)) },
            text = { Text(stringResource(R.string.media_creation_delete_record_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch { repository.deleteRecord(sessionId, record.id) }
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
private fun MediaComposer(
    selectedModelLabel: String,
    models: List<Pair<MediaGenerationProviderSetting, MediaGenerationModel>>,
    prompt: String,
    onPromptChange: (String) -> Unit,
    inputs: List<MediaGenerationInput>,
    inputRole: ImageRole,
    onRoleChange: (ImageRole) -> Unit,
    onPickMedia: () -> Unit,
    onRemoveInput: (MediaGenerationInput) -> Unit,
    generating: Boolean,
    requiresRemote: Boolean,
    onGenerate: () -> Unit,
    onSelectModel: (Pair<MediaGenerationProviderSetting, MediaGenerationModel>) -> Unit,
) {
    var modelMenu by remember { mutableStateOf(false) }
    var roleMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (inputs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    inputs.forEach { input ->
                        InputTile(input = input, onRemove = { onRemoveInput(input) })
                    }
                }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.media_creation_prompt_hint)) },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                leadingIcon = {
                    IconButton(onClick = onPickMedia) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.media_creation_add_image))
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            if (requiresRemote) {
                Text(
                    text = stringResource(R.string.media_creation_requires_remote),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { modelMenu = true }) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = selectedModelLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        models.forEach { pair ->
                            DropdownMenuItem(
                                text = {
                                    Text(pair.second.displayName.ifBlank { pair.second.modelId })
                                },
                                onClick = {
                                    onSelectModel(pair)
                                    modelMenu = false
                                },
                            )
                        }
                    }
                }
                Box {
                    TextButton(onClick = { roleMenu = true }) {
                        Text(roleLabel(inputRole))
                    }
                    DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                        ImageRole.entries.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(roleLabel(role)) },
                                onClick = {
                                    onRoleChange(role)
                                    roleMenu = false
                                },
                            )
                        }
                    }
                }
                Button(
                    onClick = onGenerate,
                    enabled = prompt.isNotBlank() && !generating && !requiresRemote,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (generating) stringResource(R.string.media_creation_generating)
                        else stringResource(R.string.media_creation_generate)
                    )
                }
            }
        }
    }
}

@Composable
private fun InputTile(
    input: MediaGenerationInput,
    onRemove: () -> Unit,
) {
    val path = input.filePath.orEmpty()
    Box(modifier = Modifier.size(60.dp)) {
        MediaThumbnail(
            file = File(path),
            isVideo = input.kind == MediaInputKind.VIDEO,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.media_creation_remove_input),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = roleLabel(input.role),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaRecordCard(
    record: MediaCreationRecordEntity,
    onPreview: (MediaPreview) -> Unit,
    onEdit: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { MediaCreationRepository.getInstance(context) }
    val outputs by repository.observeOutputs(record.id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    val inputs = remember(record.inputsJson) {
        json.decodeFromString<List<MediaGenerationInput>>(record.inputsJson)
    }
    val previewOutput = outputs.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.modelId,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(record.status)
            }
            if (inputs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    inputs.forEach { input ->
                        Box(modifier = Modifier.size(52.dp)) {
                            MediaThumbnail(
                                file = File(input.filePath.orEmpty()),
                                isVideo = input.kind == MediaInputKind.VIDEO,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(10.dp),
                            )
                            Text(
                                text = roleLabel(input.role),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 3.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
            if (record.prompt.isNotBlank()) {
                Text(text = record.prompt, style = MaterialTheme.typography.bodyMedium)
            }
            when {
                record.status.isActiveStatus() -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = recordStatusLabel(record.status),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                record.status == MediaGenerationStatus.SUCCEEDED.name -> {
                    if (outputs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.media_creation_no_outputs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OutputGrid(
                            outputs = outputs,
                            onPreview = { output ->
                                onPreview(MediaPreview(File(output.path), mediaMimeIsVideo(output.mimeType)))
                            },
                        )
                    }
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = record.error ?: recordStatusLabel(record.status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (previewOutput != null) {
                    AssistChip(
                        onClick = {
                            scope.launch {
                                try {
                                    saveMediaToGallery(context, File(previewOutput.path), previewOutput.mimeType)
                                    Toast.makeText(context, R.string.media_creation_saved, Toast.LENGTH_SHORT).show()
                                } catch (error: Exception) {
                                    Toast.makeText(
                                        context,
                                        error.message ?: context.getString(R.string.media_creation_save_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.media_creation_save)) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    AssistChip(
                        onClick = {
                            try {
                                shareMedia(context, File(previewOutput.path), previewOutput.mimeType)
                            } catch (error: Exception) {
                                Toast.makeText(
                                    context,
                                    error.message ?: context.getString(R.string.media_creation_share_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        label = { Text(stringResource(R.string.media_creation_share)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                if (!record.status.isActiveStatus()) {
                    AssistChip(
                        onClick = onEdit,
                        label = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                if (onRegenerate != null && !record.status.isActiveStatus()) {
                    AssistChip(
                        onClick = onRegenerate,
                        label = { Text(stringResource(R.string.media_creation_rerun)) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                AssistChip(
                    onClick = onDelete,
                    label = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val container = when {
        status.isActiveStatus() -> MaterialTheme.colorScheme.tertiaryContainer
        status == MediaGenerationStatus.SUCCEEDED.name -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when {
        status.isActiveStatus() -> MaterialTheme.colorScheme.onTertiaryContainer
        status == MediaGenerationStatus.SUCCEEDED.name -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text = recordStatusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun OutputGrid(
    outputs: List<MediaCreationOutputEntity>,
    onPreview: (MediaCreationOutputEntity) -> Unit,
) {
    if (outputs.size == 1) {
        val output = outputs.single()
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onPreview(output) },
        ) {
            MediaThumbnail(
                file = File(output.path),
                isVideo = mediaMimeIsVideo(output.mimeType),
                modifier = Modifier.fillMaxWidth().height(210.dp),
                shape = RoundedCornerShape(14.dp),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            outputs.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { output ->
                        Box(modifier = Modifier.weight(1f)) {
                            MediaThumbnail(
                                file = File(output.path),
                                isVideo = mediaMimeIsVideo(output.mimeType),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clickable { onPreview(output) },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(
    target: MediaPreview,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (target.isVideo) {
                VideoPlayer(file = target.file, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = target.file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoPlayer(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun recordStatusLabel(status: String): String = stringResource(
    when (status) {
        "PREPARING" -> R.string.media_creation_status_preparing
        "QUEUED" -> R.string.media_creation_status_queued
        "RUNNING" -> R.string.media_creation_status_running
        "DOWNLOADING" -> R.string.media_creation_status_downloading
        "SUCCEEDED" -> R.string.media_creation_status_succeeded
        "FAILED" -> R.string.media_creation_status_failed
        "CANCELLED" -> R.string.media_creation_status_cancelled
        "EXPIRED" -> R.string.media_creation_status_expired
        else -> R.string.media_creation_status_unknown
    }
)

private fun String.isActiveStatus(): Boolean =
    this in setOf("PREPARING", "QUEUED", "RUNNING", "DOWNLOADING")

@Composable
private fun roleLabel(role: ImageRole): String = stringResource(
    when (role) {
        ImageRole.REFERENCE -> R.string.media_creation_role_reference
        ImageRole.FIRST_FRAME -> R.string.media_creation_role_first_frame
        ImageRole.LAST_FRAME -> R.string.media_creation_role_last_frame
    }
)

private fun copyToCache(context: Context, uri: Uri): String {
    val file = File(context.cacheDir, "media_input_${UUID.randomUUID()}")
    val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
        "Unable to open selected media"
    }
    input.use { source ->
        file.outputStream().use { target -> source.copyTo(target) }
    }
    return file.absolutePath
}

private fun download(
    client: OkHttpClient,
    provider: MediaGenerationProviderSetting,
    url: String,
): ByteArray? {
    val headerValues = provider.downloadHeaders(url)
    val request = Request.Builder()
        .url(url)
        .apply { if (headerValues.isNotEmpty()) headers(headerValues.toHeaders()) }
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        return response.body?.bytes()
    }
}
