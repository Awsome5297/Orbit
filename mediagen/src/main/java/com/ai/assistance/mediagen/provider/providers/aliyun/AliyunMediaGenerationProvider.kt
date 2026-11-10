package com.ai.assistance.mediagen.provider.providers.aliyun

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

class AliyunMediaGenerationProvider(
    private val client: OkHttpClient,
) : MediaGenerationProvider<MediaGenerationProviderSetting.Aliyun> {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(
        setting: MediaGenerationProviderSetting.Aliyun,
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
        setting: MediaGenerationProviderSetting.Aliyun,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> = withContext(Dispatchers.IO) {
        try {
            val request = authorizedRequest(
                setting,
                "${setting.resolvedBaseUrl()}/tasks/$taskId",
            ).get().build()
            Result.success(parseVideoTask(execute(request)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun createImage(
        setting: MediaGenerationProviderSetting.Aliyun,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationTask {
        val body = buildJsonObject {
            put("model", model.modelId)
            put("input", buildJsonObject {
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("text", requireNotNull(request.prompt)) })
                            request.inputs.forEach { input ->
                                mediaUrl(input)?.let { url ->
                                    add(buildJsonObject { put("image", url) })
                                }
                            }
                        })
                    })
                })
            })
            put("parameters", buildJsonObject {
                request.extraParameters.forEach { (key, value) -> put(key, value) }
                request.resolution?.takeIf { it.isNotBlank() }?.let { put("size", it.toAliyunSize()) }
                request.count?.let { put("n", it) }
                request.watermark?.let { put("watermark", it) }
                request.seed?.let { put("seed", it) }
                request.promptEnhancement?.let { put("prompt_extend", it) }
            })
        }
        val httpRequest = authorizedRequest(
            setting,
            "${setting.resolvedBaseUrl()}/services/aigc/multimodal-generation/generation",
        ).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return parseImageTask(execute(httpRequest))
    }

    private fun createVideo(
        setting: MediaGenerationProviderSetting.Aliyun,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationTask {
        val body = buildJsonObject {
            put("model", model.modelId)
            put("input", buildJsonObject {
                request.prompt?.takeIf { it.isNotBlank() }?.let { put("prompt", it) }
                if (request.inputs.isNotEmpty()) {
                    put("media", buildJsonArray {
                        request.inputs.forEach { input ->
                            val url = mediaUrl(input) ?: return@forEach
                            add(buildJsonObject {
                                put(
                                    "type",
                                    when (input.role) {
                                        ImageRole.FIRST_FRAME -> "first_frame"
                                        ImageRole.LAST_FRAME -> "last_frame"
                                        ImageRole.REFERENCE -> "reference_image"
                                    },
                                )
                                put("url", url)
                            })
                        }
                    })
                }
            })
            put("parameters", buildJsonObject {
                request.extraParameters.forEach { (key, value) -> put(key, value) }
                request.resolution?.let { put("resolution", it) }
                request.aspectRatio?.let { put("ratio", it) }
                request.durationSeconds?.let { put("duration", it) }
                request.generateAudio?.let { put("audio", it) }
                request.watermark?.let { put("watermark", it) }
                request.seed?.let { put("seed", it) }
                request.promptEnhancement?.let { put("prompt_extend", it) }
            })
            require(request.count == null || request.count == 1) {
                "Aliyun generates one video per task"
            }
        }
        val httpRequest = authorizedRequest(
            setting,
            "${setting.resolvedBaseUrl()}/services/aigc/video-generation/video-synthesis",
        ).addHeader("X-DashScope-Async", "enable")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return parseVideoTask(execute(httpRequest))
    }

    private fun parseImageTask(root: JsonObject): MediaGenerationTask {
        val output = root["output"]?.jsonObject
        val urls = output?.get("choices")?.jsonArray.orEmpty()
            .flatMap { choice -> (choice.jsonObject["message"]?.jsonObject?.get("content")?.jsonArray).orEmpty() }
            .mapNotNull { it.jsonObject["image"]?.jsonPrimitive?.contentOrNull }
        return MediaGenerationTask(
            id = UUID.randomUUID().toString(),
            status = if (urls.isEmpty()) MediaGenerationStatus.FAILED else MediaGenerationStatus.SUCCEEDED,
            outputs = urls.map { MediaGenerationOutput(url = it, mimeType = "image/png") },
            error = if (urls.isEmpty()) root["message"]?.jsonPrimitive?.contentOrNull ?: "Aliyun returned no image" else null,
            metadata = root,
        )
    }

    private fun parseVideoTask(root: JsonObject): MediaGenerationTask {
        val output = root["output"]?.jsonObject ?: root
        val taskId = output["task_id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Aliyun response does not contain task_id")
        val videoUrl = output["video_url"]?.jsonPrimitive?.contentOrNull
        val error = output["message"]?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
        return MediaGenerationTask(
            id = taskId,
            status = when (output["task_status"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
                "PENDING" -> MediaGenerationStatus.QUEUED
                "RUNNING" -> MediaGenerationStatus.RUNNING
                "SUCCEEDED" -> MediaGenerationStatus.SUCCEEDED
                "FAILED" -> MediaGenerationStatus.FAILED
                "CANCELED", "CANCELLED" -> MediaGenerationStatus.CANCELLED
                "UNKNOWN" -> MediaGenerationStatus.EXPIRED
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
        setting: MediaGenerationProviderSetting.Aliyun,
        url: String,
    ): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${setting.apiKey}")

    private fun execute(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw MediaGenerationApiException(
                    provider = "Aliyun",
                    statusCode = response.code,
                    code = null,
                    message = text.ifBlank { "Aliyun request failed: HTTP ${response.code}" },
                )
            }
            return json.parseToJsonElement(text).jsonObject
        }
    }
}

private fun MediaGenerationProviderSetting.Aliyun.resolvedBaseUrl(): String {
    val placeholder = MediaGenerationProviderSetting.Aliyun.WORKSPACE_PLACEHOLDER
    val base = baseUrl.trimEnd('/')
    if (placeholder !in base) return base
    require(workspaceId.isNotBlank()) { "Aliyun workspaceId is required for $baseUrl" }
    return base.replace(placeholder, workspaceId.trim())
}

private fun String.toAliyunSize(): String =
    Regex("""(\d+)\s*[xX*]\s*(\d+)""").matchEntire(trim())
        ?.let { "${it.groupValues[1]}*${it.groupValues[2]}" }
        ?: this
