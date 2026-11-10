package com.ai.assistance.mediagen.provider

import com.ai.assistance.mediagen.model.MediaGenerationModel
import com.ai.assistance.mediagen.model.MediaKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

@Serializable
sealed class MediaGenerationProviderSetting {
    abstract val id: String
    abstract val name: String
    abstract val apiKey: String
    abstract val baseUrl: String
    abstract val models: List<MediaGenerationModel>

    abstract val supportedKinds: Set<MediaKind>

    open fun downloadHeaders(url: String): Map<String, String> = emptyMap()

    protected fun sameOriginBearer(url: String): Map<String, String> {
        val target = url.toHttpUrlOrNull() ?: return emptyMap()
        val base = baseUrl.toHttpUrlOrNull() ?: return emptyMap()
        if (target.scheme != base.scheme || target.host != base.host || target.port != base.port) {
            return emptyMap()
        }
        return mapOf("Authorization" to "Bearer $apiKey")
    }

    abstract fun copyProvider(
        id: String = this.id,
        name: String = this.name,
        apiKey: String = this.apiKey,
        baseUrl: String = this.baseUrl,
        models: List<MediaGenerationModel> = this.models,
    ): MediaGenerationProviderSetting

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "OpenAI",
        override val apiKey: String = "",
        override val baseUrl: String = "https://api.openai.com/v1",
        override val models: List<MediaGenerationModel> = listOf(
            MediaGenerationModel(modelId = "gpt-image-1", kind = MediaKind.IMAGE),
        ),
    ) : MediaGenerationProviderSetting() {
        override val supportedKinds: Set<MediaKind>
            get() = setOf(MediaKind.IMAGE)

        override fun copyProvider(
            id: String,
            name: String,
            apiKey: String,
            baseUrl: String,
            models: List<MediaGenerationModel>,
        ) = copy(id = id, name = name, apiKey = apiKey, baseUrl = baseUrl, models = models)
    }

    @Serializable
    @SerialName("aliyun")
    data class Aliyun(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Aliyun",
        override val apiKey: String = "",
        val workspaceId: String = "",
        override val baseUrl: String =
            "https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1",
        override val models: List<MediaGenerationModel> = listOf(
            MediaGenerationModel(modelId = "qwen-image", kind = MediaKind.IMAGE),
            MediaGenerationModel(modelId = "wan2.5-t2v", kind = MediaKind.VIDEO),
        ),
    ) : MediaGenerationProviderSetting() {
        override val supportedKinds: Set<MediaKind>
            get() = setOf(MediaKind.IMAGE, MediaKind.VIDEO)

        override fun copyProvider(
            id: String,
            name: String,
            apiKey: String,
            baseUrl: String,
            models: List<MediaGenerationModel>,
        ) = copy(id = id, name = name, apiKey = apiKey, baseUrl = baseUrl, models = models)

        companion object {
            const val WORKSPACE_PLACEHOLDER = "{WorkspaceId}"
        }
    }

    @Serializable
    @SerialName("volcengine")
    data class Volcengine(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Volcengine",
        override val apiKey: String = "",
        override val baseUrl: String = "https://ark.cn-beijing.volces.com/api/v3",
        override val models: List<MediaGenerationModel> = listOf(
            MediaGenerationModel(modelId = "doubao-seedream-4-0", kind = MediaKind.IMAGE),
            MediaGenerationModel(modelId = "doubao-seedance-1-0-pro", kind = MediaKind.VIDEO),
        ),
    ) : MediaGenerationProviderSetting() {
        override val supportedKinds: Set<MediaKind>
            get() = setOf(MediaKind.IMAGE, MediaKind.VIDEO)

        override fun copyProvider(
            id: String,
            name: String,
            apiKey: String,
            baseUrl: String,
            models: List<MediaGenerationModel>,
        ) = copy(id = id, name = name, apiKey = apiKey, baseUrl = baseUrl, models = models)
    }

    @Serializable
    @SerialName("minimax")
    data class MiniMax(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "MiniMax",
        override val apiKey: String = "",
        override val baseUrl: String = "https://api.minimaxi.com/v2",
        override val models: List<MediaGenerationModel> = listOf(
            MediaGenerationModel(modelId = "MiniMax-Hailuo-02", kind = MediaKind.VIDEO),
        ),
    ) : MediaGenerationProviderSetting() {
        override val supportedKinds: Set<MediaKind>
            get() = setOf(MediaKind.VIDEO)

        override fun copyProvider(
            id: String,
            name: String,
            apiKey: String,
            baseUrl: String,
            models: List<MediaGenerationModel>,
        ) = copy(id = id, name = name, apiKey = apiKey, baseUrl = baseUrl, models = models)
    }

    @Serializable
    @SerialName("openrouter")
    data class OpenRouter(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "OpenRouter",
        override val apiKey: String = "",
        override val baseUrl: String = "https://openrouter.ai/api/v1",
        override val models: List<MediaGenerationModel> = listOf(
            MediaGenerationModel(modelId = "openai/gpt-image-1", kind = MediaKind.IMAGE),
            MediaGenerationModel(modelId = "google/veo-3", kind = MediaKind.VIDEO),
        ),
    ) : MediaGenerationProviderSetting() {
        override val supportedKinds: Set<MediaKind>
            get() = setOf(MediaKind.IMAGE, MediaKind.VIDEO)

        override fun downloadHeaders(url: String): Map<String, String> = sameOriginBearer(url)

        override fun copyProvider(
            id: String,
            name: String,
            apiKey: String,
            baseUrl: String,
            models: List<MediaGenerationModel>,
        ) = copy(id = id, name = name, apiKey = apiKey, baseUrl = baseUrl, models = models)
    }

    companion object {
        val Types: List<String> = listOf("openai", "aliyun", "volcengine", "minimax", "openrouter")
    }
}
