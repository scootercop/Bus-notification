package com.navalrishi.busnotifier.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watches")
data class Watch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val routeShortName: String,
    val stopCode: String,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val pollIntervalMin: Int = 5,
    val thresholdMin: Int = 6,
    val enabled: Boolean = true,
)
