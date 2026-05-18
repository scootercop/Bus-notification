package com.navalrishi.busnotifier.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotifiedTripDao {
    @Query("SELECT COUNT(*) FROM notified_trips WHERE watchId = :watchId AND tripId = :tripId")
    suspend fun count(watchId: Long, tripId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: NotifiedTrip)

    @Query("DELETE FROM notified_trips WHERE notifiedAtEpochMs < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}
