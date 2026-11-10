package com.ai.assistance.operit.ui.features.media

import android.content.Context
import android.net.Uri
import android.view.WindowManager
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ai.assistance.mediagen.model.ImageRole
import com.ai.assistance.mediagen.model.MediaGenerationInput
import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaGenerationStatus
import com.ai.assistance.mediagen.model.MediaInputKind
import com.ai.assistance.mediagen.model.MediaKind
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
import com.ai.assistance.mediagen.provider.capabilities
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.media.MediaCreationRepository
import com.ai.assistance.operit.data.media.MediaGenerationRunner
import com.ai.assistance.operit.data.model.MediaCreationOutputEntity
import com.ai.assistance.operit.data.model.MediaCreationRecordEntity
import com.ai.assistance.operit.data.preferences.MediaGenerationPreferences
import com.ai.assistance.operit.data.preferences.MediaGenerationSettings
import com.ai.assistance.operit.ui.features.media.sketch.MediaSketchDialog
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.main.components.LocalSetScreenSoftInputMode
import com.ai.assistance.operit.ui.main.components.LocalSetUseScreenImePadding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

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
    val recentOutputs by repository.observeRecentOutputs(sessionId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val runner = remember(context) { MediaGenerationRunner.getInstance(context) }
    val activeSessions by runner.activeSessions.collectAsState()
    val generating = sessionId in activeSessions
    val listState = rememberLazyListState()
    val isCurrentScreen = LocalIsCurrentScreen.current
    val setScreenSoftInputMode = LocalSetScreenSoftInputMode.current
    val setUseScreenImePadding = LocalSetUseScreenImePadding.current

    SideEffect {
        if (isCurrentScreen) {
            setScreenSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setUseScreenImePadding(true)
        }
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
    var preview by remember { mutableStateOf<MediaPreview?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaCreationRecordEntity?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showParams by remember { mutableStateOf(false) }
    var showAssetPicker by remember { mutableStateOf(false) }
    var showSketch by remember { mutableStateOf(false) }
    var selectedInputs by remember { mutableStateOf<List<MediaGenerationInput>>(emptyList()) }
    var resolution by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyToCache(context, uri) }
                selectedInputs = selectedInputs + MediaGenerationInput(
                    kind = MediaInputKind.IMAGE,
                    role = ImageRole.REFERENCE,
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
    LaunchedEffect(sessionId, runner) {
        runner.completions.collect { completion ->
            if (completion.sessionId == sessionId && completion.success) {
                prompt = ""
            }
        }
    }

    fun startGeneration(
        provider: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        text: String,
        inputs: List<MediaGenerationInput>,
    ) {
        if (text.isBlank()) return
        val capabilities = provider.capabilities(model.kind) ?: return
        if (capabilities.requiresRemoteInputs && inputs.isNotEmpty()) return
        runner.start(
            sessionId = sessionId,
            provider = provider,
            model = model,
            prompt = text,
            inputs = inputs,
            count = count,
            resolution = resolution,
        )
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
    }

    val currentTitle = when {
        session == null -> stringResource(R.string.media_creation_title)
        session?.title.isNullOrBlank() -> stringResource(R.string.media_creation_unnamed)
        else -> session?.title.orEmpty()
    }
    val selectedCapabilities = selected?.let { (provider, model) -> provider.capabilities(model.kind) }
    val requiresRemote = selectedCapabilities?.requiresRemoteInputs == true && selectedInputs.isNotEmpty()
    Scaffold(
        topBar = {
            MediaTopBar(
                title = currentTitle,
                onNavigate = onBack,
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
                    selected = selected,
                    inputs = selectedInputs,
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    generating = generating,
                    requiresRemote = requiresRemote,
                    onOpenAssets = { showAssetPicker = true },
                    onRemoveInput = { input ->
                        selectedInputs = selectedInputs.filterNot { it == input }
                    },
                    onOpenModelPicker = { showModelPicker = true },
                    onOpenParams = { showParams = true },
                    onGenerate = ::generate,
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
                SessionEmptyState()
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

    if (showModelPicker) {
        ModelPickerSheet(
            models = generationModels,
            selected = selected,
            onSelect = {
                selected = it
                showModelPicker = false
            },
            onManage = {
                showModelPicker = false
                onOpenSettings()
            },
            onDismiss = { showModelPicker = false },
        )
    }

    if (showParams) {
        MediaParamsSheet(
            resolution = resolution,
            count = count,
            onResolutionChange = { resolution = it },
            onCountChange = { count = it },
            onDismiss = { showParams = false },
        )
    }

    if (showAssetPicker) {
        AssetPickerSheet(
            recentOutputs = recentOutputs,
            onPickGallery = {
                showAssetPicker = false
                pickImage.launch("image/*")
            },
            onSketch = {
                showAssetPicker = false
                showSketch = true
            },
            onSelect = { output ->
                selectedInputs = selectedInputs + MediaGenerationInput(
                    kind = if (mediaMimeIsVideo(output.mimeType)) MediaInputKind.VIDEO else MediaInputKind.IMAGE,
                    role = ImageRole.REFERENCE,
                    filePath = output.path,
                    mimeType = output.mimeType,
                )
                showAssetPicker = false
            },
            onDismiss = { showAssetPicker = false },
        )
    }

    if (showSketch) {
        MediaSketchDialog(
            onDismiss = { showSketch = false },
            aspectRatio = resolutionAspectRatio(resolution),
            onSave = { file ->
                selectedInputs = selectedInputs + MediaGenerationInput(
                    kind = MediaInputKind.IMAGE,
                    role = ImageRole.REFERENCE,
                    filePath = file.absolutePath,
                    mimeType = "image/png",
                )
                showSketch = false
            },
        )
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
    selected: Pair<MediaGenerationProviderSetting, MediaGenerationModel>?,
    inputs: List<MediaGenerationInput>,
    prompt: String,
    onPromptChange: (String) -> Unit,
    generating: Boolean,
    requiresRemote: Boolean,
    onOpenAssets: () -> Unit,
    onRemoveInput: (MediaGenerationInput) -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenParams: () -> Unit,
    onGenerate: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                    IconButton(onClick = onOpenAssets) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.media_creation_add_reference))
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selected != null) {
                    ProviderLogo(
                        provider = selected.first,
                        size = 34.dp,
                        modifier = Modifier.clickable(onClick = onOpenModelPicker),
                        withContainer = false,
                    )
                } else {
                    IconButton(onClick = onOpenModelPicker) {
                        Icon(Icons.Default.Image, contentDescription = null)
                    }
                }
                AssistChip(
                    onClick = onOpenParams,
                    label = { Text(stringResource(R.string.media_creation_params)) },
                    leadingIcon = {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = onGenerate,
                    enabled = prompt.isNotBlank() && !generating && !requiresRemote,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.media_creation_generate),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetPickerSheet(
    recentOutputs: List<MediaCreationOutputEntity>,
    onPickGallery: () -> Unit,
    onSketch: () -> Unit,
    onSelect: (MediaCreationOutputEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val imageOutputs = recentOutputs.filterNot { mediaMimeIsVideo(it.mimeType) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.media_creation_add_reference),
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickGallery, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.media_creation_gallery_image))
                }
                OutlinedButton(onClick = onSketch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.media_creation_sketch))
                }
            }
            Text(
                text = stringResource(R.string.media_creation_recent_outputs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (imageOutputs.isEmpty()) {
                Text(
                    text = stringResource(R.string.media_creation_recent_outputs_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    gridItems(imageOutputs, key = { it.path }) { output ->
                        MediaThumbnail(
                            file = File(output.path),
                            isVideo = mediaMimeIsVideo(output.mimeType),
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(output) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InputTile(
    input: MediaGenerationInput,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(60.dp)) {
        MediaThumbnail(
            file = File(input.filePath.orEmpty()),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    models: List<Pair<MediaGenerationProviderSetting, MediaGenerationModel>>,
    selected: Pair<MediaGenerationProviderSetting, MediaGenerationModel>?,
    onSelect: (Pair<MediaGenerationProviderSetting, MediaGenerationModel>) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.media_creation_select_model),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                models.groupBy { it.first.id }.forEach { (_, pairs) ->
                    val provider = pairs.first().first
                    item(key = "provider-${provider.id}") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProviderLogo(provider = provider, size = 24.dp)
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = provider.displayName(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(pairs, key = { it.second.id }) { pair ->
                        val isSelected = selected?.let {
                            it.first.id == pair.first.id && it.second.id == pair.second.id
                        } == true
                        Surface(
                            onClick = { onSelect(pair) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                Color.Transparent
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    text = pair.second.displayName.ifBlank { pair.second.modelId },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = modelKindLabel(pair.second.kind),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onManage, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.media_generation_manage))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MediaParamsSheet(
    resolution: String?,
    count: Int?,
    onResolutionChange: (String?) -> Unit,
    onCountChange: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val resolutionPresets = listOf("1024x1024", "1536x1024", "1024x1536")
    var customResolution by remember {
        mutableStateOf(resolution?.takeIf { it !in resolutionPresets }.orEmpty())
    }
    var customCount by remember { mutableStateOf(count?.toString().orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.media_creation_generate_params),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        customResolution = ""
                        customCount = ""
                        onResolutionChange(null)
                        onCountChange(null)
                    }
                ) {
                    Text(stringResource(R.string.media_creation_restore_defaults))
                }
            }
            Text(
                text = stringResource(R.string.media_creation_default),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.media_creation_resolution),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = resolution == null,
                    onClick = {
                        customResolution = ""
                        onResolutionChange(null)
                    },
                    label = { Text(stringResource(R.string.media_creation_default)) },
                )
                resolutionPresets.forEach { preset ->
                    FilterChip(
                        selected = resolution == preset,
                        onClick = {
                            customResolution = ""
                            onResolutionChange(preset)
                        },
                        label = { Text(preset) },
                    )
                }
            }
            OutlinedTextField(
                value = customResolution,
                onValueChange = {
                    customResolution = it
                    onResolutionChange(it.ifBlank { null })
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.media_creation_resolution_custom_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Text(
                text = stringResource(R.string.media_creation_count),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = count == null,
                    onClick = {
                        customCount = ""
                        onCountChange(null)
                    },
                    label = { Text(stringResource(R.string.media_creation_default)) },
                )
                listOf(1, 2, 3, 4).forEach { value ->
                    FilterChip(
                        selected = count == value,
                        onClick = {
                            customCount = ""
                            onCountChange(value)
                        },
                        label = { Text(value.toString()) },
                    )
                }
            }
            OutlinedTextField(
                value = customCount,
                onValueChange = {
                    customCount = it
                    onCountChange(it.toIntOrNull()?.takeIf { value -> value > 0 })
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.media_creation_count_custom_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SessionEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Surface(
            modifier = Modifier.size(112.dp),
            shape = OrganicShape(seed = 11, lobes = 8),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = stringResource(R.string.media_creation_session_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun modelKindLabel(kind: MediaKind): String = stringResource(
    when (kind) {
        MediaKind.IMAGE -> R.string.media_generation_image
        MediaKind.VIDEO -> R.string.media_generation_video
    }
)

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
                if (outputs.isNotEmpty()) {
                    AssistChip(
                        onClick = {
                            scope.launch {
                                var saved = 0
                                var failed = 0
                                var lastError: String? = null
                                outputs.forEach { output ->
                                    try {
                                        saveMediaToGallery(context, File(output.path), output.mimeType)
                                        saved++
                                    } catch (error: Exception) {
                                        failed++
                                        lastError = error.message
                                    }
                                }
                                val message = when {
                                    failed == 0 -> context.getString(R.string.media_creation_saved_count, saved)
                                    saved > 0 -> context.getString(
                                        R.string.media_creation_saved_partial,
                                        saved,
                                        failed,
                                    )
                                    else -> lastError ?: context.getString(R.string.media_creation_save_failed)
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text(stringResource(R.string.media_creation_save)) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    previewOutput?.let { output ->
                        AssistChip(
                            onClick = {
                                try {
                                    shareMedia(context, File(output.path), output.mimeType)
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

private fun resolutionAspectRatio(resolution: String?): Float? {
    val parts = resolution?.split('x') ?: return null
    if (parts.size != 2) return null
    val width = parts[0].trim().toIntOrNull() ?: return null
    val height = parts[1].trim().toIntOrNull() ?: return null
    if (width <= 0 || height <= 0) return null
    return width.toFloat() / height
}

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
