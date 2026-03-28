package com.getupandgetlit.dingshihai.util

import com.getupandgetlit.dingshihai.R
import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import com.getupandgetlit.dingshihai.domain.model.PlayMode
import java.util.Locale

fun TaskEntity.displayTime(): String {
    return String.format(Locale.US, "%02d:%02d", startHour, startMinute)
}

fun TaskEntity.displayName(): String {
    return if (name.isNullOrBlank()) "未命名任务" else name
}

fun TaskEntity.playPlanRes(): Int {
    return if (PlayMode.fromValue(playMode) == PlayMode.INTERVAL) {
        R.string.play_plan_interval
    } else {
        R.string.play_plan_single
    }
}

