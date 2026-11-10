package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.assistance.operit.data.model.MediaCreationNodeEntity
import com.ai.assistance.operit.data.model.MediaCreationOutputEntity
import com.ai.assistance.operit.data.model.MediaCreationRecordEntity
import com.ai.assistance.operit.data.model.MediaCreationSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaCreationDao {
    @Query("SELECT * FROM media_creation_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<MediaCreationSessionEntity>>

    @Query("SELECT * FROM media_creation_sessions WHERE id = :sessionId")
    fun observeSession(sessionId: String): Flow<MediaCreationSessionEntity?>

    @Query("SELECT * FROM media_creation_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): MediaCreationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: MediaCreationSessionEntity)

    @Query("UPDATE media_creation_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun renameSession(sessionId: String, title: String, updatedAt: Long)

    @Query("DELETE FROM media_creation_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM media_creation_nodes WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    fun observeNodes(sessionId: String): Flow<List<MediaCreationNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: MediaCreationNodeEntity)

    @Query("DELETE FROM media_creation_nodes WHERE id IN (:nodeIds)")
    suspend fun deleteNodes(nodeIds: List<String>)

    @Query("SELECT * FROM media_creation_records WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeRecords(sessionId: String): Flow<List<MediaCreationRecordEntity>>

    @Query("SELECT * FROM media_creation_records WHERE nodeId = :nodeId ORDER BY createdAt ASC")
    fun observeNodeRecords(nodeId: String): Flow<List<MediaCreationRecordEntity>>

    @Query("SELECT COUNT(*) FROM media_creation_records WHERE nodeId = :nodeId")
    suspend fun countRecordsForNode(nodeId: String): Int

    @Query("SELECT * FROM media_creation_records WHERE status IN ('PREPARING', 'QUEUED', 'RUNNING', 'DOWNLOADING')")
    suspend fun getActiveRecords(): List<MediaCreationRecordEntity>

    @Query("SELECT * FROM media_creation_records WHERE id = :recordId")
    suspend fun getRecord(recordId: String): MediaCreationRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MediaCreationRecordEntity)

    @Query(
        "UPDATE media_creation_records SET status = :status, taskId = :taskId, error = :error, updatedAt = :updatedAt WHERE id = :recordId"
    )
    suspend fun updateRecordStatus(
        recordId: String,
        status: String,
        taskId: String?,
        error: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM media_creation_records WHERE id = :recordId")
    suspend fun deleteRecord(recordId: String)

    @Query(
        """
        UPDATE media_creation_sessions SET
            nodeCount = (SELECT COUNT(*) FROM media_creation_nodes WHERE sessionId = :sessionId),
            activeCount = (
                SELECT COUNT(*) FROM media_creation_records
                WHERE sessionId = :sessionId
                  AND status IN ('PREPARING', 'QUEUED', 'RUNNING', 'DOWNLOADING')
            ),
            coverPath = (
                SELECT o.path FROM media_creation_outputs o
                INNER JOIN media_creation_records r ON r.id = o.recordId
                WHERE r.sessionId = :sessionId
                ORDER BY r.createdAt DESC, o.id ASC
                LIMIT 1
            ),
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun updateSessionCounts(sessionId: String, updatedAt: Long)

    @Query("SELECT * FROM media_creation_outputs WHERE recordId = :recordId ORDER BY id ASC")
    fun observeOutputs(recordId: String): Flow<List<MediaCreationOutputEntity>>

    @Query(
        """
        SELECT o.* FROM media_creation_outputs o
        INNER JOIN media_creation_records r ON r.id = o.recordId
        WHERE r.sessionId = :sessionId
        ORDER BY r.createdAt DESC, o.id ASC
        LIMIT 20
        """
    )
    fun observeRecentOutputs(sessionId: String): Flow<List<MediaCreationOutputEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutput(output: MediaCreationOutputEntity)

    @Query("DELETE FROM media_creation_outputs WHERE recordId = :recordId")
    suspend fun deleteOutputs(recordId: String)
}
