package com.ai.assistance.operit.data.media

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.MediaCreationSessionEntity
import kotlinx.coroutines.flow.Flow
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
}
