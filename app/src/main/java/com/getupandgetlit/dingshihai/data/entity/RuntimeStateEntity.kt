package com.getupandgetlit.dingshihai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runtime_state")
data class RuntimeStateEntity(
    @PrimaryKey val id: Int = 1,
    val batchActive: Boolean,
    val batchId: String?,
    val currentPlayingTaskId: Long?,
    val lastServiceHeartbeatAt: Long,
    val updatedAt: Long,
)

