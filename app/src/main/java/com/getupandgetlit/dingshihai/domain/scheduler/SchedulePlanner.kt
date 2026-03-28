package com.getupandgetlit.dingshihai.domain.scheduler

import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import java.util.Calendar

object SchedulePlanner {
    fun buildSchedule(tasks: List<TaskEntity>, nowMs: Long): Map<Long, Long> {
        return tasks.associate { task ->
            task.id to computeTriggerTime(nowMs, task.startHour, task.startMinute)
        }
    }

    fun computeTriggerTime(nowMs: Long, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        if (calendar.timeInMillis <= nowMs) {
            calendar.add(Calendar.DATE, 1)
        }
        return calendar.timeInMillis
    }
}

