package com.ai.assistance.mediagen.provider

import com.ai.assistance.mediagen.model.ImageRole
import com.ai.assistance.mediagen.model.MediaKind

enum class MediaGenerationParameter {
    COUNT,
    RESOLUTION,
    ASPECT_RATIO,
    DURATION,
    GENERATE_AUDIO,
    WATERMARK,
    SEED,
    PROMPT_ENHANCEMENT,
}

data class MediaGenerationCapabilities(
    val parameters: Set<MediaGenerationParameter>,
    val requiredParameters: Set<MediaGenerationParameter> = emptySet(),
    val imageRoles: Set<ImageRole> = emptySet(),
    val videoInput: Boolean = false,
    val framesExcludeReferences: Boolean = false,
    val requiresPrompt: Boolean = false,
    val requiresRemoteInputs: Boolean = false,
)

fun MediaGenerationProviderSetting.capabilities(kind: MediaKind): MediaGenerationCapabilities? =
    when (this) {
        is MediaGenerationProviderSetting.OpenAI -> when (kind) {
            MediaKind.IMAGE -> MediaGenerationCapabilities(
                parameters = setOf(
                    MediaGenerationParameter.COUNT,
                    MediaGenerationParameter.RESOLUTION,
                ),
                imageRoles = setOf(ImageRole.REFERENCE),
                requiresPrompt = true,
            )
            MediaKind.VIDEO -> null
        }

        is MediaGenerationProviderSetting.Aliyun -> when (kind) {
            MediaKind.IMAGE -> MediaGenerationCapabilities(
                parameters = setOf(
                    MediaGenerationParameter.COUNT,
                    MediaGenerationParameter.RESOLUTION,
                    MediaGenerationParameter.WATERMARK,
                    MediaGenerationParameter.SEED,
                    MediaGenerationParameter.PROMPT_ENHANCEMENT,
                ),
                imageRoles = setOf(ImageRole.REFERENCE),
                requiresPrompt = true,
            )
            MediaKind.VIDEO -> MediaGenerationCapabilities(
                parameters = setOf(
                    MediaGenerationParameter.RESOLUTION,
                    MediaGenerationParameter.ASPECT_RATIO,
                    MediaGenerationParameter.DURATION,
                    MediaGenerationParameter.GENERATE_AUDIO,
                    MediaGenerationParameter.WATERMARK,
                    MediaGenerationParameter.SEED,
                    MediaGenerationParameter.PROMPT_ENHANCEMENT,
                ),
                imageRoles = ImageRole.entries.toSet(),
                videoInput = true,
                framesExcludeReferences = true,
                requiresRemoteInputs = true,
            )
        }

        is MediaGenerationProviderSetting.Volcengine -> when (kind) {
            MediaKind.IMAGE -> MediaGenerationCapabilities(
                parameters = setOf(
                    MediaGenerationParameter.RESOLUTION,
                    MediaGenerationParameter.WATERMARK,
                ),
                imageRoles = setOf(ImageRole.REFERENCE),
                requiresPrompt = true,
            )
            MediaKind.VIDEO -> MediaGenerationCapabilities(
                parameters = setOf(
                    MediaGenerationParameter.RESOLUTION,
                    MediaGenerationParameter.ASPECT_RATIO,
                    MediaGenerationParameter.DURATION,
                    MediaGenerationParameter.GENERATE_AUDIO,
                    MediaGenerationParameter.WATERMARK,
                    MediaGenerationParameter.SEED,
                ),
                imageRoles = ImageRole.entries.toSet(),
                videoInput = true,
                framesExcludeReferences = true,
                requiresRemoteInputs = true,
            )
        }

        is MediaGenerationProviderSetting.MiniMax -> when (kind) {
            MediaKind.IMAGE -> null
            MediaKind.VIDEO -> MediaGenerationCapabilities(
                parameters = setOf(
                    MediaGenerationParameter.RESOLUTION,
                    MediaGenerationParameter.ASPECT_RATIO,
                    MediaGenerationParameter.DURATION,
                    MediaGenerationParameter.WATERMARK,
                ),
                requiredParameters = setOf(
                    MediaGenerationParameter.RESOLUTION,
                    MediaGenerationParameter.ASPECT_RATIO,
                    MediaGenerationParameter.DURATION,
                ),
                imageRoles = ImageRole.entries.toSet(),
                videoInput = true,
                framesExcludeReferences = true,
                requiresPrompt = true,
            )
        }

        is MediaGenerationProviderSetting.OpenRouter -> when (kind) {
            MediaKind.IMAGE -> MediaGenerationCapabilities(
                parameters = setOf(
                    MediaGenerationParameter.COUNT,
                    MediaGenerationParameter.RESOLUTION,
                    MediaGenerationParameter.ASPECT_RATIO,
                    MediaGenerationParameter.SEED,
                ),
                imageRoles = setOf(ImageRole.REFERENCE),
                requiresPrompt = true,
            )
            MediaKind.VIDEO -> MediaGenerationCapabilities(
                parameters = setOf(
                    MediaGenerationParameter.RESOLUTION,
                    MediaGenerationParameter.ASPECT_RATIO,
                    MediaGenerationParameter.DURATION,
                    MediaGenerationParameter.GENERATE_AUDIO,
                    MediaGenerationParameter.WATERMARK,
                    MediaGenerationParameter.SEED,
                ),
                imageRoles = ImageRole.entries.toSet(),
                videoInput = true,
                framesExcludeReferences = true,
                requiresPrompt = true,
            )
        }
    }
