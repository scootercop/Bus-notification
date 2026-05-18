package com.navalrishi.busnotifier.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Query("SELECT * FROM watches ORDER BY id")
    fun observeAll(): Flow<List<Watch>>

    @Query("SELECT * FROM watches WHERE enabled = 1")
    suspend fun enabled(): List<Watch>

    @Query("SELECT * FROM watches WHERE id = :id")
    suspend fun byId(id: Long): Watch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(watch: Watch): Long

    @Update
    suspend fun update(watch: Watch)

    @Delete
    suspend fun delete(watch: Watch)
}
