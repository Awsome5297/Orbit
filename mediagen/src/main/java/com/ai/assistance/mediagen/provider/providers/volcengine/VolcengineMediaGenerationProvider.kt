package com.ai.assistance.mediagen.provider.providers.volcengine

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64
import java.util.UUID

class VolcengineMediaGenerationProvider(
    private val client: OkHttpClient,
) : MediaGenerationProvider<MediaGenerationProviderSetting.Volcengine> {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(
        setting: MediaGenerationProviderSetting.Volcengine,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> = withContext(Dispatchers.IO) {
        try {
            val task = when (model.kind.name) {
                "IMAGE" -> createImage(setting, model, request)
                "VIDEO" -> createVideo(setting, model, request)
                else -> error("Unsupported media kind: ${model.kind}")
            }
            Result.success(task)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun query(
        setting: MediaGenerationProviderSetting.Volcengine,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> = withContext(Dispatchers.IO) {
        try {
            val request = authorizedRequest(
                setting,
                "${setting.baseUrl.trimEnd('/')}/contents/generations/tasks/$taskId",
            ).get().build()
            Result.success(parseVideoTask(execute(request)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun createImage(
        setting: MediaGenerationProviderSetting.Volcengine,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationTask {
        val body = buildJsonObject {
            request.extraParameters.forEach { (key, value) -> put(key, value) }
            put("model", model.modelId)
            request.prompt?.takeIf { it.isNotBlank() }?.let { put("prompt", it) }
            val images = request.inputs.mapNotNull { input ->
                mediaUrl(input)?.let { kotlinx.serialization.json.JsonPrimitive(it) }
            }
            when (images.size) {
                0 -> Unit
                1 -> put("image", images.single())
                else -> put("image", JsonArray(images))
            }
            request.resolution?.let { put("size", it) }
            request.watermark?.let { put("watermark", it) }
        }
        val httpRequest = authorizedRequest(
            setting,
            "${setting.baseUrl.trimEnd('/')}/images/generations",
        ).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return parseImageTask(execute(httpRequest))
    }

    private fun createVideo(
        setting: MediaGenerationProviderSetting.Volcengine,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationTask {
        val body = buildJsonObject {
            request.extraParameters.forEach { (key, value) -> put(key, value) }
            put("model", model.modelId)
            put("content", buildJsonArray {
                request.prompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", prompt)
                    })
                }
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
            request.aspectRatio?.let { put("ratio", it) }
            request.durationSeconds?.let { put("duration", it) }
            request.generateAudio?.let { put("generate_audio", it) }
            request.watermark?.let { put("watermark", it) }
            request.seed?.let { put("seed", it) }
            request.callbackUrl?.let { put("callback_url", it) }
            require(request.count == null || request.count == 1) {
                "Volcengine generates one video per task"
            }
        }
        val httpRequest = authorizedRequest(
            setting,
            "${setting.baseUrl.trimEnd('/')}/contents/generations/tasks",
        ).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return parseVideoTask(execute(httpRequest))
    }

    private fun parseImageTask(root: JsonObject): MediaGenerationTask {
        val items = root["data"]?.jsonArray.orEmpty()
        val outputs = items.mapNotNull { element ->
            val item = element.jsonObject
            val url = item["url"]?.jsonPrimitive?.contentOrNull
            val base64 = item["b64_json"]?.jsonPrimitive?.contentOrNull
            when {
                base64 != null -> MediaGenerationOutput(
                    data = Base64.getDecoder().decode(base64),
                    mimeType = "image/png",
                )
                url != null -> MediaGenerationOutput(url = url, mimeType = "image/jpeg")
                else -> null
            }
        }
        return MediaGenerationTask(
            id = UUID.randomUUID().toString(),
            status = if (outputs.isEmpty()) MediaGenerationStatus.FAILED else MediaGenerationStatus.SUCCEEDED,
            outputs = outputs,
            error = if (outputs.isEmpty()) "Volcengine returned no image" else null,
            metadata = root,
        )
    }

    private fun parseVideoTask(root: JsonObject): MediaGenerationTask {
        val content = root["content"]?.jsonObject
        val videoUrl = content?.get("video_url")?.jsonPrimitive?.contentOrNull
        val error = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        return MediaGenerationTask(
            id = root["id"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("Volcengine response does not contain id"),
            status = when (root["status"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                "queued" -> MediaGenerationStatus.QUEUED
                "running" -> MediaGenerationStatus.RUNNING
                "succeeded" -> MediaGenerationStatus.SUCCEEDED
                "failed" -> MediaGenerationStatus.FAILED
                "cancelled", "canceled" -> MediaGenerationStatus.CANCELLED
                "expired" -> MediaGenerationStatus.EXPIRED
                else -> MediaGenerationStatus.UNKNOWN
            },
            outputs = videoUrl?.let {
                listOf(MediaGenerationOutput(url = it, mimeType = "video/mp4"))
            }.orEmpty(),
            error = error,
            metadata = root,
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
        setting: MediaGenerationProviderSetting.Volcengine,
        url: String,
    ): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${setting.apiKey}")

    private fun execute(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw MediaGenerationApiException(
                    provider = "Volcengine",
                    statusCode = response.code,
                    code = null,
                    message = text.ifBlank { "Volcengine request failed: HTTP ${response.code}" },
                )
            }
            return json.parseToJsonElement(text).jsonObject
        }
    }
}
