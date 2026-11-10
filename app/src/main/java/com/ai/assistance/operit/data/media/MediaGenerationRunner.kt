package com.ai.assistance.operit.data.media

import android.content.Context
import com.ai.assistance.mediagen.model.MediaGenerationInput
import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaGenerationRequest
import com.ai.assistance.mediagen.model.MediaGenerationStatus
import com.ai.assistance.mediagen.model.MediaGenerationTask
import com.ai.assistance.mediagen.provider.MediaGenerationManager
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
import com.ai.assistance.mediagen.provider.providers.aliyun.AliyunMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.minimax.MiniMaxMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.openai.OpenAIMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.openrouter.OpenRouterMediaGenerationProvider
import com.ai.assistance.mediagen.provider.providers.volcengine.VolcengineMediaGenerationProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.MediaCreationOutputEntity
import com.ai.assistance.operit.services.MediaGenerationService
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class MediaGenerationCompletion(
    val sessionId: String,
    val success: Boolean,
)

/**
 * 媒体生成任务不依赖页面生命周期。页面退出后任务继续执行，后台期间由前台服务保活。
 */
internal class MediaGenerationRunner private constructor(context: Context) {
    companion object {
        private const val TAG = "MediaGenerationRunner"

        @Volatile
        private var instance: MediaGenerationRunner? = null

        fun getInstance(context: Context): MediaGenerationRunner =
            instance ?: synchronized(this) {
                instance ?: MediaGenerationRunner(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val repository = MediaCreationRepository.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.MINUTES).build()
    private val manager = MediaGenerationManager(
        OpenAIMediaGenerationProvider(client),
        AliyunMediaGenerationProvider(client),
        VolcengineMediaGenerationProvider(client),
        MiniMaxMediaGenerationProvider(client),
        OpenRouterMediaGenerationProvider(client),
    )

    private val lock = Any()

    private val _activeSessions = MutableStateFlow<Set<String>>(emptySet())
    val activeSessions = _activeSessions.asStateFlow()

    private val _completions = MutableSharedFlow<MediaGenerationCompletion>(extraBufferCapacity = 16)
    val completions = _completions.asSharedFlow()

    init {
        val processStartedAt = System.currentTimeMillis()
        scope.launch {
            runCatching {
                repository.failInterruptedRecords(
                    before = processStartedAt,
                    error = appContext.getString(R.string.media_generation_interrupted),
                )
            }.onFailure {
                AppLogger.e(TAG, "Failed to recover interrupted media generation", it)
            }
        }
    }

    fun start(
        sessionId: String,
        provider: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        prompt: String,
        inputs: List<MediaGenerationInput>,
        count: Int?,
        resolution: String?,
    ): Boolean {
        if (prompt.isBlank() && inputs.isEmpty()) return false

        synchronized(lock) {
            if (sessionId in _activeSessions.value) return false
            val wasEmpty = _activeSessions.value.isEmpty()
            _activeSessions.value = _activeSessions.value + sessionId
            if (wasEmpty) {
                runCatching { MediaGenerationService.start(appContext) }
                    .onFailure {
                        AppLogger.w(TAG, "Failed to start media generation foreground service", it)
                    }
            }
        }

        scope.launch {
            runGeneration(
                sessionId = sessionId,
                provider = provider,
                model = model,
                prompt = prompt,
                inputs = inputs,
                count = count,
                resolution = resolution,
            )
        }
        return true
    }

    private suspend fun runGeneration(
        sessionId: String,
        provider: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        prompt: String,
        inputs: List<MediaGenerationInput>,
        count: Int?,
        resolution: String?,
    ) {
        var recordId: String? = null
        var success = false
        try {
            val createdRecordId = repository.createRecord(
                sessionId = sessionId,
                providerId = provider.id,
                providerName = provider.name,
                modelId = model.modelId,
                kind = model.kind,
                prompt = prompt,
                inputs = inputs,
            )
            recordId = createdRecordId

            manager.generate(
                setting = provider,
                model = model,
                request = MediaGenerationRequest(
                    prompt = prompt,
                    inputs = inputs,
                    count = count,
                    resolution = resolution,
                ),
            ).collect { task ->
                repository.finishRecord(
                    recordId = createdRecordId,
                    status = task.status.name,
                    taskId = task.id,
                    error = task.error,
                )
                success = task.status == MediaGenerationStatus.SUCCEEDED
                if (success) {
                    saveOutputs(sessionId, createdRecordId, task, provider)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.e(TAG, "Media generation failed for session $sessionId", error)
            success = false
            recordId?.let {
                repository.finishRecord(
                    recordId = it,
                    status = MediaGenerationStatus.FAILED.name,
                    error = error.message,
                )
            }
        } finally {
            _completions.tryEmit(MediaGenerationCompletion(sessionId, success))
            synchronized(lock) {
                _activeSessions.value = _activeSessions.value - sessionId
                if (_activeSessions.value.isEmpty()) {
                    MediaGenerationService.stop(appContext)
                }
            }
        }
    }

    private suspend fun saveOutputs(
        sessionId: String,
        recordId: String,
        task: MediaGenerationTask,
        provider: MediaGenerationProviderSetting,
    ) {
        val directory = repository.recordDir(sessionId, recordId)
        task.outputs.forEachIndexed { index, output ->
            val bytes = output.data ?: output.url?.let { url ->
                withContext(Dispatchers.IO) { download(provider, url) }
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

    private fun download(provider: MediaGenerationProviderSetting, url: String): ByteArray {
        val headerValues = provider.downloadHeaders(url)
        val request = Request.Builder()
            .url(url)
            .apply { if (headerValues.isNotEmpty()) headers(headerValues.toHeaders()) }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Media download failed: HTTP ${response.code}")
            }
            return response.body?.bytes()
                ?: throw IllegalStateException("Media download returned an empty body")
        }
    }
}
