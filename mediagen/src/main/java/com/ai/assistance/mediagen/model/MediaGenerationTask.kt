package com.ai.assistance.mediagen.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class MediaGenerationStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED,
    UNKNOWN;

    val isTerminal: Boolean
        get() = this in setOf(SUCCEEDED, FAILED, CANCELLED, EXPIRED)
}

@Serializable
data class MediaGenerationOutput(
    val url: String? = null,
    val data: ByteArray? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Double? = null,
)

@Serializable
data class MediaGenerationTask(
    val id: String,
    val status: MediaGenerationStatus,
    val outputs: List<MediaGenerationOutput> = emptyList(),
    val error: String? = null,
    val metadata: JsonObject = JsonObject(emptyMap()),
) {
    val isTerminal: Boolean
        get() = status.isTerminal
}
