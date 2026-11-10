package com.ai.assistance.mediagen.provider.providers.openai

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64
import java.util.UUID

class OpenAIMediaGenerationProvider(
    private val client: OkHttpClient,
) : MediaGenerationProvider<MediaGenerationProviderSetting.OpenAI> {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(
        setting: MediaGenerationProviderSetting.OpenAI,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = setting.baseUrl.trimEnd('/')
            val imageInputs = request.inputs.filter {
                it.kind == MediaInputKind.IMAGE && !it.filePath.isNullOrBlank()
            }
            val endpoint = if (imageInputs.isEmpty()) "/images/generations" else "/images/edits"
            val body = if (imageInputs.isEmpty()) {
                buildGenerationBody(model, request)
            } else {
                buildEditBody(model, request, imageInputs)
            }
            val httpRequest = Request.Builder()
                .url(baseUrl + endpoint)
                .header("Authorization", "Bearer ${setting.apiKey}")
                .post(body)
                .build()
            client.newCall(httpRequest).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw MediaGenerationApiException(
                        provider = "OpenAI",
                        statusCode = response.code,
                        code = null,
                        message = text.ifBlank { "OpenAI image request failed: HTTP ${response.code}" },
                    )
                }
                Result.success(parseTask(text))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun query(
        setting: MediaGenerationProviderSetting.OpenAI,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> = Result.failure(
        UnsupportedOperationException("OpenAI images are synchronous and cannot be queried")
    )

    private fun buildGenerationBody(
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): RequestBody {
        val body = buildJsonObject {
            put("model", model.modelId)
            put("prompt", requireNotNull(request.prompt))
            request.count?.let { put("n", it) }
            request.resolution?.let { put("size", it) }
            request.extraParameters.forEach { (key, value) -> put(key, value) }
        }
        return body.toString().toRequestBody("application/json".toMediaType())
    }

    private fun buildEditBody(
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
        inputs: List<MediaGenerationInput>,
    ): RequestBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("model", model.modelId)
        builder.addFormDataPart("prompt", requireNotNull(request.prompt))
        request.count?.let { builder.addFormDataPart("n", it.toString()) }
        request.resolution?.let { builder.addFormDataPart("size", it) }
        val fieldName = if (inputs.size == 1) "image" else "image[]"
        inputs.forEach { input ->
            val path = requireNotNull(input.filePath)
            val file = File(path)
            val mimeType = input.mimeType ?: "image/png"
            builder.addFormDataPart(
                fieldName,
                file.name,
                file.asRequestBody(mimeType.toMediaType()),
            )
        }
        return builder.build()
    }

    private fun parseTask(text: String): MediaGenerationTask {
        val root = json.parseToJsonElement(text).jsonObject
        val data = root["data"]?.jsonArray
        if (data.isNullOrEmpty()) {
            val error = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            return MediaGenerationTask(
                id = UUID.randomUUID().toString(),
                status = MediaGenerationStatus.FAILED,
                error = error ?: "OpenAI image response has no output",
            )
        }
        val outputs = data.mapNotNull { element ->
            val item = element.jsonObject
            val base64 = item["b64_json"]?.jsonPrimitive?.contentOrNull
            val url = item["url"]?.jsonPrimitive?.contentOrNull
            when {
                base64 != null -> MediaGenerationOutput(
                    data = Base64.getDecoder().decode(base64),
                    mimeType = "image/png",
                )
                url != null -> MediaGenerationOutput(url = url, mimeType = "image/png")
                else -> null
            }
        }
        return MediaGenerationTask(
            id = UUID.randomUUID().toString(),
            status = if (outputs.isEmpty()) MediaGenerationStatus.FAILED else MediaGenerationStatus.SUCCEEDED,
            outputs = outputs,
            error = if (outputs.isEmpty()) "OpenAI image response has no output" else null,
        )
    }
}
