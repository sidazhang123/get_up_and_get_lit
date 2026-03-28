package com.getupandgetlit.dingshihai.ui.taskform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getupandgetlit.dingshihai.R
import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import com.getupandgetlit.dingshihai.data.repo.TaskRepository
import com.getupandgetlit.dingshihai.domain.logger.AppLogger
import com.getupandgetlit.dingshihai.domain.model.PlayMode
import com.getupandgetlit.dingshihai.domain.model.TaskDraft
import com.getupandgetlit.dingshihai.domain.model.TaskValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TaskFormViewModel(
    private val repository: TaskRepository,
    private val logger: AppLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskFormUiState())
    val uiState: StateFlow<TaskFormUiState> = _uiState.asStateFlow()

    fun loadTask(taskId: Long) {
        viewModelScope.launch {
            repository.getTaskById(taskId)?.let(::applyTask)
        }
    }

    fun updateName(value: String) {
        val trimmed = if (value.length > 10) value.take(10) else value
        updateDraft { copy(name = trimmed) }
    }

    fun updateTime(hour: Int, minute: Int) {
        updateDraft { copy(startHour = hour, startMinute = minute) }
    }

    fun updateFile(uri: String, fileName: String) {
        updateDraft { copy(fileUri = uri, fileName = fileName) }
    }

    fun updatePlayMode(mode: PlayMode) {
        updateDraft {
            if (mode == PlayMode.SINGLE) {
                copy(playMode = mode, loopCount = null, intervalMinSec = null, intervalMaxSec = null)
            } else {
                copy(playMode = mode)
            }
        }
    }

    fun updateLoopCount(value: String) {
        updateDraft { copy(loopCount = value.toIntOrNull()) }
    }

    fun updateIntervalMin(value: String) {
        updateDraft { copy(intervalMinSec = value.toIntOrNull()) }
    }

    fun updateIntervalMax(value: String) {
        updateDraft { copy(intervalMaxSec = value.toIntOrNull()) }
    }

    suspend fun save(): TaskSaveResult {
        return when (repository.validateTask(_uiState.value.draft)) {
            TaskValidationResult.Valid -> {
                val draft = _uiState.value.draft
                if (_uiState.value.isEditMode) {
                    repository.updateTask(draft)
                    repository.getTaskById(requireNotNull(draft.id))?.let {
                        logger.log(event = "task_updated", result = "ok", task = it)
                    }
                } else {
                    val entity = repository.createTask(draft)
                    logger.log(event = "task_created", result = "ok", task = entity)
                }
                TaskSaveResult.Success
            }

            TaskValidationResult.MissingRequired -> TaskSaveResult.Error(R.string.field_required)
            TaskValidationResult.InvalidTime -> TaskSaveResult.Error(R.string.invalid_time)
            TaskValidationResult.DuplicateTime -> TaskSaveResult.Error(R.string.duplicate_time)
            TaskValidationResult.InvalidFileType -> TaskSaveResult.Error(R.string.invalid_file_type)
            TaskValidationResult.InvalidInterval -> TaskSaveResult.Error(R.string.invalid_interval)
        }
    }

    private fun applyTask(task: TaskEntity) {
        _uiState.value = TaskFormUiState(
            isEditMode = true,
            draft = TaskDraft(
                id = task.id,
                name = task.name.orEmpty(),
                startHour = task.startHour,
                startMinute = task.startMinute,
                fileUri = task.fileUri,
                fileName = task.fileName,
                playMode = PlayMode.fromValue(task.playMode),
                loopCount = task.loopCount,
                intervalMinSec = task.intervalMinSec,
                intervalMaxSec = task.intervalMaxSec,
            ),
        )
    }

    private fun updateDraft(update: TaskDraft.() -> TaskDraft) {
        _uiState.value = _uiState.value.copy(draft = _uiState.value.draft.update())
    }
}
