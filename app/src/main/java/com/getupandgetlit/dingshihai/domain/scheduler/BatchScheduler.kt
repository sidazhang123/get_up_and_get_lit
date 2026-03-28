package com.getupandgetlit.dingshihai.domain.scheduler

import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import com.getupandgetlit.dingshihai.data.repo.TaskRepository
import com.getupandgetlit.dingshihai.domain.logger.AppLogger

class BatchScheduler(
    private val repository: TaskRepository,
    private val logger: AppLogger,
) {
    fun buildSchedule(tasks: List<TaskEntity>, nowMs: Long): Map<Long, Long> {
        return SchedulePlanner.buildSchedule(tasks, nowMs)
    }

    fun computeTriggerTime(nowMs: Long, hour: Int, minute: Int): Long {
        return SchedulePlanner.computeTriggerTime(nowMs, hour, minute)
    }

    suspend fun resolveConflictGroup(task: TaskEntity): Pair<TaskEntity, List<TaskEntity>> {
        val scheduledAt = task.scheduledAtEpochMs ?: return task to emptyList()
        val sameTimeTasks = repository.getPendingTasksByScheduledAt(scheduledAt)
        val primary = sameTimeTasks.firstOrNull { it.id == task.id } ?: sameTimeTasks.firstOrNull() ?: task
        val skipped = sameTimeTasks.filterNot { it.id == primary.id }
        if (skipped.isNotEmpty()) {
            repository.markTasksTriggered(skipped.map { it.id })
            skipped.forEach {
                logger.log(
                    event = "task_conflict_skipped",
                    result = "skipped",
                    task = it,
                    message = "same scheduledAtEpochMs as taskId=${primary.id}",
                )
            }
        }
        return primary to skipped
    }
}
