package com.ai.assistance.mediagen.provider.providers.openrouter

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

class OpenRouterMediaGenerationProvider(
    private val client: OkHttpClient,
) : MediaGenerationProvider<MediaGenerationProviderSetting.OpenRouter> {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(
        setting: MediaGenerationProviderSetting.OpenRouter,
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
        setting: MediaGenerationProviderSetting.OpenRouter,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> = withContext(Dispatchers.IO) {
        try {
            val request = setting.request("/videos/$taskId").get().build()
            Result.success(parseVideoTask(setting, execute(request), model.modelId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun createImage(
        setting: MediaGenerationProviderSetting.OpenRouter,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationTask {
        val body = buildJsonObject {
            request.extraParameters.forEach { (key, value) -> put(key, value) }
            put("model", model.modelId)
            put("prompt", requireNotNull(request.prompt))
            request.inputs.mapNotNull { input ->
                imageUrl(input)?.let { url ->
                    buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", url) })
                    }
                }
            }.takeIf { it.isNotEmpty() }?.let { put("input_references", JsonArray(it)) }
            request.count?.let { put("n", it) }
            putResolution(request.resolution)
            request.aspectRatio?.let { put("aspect_ratio", it) }
            request.seed?.let { put("seed", it) }
        }
        val httpRequest = setting.request("/images")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return parseImageTask(execute(httpRequest))
    }

    private fun createVideo(
        setting: MediaGenerationProviderSetting.OpenRouter,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationTask {
        val body = buildJsonObject {
            request.extraParameters.forEach { (key, value) -> put(key, value) }
            put("model", model.modelId)
            request.prompt?.takeIf { it.isNotBlank() }?.let { put("prompt", it) }
            val frames = mutableListOf<JsonObject>()
            val references = mutableListOf<JsonObject>()
            request.inputs.forEach { input ->
                val url = imageUrl(input) ?: return@forEach
                when (input.role) {
                    ImageRole.FIRST_FRAME, ImageRole.LAST_FRAME -> {
                        frames += buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", url) })
                            put(
                                "frame_type",
                                if (input.role == ImageRole.FIRST_FRAME) "first_frame" else "last_frame",
                            )
                        }
                    }
                    ImageRole.REFERENCE -> {
                        references += buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", url) })
                        }
                    }
                }
            }
            if (frames.isNotEmpty()) put("frame_images", JsonArray(frames))
            if (references.isNotEmpty()) put("input_references", JsonArray(references))
            putResolution(request.resolution)
            request.aspectRatio?.let { put("aspect_ratio", it) }
            request.durationSeconds?.let { put("duration", it) }
            request.generateAudio?.let { put("generate_audio", it) }
            request.seed?.let { put("seed", it) }
            request.callbackUrl?.let { put("callback_url", it) }
            require(request.count == null || request.count == 1) {
                "OpenRouter generates one video per job"
            }
        }
        val httpRequest = setting.request("/videos")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return parseVideoTask(setting, execute(httpRequest), model.modelId)
    }

    private fun parseImageTask(root: JsonObject): MediaGenerationTask {
        val outputs = root["data"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val base64 = item["b64_json"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            MediaGenerationOutput(
                data = Base64.getDecoder().decode(base64),
                mimeType = item["media_type"]?.jsonPrimitive?.contentOrNull ?: "image/png",
            )
        }
        return MediaGenerationTask(
            id = UUID.randomUUID().toString(),
            status = if (outputs.isEmpty()) MediaGenerationStatus.FAILED else MediaGenerationStatus.SUCCEEDED,
            outputs = outputs,
            error = if (outputs.isEmpty()) "OpenRouter returned no image" else null,
            metadata = root,
        )
    }

    private fun parseVideoTask(
        setting: MediaGenerationProviderSetting.OpenRouter,
        root: JsonObject,
        modelId: String,
    ): MediaGenerationTask {
        val base = setting.baseUrl.trimEnd('/')
        val urls = root["unsigned_urls"]?.jsonArray.orEmpty().mapNotNull {
            it.jsonPrimitive.contentOrNull?.let { url ->
                if (url.startsWith("http://") || url.startsWith("https://")) url else "$base$url"
            }
        }
        val error = when (val value = root["error"]) {
            is JsonObject -> value["message"]?.jsonPrimitive?.contentOrNull
            else -> value?.jsonPrimitive?.contentOrNull
        }
        return MediaGenerationTask(
            id = root["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
            status = when (root["status"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                "pending" -> MediaGenerationStatus.QUEUED
                "in_progress" -> MediaGenerationStatus.RUNNING
                "completed" -> MediaGenerationStatus.SUCCEEDED
                "failed" -> MediaGenerationStatus.FAILED
                "cancelled", "canceled" -> MediaGenerationStatus.CANCELLED
                "expired" -> MediaGenerationStatus.EXPIRED
                else -> MediaGenerationStatus.UNKNOWN
            },
            outputs = urls.map { MediaGenerationOutput(url = it, mimeType = "video/mp4") },
            error = error,
            metadata = buildJsonObject { put("model", modelId) },
        )
    }

    private fun imageUrl(input: MediaGenerationInput): String? {
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

    private fun execute(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw MediaGenerationApiException(
                    provider = "OpenRouter",
                    statusCode = response.code,
                    code = null,
                    message = text.ifBlank { "OpenRouter request failed: HTTP ${response.code}" },
                )
            }
            return json.parseToJsonElement(text).jsonObject
        }
    }
}

private fun MediaGenerationProviderSetting.OpenRouter.request(path: String): Request.Builder =
    Request.Builder()
        .url("${baseUrl.trimEnd('/')}$path")
        .header("Authorization", "Bearer $apiKey")
        .header("X-Title", "Orbit")
        .header("HTTP-Referer", "https://github.com/Awsome5297/Orbit")

private fun kotlinx.serialization.json.JsonObjectBuilder.putResolution(resolution: String?) {
    if (resolution.isNullOrBlank()) return
    put(if (Regex("""\d+[xX]\d+""").matches(resolution)) "size" else "resolution", resolution)
}
