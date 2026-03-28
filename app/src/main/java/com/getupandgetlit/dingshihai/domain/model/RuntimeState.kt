package com.getupandgetlit.dingshihai.domain.model

data class RuntimeState(
    val batchActive: Boolean,
    val batchId: String?,
    val currentPlayingTaskId: Long?,
    val lastServiceHeartbeatAt: Long,
    val updatedAt: Long,
) {
    companion object {
        fun idle(now: Long): RuntimeState {
            return RuntimeState(
                batchActive = false,
                batchId = null,
                currentPlayingTaskId = null,
                lastServiceHeartbeatAt = now,
                updatedAt = now,
            )
        }
    }
}

