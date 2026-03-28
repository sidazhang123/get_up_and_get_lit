package com.getupandgetlit.dingshihai.data.repo

import androidx.room.withTransaction
import com.getupandgetlit.dingshihai.data.db.AppDatabase
import com.getupandgetlit.dingshihai.data.entity.RuntimeStateEntity
import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import com.getupandgetlit.dingshihai.domain.model.PlayMode
import com.getupandgetlit.dingshihai.domain.model.RuntimeState
import com.getupandgetlit.dingshihai.domain.model.TaskDraft
import com.getupandgetlit.dingshihai.domain.model.TaskDraftValidator
import com.getupandgetlit.dingshihai.domain.model.TaskStatus
import com.getupandgetlit.dingshihai.domain.model.TaskValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val database: AppDatabase,
) {
    private val taskDao = database.taskDao()
    private val runtimeStateDao = database.runtimeStateDao()

    fun observeTasks(): Flow<List<TaskEntity>> = taskDao.observeTasks()

    fun observeRuntimeState(): Flow<RuntimeState> {
        return runtimeStateDao.observeRuntimeState().map { entity ->
            entity?.toDomain() ?: RuntimeState.idle(System.currentTimeMillis())
        }
    }

    suspend fun getAllTasks(): List<TaskEntity> = taskDao.getAllTasks()

    suspend fun getTaskById(taskId: Long): TaskEntity? = taskDao.getTaskById(taskId)

    suspend fun getRuntimeState(): RuntimeState {
        return runtimeStateDao.getRuntimeState()?.toDomain()
            ?: RuntimeState.idle(System.currentTimeMillis())
    }

    suspend fun validateTask(draft: TaskDraft): TaskValidationResult {
        val hour = draft.startHour ?: return TaskValidationResult.MissingRequired
        val minute = draft.startMinute ?: return TaskValidationResult.MissingRequired
        return TaskDraftValidator.validate(
            draft = draft,
            duplicateExists = taskDao.countByTime(hour, minute, draft.id) > 0,
        )
    }

    suspend fun createTask(draft: TaskDraft): TaskEntity {
        val now = System.currentTimeMillis()
        val entity = TaskEntity(
            name = draft.name.trim().ifBlank { null },
            startHour = requireNotNull(draft.startHour),
            startMinute = requireNotNull(draft.startMinute),
            fileUri = draft.fileUri,
            fileName = draft.fileName,
            playMode = draft.playMode.value,
            loopCount = draft.loopCount,
            intervalMinSec = draft.intervalMinSec,
            intervalMaxSec = draft.intervalMaxSec,
            status = TaskStatus.UNTRIGGERED.value,
            scheduledAtEpochMs = null,
            createdAt = now,
            updatedAt = now,
        )
        val id = taskDao.insertTask(entity)
        return requireNotNull(taskDao.getTaskById(id))
    }

    suspend fun updateTask(draft: TaskDraft) {
        val current = requireNotNull(taskDao.getTaskById(requireNotNull(draft.id)))
        val now = System.currentTimeMillis()
        taskDao.updateTask(
            current.copy(
                name = draft.name.trim().ifBlank { null },
                startHour = requireNotNull(draft.startHour),
                startMinute = requireNotNull(draft.startMinute),
                fileUri = draft.fileUri,
                fileName = draft.fileName,
                playMode = draft.playMode.value,
                loopCount = draft.loopCount,
                intervalMinSec = draft.intervalMinSec,
                intervalMaxSec = draft.intervalMaxSec,
                updatedAt = now,
            )
        )
    }

    suspend fun deleteTask(taskId: Long) {
        taskDao.getTaskById(taskId)?.let { task ->
            taskDao.deleteTask(task)
        }
    }

    suspend fun setRuntimeState(state: RuntimeState) {
        runtimeStateDao.upsert(state.toEntity())
    }

    suspend fun clearRuntimeState() {
        runtimeStateDao.clear()
    }

    suspend fun updateCurrentPlayingTask(taskId: Long?) {
        val current = getRuntimeState()
        setRuntimeState(
            current.copy(
                currentPlayingTaskId = taskId,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun heartbeatService(now: Long = System.currentTimeMillis()) {
        val current = getRuntimeState()
        setRuntimeState(
            current.copy(
                lastServiceHeartbeatAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun startBatch(batchId: String, scheduleMap: Map<Long, Long>) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            taskDao.updateAllStatuses(TaskStatus.UNTRIGGERED.value, now)
            scheduleMap.forEach { (taskId, scheduledAtEpochMs) ->
                taskDao.updateScheduledTime(taskId, scheduledAtEpochMs, now)
            }
            runtimeStateDao.upsert(
                RuntimeStateEntity(
                    batchActive = true,
                    batchId = batchId,
                    currentPlayingTaskId = null,
                    lastServiceHeartbeatAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    suspend fun stopBatch(
        clearStatusesOnly: Boolean = false,
        preserveCurrentPlayingTask: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val currentRuntime = getRuntimeState()
        database.withTransaction {
            taskDao.clearAllSchedules(now)
            if (clearStatusesOnly) {
                taskDao.updateAllStatuses(TaskStatus.UNTRIGGERED.value, now)
            }
            runtimeStateDao.upsert(
                RuntimeStateEntity(
                    batchActive = false,
                    batchId = null,
                    currentPlayingTaskId = if (preserveCurrentPlayingTask) {
                        currentRuntime.currentPlayingTaskId
                    } else {
                        null
                    },
                    lastServiceHeartbeatAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    suspend fun resetAfterBoot() {
        val now = System.currentTimeMillis()
        database.withTransaction {
            taskDao.updateAllStatuses(TaskStatus.UNTRIGGERED.value, now)
            taskDao.clearAllSchedules(now)
            runtimeStateDao.clear()
            runtimeStateDao.upsert(RuntimeState.idle(now).toEntity())
        }
    }

    suspend fun markTaskStatus(taskId: Long, status: TaskStatus) {
        taskDao.updateTaskStatus(taskId, status.value, System.currentTimeMillis())
    }

    suspend fun markTasksTriggered(taskIds: List<Long>) {
        val now = System.currentTimeMillis()
        taskIds.forEach { taskDao.updateTaskStatus(it, TaskStatus.TRIGGERED.value, now) }
    }

    suspend fun getNextPendingTask(): TaskEntity? {
        return taskDao.getNextPendingTask(TaskStatus.UNTRIGGERED.value)
    }

    suspend fun getPendingTasksByScheduledAt(scheduledAtEpochMs: Long): List<TaskEntity> {
        return taskDao.getPendingTasksByScheduledAt(TaskStatus.UNTRIGGERED.value, scheduledAtEpochMs)
    }

    suspend fun hasPendingTasks(): Boolean {
        return taskDao.countByStatus(TaskStatus.UNTRIGGERED.value) > 0
    }

    private fun RuntimeStateEntity.toDomain(): RuntimeState {
        return RuntimeState(
            batchActive = batchActive,
            batchId = batchId,
            currentPlayingTaskId = currentPlayingTaskId,
            lastServiceHeartbeatAt = lastServiceHeartbeatAt,
            updatedAt = updatedAt,
        )
    }

    private fun RuntimeState.toEntity(): RuntimeStateEntity {
        return RuntimeStateEntity(
            batchActive = batchActive,
            batchId = batchId,
            currentPlayingTaskId = currentPlayingTaskId,
            lastServiceHeartbeatAt = lastServiceHeartbeatAt,
            updatedAt = updatedAt,
        )
    }
}
