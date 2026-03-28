package com.getupandgetlit.dingshihai

import android.app.Application
import com.getupandgetlit.dingshihai.data.db.AppDatabase
import com.getupandgetlit.dingshihai.data.repo.TaskRepository
import com.getupandgetlit.dingshihai.domain.bluetooth.BluetoothChecker
import com.getupandgetlit.dingshihai.domain.logger.AppLogger
import com.getupandgetlit.dingshihai.domain.player.PlaybackController
import com.getupandgetlit.dingshihai.domain.root.RootOps
import com.getupandgetlit.dingshihai.domain.scheduler.AlarmCoordinator
import com.getupandgetlit.dingshihai.domain.scheduler.BatchScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DingShiHaiApp : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(app: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = AppDatabase.build(app)
    val taskRepository = TaskRepository(database)
    val appLogger = AppLogger(app)
    val bluetoothChecker = BluetoothChecker(app)
    val rootOps = RootOps(app.packageName, appLogger)
    val alarmCoordinator = AlarmCoordinator(app)
    val playbackController = PlaybackController(
        context = app,
        bluetoothChecker = bluetoothChecker,
        appLogger = appLogger,
        rootOps = rootOps,
    )
    val batchScheduler = BatchScheduler(
        repository = taskRepository,
        logger = appLogger,
    )
}
