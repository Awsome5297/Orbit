package com.ai.assistance.operit.ui.features.media

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.ai.assistance.mediagen.model.ImageRole
import com.ai.assistance.mediagen.model.MediaGenerationInput
import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaGenerationRequest
import com.ai.assistance.mediagen.model.MediaGenerationStatus
import com.ai.assistance.mediagen.model.MediaInputKind
import com.ai.assistance.mediagen.model.MediaKind
import com.ai.assistance.mediagen.provider.MediaGenerationManager
import com.ai.assistance.mediagen.provider.capabilities
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

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
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var inputRole by remember { mutableStateOf(ImageRole.REFERENCE) }
    var selectedInputs by remember { mutableStateOf<List<MediaGenerationInput>>(emptyList()) }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyToCache(context, uri) }
                selectedInputs = selectedInputs + MediaGenerationInput(
                    kind = MediaInputKind.IMAGE,
                    role = inputRole,
                    filePath = path,
                    mimeType = mimeType,
                )
            }
        }
    }

    LaunchedEffect(generationModels) {
        if (selected == null || generationModels.none { it.first.id == selected?.first?.id && it.second.id == selected?.second?.id }) {
            selected = generationModels.firstOrNull()
        }
    }
    LaunchedEffect(records.size) {
        if (records.isNotEmpty()) listState.animateScrollToItem(records.lastIndex)
    }

    val current = session
    val title = when {
        current == null -> stringResource(R.string.media_creation_title)
        current.title.isNotBlank() -> current.title
        else -> stringResource(R.string.media_creation_unnamed)
    }
    val selectedModelLabel = selected?.second?.let { model ->
        if (model.displayName.isBlank()) model.modelId else model.displayName
    } ?: stringResource(R.string.media_creation_model)
    val selectedCapabilities = selected?.let { (provider, model) -> provider.capabilities(model.kind) }
    val requiresRemote = selectedCapabilities?.requiresRemoteInputs == true && selectedInputs.isNotEmpty()

    fun generate() {
        val selection = selected ?: return
        val text = prompt.trim()
        if (text.isEmpty() || generating || requiresRemote) return
        generating = true
        scope.launch {
            val recordId = repository.createRecord(
                sessionId = sessionId,
                providerId = selection.first.id,
                providerName = selection.first.name,
                modelId = selection.second.modelId,
                kind = selection.second.kind,
                prompt = text,
            )
            try {
                manager.generate(
                    setting = selection.first,
                    model = selection.second,
                    request = MediaGenerationRequest(prompt = text, count = 1, inputs = selectedInputs),
                ).collect { task ->
                    repository.finishRecord(recordId, task.status.name, task.id, task.error)
                    if (task.status == MediaGenerationStatus.SUCCEEDED) {
                        val directory = repository.recordDir(sessionId, recordId)
                        task.outputs.forEachIndexed { index, output ->
                            val bytes = output.data ?: output.url?.let { url ->
                                download(client, selection.first, url)
                            } ?: return@forEachIndexed
                            val mimeType = output.mimeType ?: "image/png"
                            val extension = mimeType.substringAfter('/', "png").substringBefore('+')
                            val file = File(directory, "output_${index}.$extension")
                            file.writeBytes(bytes)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_media_generation))
                    }
                },
            )
        },
        bottomBar = {
            if (generationModels.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedModelLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Box {
                                TextButton(onClick = { modelMenuExpanded = true }) {
                                    Text(selectedModelLabel)
                                }
                                DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                                    generationModels.forEach { pair ->
                                        DropdownMenuItem(
                                            text = { Text(pair.second.displayName.ifBlank { pair.second.modelId }) },
                                            onClick = {
                                                selected = pair
                                                modelMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                TextButton(onClick = { roleMenuExpanded = true }) {
                                    Text(roleLabel(inputRole))
                                }
                                DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                                    ImageRole.entries.forEach { role ->
                                        DropdownMenuItem(
                                            text = { Text(roleLabel(role)) },
                                            onClick = {
                                                inputRole = role
                                                roleMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { pickMedia.launch("image/*") }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text(stringResource(R.string.media_creation_add_image))
                            }
                        }
                        selectedInputs.forEachIndexed { index, input ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(roleLabel(input.role), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    File(input.filePath.orEmpty()).name,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    maxLines = 1,
                                )
                                IconButton(onClick = {
                                    selectedInputs = selectedInputs.filterIndexed { i, _ -> i != index }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        }
                        if (requiresRemote) {
                            Text(
                                stringResource(R.string.media_creation_requires_remote),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.media_creation_prompt_hint)) },
                            minLines = 1,
                            maxLines = 4,
                        )
                        Button(
                            onClick = ::generate,
                            enabled = prompt.isNotBlank() && selected != null && !generating && !requiresRemote,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (generating) stringResource(R.string.media_creation_generating)
                                else stringResource(R.string.media_creation_generate)
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (generationModels.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.media_creation_no_provider))
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.media_creation_open_settings))
                    }
                }
            }
        } else if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.media_creation_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    MediaCreationRecordCard(
                        record = record,
                        repository = repository,
                        onDelete = { scope.launch { repository.deleteRecord(sessionId, record.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCreationRecordCard(
    record: MediaCreationRecordEntity,
    repository: MediaCreationRepository,
    onDelete: () -> Unit,
) {
    val outputs by repository.observeOutputs(record.id).collectAsState(initial = emptyList())
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.prompt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
            Text(
                text = record.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            record.error?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            outputs.forEach { output ->
                if (output.mimeType.startsWith("video/")) {
                    VideoOutput(File(output.path))
                } else {
                    AsyncImage(
                        model = File(output.path),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
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

@Composable
private fun VideoOutput(file: File) {
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
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
    )
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
