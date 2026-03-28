package com.getupandgetlit.dingshihai.domain.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import com.getupandgetlit.dingshihai.domain.bluetooth.BluetoothChecker
import com.getupandgetlit.dingshihai.domain.logger.AppLogger
import com.getupandgetlit.dingshihai.domain.model.PlayMode
import com.getupandgetlit.dingshihai.domain.root.RootOps
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class PlaybackEvent {
    data class Started(val taskId: Long) : PlaybackEvent()
    data class Finished(val taskId: Long) : PlaybackEvent()
    data class Failed(val taskId: Long, val message: String) : PlaybackEvent()
    data class BluetoothLost(val taskId: Long) : PlaybackEvent()
    data class Stopped(val taskId: Long, val reason: String) : PlaybackEvent()
}

class PlaybackController(
    context: Context,
    private val bluetoothChecker: BluetoothChecker,
    private val appLogger: AppLogger,
    private val rootOps: RootOps,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controlMutex = Mutex()
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 32)
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { }
    private val playerAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
    private var player: ExoPlayer? = null
    private var playbackJob: Job? = null
    private var activeTaskId: Long? = null
    private var hasAudioFocus = false

    val events: SharedFlow<PlaybackEvent> = _events

    suspend fun startTask(task: TaskEntity) {
        controlMutex.withLock {
            stopLocked("preempted")
            activeTaskId = task.id
            playbackJob = playerScope.launch {
                var startedEmitted = false
                val result = runCatching {
                    runTask(task) {
                        if (!startedEmitted) {
                            startedEmitted = true
                            _events.emit(PlaybackEvent.Started(task.id))
                        }
                    }
                }.getOrElse { throwable ->
                    PlaybackResult.Failed(
                        throwable.message ?: throwable::class.java.simpleName,
                    )
                }
                when (result) {
                    PlaybackResult.Finished -> _events.emit(PlaybackEvent.Finished(task.id))
                    PlaybackResult.BluetoothLost -> _events.emit(PlaybackEvent.BluetoothLost(task.id))
                    is PlaybackResult.Failed -> _events.emit(PlaybackEvent.Failed(task.id, result.message))
                    is PlaybackResult.Stopped -> _events.emit(PlaybackEvent.Stopped(task.id, result.reason))
                }
                controlMutex.withLock {
                    if (activeTaskId == task.id) {
                        activeTaskId = null
                        playbackJob = null
                    }
                }
            }
        }
    }

    suspend fun stopCurrentPlayback(reason: String) {
        controlMutex.withLock {
            stopLocked(reason)
        }
    }

    suspend fun isPlayingTask(taskId: Long?): Boolean {
        return controlMutex.withLock { activeTaskId != null && activeTaskId == taskId }
    }

    private suspend fun stopLocked(reason: String) {
        val job = playbackJob
        activeTaskId = null
        playbackJob = null
        if (job != null) {
            job.cancelAndJoin()
        }
        withContext(Dispatchers.Main.immediate) {
            player?.run {
                stop()
                clearMediaItems()
            }
        }
    }

    private suspend fun runTask(task: TaskEntity, onStarted: suspend () -> Unit): PlaybackResult {
        val iterations = if (PlayMode.fromValue(task.playMode) == PlayMode.INTERVAL) {
            task.loopCount ?: return PlaybackResult.Failed("loopCount missing")
        } else {
            1
        }
        repeat(iterations) { index ->
            if (!bluetoothChecker.isBluetoothAudioAvailable()) {
                return PlaybackResult.BluetoothLost
            }
            when (val onceResult = playOnce(task.fileUri, onStarted)) {
                PlaybackOnceResult.Completed -> Unit
                PlaybackOnceResult.BluetoothLost -> return PlaybackResult.BluetoothLost
                is PlaybackOnceResult.Failed -> return PlaybackResult.Failed(onceResult.message)
                is PlaybackOnceResult.Stopped -> return PlaybackResult.Stopped(onceResult.reason)
            }
            if (index < iterations - 1) {
                val minSec = task.intervalMinSec ?: return PlaybackResult.Failed("intervalMinSec missing")
                val maxSec = task.intervalMaxSec ?: return PlaybackResult.Failed("intervalMaxSec missing")
                val waitMs = Random.nextInt(minSec, maxSec + 1) * 1000L
                var elapsed = 0L
                while (elapsed < waitMs) {
                    if (!bluetoothChecker.isBluetoothAudioAvailable()) {
                        return PlaybackResult.BluetoothLost
                    }
                    val step = min(500L, waitMs - elapsed)
                    delay(step)
                    elapsed += step
                }
            }
        }
        return PlaybackResult.Finished
    }

    private suspend fun playOnce(
        fileUri: String,
        onStarted: suspend () -> Unit,
    ): PlaybackOnceResult {
        val completion = CompletableDeferred<PlaybackOnceResult>()
        val started = CompletableDeferred<Unit>()
        val routeState = bluetoothChecker.preparePlaybackRoute()
        if (!routeState.available) {
            return PlaybackOnceResult.BluetoothLost
        }
        appLogger.log(
            event = "bluetooth_route_prepared",
            result = "ok",
            message = "a2dp=${routeState.a2dpConnected} headset=${routeState.headsetConnected} device=${routeState.outputDevicePresent}",
        )
        rootOps.preparePlaybackWindow()
        val exoPlayer = withContext(Dispatchers.Main.immediate) {
            getOrCreatePlayer()
        }
        if (!requestAudioFocus()) {
            return PlaybackOnceResult.Failed("audio focus denied")
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !started.isCompleted) {
                    started.complete(Unit)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && exoPlayer.playWhenReady && !started.isCompleted) {
                    started.complete(Unit)
                }
                if (playbackState == Player.STATE_ENDED && !completion.isCompleted) {
                    completion.complete(PlaybackOnceResult.Completed)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!completion.isCompleted) {
                    completion.complete(PlaybackOnceResult.Failed(error.message ?: "player error"))
                }
            }
        }
        withContext(Dispatchers.Main.immediate) {
            exoPlayer.addListener(listener)
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(fileUri)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }
        try {
            when (val startResult = awaitPlaybackStart(started, completion, fileUri)) {
                null -> onStarted()
                else -> return startResult
            }
            while (!completion.isCompleted) {
                delay(250)
                if (!bluetoothChecker.isBluetoothAudioAvailable()) {
                    completion.complete(PlaybackOnceResult.BluetoothLost)
                    break
                }
            }
            return completion.await()
        } catch (cancellation: Exception) {
            if (!completion.isCompleted) {
                completion.complete(PlaybackOnceResult.Stopped("cancelled"))
            }
            return completion.await()
        } finally {
            withContext(Dispatchers.Main.immediate) {
                exoPlayer.removeListener(listener)
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
            abandonAudioFocus()
        }
    }

    private suspend fun awaitPlaybackStart(
        started: CompletableDeferred<Unit>,
        completion: CompletableDeferred<PlaybackOnceResult>,
        fileUri: String,
    ): PlaybackOnceResult? {
        var elapsedMs = 0L
        while (elapsedMs < PLAYBACK_START_TIMEOUT_MS && !started.isCompleted && !completion.isCompleted) {
            delay(200)
            elapsedMs += 200
            val routeState = bluetoothChecker.preparePlaybackRoute()
            if (!routeState.available) {
                return PlaybackOnceResult.BluetoothLost
            }
        }
        if (started.isCompleted || completion.isCompleted) {
            return null
        }
        appLogger.log(
            event = "playback_start_timeout",
            result = "failed",
            message = "uri=$fileUri waitedMs=$PLAYBACK_START_TIMEOUT_MS",
        )
        return PlaybackOnceResult.Failed("playback start timeout")
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) {
            return true
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        val result = audioManager.requestAudioFocus(
            audioFocusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        )
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        if (!hasAudioFocus) {
            return
        }
        audioManager.abandonAudioFocus(audioFocusListener)
        hasAudioFocus = false
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(appContext).build().also {
            it.setAudioAttributes(playerAudioAttributes, false)
            it.setHandleAudioBecomingNoisy(true)
            it.setWakeMode(C.WAKE_MODE_LOCAL)
            player = it
        }
    }

    companion object {
        private const val PLAYBACK_START_TIMEOUT_MS = 4_000L
    }
}

private sealed class PlaybackResult {
    data object Finished : PlaybackResult()
    data object BluetoothLost : PlaybackResult()
    data class Failed(val message: String) : PlaybackResult()
    data class Stopped(val reason: String) : PlaybackResult()
}

private sealed class PlaybackOnceResult {
    data object Completed : PlaybackOnceResult()
    data object BluetoothLost : PlaybackOnceResult()
    data class Failed(val message: String) : PlaybackOnceResult()
    data class Stopped(val reason: String) : PlaybackOnceResult()
}
