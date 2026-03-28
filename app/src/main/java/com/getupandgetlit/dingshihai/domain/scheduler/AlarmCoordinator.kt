package com.getupandgetlit.dingshihai.domain.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.getupandgetlit.dingshihai.receiver.GuardAlarmReceiver

class AlarmCoordinator(
    context: Context,
) {
    companion object {
        private const val REQUEST_CODE_GUARD_ALARM = 1001
    }

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleGuardAlarm(batchId: String, taskId: Long, triggerAtMillis: Long) {
        val pendingIntent = createPendingIntent(batchId, taskId)
        alarmManager.cancel(pendingIntent)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            maxOf(System.currentTimeMillis() + 100L, triggerAtMillis),
            pendingIntent,
        )
    }

    fun scheduleImmediateRevive(batchId: String?) {
        val pendingIntent = createPendingIntent(batchId, -1L)
        alarmManager.cancel(pendingIntent)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1_000L,
            pendingIntent,
        )
    }

    fun cancelGuardAlarm() {
        alarmManager.cancel(createPendingIntent(null, -1))
    }

    private fun createPendingIntent(batchId: String?, taskId: Long): PendingIntent {
        val intent = Intent(appContext, GuardAlarmReceiver::class.java).apply {
            action = GuardAlarmReceiver.ACTION_GUARD_ALARM
            putExtra(GuardAlarmReceiver.EXTRA_BATCH_ID, batchId)
            putExtra(GuardAlarmReceiver.EXTRA_TASK_ID, taskId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(appContext, REQUEST_CODE_GUARD_ALARM, intent, flags)
    }
}
