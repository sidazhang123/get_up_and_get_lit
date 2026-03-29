package com.getupandgetlit.dingshihai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.getupandgetlit.dingshihai.DingShiHaiApp
import com.getupandgetlit.dingshihai.R
import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import com.getupandgetlit.dingshihai.domain.root.ScreenBrightnessSnapshot
import com.getupandgetlit.dingshihai.domain.model.TaskStatus
import com.getupandgetlit.dingshihai.domain.player.PlaybackEvent
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SchedulerForegroundService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private val container by lazy { (application as DingShiHaiApp).appContainer }
    private val triggerMutex = Mutex()
    private var processWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var brightnessSnapshot: ScreenBrightnessSnapshot? = null
    private var startedForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            container.playbackController.events.collectLatest(::handlePlaybackEvent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESERVE_ALL -> serviceScope.launch { handleReserveAll() }
            ACTION_CANCEL_RESERVE -> serviceScope.launch { handleCancelReserve() }
            ACTION_GUARD_ALARM, ACTION_RECONCILE_BATCH, null -> serviceScope.launch {
                ensureForeground()
                acquireProcessWakeLock(PROCESS_WAKE_LOCK_HANDOFF_MS)
                container.rootOps.protectCurrentProcess(Process.myPid())
                if (intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L > 0L) {
                    handleAlarmTrigger(
                        batchId = intent?.getStringExtra(EXTRA_BATCH_ID),
                        taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L,
                    )
                } else {
                    reconcileBatchState()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLocks()
        serviceScope.launch {
            restoreBrightnessIfNeeded()
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch {
            val runtimeState = container.taskRepository.getRuntimeState()
            if (runtimeState.batchActive) {
                container.appLogger.log(
                    event = "task_removed_restart_requested",
                    result = "ok",
                    message = "recent task removed while batch active",
                )
                container.alarmCoordinator.scheduleImmediateRevive(runtimeState.batchId)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    private suspend fun handleReserveAll() {
        val tasks = container.taskRepository.getAllTasks()
        if (tasks.isEmpty()) {
            stopSelf()
            return
        }
        val selectedTasks = tasks.filter { it.selectedForReserve }
        if (selectedTasks.isEmpty()) {
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    this@SchedulerForegroundService,
                    getString(R.string.reserve_requires_selected_tasks),
                    Toast.LENGTH_LONG,
                ).show()
            }
            stopSelf()
            return
        }
        container.playbackController.stopCurrentPlayback("reserve_all")
        container.alarmCoordinator.cancelGuardAlarm()

        val whitelistResult = container.rootOps.ensureDozeWhitelist()
        if (whitelistResult.rooted && !whitelistResult.whitelisted) {
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    this@SchedulerForegroundService,
                    getString(R.string.root_whitelist_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        val batchId = UUID.randomUUID().toString()
        val scheduleMap = container.batchScheduler.buildSchedule(selectedTasks, System.currentTimeMillis())
        container.taskRepository.startBatch(batchId, scheduleMap)
        container.appLogger.log(event = "reserve_all_clicked", result = "ok")
        container.appLogger.log(
            event = "scheduler_started",
            result = "ok",
            message = "batchId=$batchId",
        )
        ensureForeground()
        scheduleNextExactAlarmOrFinish()
    }

    private suspend fun handleCancelReserve() {
        container.appLogger.log(event = "cancel_reserve_clicked", result = "ok")
        container.alarmCoordinator.cancelGuardAlarm()
        container.playbackController.stopCurrentPlayback("cancel_reserve")
        container.taskRepository.stopBatch(clearStatusesOnly = false)
        container.appLogger.log(event = "scheduler_stopped", result = "cancelled")
        stopIfIdle()
    }

    private suspend fun handleAlarmTrigger(batchId: String?, taskId: Long) {
        triggerMutex.withLock {
            val runtimeState = container.taskRepository.getRuntimeState()
            if (!runtimeState.batchActive) {
                stopIfIdle()
                return
            }
            if (batchId != null && runtimeState.batchId != null && batchId != runtimeState.batchId) {
                return
            }
            val task = container.taskRepository.getTaskById(taskId) ?: run {
                scheduleNextExactAlarmOrFinish()
                return
            }
            if (task.status != TaskStatus.UNTRIGGERED.value) {
                scheduleNextExactAlarmOrFinish()
                return
            }
            val (primaryTask, _) = container.batchScheduler.resolveConflictGroup(task)
            if (primaryTask.status != TaskStatus.UNTRIGGERED.value) {
                scheduleNextExactAlarmOrFinish()
                return
            }
            triggerTask(primaryTask)
        }
    }

    private suspend fun reconcileBatchState() {
        triggerMutex.withLock {
            val runtimeState = container.taskRepository.getRuntimeState()
            if (!runtimeState.batchActive) {
                stopIfIdle()
                return
            }
            val nextTask = container.taskRepository.getNextPendingTask()
            if (nextTask == null) {
                container.taskRepository.stopBatch(
                    clearStatusesOnly = false,
                    preserveCurrentPlayingTask = true,
                )
                container.appLogger.log(
                    event = "scheduler_stopped",
                    result = "completed",
                    message = "all tasks finished",
                )
                stopIfIdle()
                return
            }
            if ((nextTask.scheduledAtEpochMs ?: 0L) <= System.currentTimeMillis()) {
                val (primaryTask, _) = container.batchScheduler.resolveConflictGroup(nextTask)
                if (primaryTask.status == TaskStatus.UNTRIGGERED.value) {
                    triggerTask(primaryTask)
                } else {
                    scheduleNextExactAlarmOrFinish()
                }
            } else {
                scheduleNextExactAlarmOrFinish()
            }
        }
    }

    private suspend fun triggerTask(task: TaskEntity) {
        container.playbackController.stopCurrentPlayback("preempted_by_new_task")
        container.appLogger.log(
            event = "task_triggered",
            result = "ok",
            task = task,
        )
        if (task.forceBluetoothPlayback && !container.bluetoothChecker.isBluetoothAudioAvailable()) {
            container.taskRepository.markTaskStatus(task.id, TaskStatus.BLUETOOTH_UNAVAILABLE)
            container.appLogger.log(
                event = "bluetooth_unavailable",
                result = "failed",
                task = task,
            )
            scheduleNextExactAlarmOrFinish()
            return
        }
        container.taskRepository.markTaskStatus(task.id, TaskStatus.TRIGGERED)
        container.taskRepository.updateCurrentPlayingTask(task.id)
        acquireProcessWakeLock(PROCESS_WAKE_LOCK_ACTIVE_TASK_MS)
        container.playbackController.startTask(task)
        scheduleNextExactAlarmOrFinish()
    }

    private suspend fun scheduleNextExactAlarmOrFinish() {
        val runtimeState = container.taskRepository.getRuntimeState()
        if (!runtimeState.batchActive) {
            stopIfIdle()
            return
        }
        val nextTask = container.taskRepository.getNextPendingTask()
        if (nextTask == null) {
            container.alarmCoordinator.cancelGuardAlarm()
            container.taskRepository.stopBatch(
                clearStatusesOnly = false,
                preserveCurrentPlayingTask = true,
            )
            container.appLogger.log(
                event = "scheduler_stopped",
                result = "completed",
                message = "all tasks finished",
            )
            stopIfIdle()
            return
        }
        val batchId = runtimeState.batchId ?: return
        val triggerAtMillis = nextTask.scheduledAtEpochMs ?: System.currentTimeMillis()
        container.alarmCoordinator.scheduleGuardAlarm(batchId, nextTask.id, triggerAtMillis)
        container.appLogger.log(
            event = "next_alarm_scheduled",
            result = "ok",
            task = nextTask,
            message = "triggerAt=$triggerAtMillis",
        )
        stopIfIdle()
    }

    private suspend fun handlePlaybackEvent(event: PlaybackEvent) {
        when (event) {
            is PlaybackEvent.Started -> {
                acquireScreenWakeWindow(15_000L)
                container.taskRepository.getTaskById(event.taskId)?.let {
                    container.appLogger.log(event = "playback_started", result = "ok", task = it)
                }
            }

            is PlaybackEvent.Finished -> {
                clearCurrentPlayingIfNeeded(event.taskId)
                container.taskRepository.getTaskById(event.taskId)?.let {
                    container.appLogger.log(
                        event = "playback_finished",
                        result = "ok",
                        task = it,
                        message = "reason=${event.finishReason.logValue} hadTruncatedRounds=${event.hadTruncatedRounds}",
                    )
                }
                stopIfIdle()
            }

            is PlaybackEvent.Failed -> {
                clearCurrentPlayingIfNeeded(event.taskId)
                container.taskRepository.getTaskById(event.taskId)?.let {
                    container.appLogger.log(
                        event = "playback_failed",
                        result = "failed",
                        task = it,
                        message = event.message,
                    )
                }
                stopIfIdle()
            }

            is PlaybackEvent.BluetoothLost -> {
                clearCurrentPlayingIfNeeded(event.taskId)
                container.taskRepository.getTaskById(event.taskId)?.let {
                    container.appLogger.log(
                        event = "playback_failed",
                        result = "bluetooth_lost",
                        task = it,
                        message = "bluetooth disconnected during playback",
                    )
                }
                stopIfIdle()
            }

            is PlaybackEvent.Stopped -> {
                clearCurrentPlayingIfNeeded(event.taskId)
                stopIfIdle()
            }
        }
    }

    private suspend fun clearCurrentPlayingIfNeeded(taskId: Long) {
        val runtimeState = container.taskRepository.getRuntimeState()
        if (runtimeState.currentPlayingTaskId == taskId) {
            container.taskRepository.updateCurrentPlayingTask(null)
        }
    }

    private suspend fun stopIfIdle() {
        val runtimeState = container.taskRepository.getRuntimeState()
        if (runtimeState.currentPlayingTaskId == null) {
            if (!runtimeState.batchActive) {
                container.alarmCoordinator.cancelGuardAlarm()
            }
            releaseWakeLocks()
            restoreBrightnessIfNeeded()
            if (startedForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                startedForeground = false
            }
            stopSelf()
        }
    }

    private fun ensureForeground() {
        if (!startedForeground) {
            startForeground(NOTIFICATION_ID, buildNotification())
            startedForeground = true
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.batch_running))
            .setContentText(getString(R.string.foreground_notification_text))
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun acquireProcessWakeLock(timeoutMs: Long) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        processWakeLock?.takeIf { it.isHeld }?.release()
        processWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "dingshihai::process",
        ).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun acquireScreenWakeWindow(durationMs: Long) {
        if (brightnessSnapshot == null) {
            brightnessSnapshot = container.rootOps.dimScreenBrightnessForPlayback()
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenWakeLock?.takeIf { it.isHeld }?.release()
        screenWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "dingshihai::screen",
        ).apply {
            setReferenceCounted(false)
            acquire(durationMs)
        }
    }

    private suspend fun restoreBrightnessIfNeeded() {
        val snapshot = brightnessSnapshot ?: return
        brightnessSnapshot = null
        container.rootOps.restoreScreenBrightness(snapshot)
    }

    private fun releaseWakeLocks() {
        processWakeLock?.takeIf { it.isHeld }?.release()
        processWakeLock = null
        screenWakeLock?.takeIf { it.isHeld }?.release()
        screenWakeLock = null
    }

    companion object {
        private const val PROCESS_WAKE_LOCK_HANDOFF_MS = 2 * 60 * 1000L
        private const val PROCESS_WAKE_LOCK_ACTIVE_TASK_MS = 10 * 60 * 60 * 1000L
        private const val CHANNEL_ID = "scheduler_foreground"
        private const val NOTIFICATION_ID = 100
        private const val ACTION_RESERVE_ALL = "com.getupandgetlit.dingshihai.RESERVE_ALL"
        private const val ACTION_CANCEL_RESERVE = "com.getupandgetlit.dingshihai.CANCEL_RESERVE"
        private const val ACTION_GUARD_ALARM = "com.getupandgetlit.dingshihai.GUARD_ALARM_WAKE"
        private const val ACTION_RECONCILE_BATCH = "com.getupandgetlit.dingshihai.RECONCILE_BATCH"
        private const val EXTRA_BATCH_ID = "extra_batch_id"
        private const val EXTRA_TASK_ID = "extra_task_id"

        fun reserveAllIntent(context: Context): Intent {
            return Intent(context, SchedulerForegroundService::class.java).apply {
                action = ACTION_RESERVE_ALL
            }
        }

        fun cancelReserveIntent(context: Context): Intent {
            return Intent(context, SchedulerForegroundService::class.java).apply {
                action = ACTION_CANCEL_RESERVE
            }
        }

        fun guardAlarmIntent(context: Context, batchId: String?, taskId: Long): Intent {
            return Intent(context, SchedulerForegroundService::class.java).apply {
                action = ACTION_GUARD_ALARM
                putExtra(EXTRA_BATCH_ID, batchId)
                putExtra(EXTRA_TASK_ID, taskId)
            }
        }

        fun reviveIntent(context: Context): Intent {
            return Intent(context, SchedulerForegroundService::class.java).apply {
                action = ACTION_RECONCILE_BATCH
            }
        }
    }
}
