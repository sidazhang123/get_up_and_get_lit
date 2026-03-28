package com.getupandgetlit.dingshihai.domain.logger

import android.content.Context
import android.os.Environment
import android.util.Log
import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppLogger(
    private val context: Context,
) {
    companion object {
        private const val TAG = "DingShiHaiLogger"
        private const val LOG_DIR_NAME = "\u5B9A\u65F6\u55E8"
        private const val LOG_FILE_PREFIX = "\u5B9A\u65F6\u55E8_"
    }

    private val mutex = Mutex()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    @Volatile
    private var currentLogFile: File? = null

    suspend fun log(
        event: String,
        result: String,
        task: TaskEntity? = null,
        message: String? = null,
    ) {
        val line = buildString {
            append("timestamp=").append(timestampFormat.format(Date()))
            append(" | event=").append(event)
            append(" | result=").append(result)
            append(" | taskId=").append(task?.id ?: "")
            append(" | taskName=").append(task?.name ?: "")
            if (!message.isNullOrBlank()) {
                append(" | info=").append(message.replace('\n', ' '))
            }
        }

        mutex.withLock {
            val primaryResult = runCatching {
                val file = getOrCreateLogFile()
                file.appendText("$line\n", StandardCharsets.UTF_8)
            }
            if (primaryResult.isFailure) {
                val fallbackResult = runCatching {
                    val fallbackFile = getOrCreateFallbackLogFile()
                    fallbackFile.appendText("$line\n", StandardCharsets.UTF_8)
                }
                Log.e(
                    TAG,
                    "log write failed, primary=${primaryResult.exceptionOrNull()?.message}, fallback=${fallbackResult.exceptionOrNull()?.message}",
                    primaryResult.exceptionOrNull() ?: fallbackResult.exceptionOrNull(),
                )
            }
        }

        Log.i(TAG, line)
    }

    private fun getOrCreateLogFile(): File {
        currentLogFile?.let { file ->
            if (file.exists() || file.parentFile?.exists() == true) {
                return file
            }
        }
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadDir, LOG_DIR_NAME)
        ensureDirectory(targetDir)
        return File(targetDir, "$LOG_FILE_PREFIX${fileNameFormat.format(Date())}.log").also { file ->
            ensureFile(file)
            currentLogFile = file
        }
    }

    private fun getOrCreateFallbackLogFile(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val targetDir = File(baseDir, LOG_DIR_NAME)
        ensureDirectory(targetDir)
        return File(targetDir, "$LOG_FILE_PREFIX${fileNameFormat.format(Date())}.log").also(::ensureFile)
    }

    private fun ensureDirectory(dir: File) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Failed to create directory: ${dir.absolutePath}")
        }
    }

    private fun ensureFile(file: File) {
        if (!file.exists() && !file.createNewFile()) {
            throw IllegalStateException("Failed to create file: ${file.absolutePath}")
        }
    }
}
