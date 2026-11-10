package com.ai.assistance.mediagen.provider.providers.minimax

import com.ai.assistance.mediagen.model.ImageRole
import com.ai.assistance.mediagen.model.MediaGenerationInput
import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaGenerationOutput
import com.ai.assistance.mediagen.model.MediaGenerationRequest
import com.ai.assistance.mediagen.model.MediaGenerationStatus
import com.ai.assistance.mediagen.model.MediaGenerationTask
import com.ai.assistance.mediagen.model.MediaInputKind
import com.ai.assistance.mediagen.provider.MediaGenerationApiException
import com.ai.assistance.mediagen.provider.MediaGenerationProvider
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64

class MiniMaxMediaGenerationProvider(
    private val client: OkHttpClient,
) : MediaGenerationProvider<MediaGenerationProviderSetting.MiniMax> {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(
        setting: MediaGenerationProviderSetting.MiniMax,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                request.extraParameters.forEach { (key, value) -> put(key, value) }
                put("model", model.modelId)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", requireNotNull(request.prompt))
                    })
                    request.inputs.forEach { input ->
                        val url = mediaUrl(input) ?: return@forEach
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", url) })
                            put(
                                "role",
                                when (input.role) {
                                    ImageRole.FIRST_FRAME -> "first_frame"
                                    ImageRole.LAST_FRAME -> "last_frame"
                                    ImageRole.REFERENCE -> "reference_image"
                                },
                            )
                        })
                    }
                })
                request.resolution?.let { put("resolution", it) }
                request.durationSeconds?.let { put("duration", it) }
                request.aspectRatio?.let { put("ratio", it) }
                request.callbackUrl?.let { put("callback_url", it) }
                request.watermark?.let { put("aigc_watermark", it) }
                require(request.count == null || request.count == 1) {
                    "MiniMax generates one video per task"
                }
            }
            val httpRequest = authorizedRequest(
                setting,
                "${setting.baseUrl.trimEnd('/')}/video_generation",
            ).post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val root = execute(httpRequest)
            val taskId = root["task_id"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("MiniMax response does not contain task_id")
            Result.success(
                MediaGenerationTask(
                    id = taskId,
                    status = MediaGenerationStatus.QUEUED,
                    metadata = root,
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun query(
        setting: MediaGenerationProviderSetting.MiniMax,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = authorizedRequest(
                setting,
                "${setting.baseUrl.trimEnd('/')}/query/video_generation/$taskId",
            ).get().build()
            Result.success(parseTask(execute(httpRequest)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun parseTask(root: JsonObject): MediaGenerationTask {
        val task = root["task"]?.jsonObject
            ?: throw IllegalArgumentException("MiniMax response does not contain task")
        val content = task["content"]?.jsonObject
        val videoUrl = content?.get("url")?.jsonPrimitive?.contentOrNull
        val error = task["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        return MediaGenerationTask(
            id = task["id"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("MiniMax task does not contain id"),
            status = when (task["status"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                "queued" -> MediaGenerationStatus.QUEUED
                "running" -> MediaGenerationStatus.RUNNING
                "succeeded" -> MediaGenerationStatus.SUCCEEDED
                "failed" -> MediaGenerationStatus.FAILED
                "cancelled", "canceled" -> MediaGenerationStatus.CANCELLED
                else -> MediaGenerationStatus.UNKNOWN
            },
            outputs = videoUrl?.let {
                listOf(MediaGenerationOutput(url = it, mimeType = "video/mp4"))
            }.orEmpty(),
            error = error,
            metadata = task,
        )
    }

    private fun mediaUrl(input: MediaGenerationInput): String? {
        if (input.kind != MediaInputKind.IMAGE) return null
        input.url?.takeIf { it.isNotBlank() }?.let { return it }
        val path = input.filePath?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.isFile) return null
        val mimeType = input.mimeType ?: when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        val encoded = Base64.getEncoder().encodeToString(file.readBytes())
        return "data:$mimeType;base64,$encoded"
    }

    private fun authorizedRequest(
        setting: MediaGenerationProviderSetting.MiniMax,
        url: String,
    ): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${setting.apiKey}")

    private fun execute(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw MediaGenerationApiException(
                    provider = "MiniMax",
                    statusCode = response.code,
                    code = null,
                    message = text.ifBlank { "MiniMax request failed: HTTP ${response.code}" },
                )
            }
            return json.parseToJsonElement(text).jsonObject
        }
    }
}
