package com.getupandgetlit.dingshihai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.getupandgetlit.dingshihai.DingShiHaiApp
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val container = (context.applicationContext as DingShiHaiApp).appContainer
        container.applicationScope.launch {
            container.alarmCoordinator.cancelGuardAlarm()
            container.taskRepository.resetAfterBoot()
            container.appLogger.log(
                event = "boot_reset_all_tasks",
                result = "ok",
                message = "boot completed receiver reset app state",
            )
            pendingResult.finish()
        }
    }
}

