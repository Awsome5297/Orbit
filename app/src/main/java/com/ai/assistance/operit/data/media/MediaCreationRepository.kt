package com.ai.assistance.operit.data.media

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.MediaCreationNodeEntity
import com.ai.assistance.operit.data.model.MediaCreationOutputEntity
import com.ai.assistance.operit.data.model.MediaCreationRecordEntity
import com.ai.assistance.operit.data.model.MediaCreationSessionEntity
import com.ai.assistance.mediagen.model.MediaKind
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class MediaCreationRepository private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: MediaCreationRepository? = null

        fun getInstance(context: Context): MediaCreationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaCreationRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val dao = AppDatabase.getDatabase(context).mediaCreationDao()
    private val files = MediaCreationFiles(context)

    fun observeSessions(): Flow<List<MediaCreationSessionEntity>> = dao.observeSessions()

    fun observeSession(sessionId: String): Flow<MediaCreationSessionEntity?> =
        dao.observeSession(sessionId)

    suspend fun getSession(sessionId: String): MediaCreationSessionEntity? = dao.getSession(sessionId)

    suspend fun createSession(title: String = ""): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertSession(
            MediaCreationSessionEntity(
                id = id,
                title = title,
                draftJson = "{}",
                createdAt = now,
                updatedAt = now,
            )
        )
        files.sessionDir(id)
        return id
    }

    suspend fun renameSession(sessionId: String, title: String) {
        dao.renameSession(sessionId, title, System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
        files.deleteSession(sessionId)
    }

    fun observeRecords(sessionId: String): Flow<List<MediaCreationRecordEntity>> =
        dao.observeRecords(sessionId)

    fun observeOutputs(recordId: String): Flow<List<MediaCreationOutputEntity>> =
        dao.observeOutputs(recordId)

    suspend fun createRecord(
        sessionId: String,
        providerId: String,
        providerName: String,
        modelId: String,
        kind: MediaKind,
        prompt: String,
        nodeId: String = UUID.randomUUID().toString(),
    ): String {
        val now = System.currentTimeMillis()
        val recordId = UUID.randomUUID().toString()
        dao.insertNode(
            MediaCreationNodeEntity(
                id = nodeId,
                sessionId = sessionId,
                createdAt = now,
                orderIndex = now,
            )
        )
        dao.insertRecord(
            MediaCreationRecordEntity(
                id = recordId,
                sessionId = sessionId,
                nodeId = nodeId,
                providerId = providerId,
                providerName = providerName,
                modelId = modelId,
                kind = kind.name,
                prompt = prompt,
                paramsJson = "{}",
                inputsJson = "[]",
                status = "PREPARING",
                createdAt = now,
                updatedAt = now,
            )
        )
        val session = dao.getSession(sessionId)
        if (session != null && session.title.isBlank()) {
            dao.renameSession(sessionId, prompt.take(40), now)
        }
        dao.updateSessionCounts(sessionId, now)
        files.recordDir(sessionId, recordId)
        return recordId
    }

    suspend fun finishRecord(
        recordId: String,
        status: String,
        taskId: String? = null,
        error: String? = null,
    ) {
        dao.updateRecordStatus(recordId, status, taskId, error, System.currentTimeMillis())
    }

    suspend fun saveOutput(
        sessionId: String,
        recordId: String,
        output: MediaCreationOutputEntity,
    ) {
        dao.insertOutput(output)
        files.recordDir(sessionId, recordId)
    }

    fun recordDir(sessionId: String, recordId: String): File =
        files.recordDir(sessionId, recordId)

    suspend fun getRecord(recordId: String): MediaCreationRecordEntity? = dao.getRecord(recordId)

    suspend fun deleteRecord(sessionId: String, recordId: String) {
        dao.deleteRecord(recordId)
        files.deleteRecord(sessionId, recordId)
        dao.updateSessionCounts(sessionId, System.currentTimeMillis())
    }
}
