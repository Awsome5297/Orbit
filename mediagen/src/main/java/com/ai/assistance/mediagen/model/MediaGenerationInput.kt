package com.ai.assistance.mediagen.model

import kotlinx.serialization.Serializable

@Serializable
enum class ImageRole {
    FIRST_FRAME,
    LAST_FRAME,
    REFERENCE,
}

@Serializable
enum class MediaInputKind {
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    WEB,
}

@Serializable
data class MediaGenerationInput(
    val kind: MediaInputKind = MediaInputKind.IMAGE,
    val role: ImageRole = ImageRole.REFERENCE,
    val url: String? = null,
    val filePath: String? = null,
    val mimeType: String? = null,
)
