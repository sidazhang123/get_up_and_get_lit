package com.getupandgetlit.dingshihai.domain.root

import com.getupandgetlit.dingshihai.domain.logger.AppLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RootWhitelistResult(
    val rooted: Boolean,
    val whitelisted: Boolean,
    val message: String,
)

data class ScreenBrightnessSnapshot(
    val mode: String,
    val brightness: String,
)

class RootOps(
    private val packageName: String,
    private val logger: AppLogger,
) {
    suspend fun ensureDozeWhitelist(): RootWhitelistResult = withContext(Dispatchers.IO) {
        if (!hasRoot()) {
            return@withContext RootWhitelistResult(
                rooted = false,
                whitelisted = false,
                message = "su unavailable",
            )
        }
        val whitelistOutput = runSuCommand("dumpsys deviceidle whitelist")
        if (whitelistOutput.contains(packageName)) {
            return@withContext RootWhitelistResult(true, true, "already whitelisted")
        }
        runSuCommand("dumpsys deviceidle whitelist +$packageName")
        val verified = runSuCommand("dumpsys deviceidle whitelist").contains(packageName)
        if (!verified) {
            logger.log(
                event = "root_whitelist_failed",
                result = "failed",
                message = "package=$packageName",
            )
        }
        RootWhitelistResult(
            rooted = true,
            whitelisted = verified,
            message = if (verified) "whitelist added" else "whitelist add failed",
        )
    }

    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            runSuCommand("id").contains("uid=0")
        }.getOrDefault(false)
    }

    suspend fun startSchedulerService(serviceClassName: String, action: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasRoot()) {
            return@withContext false
        }
        val command = "am startservice -n $packageName/$serviceClassName -a $action"
        val output = runCatching { runSuCommand(command) }.getOrElse { throwable ->
            logger.log(
                event = "root_service_restart_failed",
                result = "failed",
                message = throwable.message ?: "unknown error",
            )
            return@withContext false
        }
        val success = output.contains("Starting service") || output.contains("Service started")
        if (!success) {
            logger.log(
                event = "root_service_restart_failed",
                result = "failed",
                message = output.trim(),
            )
        }
        return@withContext success
    }

    suspend fun protectCurrentProcess(pid: Int): Boolean = withContext(Dispatchers.IO) {
        if (!hasRoot()) {
            return@withContext false
        }
        val output = runCatching {
            runSuCommand("echo -1000 > /proc/$pid/oom_score_adj; cat /proc/$pid/oom_score_adj")
        }.getOrElse { throwable ->
            logger.log(
                event = "root_process_protect_failed",
                result = "failed",
                message = throwable.message ?: "unknown error",
            )
            return@withContext false
        }
        val success = output.trim().endsWith("-1000")
        if (!success) {
            logger.log(
                event = "root_process_protect_failed",
                result = "failed",
                message = output.trim(),
            )
        } else {
            logger.log(
                event = "root_process_protected",
                result = "ok",
                message = "pid=$pid oom_score_adj=-1000",
            )
        }
        return@withContext success
    }

    suspend fun wakeDevice(): Boolean = withContext(Dispatchers.IO) {
        if (!hasRoot()) {
            return@withContext false
        }
        val output = runCatching {
            runSuCommand("input keyevent 224")
        }.getOrElse { throwable ->
            logger.log(
                event = "root_wakeup_failed",
                result = "failed",
                message = throwable.message ?: "unknown error",
            )
            return@withContext false
        }
        logger.log(
            event = "root_wakeup_sent",
            result = "ok",
            message = output.trim(),
        )
        return@withContext true
    }

    suspend fun preparePlaybackWindow(): Boolean = withContext(Dispatchers.IO) {
        if (!hasRoot()) {
            return@withContext false
        }
        val output = runCatching {
            runSuCommand("input keyevent 224; wm dismiss-keyguard >/dev/null 2>&1; echo playback_window_ready")
        }.getOrElse { throwable ->
            logger.log(
                event = "root_playback_window_failed",
                result = "failed",
                message = throwable.message ?: "unknown error",
            )
            return@withContext false
        }
        logger.log(
            event = "root_playback_window_prepared",
            result = "ok",
            message = output.trim(),
        )
        return@withContext true
    }

    suspend fun dimScreenBrightnessForPlayback(): ScreenBrightnessSnapshot? = withContext(Dispatchers.IO) {
        if (!hasRoot()) {
            return@withContext null
        }
        val mode = runCatching {
            runSuCommand("settings get system screen_brightness_mode").trim()
        }.getOrElse { throwable ->
            logger.log(
                event = "root_brightness_dim_failed",
                result = "failed",
                message = throwable.message ?: "read mode failed",
            )
            return@withContext null
        }
        val brightness = runCatching {
            runSuCommand("settings get system screen_brightness").trim()
        }.getOrElse { throwable ->
            logger.log(
                event = "root_brightness_dim_failed",
                result = "failed",
                message = throwable.message ?: "read brightness failed",
            )
            return@withContext null
        }
        val output = runCatching {
            runSuCommand("settings put system screen_brightness_mode 0; settings put system screen_brightness 1; echo brightness_dimmed")
        }.getOrElse { throwable ->
            logger.log(
                event = "root_brightness_dim_failed",
                result = "failed",
                message = throwable.message ?: "write brightness failed",
            )
            return@withContext null
        }
        logger.log(
            event = "root_brightness_dimmed",
            result = "ok",
            message = "mode=$mode brightness=$brightness output=${output.trim()}",
        )
        return@withContext ScreenBrightnessSnapshot(
            mode = mode,
            brightness = brightness,
        )
    }

    suspend fun restoreScreenBrightness(snapshot: ScreenBrightnessSnapshot?) = withContext(Dispatchers.IO) {
        if (snapshot == null || !hasRoot()) {
            return@withContext
        }
        runCatching {
            val mode = snapshot.mode.ifBlank { "0" }
            val brightness = snapshot.brightness.ifBlank { "20" }
            runSuCommand("settings put system screen_brightness_mode $mode; settings put system screen_brightness $brightness; echo brightness_restored")
        }.onSuccess { output ->
            logger.log(
                event = "root_brightness_restored",
                result = "ok",
                message = "mode=${snapshot.mode} brightness=${snapshot.brightness} output=${output.trim()}",
            )
        }.onFailure { throwable ->
            logger.log(
                event = "root_brightness_restore_failed",
                result = "failed",
                message = throwable.message ?: "restore brightness failed",
            )
        }
    }

    private fun runSuCommand(command: String): String {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.readText()
        }
        process.waitFor()
        return output
    }
}
