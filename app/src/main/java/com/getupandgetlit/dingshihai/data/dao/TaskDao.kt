package com.getupandgetlit.dingshihai.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY startHour ASC, startMinute ASC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY startHour ASC, startMinute ASC")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): TaskEntity?

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE startHour = :hour AND startMinute = :minute
        AND (:excludeTaskId IS NULL OR id != :excludeTaskId)
        """
    )
    suspend fun countByTime(hour: Int, minute: Int, excludeTaskId: Long?): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt")
    suspend fun updateAllStatuses(status: String, updatedAt: Long)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Long, status: String, updatedAt: Long)

    @Query("UPDATE tasks SET selectedForReserve = :selected, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateSelectedForReserve(taskId: Long, selected: Boolean, updatedAt: Long)

    @Query("UPDATE tasks SET scheduledAtEpochMs = :scheduledAtEpochMs, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateScheduledTime(taskId: Long, scheduledAtEpochMs: Long?, updatedAt: Long)

    @Query("UPDATE tasks SET scheduledAtEpochMs = NULL, updatedAt = :updatedAt")
    suspend fun clearAllSchedules(updatedAt: Long)

    @Query(
        """
        SELECT * FROM tasks
        WHERE status = :status AND scheduledAtEpochMs IS NOT NULL
        ORDER BY scheduledAtEpochMs ASC, updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getNextPendingTask(status: String): TaskEntity?

    @Query(
        """
        SELECT * FROM tasks
        WHERE status = :status AND scheduledAtEpochMs = :scheduledAtEpochMs
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getPendingTasksByScheduledAt(status: String, scheduledAtEpochMs: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = :status")
    suspend fun countByStatus(status: String): Int
}

