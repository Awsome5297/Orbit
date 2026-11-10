package com.ai.assistance.mediagen.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MediaGenerationModel(
    val id: String = UUID.randomUUID().toString(),
    val modelId: String,
    val kind: MediaKind,
    val displayName: String = "",
)
