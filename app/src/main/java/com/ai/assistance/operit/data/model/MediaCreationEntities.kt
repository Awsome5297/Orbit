package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "media_creation_sessions")
data class MediaCreationSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val draftJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val nodeCount: Int = 0,
    val activeCount: Int = 0,
    val coverPath: String? = null,
)

@Entity(
    tableName = "media_creation_nodes",
    foreignKeys = [
        ForeignKey(
            entity = MediaCreationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class MediaCreationNodeEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val createdAt: Long,
    val orderIndex: Long,
)

@Entity(
    tableName = "media_creation_records",
    foreignKeys = [
        ForeignKey(
            entity = MediaCreationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaCreationNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("nodeId"), Index("status")],
)
data class MediaCreationRecordEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val nodeId: String,
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val kind: String,
    val prompt: String,
    val paramsJson: String,
    val inputsJson: String,
    val status: String,
    val taskId: String? = null,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "media_creation_outputs",
    foreignKeys = [
        ForeignKey(
            entity = MediaCreationRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordId")],
)
data class MediaCreationOutputEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val path: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Double? = null,
    val posterPath: String? = null,
    val lastFramePath: String? = null,
)
