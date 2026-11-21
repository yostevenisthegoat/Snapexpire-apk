package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {
    @Query("SELECT * FROM managed_screenshots ORDER BY capturedAtMillis DESC")
    fun observeAll(): Flow<List<ManagedScreenshotEntity>>

    @Query("SELECT * FROM managed_screenshots WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ManagedScreenshotEntity?>

    @Query("SELECT * FROM managed_screenshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ManagedScreenshotEntity?

    @Query("SELECT * FROM managed_screenshots WHERE contentUri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): ManagedScreenshotEntity?

    @Query("SELECT * FROM managed_screenshots WHERE status IN ('SCHEDULED', 'PENDING_CONFIRMATION')")
    suspend fun getScheduledAndPending(): List<ManagedScreenshotEntity>

    @Query("SELECT * FROM managed_screenshots WHERE status = 'PENDING_CONFIRMATION' ORDER BY expiresAtMillis ASC")
    suspend fun getPendingConfirmation(): List<ManagedScreenshotEntity>

    @Query("SELECT * FROM managed_screenshots WHERE status != 'EXPIRED'")
    suspend fun getAllNonExpired(): List<ManagedScreenshotEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: ManagedScreenshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ManagedScreenshotEntity)

    @Query(
        "UPDATE managed_screenshots SET expiresAtMillis = :expiresAtMillis, presetLabel = :presetLabel, " +
            "status = :status, warningNotified = :warningNotified, updatedAtMillis = :updatedAt WHERE id = :id"
    )
    suspend fun updateExpiration(
        id: Long,
        expiresAtMillis: Long?,
        presetLabel: String?,
        status: ScreenshotStatus,
        warningNotified: Boolean,
        updatedAt: Long
    )

    @Query("UPDATE managed_screenshots SET status = :status, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ScreenshotStatus, updatedAt: Long)

    @Query("UPDATE managed_screenshots SET warningNotified = 1, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun markWarningNotified(id: Long, updatedAt: Long)

    @Query(
        "UPDATE managed_screenshots SET status = 'EXPIRED', expiredAtMillis = :expiredAt, updatedAtMillis = :expiredAt WHERE id = :id"
    )
    suspend fun markExpiredVerified(id: Long, expiredAt: Long)

    @Query("DELETE FROM managed_screenshots WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM managed_screenshots WHERE status = 'EXPIRED'")
    suspend fun clearExpiredHistory()
}
