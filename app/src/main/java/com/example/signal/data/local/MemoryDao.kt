package com.example.signal.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    // =========================================================
    // SAVE
    // =========================================================

    @Insert
    suspend fun insert(
        memory: MemoryEntity
    ): Long


    // =========================================================
    // DELETE
    // =========================================================

    @Delete
    suspend fun delete(
        memory: MemoryEntity
    )


    // =========================================================
    // LOAD ALL MEMORIES
    // =========================================================

    @Query(
        """
        SELECT * FROM memories
        ORDER BY createdAt DESC
        """
    )
    fun getAllMemories(): Flow<List<MemoryEntity>>


    // Snapshot version for AI processing
    @Query(
        """
        SELECT * FROM memories
        ORDER BY createdAt DESC
        """
    )
    suspend fun getAllMemoriesSnapshot(): List<MemoryEntity>


    // =========================================================
    // GET SINGLE MEMORY
    // =========================================================

    @Query(
        """
        SELECT * FROM memories
        WHERE id = :id
        LIMIT 1
        """
    )
    suspend fun getMemoryById(
        id: Long
    ): MemoryEntity?

    @Query("SELECT * FROM memories WHERE reminderAt IS NOT NULL AND reminderAt > :now ORDER BY reminderAt")
    suspend fun getUpcomingReminders(now: Long): List<MemoryEntity>


    // =========================================================
    // LOCAL TEXT SEARCH
    // =========================================================

    @Query(
        """
        SELECT * FROM memories
        WHERE title LIKE '%' || :query || '%'
        OR content LIKE '%' || :query || '%'
        OR summary LIKE '%' || :query || '%'
        OR keywords LIKE '%' || :query || '%'
        OR sourceType LIKE '%' || :query || '%'
        OR captureLocation LIKE '%' || :query || '%'
        OR targetLocation LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """
    )
    fun searchMemories(
        query: String
    ): Flow<List<MemoryEntity>>


    // =========================================================
    // SNAPSHOT SEARCH
    //
    // Used later by AI semantic ranking.
    // =========================================================

    @Query(
        """
        SELECT * FROM memories
        WHERE title LIKE '%' || :query || '%'
        OR content LIKE '%' || :query || '%'
        OR summary LIKE '%' || :query || '%'
        OR keywords LIKE '%' || :query || '%'
        OR sourceType LIKE '%' || :query || '%'
        OR captureLocation LIKE '%' || :query || '%'
        OR targetLocation LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """
    )
    suspend fun searchMemoriesSnapshot(
        query: String
    ): List<MemoryEntity>
}
