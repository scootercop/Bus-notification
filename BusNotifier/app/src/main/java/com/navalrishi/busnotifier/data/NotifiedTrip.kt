package com.navalrishi.busnotifier.data

import androidx.room.Entity

@Entity(tableName = "notified_trips", primaryKeys = ["watchId", "tripId"])
data class NotifiedTrip(
    val watchId: Long,
    val tripId: String,
    val notifiedAtEpochMs: Long,
)
