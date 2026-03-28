package com.getupandgetlit.dingshihai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.getupandgetlit.dingshihai.service.SchedulerForegroundService
import com.getupandgetlit.dingshihai.util.ServiceLauncher

class GuardAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GUARD_ALARM) return
        ServiceLauncher.startServiceCompat(
            context,
            SchedulerForegroundService.guardAlarmIntent(
                context = context,
                batchId = intent.getStringExtra(EXTRA_BATCH_ID),
                taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L),
            ),
        )
    }

    companion object {
        const val ACTION_GUARD_ALARM = "com.getupandgetlit.dingshihai.GUARD_ALARM"
        const val EXTRA_BATCH_ID = "extra_batch_id"
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}

