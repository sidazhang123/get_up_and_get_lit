package com.getupandgetlit.dingshihai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val startHour: Int,
    val startMinute: Int,
    val fileUri: String,
    val fileName: String,
    val playMode: String,
    val loopCount: Int?,
    val intervalMinSec: Int?,
    val intervalMaxSec: Int?,
    val maxPlaybackMinutes: Int,
    val forceBluetoothPlayback: Boolean,
    val status: String,
    val scheduledAtEpochMs: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
